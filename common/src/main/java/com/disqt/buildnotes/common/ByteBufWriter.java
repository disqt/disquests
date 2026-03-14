package com.disqt.buildnotes.common;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ByteBufWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public void writeByte(int value) {
        out.write(value);
    }

    public void writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    public void writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    public void writeLong(long value) {
        for (int i = 56; i >= 0; i -= 8) {
            out.write((int) (value >>> i));
        }
    }

    public void writeUUID(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    public void writeBoolean(boolean value) {
        out.write(value ? 1 : 0);
    }

    public void writeBytes(byte[] data) {
        writeVarInt(data.length);
        out.write(data, 0, data.length);
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
