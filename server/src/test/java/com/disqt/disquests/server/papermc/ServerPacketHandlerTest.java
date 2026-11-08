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
}
