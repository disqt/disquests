package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.*;
import com.disqt.disquests.common.model.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.*;

public class ServerPacketHandler implements PluginMessageListener, Listener {
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_CONTENT_LENGTH = 65536;
    private static final int MAX_MAP_LENGTH = 256;
    private final DisquestsPlugin plugin;
    private final DataManager dataManager;
    private final Config config;

    public ServerPacketHandler(DisquestsPlugin plugin, DataManager dataManager, Config config) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.config = config;
    }

    // --- Plugin Message Handling ---

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!DisquestsPlugin.CHANNEL.equals(channel)) return;
        try {
            ByteBufReader buf = new ByteBufReader(data);
            PacketType type = PacketType.fromId(buf.readByte());

            switch (type) {
                case REQUEST_SYNC -> handleRequestSync(player);
                case SAVE_QUEST -> handleSaveQuest(player, PacketCodec.readSaveQuest(buf));
                case DELETE_QUEST -> handleDeleteQuest(player, PacketCodec.readDeleteQuest(buf));
                case JOIN_QUEST -> handleJoinQuest(player, PacketCodec.readJoinQuest(buf));
                case REQUEST_COLLABORATION -> handleRequestCollaboration(player, PacketCodec.readRequestCollaboration(buf));
                case RESPOND_COLLABORATION -> handleRespondCollaboration(player, PacketCodec.readRespondCollaboration(buf));
                case UPDATE_CONTRIBUTORS -> handleUpdateContributors(player, PacketCodec.readUpdateContributors(buf));
                case UPDATE_VISIBILITY -> handleUpdateVisibility(player, PacketCodec.readUpdateVisibility(buf));
                case PIN_QUEST -> handlePinQuest(player, PacketCodec.readPinQuest(buf));
                case LEAVE_QUEST -> handleLeaveQuest(player, buf);
                default -> plugin.getLogger().warning("Unknown C2S packet from " + player.getName() + ": " + type);
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Malformed packet from " + player.getName(), e);
        }
    }

    // --- Event Handlers ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay handshake 40 ticks to allow Fabric channel registration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!isModPlayer(player)) return;
            sendHandshake(player);
        }, 40L);
    }

    // --- Packet Handlers ---

    private void handleRequestSync(Player player) {
        UUID uuid = player.getUniqueId();
        List<QuestData> myQuests = dataManager.getQuestsForPlayer(uuid);
        List<QuestData> serverQuests = dataManager.getServerQuests(uuid);
        Map<UUID, Integer> pendingCounts = dataManager.getPendingCountByQuest(uuid);
        List<CollaborationRequestData> pendingRequests = dataManager.getPendingRequestsForOwner(uuid);

        sendPacket(player, PacketCodec.writeSyncMyQuests(myQuests, pendingCounts));
        sendPacket(player, PacketCodec.writeSyncServerQuests(serverQuests));
        sendPacket(player, PacketCodec.writeSyncPendingRequests(pendingRequests));
    }

    private void handleSaveQuest(Player player, PacketCodec.SaveQuestPayload payload) {
        if (payload.title().length() > MAX_TITLE_LENGTH ||
            payload.content().length() > MAX_CONTENT_LENGTH ||
            (payload.map() != null && payload.map().length() > MAX_MAP_LENGTH)) {
            plugin.getLogger().warning("Quest field too long from " + player.getName());
            return;
        }
        List<String> tags = payload.tags().stream()
                .map(String::toLowerCase)
                .filter(t -> !t.isEmpty() && t.length() <= 32)
                .filter(t -> t.matches("[a-z0-9_-]+"))
                .distinct()
                .limit(8)
                .toList();
        UUID playerUuid = player.getUniqueId();
        QuestData existing = dataManager.getQuest(payload.questId());

        if (existing == null) {
            // New quest - player becomes owner, default PRIVATE
            QuestData newQuest = new QuestData(
                payload.questId(), payload.title(), payload.content(),
                playerUuid, player.getName(), Visibility.PRIVATE,
                List.of(), System.currentTimeMillis() / 1000,
                payload.coords(), payload.isRegion(), payload.coords2(), payload.map(),
                tags
            );
            dataManager.saveQuest(newQuest);
            QuestData saved = dataManager.getQuest(payload.questId());
            // Private quest - only send back to owner
            sendPacket(player, PacketCodec.writeUpdateQuest(saved));
        } else {
            // Existing quest - check permission
            boolean isOwner = existing.ownerUuid().equals(playerUuid);
            boolean canEdit = isOwner || existing.contributors().stream()
                .anyMatch(c -> c.uuid().equals(playerUuid) && c.canEdit());
            if (!canEdit) return; // silently ignore unauthorized edits

            QuestData updated = new QuestData(
                existing.id(), payload.title(), payload.content(),
                existing.ownerUuid(), existing.ownerName(), existing.visibility(),
                existing.contributors(), System.currentTimeMillis() / 1000,
                payload.coords(), payload.isRegion(), payload.coords2(), payload.map(),
                tags
            );
            dataManager.saveQuest(updated);
            QuestData saved = dataManager.getQuest(payload.questId());
            broadcastQuestUpdate(saved);
        }
    }

    private void handleDeleteQuest(Player player, UUID questId) {
        QuestData quest = dataManager.getQuest(questId);
        if (quest == null) return;
        if (!quest.ownerUuid().equals(player.getUniqueId())) return; // owner only

        dataManager.deleteQuest(questId);
        byte[] deletePacket = PacketCodec.writeDeleteQuestS2C(questId);

        // Broadcast to all relevant players
        if (quest.visibility() == Visibility.PRIVATE) {
            // Private: notify owner + contributors
            sendPacket(player, deletePacket);
            broadcastToContributors(quest, deletePacket);
        } else {
            // Open/Closed: notify all mod players
            broadcastToModPlayers(deletePacket);
        }
    }

    private void handleJoinQuest(Player player, UUID questId) {
        QuestData quest = dataManager.getQuest(questId);
        if (quest == null) return;
        if (quest.visibility() != Visibility.OPEN) return; // only open quests
        if (quest.ownerUuid().equals(player.getUniqueId())) return; // owner can't join own quest
        if (dataManager.isContributor(questId, player.getUniqueId())) return; // already contributor

        dataManager.addContributor(questId, player.getUniqueId(), false); // view-only by default
        QuestData updated = dataManager.getQuest(questId);

        // Send updated quest to the joiner (they'll move it to My Quests)
        sendPacket(player, PacketCodec.writeUpdateQuest(updated));
        // Also broadcast to other viewers so their contributor list updates
        broadcastQuestUpdate(updated);
    }

    private void handleRequestCollaboration(Player player, UUID questId) {
        QuestData quest = dataManager.getQuest(questId);
        if (quest == null) return;
        if (quest.visibility() != Visibility.CLOSED) return;
        if (quest.ownerUuid().equals(player.getUniqueId())) return;
        if (dataManager.isContributor(questId, player.getUniqueId())) return;

        try {
            UUID requestId = dataManager.createCollaborationRequest(questId, player.getUniqueId());
            // Notify quest owner if online
            Player owner = Bukkit.getPlayer(quest.ownerUuid());
            if (owner != null && isModPlayer(owner)) {
                sendPacket(owner, PacketCodec.writeCollaborationRequest(
                    requestId, questId, quest.title(), player.getName()));
            }
        } catch (RuntimeException e) {
            if (e.getCause() instanceof java.sql.SQLException) {
                // Duplicate request - silently ignore
            } else {
                plugin.getLogger().warning("Failed to create collaboration request: " + e.getMessage());
            }
        }
    }

    private void handleRespondCollaboration(Player player, PacketCodec.RespondCollaborationPayload payload) {
        CollaborationRequestData request = dataManager.getCollaborationRequest(payload.requestId());
        if (request == null) return;

        QuestData quest = dataManager.getQuest(request.questId());
        if (quest == null) return;
        if (!quest.ownerUuid().equals(player.getUniqueId())) return; // owner only

        dataManager.deleteCollaborationRequest(payload.requestId());

        if (payload.approved()) {
            dataManager.addContributor(quest.id(), request.requesterUuid(), false);
            QuestData updated = dataManager.getQuest(quest.id());

            // Notify requester
            Player requester = Bukkit.getPlayer(request.requesterUuid());
            if (requester != null && isModPlayer(requester)) {
                sendPacket(requester, PacketCodec.writeCollaborationResponse(
                    quest.id(), true, updated));
            }
            // Broadcast updated quest to all relevant
            broadcastQuestUpdate(updated);
        } else {
            // Notify requester of denial
            Player requester = Bukkit.getPlayer(request.requesterUuid());
            if (requester != null && isModPlayer(requester)) {
                sendPacket(requester, PacketCodec.writeCollaborationResponse(
                    quest.id(), false, null));
            }
        }
    }

    private void handleUpdateContributors(Player player, PacketCodec.UpdateContributorsPayload payload) {
        QuestData quest = dataManager.getQuest(payload.questId());
        if (quest == null) return;
        if (!quest.ownerUuid().equals(player.getUniqueId())) return; // owner only

        // Track players that need notification
        List<UUID> notifyPlayers = new ArrayList<>();

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
                        notifyPlayers.add(targetUuid);
                    }
                }
                case REMOVE -> {
                    if (op.playerUuid() != null) {
                        dataManager.removeContributor(payload.questId(), op.playerUuid());
                        notifyPlayers.add(op.playerUuid());
                    }
                }
                case UPDATE -> {
                    if (op.playerUuid() != null) {
                        dataManager.updateContributor(payload.questId(), op.playerUuid(), op.canEdit());
                    }
                }
            }
        }

        // Fetch updated quest once after all ops
        QuestData updated = dataManager.getQuest(payload.questId());
        byte[] updatePacket = PacketCodec.writeUpdateQuest(updated);

        // Notify affected players
        for (UUID targetUuid : notifyPlayers) {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && isModPlayer(target)) {
                // For removed contributors of PRIVATE quests, send delete instead
                if (updated.visibility() == Visibility.PRIVATE &&
                    updated.contributors().stream().noneMatch(c -> c.uuid().equals(targetUuid)) &&
                    !updated.ownerUuid().equals(targetUuid)) {
                    sendPacket(target, PacketCodec.writeDeleteQuestS2C(payload.questId()));
                } else {
                    sendPacket(target, updatePacket);
                }
            }
        }

        // Send updated quest back to owner
        sendPacket(player, updatePacket);
    }

    private void handleUpdateVisibility(Player player, PacketCodec.UpdateVisibilityPayload payload) {
        QuestData quest = dataManager.getQuest(payload.questId());
        if (quest == null) return;
        if (!quest.ownerUuid().equals(player.getUniqueId())) return; // owner only

        Visibility oldVisibility = quest.visibility();
        Visibility newVisibility = payload.visibility();
        dataManager.updateVisibility(payload.questId(), newVisibility);
        QuestData updated = dataManager.getQuest(payload.questId());

        if (oldVisibility == Visibility.PRIVATE && newVisibility != Visibility.PRIVATE) {
            // Private -> Open/Closed: quest appears for all mod players
            broadcastToModPlayers(PacketCodec.writeUpdateQuest(updated));
        } else if (oldVisibility != Visibility.PRIVATE && newVisibility == Visibility.PRIVATE) {
            // Open/Closed -> Private: quest disappears for non-contributors
            byte[] deletePacket = PacketCodec.writeDeleteQuestS2C(payload.questId());
            for (Player p : getModPlayers()) {
                UUID pUuid = p.getUniqueId();
                if (!updated.ownerUuid().equals(pUuid) &&
                    updated.contributors().stream().noneMatch(c -> c.uuid().equals(pUuid))) {
                    sendPacket(p, deletePacket);
                }
            }
            // Send updated quest to owner + contributors
            sendPacket(player, PacketCodec.writeUpdateQuest(updated));
            broadcastToContributors(updated, PacketCodec.writeUpdateQuest(updated));
        } else {
            // Open <-> Closed or same: just update everyone
            broadcastQuestUpdate(updated);
        }
    }

    private void handlePinQuest(Player player, UUID questId) {
        if (questId == null) return;
        UUID playerUuid = player.getUniqueId();
        if (dataManager.isQuestPinned(playerUuid, questId)) {
            dataManager.unpinQuest(playerUuid, questId);
        } else {
            QuestData quest = dataManager.getQuest(questId);
            if (quest == null) return;
            boolean canSee = quest.ownerUuid().equals(playerUuid)
                || quest.contributors().stream().anyMatch(c -> c.uuid().equals(playerUuid))
                || quest.visibility() == Visibility.OPEN;
            if (!canSee) return;
            dataManager.pinQuest(playerUuid, questId);
        }
    }

    private void handleLeaveQuest(Player player, ByteBufReader reader) {
        UUID questId = PacketCodec.readLeaveQuest(reader);
        UUID playerUuid = player.getUniqueId();

        QuestData quest = dataManager.getQuest(questId);
        if (quest == null) return;

        // Owner cannot leave, only delete
        if (quest.ownerUuid().equals(playerUuid)) return;

        // Must be a contributor
        boolean isContributor = dataManager.isContributor(questId, playerUuid);
        if (!isContributor) return;

        dataManager.leaveQuest(questId, playerUuid);

        // Notify the leaving player: remove quest from their "my quests"
        sendPacket(player, PacketCodec.writeDeleteQuestS2C(questId));

        // Broadcast updated quest to everyone who should see it
        QuestData updated = dataManager.getQuest(questId);
        if (updated != null) {
            broadcastQuestUpdate(updated);
        }
    }

    // --- Public API ---

    public void resendHandshakes() {
        for (Player p : getModPlayers()) {
            sendHandshake(p);
        }
    }

    // --- Helpers ---

    private void sendHandshake(Player player) {
        List<UUID> pinnedIds = dataManager.getPinnedQuestIds(player.getUniqueId());
        int pendingCount = dataManager.getPendingRequestCount(player.getUniqueId());
        sendPacket(player, PacketCodec.writeHandshake(
            config.getBluemapUrl(), pendingCount, pinnedIds,
            player.getUniqueId(), config.getBluemapMapNames(), config.getPredefinedTags()));
    }

    private void sendPacket(Player player, byte[] data) {
        player.sendPluginMessage(plugin, DisquestsPlugin.CHANNEL, data);
    }

    private boolean isModPlayer(Player player) {
        return player.getListeningPluginChannels().contains(DisquestsPlugin.CHANNEL);
    }

    private List<Player> getModPlayers() {
        List<Player> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isModPlayer(p)) result.add(p);
        }
        return result;
    }

    private void broadcastToModPlayers(byte[] data) {
        for (Player p : getModPlayers()) {
            sendPacket(p, data);
        }
    }

    private void broadcastToContributors(QuestData quest, byte[] data) {
        for (ContributorData c : quest.contributors()) {
            Player p = Bukkit.getPlayer(c.uuid());
            if (p != null && isModPlayer(p)) {
                sendPacket(p, data);
            }
        }
    }

    private void broadcastQuestUpdate(QuestData quest) {
        byte[] packet = PacketCodec.writeUpdateQuest(quest);
        if (quest.visibility() == Visibility.PRIVATE) {
            // Private: owner + contributors only
            Player owner = Bukkit.getPlayer(quest.ownerUuid());
            if (owner != null && isModPlayer(owner)) sendPacket(owner, packet);
            broadcastToContributors(quest, packet);
        } else {
            // Open/Closed: all mod players
            broadcastToModPlayers(packet);
        }
    }
}
