package com.disqt.disquests.client.gui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DropTrackerTest {

  /** A fixed rectangle that logs its highlight changes. */
  private static final class Box implements DropTracker.Zone {
    final String id;
    final int x, y, w, h;
    final List<String> log;

    Box(String id, int x, int y, int w, int h, List<String> log) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.log = log;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public int x() {
      return x;
    }

    @Override
    public int y() {
      return y;
    }

    @Override
    public int width() {
      return w;
    }

    @Override
    public int height() {
      return h;
    }

    @Override
    public void highlighted(boolean on) {
      log.add(id + (on ? "+" : "-"));
    }
  }

  // Three stacked 100x12 rows, then a drop strip below them.
  private final List<String> log = new ArrayList<>();
  private final Box a = new Box("a", 10, 0, 100, 12, log);
  private final Box b = new Box("b", 10, 12, 100, 12, log);
  private final Box c = new Box("c", 10, 24, 100, 12, log);
  private final DropTracker.Zone out =
      DropTracker.area(
          "out", () -> 10, () -> 40, () -> 100, () -> 10, on -> log.add(on ? "out+" : "out-"));
  private final List<DropTracker.Zone> zones = List.of(a, b, c, out);

  @Nested
  class HitTest {
    static Stream<Arguments> points() {
      return Stream.of(
          Arguments.of("inside first row", 50, 5, "a"),
          Arguments.of("top-left corner is inside", 10, 0, "a"),
          Arguments.of("bottom edge belongs to next row", 50, 12, "b"),
          Arguments.of("right edge is outside", 110, 5, null),
          Arguments.of("left of rows", 9, 5, null),
          Arguments.of("gap above the strip", 50, 37, null),
          Arguments.of("drop strip", 50, 45, "out"),
          Arguments.of("below everything", 50, 60, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("points")
    void findsTheZoneUnderThePoint(String name, int x, int y, String expected) {
      DropTracker.Zone hit = DropTracker.at(zones, null, x, y);
      assertEquals(expected, hit == null ? null : hit.id());
    }

    @Test
    void aRowIsNotItsOwnTarget() {
      assertNull(DropTracker.at(zones, b, 50, 15));
    }

    @Test
    void areaFollowsItsSuppliers() {
      int[] y = {100};
      DropTracker.Zone moving =
          DropTracker.area("m", () -> 0, () -> y[0], () -> 10, () -> 10, on -> {});
      assertNull(DropTracker.at(List.of(moving), null, 5, 5));
      y[0] = 0;
      assertSame(moving, DropTracker.at(List.of(moving), null, 5, 5));
    }
  }

  @Nested
  class Highlight {
    private DropTracker tracker;

    @BeforeEach
    void setUp() {
      tracker = new DropTracker(zones);
    }

    @Test
    void keepsOneZoneHighlightedAsTheCursorMoves() {
      tracker.moved(a, 50, 5); // over the source itself: nothing
      tracker.moved(a, 50, 15); // b
      tracker.moved(a, 60, 16); // still b: no change
      tracker.moved(a, 50, 30); // c
      tracker.moved(a, 50, 45); // out
      tracker.moved(a, 50, 60); // nowhere
      assertEquals(List.of("b+", "b-", "c+", "c-", "out+", "out-"), log);
      assertNull(tracker.current());
    }

    @Test
    void dropReturnsTheZoneAndClearsTheHighlight() {
      tracker.moved(a, 50, 30);
      DropTracker.Zone target = tracker.dropped(a, 50, 30);
      assertSame(c, target);
      assertEquals(List.of("c+", "c-"), log);
      assertNull(tracker.current());
    }

    @Test
    void cancelClearsTheHighlight() {
      tracker.moved(a, 50, 15);
      tracker.clear();
      assertEquals(List.of("b+", "b-"), log);
      assertNull(tracker.current());
    }
  }
}
