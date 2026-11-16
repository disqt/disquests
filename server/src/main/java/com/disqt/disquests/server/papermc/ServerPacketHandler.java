package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.*;
import com.disqt.disquests.common.TagConstraints;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Receives the client's packets and applies them. Every handler that changes quests loads the graph
 * before and after its writes and hands both to {@link Delivery}, which alone sends quests out.
 */
public class ServerPacketHandler implements PluginMessageListener, Listener {
  private static final int MAX_TITLE_LENGTH = 256;
  private static final int MAX_CONTENT_LENGTH = 65536;
  private static final int MAX_MAP_LENGTH = 256;
  private final DisquestsPlugin plugin;
  private final DataManager dataManager;
  private final Outbox outbox;
  private final WikiLinkResolver wikiLinkResolver;
  private final Delivery delivery;
  private final Map<UUID, Integer> playerProtocolVersions = new ConcurrentHashMap<>();

  public ServerPacketHandler(
      DisquestsPlugin plugin, DataManager dataManager, Config config, Outbox outbox) {
    this.plugin = plugin;
    this.dataManager = dataManager;
    this.outbox = outbox;
    this.wikiLinkResolver = new WikiLinkResolver(dataManager);
    this.delivery =
        new Delivery(dataManager, config, outbox, wikiLinkResolver, this::getProtocolVersion);
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
        case SAVE_QUEST -> handleSaveQuest(player, name, PacketCodec.readSaveQuest(buf));
        case DELETE_QUEST -> handleDeleteQuest(player, PacketCodec.readDeleteQuest(buf));
        case JOIN_QUEST -> handleJoinQuest(player, PacketCodec.readJoinQuest(buf));
        case REQUEST_COLLABORATION ->
            handleRequestCollaboration(player, name, PacketCodec.readRequestCollaboration(buf));
        case RESPOND_COLLABORATION ->
            handleRespondCollaboration(player, PacketCodec.readRespondCollaboration(buf));
        case UPDATE_CONTRIBUTORS ->
            handleUpdateContributors(player, PacketCodec.readUpdateContributors(buf));
        case UPDATE_VISIBILITY ->
            handleUpdateVisibility(player, PacketCodec.readUpdateVisibility(buf));
        case PIN_QUEST -> handlePinQuest(player, PacketCodec.readPinQuest(buf));
        case LEAVE_QUEST -> handleLeaveQuest(player, buf);
        case COMPLETE_QUEST -> handleCompleteQuest(player, PacketCodec.readCompleteQuest(buf));
        case REOPEN_QUEST -> handleReopenQuest(player, PacketCodec.readReopenQuest(buf));
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

  private void handleSaveQuest(UUID player, String name, PacketCodec.SaveQuestPayload payload) {
    if (payload.title().length() > MAX_TITLE_LENGTH
        || payload.content().length() > MAX_CONTENT_LENGTH
        || (payload.map() != null && payload.map().length() > MAX_MAP_LENGTH)) {
      plugin.getLogger().warning("Quest field too long from " + name);
      return;
    }
    if (!QuestLocation.isValid(payload.polygon())) {
      plugin.getLogger().warning("Invalid quest polygon from " + name);
      return;
    }
    List<String> tags =
        payload.tags().stream()
            .map(String::toLowerCase)
            .filter(t -> !t.isEmpty() && t.length() <= TagConstraints.MAX_TAG_LENGTH)
            .filter(t -> TagConstraints.TAG_PATTERN.matcher(t).matches())
            .distinct()
            .limit(TagConstraints.MAX_TAGS)
            .toList();
    String content = wikiLinkResolver.reverseResolve(payload.content(), player);
    QuestGraph before = dataManager.loadGraph();
    QuestData existing = before.get(payload.questId());

    if (existing == null) {
      // New quest - player becomes owner, default PRIVATE
      QuestData newQuest =
          new QuestData(
              payload.questId(),
              payload.title(),
              content,
              player,
              name,
              Visibility.PRIVATE,
              List.of(),
              System.currentTimeMillis() / 1000,
              payload.coords(),
              payload.isRegion(),
              payload.coords2(),
              DimensionKeys.normalize(payload.map()),
              tags,
              payload.polygon());
      dataManager.saveQuest(newQuest);
    } else {
      // Existing quest - completed quests are locked; then check permission
      if (existing.isCompleted()) return;
      if (!canEdit(existing, player)) return; // silently ignore unauthorized edits

      QuestData updated =
          new QuestData(
              existing.id(),
              payload.title(),
              content,
              existing.ownerUuid(),
              existing.ownerName(),
              existing.visibility(),
              existing.contributors(),
              System.currentTimeMillis() / 1000,
              payload.coords(),
              payload.isRegion(),
              payload.coords2(),
              DimensionKeys.normalize(payload.map()),
              tags,
              payload.polygon());
      updated = keepExistingPolygonForUnchangedRectangle(player, existing, updated);
      dataManager.saveQuest(updated);
    }
    delivery.deliver(before, dataManager.loadGraph());
  }

  /** Owner or a contributor with edit rights, judged on the quest's current state. */
  private boolean canEdit(QuestData quest, UUID playerUuid) {
    return quest.ownerUuid().equals(playerUuid)
        || quest.contributors().stream().anyMatch(c -> c.uuid().equals(playerUuid) && c.canEdit());
  }

  private void handleCompleteQuest(UUID player, UUID questId) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(questId);
    if (quest == null || quest.isCompleted()) return;
    if (!canEdit(quest, player)) return;

    int droppedRequests =
        dataManager.getPendingCountByQuest(quest.ownerUuid()).getOrDefault(questId, 0);
    dataManager.completeQuest(questId, player, System.currentTimeMillis());
    delivery.deliver(before, dataManager.loadGraph());

    // completeQuest dropped the quest's pending requests: refresh the owner's inbox count, which
    // only the handshake carries (the client re-syncs its request list in response).
    if (droppedRequests > 0 && outbox.onlineModPlayers().contains(quest.ownerUuid())) {
      delivery.handshake(quest.ownerUuid());
    }
  }

  private void handleReopenQuest(UUID player, UUID questId) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(questId);
    if (quest == null || !quest.isCompleted()) return;
    if (!canEdit(quest, player)) return;

    dataManager.reopenQuest(questId);
    delivery.deliver(before, dataManager.loadGraph());
  }

  /**
   * A pre-V2 client only sees the bounding rectangle of a polygon. If it saves that rectangle back
   * unchanged, keep the stored polygon instead of flattening it to a rectangle.
   */
  private QuestData keepExistingPolygonForUnchangedRectangle(
      UUID player, QuestData existing, QuestData updated) {
    if (getProtocolVersion(player) >= ProtocolVersion.V2) return updated;
    if (existing.polygon().size() < 2) return updated;
    if (updated.coordinates() == null || updated.coordinates2() == null) return updated;
    CoordinatesData[] box = QuestLocation.boundingBox(existing.polygon(), 0);
    if (sameXZ(updated.coordinates(), box[0]) && sameXZ(updated.coordinates2(), box[1])) {
      return updated.withPolygon(existing.polygon());
    }
    return updated;
  }

  private static boolean sameXZ(CoordinatesData a, CoordinatesData b) {
    return Math.abs(a.x() - b.x()) <= 1e-6 && Math.abs(a.z() - b.z()) <= 1e-6;
  }

  private void handleDeleteQuest(UUID player, UUID questId) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(questId);
    if (quest == null) return;
    if (!quest.ownerUuid().equals(player)) return; // owner only

    dataManager.deleteQuest(questId);
    delivery.deliver(before, dataManager.loadGraph());
  }

  private void handleJoinQuest(UUID player, UUID questId) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(questId);
    if (quest == null) return;
    if (quest.isCompleted()) return;
    if (quest.visibility() != Visibility.OPEN) return; // only open quests
    if (quest.ownerUuid().equals(player)) return; // owner can't join own quest
    if (dataManager.isContributor(questId, player)) return; // already contributor

    dataManager.addContributor(questId, player, false); // view-only by default
    delivery.deliver(before, dataManager.loadGraph());
  }

  private void handleRequestCollaboration(UUID player, String name, UUID questId) {
    QuestData quest = dataManager.getQuest(questId);
    if (quest == null) return;
    if (quest.isCompleted()) return;
    if (quest.visibility() != Visibility.CLOSED) return;
    if (quest.ownerUuid().equals(player)) return;
    if (dataManager.isContributor(questId, player)) return;

    try {
      UUID requestId = dataManager.createCollaborationRequest(questId, player);
      // Notify quest owner if online
      outbox.send(
          quest.ownerUuid(),
          PacketCodec.writeCollaborationRequest(requestId, questId, quest.title(), name));
    } catch (RuntimeException e) {
      if (e.getCause() instanceof java.sql.SQLException) {
        // Duplicate request - silently ignore
      } else {
        plugin.getLogger().warning("Failed to create collaboration request: " + e.getMessage());
      }
    }
  }

  private void handleRespondCollaboration(
      UUID player, PacketCodec.RespondCollaborationPayload payload) {
    CollaborationRequestData request = dataManager.getCollaborationRequest(payload.requestId());
    if (request == null) return;

    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(request.questId());
    if (quest == null) return;
    if (!quest.ownerUuid().equals(player)) return; // owner only

    dataManager.deleteCollaborationRequest(payload.requestId());
    UUID requester = request.requesterUuid();
    int requesterVersion = getProtocolVersion(requester);

    if (payload.approved()) {
      dataManager.addContributor(quest.id(), requester, false);
      outbox.send(
          requester,
          PacketCodec.writeCollaborationResponse(
              quest.id(), true, delivery.sendableView(requester, quest.id()), requesterVersion));
      delivery.deliver(before, dataManager.loadGraph());
    } else {
      outbox.send(
          requester,
          PacketCodec.writeCollaborationResponse(quest.id(), false, null, requesterVersion));
    }
  }

  private void handleUpdateContributors(
      UUID player, PacketCodec.UpdateContributorsPayload payload) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(payload.questId());
    if (quest == null || quest.isCompleted()) return;
    if (!quest.ownerUuid().equals(player)) return; // owner only

    // Each op is its own write: deliver what was written even if a later op fails.
    try {
      for (PacketCodec.ContributorOpEntry op : payload.ops()) {
        switch (op.action()) {
          case ADD -> {
            UUID targetUuid = op.playerUuid();
            if (targetUuid == null && op.playerName() != null) {
              targetUuid = dataManager.getPlayerUuidByName(op.playerName());
            }
            if (targetUuid != null
                && !targetUuid.equals(quest.ownerUuid())
                && !dataManager.isContributor(payload.questId(), targetUuid)) {
              dataManager.addContributor(payload.questId(), targetUuid, op.canEdit());
            }
          }
          case REMOVE -> {
            if (op.playerUuid() != null) {
              dataManager.removeContributor(payload.questId(), op.playerUuid());
            }
          }
          case UPDATE -> {
            if (op.playerUuid() != null) {
              dataManager.updateContributor(payload.questId(), op.playerUuid(), op.canEdit());
            }
          }
        }
      }
    } finally {
      delivery.deliver(before, dataManager.loadGraph());
    }
  }

  private void handleUpdateVisibility(UUID player, PacketCodec.UpdateVisibilityPayload payload) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(payload.questId());
    if (quest == null || quest.isCompleted()) return;
    if (!quest.ownerUuid().equals(player)) return; // owner only

    dataManager.updateVisibility(payload.questId(), payload.visibility());
    delivery.deliver(before, dataManager.loadGraph());
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

  private void handleLeaveQuest(UUID player, ByteBufReader reader) {
    UUID questId = PacketCodec.readLeaveQuest(reader);

    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(questId);
    if (quest == null || quest.isCompleted()) return;

    // Owner cannot leave, only delete
    if (quest.ownerUuid().equals(player)) return;

    // Must be a contributor
    if (!dataManager.isContributor(questId, player)) return;

    dataManager.leaveQuest(questId, player);
    delivery.deliver(before, dataManager.loadGraph());
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
