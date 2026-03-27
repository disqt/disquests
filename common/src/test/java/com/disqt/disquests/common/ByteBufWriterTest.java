package com.disqt.disquests.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ByteBufWriterTest {

  @Test
  void writeVarInt_multiByteValues() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeVarInt(128);
    w.writeVarInt(16384);
    w.writeVarInt(Integer.MAX_VALUE);
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertEquals(128, r.readVarInt());
    assertEquals(16384, r.readVarInt());
    assertEquals(Integer.MAX_VALUE, r.readVarInt());
  }

  @Test
  void writeString_emptyString() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeString("");
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertEquals("", r.readString());
  }

  @Test
  void writeVarInt_zero() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeVarInt(0);
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertEquals(0, r.readVarInt());
  }
}
