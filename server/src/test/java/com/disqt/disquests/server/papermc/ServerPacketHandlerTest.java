package com.disqt.disquests.server.papermc;

import static org.mockito.Mockito.*;

import com.disqt.disquests.common.PacketCodec;
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
