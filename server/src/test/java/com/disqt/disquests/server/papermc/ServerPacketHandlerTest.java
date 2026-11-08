package com.disqt.disquests.server.papermc;

import static org.mockito.Mockito.*;

import com.disqt.disquests.common.ByteBufReader;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.PacketType;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.*;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class ServerPacketHandlerTest {

  private static final String CHANNEL = "disquests:main";
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CONTRIBUTOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID BYSTANDER = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @TempDir Path tempDir;

  private DataManager dm;
  private ServerPacketHandler handler;
  private DisquestsPlugin plugin;
  private Player ownerPlayer;
  private Player contributorPlayer;
  private Player bystanderPlayer;
  private Config config;
  private MockedStatic<Bukkit> bukkitMock;

  @BeforeEach
  void setUp() {
    dm = new DataManager(tempDir);
    dm.initialize();

    plugin = mock(DisquestsPlugin.class);
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
    config = mock(Config.class);
    when(config.getBluemapUrl()).thenReturn("");
    when(config.getPredefinedTags()).thenReturn(List.of());
    when(config.getBluemapDefaultMap()).thenReturn("overworld");
    when(config.isPlayerMarkerEnabled()).thenReturn(true);
    when(config.isWorldMapEnabled()).thenReturn(true);

    handler = new ServerPacketHandler(plugin, dm, config);

    ownerPlayer = mockModPlayer(OWNER, "Owner");
    contributorPlayer = mockModPlayer(CONTRIBUTOR, "Contributor");
    bystanderPlayer = mockModPlayer(BYSTANDER, "Bystander");

    bukkitMock = mockStatic(Bukkit.class);
    bukkitMock
        .when(Bukkit::getOnlinePlayers)
        .thenReturn(List.of(ownerPlayer, contributorPlayer, bystanderPlayer));
    bukkitMock.when(() -> Bukkit.getPlayer(OWNER)).thenReturn(ownerPlayer);
    bukkitMock.when(() -> Bukkit.getPlayer(CONTRIBUTOR)).thenReturn(contributorPlayer);
    bukkitMock.when(() -> Bukkit.getPlayer(BYSTANDER)).thenReturn(bystanderPlayer);
  }

  @AfterEach
  void tearDown() {
    bukkitMock.close();
    dm.close();
  }

  private Player mockModPlayer(UUID uuid, String name) {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(uuid);
    when(player.getName()).thenReturn(name);
    when(player.getListeningPluginChannels()).thenReturn(Set.of(CHANNEL));
    when(player.isOnline()).thenReturn(true);
    return player;
  }

  @Test
  void saveNewQuest_broadcastsSyncTagsToV1Players() {
    dm.upsertPlayerName(OWNER, "Owner");
    dm.upsertPlayerName(BYSTANDER, "Bystander");

    // Register owner as V1 by sending REQUEST_SYNC
    byte[] syncPacket = PacketCodec.writeRequestSync(ProtocolVersion.V1);
    handler.onPluginMessageReceived(CHANNEL, ownerPlayer, syncPacket);
    // Register bystander as V1
    handler.onPluginMessageReceived(CHANNEL, bystanderPlayer, syncPacket);

    // Clear invocations from the sync setup
    clearInvocations(ownerPlayer, bystanderPlayer, contributorPlayer);

    // Save a new quest with a custom tag
    UUID questId = UUID.randomUUID();
    byte[] savePacket =
        PacketCodec.writeSaveQuest(
            questId, "Tagged Quest", "Content", null, false, null, null, List.of("newtag"));

    handler.onPluginMessageReceived(CHANNEL, ownerPlayer, savePacket);

    // Verify SYNC_TAGS was sent to the owner (V1) and bystander (V1)
    // Owner gets UPDATE_QUEST + SYNC_TAGS, bystander gets only SYNC_TAGS
    verify(ownerPlayer, atLeast(2)).sendPluginMessage(eq(plugin), eq(CHANNEL), any(byte[].class));
    verify(bystanderPlayer, atLeastOnce())
        .sendPluginMessage(
            eq(plugin),
            eq(CHANNEL),
            argThat(data -> data.length > 0 && data[0] == PacketType.SYNC_TAGS.getId()));
    // Contributor is V0 (never sent REQUEST_SYNC with V1), should NOT get SYNC_TAGS
    verify(contributorPlayer, never()).sendPluginMessage(any(), any(), any());
  }

  @Test
  void saveExistingQuest_onlyNotifiesOwnerAndContributors() {
    dm.upsertPlayerName(OWNER, "Owner");
    dm.upsertPlayerName(CONTRIBUTOR, "Contributor");
    dm.upsertPlayerName(BYSTANDER, "Bystander");

    UUID questId = UUID.randomUUID();
    QuestData quest =
        new QuestData(
            questId,
            "Test Quest",
            "Original content",
            OWNER,
            "Owner",
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of());
    dm.saveQuest(quest);
    dm.addContributor(questId, CONTRIBUTOR, true);

    byte[] packet =
        PacketCodec.writeSaveQuest(
            questId, "Updated Title", "Updated content", null, false, null, null, List.of());

    handler.onPluginMessageReceived(CHANNEL, ownerPlayer, packet);

    verify(ownerPlayer, atLeastOnce())
        .sendPluginMessage(eq(plugin), eq(CHANNEL), any(byte[].class));
    verify(contributorPlayer, atLeastOnce())
        .sendPluginMessage(eq(plugin), eq(CHANNEL), any(byte[].class));
    verify(bystanderPlayer, never()).sendPluginMessage(any(), any(), any());
  }

  // ---- V2 polygons ----

  private static final List<PointData> TRIANGLE =
      List.of(new PointData(0, 0), new PointData(10, 0), new PointData(0, 10));

  private void register(Player player, int version) {
    handler.onPluginMessageReceived(CHANNEL, player, PacketCodec.writeRequestSync(version));
  }

  private void send(
      Player player,
      UUID id,
      CoordinatesData c1,
      boolean region,
      CoordinatesData c2,
      String map,
      List<PointData> polygon) {
    handler.onPluginMessageReceived(
        CHANNEL,
        player,
        PacketCodec.writeSaveQuest(id, "T", "C", c1, region, c2, map, List.of(), polygon));
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
    register(ownerPlayer, ProtocolVersion.V2);
    UUID id = UUID.randomUUID();
    send(ownerPlayer, id, null, false, null, "overworld", TRIANGLE);
    Assertions.assertEquals(3, dm.getQuest(id).polygon().size());
  }

  @Test
  void saveQuest_v1Rectangle_becomesPolygon() {
    dm.upsertPlayerName(OWNER, "Owner");
    register(ownerPlayer, ProtocolVersion.V1);
    UUID id = UUID.randomUUID();
    send(
        ownerPlayer,
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
    register(ownerPlayer, ProtocolVersion.V1);
    send(
        ownerPlayer,
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
    register(ownerPlayer, ProtocolVersion.V1);
    send(
        ownerPlayer,
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
    register(ownerPlayer, ProtocolVersion.V2);
    UUID tooMany = UUID.randomUUID();
    List<PointData> big = new ArrayList<>();
    for (int i = 0; i < QuestLocation.MAX_VERTICES + 1; i++) big.add(new PointData(i, i * i));
    send(ownerPlayer, tooMany, null, false, null, "overworld", big);
    Assertions.assertNull(dm.getQuest(tooMany));
    UUID nan = UUID.randomUUID();
    send(
        ownerPlayer,
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
    register(ownerPlayer, ProtocolVersion.V2);
    UUID id = UUID.randomUUID();
    send(ownerPlayer, id, new CoordinatesData(1, 2, 3), false, null, "nether", List.of());
    Assertions.assertEquals("the_nether", dm.getQuest(id).map());
  }

  @Test
  void updateQuest_toV1Recipient_isWrittenInV1Form() {
    dm.upsertPlayerName(OWNER, "Owner");
    dm.upsertPlayerName(CONTRIBUTOR, "Contributor");
    register(ownerPlayer, ProtocolVersion.V2);
    register(contributorPlayer, ProtocolVersion.V1);
    UUID id = UUID.randomUUID();
    send(ownerPlayer, id, null, false, null, "overworld", TRIANGLE);
    dm.addContributor(id, CONTRIBUTOR, true);
    clearInvocations(contributorPlayer);

    send(ownerPlayer, id, null, false, null, "overworld", TRIANGLE);

    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    verify(contributorPlayer).sendPluginMessage(eq(plugin), eq(CHANNEL), captor.capture());
    ByteBufReader buf = new ByteBufReader(captor.getValue());
    Assertions.assertEquals(PacketType.UPDATE_QUEST, PacketCodec.readType(buf));
    QuestData q = PacketCodec.readUpdateQuest(buf, ProtocolVersion.V1);
    Assertions.assertTrue(q.isRegion());
    Assertions.assertEquals(0, q.coordinates().x());
    Assertions.assertEquals(0, q.coordinates().z());
    Assertions.assertEquals(10, q.coordinates2().x());
    Assertions.assertEquals(10, q.coordinates2().z());
    Assertions.assertTrue(q.polygon().isEmpty());
  }

  @Test
  void handshake_includesServerVersion() {
    handler.resendHandshakes();
    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    verify(ownerPlayer).sendPluginMessage(eq(plugin), eq(CHANNEL), captor.capture());
    ByteBufReader buf = new ByteBufReader(captor.getValue());
    Assertions.assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(buf));
    Assertions.assertEquals(
        ProtocolVersion.CURRENT, PacketCodec.readHandshake(buf).serverProtocolVersion());
  }

  @Test
  void handshake_carriesConfiguredWorldMapFlag() {
    when(config.isWorldMapEnabled()).thenReturn(false);
    handler.resendHandshakes();
    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    verify(ownerPlayer).sendPluginMessage(eq(plugin), eq(CHANNEL), captor.capture());
    ByteBufReader buf = new ByteBufReader(captor.getValue());
    Assertions.assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(buf));
    Assertions.assertFalse(PacketCodec.readHandshake(buf).worldMapEnabled());

    clearInvocations(ownerPlayer);
    when(config.isWorldMapEnabled()).thenReturn(true);
    handler.resendHandshakes();
    verify(ownerPlayer).sendPluginMessage(eq(plugin), eq(CHANNEL), captor.capture());
    ByteBufReader buf2 = new ByteBufReader(captor.getValue());
    PacketCodec.readType(buf2);
    Assertions.assertTrue(PacketCodec.readHandshake(buf2).worldMapEnabled());
  }

  @Test
  void handshake_carriesConfiguredPlayerMarkerFlag() {
    when(config.isPlayerMarkerEnabled()).thenReturn(false);
    handler.resendHandshakes();
    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    verify(ownerPlayer).sendPluginMessage(eq(plugin), eq(CHANNEL), captor.capture());
    ByteBufReader buf = new ByteBufReader(captor.getValue());
    Assertions.assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(buf));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(buf);
    Assertions.assertFalse(payload.playerMarkerEnabled());
    Assertions.assertTrue(payload.worldMapEnabled());

    clearInvocations(ownerPlayer);
    when(config.isPlayerMarkerEnabled()).thenReturn(true);
    handler.resendHandshakes();
    verify(ownerPlayer).sendPluginMessage(eq(plugin), eq(CHANNEL), captor.capture());
    ByteBufReader buf2 = new ByteBufReader(captor.getValue());
    PacketCodec.readType(buf2);
    Assertions.assertTrue(PacketCodec.readHandshake(buf2).playerMarkerEnabled());
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

  private List<byte[]> packetsOfType(Player player, PacketType type) {
    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    verify(player, atLeast(0)).sendPluginMessage(eq(plugin), eq(CHANNEL), captor.capture());
    return captor.getAllValues().stream()
        .filter(d -> d.length > 0 && d[0] == type.getId())
        .toList();
  }

  private QuestData onlyUpdate(Player player, int version) {
    List<byte[]> updates = packetsOfType(player, PacketType.UPDATE_QUEST);
    Assertions.assertEquals(1, updates.size(), "expected exactly one UPDATE_QUEST");
    ByteBufReader buf = new ByteBufReader(updates.get(0));
    PacketCodec.readType(buf);
    return PacketCodec.readUpdateQuest(buf, version);
  }

  private UUID onlyDelete(Player player) {
    List<byte[]> deletes = packetsOfType(player, PacketType.DELETE_QUEST_S2C);
    Assertions.assertEquals(1, deletes.size(), "expected exactly one DELETE_QUEST_S2C");
    ByteBufReader buf = new ByteBufReader(deletes.get(0));
    PacketCodec.readType(buf);
    return PacketCodec.readDeleteQuestS2C(buf);
  }

  private void complete(Player player, UUID id) {
    handler.onPluginMessageReceived(CHANNEL, player, PacketCodec.writeCompleteQuest(id));
  }

  private void reopen(Player player, UUID id) {
    handler.onPluginMessageReceived(CHANNEL, player, PacketCodec.writeReopenQuest(id));
  }

  @Test
  void complete_byOwner_marksCompleted_andUpdatesV3Owner() {
    UUID id = seedQuest(Visibility.PRIVATE);
    register(ownerPlayer, ProtocolVersion.V3);
    clearInvocations(ownerPlayer);

    complete(ownerPlayer, id);

    QuestData stored = dm.getQuest(id);
    Assertions.assertTrue(stored.isCompleted());
    Assertions.assertEquals(OWNER, stored.completedBy());
    QuestData sent = onlyUpdate(ownerPlayer, ProtocolVersion.V3);
    Assertions.assertTrue(sent.isCompleted());
    Assertions.assertEquals(OWNER, sent.completedBy());
    Assertions.assertEquals("Owner", sent.completedByName());
  }

  @Test
  void complete_byViewOnlyContributor_isRefused() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.addContributor(id, CONTRIBUTOR, false);
    register(contributorPlayer, ProtocolVersion.V3);
    clearInvocations(ownerPlayer, contributorPlayer, bystanderPlayer);

    complete(contributorPlayer, id);

    Assertions.assertFalse(dm.getQuest(id).isCompleted());
    verify(ownerPlayer, never()).sendPluginMessage(any(), any(), any());
    verify(contributorPlayer, never()).sendPluginMessage(any(), any(), any());
    verify(bystanderPlayer, never()).sendPluginMessage(any(), any(), any());
  }

  @Test
  void complete_byEditContributor_isAccepted() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.addContributor(id, CONTRIBUTOR, true);
    complete(contributorPlayer, id);
    QuestData stored = dm.getQuest(id);
    Assertions.assertTrue(stored.isCompleted());
    Assertions.assertEquals(CONTRIBUTOR, stored.completedBy());
  }

  @Test
  void complete_sendsDeleteToV2Viewers() {
    UUID id = seedQuest(Visibility.OPEN);
    register(ownerPlayer, ProtocolVersion.V3);
    register(bystanderPlayer, ProtocolVersion.V2);
    register(contributorPlayer, ProtocolVersion.V3); // not a participant
    clearInvocations(ownerPlayer, contributorPlayer, bystanderPlayer);

    complete(ownerPlayer, id);

    Assertions.assertEquals(id, onlyDelete(bystanderPlayer));
    Assertions.assertTrue(packetsOfType(bystanderPlayer, PacketType.UPDATE_QUEST).isEmpty());
    Assertions.assertEquals(id, onlyDelete(contributorPlayer));
    Assertions.assertTrue(packetsOfType(contributorPlayer, PacketType.UPDATE_QUEST).isEmpty());
    Assertions.assertTrue(onlyUpdate(ownerPlayer, ProtocolVersion.V3).isCompleted());
    Assertions.assertTrue(packetsOfType(ownerPlayer, PacketType.DELETE_QUEST_S2C).isEmpty());
  }

  @Test
  void complete_sendsDeleteToV2Contributor() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.addContributor(id, CONTRIBUTOR, true);
    register(ownerPlayer, ProtocolVersion.V3);
    register(contributorPlayer, ProtocolVersion.V2);
    clearInvocations(ownerPlayer, contributorPlayer, bystanderPlayer);

    complete(ownerPlayer, id);

    Assertions.assertEquals(id, onlyDelete(contributorPlayer));
    Assertions.assertTrue(packetsOfType(contributorPlayer, PacketType.UPDATE_QUEST).isEmpty());
    // Private quest: a non-participant never saw it and gets nothing
    verify(bystanderPlayer, never()).sendPluginMessage(any(), any(), any());
  }

  @Test
  void complete_removesPins_andResendsOwnerHandshakeWhenRequestsDropped() {
    UUID id = seedQuest(Visibility.CLOSED);
    dm.pinQuest(BYSTANDER, id);
    dm.createCollaborationRequest(id, BYSTANDER);
    register(ownerPlayer, ProtocolVersion.V3);
    clearInvocations(ownerPlayer);

    complete(ownerPlayer, id);

    Assertions.assertFalse(dm.isQuestPinned(BYSTANDER, id));
    Assertions.assertEquals(0, dm.getPendingRequestCount(OWNER));
    List<byte[]> handshakes = packetsOfType(ownerPlayer, PacketType.HANDSHAKE);
    Assertions.assertEquals(1, handshakes.size());
    ByteBufReader buf = new ByteBufReader(handshakes.get(0));
    PacketCodec.readType(buf);
    Assertions.assertEquals(0, PacketCodec.readHandshake(buf).pendingRequestCount());
  }

  @Test
  void complete_withoutPendingRequests_sendsNoHandshake() {
    UUID id = seedQuest(Visibility.CLOSED);
    register(ownerPlayer, ProtocolVersion.V3);
    clearInvocations(ownerPlayer);
    complete(ownerPlayer, id);
    Assertions.assertTrue(packetsOfType(ownerPlayer, PacketType.HANDSHAKE).isEmpty());
  }

  @Test
  void reopen_byOwner_restoresAndBroadcasts() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.completeQuest(id, OWNER, 5000L);
    register(ownerPlayer, ProtocolVersion.V3);
    register(bystanderPlayer, ProtocolVersion.V3);
    register(contributorPlayer, ProtocolVersion.V3);
    clearInvocations(ownerPlayer, contributorPlayer, bystanderPlayer);

    reopen(ownerPlayer, id);

    Assertions.assertFalse(dm.getQuest(id).isCompleted());
    Assertions.assertFalse(onlyUpdate(ownerPlayer, ProtocolVersion.V3).isCompleted());
    QuestData toBystander = onlyUpdate(bystanderPlayer, ProtocolVersion.V3);
    Assertions.assertEquals(id, toBystander.id());
    Assertions.assertFalse(toBystander.isCompleted());
    Assertions.assertFalse(onlyUpdate(contributorPlayer, ProtocolVersion.V3).isCompleted());
  }

  @Test
  void reopen_ofPrivateQuest_notifiesOnlyParticipants() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.addContributor(id, CONTRIBUTOR, true);
    dm.completeQuest(id, OWNER, 5000L);
    register(ownerPlayer, ProtocolVersion.V3);
    register(contributorPlayer, ProtocolVersion.V3);
    register(bystanderPlayer, ProtocolVersion.V3);
    clearInvocations(ownerPlayer, contributorPlayer, bystanderPlayer);

    reopen(ownerPlayer, id);

    Assertions.assertFalse(dm.getQuest(id).isCompleted());
    Assertions.assertFalse(onlyUpdate(ownerPlayer, ProtocolVersion.V3).isCompleted());
    Assertions.assertFalse(onlyUpdate(contributorPlayer, ProtocolVersion.V3).isCompleted());
    verify(bystanderPlayer, never()).sendPluginMessage(any(), any(), any());
  }

  @Test
  void reopen_byDemotedContributor_isRefused() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.addContributor(id, CONTRIBUTOR, true);
    dm.completeQuest(id, CONTRIBUTOR, 5000L);
    dm.updateContributor(id, CONTRIBUTOR, false);
    clearInvocations(ownerPlayer, contributorPlayer, bystanderPlayer);

    reopen(contributorPlayer, id);

    Assertions.assertTrue(dm.getQuest(id).isCompleted());
    verify(ownerPlayer, never()).sendPluginMessage(any(), any(), any());
    verify(bystanderPlayer, never()).sendPluginMessage(any(), any(), any());
  }

  @Test
  void saveOnCompletedQuest_isIgnored() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.completeQuest(id, OWNER, 5000L);
    clearInvocations(ownerPlayer);

    handler.onPluginMessageReceived(
        CHANNEL,
        ownerPlayer,
        PacketCodec.writeSaveQuest(id, "New title", "New", null, false, null, null, List.of()));

    QuestData stored = dm.getQuest(id);
    Assertions.assertEquals("Gare", stored.title());
    Assertions.assertTrue(stored.isCompleted());
    verify(ownerPlayer, never()).sendPluginMessage(any(), any(), any());
  }

  @Test
  void joinCompletedQuest_isIgnored() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.completeQuest(id, OWNER, 5000L);
    clearInvocations(bystanderPlayer);

    handler.onPluginMessageReceived(CHANNEL, bystanderPlayer, PacketCodec.writeJoinQuest(id));

    Assertions.assertFalse(dm.isContributor(id, BYSTANDER));
    verify(bystanderPlayer, never()).sendPluginMessage(any(), any(), any());
  }

  @Test
  void pinCompletedQuest_isIgnored() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.completeQuest(id, OWNER, 5000L);

    handler.onPluginMessageReceived(CHANNEL, ownerPlayer, PacketCodec.writePinQuest(id));
    handler.onPluginMessageReceived(CHANNEL, bystanderPlayer, PacketCodec.writePinQuest(id));

    Assertions.assertFalse(dm.isQuestPinned(OWNER, id));
    Assertions.assertFalse(dm.isQuestPinned(BYSTANDER, id));
  }

  @Test
  void leaveCompletedQuest_isIgnored() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.addContributor(id, CONTRIBUTOR, true);
    dm.completeQuest(id, OWNER, 5000L);
    clearInvocations(contributorPlayer);

    handler.onPluginMessageReceived(CHANNEL, contributorPlayer, PacketCodec.writeLeaveQuest(id));

    Assertions.assertTrue(dm.isContributor(id, CONTRIBUTOR));
    verify(contributorPlayer, never()).sendPluginMessage(any(), any(), any());
  }

  @Test
  void contributorsVisibilityAndRequestOnCompletedQuest_areIgnored() {
    UUID id = seedQuest(Visibility.CLOSED);
    dm.completeQuest(id, OWNER, 5000L);

    handler.onPluginMessageReceived(
        CHANNEL, ownerPlayer, PacketCodec.writeUpdateVisibility(id, Visibility.OPEN));
    handler.onPluginMessageReceived(
        CHANNEL, bystanderPlayer, PacketCodec.writeRequestCollaboration(id));

    Assertions.assertEquals(Visibility.CLOSED, dm.getQuest(id).visibility());
    Assertions.assertEquals(0, dm.getPendingRequestCount(OWNER));
  }

  @Test
  void deleteCompletedQuest_byOwner_isAccepted() {
    UUID id = seedQuest(Visibility.PRIVATE);
    dm.completeQuest(id, OWNER, 5000L);
    handler.onPluginMessageReceived(CHANNEL, ownerPlayer, PacketCodec.writeDeleteQuest(id));
    Assertions.assertNull(dm.getQuest(id));
  }

  private List<QuestData> syncList(Player player, int version, PacketType type) {
    clearInvocations(player);
    register(player, version);
    List<byte[]> packets = packetsOfType(player, type);
    Assertions.assertEquals(1, packets.size());
    ByteBufReader buf = new ByteBufReader(packets.get(0));
    PacketCodec.readType(buf);
    return type == PacketType.SYNC_MY_QUESTS
        ? PacketCodec.readSyncMyQuests(buf, version)
        : PacketCodec.readSyncServerQuests(buf, version);
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
        syncList(ownerPlayer, ProtocolVersion.V2, PacketType.SYNC_MY_QUESTS).stream()
            .map(QuestData::id)
            .toList();
    Assertions.assertEquals(List.of(active), ownerV2);

    List<QuestData> ownerV3 = syncList(ownerPlayer, ProtocolVersion.V3, PacketType.SYNC_MY_QUESTS);
    Assertions.assertEquals(
        Set.of(done, active), new HashSet<>(ownerV3.stream().map(QuestData::id).toList()));
    Assertions.assertTrue(
        ownerV3.stream().filter(q -> q.id().equals(done)).findFirst().orElseThrow().isCompleted());

    List<UUID> contribV2 =
        syncList(contributorPlayer, ProtocolVersion.V2, PacketType.SYNC_MY_QUESTS).stream()
            .map(QuestData::id)
            .toList();
    Assertions.assertTrue(contribV2.isEmpty());

    List<UUID> contribV3 =
        syncList(contributorPlayer, ProtocolVersion.V3, PacketType.SYNC_MY_QUESTS).stream()
            .map(QuestData::id)
            .toList();
    Assertions.assertEquals(List.of(done), contribV3);

    List<UUID> bystanderBoard =
        syncList(bystanderPlayer, ProtocolVersion.V3, PacketType.SYNC_SERVER_QUESTS).stream()
            .map(QuestData::id)
            .toList();
    Assertions.assertFalse(bystanderBoard.contains(done));
    Assertions.assertTrue(bystanderBoard.contains(active));
  }

  @Test
  void wikiLink_toCompletedQuest_stillResolves() {
    UUID id = seedQuest(Visibility.OPEN);
    dm.completeQuest(id, OWNER, 5000L);
    WikiLinkResolver resolver = new WikiLinkResolver(dm);
    Assertions.assertEquals(
        "See [[" + id + "|Gare]]", resolver.resolveForRecipient("See [[Gare]]", BYSTANDER));
  }
}
