package com.disqt.disquests.client.gui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.gui.component.DropdownLayout.Placement;
import com.disqt.disquests.client.gui.component.DropdownLayout.Root;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DropdownLayoutTest {

  @Nested
  class Size {
    static Stream<Arguments> widths() {
      return Stream.of(
          Arguments.of("anchor wider than the rows", 120, 60, 3, 400, 120),
          Arguments.of("rows wider than the anchor", 80, 150, 3, 400, 154),
          Arguments.of("no anchor width", 0, 60, 3, 400, 64),
          Arguments.of("scrollbar room past ten rows", 0, 60, 11, 400, 68),
          Arguments.of("never wider than the root", 80, 500, 3, 300, 300));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("widths")
    void width(String name, int min, int widest, int rows, int rootWidth, int expected) {
      assertEquals(expected, DropdownLayout.width(min, widest, rows, rootWidth));
    }

    static Stream<Arguments> heights() {
      return Stream.of(
          Arguments.of("one row", 1, 300, 17),
          Arguments.of("ten rows", 10, 300, 134),
          Arguments.of("more rows scroll", 25, 300, 134),
          Arguments.of("never taller than the root", 10, 100, 100));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("heights")
    void height(String name, int rows, int rootHeight, int expected) {
      assertEquals(expected, DropdownLayout.height(rows, rootHeight));
    }
  }

  @Nested
  class Place {
    static final Root SCREEN = new Root(0, 0, 200, 100, 0, 0);

    // anchor line at (x, y), 10 px tall; panel width x height
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("below when it fits", 10, 20, 50, 30, SCREEN, false, new Placement(10, 31)),
          Arguments.of(
              "above when below is full", 10, 80, 50, 30, SCREEN, false, new Placement(10, 49)),
          Arguments.of("above when preferred", 10, 50, 50, 30, SCREEN, true, new Placement(10, 19)),
          Arguments.of(
              "below when above is full", 10, 10, 50, 30, SCREEN, true, new Placement(10, 21)),
          Arguments.of(
              "fits nowhere, kept on screen", 10, 40, 50, 80, SCREEN, false, new Placement(10, 20)),
          Arguments.of(
              "kept inside on the right", 180, 20, 50, 30, SCREEN, false, new Placement(150, 31)),
          Arguments.of(
              "root padding taken off the offset",
              10,
              20,
              50,
              30,
              new Root(0, 0, 200, 100, 6, 4),
              false,
              new Placement(4, 27)),
          Arguments.of(
              "root not at the origin",
              30,
              40,
              50,
              30,
              new Root(20, 10, 200, 100, 0, 0),
              false,
              new Placement(10, 41)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void nextToTheAnchorLine(
        String name,
        int x,
        int y,
        int width,
        int height,
        Root root,
        boolean preferAbove,
        Placement expected) {
      assertEquals(expected, DropdownLayout.place(x, y, 10, width, height, root, preferAbove));
    }
  }
}
