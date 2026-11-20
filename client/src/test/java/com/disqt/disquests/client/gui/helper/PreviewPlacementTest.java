package com.disqt.disquests.client.gui.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.gui.helper.PreviewPlacement.Position;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PreviewPlacementTest {

  private static final int SCREEN_W = 400;
  private static final int SCREEN_H = 240;

  static Stream<Arguments> cases() {
    return Stream.of(
        // anchorLeft, anchorRight, rowTop, cardW, cardH, expected
        Arguments.of("right of the pane, on the row", 0, 100, 40, 150, 50, new Position(106, 40)),
        Arguments.of("raised at the bottom edge", 0, 100, 220, 150, 50, new Position(106, 190)),
        Arguments.of("at the top", 0, 100, -5, 150, 50, new Position(106, 0)),
        Arguments.of(
            "left of the anchor when the right is full",
            300,
            380,
            40,
            150,
            50,
            new Position(144, 40)),
        Arguments.of("clamped when neither side fits", 100, 300, 40, 250, 50, new Position(0, 40)),
        Arguments.of(
            "taller than the screen keeps to the top", 0, 100, 40, 150, 300, new Position(106, 0)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void placed(String name, int left, int right, int top, int w, int h, Position expected) {
    assertEquals(expected, PreviewPlacement.beside(left, right, top, w, h, SCREEN_W, SCREEN_H));
  }
}
