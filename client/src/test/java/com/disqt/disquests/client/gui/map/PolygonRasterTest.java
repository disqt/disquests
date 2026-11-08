package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.gui.map.PolygonRaster.Span;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolygonRasterTest {
  @Test
  void spans_square_fullRows() {
    List<Span> spans =
        PolygonRaster.scanlineSpans(
            new double[] {0, 10, 10, 0}, new double[] {0, 0, 10, 10}, -100, 100);
    assertEquals(10, spans.size());
    for (int y = 0; y < 10; y++) {
      assertEquals(new Span(y, 0, 10), spans.get(y));
    }
  }

  @Test
  void spans_concaveL_twoSpansOnNotchRows() {
    // U-shaped polygon: a notch cut from the top between x=3 and x=7 down to y=6
    double[] xs = {0, 3, 3, 7, 7, 10, 10, 0};
    double[] ys = {0, 0, 6, 6, 0, 0, 10, 10};
    List<Span> spans = PolygonRaster.scanlineSpans(xs, ys, 0, 10);
    // rows 0..5: two spans each; rows 6..9: one span each
    assertEquals(6 * 2 + 4, spans.size());
    assertEquals(new Span(0, 0, 3), spans.get(0));
    assertEquals(new Span(0, 7, 10), spans.get(1));
    assertEquals(new Span(5, 0, 3), spans.get(10));
    assertEquals(new Span(5, 7, 10), spans.get(11));
    assertEquals(new Span(6, 0, 10), spans.get(12));
    assertEquals(new Span(9, 0, 10), spans.get(15));
  }

  @Test
  void spans_clippedToRange() {
    List<Span> spans =
        PolygonRaster.scanlineSpans(new double[] {0, 10, 10, 0}, new double[] {0, 0, 10, 10}, 5, 8);
    assertEquals(3, spans.size());
    assertEquals(5, spans.get(0).y());
    assertEquals(7, spans.get(2).y());
  }

  @Test
  void linePixels_diagonal_hasAllSteps() {
    List<int[]> px = PolygonRaster.linePixels(0, 0, 3, 3);
    assertEquals(4, px.size());
    for (int i = 0; i < 4; i++) {
      assertArrayEquals(new int[] {i, i}, px.get(i));
    }
  }

  @Test
  void linePixels_steepAndReversed() {
    List<int[]> px = PolygonRaster.linePixels(5, 0, 0, 10);
    assertEquals(11, px.size());
    assertArrayEquals(new int[] {5, 0}, px.get(0));
    assertArrayEquals(new int[] {0, 10}, px.get(10));
    for (int i = 1; i < px.size(); i++) {
      assertEquals(i, px.get(i)[1]);
    }
  }
}
