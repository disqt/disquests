package com.disqt.disquests.client.gui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.client.gui.component.TreeRowGeometry.Part;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TreeRowGeometryTest {

  private static final int WIDTH = 100;

  private static TreeRowGeometry row(
      int depth,
      boolean edit,
      int children,
      boolean locked,
      boolean completed,
      boolean removable,
      boolean draggable) {
    return new TreeRowGeometry(
        depth, edit, children, locked, completed, removable, draggable, WIDTH);
  }

  /** What a row offers. */
  record Offers(boolean toggle, boolean remove, boolean drag) {}

  /** Where its parts start. */
  record Xs(int toggle, int icon, int title, int remove) {}

  @Nested
  class Parts {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of(
              "read, with subquests",
              row(0, false, 2, false, false, true, true),
              new Offers(true, false, false)),
          Arguments.of(
              "read, no subquests",
              row(0, false, 0, false, false, true, true),
              new Offers(false, false, false)),
          Arguments.of(
              "edit, removable and draggable",
              row(0, true, 2, false, false, true, true),
              new Offers(false, true, true)),
          Arguments.of(
              "edit, neither",
              row(0, true, 0, false, false, false, false),
              new Offers(false, false, false)),
          Arguments.of(
              "edit, completed is frozen",
              row(0, true, 0, false, true, true, true),
              new Offers(false, false, false)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void offers(String name, TreeRowGeometry g, Offers expected) {
      assertEquals(expected, new Offers(g.hasToggle(), g.hasRemove(), g.canDrag()));
    }
  }

  @Nested
  class Positions {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of(
              "top level", row(0, false, 0, false, false, false, false), new Xs(2, 11, 11, 100)),
          Arguments.of(
              "locked, two levels down",
              row(2, false, 0, true, false, false, false),
              new Xs(22, 31, 41, 100)),
          Arguments.of(
              "completed, edit", row(1, true, 0, false, true, true, true), new Xs(12, 21, 31, 100)),
          Arguments.of(
              "removable, edit",
              row(1, true, 0, false, false, true, true),
              new Xs(12, 21, 21, 88)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void partsStartAt(String name, TreeRowGeometry g, Xs expected) {
      assertEquals(expected, new Xs(g.toggleX(), g.iconX(), g.titleX(), g.removeX()));
    }
  }

  @Nested
  class Hits {
    static Stream<Arguments> cases() {
      TreeRowGeometry read = row(1, false, 3, false, false, false, false);
      TreeRowGeometry edit = row(1, true, 3, false, false, true, true);
      return Stream.of(
          Arguments.of("left of the toggle", read, 11.0, Part.BODY),
          Arguments.of("toggle start", read, 12.0, Part.TOGGLE),
          Arguments.of("toggle end", read, 20.9, Part.TOGGLE),
          Arguments.of("after the toggle", read, 21.0, Part.BODY),
          Arguments.of("no toggle in edit mode", edit, 15.0, Part.BODY),
          Arguments.of("before the remove zone", edit, 87.9, Part.BODY),
          Arguments.of("remove zone", edit, 88.0, Part.REMOVE),
          Arguments.of("no remove zone in read mode", read, 95.0, Part.BODY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void partAt(String name, TreeRowGeometry g, double relX, Part expected) {
      assertEquals(expected, g.partAt(relX));
    }
  }

  @Nested
  class Handle {
    @Test
    void aDraggableRowHasAGripInTheToggleSlot() {
      TreeRowGeometry g = row(1, true, 0, false, false, true, true);
      assertTrue(g.hasHandle());
      // Toggle slot starts at 12 and is 9 wide; the 5 px grip is centred in it
      assertEquals(14, g.handleX());
      assertEquals(Part.BODY, g.partAt(g.handleX() + 1.0));
    }

    static Stream<Arguments> noHandle() {
      return Stream.of(
          Arguments.of("read mode", row(0, false, 0, false, false, true, true)),
          Arguments.of("not draggable", row(0, true, 0, false, false, true, false)),
          Arguments.of("completed", row(0, true, 0, false, true, true, true)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noHandle")
    void otherRowsHaveNone(String name, TreeRowGeometry g) {
      assertFalse(g.hasHandle());
    }
  }

  @Nested
  class Trailing {
    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("nothing", 0, "", ""),
          Arguments.of("count only", 2, "", "(2)"),
          Arguments.of("editing only", 0, "(editing)", "(editing)"),
          Arguments.of("count then editing", 3, "(editing)", "(3) (editing)"),
          Arguments.of("null label", 1, null, "(1)"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void follows(String name, int children, String editing, String expected) {
      assertEquals(expected, TreeRowGeometry.trailing(children, editing));
    }
  }
}
