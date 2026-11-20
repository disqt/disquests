package com.disqt.disquests.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.IntUnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PaneWidthTest {

  private static final IntUnaryOperator FREE = w -> w;
  private static final IntUnaryOperator BOUNDED = w -> PaneWidth.clamp(w, 216, 300);

  @Nested
  class AfterDrag {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("right pane widens leftwards", 216, -30.0, true, FREE, 246),
          Arguments.of("right pane narrows rightwards", 246, 30.0, true, FREE, 216),
          Arguments.of("left pane widens rightwards", 220, 30.0, false, FREE, 250),
          Arguments.of("left pane narrows leftwards", 220, -20.0, false, FREE, 200),
          Arguments.of("rounded to the pixel", 216, -0.6, true, FREE, 217),
          Arguments.of("held at the minimum", 216, 30.0, true, BOUNDED, 216),
          Arguments.of("held at the maximum", 216, -200.0, true, BOUNDED, 300));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void followsTheDivider(
        String name,
        int start,
        double dx,
        boolean paneOnRight,
        IntUnaryOperator clamp,
        int expected) {
      assertEquals(expected, PaneWidth.afterDrag(start, dx, paneOnRight, clamp));
    }
  }

  @Nested
  class Clamp {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("inside", 250, 216, 300, 250),
          Arguments.of("below", 100, 216, 300, 216),
          Arguments.of("above", 400, 216, 300, 300),
          Arguments.of("bounds crossed on a narrow screen: minimum wins", 250, 216, 150, 216));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void keepsWithinBounds(String name, int requested, int min, int max, int expected) {
      assertEquals(expected, PaneWidth.clamp(requested, min, max));
    }
  }
}
