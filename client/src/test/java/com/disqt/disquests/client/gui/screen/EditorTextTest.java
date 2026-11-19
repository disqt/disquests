package com.disqt.disquests.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EditorTextTest {

  private static final String PRIVATE = "gui.disquests.tooltip.private";
  private static final String CLOSED = "gui.disquests.tooltip.closed";
  private static final String OPEN = "gui.disquests.tooltip.open";

  @Nested
  class VisibilityHint {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("private, top level", Visibility.PRIVATE, false, List.of(PRIVATE)),
          Arguments.of(
              "private stands alone inside another", Visibility.PRIVATE, true, List.of(PRIVATE)),
          Arguments.of("closed, top level", Visibility.CLOSED, false, List.of(CLOSED)),
          Arguments.of(
              "closed inside another",
              Visibility.CLOSED,
              true,
              List.of(CLOSED, EditorText.VISIBILITY_IN_PARENT)),
          Arguments.of("open, top level", Visibility.OPEN, false, List.of(OPEN)),
          Arguments.of(
              "open inside another",
              Visibility.OPEN,
              true,
              List.of(OPEN, EditorText.VISIBILITY_IN_PARENT)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void sentences(String name, Visibility v, boolean inside, List<String> expected) {
      assertEquals(expected, EditorText.visibilityHintKeys(v, inside));
    }
  }
}
