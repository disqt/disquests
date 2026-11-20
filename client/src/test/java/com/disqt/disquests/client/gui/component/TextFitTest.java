package com.disqt.disquests.client.gui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextFitTest {

  /** Every character, the space and the ellipsis included, is 6 px wide. */
  private static final ToIntFunction<String> WIDTH = s -> 6 * s.length();

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of("whole title, nothing after", "Gare", "", 24, "Gare"),
        Arguments.of("whole title and reason", "Gare", "(x)", 48, "Gare"),
        Arguments.of("title shortened, reason kept", "Les wagons", "(loop)", 80, "Les w…"),
        Arguments.of("trailing space dropped", "Les wagons", "", 26, "Les…"),
        Arguments.of("one letter and the ellipsis", "Wagons", "", 12, "W…"),
        Arguments.of("no room for any title", "Wagons", "", 11, ""),
        Arguments.of("the reason alone takes all the room", "Wagons", "(too deep)", 66, ""),
        Arguments.of("empty title", "", "(loop)", 100, ""),
        Arguments.of("count kept after a tree title", "Westenheim hub", "(3)", 60, "Weste…"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void shortensOnlyTheTitle(
      String name, String title, String suffix, int available, String expected) {
    assertEquals(expected, TextFit.title(title, suffix, available, WIDTH));
  }
}
