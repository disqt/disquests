package com.disqt.disquests.server.papermc;

import static org.mockito.Mockito.*;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.PacketType;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.model.*;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
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
  private MockedStatic<Bukkit> bukkitMock;

  @BeforeEach
  void setUp() {
    dm = new DataManager(tempDir);
    dm.initialize();

    plugin = mock(DisquestsPlugin.class);
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
    Config config = mock(Config.class);
    when(config.getBluemapUrl()).thenReturn("");
    when(config.getPredefinedTags()).thenReturn(List.of());

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
}
