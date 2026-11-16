package com.disqt.disquests.server.papermc;

import static org.mockito.Mockito.*;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.PacketType;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.QuestView;
import com.disqt.disquests.common.model.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ServerPacketHandlerTest {

  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CONTRIBUTOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID BYSTANDER = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final Map<UUID, String> NAMES =
      Map.of(OWNER, "Owner", CONTRIBUTOR, "Contributor", BYSTANDER, "Bystander");

  @TempDir Path tempDir;

  private DataManager dm;
  private ServerPacketHandler handler;
  private FakeOutbox outbox;
  private Config config;

  @BeforeEach
  void setUp() {
    dm = new DataManager(tempDir);
    dm.initialize();

    DisquestsPlugin plugin = mock(DisquestsPlugin.class);
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
    config = mock(Config.class);
    when(config.getBluemapUrl()).thenReturn("");
    when(config.getPredefinedTags()).thenReturn(List.of());
    when(config.getBluemapDefaultMap()).thenReturn("overworld");
    when(config.isPlayerMarkerEnabled()).thenReturn(true);
    when(config.isWorldMapEnabled()).thenReturn(true);
    when(config.getMaxQuestDepth()).thenReturn(3);

    outbox = new FakeOutbox();
    for (UUID player : List.of(OWNER, CONTRIBUTOR, BYSTANDER)) {
      outbox.join(player, NAMES.get(player), ProtocolVersion.V0);
    }
    handler = new ServerPacketHandler(plugin, dm, config, outbox);
  }

  @AfterEach
  void tearDown() {
    dm.close();
  }

  private void receive(UUID player, byte[] packet) {
    handler.receive(player, NAMES.get(player), packet);
  }

  /** The player's client syncs at {@code version}. */
  private void register(UUID player, int version) {
    outbox.join(player, NAMES.get(player), version);
    receive(player, PacketCodec.writeRequestSync(version));
  }

  private void assertNothingSentTo(UUID... players) {
    for (UUID player : players) {
      Assertions.assertEquals(List.of(), outbox.packetsFor(player), "packets for " + player);
    }
  }

  // ---- saves ----

  @Test
  void saveNewQuest_sendsQuestAndTagsOnlyToItsOwner() {
    dm.upsertPlayerName(OWNER, "Owner");
    dm.upsertPlayerName(BYSTANDER, "Bystander");
    register(OWNER, ProtocolVersion.V1);
    register(BYSTANDER, ProtocolVersion.V1);
    outbox.clear();

    UUID questId = UUID.randomUUID();
    receive(
        OWNER,
        PacketCodec.writeSaveQuest(
            questId, "Tagged Quest", "Content", null, false, null, null, List.of("newtag")));

    Assertions.assertEquals(
        List.of(questId), outbox.updatesFor(OWNER).stream().map(QuestData::id).toList());
    Assertions.assertEquals(List.of(List.of("newtag")), outbox.tagsFor(OWNER));
    // The quest is Private: its tag is no suggestion for anyone else.
    assertNothingSentTo(BYSTANDER, CONTRIBUTOR);
  }

  @Test
  void saveExistingQuest_reachesEveryViewer() {
    UUID questId = seedQuest(Visibility.OPEN);
    dm.addContributor(questId, CONTRIBUTOR, true);
    for (UUID player : List.of(OWNER, CONTRIBUTOR, BYSTANDER)) register(player, ProtocolVersion.V4);
    outbox.clear();

    receive(
        OWNER,
        PacketCodec.writeSaveQuest(
            questId, "Updated Title", "Updated content", null, false, null, null, List.of()));

    QuestGraph after = dm.loadGraph();
    for (UUID player : List.of(OWNER, CONTRIBUTOR, BYSTANDER)) {
      Assertions.assertEquals(
          List.of(QuestView.forRecipient(after, player, questId)),
          outbox.updatesFor(player),
          "update for " + player);
      Assertions.assertEquals(List.of(), outbox.deletesFor(player));
    }
    Assertions.assertEquals("Updated Title", outbox.updatesFor(BYSTANDER).get(0).title());
  }

  @Test
  void saveByViewOnlyContributor_isIgnored() {
    UUID questId = seedQuest(Visibility.OPEN);
    dm.addContributor(questId, CONTRIBUTOR, false);
    register(OWNER, ProtocolVersion.V4);
    outbox.clear();

    receive(
        CONTRIBUTOR,
        PacketCodec.writeSaveQuest(questId, "Hijack", "", null, false, null, null, List.of()));

    Assertions.assertEquals("Gare", dm.getQuest(questId).title());
    assertNothingSentTo(OWNER, CONTRIBUTOR, BYSTANDER);
  }

  // ---- V2 polygons ----

  private static final List<PointData> TRIANGLE =
      List.of(new PointData(0, 0), new PointData(10, 0), new PointData(0, 10));

  private void send(
      UUID player,
      UUID id,
      CoordinatesData c1,
      boolean region,
      CoordinatesData c2,
      String map,
      List<PointData> polygon) {
    receive(
        player, PacketCodec.writeSaveQuest(id, "T", "C", c1, region, c2, map, List.of(), polygon));
  }

  private void seedTriangle(UUID id) {
    dm.upsertPlayerName(OWNER, "Owner");
    dm.saveQuest(
        new QuestData(
            id,
            "T",
            "C",
            OWNER,
            "Owner",
            Visibility.PRIVATE,
            List.of(),
            1000L,
            null,
            false,
            null,
            "overworld",
            List.of(),
            TRIANGLE));
  }

  @Test
  void saveQuest_v2_persistsPolygon() {
    dm.upsertPlayerName(OWNER, "Owner");
    register(OWNER, ProtocolVersion.V2);
    UUID id = UUID.randomUUID();
    send(OWNER, id, null, false, null, "overworld", TRIANGLE);
    Assertions.assertEquals(3, dm.getQuest(id).polygon().size());
  }

  @Test
  void saveQuest_v1Rectangle_becomesPolygon() {
    dm.upsertPlayerName(OWNER, "Owner");
    register(OWNER, ProtocolVersion.V1);
    UUID id = UUID.randomUUID();
    send(
        OWNER,
        id,
        new CoordinatesData(0, 64, 0),
        true,
        new CoordinatesData(10, 64, 10),
        "overworld",
        List.of());
    Assertions.assertEquals(4, dm.getQuest(id).polygon().size());
  }

  @Test
  void saveQuest_v1UnchangedRectangle_keepsExistingPolygon() {
    UUID id = UUID.randomUUID();
    seedTriangle(id);
    register(OWNER, ProtocolVersion.V1);
    send(
        OWNER,
        id,
        new CoordinatesData(0, 70, 0),
        true,
        new CoordinatesData(10, 70, 10),
        "overworld",
        List.of());
    Assertions.assertEquals(TRIANGLE, dm.getQuest(id).polygon());
  }

  @Test
  void saveQuest_v1ChangedRectangle_replacesPolygon() {
    UUID id = UUID.randomUUID();
    seedTriangle(id);
    register(OWNER, ProtocolVersion.V1);
    send(
        OWNER,
        id,
        new CoordinatesData(0, 70, 0),
        true,
        new CoordinatesData(20, 70, 20),
        "overworld",
        List.of());
    Assertions.assertEquals(4, dm.getQuest(id).polygon().size());
  }

  @Test
  void saveQuest_rejectsInvalidPolygon() {
    dm.upsertPlayerName(OWNER, "Owner");
    register(OWNER, ProtocolVersion.V2);
    UUID tooMany = UUID.randomUUID();
    List<PointData> big = new ArrayList<>();
    for (int i = 0; i < QuestLocation.MAX_VERTICES + 1; i++) big.add(new PointData(i, i * i));
    send(OWNER, tooMany, null, false, null, "overworld", big);
    Assertions.assertNull(dm.getQuest(tooMany));
    UUID nan = UUID.randomUUID();
    send(
        OWNER,
        nan,
        null,
        false,
        null,
        "overworld",
        List.of(new PointData(Double.NaN, 0), new PointData(1, 1), new PointData(2, 0)));
    Assertions.assertNull(dm.getQuest(nan));
  }

  @Test
  void saveQuest_normalisesNetherMap() {
    dm.upsertPlayerName(OWNER, "Owner");
    register(OWNER, ProtocolVersion.V2);
    UUID id = UUID.randomUUID();
    send(OWNER, id, new CoordinatesData(1, 2, 3), false, null, "nether", List.of());
    Assertions.assertEquals("the_nether", dm.getQuest(id).map());
  }

  @Test
  void updateQuest_toV1Recipient_isWrittenInV1Form() {
    dm.upsertPlayerName(OWNER, "Owner");
    dm.upsertPlayerName(CONTRIBUTOR, "Contributor");
    register(OWNER, ProtocolVersion.V2);
    register(CONTRIBUTOR, ProtocolVersion.V1);
    UUID id = UUID.randomUUID();
    send(OWNER, id, null, false, null, "overworld", TRIANGLE);
    dm.addContributor(id, CONTRIBUTOR, true);
    outbox.clear();

    receive(
        OWNER,
        PacketCodec.writeSaveQuest(
            id, "T2", "C", null, false, null, "overworld", List.of(), TRIANGLE));

    List<QuestData> updates = outbox.updatesFor(CONTRIBUTOR);
    Assertions.assertEquals(1, updates.size());
    QuestData q = updates.get(0);
    Assertions.assertTrue(q.isRegion());
    Assertions.assertEquals(0, q.coordinates().x());
    Assertions.assertEquals(0, q.coordinates().z());
    Assertions.assertEquals(10, q.coordinates2().x());
    Assertions.assertEquals(10, q.coordinates2().z());
    Assertions.assertEquals(List.of(), q.polygon());
  }

  // ---- handshake ----

  private PacketCodec.HandshakePayload resendAndReadHandshake() {
    outbox.clear();
    handler.resendHandshakes();
    List<PacketCodec.HandshakePayload> handshakes = outbox.handshakesFor(OWNER);
    Assertions.assertEquals(1, handshakes.size());
    return handshakes.get(0);
  }

  @Test
  void handshake_includesServerVersionAndMaxDepth() {
    when(config.getMaxQuestDepth()).thenReturn(0);
    PacketCodec.HandshakePayload handshake = resendAndReadHandshake();
    Assertions.assertEquals(ProtocolVersion.CURRENT, handshake.serverProtocolVersion());
    Assertions.assertEquals(0, handshake.maxQuestDepth());
  }

  @Test
  void handshake_carriesConfiguredWorldMapFlag() {
    when(config.isWorldMapEnabled()).thenReturn(false);
    Assertions.assertFalse(resendAndReadHandshake().worldMapEnabled());
    when(config.isWorldMapEnabled()).thenReturn(true);
    Assertions.assertTrue(resendAndReadHandshake().worldMapEnabled());
  }

  @Test
  void handshake_carriesConfiguredPlayerMarkerFlag() {
    when(config.isPlayerMarkerEnabled()).thenReturn(false);
    PacketCodec.HandshakePayload off = resendAndReadHandshake();
    Assertions.assertFalse(off.playerMarkerEnabled());
    Assertions.assertTrue(off.worldMapEnabled());
    when(config.isPlayerMarkerEnabled()).thenReturn(true);
    Assertions.assertTrue(resendAndReadHandshake().playerMarkerEnabled());
  }

  @Test
  void handshake_reachesEveryOnlineModPlayer() {
    handler.resendHandshakes();
    for (UUID player : List.of(OWNER, CONTRIBUTOR, BYSTANDER)) {
      Assertions.assertEquals(1, outbox.handshakesFor(player).size(), "handshake for " + player);
    }
  }

  // ---- V3 quest completion ----

  private UUID seedQuest(Visibility visibility) {
    dm.upsertPlayerName(OWNER, "Owner");
    dm.upsertPlayerName(CONTRIBUTOR, "Contributor");
    dm.upsertPlayerName(BYSTANDER, "Bystander");
    UUID id = UUID.randomUUID();
    dm.saveQuest(
        new QuestData(
            id,
            "Gare",
            "Content",
            OWNER,
            "Owner",
            visibility,
            List.of(),
            1000L,
            null,
            false,
            null,
            "overworld",
            List.of()));
    return id;
  }

  private QuestData onlyUpdate(UUID player) {
    List<QuestData> updates = outbox.updatesFor(player);
    Assertions.assertEquals(1, updates.size(), "expected exactly one UPDATE_QUEST");
    return updates.get(0);
  }

  private void complete(UUID player, UUID id) {
    receive(player, PacketCodec.writeCompleteQuest(id));
  }

  private void reopen(UUID player, UUID id) {
    receive(player, PacketCodec.writeReopenQuest(id));
  }

  @Test
  void complete_byOwner_marksCompleted_andUpdatesV3Owner() {
    UUID id = seedQuest(Visibility.PRIVATE);
    register(OWNER, ProtocolVersion.V3);
    outbox.clear();

    complete(OWNER, id);

    QuestData stored = dm.getQuest(id);
    Assertions.assertTrue(stored.isCompleted());
    Assertions.assertEquals(OWNER, stored.completedBy());
    QuestData sent = onlyUpdate(OWNER);
    Assertions.assertTrue(sent.isCompleted());
    Assertions.assertEquals(OWNER, sent.completedBy());
    Assertions.assertEquals("Owner", sent.completedByName());
  }

  @Test
  void complete_byViewOnlyContributor_isRefused() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.addContributor(id, CONTRIBUTOR, false);
    register(CONTRIBUTOR, ProtocolVersion.V3);
    outbox.clear();

    complete(CONTRIBUTOR, id);

    Assertions.assertFalse(dm.getQuest(id).isCompleted());
    assertNothingSentTo(OWNER, CONTRIBUTOR, BYSTANDER);
  }

  @Test
  void complete_byEditContributor_isAccepted() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.addContributor(id, CONTRIBUTOR, true);
    complete(CONTRIBUTOR, id);
    QuestData stored = dm.getQuest(id);
    Assertions.assertTrue(stored.isCompleted());
    Assertions.assertEquals(CONTRIBUTOR, stored.completedBy());
  }

  @Test
  void complete_sendsDeleteToV2ViewersAndToPlayersWithoutARole() {
    UUID id = seedQuest(Visibility.OPEN);
    register(OWNER, ProtocolVersion.V3);
    register(BYSTANDER, ProtocolVersion.V2);
    register(CONTRIBUTOR, ProtocolVersion.V3); // not a participant
    outbox.clear();

    complete(OWNER, id);

    Assertions.assertEquals(List.of(id), outbox.deletesFor(BYSTANDER));
    Assertions.assertEquals(List.of(), outbox.updatesFor(BYSTANDER));
    Assertions.assertEquals(List.of(id), outbox.deletesFor(CONTRIBUTOR));
    Assertions.assertEquals(List.of(), outbox.updatesFor(CONTRIBUTOR));
    Assertions.assertTrue(onlyUpdate(OWNER).isCompleted());
    Assertions.assertEquals(List.of(), outbox.deletesFor(OWNER));
  }

  @Test
  void complete_sendsDeleteToV2Contributor() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.addContributor(id, CONTRIBUTOR, true);
    register(OWNER, ProtocolVersion.V3);
    register(CONTRIBUTOR, ProtocolVersion.V2);
    outbox.clear();

    complete(OWNER, id);

    Assertions.assertEquals(List.of(id), outbox.deletesFor(CONTRIBUTOR));
    Assertions.assertEquals(List.of(), outbox.updatesFor(CONTRIBUTOR));
    // Private quest: a non-participant never saw it and gets nothing
    assertNothingSentTo(BYSTANDER);
  }

  @Test
  void complete_removesPins_andResendsOwnerHandshakeWhenRequestsDropped() {
    UUID id = seedQuest(Visibility.CLOSED);
    dm.pinQuest(BYSTANDER, id);
    dm.createCollaborationRequest(id, BYSTANDER);
    register(OWNER, ProtocolVersion.V3);
    outbox.clear();

    complete(OWNER, id);

    Assertions.assertFalse(dm.isQuestPinned(BYSTANDER, id));
    Assertions.assertEquals(0, dm.getPendingRequestCount(OWNER));
    List<PacketCodec.HandshakePayload> handshakes = outbox.handshakesFor(OWNER);
    Assertions.assertEquals(1, handshakes.size());
    Assertions.assertEquals(0, handshakes.get(0).pendingRequestCount());
  }

  @Test
  void complete_withoutPendingRequests_sendsNoHandshake() {
    UUID id = seedQuest(Visibility.CLOSED);
    register(OWNER, ProtocolVersion.V3);
    outbox.clear();
    complete(OWNER, id);
    Assertions.assertEquals(List.of(), outbox.handshakesFor(OWNER));
  }

  @Test
  void reopen_byOwner_restoresAndBroadcasts() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.completeQuest(id, OWNER, 5000L);
    register(OWNER, ProtocolVersion.V3);
    register(BYSTANDER, ProtocolVersion.V3);
    register(CONTRIBUTOR, ProtocolVersion.V3);
    outbox.clear();

    reopen(OWNER, id);

    Assertions.assertFalse(dm.getQuest(id).isCompleted());
    Assertions.assertFalse(onlyUpdate(OWNER).isCompleted());
    QuestData toBystander = onlyUpdate(BYSTANDER);
    Assertions.assertEquals(id, toBystander.id());
    Assertions.assertFalse(toBystander.isCompleted());
    Assertions.assertFalse(onlyUpdate(CONTRIBUTOR).isCompleted());
  }

  @Test
  void reopen_ofPrivateQuest_notifiesOnlyParticipants() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.addContributor(id, CONTRIBUTOR, true);
    dm.completeQuest(id, OWNER, 5000L);
    register(OWNER, ProtocolVersion.V3);
    register(CONTRIBUTOR, ProtocolVersion.V3);
    register(BYSTANDER, ProtocolVersion.V3);
    outbox.clear();

    reopen(OWNER, id);

    Assertions.assertFalse(dm.getQuest(id).isCompleted());
    Assertions.assertFalse(onlyUpdate(OWNER).isCompleted());
    Assertions.assertFalse(onlyUpdate(CONTRIBUTOR).isCompleted());
    assertNothingSentTo(BYSTANDER);
  }

  @Test
  void reopen_byDemotedContributor_isRefused() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.addContributor(id, CONTRIBUTOR, true);
    dm.completeQuest(id, CONTRIBUTOR, 5000L);
    dm.updateContributor(id, CONTRIBUTOR, false);

    reopen(CONTRIBUTOR, id);

    Assertions.assertTrue(dm.getQuest(id).isCompleted());
    assertNothingSentTo(OWNER, BYSTANDER);
  }

  @Test
  void saveOnCompletedQuest_isIgnored() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.completeQuest(id, OWNER, 5000L);

    receive(
        OWNER,
        PacketCodec.writeSaveQuest(id, "New title", "New", null, false, null, null, List.of()));

    QuestData stored = dm.getQuest(id);
    Assertions.assertEquals("Gare", stored.title());
    Assertions.assertTrue(stored.isCompleted());
    assertNothingSentTo(OWNER);
  }

  @Test
  void joinCompletedQuest_isIgnored() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.completeQuest(id, OWNER, 5000L);

    receive(BYSTANDER, PacketCodec.writeJoinQuest(id));

    Assertions.assertFalse(dm.isContributor(id, BYSTANDER));
    assertNothingSentTo(BYSTANDER);
  }

  @Test
  void leaveCompletedQuest_isIgnored() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.addContributor(id, CONTRIBUTOR, true);
    dm.completeQuest(id, OWNER, 5000L);

    receive(CONTRIBUTOR, PacketCodec.writeLeaveQuest(id));

    Assertions.assertTrue(dm.isContributor(id, CONTRIBUTOR));
    assertNothingSentTo(CONTRIBUTOR);
  }

  @Test
  void contributorsVisibilityAndRequestOnCompletedQuest_areIgnored() {
    UUID id = seedQuest(Visibility.CLOSED);
    dm.completeQuest(id, OWNER, 5000L);

    receive(OWNER, PacketCodec.writeUpdateVisibility(id, Visibility.OPEN));
    receive(BYSTANDER, PacketCodec.writeRequestCollaboration(id));

    Assertions.assertEquals(Visibility.CLOSED, dm.getQuest(id).visibility());
    Assertions.assertEquals(0, dm.getPendingRequestCount(OWNER));
  }

  @Test
  void deleteCompletedQuest_byOwner_isAccepted() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.completeQuest(id, OWNER, 5000L);
    receive(OWNER, PacketCodec.writeDeleteQuest(id));
    Assertions.assertNull(dm.getQuest(id));
  }

  // ---- sync ----

  private List<QuestData> syncList(UUID player, int version, PacketType type) {
    outbox.clear();
    register(player, version);
    if (type == PacketType.SYNC_MY_QUESTS) {
      List<FakeOutbox.MyQuests> syncs = outbox.myQuestsFor(player);
      Assertions.assertEquals(1, syncs.size());
      return syncs.get(0).quests();
    }
    List<List<QuestData>> syncs = outbox.serverQuestsFor(player);
    Assertions.assertEquals(1, syncs.size());
    return syncs.get(0);
  }

  @Test
  void sync_v2_excludesCompleted_v3_includesForParticipants() {
    UUID done = seedQuest(Visibility.OPEN);
    dm.addContributor(done, CONTRIBUTOR, false);
    dm.completeQuest(done, OWNER, 5000L);
    UUID active = UUID.randomUUID();
    dm.saveQuest(
        new QuestData(
            active,
            "Active",
            "C",
            OWNER,
            "Owner",
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            "overworld",
            List.of()));

    List<UUID> ownerV2 =
        syncList(OWNER, ProtocolVersion.V2, PacketType.SYNC_MY_QUESTS).stream()
            .map(QuestData::id)
            .toList();
    Assertions.assertEquals(List.of(active), ownerV2);

    List<QuestData> ownerV3 = syncList(OWNER, ProtocolVersion.V3, PacketType.SYNC_MY_QUESTS);
    Assertions.assertEquals(
        Set.of(done, active), new HashSet<>(ownerV3.stream().map(QuestData::id).toList()));
    Assertions.assertTrue(
        ownerV3.stream().filter(q -> q.id().equals(done)).findFirst().orElseThrow().isCompleted());

    List<UUID> contribV2 =
        syncList(CONTRIBUTOR, ProtocolVersion.V2, PacketType.SYNC_MY_QUESTS).stream()
            .map(QuestData::id)
            .toList();
    Assertions.assertEquals(List.of(), contribV2);

    List<UUID> contribV3 =
        syncList(CONTRIBUTOR, ProtocolVersion.V3, PacketType.SYNC_MY_QUESTS).stream()
            .map(QuestData::id)
            .toList();
    Assertions.assertEquals(List.of(done), contribV3);

    List<UUID> bystanderBoard =
        syncList(BYSTANDER, ProtocolVersion.V3, PacketType.SYNC_SERVER_QUESTS).stream()
            .map(QuestData::id)
            .toList();
    Assertions.assertEquals(List.of(active), bystanderBoard);
  }

  @Test
  void sync_sendsPendingRequestsToTheOwner() {
    UUID id = seedQuest(Visibility.CLOSED);
    dm.createCollaborationRequest(id, BYSTANDER);

    register(OWNER, ProtocolVersion.V4);

    Assertions.assertEquals(Map.of(id, 1), outbox.myQuestsFor(OWNER).get(0).pendingCounts());
    List<FakeOutbox.Packet> pending =
        outbox.packetsFor(OWNER).stream()
            .filter(p -> p.type() == PacketType.SYNC_PENDING_REQUESTS)
            .toList();
    Assertions.assertEquals(1, pending.size());
    Assertions.assertEquals(1, ((List<?>) pending.get(0).payload()).size());
  }

  @Test
  void wikiLink_toCompletedQuest_stillResolves() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.completeQuest(id, OWNER, 5000L);
    WikiLinkResolver resolver = new WikiLinkResolver(dm);
    Assertions.assertEquals(
        "See [[" + id + "|Gare]]",
        resolver.resolveForRecipient(dm.loadGraph(), "See [[Gare]]", BYSTANDER));
  }

  // ---- pins ----

  @Test
  void pin_ofAClosedQuestTheyCanSee_isAllowed() {
    UUID id = seedQuest(Visibility.CLOSED);
    receive(BYSTANDER, PacketCodec.writePinQuest(id));
    Assertions.assertTrue(dm.isQuestPinned(BYSTANDER, id));
  }

  @Test
  void pin_ofAPrivateQuestTheyCannotSee_isRefused() {
    UUID id = seedQuest(Visibility.PRIVATE);
    receive(BYSTANDER, PacketCodec.writePinQuest(id));
    Assertions.assertFalse(dm.isQuestPinned(BYSTANDER, id));
  }

  @Test
  void pin_ofALockedQuest_isRefused_unpinIsAlwaysAllowed() {
    UUID first = seedQuest(Visibility.OPEN);
    UUID second = seedQuest(Visibility.OPEN);
    dm.setPrerequisites(second, List.of(first));

    receive(BYSTANDER, PacketCodec.writePinQuest(second));
    Assertions.assertFalse(dm.isQuestPinned(BYSTANDER, second));

    dm.pinQuest(BYSTANDER, second);
    receive(BYSTANDER, PacketCodec.writePinQuest(second));
    Assertions.assertFalse(dm.isQuestPinned(BYSTANDER, second));
  }

  @Test
  void pinCompletedQuest_isIgnored() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.completeQuest(id, OWNER, 5000L);

    receive(OWNER, PacketCodec.writePinQuest(id));
    receive(BYSTANDER, PacketCodec.writePinQuest(id));

    Assertions.assertFalse(dm.isQuestPinned(OWNER, id));
    Assertions.assertFalse(dm.isQuestPinned(BYSTANDER, id));
  }

  // ---- people and visibility ----

  @Test
  void join_sendsTheJoinerTheirView() {
    UUID id = seedQuest(Visibility.OPEN);
    register(BYSTANDER, ProtocolVersion.V4);
    outbox.clear();

    receive(BYSTANDER, PacketCodec.writeJoinQuest(id));

    QuestData view = onlyUpdate(BYSTANDER);
    Assertions.assertEquals(QuestView.forRecipient(dm.loadGraph(), BYSTANDER, id), view);
    Assertions.assertTrue(view.contributors().stream().anyMatch(c -> c.uuid().equals(BYSTANDER)));
  }

  @Test
  void leaveOpenQuest_movesItToTheLeaversBoard() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.addContributor(id, CONTRIBUTOR, false);
    register(CONTRIBUTOR, ProtocolVersion.V4);
    outbox.clear();

    receive(CONTRIBUTOR, PacketCodec.writeLeaveQuest(id));

    QuestData view = onlyUpdate(CONTRIBUTOR);
    Assertions.assertEquals(QuestView.forRecipient(dm.loadGraph(), CONTRIBUTOR, id), view);
    Assertions.assertTrue(
        view.contributors().stream().noneMatch(c -> c.uuid().equals(CONTRIBUTOR)));
    Assertions.assertEquals(List.of(), outbox.deletesFor(CONTRIBUTOR));
  }

  @Test
  void approvedRequest_carriesTheRequestersView() {
    UUID id = seedQuest(Visibility.CLOSED);
    UUID requestId = dm.createCollaborationRequest(id, BYSTANDER);
    register(BYSTANDER, ProtocolVersion.V4);
    outbox.clear();

    receive(OWNER, PacketCodec.writeRespondCollaboration(requestId, true));

    QuestData view = QuestView.forRecipient(dm.loadGraph(), BYSTANDER, id);
    Assertions.assertEquals(
        List.of(new PacketCodec.CollaborationResponsePayload(id, true, view)),
        outbox.collaborationResponsesFor(BYSTANDER));
    Assertions.assertEquals(List.of(view), outbox.updatesFor(BYSTANDER));
  }

  @Test
  void collaborationRequest_reachesTheOwner() {
    UUID id = seedQuest(Visibility.CLOSED);

    receive(BYSTANDER, PacketCodec.writeRequestCollaboration(id));

    List<PacketCodec.CollaborationRequestPayload> requests = outbox.collaborationRequestsFor(OWNER);
    Assertions.assertEquals(1, requests.size());
    Assertions.assertEquals("Gare", requests.get(0).questTitle());
    Assertions.assertEquals("Bystander", requests.get(0).requesterName());
  }

  @Test
  void removingAContributorFromAPrivateQuest_deletesItForThem() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.addContributor(id, CONTRIBUTOR, true);
    register(OWNER, ProtocolVersion.V4);
    register(CONTRIBUTOR, ProtocolVersion.V4);
    outbox.clear();

    receive(
        OWNER,
        PacketCodec.writeUpdateContributors(
            id,
            List.of(
                new PacketCodec.ContributorOpEntry(
                    ContributorOp.REMOVE, CONTRIBUTOR, null, false))));

    Assertions.assertEquals(List.of(id), outbox.deletesFor(CONTRIBUTOR));
    Assertions.assertEquals(
        List.of(QuestView.forRecipient(dm.loadGraph(), OWNER, id)), outbox.updatesFor(OWNER));
    assertNothingSentTo(BYSTANDER);
  }

  @Test
  void deletingAnOpenQuest_deletesItForEveryoneWhoSawIt() {
    UUID open = seedQuest(Visibility.OPEN);
    UUID hidden = seedQuest(Visibility.PRIVATE);
    for (UUID player : List.of(OWNER, CONTRIBUTOR, BYSTANDER)) register(player, ProtocolVersion.V4);
    outbox.clear();

    receive(OWNER, PacketCodec.writeDeleteQuest(open));
    receive(OWNER, PacketCodec.writeDeleteQuest(hidden));

    Assertions.assertEquals(List.of(open, hidden), outbox.deletesFor(OWNER));
    Assertions.assertEquals(List.of(open), outbox.deletesFor(CONTRIBUTOR));
    Assertions.assertEquals(List.of(open), outbox.deletesFor(BYSTANDER));
  }

  @Test
  void settingAnOpenQuestPrivate_deletesItForNonParticipantsOnly() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.addContributor(id, CONTRIBUTOR, false);
    for (UUID player : List.of(OWNER, CONTRIBUTOR, BYSTANDER)) register(player, ProtocolVersion.V4);
    outbox.clear();

    receive(OWNER, PacketCodec.writeUpdateVisibility(id, Visibility.PRIVATE));

    Assertions.assertEquals(List.of(id), outbox.deletesFor(BYSTANDER));
    QuestGraph after = dm.loadGraph();
    for (UUID player : List.of(OWNER, CONTRIBUTOR)) {
      Assertions.assertEquals(
          List.of(QuestView.forRecipient(after, player, id)), outbox.updatesFor(player));
    }
  }
}
