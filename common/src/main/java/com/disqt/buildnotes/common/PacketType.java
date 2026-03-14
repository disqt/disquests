package com.disqt.buildnotes.common;

public enum PacketType {
    // C2S
    REQUEST_DATA((byte) 0x01),
    SAVE_NOTE((byte) 0x02),
    DELETE_NOTE((byte) 0x03),
    SAVE_BUILD((byte) 0x04),
    DELETE_BUILD((byte) 0x05),
    UPLOAD_IMAGE_CHUNK((byte) 0x06),
    REQUEST_IMAGE((byte) 0x07),

    // S2C
    HANDSHAKE((byte) 0x10),
    INITIAL_SYNC((byte) 0x11),
    UPDATE_NOTE((byte) 0x12),
    DELETE_NOTE_S2C((byte) 0x13),
    UPDATE_BUILD((byte) 0x14),
    DELETE_BUILD_S2C((byte) 0x15),
    IMAGE_CHUNK((byte) 0x16),
    IMAGE_NOT_FOUND((byte) 0x17),
    UPDATE_PERMISSION((byte) 0x18);

    private final byte id;

    PacketType(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }

    public static PacketType fromId(byte id) {
        for (PacketType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown packet type: 0x" + String.format("%02X", id));
    }
}
