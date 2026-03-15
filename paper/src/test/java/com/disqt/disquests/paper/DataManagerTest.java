package com.disqt.disquests.paper;

import com.disqt.disquests.common.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DataManagerTest {

    @TempDir
    Path tempDir;

    private DataManager dm;

    // Fixed UUIDs for deterministic tests
    private static final UUID OWNER   = UUID.fromString("00000000-0000-0000-0000-000000000001");
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
                "world"
        );
    }

    // -------------------------------------------------------------------------
    // Quests
    // -------------------------------------------------------------------------

    @Test
    void saveAndGetQuest() {
        UUID id = UUID.randomUUID();
        dm.upsertPlayerName(OWNER, "Alice");

        QuestData quest = new QuestData(
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
                "the_end"
        );
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

        QuestData quest = new QuestData(
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
                null
        );
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
        assertThrows(RuntimeException.class,
                () -> dm.createCollaborationRequest(questId, PLAYER2));

        // Only one request exists
        List<CollaborationRequestData> requests = dm.getPendingRequestsForOwner(OWNER);
        assertEquals(1, requests.size());
    }

    // -------------------------------------------------------------------------
    // Pins
    // -------------------------------------------------------------------------

    @Test
    void pinAndUnpinQuest() {
        UUID questId = UUID.randomUUID();
        dm.upsertPlayerName(OWNER, "Alice");
        dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));

        assertNull(dm.getPinnedQuestId(OWNER));

        dm.pinQuest(OWNER, questId);
        assertEquals(questId, dm.getPinnedQuestId(OWNER));

        dm.unpinQuest(OWNER);
        assertNull(dm.getPinnedQuestId(OWNER));
    }

    @Test
    void pinQuest_overwritesPrevious() {
        UUID questA = UUID.randomUUID();
        UUID questB = UUID.randomUUID();
        dm.upsertPlayerName(OWNER, "Alice");
        dm.saveQuest(makeQuest(questA, OWNER, "Quest A", Visibility.OPEN));
        dm.saveQuest(makeQuest(questB, OWNER, "Quest B", Visibility.OPEN));

        dm.pinQuest(OWNER, questA);
        assertEquals(questA, dm.getPinnedQuestId(OWNER));

        dm.pinQuest(OWNER, questB);
        assertEquals(questB, dm.getPinnedQuestId(OWNER));
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
    void deleteQuest_cascadesPin() {
        UUID questId = UUID.randomUUID();
        dm.upsertPlayerName(OWNER, "Alice");
        dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.OPEN));
        dm.pinQuest(OWNER, questId);
        assertEquals(questId, dm.getPinnedQuestId(OWNER));

        dm.deleteQuest(questId);

        assertNull(dm.getPinnedQuestId(OWNER));
    }
}
