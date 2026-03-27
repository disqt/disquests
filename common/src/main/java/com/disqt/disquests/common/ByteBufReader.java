package com.disqt.disquests.common;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ByteBufReader {

  public static final int MAX_STRING_LENGTH = 65536;
  public static final int MAX_BYTES_LENGTH = 1048576;

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
      if (pos >= data.length) {
        throw new IllegalArgumentException("VarInt extends past end of buffer");
      }
      current = data[pos++];
      value |= (current & 0x7F) << shift;
      shift += 7;
      if (shift > 35) {
        throw new IllegalArgumentException("VarInt too large");
      }
    } while ((current & 0x80) != 0);
    return value;
  }

  public String readString() {
    return readString(MAX_STRING_LENGTH);
  }

  public String readString(int maxLength) {
    int length = readVarInt();
    if (length < 0 || length > maxLength) {
      throw new IllegalArgumentException(
          "String length " + length + " outside bounds [0, " + maxLength + "]");
    }
    if (pos + length > data.length) {
      throw new IllegalArgumentException("String length " + length + " exceeds remaining buffer");
    }
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
    if (length < 0 || length > MAX_BYTES_LENGTH) {
      throw new IllegalArgumentException(
          "Bytes length " + length + " outside bounds [0, " + MAX_BYTES_LENGTH + "]");
    }
    if (pos + length > data.length) {
      throw new IllegalArgumentException("Bytes length " + length + " exceeds remaining buffer");
    }
    byte[] result = new byte[length];
    System.arraycopy(data, pos, result, 0, length);
    pos += length;
    return result;
  }

  public int remaining() {
    return data.length - pos;
  }
}
