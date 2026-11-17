package com.disqt.disquests.client.gui.component;

import static com.disqt.disquests.client.gui.component.QuestEntryLayout.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuestEntryLayoutTest {

  @Nested
  class TitleRow {

    @Test
    void withoutMarksTheTitleKeepsTodaysRoom() {
      // 200 wide, 50 px of visibility and owner text: 200 - 8 - 50
      assertEquals(
          new QuestEntryLayout(4, 142, NONE, NONE), QuestEntryLayout.of(200, false, 50, 0, 0, 0));
    }

    @Test
    void aLockPushesTheTitleRight() {
      assertEquals(
          new QuestEntryLayout(14, 132, NONE, NONE), QuestEntryLayout.of(200, true, 50, 0, 0, 0));
    }

    @Test
    void theWarningSitsLeftOfTheRightSideTextAndTheNewMarkLeftOfIt() {
      // right side starts at 146; warning 6 wide at 140; NEW 20 wide at 116; title up to 112
      assertEquals(
          new QuestEntryLayout(4, 108, 116, 140), QuestEntryLayout.of(200, false, 50, 0, 20, 6));
    }

    @Test
    void theNewMarkAloneSitsLeftOfTheRightSideText() {
      assertEquals(
          new QuestEntryLayout(4, 118, 126, NONE), QuestEntryLayout.of(200, false, 50, 0, 20, 0));
    }

    @Test
    void thePendingBadgeKeepsTheMarksOffIt() {
      // right side text from 146, the 30 px pending badge from 116: warning at 110, NEW at 86
      assertEquals(
          new QuestEntryLayout(4, 78, 86, 110), QuestEntryLayout.of(200, false, 50, 30, 20, 6));
    }

    @Test
    void theTitleRoomNeverGoesNegative() {
      assertEquals(
          new QuestEntryLayout(14, 0, -22, 2), QuestEntryLayout.of(40, true, 30, 0, 20, 4));
    }
  }

  @Nested
  class WarningHit {

    @ParameterizedTest(name = "({0}, {1}) on the warning: {2}")
    @CsvSource({
      "140, 6, true",
      "145, 12, true",
      "139, 6, false",
      "146, 6, false",
      "142, 1, false",
      "142, 14, false"
    })
    void onlyTheMarkInTheTitleRowHits(double x, double y, boolean hit) {
      QuestEntryLayout layout = QuestEntryLayout.of(200, false, 50, 0, 20, 6);
      assertEquals(hit, layout.onWarning(x, y, 6));
    }

    @Test
    void noWarningNeverHits() {
      assertEquals(false, QuestEntryLayout.of(200, false, 50, 0, 20, 0).onWarning(140, 6, 6));
    }
  }

  @Nested
  class RowInset {

    @ParameterizedTest(name = "depth {0}, toggles in list {1}: indent {2}, slot {3}")
    @CsvSource({"0, false, 0, 0", "0, true, 0, 9", "1, true, 10, 9", "3, true, 30, 9"})
    void depthIndentsAndTheToggleSlotIsReservedOnlyWhenTheListHasToggles(
        int depth, boolean toggles, int indent, int slot) {
      assertEquals(indent, QuestEntryLayout.indent(depth));
      assertEquals(slot, QuestEntryLayout.toggleSlot(toggles));
    }
  }
}
