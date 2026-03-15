package com.disqt.buildnotes.paper;

import com.disqt.buildnotes.common.ByteBufReader;
import com.disqt.buildnotes.common.PacketCodec;
import com.disqt.buildnotes.common.PacketType;
import com.disqt.buildnotes.common.model.BuildData;
import com.disqt.buildnotes.common.model.NoteData;
import com.disqt.buildnotes.common.model.PermissionLevel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ServerPacketHandler implements PluginMessageListener, Listener {

    private final BuildNotesPlugin plugin;
    private final DataManager dataManager;
    private final PermissionManager permissionManager;
    private final ImageTransferManager imageTransferManager = new ImageTransferManager();

    public ServerPacketHandler(BuildNotesPlugin plugin, DataManager dataManager, PermissionManager permissionManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.permissionManager = permissionManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sendToPlayer(player, PacketCodec.writeHandshake(permissionManager.getPermission(player)));
            }
        }, 40L); // 2 seconds — wait for Fabric client to register plugin channels
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        imageTransferManager.onPlayerDisconnect(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BuildNotesPlugin.CHANNEL.equals(channel)) {
            return;
        }

        ByteBufReader r = new ByteBufReader(message);
        PacketType type = PacketCodec.readType(r);

        // Capture remaining bytes for main thread dispatch
        int headerSize = message.length - r.remaining();
        byte[] payload = Arrays.copyOfRange(message, headerSize, message.length);

        Bukkit.getScheduler().runTask(plugin, () -> {
            ByteBufReader reader = new ByteBufReader(payload);
            switch (type) {
                case REQUEST_DATA -> handleRequestData(player);
                case SAVE_NOTE -> handleSaveNote(player, reader);
                case DELETE_NOTE -> handleDeleteNote(player, reader);
                case SAVE_BUILD -> handleSaveBuild(player, reader);
                case DELETE_BUILD -> handleDeleteBuild(player, reader);
                case UPLOAD_IMAGE_CHUNK -> handleImageChunk(player, reader);
                case REQUEST_IMAGE -> handleRequestImage(player, reader);
                default -> plugin.getLogger().warning("Unexpected C2S packet type: " + type);
            }
        });
    }

    private void handleRequestData(Player player) {
        List<NoteData> notes = dataManager.getAllNotes();
        List<BuildData> builds = dataManager.getAllBuilds();
        sendToPlayer(player, PacketCodec.writeInitialSync(notes, builds));
    }

    private void handleSaveNote(Player player, ByteBufReader r) {
        if (!hasEditPermission(player)) {
            return;
        }
        NoteData note = PacketCodec.readNote(r);
        NoteData owned = new NoteData(note.id(), note.lastModified(), player.getUniqueId(), note.title(), note.content());
        dataManager.saveNote(owned);
        broadcast(PacketCodec.writeUpdateNote(owned));
    }

    private void handleDeleteNote(Player player, ByteBufReader r) {
        if (!hasEditPermission(player)) {
            return;
        }
        UUID noteId = r.readUUID();
        if (dataManager.deleteNote(noteId)) {
            broadcast(PacketCodec.writeDeleteNoteS2C(noteId));
        }
    }

    private void handleSaveBuild(Player player, ByteBufReader r) {
        if (!hasEditPermission(player)) {
            return;
        }
        BuildData build = PacketCodec.readBuild(r);
        BuildData owned = new BuildData(build.id(), build.lastModified(), player.getUniqueId(),
                build.name(), build.coordinates(), build.dimension(),
                build.description(), build.credits(), build.imageFileNames(), build.customFields());
        dataManager.saveBuild(owned);
        broadcast(PacketCodec.writeUpdateBuild(owned));
    }

    private void handleDeleteBuild(Player player, ByteBufReader r) {
        if (!hasEditPermission(player)) {
            return;
        }
        UUID buildId = r.readUUID();
        if (dataManager.deleteBuild(buildId)) {
            broadcast(PacketCodec.writeDeleteBuildS2C(buildId));
        }
    }

    private void handleImageChunk(Player player, ByteBufReader r) {
        if (!hasEditPermission(player)) {
            return;
        }
        UUID buildId = r.readUUID();
        String filename = r.readString();
        int totalChunks = r.readVarInt();
        int chunkIndex = r.readVarInt();
        byte[] data = r.readBytes();

        byte[] complete = imageTransferManager.handleChunk(
                player.getUniqueId(), buildId, filename, totalChunks, chunkIndex, data);
        if (complete != null) {
            dataManager.saveImage(buildId, "build", filename, complete);
        }
    }

    private void handleRequestImage(Player player, ByteBufReader r) {
        UUID buildId = r.readUUID();
        String filename = r.readString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] imageData = dataManager.getImage(buildId, filename);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (imageData == null) {
                    sendToPlayer(player, PacketCodec.writeImageNotFound(buildId, filename));
                } else {
                    sendImageChunks(player, buildId, filename, imageData);
                }
            });
        });
    }

    private void sendImageChunks(Player player, UUID buildId, String filename, byte[] imageData) {
        int chunkSize = PacketCodec.CHUNK_SIZE;
        int totalChunks = (imageData.length + chunkSize - 1) / chunkSize;
        for (int i = 0; i < totalChunks; i++) {
            int offset = i * chunkSize;
            int length = Math.min(chunkSize, imageData.length - offset);
            byte[] chunk = Arrays.copyOfRange(imageData, offset, offset + length);
            sendToPlayer(player, PacketCodec.writeImageChunk(buildId, filename, totalChunks, i, chunk));
        }
    }

    private boolean hasEditPermission(Player player) {
        return permissionManager.getPermission(player) == PermissionLevel.CAN_EDIT;
    }

    private void sendToPlayer(Player player, byte[] data) {
        player.sendPluginMessage(plugin, BuildNotesPlugin.CHANNEL, data);
    }

    private void broadcast(byte[] data) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendToPlayer(player, data);
        }
    }
}
