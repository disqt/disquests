package com.disqt.disquests.common;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.common.model.CollaborationRequestData;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.ContributorOp;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PacketCodecTest {

  // ---- helpers ----

  private QuestData makeQuest(boolean withCoords, boolean isRegion, boolean withMap) {
    List<ContributorData> contributors =
        List.of(
            new ContributorData(UUID.randomUUID(), "alice", true),
            new ContributorData(UUID.randomUUID(), "bob", false));
    CoordinatesData coords = withCoords ? new CoordinatesData(100.5, 64.0, -200.25) : null;
    CoordinatesData coords2 =
        (withCoords && isRegion) ? new CoordinatesData(150.0, 70.0, -180.0) : null;
    String map = withMap ? "mirage" : null;
    return new QuestData(
        UUID.randomUUID(),
        "Find the Dragon",
        "Travel to the end and defeat it.",
        UUID.randomUUID(),
        "ownerPlayer",
        Visibility.OPEN,
        contributors,
        System.currentTimeMillis(),
        coords,
        isRegion,
        coords2,
        map,
        List.of());
  }

  private void assertQuestsEqual(QuestData expected, QuestData actual) {
    assertEquals(expected.id(), actual.id());
    assertEquals(expected.title(), actual.title());
    assertEquals(expected.content(), actual.content());
    assertEquals(expected.ownerUuid(), actual.ownerUuid());
    assertEquals(expected.ownerName(), actual.ownerName());
    assertEquals(expected.visibility(), actual.visibility());
    assertEquals(expected.lastModified(), actual.lastModified());
    assertEquals(expected.isRegion(), actual.isRegion());
    assertEquals(expected.map(), actual.map());

    assertEquals(expected.tags(), actual.tags());

    assertEquals(expected.contributors().size(), actual.contributors().size());
    for (int i = 0; i < expected.contributors().size(); i++) {
      ContributorData ec = expected.contributors().get(i);
      ContributorData ac = actual.contributors().get(i);
      assertEquals(ec.uuid(), ac.uuid());
      assertEquals(ec.name(), ac.name());
      assertEquals(ec.canEdit(), ac.canEdit());
    }

    if (expected.coordinates() == null) {
      assertNull(actual.coordinates());
    } else {
      assertNotNull(actual.coordinates());
      assertEquals(expected.coordinates().x(), actual.coordinates().x());
      assertEquals(expected.coordinates().y(), actual.coordinates().y());
      assertEquals(expected.coordinates().z(), actual.coordinates().z());
    }

    if (expected.coordinates2() == null) {
      assertNull(actual.coordinates2());
    } else {
      assertNotNull(actual.coordinates2());
      assertEquals(expected.coordinates2().x(), actual.coordinates2().x());
      assertEquals(expected.coordinates2().y(), actual.coordinates2().y());
      assertEquals(expected.coordinates2().z(), actual.coordinates2().z());
    }
  }

  // ---- 1. testHandshakeRoundTrip (multi-pin) ----

  @Test
  void testHandshakeRoundTrip() {
    List<UUID> pinnedIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    UUID playerUuid = UUID.randomUUID();
    byte[] packet =
        PacketCodec.writeHandshake("https://bluemap.disqt.com/", 3, pinnedIds, playerUuid);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);

    assertEquals("https://bluemap.disqt.com/", payload.bluemapUrl());
    assertEquals(3, payload.pendingRequestCount());
    assertEquals(pinnedIds, payload.pinnedQuestIds());
    assertEquals(playerUuid, payload.playerUuid());
    assertEquals(0, reader.remaining());
  }

  // ---- 2. testHandshakeEmptyPinnedList ----

  @Test
  void testHandshakeEmptyPinnedList() {
    UUID playerUuid = UUID.randomUUID();
    byte[] packet = PacketCodec.writeHandshake("", 0, List.of(), playerUuid);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);

    assertEquals("", payload.bluemapUrl());
    assertEquals(0, payload.pendingRequestCount());
    assertTrue(payload.pinnedQuestIds().isEmpty());
    assertEquals(playerUuid, payload.playerUuid());
    assertEquals(0, reader.remaining());
  }

  // ---- 3. testQuestRoundTrip ----

  @Test
  void testQuestRoundTrip() {
    QuestData quest = makeQuest(true, false, true);
    ByteBufWriter writer = new ByteBufWriter();
    PacketCodec.writeQuest(writer, quest);

    ByteBufReader reader = new ByteBufReader(writer.toByteArray());
    QuestData decoded = PacketCodec.readQuest(reader);

    assertQuestsEqual(quest, decoded);
    assertEquals(0, reader.remaining());
  }

  // ---- 4. testQuestWithRegion ----

  @Test
  void testQuestWithRegion() {
    QuestData quest = makeQuest(true, true, true);
    ByteBufWriter writer = new ByteBufWriter();
    PacketCodec.writeQuest(writer, quest);

    ByteBufReader reader = new ByteBufReader(writer.toByteArray());
    QuestData decoded = PacketCodec.readQuest(reader);

    assertQuestsEqual(quest, decoded);
    assertTrue(decoded.isRegion());
    assertNotNull(decoded.coordinates2());
    assertEquals(0, reader.remaining());
  }

  // ---- 5. testQuestNoOptionalFields ----

  @Test
  void testQuestNoOptionalFields() {
    QuestData quest =
        new QuestData(
            UUID.randomUUID(),
            "Simple Quest",
            "No coords or map.",
            UUID.randomUUID(),
            "player1",
            Visibility.PRIVATE,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of());
    ByteBufWriter writer = new ByteBufWriter();
    PacketCodec.writeQuest(writer, quest);

    ByteBufReader reader = new ByteBufReader(writer.toByteArray());
    QuestData decoded = PacketCodec.readQuest(reader);

    assertQuestsEqual(quest, decoded);
    assertNull(decoded.coordinates());
    assertNull(decoded.coordinates2());
    assertNull(decoded.map());
    assertEquals(0, reader.remaining());
  }

  // ---- 6. testSyncMyQuestsRoundTrip ----

  @Test
  void testSyncMyQuestsRoundTrip() {
    QuestData q1 = makeQuest(true, false, true);
    QuestData q2 = makeQuest(false, false, false);
    List<QuestData> quests = List.of(q1, q2);

    byte[] packet = PacketCodec.writeSyncMyQuests(quests);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.SYNC_MY_QUESTS, PacketCodec.readType(reader));
    List<QuestData> decoded = PacketCodec.readSyncMyQuests(reader);

    assertEquals(2, decoded.size());
    assertQuestsEqual(q1, decoded.get(0));
    assertQuestsEqual(q2, decoded.get(1));
    assertEquals(0, reader.remaining());
  }

  // ---- 7. testSaveQuestC2SRoundTrip ----

  @Test
  void testSaveQuestC2SRoundTrip() {
    UUID questId = UUID.randomUUID();
    CoordinatesData coords = new CoordinatesData(50.0, 63.0, 100.0);
    byte[] packet =
        PacketCodec.writeSaveQuest(
            questId,
            "Build a house",
            "Gather wood and build.",
            coords,
            false,
            null,
            "dust2",
            List.of());

    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.SAVE_QUEST, PacketCodec.readType(reader));
    PacketCodec.SaveQuestPayload payload = PacketCodec.readSaveQuest(reader);

    assertEquals(questId, payload.questId());
    assertEquals("Build a house", payload.title());
    assertEquals("Gather wood and build.", payload.content());
    assertNotNull(payload.coords());
    assertEquals(50.0, payload.coords().x());
    assertEquals(63.0, payload.coords().y());
    assertEquals(100.0, payload.coords().z());
    assertFalse(payload.isRegion());
    assertNull(payload.coords2());
    assertEquals("dust2", payload.map());
    assertEquals(0, reader.remaining());
  }

  // ---- 8. testDeleteQuestRoundTrip ----

  @Test
  void testDeleteQuestRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeDeleteQuest(questId);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.DELETE_QUEST, PacketCodec.readType(reader));
    UUID decoded = PacketCodec.readDeleteQuest(reader);

    assertEquals(questId, decoded);
    assertEquals(0, reader.remaining());
  }

  // ---- 9. testRespondCollaborationRoundTrip ----

  @Test
  void testRespondCollaborationRoundTrip() {
    UUID requestId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeRespondCollaboration(requestId, true);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.RESPOND_COLLABORATION, PacketCodec.readType(reader));
    PacketCodec.RespondCollaborationPayload payload = PacketCodec.readRespondCollaboration(reader);

    assertEquals(requestId, payload.requestId());
    assertTrue(payload.approved());
    assertEquals(0, reader.remaining());
  }

  // ---- 10. testUpdateVisibilityRoundTrip ----

  @Test
  void testUpdateVisibilityRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeUpdateVisibility(questId, Visibility.CLOSED);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.UPDATE_VISIBILITY, PacketCodec.readType(reader));
    PacketCodec.UpdateVisibilityPayload payload = PacketCodec.readUpdateVisibility(reader);

    assertEquals(questId, payload.questId());
    assertEquals(Visibility.CLOSED, payload.visibility());
    assertEquals(0, reader.remaining());
  }

  // ---- 11. testCollaborationRequestS2CRoundTrip ----

  @Test
  void testCollaborationRequestS2CRoundTrip() {
    UUID requestId = UUID.randomUUID();
    UUID questId = UUID.randomUUID();
    byte[] packet =
        PacketCodec.writeCollaborationRequest(requestId, questId, "Epic Quest", "requesterPlayer");

    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.COLLABORATION_REQUEST, PacketCodec.readType(reader));
    PacketCodec.CollaborationRequestPayload payload = PacketCodec.readCollaborationRequest(reader);

    assertEquals(requestId, payload.requestId());
    assertEquals(questId, payload.questId());
    assertEquals("Epic Quest", payload.questTitle());
    assertEquals("requesterPlayer", payload.requesterName());
    assertEquals(0, reader.remaining());
  }

  // ---- 12. testCollaborationResponseApproved ----

  @Test
  void testCollaborationResponseApproved() {
    UUID questId = UUID.randomUUID();
    QuestData quest = makeQuest(true, false, false);
    byte[] packet = PacketCodec.writeCollaborationResponse(questId, true, quest);

    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.COLLABORATION_RESPONSE, PacketCodec.readType(reader));
    PacketCodec.CollaborationResponsePayload payload =
        PacketCodec.readCollaborationResponse(reader);

    assertEquals(questId, payload.questId());
    assertTrue(payload.approved());
    assertNotNull(payload.quest());
    assertQuestsEqual(quest, payload.quest());
    assertEquals(0, reader.remaining());
  }

  // ---- 13. testCollaborationResponseDenied ----

  @Test
  void testCollaborationResponseDenied() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeCollaborationResponse(questId, false, null);

    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.COLLABORATION_RESPONSE, PacketCodec.readType(reader));
    PacketCodec.CollaborationResponsePayload payload =
        PacketCodec.readCollaborationResponse(reader);

    assertEquals(questId, payload.questId());
    assertFalse(payload.approved());
    assertNull(payload.quest());
    assertEquals(0, reader.remaining());
  }

  // ---- 14. testPinQuestRoundTrip ----

  @Test
  void testPinQuestRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writePinQuest(questId);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.PIN_QUEST, PacketCodec.readType(reader));
    UUID decoded = PacketCodec.readPinQuest(reader);

    assertEquals(questId, decoded);
    assertEquals(0, reader.remaining());
  }

  // ---- 15. testPinQuestUnpin ----

  @Test
  void testPinQuestUnpin() {
    byte[] packet = PacketCodec.writePinQuest(null);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.PIN_QUEST, PacketCodec.readType(reader));
    UUID decoded = PacketCodec.readPinQuest(reader);

    assertNull(decoded);
    assertEquals(0, reader.remaining());
  }

  // ---- bounds / error tests ----

  @Test
  void readQuest_invalidVisibilityOrdinal_throws() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeUUID(UUID.randomUUID());
    w.writeString("title");
    w.writeString("content");
    w.writeUUID(UUID.randomUUID());
    w.writeString("owner");
    w.writeVarInt(99); // invalid visibility ordinal
    w.writeVarInt(0);
    w.writeLong(1000L);
    w.writeBoolean(false);
    w.writeBoolean(false);
    w.writeBoolean(false);
    w.writeBoolean(false);
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertThrows(IllegalArgumentException.class, () -> PacketCodec.readQuest(r));
  }

  @Test
  void readUpdateContributors_invalidOpOrdinal_throws() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeUUID(UUID.randomUUID());
    w.writeVarInt(1);
    w.writeVarInt(99); // invalid ContributorOp ordinal
    w.writeBoolean(false);
    w.writeBoolean(false);
    w.writeBoolean(false);
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertThrows(IllegalArgumentException.class, () -> PacketCodec.readUpdateContributors(r));
  }

  @Test
  void readQuest_negativeContributorCount_throws() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeUUID(UUID.randomUUID());
    w.writeString("title");
    w.writeString("content");
    w.writeUUID(UUID.randomUUID());
    w.writeString("owner");
    w.writeVarInt(0);
    w.writeVarInt(-1); // negative
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertThrows(IllegalArgumentException.class, () -> PacketCodec.readQuest(r));
  }

  @Test
  void readType_unknownPacketId_throws() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeByte(0xFF);
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertThrows(IllegalArgumentException.class, () -> PacketCodec.readType(r));
  }

  @Test
  void testHandshakeNullBluemapUrl() {
    UUID playerUuid = UUID.randomUUID();
    byte[] packet = PacketCodec.writeHandshake(null, 0, List.of(), playerUuid);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);
    assertNull(payload.bluemapUrl());
    assertTrue(payload.pinnedQuestIds().isEmpty());
    assertEquals(playerUuid, payload.playerUuid());
    assertEquals(0, reader.remaining());
  }

  // ---- 16. testUpdateContributorsRoundTrip ----

  @Test
  void testUpdateContributorsRoundTrip() {
    UUID questId = UUID.randomUUID();
    UUID playerUuid1 = UUID.randomUUID();
    UUID playerUuid2 = UUID.randomUUID();

    List<PacketCodec.ContributorOpEntry> ops =
        List.of(
            new PacketCodec.ContributorOpEntry(ContributorOp.ADD, playerUuid1, "alice", true),
            new PacketCodec.ContributorOpEntry(ContributorOp.REMOVE, playerUuid2, "bob", false));

    byte[] packet = PacketCodec.writeUpdateContributors(questId, ops);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.UPDATE_CONTRIBUTORS, PacketCodec.readType(reader));
    PacketCodec.UpdateContributorsPayload payload = PacketCodec.readUpdateContributors(reader);

    assertEquals(questId, payload.questId());
    assertEquals(2, payload.ops().size());

    PacketCodec.ContributorOpEntry op1 = payload.ops().get(0);
    assertEquals(ContributorOp.ADD, op1.action());
    assertEquals(playerUuid1, op1.playerUuid());
    assertEquals("alice", op1.playerName());
    assertTrue(op1.canEdit());

    PacketCodec.ContributorOpEntry op2 = payload.ops().get(1);
    assertEquals(ContributorOp.REMOVE, op2.action());
    assertEquals(playerUuid2, op2.playerUuid());
    assertEquals("bob", op2.playerName());
    assertFalse(op2.canEdit());

    assertEquals(0, reader.remaining());
  }

  // ---- 17. testRequestSyncRoundTrip ----

  @Test
  void testRequestSyncRoundTrip() {
    byte[] packet = PacketCodec.writeRequestSync();
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.REQUEST_SYNC, PacketCodec.readType(reader));
    assertEquals(0, reader.remaining());
  }

  // ---- 18. testJoinQuestRoundTrip ----

  @Test
  void testJoinQuestRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeJoinQuest(questId);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.JOIN_QUEST, PacketCodec.readType(reader));
    UUID decoded = PacketCodec.readJoinQuest(reader);
    assertEquals(questId, decoded);
    assertEquals(0, reader.remaining());
  }

  // ---- 19. testRequestCollaborationRoundTrip ----

  @Test
  void testRequestCollaborationRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeRequestCollaboration(questId);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.REQUEST_COLLABORATION, PacketCodec.readType(reader));
    UUID decoded = PacketCodec.readRequestCollaboration(reader);
    assertEquals(questId, decoded);
    assertEquals(0, reader.remaining());
  }

  // ---- 20. testSyncServerQuestsRoundTrip ----

  @Test
  void testSyncServerQuestsRoundTrip() {
    QuestData q1 = makeQuest(false, false, false);
    List<QuestData> quests = List.of(q1);
    byte[] packet = PacketCodec.writeSyncServerQuests(quests);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_SERVER_QUESTS, PacketCodec.readType(reader));
    List<QuestData> decoded = PacketCodec.readSyncServerQuests(reader);
    assertEquals(1, decoded.size());
    assertQuestsEqual(q1, decoded.get(0));
    assertEquals(0, reader.remaining());
  }

  // ---- 21. testUpdateQuestRoundTrip ----

  @Test
  void testUpdateQuestRoundTrip() {
    QuestData quest = makeQuest(true, false, true);
    byte[] packet = PacketCodec.writeUpdateQuest(quest);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.UPDATE_QUEST, PacketCodec.readType(reader));
    QuestData decoded = PacketCodec.readUpdateQuest(reader);
    assertQuestsEqual(quest, decoded);
    assertEquals(0, reader.remaining());
  }

  // ---- 22. testDeleteQuestS2CRoundTrip ----

  @Test
  void testDeleteQuestS2CRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeDeleteQuestS2C(questId);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.DELETE_QUEST_S2C, PacketCodec.readType(reader));
    UUID decoded = PacketCodec.readDeleteQuestS2C(reader);
    assertEquals(questId, decoded);
    assertEquals(0, reader.remaining());
  }

  // ---- 23. testUpdateContributorsWithUpdateOp ----

  @Test
  void testUpdateContributorsWithUpdateOp() {
    UUID questId = UUID.randomUUID();
    UUID playerUuid = UUID.randomUUID();
    List<PacketCodec.ContributorOpEntry> ops =
        List.of(new PacketCodec.ContributorOpEntry(ContributorOp.UPDATE, playerUuid, null, true));
    byte[] packet = PacketCodec.writeUpdateContributors(questId, ops);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.UPDATE_CONTRIBUTORS, PacketCodec.readType(reader));
    PacketCodec.UpdateContributorsPayload payload = PacketCodec.readUpdateContributors(reader);
    assertEquals(ContributorOp.UPDATE, payload.ops().get(0).action());
    assertEquals(0, reader.remaining());
  }

  // ---- 24. testQuestWithClosedVisibility ----

  @Test
  void testQuestWithClosedVisibility() {
    QuestData quest =
        new QuestData(
            UUID.randomUUID(),
            "Closed Quest",
            "Only collaborators.",
            UUID.randomUUID(),
            "owner",
            Visibility.CLOSED,
            List.of(new ContributorData(UUID.randomUUID(), "helper", true)),
            1000L,
            null,
            false,
            null,
            null,
            List.of());
    ByteBufWriter w = new ByteBufWriter();
    PacketCodec.writeQuest(w, quest);
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    QuestData decoded = PacketCodec.readQuest(r);
    assertEquals(Visibility.CLOSED, decoded.visibility());
    assertQuestsEqual(quest, decoded);
  }

  // ---- 25. testUnicodeStringRoundTrip ----

  @Test
  void testUnicodeStringRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet =
        PacketCodec.writeSaveQuest(
            questId,
            "Quest: \u9F8D Dragon",
            "Description with unicode: \u00E9\u00E8\u00EA and CJK: \u4E16\u754C",
            null,
            false,
            null,
            null,
            List.of());
    ByteBufReader reader = new ByteBufReader(packet);
    PacketCodec.readType(reader);
    PacketCodec.SaveQuestPayload payload = PacketCodec.readSaveQuest(reader);
    assertEquals("Quest: \u9F8D Dragon", payload.title());
    assertEquals(
        "Description with unicode: \u00E9\u00E8\u00EA and CJK: \u4E16\u754C", payload.content());
  }

  // ---- 26. testEmptySyncMyQuests ----

  @Test
  void testEmptySyncMyQuests() {
    byte[] packet = PacketCodec.writeSyncMyQuests(List.of());
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_MY_QUESTS, PacketCodec.readType(reader));
    List<QuestData> decoded = PacketCodec.readSyncMyQuests(reader);
    assertTrue(decoded.isEmpty());
    assertEquals(0, reader.remaining());
  }

  // ---- 27. testLeaveQuestRoundTrip ----

  @Test
  void testHandshakeWithMapMappings() {
    List<UUID> pinnedIds = List.of(UUID.randomUUID());
    UUID playerUuid = UUID.randomUUID();
    Map<String, String> mapNames = Map.of("overworld", "world_new", "nether", "world_new_nether");

    byte[] packet =
        PacketCodec.writeHandshake("https://example.com", 1, pinnedIds, playerUuid, mapNames);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);
    assertEquals("https://example.com", payload.bluemapUrl());
    assertEquals(mapNames, payload.bluemapMapNames());
    assertEquals(0, reader.remaining());
  }

  @Test
  void testHandshakeEmptyMapMappings() {
    List<UUID> pinnedIds = List.of();
    UUID playerUuid = UUID.randomUUID();
    Map<String, String> mapNames = Map.of();

    byte[] packet = PacketCodec.writeHandshake("", 0, pinnedIds, playerUuid, mapNames);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);
    assertTrue(payload.bluemapMapNames().isEmpty());
    assertEquals(0, reader.remaining());
  }

  @Test
  void testLeaveQuestRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeLeaveQuest(questId);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.LEAVE_QUEST, PacketCodec.readType(reader));
    UUID readId = PacketCodec.readLeaveQuest(reader);
    assertEquals(questId, readId);
    assertEquals(0, reader.remaining());
  }

  // ---- 28. testSyncMyQuestsWithPendingCounts ----

  @Test
  void testSyncMyQuestsWithPendingCounts() {
    UUID questId = UUID.randomUUID();
    QuestData quest =
        new QuestData(
            questId,
            "Test",
            "content",
            UUID.randomUUID(),
            "Owner",
            Visibility.OPEN,
            List.of(),
            System.currentTimeMillis(),
            null,
            false,
            null,
            null,
            List.of());
    Map<UUID, Integer> pendingCounts = Map.of(questId, 3);

    byte[] packet = PacketCodec.writeSyncMyQuests(List.of(quest), pendingCounts);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_MY_QUESTS, PacketCodec.readType(reader));
    List<QuestData> readQuests = PacketCodec.readSyncMyQuests(reader);
    assertEquals(1, readQuests.size());
    Map<UUID, Integer> readCounts = PacketCodec.readPendingCounts(reader);
    assertEquals(3, readCounts.get(questId));
    assertEquals(0, reader.remaining());
  }

  // ---- 29. testSyncPendingRequestsRoundTrip ----

  @Test
  void testSyncPendingRequestsRoundTrip() {
    CollaborationRequestData req =
        new CollaborationRequestData(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Test Quest",
            UUID.randomUUID(),
            "PlayerName",
            1234567890L);

    byte[] packet = PacketCodec.writeSyncPendingRequests(List.of(req));
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_PENDING_REQUESTS, PacketCodec.readType(reader));
    List<CollaborationRequestData> readReqs = PacketCodec.readSyncPendingRequests(reader);
    assertEquals(1, readReqs.size());
    assertEquals(req.id(), readReqs.get(0).id());
    assertEquals(req.questId(), readReqs.get(0).questId());
    assertEquals(req.questTitle(), readReqs.get(0).questTitle());
    assertEquals(req.requesterName(), readReqs.get(0).requesterName());
    assertEquals(req.timestamp(), readReqs.get(0).timestamp());
    assertEquals(0, reader.remaining());
  }

  // ---- 30. testQuestTagsRoundTrip ----

  @Test
  void testQuestTagsRoundTrip() {
    List<String> tags = List.of("combat", "exploration", "daily");
    QuestData quest =
        new QuestData(
            UUID.randomUUID(),
            "Tagged Quest",
            "A quest with tags.",
            UUID.randomUUID(),
            "ownerPlayer",
            Visibility.OPEN,
            List.of(),
            System.currentTimeMillis(),
            null,
            false,
            null,
            null,
            tags);
    ByteBufWriter writer = new ByteBufWriter();
    PacketCodec.writeQuest(writer, quest);

    ByteBufReader reader = new ByteBufReader(writer.toByteArray());
    QuestData decoded = PacketCodec.readQuest(reader);

    assertQuestsEqual(quest, decoded);
    assertEquals(tags, decoded.tags());
    assertEquals(0, reader.remaining());
  }

  @Test
  void testQuestEmptyTagsRoundTrip() {
    QuestData quest =
        new QuestData(
            UUID.randomUUID(),
            "No Tags Quest",
            "A quest with no tags.",
            UUID.randomUUID(),
            "ownerPlayer",
            Visibility.PRIVATE,
            List.of(),
            System.currentTimeMillis(),
            null,
            false,
            null,
            null,
            List.of());
    ByteBufWriter writer = new ByteBufWriter();
    PacketCodec.writeQuest(writer, quest);

    ByteBufReader reader = new ByteBufReader(writer.toByteArray());
    QuestData decoded = PacketCodec.readQuest(reader);

    assertQuestsEqual(quest, decoded);
    assertTrue(decoded.tags().isEmpty());
    assertEquals(0, reader.remaining());
  }

  // ---- 31. testSaveQuestTagsRoundTrip ----

  @Test
  void testSaveQuestTagsRoundTrip() {
    UUID questId = UUID.randomUUID();
    List<String> tags = List.of("pvp", "hardcore");
    byte[] packet =
        PacketCodec.writeSaveQuest(
            questId, "PvP Quest", "Fight to win.", null, false, null, "mirage", tags);

    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SAVE_QUEST, PacketCodec.readType(reader));
    PacketCodec.SaveQuestPayload payload = PacketCodec.readSaveQuest(reader);

    assertEquals(questId, payload.questId());
    assertEquals("PvP Quest", payload.title());
    assertEquals(tags, payload.tags());
    assertEquals(0, reader.remaining());
  }

  // ---- 32. testHandshakePredefinedTagsRoundTrip ----

  @Test
  void testHandshakePredefinedTagsRoundTrip() {
    List<UUID> pinnedIds = List.of(UUID.randomUUID());
    UUID playerUuid = UUID.randomUUID();
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    List<String> predefinedTags = List.of("combat", "exploration", "crafting");

    byte[] packet =
        PacketCodec.writeHandshake(
            "https://bluemap.example.com", 1, pinnedIds, playerUuid, mapNames, predefinedTags);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);

    assertEquals("https://bluemap.example.com", payload.bluemapUrl());
    assertEquals(predefinedTags, payload.predefinedTags());
    assertEquals(0, reader.remaining());
  }

  @Test
  void testHandshakePredefinedTagsEmptyViaConvenienceOverload() {
    UUID playerUuid = UUID.randomUUID();
    byte[] packet = PacketCodec.writeHandshake(null, 0, List.of(), playerUuid);
    ByteBufReader reader = new ByteBufReader(packet);

    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);

    assertTrue(payload.predefinedTags().isEmpty());
    assertEquals(0, reader.remaining());
  }
}
