package com.disqt.buildnotes.common;

import com.disqt.buildnotes.common.model.BuildData;
import com.disqt.buildnotes.common.model.CustomFieldData;
import com.disqt.buildnotes.common.model.NoteData;
import com.disqt.buildnotes.common.model.PermissionLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTest {

    @Test
    void roundTripNote() {
        NoteData note = new NoteData(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                UUID.randomUUID(),
                "Test Note",
                "This is the note content."
        );

        byte[] packet = PacketCodec.writeSaveNote(note);
        ByteBufReader reader = new ByteBufReader(packet);

        PacketType type = PacketCodec.readType(reader);
        assertEquals(PacketType.SAVE_NOTE, type);

        NoteData decoded = PacketCodec.readNote(reader);
        assertEquals(note.id(), decoded.id());
        assertEquals(note.lastModified(), decoded.lastModified());
        assertEquals(note.ownerUuid(), decoded.ownerUuid());
        assertEquals(note.title(), decoded.title());
        assertEquals(note.content(), decoded.content());
        assertEquals(0, reader.remaining());
    }

    @Test
    void roundTripBuild() {
        BuildData build = new BuildData(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                UUID.randomUUID(),
                "Iron Farm",
                "100 64 -200",
                "overworld",
                "A simple iron farm design",
                "Built by leo",
                List.of("screenshot1.png", "screenshot2.png"),
                List.of(
                        new CustomFieldData("Rates", "350 ingots/hr"),
                        new CustomFieldData("Materials", "20 hoppers, 4 chests")
                )
        );

        byte[] packet = PacketCodec.writeSaveBuild(build);
        ByteBufReader reader = new ByteBufReader(packet);

        PacketType type = PacketCodec.readType(reader);
        assertEquals(PacketType.SAVE_BUILD, type);

        BuildData decoded = PacketCodec.readBuild(reader);
        assertEquals(build.id(), decoded.id());
        assertEquals(build.lastModified(), decoded.lastModified());
        assertEquals(build.ownerUuid(), decoded.ownerUuid());
        assertEquals(build.name(), decoded.name());
        assertEquals(build.coordinates(), decoded.coordinates());
        assertEquals(build.dimension(), decoded.dimension());
        assertEquals(build.description(), decoded.description());
        assertEquals(build.credits(), decoded.credits());
        assertEquals(build.imageFileNames(), decoded.imageFileNames());
        assertEquals(build.customFields().size(), decoded.customFields().size());
        assertEquals(build.customFields().get(0).title(), decoded.customFields().get(0).title());
        assertEquals(build.customFields().get(0).content(), decoded.customFields().get(0).content());
        assertEquals(build.customFields().get(1).title(), decoded.customFields().get(1).title());
        assertEquals(build.customFields().get(1).content(), decoded.customFields().get(1).content());
        assertEquals(0, reader.remaining());
    }

    @Test
    void roundTripInitialSync() {
        NoteData note1 = new NoteData(UUID.randomUUID(), 1000L, UUID.randomUUID(), "Note 1", "Content 1");
        NoteData note2 = new NoteData(UUID.randomUUID(), 2000L, UUID.randomUUID(), "Note 2", "Content 2");
        BuildData build1 = new BuildData(
                UUID.randomUUID(), 3000L, UUID.randomUUID(),
                "Build 1", "0 0 0", "overworld", "desc", "credits",
                List.of("img.png"), List.of()
        );

        byte[] packet = PacketCodec.writeInitialSync(List.of(note1, note2), List.of(build1));
        ByteBufReader reader = new ByteBufReader(packet);

        PacketType type = PacketCodec.readType(reader);
        assertEquals(PacketType.INITIAL_SYNC, type);

        int noteCount = reader.readVarInt();
        assertEquals(2, noteCount);
        NoteData decodedNote1 = PacketCodec.readNote(reader);
        assertEquals(note1.id(), decodedNote1.id());
        assertEquals(note1.title(), decodedNote1.title());
        NoteData decodedNote2 = PacketCodec.readNote(reader);
        assertEquals(note2.id(), decodedNote2.id());
        assertEquals(note2.title(), decodedNote2.title());

        int buildCount = reader.readVarInt();
        assertEquals(1, buildCount);
        BuildData decodedBuild = PacketCodec.readBuild(reader);
        assertEquals(build1.id(), decodedBuild.id());
        assertEquals(build1.name(), decodedBuild.name());
        assertEquals(build1.imageFileNames(), decodedBuild.imageFileNames());
        assertEquals(0, reader.remaining());
    }

    @Test
    void roundTripHandshake() {
        byte[] packet = PacketCodec.writeHandshake(PermissionLevel.CAN_EDIT);
        ByteBufReader reader = new ByteBufReader(packet);

        PacketType type = PacketCodec.readType(reader);
        assertEquals(PacketType.HANDSHAKE, type);

        PermissionLevel perm = PacketCodec.readPermission(reader);
        assertEquals(PermissionLevel.CAN_EDIT, perm);
        assertEquals(0, reader.remaining());
    }

    @Test
    void roundTripDeleteNote() {
        UUID noteId = UUID.randomUUID();
        byte[] packet = PacketCodec.writeDeleteNote(noteId);
        ByteBufReader reader = new ByteBufReader(packet);

        PacketType type = PacketCodec.readType(reader);
        assertEquals(PacketType.DELETE_NOTE, type);

        UUID decoded = PacketCodec.readUUID(reader);
        assertEquals(noteId, decoded);
        assertEquals(0, reader.remaining());
    }

    @Test
    void roundTripImageChunk() {
        UUID buildId = UUID.randomUUID();
        String filename = "screenshot.png";
        byte[] chunkData = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};

        byte[] packet = PacketCodec.writeUploadImageChunk(buildId, filename, 10, 3, chunkData);
        ByteBufReader reader = new ByteBufReader(packet);

        PacketType type = PacketCodec.readType(reader);
        assertEquals(PacketType.UPLOAD_IMAGE_CHUNK, type);

        UUID decodedBuildId = reader.readUUID();
        assertEquals(buildId, decodedBuildId);

        String decodedFilename = reader.readString();
        assertEquals(filename, decodedFilename);

        int totalChunks = reader.readVarInt();
        assertEquals(10, totalChunks);

        int chunkIndex = reader.readVarInt();
        assertEquals(3, chunkIndex);

        byte[] decodedData = reader.readBytes();
        assertArrayEquals(chunkData, decodedData);
        assertEquals(0, reader.remaining());
    }

    @Test
    void roundTripUnicodeStrings() {
        NoteData note = new NoteData(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                UUID.randomUUID(),
                "Fran\u00E7ais",
                "Caf\u00E9 cr\u00E8me"
        );

        byte[] packet = PacketCodec.writeSaveNote(note);
        ByteBufReader reader = new ByteBufReader(packet);

        PacketCodec.readType(reader);
        NoteData decoded = PacketCodec.readNote(reader);
        assertEquals("Fran\u00E7ais", decoded.title());
        assertEquals("Caf\u00E9 cr\u00E8me", decoded.content());
        assertEquals(0, reader.remaining());
    }

    @Test
    void roundTripEmptyLists() {
        byte[] packet = PacketCodec.writeInitialSync(List.of(), List.of());
        ByteBufReader reader = new ByteBufReader(packet);

        PacketType type = PacketCodec.readType(reader);
        assertEquals(PacketType.INITIAL_SYNC, type);

        int noteCount = reader.readVarInt();
        assertEquals(0, noteCount);

        int buildCount = reader.readVarInt();
        assertEquals(0, buildCount);

        assertEquals(0, reader.remaining());
    }
}
