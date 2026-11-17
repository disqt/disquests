package com.disqt.disquests.client.gui.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.data.NoticeText.Message;
import com.disqt.disquests.client.gui.tree.SuggestionText.Line;
import com.disqt.disquests.client.gui.tree.Suggestions.Suggestion;
import com.disqt.disquests.common.graph.Refusal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SuggestionTextTest {

  private static final UUID ID = UUID.randomUUID();
  private static final String K = "gui.disquests.suggestion.";

  private static Message msg(String key, Object... args) {
    return new Message(key, List.of(args));
  }

  @Nested
  class Addable {
    @Test
    void titleOnly() {
      assertEquals(
          new Line("Wagons", null, true, null),
          SuggestionText.of(new Suggestion(ID, "Wagons", null, null), 3));
    }

    @Test
    void insideAnotherQuestNote() {
      assertEquals(
          new Line("Wagons", null, true, msg(K + "inside_another")),
          SuggestionText.of(new Suggestion(ID, "Wagons", null, Suggestions.INSIDE_ANOTHER), 3));
    }

    @Test
    void missingTitleReadsEmpty() {
      assertEquals(
          new Line("", null, true, null),
          SuggestionText.of(new Suggestion(ID, null, null, null), 3));
    }
  }

  @Nested
  class Greyed {
    static Stream<Arguments> cases() {
      Message needOwner = msg("gui.disquests.refusal.need_owner");
      return Stream.of(
          Arguments.of("loop", Refusal.LOOP, null, msg("gui.disquests.refusal.loop")),
          Arguments.of(
              "too deep carries the limit",
              Refusal.TOO_DEEP,
              null,
              msg("gui.disquests.refusal.too_deep", 4)),
          Arguments.of(
              "need owner, holding Read",
              Refusal.NEED_OWNER,
              "READ",
              msg(K + "with_role", needOwner, msg(K + "role_read"))),
          Arguments.of(
              "need owner, holding Write",
              Refusal.NEED_OWNER,
              "WRITE",
              msg(K + "with_role", needOwner, msg(K + "role_write"))),
          Arguments.of(
              "need owner, holding nothing",
              Refusal.NEED_OWNER,
              "",
              msg(K + "with_role", needOwner, msg(K + "role_none"))),
          Arguments.of("need owner without a note", Refusal.NEED_OWNER, null, needOwner),
          Arguments.of(
              "a note on another refusal is ignored",
              Refusal.COMPLETED,
              "READ",
              msg("gui.disquests.refusal.completed")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void reasonInBracketsAfterTheTitle(
        String name, Refusal reason, String note, Message expectedReason) {
      assertEquals(
          new Line("Les wagons", msg(K + "reason", expectedReason), false, null),
          SuggestionText.of(new Suggestion(ID, "Les wagons", reason, note), 4));
    }
  }
}
