package com.disqt.buildnotes.common;

import com.disqt.buildnotes.common.model.BuildData;
import com.disqt.buildnotes.common.model.CustomFieldData;
import com.disqt.buildnotes.common.model.NoteData;
import com.disqt.buildnotes.common.model.PermissionLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PacketCodec {

    public static final int CHUNK_SIZE = 24 * 1024;

    private PacketCodec() {
    }

    // ---- C2S encode ----

    public static byte[] writeRequestData() {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.REQUEST_DATA.getId());
        return buf.toByteArray();
    }

    public static byte[] writeSaveNote(NoteData note) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.SAVE_NOTE.getId());
        writeNote(buf, note);
        return buf.toByteArray();
    }

    public static byte[] writeDeleteNote(UUID noteId) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.DELETE_NOTE.getId());
        buf.writeUUID(noteId);
        return buf.toByteArray();
    }

    public static byte[] writeSaveBuild(BuildData build) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.SAVE_BUILD.getId());
        writeBuild(buf, build);
        return buf.toByteArray();
    }

    public static byte[] writeDeleteBuild(UUID buildId) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.DELETE_BUILD.getId());
        buf.writeUUID(buildId);
        return buf.toByteArray();
    }

    public static byte[] writeUploadImageChunk(UUID buildId, String filename, int totalChunks, int chunkIndex, byte[] data) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.UPLOAD_IMAGE_CHUNK.getId());
        buf.writeUUID(buildId);
        buf.writeString(filename);
        buf.writeVarInt(totalChunks);
        buf.writeVarInt(chunkIndex);
        buf.writeBytes(data);
        return buf.toByteArray();
    }

    public static byte[] writeRequestImage(UUID buildId, String filename) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.REQUEST_IMAGE.getId());
        buf.writeUUID(buildId);
        buf.writeString(filename);
        return buf.toByteArray();
    }

    // ---- S2C encode ----

    public static byte[] writeHandshake(PermissionLevel permission) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.HANDSHAKE.getId());
        buf.writeVarInt(permission.ordinal());
        return buf.toByteArray();
    }

    public static byte[] writeInitialSync(List<NoteData> notes, List<BuildData> builds) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.INITIAL_SYNC.getId());
        buf.writeVarInt(notes.size());
        for (NoteData note : notes) {
            writeNote(buf, note);
        }
        buf.writeVarInt(builds.size());
        for (BuildData build : builds) {
            writeBuild(buf, build);
        }
        return buf.toByteArray();
    }

    public static byte[] writeUpdateNote(NoteData note) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.UPDATE_NOTE.getId());
        writeNote(buf, note);
        return buf.toByteArray();
    }

    public static byte[] writeDeleteNoteS2C(UUID noteId) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.DELETE_NOTE_S2C.getId());
        buf.writeUUID(noteId);
        return buf.toByteArray();
    }

    public static byte[] writeUpdateBuild(BuildData build) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.UPDATE_BUILD.getId());
        writeBuild(buf, build);
        return buf.toByteArray();
    }

    public static byte[] writeDeleteBuildS2C(UUID buildId) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.DELETE_BUILD_S2C.getId());
        buf.writeUUID(buildId);
        return buf.toByteArray();
    }

    public static byte[] writeImageChunk(UUID buildId, String filename, int totalChunks, int chunkIndex, byte[] data) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.IMAGE_CHUNK.getId());
        buf.writeUUID(buildId);
        buf.writeString(filename);
        buf.writeVarInt(totalChunks);
        buf.writeVarInt(chunkIndex);
        buf.writeBytes(data);
        return buf.toByteArray();
    }

    public static byte[] writeImageNotFound(UUID buildId, String filename) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.IMAGE_NOT_FOUND.getId());
        buf.writeUUID(buildId);
        buf.writeString(filename);
        return buf.toByteArray();
    }

    public static byte[] writeUpdatePermission(PermissionLevel permission) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.UPDATE_PERMISSION.getId());
        buf.writeVarInt(permission.ordinal());
        return buf.toByteArray();
    }

    // ---- Decode helpers ----

    public static PacketType readType(ByteBufReader buf) {
        return PacketType.fromId(buf.readByte());
    }

    public static NoteData readNote(ByteBufReader buf) {
        UUID id = buf.readUUID();
        long lastModified = buf.readLong();
        UUID ownerUuid = buf.readUUID();
        String title = buf.readString();
        String content = buf.readString();
        return new NoteData(id, lastModified, ownerUuid, title, content);
    }

    public static BuildData readBuild(ByteBufReader buf) {
        UUID id = buf.readUUID();
        long lastModified = buf.readLong();
        UUID ownerUuid = buf.readUUID();
        String name = buf.readString();
        String coordinates = buf.readString();
        String dimension = buf.readString();
        String description = buf.readString();
        String credits = buf.readString();

        int imageCount = buf.readVarInt();
        List<String> imageFileNames = new ArrayList<>(imageCount);
        for (int i = 0; i < imageCount; i++) {
            imageFileNames.add(buf.readString());
        }

        int fieldCount = buf.readVarInt();
        List<CustomFieldData> customFields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            String fieldTitle = buf.readString();
            String fieldContent = buf.readString();
            customFields.add(new CustomFieldData(fieldTitle, fieldContent));
        }

        return new BuildData(id, lastModified, ownerUuid, name, coordinates, dimension, description, credits, imageFileNames, customFields);
    }

    public static PermissionLevel readPermission(ByteBufReader buf) {
        int ordinal = buf.readVarInt();
        return PermissionLevel.values()[ordinal];
    }

    public static UUID readUUID(ByteBufReader buf) {
        return buf.readUUID();
    }

    // ---- Private helpers ----

    private static void writeNote(ByteBufWriter buf, NoteData note) {
        buf.writeUUID(note.id());
        buf.writeLong(note.lastModified());
        buf.writeUUID(note.ownerUuid());
        buf.writeString(note.title());
        buf.writeString(note.content());
    }

    private static void writeBuild(ByteBufWriter buf, BuildData build) {
        buf.writeUUID(build.id());
        buf.writeLong(build.lastModified());
        buf.writeUUID(build.ownerUuid());
        buf.writeString(build.name());
        buf.writeString(build.coordinates());
        buf.writeString(build.dimension());
        buf.writeString(build.description());
        buf.writeString(build.credits());

        buf.writeVarInt(build.imageFileNames().size());
        for (String fileName : build.imageFileNames()) {
            buf.writeString(fileName);
        }

        buf.writeVarInt(build.customFields().size());
        for (CustomFieldData field : build.customFields()) {
            buf.writeString(field.title());
            buf.writeString(field.content());
        }
    }
}
