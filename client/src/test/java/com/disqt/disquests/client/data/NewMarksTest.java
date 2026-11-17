package com.disqt.disquests.client.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewMarksTest {

  private final NewMarks marks = new NewMarks();
  private final UUID quest = UUID.randomUUID();
  private final UUID other = UUID.randomUUID();

  @Test
  void markedQuestIsNewUntilSomethingClearsIt() {
    assertFalse(marks.isNew(quest));
    marks.mark(quest);
    assertTrue(marks.isNew(quest));
    assertFalse(marks.isNew(other));
  }

  @Test
  void openingTheQuestRemovesItsMark() {
    marks.mark(quest);
    marks.mark(other);
    marks.opened(quest);
    assertFalse(marks.isNew(quest));
    assertTrue(marks.isNew(other));
  }

  @Test
  void markGoesAfterTheThirdBoardOpening() {
    marks.mark(quest);
    marks.boardOpened();
    assertTrue(marks.isNew(quest));
    marks.boardOpened();
    assertTrue(marks.isNew(quest));
    marks.boardOpened();
    assertFalse(marks.isNew(quest));
  }

  @Test
  void aMarkAddedLaterCountsItsOwnBoardOpenings() {
    marks.mark(quest);
    marks.boardOpened();
    marks.boardOpened();
    marks.mark(other);
    marks.boardOpened();
    assertFalse(marks.isNew(quest));
    assertTrue(marks.isNew(other));
  }

  @Test
  void clearForgetsEveryMark() {
    marks.mark(quest);
    marks.mark(other);
    marks.clear();
    assertFalse(marks.isNew(quest));
    assertFalse(marks.isNew(other));
  }
}
