package com.disqt.buildnotes.common;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ByteBufReader {

    private final byte[] data;
    private int pos;

    public ByteBufReader(byte[] data) {
        this.data = data;
        this.pos = 0;
    }

    public byte readByte() {
        return data[pos++];
    }

    public int readVarInt() {
        int value = 0;
        int shift = 0;
        byte current;
        do {
            current = data[pos++];
            value |= (current & 0x7F) << shift;
            shift += 7;
            if (shift > 35) {
                throw new RuntimeException("VarInt too large");
            }
        } while ((current & 0x80) != 0);
        return value;
    }

    public String readString() {
        int length = readVarInt();
        String str = new String(data, pos, length, StandardCharsets.UTF_8);
        pos += length;
        return str;
    }

    public long readLong() {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (data[pos++] & 0xFFL);
        }
        return value;
    }

    public UUID readUUID() {
        long most = readLong();
        long least = readLong();
        return new UUID(most, least);
    }

    public boolean readBoolean() {
        return data[pos++] != 0;
    }

    public byte[] readBytes() {
        int length = readVarInt();
        byte[] result = new byte[length];
        System.arraycopy(data, pos, result, 0, length);
        pos += length;
        return result;
    }

    public int remaining() {
        return data.length - pos;
    }
}
