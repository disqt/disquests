package com.disqt.disquests.client.gui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DragGhostTest {

  private static final int SCREEN_W = 400;
  private static final int SCREEN_H = 240;

  @Nested
  class Rect {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("below right of the pointer", 100, 50, 60, new int[] {110, 56}),
          Arguments.of("flips left at the right edge", 380, 50, 60, new int[] {310, 56}),
          Arguments.of("flips up at the bottom edge", 100, 235, 60, new int[] {110, 217}),
          Arguments.of("flips both in the corner", 395, 238, 60, new int[] {325, 220}),
          Arguments.of(
              "too wide for either side keeps to the left edge", 200, 50, 250, new int[] {0, 56}),
          Arguments.of("never left of the screen", 5, 50, 500, new int[] {0, 56}),
          Arguments.of("at the origin", 0, 0, 60, new int[] {10, 6}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void placed(String name, int mouseX, int mouseY, int width, int[] expected) {
      DropTracker.Bounds b = DragGhost.rect(mouseX, mouseY, width, 12, SCREEN_W, SCREEN_H);
      assertEquals(expected[0], b.x(), "x");
      assertEquals(expected[1], b.y(), "y");
      assertEquals(width, b.width());
      assertEquals(12, b.height());
    }
  }

  @Nested
  class Width {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("title plus padding", 50, 200, 58),
          Arguments.of("never narrower than the minimum", 5, 200, DragGhost.MIN_WIDTH),
          Arguments.of("never wider than the row", 300, 120, 120));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void sized(String name, int textWidth, int rowWidth, int expected) {
      assertEquals(expected, DragGhost.width(textWidth, rowWidth));
    }
  }
}
