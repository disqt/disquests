package com.disqt.disquests.client.gui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SuggestionSelectionTest {

  private static final boolean Y = true;
  private static final boolean N = false;

  @Nested
  class First {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("empty", new boolean[] {}, SuggestionSelection.NONE),
          Arguments.of("all greyed", new boolean[] {N, N}, SuggestionSelection.NONE),
          Arguments.of("first enabled", new boolean[] {Y, N, Y}, 0),
          Arguments.of("leading greyed skipped", new boolean[] {N, N, Y}, 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void firstPickableRow(String name, boolean[] enabled, int expected) {
      assertEquals(expected, SuggestionSelection.first(enabled));
    }
  }

  @Nested
  class Move {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("down to next", new boolean[] {Y, Y, Y}, 0, 1, 1),
          Arguments.of("up to previous", new boolean[] {Y, Y, Y}, 2, -1, 1),
          Arguments.of("down skips greyed", new boolean[] {Y, N, N, Y}, 0, 1, 3),
          Arguments.of("up skips greyed", new boolean[] {Y, N, Y}, 2, -1, 0),
          Arguments.of("down stops at last pickable", new boolean[] {Y, Y, N}, 1, 1, 1),
          Arguments.of("up stops at first pickable", new boolean[] {N, Y, Y}, 1, -1, 1),
          Arguments.of("from none goes to first", new boolean[] {N, Y}, -1, 1, 1),
          Arguments.of("from none upward also first", new boolean[] {N, Y}, -1, -1, 1),
          Arguments.of("stale index past end resets", new boolean[] {Y}, 4, 1, 0),
          Arguments.of("current greyed resets", new boolean[] {N, Y}, 0, -1, 1),
          Arguments.of("all greyed stays none", new boolean[] {N, N}, -1, 1, -1),
          Arguments.of("larger step moves one row", new boolean[] {Y, Y, Y}, 0, 5, 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void arrowsSkipGreyedRows(String name, boolean[] enabled, int current, int step, int expected) {
      assertEquals(expected, SuggestionSelection.move(enabled, current, step));
    }
  }
}
