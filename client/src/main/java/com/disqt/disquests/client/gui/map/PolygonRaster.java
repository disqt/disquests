package com.disqt.disquests.client.gui.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure scanline fill and Bresenham line helpers for drawing polygons on the map. */
public final class PolygonRaster {
  private PolygonRaster() {}

  /** Horizontal run on row {@code y}, covering x in [x0, x1). */
  public record Span(int y, int x0, int x1) {}

  /** Even-odd scanline fill of a closed polygon over rows [clipTop, clipBottom). */
  public static List<Span> scanlineSpans(double[] xs, double[] ys, int clipTop, int clipBottom) {
    List<Span> spans = new ArrayList<>();
    int n = xs.length;
    double[] crossings = new double[n];
    for (int y = clipTop; y < clipBottom; y++) {
      double s = y + 0.5;
      int count = 0;
      for (int i = 0, j = n - 1; i < n; j = i++) {
        double x0 = xs[j];
        double y0 = ys[j];
        double x1 = xs[i];
        double y1 = ys[i];
        if ((y0 <= s) != (y1 <= s)) {
          crossings[count++] = x0 + (s - y0) / (y1 - y0) * (x1 - x0);
        }
      }
      Arrays.sort(crossings, 0, count);
      for (int k = 0; k + 1 < count; k += 2) {
        int xa = (int) Math.floor(crossings[k]);
        int xb = (int) Math.floor(crossings[k + 1]);
        if (xb > xa) {
          spans.add(new Span(y, xa, xb));
        }
      }
    }
    return spans;
  }

  /** Integer Bresenham line, inclusive of both endpoints. */
  public static List<int[]> linePixels(int x0, int y0, int x1, int y1) {
    List<int[]> out = new ArrayList<>();
    int dx = Math.abs(x1 - x0);
    int dy = -Math.abs(y1 - y0);
    int sx = x0 < x1 ? 1 : -1;
    int sy = y0 < y1 ? 1 : -1;
    int err = dx + dy;
    int x = x0;
    int y = y0;
    while (true) {
      out.add(new int[] {x, y});
      if (x == x1 && y == y1) {
        break;
      }
      int e2 = 2 * err;
      if (e2 >= dy) {
        err += dy;
        x += sx;
      }
      if (e2 <= dx) {
        err += dx;
        y += sy;
      }
    }
    return out;
  }
}
