package com.disqt.disquests.common;

public enum PacketType {
    // C2S
    REQUEST_SYNC((byte) 0x01),
    SAVE_QUEST((byte) 0x02),
    DELETE_QUEST((byte) 0x03),
    JOIN_QUEST((byte) 0x04),
    REQUEST_COLLABORATION((byte) 0x05),
    RESPOND_COLLABORATION((byte) 0x06),
    UPDATE_CONTRIBUTORS((byte) 0x07),
    UPDATE_VISIBILITY((byte) 0x08),
    PIN_QUEST((byte) 0x09),
    LEAVE_QUEST((byte) 0x0A),

    // S2C
    HANDSHAKE((byte) 0x10),
    SYNC_MY_QUESTS((byte) 0x11),
    SYNC_SERVER_QUESTS((byte) 0x12),
    UPDATE_QUEST((byte) 0x13),
    DELETE_QUEST_S2C((byte) 0x14),
    COLLABORATION_REQUEST((byte) 0x15),
    COLLABORATION_RESPONSE((byte) 0x16),
    SYNC_PENDING_REQUESTS((byte) 0x17);

    private final byte id;

    PacketType(byte id) { this.id = id; }

    public byte getId() { return id; }

    public static PacketType fromId(byte id) {
        for (PacketType type : values()) {
            if (type.id == id) return type;
        }
        throw new IllegalArgumentException("Unknown packet type: 0x" + String.format("%02X", id));
    }
}
