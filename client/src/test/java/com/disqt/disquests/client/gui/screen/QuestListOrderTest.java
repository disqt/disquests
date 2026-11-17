package com.disqt.disquests.client.gui.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class QuestListOrderTest {

  @Test
  void shouldCollapseList_below640() {
    assertTrue(QuestListOrder.shouldCollapseList(0));
    assertTrue(QuestListOrder.shouldCollapseList(639));
    assertFalse(QuestListOrder.shouldCollapseList(640));
    assertFalse(QuestListOrder.shouldCollapseList(1920));
  }

  @Test
  void clampPanelWidth_between216And60PercentOfScreen() {
    assertEquals(216, QuestListOrder.clampPanelWidth(100, 1000));
    assertEquals(400, QuestListOrder.clampPanelWidth(400, 1000));
    assertEquals(600, QuestListOrder.clampPanelWidth(900, 1000));
    // Narrow screens never go below the minimum.
    assertEquals(216, QuestListOrder.clampPanelWidth(500, 200));
  }
}
