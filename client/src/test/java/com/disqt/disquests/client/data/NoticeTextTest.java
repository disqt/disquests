package com.disqt.disquests.client.data;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.Notice;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NoticeTextTest {

  private static final UUID ID = UUID.randomUUID();

  @ParameterizedTest
  @EnumSource(
      value = Notice.Kind.class,
      names = {"QUEST_UNLOCKED", "QUEST_UNLOCKED_FOLLOWED"})
  void bothUnlockKindsReadAsNewQuest(Notice.Kind kind) {
    assertEquals(
        new NoticeText.Message("gui.disquests.notice.unlocked", List.of("Dig")),
        NoticeText.of(new Notice(kind, ID, "Dig", null), 3));
  }

  @Test
  void setPrivateNamesTheQuest() {
    assertEquals(
        new NoticeText.Message("gui.disquests.notice.set_private", List.of("Dig")),
        NoticeText.of(new Notice(Notice.Kind.SET_PRIVATE, ID, "Dig", null), 3));
  }

  @Nested
  class Refused {
    @Test
    void withTitle() {
      assertEquals(
          new NoticeText.Message(
              "gui.disquests.notice.refused_titled",
              List.of("Dig", new NoticeText.Message("gui.disquests.refusal.loop", List.of()))),
          NoticeText.of(new Notice(Notice.Kind.CHANGE_REFUSED, ID, "Dig", Refusal.LOOP), 3));
    }

    @Test
    void withoutTitle() {
      assertEquals(
          new NoticeText.Message(
              "gui.disquests.notice.refused",
              List.of(new NoticeText.Message("gui.disquests.refusal.not_visible", List.of()))),
          NoticeText.of(
              new Notice(Notice.Kind.CHANGE_REFUSED, null, null, Refusal.NOT_VISIBLE), 3));
    }

    @Test
    void tooDeepCarriesTheLimit() {
      assertEquals(
          new NoticeText.Message("gui.disquests.refusal.too_deep", List.of(4)),
          NoticeText.refusal(Refusal.TOO_DEEP, 4));
    }

    @Test
    void everyRefusalHasItsLowerCaseKey() {
      for (Refusal r : Refusal.values()) {
        assertEquals(
            "gui.disquests.refusal." + r.name().toLowerCase(Locale.ROOT),
            NoticeText.refusal(r, 3).key());
      }
    }

    @Test
    void missingReasonFallsBackToUnknown() {
      assertEquals("gui.disquests.refusal.unknown", NoticeText.refusal(null, 3).key());
    }
  }
}
