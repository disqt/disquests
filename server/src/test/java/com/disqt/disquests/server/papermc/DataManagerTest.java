package com.disqt.disquests.server.papermc;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.common.model.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DataManagerTest {

  @TempDir Path tempDir;

  private DataManager dm;

  // Fixed UUIDs for deterministic tests
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PLAYER2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID PLAYER3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @BeforeEach
  void setUp() {
    dm = new DataManager(tempDir);
    dm.initialize();
  }

  @AfterEach
  void tearDown() {
    dm.close();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private QuestData makeQuest(UUID id, UUID owner, String title, Visibility vis) {
    return new QuestData(
        id,
        title,
        "content",
        owner,
        null,
        vis,
        List.of(),
        1000L,
        new CoordinatesData(1.0, 2.0, 3.0),
        false,
        null,
        "world",
        List.of());
  }

  // -------------------------------------------------------------------------
  // Quests
  // -------------------------------------------------------------------------

  @Test
  void saveAndGetQuest() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");

    QuestData quest =
        new QuestData(
            id,
            "My Quest",
            "Do the thing",
            OWNER,
            null,
            Visibility.OPEN,
            List.of(),
            9999L,
            new CoordinatesData(10.5, 64.0, -32.25),
            true,
            new CoordinatesData(20.5, 65.0, -31.0),
            "the_end",
            List.of());
    dm.saveQuest(quest);

    QuestData loaded = dm.getQuest(id);
    assertNotNull(loaded);
    assertEquals(id, loaded.id());
    assertEquals("My Quest", loaded.title());
    assertEquals("Do the thing", loaded.content());
    assertEquals(OWNER, loaded.ownerUuid());
    assertEquals("Alice", loaded.ownerName());
    assertEquals(Visibility.OPEN, loaded.visibility());
    assertEquals(9999L, loaded.lastModified());
    assertTrue(loaded.isRegion());
    assertNotNull(loaded.coordinates());
    assertEquals(10.5, loaded.coordinates().x(), 1e-9);
    assertEquals(64.0, loaded.coordinates().y(), 1e-9);
    assertEquals(-32.25, loaded.coordinates().z(), 1e-9);
    assertNotNull(loaded.coordinates2());
    assertEquals(20.5, loaded.coordinates2().x(), 1e-9);
    assertEquals(65.0, loaded.coordinates2().y(), 1e-9);
    assertEquals(-31.0, loaded.coordinates2().z(), 1e-9);
    assertEquals("the_end", loaded.map());
  }

  @Test
  void saveAndGetQuest_noOptionalFields() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");

    QuestData quest =
        new QuestData(
            id,
            "Minimal",
            "",
            OWNER,
            null,
            Visibility.PRIVATE,
            List.of(),
            1L,
            null,
            false,
            null,
            null,
            List.of());
    dm.saveQuest(quest);

    QuestData loaded = dm.getQuest(id);
    assertNotNull(loaded);
    assertNull(loaded.coordinates());
    assertNull(loaded.coordinates2());
    assertNull(loaded.map());
    assertFalse(loaded.isRegion());
  }

  @Test
  void deleteQuest() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(id, OWNER, "To Delete", Visibility.OPEN));

    assertNotNull(dm.getQuest(id));
    boolean deleted = dm.deleteQuest(id);
    assertTrue(deleted);
    assertNull(dm.getQuest(id));
  }

  @Test
  void getQuestsForPlayer_asOwner() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(id, OWNER, "My Quest", Visibility.OPEN));

    List<QuestData> quests = dm.getQuestsForPlayer(OWNER);
    assertEquals(1, quests.size());
    assertEquals(id, quests.get(0).id());
  }

  @Test
  void getQuestsForPlayer_asContributor() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.saveQuest(makeQuest(id, OWNER, "Shared Quest", Visibility.OPEN));
    dm.addContributor(id, PLAYER2, false);

    List<QuestData> quests = dm.getQuestsForPlayer(PLAYER2);
    assertEquals(1, quests.size());
    assertEquals(id, quests.get(0).id());
  }

  @Test
  void getServerQuests_excludesOwnedAndContributed() {
    UUID ownedId = UUID.randomUUID();
    UUID contribId = UUID.randomUUID();
    UUID unrelatedId = UUID.randomUUID();

    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.upsertPlayerName(PLAYER3, "Carol");

    // OWNER's own quest
    dm.saveQuest(makeQuest(ownedId, OWNER, "Owned", Visibility.OPEN));
    // quest by PLAYER2 where OWNER is contributor
    dm.saveQuest(makeQuest(contribId, PLAYER2, "Contributed", Visibility.OPEN));
    dm.addContributor(contribId, OWNER, false);
    // unrelated open quest by PLAYER3
    dm.saveQuest(makeQuest(unrelatedId, PLAYER3, "Unrelated", Visibility.OPEN));

    List<QuestData> server = dm.getServerQuests(OWNER);
    assertEquals(1, server.size());
    assertEquals(unrelatedId, server.get(0).id());
  }

  @Test
  void getServerQuests_excludesPrivate() {
    UUID privateId = UUID.randomUUID();
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.saveQuest(makeQuest(privateId, PLAYER2, "Secret", Visibility.PRIVATE));

    List<QuestData> server = dm.getServerQuests(OWNER);
    assertTrue(server.isEmpty());
  }

  @Test
  void updateVisibility() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(id, OWNER, "Quest", Visibility.PRIVATE));

    dm.updateVisibility(id, Visibility.OPEN);

    QuestData loaded = dm.getQuest(id);
    assertNotNull(loaded);
    assertEquals(Visibility.OPEN, loaded.visibility());
  }

  // -------------------------------------------------------------------------
  // Contributors
  // -------------------------------------------------------------------------

  @Test
  void addAndRemoveContributor() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

    dm.addContributor(questId, PLAYER2, false);
    List<ContributorData> contributors = dm.getContributors(questId);
    assertEquals(1, contributors.size());
    assertEquals(PLAYER2, contributors.get(0).uuid());
    assertFalse(contributors.get(0).canEdit());

    dm.removeContributor(questId, PLAYER2);
    List<ContributorData> after = dm.getContributors(questId);
    assertTrue(after.isEmpty());
  }

  @Test
  void updateContributor() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

    dm.addContributor(questId, PLAYER2, false);
    dm.updateContributor(questId, PLAYER2, true);

    List<ContributorData> contributors = dm.getContributors(questId);
    assertEquals(1, contributors.size());
    assertTrue(contributors.get(0).canEdit());
  }

  @Test
  void isContributor() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

    assertFalse(dm.isContributor(questId, PLAYER2));
    dm.addContributor(questId, PLAYER2, false);
    assertTrue(dm.isContributor(questId, PLAYER2));
  }

  // -------------------------------------------------------------------------
  // Collaboration Requests
  // -------------------------------------------------------------------------

  @Test
  void collaborationRequest_createAndGet() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.saveQuest(makeQuest(questId, OWNER, "Collab Quest", Visibility.OPEN));

    UUID requestId = dm.createCollaborationRequest(questId, PLAYER2);
    assertNotNull(requestId);

    CollaborationRequestData req = dm.getCollaborationRequest(requestId);
    assertNotNull(req);
    assertEquals(requestId, req.id());
    assertEquals(questId, req.questId());
    assertEquals("Collab Quest", req.questTitle());
    assertEquals(PLAYER2, req.requesterUuid());
    assertEquals("Bob", req.requesterName());
    assertTrue(req.timestamp() > 0);
  }

  @Test
  void collaborationRequest_duplicateSilentlyIgnored() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

    dm.createCollaborationRequest(questId, PLAYER2);

    // Second request for same quest+requester hits UNIQUE constraint and throws
    assertThrows(RuntimeException.class, () -> dm.createCollaborationRequest(questId, PLAYER2));

    // Only one request exists
    List<CollaborationRequestData> requests = dm.getPendingRequestsForOwner(OWNER);
    assertEquals(1, requests.size());
  }

  // -------------------------------------------------------------------------
  // Pins
  // -------------------------------------------------------------------------

  @Test
  void multiPin_addAndList() {
    UUID questA = UUID.randomUUID();
    UUID questB = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(questA, OWNER, "Quest A", Visibility.OPEN));
    dm.saveQuest(makeQuest(questB, OWNER, "Quest B", Visibility.OPEN));

    assertTrue(dm.getPinnedQuestIds(OWNER).isEmpty());

    dm.pinQuest(OWNER, questA);
    dm.pinQuest(OWNER, questB);

    List<UUID> pinned = dm.getPinnedQuestIds(OWNER);
    assertEquals(2, pinned.size());
    assertTrue(
        pinned.containsAll(List.of(questA, questB)),
        "Pinned list should contain both quests (order not guaranteed)");
  }

  @Test
  void multiPin_toggleUnpin() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

    dm.pinQuest(OWNER, questId);
    assertTrue(dm.isQuestPinned(OWNER, questId));
    assertEquals(1, dm.getPinnedQuestIds(OWNER).size());

    dm.unpinQuest(OWNER, questId);
    assertFalse(dm.isQuestPinned(OWNER, questId));
    assertTrue(dm.getPinnedQuestIds(OWNER).isEmpty());
  }

  @Test
  void isQuestPinned() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

    assertFalse(dm.isQuestPinned(OWNER, questId));
    dm.pinQuest(OWNER, questId);
    assertTrue(dm.isQuestPinned(OWNER, questId));
  }

  @Test
  void pinQuest_duplicateIgnored() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

    dm.pinQuest(OWNER, questId);
    dm.pinQuest(OWNER, questId); // should be silently ignored

    List<UUID> pinned = dm.getPinnedQuestIds(OWNER);
    assertEquals(1, pinned.size());
  }

  // -------------------------------------------------------------------------
  // Player Names
  // -------------------------------------------------------------------------

  @Test
  void playerName_upsert() {
    dm.upsertPlayerName(OWNER, "Alice");
    assertEquals("Alice", dm.getPlayerName(OWNER));

    dm.upsertPlayerName(OWNER, "AliceRenamed");
    assertEquals("AliceRenamed", dm.getPlayerName(OWNER));
  }

  @Test
  void getPlayerUuidByName() {
    dm.upsertPlayerName(OWNER, "Alice");
    assertEquals(OWNER, dm.getPlayerUuidByName("Alice"));
    assertNull(dm.getPlayerUuidByName("UnknownPlayer"));
  }

  // -------------------------------------------------------------------------
  // Leave Quest
  // -------------------------------------------------------------------------

  @Test
  void leaveQuest_removesContributorAndPin() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Owner");
    dm.upsertPlayerName(PLAYER2, "Player2");

    QuestData quest =
        new QuestData(
            questId,
            "Quest",
            "content",
            OWNER,
            null,
            Visibility.OPEN,
            List.of(),
            System.currentTimeMillis(),
            null,
            false,
            null,
            null,
            List.of());
    dm.saveQuest(quest);
    dm.addContributor(questId, PLAYER2, false);
    dm.pinQuest(PLAYER2, questId);

    assertTrue(dm.isContributor(questId, PLAYER2));
    assertTrue(dm.isQuestPinned(PLAYER2, questId));

    dm.leaveQuest(questId, PLAYER2);

    assertFalse(dm.isContributor(questId, PLAYER2));
    assertFalse(dm.isQuestPinned(PLAYER2, questId));
    assertNotNull(dm.getQuest(questId)); // quest still exists
  }

  @Test
  void getPendingCountByQuest_returnsMapOfCounts() {
    UUID questId1 = UUID.randomUUID();
    UUID questId2 = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Owner");
    dm.upsertPlayerName(PLAYER2, "Player2");
    dm.upsertPlayerName(PLAYER3, "Player3");
    dm.saveQuest(
        new QuestData(
            questId1,
            "Q1",
            "",
            OWNER,
            null,
            Visibility.CLOSED,
            List.of(),
            System.currentTimeMillis(),
            null,
            false,
            null,
            null,
            List.of()));
    dm.saveQuest(
        new QuestData(
            questId2,
            "Q2",
            "",
            OWNER,
            null,
            Visibility.CLOSED,
            List.of(),
            System.currentTimeMillis(),
            null,
            false,
            null,
            null,
            List.of()));
    dm.createCollaborationRequest(questId1, PLAYER2);
    dm.createCollaborationRequest(questId1, PLAYER3);
    dm.createCollaborationRequest(questId2, PLAYER2);
    Map<UUID, Integer> counts = dm.getPendingCountByQuest(OWNER);
    assertEquals(2, counts.get(questId1));
    assertEquals(1, counts.get(questId2));
  }

  // -------------------------------------------------------------------------
  // Tags
  // -------------------------------------------------------------------------

  @Test
  void saveQuest_persistsTags() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    QuestData quest =
        new QuestData(
            id,
            "Tagged Quest",
            "content",
            OWNER,
            null,
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of("build", "survival", "creative"));
    dm.saveQuest(quest);

    QuestData loaded = dm.getQuest(id);
    assertNotNull(loaded);
    assertEquals(3, loaded.tags().size());
    assertTrue(loaded.tags().containsAll(List.of("build", "survival", "creative")));
  }

  @Test
  void saveQuest_replacesTags() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    QuestData quest =
        new QuestData(
            id,
            "Quest",
            "content",
            OWNER,
            null,
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of("old-tag", "another-old"));
    dm.saveQuest(quest);

    QuestData updated =
        new QuestData(
            id,
            "Quest",
            "content",
            OWNER,
            null,
            Visibility.OPEN,
            List.of(),
            2000L,
            null,
            false,
            null,
            null,
            List.of("new-tag"));
    dm.saveQuest(updated);

    QuestData loaded = dm.getQuest(id);
    assertNotNull(loaded);
    assertEquals(1, loaded.tags().size());
    assertTrue(loaded.tags().contains("new-tag"));
    assertFalse(loaded.tags().contains("old-tag"));
    assertFalse(loaded.tags().contains("another-old"));
  }

  @Test
  void saveQuest_emptyTags() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    QuestData quest =
        new QuestData(
            id,
            "Quest",
            "content",
            OWNER,
            null,
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of());
    dm.saveQuest(quest);

    QuestData loaded = dm.getQuest(id);
    assertNotNull(loaded);
    assertTrue(loaded.tags().isEmpty());
  }

  @Test
  void resetDatabase_clearsTags() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    QuestData quest =
        new QuestData(
            id,
            "Tagged Quest",
            "content",
            OWNER,
            null,
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of("tag1", "tag2"));
    dm.saveQuest(quest);

    dm.resetDatabase();

    assertNull(dm.getQuest(id));
    assertTrue(dm.getQuestsForPlayer(OWNER).isEmpty());
  }

  // -------------------------------------------------------------------------
  // Reset Database
  // -------------------------------------------------------------------------

  @Test
  void resetDatabase_clearsAllTables() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(questId, OWNER, "Test Quest", Visibility.OPEN));
    dm.pinQuest(OWNER, questId);
    dm.addContributor(questId, PLAYER2, false);
    dm.createCollaborationRequest(questId, PLAYER2);

    dm.resetDatabase();

    assertTrue(dm.getQuestsForPlayer(OWNER).isEmpty());
    assertTrue(dm.getPinnedQuestIds(OWNER).isEmpty());
    assertFalse(dm.isContributor(questId, PLAYER2));
    assertTrue(dm.getPendingRequestsForOwner(OWNER).isEmpty());
    assertNull(dm.getPlayerName(OWNER));
  }

  // -------------------------------------------------------------------------
  // Cascade Deletes
  // -------------------------------------------------------------------------

  @Test
  void deleteQuest_cascadesContributors() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.upsertPlayerName(PLAYER3, "Carol");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));
    dm.addContributor(questId, PLAYER2, false);
    dm.addContributor(questId, PLAYER3, true);

    assertEquals(2, dm.getContributors(questId).size());

    dm.deleteQuest(questId);

    // getContributors on a deleted quest should return empty (FK cascade)
    List<ContributorData> remaining = dm.getContributors(questId);
    assertTrue(remaining.isEmpty());
  }

  @Test
  void deleteQuest_cascadesRequests() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

    UUID requestId = dm.createCollaborationRequest(questId, PLAYER2);
    assertNotNull(dm.getCollaborationRequest(requestId));

    dm.deleteQuest(questId);

    assertNull(dm.getCollaborationRequest(requestId));
  }

  @Test
  void deleteCollaborationRequest() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

    UUID requestId = dm.createCollaborationRequest(questId, PLAYER2);
    assertNotNull(dm.getCollaborationRequest(requestId));

    dm.deleteCollaborationRequest(requestId);
    assertNull(dm.getCollaborationRequest(requestId));
  }

  @Test
  void getPendingRequestCount() {
    UUID questId1 = UUID.randomUUID();
    UUID questId2 = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.upsertPlayerName(PLAYER3, "Carol");
    dm.saveQuest(makeQuest(questId1, OWNER, "Q1", Visibility.CLOSED));
    dm.saveQuest(makeQuest(questId2, OWNER, "Q2", Visibility.CLOSED));

    assertEquals(0, dm.getPendingRequestCount(OWNER));

    dm.createCollaborationRequest(questId1, PLAYER2);
    dm.createCollaborationRequest(questId1, PLAYER3);
    dm.createCollaborationRequest(questId2, PLAYER2);

    assertEquals(3, dm.getPendingRequestCount(OWNER));
  }

  @Test
  void deleteQuest_returnsFalseForNonexistent() {
    boolean deleted = dm.deleteQuest(UUID.randomUUID());
    assertFalse(deleted);
  }

  @Test
  void getQuest_returnsNullForNonexistent() {
    assertNull(dm.getQuest(UUID.randomUUID()));
  }

  @Test
  void saveQuest_upserts() {
    UUID id = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(id, OWNER, "Original", Visibility.PRIVATE));

    QuestData loaded = dm.getQuest(id);
    assertNotNull(loaded);
    assertEquals("Original", loaded.title());

    QuestData updated =
        new QuestData(
            id,
            "Updated",
            "new content",
            OWNER,
            null,
            Visibility.OPEN,
            List.of(),
            2000L,
            new CoordinatesData(5.0, 6.0, 7.0),
            false,
            null,
            "nether",
            List.of());
    dm.saveQuest(updated);

    QuestData reloaded = dm.getQuest(id);
    assertNotNull(reloaded);
    assertEquals("Updated", reloaded.title());
    assertEquals("new content", reloaded.content());
    assertEquals(Visibility.OPEN, reloaded.visibility());
    assertEquals(2000L, reloaded.lastModified());
    assertNotNull(reloaded.coordinates());
    assertEquals(5.0, reloaded.coordinates().x(), 1e-9);
    assertEquals("nether", reloaded.map());
  }

  @Test
  void getQuestsForPlayer_batchContributors() {
    UUID questId1 = UUID.randomUUID();
    UUID questId2 = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.upsertPlayerName(PLAYER2, "Bob");
    dm.upsertPlayerName(PLAYER3, "Carol");
    dm.saveQuest(makeQuest(questId1, OWNER, "Q1", Visibility.OPEN));
    dm.saveQuest(makeQuest(questId2, OWNER, "Q2", Visibility.OPEN));

    dm.addContributor(questId1, PLAYER2, false);
    dm.addContributor(questId2, PLAYER2, true);
    dm.addContributor(questId2, PLAYER3, false);

    List<QuestData> quests = dm.getQuestsForPlayer(OWNER);
    assertEquals(2, quests.size());

    QuestData q1 = quests.stream().filter(q -> q.id().equals(questId1)).findFirst().orElse(null);
    QuestData q2 = quests.stream().filter(q -> q.id().equals(questId2)).findFirst().orElse(null);
    assertNotNull(q1);
    assertNotNull(q2);

    assertEquals(1, q1.contributors().size());
    assertEquals(PLAYER2, q1.contributors().get(0).uuid());
    assertFalse(q1.contributors().get(0).canEdit());

    assertEquals(2, q2.contributors().size());
    List<UUID> q2Uuids = q2.contributors().stream().map(ContributorData::uuid).toList();
    assertTrue(q2Uuids.containsAll(List.of(PLAYER2, PLAYER3)));
  }

  @Test
  void deleteQuest_cascadesPin() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));
    dm.pinQuest(OWNER, questId);
    assertTrue(dm.isQuestPinned(OWNER, questId));

    dm.deleteQuest(questId);

    assertFalse(dm.isQuestPinned(OWNER, questId));
    assertTrue(dm.getPinnedQuestIds(OWNER).isEmpty());
  }
}
