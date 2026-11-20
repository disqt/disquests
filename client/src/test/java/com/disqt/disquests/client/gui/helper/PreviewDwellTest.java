package com.disqt.disquests.client.gui.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PreviewDwellTest {

  private final PreviewDwell<String> dwell = new PreviewDwell<>(500);

  @Test
  void showsOnlyAfterTheDelayOnTheSameThing() {
    assertNull(dwell.update("a", 1000));
    assertNull(dwell.update("a", 1499));
    assertEquals("a", dwell.update("a", 1500));
    assertEquals("a", dwell.update("a", 3000));
  }

  @Test
  void movingToAnotherRowRestartsTheWait() {
    dwell.update("a", 0);
    assertEquals("a", dwell.update("a", 600));
    assertNull(dwell.update("b", 700));
    assertNull(dwell.update("b", 1100));
    assertEquals("b", dwell.update("b", 1200));
  }

  @Test
  void leavingResetsIt() {
    dwell.update("a", 0);
    assertNull(dwell.update(null, 400));
    assertNull(dwell.update("a", 450));
    assertNull(dwell.update("a", 900));
    assertEquals("a", dwell.update("a", 950));
  }

  @Test
  void theDefaultDelayIsHalfASecond() {
    PreviewDwell<String> d = new PreviewDwell<>();
    d.update("a", 0);
    assertNull(d.update("a", PreviewDwell.DELAY_MS - 1));
    assertEquals("a", d.update("a", PreviewDwell.DELAY_MS));
  }
}
