package com.disqt.disquests.common;

import org.junit.jupiter.api.Test;
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
}
