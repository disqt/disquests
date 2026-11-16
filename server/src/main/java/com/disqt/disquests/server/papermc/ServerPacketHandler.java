package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.ByteBufReader;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.PacketType;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Receives the client's packets and hands each to the handler for its concern: {@link SaveHandler}
 * for saves, {@link PeopleHandler} for roles, joining and visibility, {@link CompletionHandler} for
 * completing, reopening and deleting. Every handler that changes quests loads the graph before and
 * after its writes and hands both to {@link Delivery}, which alone sends quests out.
 */
public class ServerPacketHandler implements PluginMessageListener, Listener {
  private final DisquestsPlugin plugin;
  private final DataManager dataManager;
  private final Outbox outbox;
  private final Delivery delivery;
  private final SaveHandler saves;
  private final PeopleHandler people;
  private final CompletionHandler completion;
  private final Map<UUID, Integer> playerProtocolVersions = new ConcurrentHashMap<>();

  public ServerPacketHandler(
      DisquestsPlugin plugin, DataManager dataManager, Config config, Outbox outbox) {
    this.plugin = plugin;
    this.dataManager = dataManager;
    this.outbox = outbox;
    WikiLinkResolver wikiLinks = new WikiLinkResolver(dataManager);
    this.delivery = new Delivery(dataManager, config, outbox, wikiLinks, this::getProtocolVersion);
    Outcomes outcomes = new Outcomes(dataManager, delivery, outbox, this::getProtocolVersion);
    Logger logger = plugin.getLogger();
    this.saves =
        new SaveHandler(dataManager, config, wikiLinks, outcomes, this::getProtocolVersion, logger);
    this.people =
        new PeopleHandler(
            dataManager, delivery, outbox, outcomes, this::getProtocolVersion, logger);
    this.completion = new CompletionHandler(dataManager, delivery, outbox, outcomes);
  }

  // --- Plugin Message Handling ---

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] data) {
    if (!DisquestsPlugin.CHANNEL.equals(channel)) return;
    receive(player.getUniqueId(), player.getName(), data);
  }

  /** One packet from a player, past the Bukkit boundary. */
  void receive(UUID player, String name, byte[] data) {
    try {
      ByteBufReader buf = new ByteBufReader(data);
      PacketType type = PacketType.fromId(buf.readByte());

      switch (type) {
        case REQUEST_SYNC -> handleRequestSync(player, buf);
        case SAVE_QUEST -> saves.save(player, name, PacketCodec.readSaveQuest(buf));
        case DELETE_QUEST -> completion.delete(player, PacketCodec.readDeleteQuestAction(buf));
        case COMPLETE_QUEST ->
            completion.complete(player, PacketCodec.readCompleteQuestAction(buf));
        case REOPEN_QUEST -> completion.reopen(player, PacketCodec.readReopenQuest(buf));
        case JOIN_QUEST -> people.join(player, PacketCodec.readJoinQuest(buf));
        case REQUEST_COLLABORATION ->
            people.request(player, name, PacketCodec.readRequestCollaboration(buf));
        case RESPOND_COLLABORATION ->
            people.respond(player, PacketCodec.readRespondCollaboration(buf));
        case LEAVE_QUEST -> people.leave(player, PacketCodec.readLeaveQuest(buf));
        case UPDATE_PEOPLE -> people.updatePeople(player, PacketCodec.readUpdatePeople(buf));
        case APPLY_ROLE_DOWN -> people.applyRoleDown(player, PacketCodec.readApplyRoleDown(buf));
        case UPDATE_CONTRIBUTORS ->
            people.updateContributors(player, PacketCodec.readUpdateContributors(buf));
        case UPDATE_VISIBILITY ->
            people.updateVisibility(player, PacketCodec.readUpdateVisibility(buf));
        case RESOLVE_PRIVATIZED ->
            people.resolvePrivatized(player, PacketCodec.readResolvePrivatized(buf));
        case PIN_QUEST -> handlePinQuest(player, PacketCodec.readPinQuest(buf));
        default -> plugin.getLogger().warning("Unknown C2S packet from " + name + ": " + type);
      }
    } catch (Exception e) {
      plugin.getLogger().log(java.util.logging.Level.WARNING, "Malformed packet from " + name, e);
    }
  }

  // --- Event Handlers ---

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    // Retry handshake every 20 ticks until the client registers the channel (up to 10 seconds).
    // Channel registration can be delayed on slow environments (CI with Xvfb).
    final int[] attempts = {0};
    Bukkit.getScheduler()
        .runTaskTimer(
            plugin,
            task -> {
              attempts[0]++;
              if (!player.isOnline() || attempts[0] > 10) {
                task.cancel();
                return;
              }
              if (player.getListeningPluginChannels().contains(DisquestsPlugin.CHANNEL)) {
                delivery.handshake(player.getUniqueId());
                task.cancel();
              }
            },
            40L,
            20L);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    playerProtocolVersions.remove(event.getPlayer().getUniqueId());
  }

  // --- Packet Handlers ---

  private void handleRequestSync(UUID player, ByteBufReader buf) {
    int protocolVersion = PacketCodec.readRequestSyncVersion(buf);
    playerProtocolVersions.put(player, protocolVersion);
    delivery.sync(player, protocolVersion);
  }

  /**
   * Pins a quest the player can see and that is not locked for them, or unpins it. Unpinning is
   * always allowed.
   */
  private void handlePinQuest(UUID player, UUID questId) {
    if (questId == null) return;
    if (dataManager.isQuestPinned(player, questId)) {
      dataManager.unpinQuest(player, questId);
      return;
    }
    QuestGraph graph = dataManager.loadGraph();
    QuestData quest = graph.get(questId);
    if (quest == null || quest.isCompleted()) return; // archived quests are never pinned
    if (!Delivery.fullyVisible(graph, player, questId)) return;
    dataManager.pinQuest(player, questId);
  }

  // --- Public API ---

  public void resendHandshakes() {
    for (UUID player : outbox.onlineModPlayers()) {
      delivery.handshake(player);
    }
  }

  private int getProtocolVersion(UUID player) {
    return playerProtocolVersions.getOrDefault(player, ProtocolVersion.V0);
  }
}
