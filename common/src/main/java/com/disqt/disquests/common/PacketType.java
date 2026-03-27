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

  PacketType(byte id) {
    this.id = id;
  }

  public byte getId() {
    return id;
  }

  private static final PacketType[] BY_ID;

  static {
    BY_ID = new PacketType[256];
    for (PacketType type : values()) {
      BY_ID[type.id & 0xFF] = type;
    }
  }

  public static PacketType fromId(byte id) {
    PacketType type = BY_ID[id & 0xFF];
    if (type == null) {
      throw new IllegalArgumentException("Unknown packet type: 0x" + String.format("%02X", id));
    }
    return type;
  }
}
