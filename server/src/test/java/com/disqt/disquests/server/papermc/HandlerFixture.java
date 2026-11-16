package com.disqt.disquests.server.papermc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.QuestView;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.TreeOp;
import com.disqt.disquests.common.model.Visibility;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives {@link ServerPacketHandler} with real storage on a temporary directory and a {@link
 * FakeOutbox}. Four players are online with V4 clients and have synced; each test sets up quests
 * through {@link DataManager}, clears the outbox and sends packets.
 */
abstract class HandlerFixture {

  static final UUID LEO = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  static final UUID MAX = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  static final UUID JUJU = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
  static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
  static final Map<UUID, String> NAMES = Map.of(LEO, "Leo", MAX, "Max", JUJU, "Juju", ANNA, "Anna");

  @TempDir Path tempDir;

  DataManager dm;
  Config config;
  FakeOutbox outbox;
  ServerPacketHandler handler;

  @BeforeEach
  void startServer() {
    dm = new DataManager(tempDir);
    dm.initialize();
    for (Map.Entry<UUID, String> player : NAMES.entrySet()) {
      dm.upsertPlayerName(player.getKey(), player.getValue());
    }

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
    handler = new ServerPacketHandler(plugin, dm, config, outbox);
    for (UUID player : List.of(LEO, MAX, JUJU, ANNA)) register(player, ProtocolVersion.V4);
    outbox.clear();
  }

  @AfterEach
  void stopServer() {
    dm.close();
  }

  // ---- players ----

  /** The player comes online with a client at {@code version} and syncs. */
  void register(UUID player, int version) {
    outbox.join(player, NAMES.get(player), version);
    receive(player, PacketCodec.writeRequestSync(version));
  }

  void receive(UUID player, byte[] packet) {
    handler.receive(player, NAMES.get(player), packet);
  }

  // ---- quests ----

  UUID quest(UUID owner, String title, Visibility visibility) {
    UUID id = UUID.randomUUID();
    dm.saveQuest(
        new QuestData(
            id,
            title,
            "",
            owner,
            NAMES.get(owner),
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

  UUID subquest(UUID parent, UUID owner, String title, Visibility visibility) {
    UUID id = quest(owner, title, visibility);
    dm.setParent(id, parent);
    return id;
  }

  QuestData stored(UUID id) {
    return dm.loadGraph().get(id);
  }

  Role roleOf(UUID quest, UUID player) {
    return stored(quest).roleOf(player);
  }

  /** The quest as {@code player} may see it now. */
  QuestData viewOf(UUID player, UUID quest) {
    QuestGraph g = dm.loadGraph();
    return QuestView.forRecipient(g, player, quest);
  }

  // ---- packets ----

  void save(UUID player, UUID quest, String title, LinkChanges links) {
    receive(
        player,
        PacketCodec.writeSaveQuest(
            quest, title, "", null, false, null, "overworld", List.of(), List.of(), links));
  }

  void saveText(UUID player, UUID quest, String title) {
    save(player, quest, title, LinkChanges.NONE);
  }

  static LinkChanges treeOps(TreeOp... ops) {
    return new LinkChanges(null, List.of(ops), List.of(), List.of(), false, false, null);
  }

  static LinkChanges visibility(Visibility visibility) {
    return new LinkChanges(visibility, List.of(), List.of(), List.of(), false, false, null);
  }

  static LinkChanges addPrerequisites(UUID... prerequisites) {
    return new LinkChanges(null, List.of(), List.of(prerequisites), List.of(), false, false, null);
  }

  static TreeOp attach(UUID quest, UUID parent) {
    return new TreeOp(TreeOp.Kind.ATTACH, quest, parent);
  }

  static TreeOp detach(UUID quest) {
    return new TreeOp(TreeOp.Kind.DETACH, quest, null);
  }

  static TreeOp swap(UUID container, UUID descendant) {
    return new TreeOp(TreeOp.Kind.SWAP, container, descendant);
  }

  List<Notice> notices(UUID player) {
    return outbox.noticesFor(player);
  }
}
