package net.atif.buildnotes.network;

import com.disqt.buildnotes.common.ByteBufReader;
import com.disqt.buildnotes.common.PacketCodec;
import com.disqt.buildnotes.common.PacketType;
import com.disqt.buildnotes.common.model.BuildData;
import com.disqt.buildnotes.common.model.CustomFieldData;
import com.disqt.buildnotes.common.model.NoteData;
import com.disqt.buildnotes.common.model.PermissionLevel;
import net.atif.buildnotes.Buildnotes;
import net.atif.buildnotes.client.ClientCache;
import net.atif.buildnotes.client.ClientImageTransferManager;
import net.atif.buildnotes.client.ClientSession;
import net.atif.buildnotes.data.Build;
import net.atif.buildnotes.data.CustomField;
import net.atif.buildnotes.data.Note;
import net.atif.buildnotes.data.Scope;
import net.atif.buildnotes.gui.screen.MainScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientPacketHandler {

    public static void handleRawPayload(RawPayload payload, ClientPlayNetworking.Context context) {
        ByteBufReader r = new ByteBufReader(payload.data());
        PacketType type;
        try {
            type = PacketCodec.readType(r);
        } catch (Exception e) {
            Buildnotes.LOGGER.warn("Received invalid BuildNotes packet", e);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            switch (type) {
                case HANDSHAKE -> handleHandshake(r);
                case INITIAL_SYNC -> handleInitialSync(r, client);
                case UPDATE_NOTE -> handleUpdateNote(r, client);
                case DELETE_NOTE_S2C -> handleDeleteNote(r, client);
                case UPDATE_BUILD -> handleUpdateBuild(r, client);
                case DELETE_BUILD_S2C -> handleDeleteBuild(r, client);
                case IMAGE_CHUNK -> handleImageChunk(r);
                case IMAGE_NOT_FOUND -> handleImageNotFound(r);
                case UPDATE_PERMISSION -> handleUpdatePermission(r, client);
                default -> Buildnotes.LOGGER.warn("Unexpected S2C packet type: {}", type);
            }
        });
    }

    private static void handleHandshake(ByteBufReader r) {
        PermissionLevel permission = PacketCodec.readPermission(r);
        ClientSession.joinServer(toClientPermission(permission));
        ClientPlayNetworking.send(new RawPayload(PacketCodec.writeRequestData()));
    }

    private static void handleInitialSync(ByteBufReader r, MinecraftClient client) {
        int noteCount = r.readVarInt();
        List<Note> notes = new ArrayList<>(noteCount);
        for (int i = 0; i < noteCount; i++) {
            notes.add(toNote(PacketCodec.readNote(r)));
        }
        int buildCount = r.readVarInt();
        List<Build> builds = new ArrayList<>(buildCount);
        for (int i = 0; i < buildCount; i++) {
            builds.add(toBuild(PacketCodec.readBuild(r)));
        }
        ClientCache.setNotes(notes);
        ClientCache.setBuilds(builds);
        refreshMainScreen(client);
    }

    private static void handleUpdateNote(ByteBufReader r, MinecraftClient client) {
        ClientCache.addOrUpdateNote(toNote(PacketCodec.readNote(r)));
        refreshMainScreen(client);
    }

    private static void handleDeleteNote(ByteBufReader r, MinecraftClient client) {
        ClientCache.removeNoteById(PacketCodec.readUUID(r));
        refreshMainScreen(client);
    }

    private static void handleUpdateBuild(ByteBufReader r, MinecraftClient client) {
        ClientCache.addOrUpdateBuild(toBuild(PacketCodec.readBuild(r)));
        refreshMainScreen(client);
    }

    private static void handleDeleteBuild(ByteBufReader r, MinecraftClient client) {
        ClientCache.removeBuildById(PacketCodec.readUUID(r));
        refreshMainScreen(client);
    }

    private static void handleImageChunk(ByteBufReader r) {
        UUID buildId = r.readUUID();
        String filename = r.readString();
        int totalChunks = r.readVarInt();
        int chunkIndex = r.readVarInt();
        byte[] data = r.readBytes();
        ClientImageTransferManager.handleChunk(buildId, filename, totalChunks, chunkIndex, data);
    }

    private static void handleImageNotFound(ByteBufReader r) {
        UUID buildId = r.readUUID();
        String filename = r.readString();
        ClientImageTransferManager.onDownloadFailed(buildId, filename);
    }

    private static void handleUpdatePermission(ByteBufReader r, MinecraftClient client) {
        PermissionLevel permission = PacketCodec.readPermission(r);
        ClientSession.updatePermissionLevel(toClientPermission(permission));
        refreshMainScreen(client);
    }

    private static void refreshMainScreen(MinecraftClient client) {
        if (client.currentScreen instanceof MainScreen) {
            ((MainScreen) client.currentScreen).refreshData();
        }
    }

    // --- Converters between common model and client model ---

    public static net.atif.buildnotes.data.PermissionLevel toClientPermission(PermissionLevel p) {
        return p == PermissionLevel.CAN_EDIT
                ? net.atif.buildnotes.data.PermissionLevel.CAN_EDIT
                : net.atif.buildnotes.data.PermissionLevel.VIEW_ONLY;
    }

    public static Note toNote(NoteData data) {
        return Note.fromNetwork(data.id(), data.lastModified(), data.title(), data.content());
    }

    public static NoteData toNoteData(Note note) {
        return new NoteData(note.getId(), note.getLastModified(), new UUID(0, 0), note.getTitle(), note.getContent());
    }

    public static Build toBuild(BuildData data) {
        List<CustomField> fields = new ArrayList<>();
        for (CustomFieldData f : data.customFields()) {
            fields.add(new CustomField(f.title(), f.content()));
        }
        return Build.fromNetwork(data.id(), data.lastModified(),
                data.name(), data.coordinates(), data.dimension(),
                data.description(), data.credits(),
                new ArrayList<>(data.imageFileNames()), fields);
    }

    public static BuildData toBuildData(Build build) {
        List<CustomFieldData> fields = new ArrayList<>();
        for (CustomField f : build.getCustomFields()) {
            fields.add(new CustomFieldData(f.getTitle(), f.getContent()));
        }
        return new BuildData(build.getId(), build.getLastModified(), new UUID(0, 0),
                build.getName(), build.getCoordinates(), build.getDimension(),
                build.getDescription(), build.getCredits(),
                build.getImageFileNames(), fields);
    }
}
