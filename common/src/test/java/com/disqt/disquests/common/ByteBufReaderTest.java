package com.disqt.disquests.common;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ByteBufReaderTest {

    @Test
    void readVarInt_negativeLengthString_throws() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeVarInt(-1);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> r.readString());
    }

    @Test
    void readString_exceedsMaxLength_throws() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeString("a".repeat(200));
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> r.readString(100));
    }

    @Test
    void readString_withinMaxLength_succeeds() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeString("hello");
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertEquals("hello", r.readString(100));
    }

    @Test
    void readVarInt_tooManyBytes_throws() {
        byte[] bad = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01};
        ByteBufReader r = new ByteBufReader(bad);
        assertThrows(IllegalArgumentException.class, r::readVarInt);
    }

    @Test
    void readVarInt_truncatedBuffer_throws() {
        byte[] truncated = {(byte) 0x80, (byte) 0x80};
        ByteBufReader r = new ByteBufReader(truncated);
        assertThrows(IllegalArgumentException.class, r::readVarInt);
    }

    @Test
    void readBytes_negativeLengthArray_throws() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeVarInt(-1);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> r.readBytes());
    }

    @Test
    void readBytes_validData_succeeds() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeBytes(new byte[]{1, 2, 3});
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertArrayEquals(new byte[]{1, 2, 3}, r.readBytes());
    }

    @Test
    void readBytes_exceedsBuffer_throws() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeVarInt(100);
        w.writeByte(1);
        w.writeByte(2);
        w.writeByte(3);
        w.writeByte(4);
        w.writeByte(5);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> r.readBytes());
    }

    @Test
    void readString_exceedsBuffer_throws() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeVarInt(100);
        w.writeByte((byte) 'a');
        w.writeByte((byte) 'b');
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> r.readString());
    }

    @Test
    void readLong_roundTrip() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeLong(Long.MAX_VALUE);
        w.writeLong(Long.MIN_VALUE);
        w.writeLong(0L);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertEquals(Long.MAX_VALUE, r.readLong());
        assertEquals(Long.MIN_VALUE, r.readLong());
        assertEquals(0L, r.readLong());
    }

    @Test
    void readUUID_roundTrip() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        ByteBufWriter w = new ByteBufWriter();
        w.writeUUID(uuid);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertEquals(uuid, r.readUUID());
    }

    @Test
    void readBoolean_roundTrip() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeBoolean(true);
        w.writeBoolean(false);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertTrue(r.readBoolean());
        assertFalse(r.readBoolean());
    }

    @Test
    void readByte_roundTrip() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(0x42);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertEquals((byte) 0x42, r.readByte());
    }

    @Test
    void writeBytes_roundTrip() {
        byte[] input = {10, 20, 30, 40, 50};
        ByteBufWriter w = new ByteBufWriter();
        w.writeBytes(input);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertArrayEquals(input, r.readBytes());
    }

    @Test
    void remaining_tracksPosition() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeBoolean(true);
        w.writeLong(123L);
        w.writeVarInt(7);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        int initial = r.remaining();
        r.readBoolean();
        assertEquals(initial - 1, r.remaining());
        r.readLong();
        assertEquals(initial - 1 - 8, r.remaining());
        r.readVarInt();
        assertEquals(0, r.remaining());
    }
}
