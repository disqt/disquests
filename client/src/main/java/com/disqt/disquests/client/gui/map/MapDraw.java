package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.gui.map.PolygonRaster.Span;
import com.disqt.disquests.common.model.PointData;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import java.util.List;

/**
 * Drawing helpers shared by map layers. Vertex arrays are component-relative screen coordinates (as
 * returned by {@link MapView#blockToScreen}); everything is clipped to the view's bounds.
 */
final class MapDraw {
  private MapDraw() {}

  /** Projects polygon vertices to component-relative screen coordinates: {xs, ys}. */
  static double[][] project(MapView view, List<PointData> poly) {
    int n = poly.size();
    double[] xs = new double[n];
    double[] ys = new double[n];
    for (int i = 0; i < n; i++) {
      double[] s = view.blockToScreen(poly.get(i).x(), poly.get(i).z());
      xs[i] = s[0];
      ys[i] = s[1];
    }
    return new double[][] {xs, ys};
  }

  /** Even-odd scanline fill, one {@code fill} per span. */
  static void fillPolygon(OwoUIGraphics g, MapView view, double[] xs, double[] ys, int color) {
    if (xs.length < 3) {
      return;
    }
    int w = view.width();
    int h = view.height();
    double minX = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < xs.length; i++) {
      minX = Math.min(minX, xs[i]);
      maxX = Math.max(maxX, xs[i]);
      minY = Math.min(minY, ys[i]);
      maxY = Math.max(maxY, ys[i]);
    }
    if (maxX < 0 || minX > w || maxY < 0 || minY > h) {
      return;
    }
    int top = Math.max(0, (int) Math.floor(minY));
    int bottom = Math.min(h, (int) Math.ceil(maxY) + 1);
    int ox = view.x();
    int oy = view.y();
    for (Span span : PolygonRaster.scanlineSpans(xs, ys, top, bottom)) {
      int x0 = Math.max(0, span.x0());
      int x1 = Math.min(w, span.x1());
      if (x1 > x0) {
        g.fill(ox + x0, oy + span.y(), ox + x1, oy + span.y() + 1, color);
      }
    }
  }

  /** 1 px closed outline through the vertices. */
  static void outlinePolygon(OwoUIGraphics g, MapView view, double[] xs, double[] ys, int color) {
    int n = xs.length;
    if (n == 2) {
      line(g, view, xs[0], ys[0], xs[1], ys[1], color);
      return;
    }
    for (int i = 0, j = n - 1; i < n; j = i++) {
      line(g, view, xs[j], ys[j], xs[i], ys[i], color);
    }
  }

  /**
   * The polygon offset inwards by {@code d} pixels (mitred, with the mitre capped at 2d so sharp
   * corners do not spike). Returns {xs, ys}; degenerate polygons are returned unchanged.
   */
  static double[][] inset(double[] xs, double[] ys, double d) {
    int n = xs.length;
    double area2 = 0;
    for (int i = 0; i < n; i++) {
      int k = (i + 1) % n;
      area2 += xs[i] * ys[k] - xs[k] * ys[i];
    }
    if (n < 3 || area2 == 0) {
      return new double[][] {xs, ys};
    }
    double sign = Math.signum(area2);
    double[] ox = new double[n];
    double[] oy = new double[n];
    for (int i = 0; i < n; i++) {
      int prev = (i + n - 1) % n;
      int next = (i + 1) % n;
      double[] n1 = inwardNormal(xs[prev], ys[prev], xs[i], ys[i], sign);
      double[] n2 = inwardNormal(xs[i], ys[i], xs[next], ys[next], sign);
      double mx = n1[0] + n2[0];
      double my = n1[1] + n2[1];
      double denom = 1 + n1[0] * n2[0] + n1[1] * n2[1];
      if (denom < 0.25) {
        // Near-reversal corner: cap the mitre length at 2d.
        double len = Math.hypot(mx, my);
        double scale = len == 0 ? 0 : 2 / len;
        mx *= scale;
        my *= scale;
      } else {
        mx /= denom;
        my /= denom;
      }
      ox[i] = xs[i] + mx * d;
      oy[i] = ys[i] + my * d;
    }
    return new double[][] {ox, oy};
  }

  private static double[] inwardNormal(double x0, double y0, double x1, double y1, double sign) {
    double dx = x1 - x0;
    double dy = y1 - y0;
    double len = Math.hypot(dx, dy);
    if (len == 0) {
      return new double[] {0, 0};
    }
    return new double[] {-dy / len * sign, dx / len * sign};
  }

  /**
   * Bresenham line clipped to the view, merging horizontal and vertical pixel runs into single
   * fills.
   */
  static void line(
      OwoUIGraphics g, MapView view, double x0, double y0, double x1, double y1, int color) {
    double[] c = clip(x0, y0, x1, y1, view.width() - 1, view.height() - 1);
    if (c == null) {
      return;
    }
    int ox = view.x();
    int oy = view.y();
    List<int[]> pixels =
        PolygonRaster.linePixels(
            (int) Math.round(c[0]),
            (int) Math.round(c[1]),
            (int) Math.round(c[2]),
            (int) Math.round(c[3]));
    int[] first = pixels.get(0);
    int rx0 = first[0];
    int ry0 = first[1];
    int rx1 = rx0;
    int ry1 = ry0;
    for (int i = 1; i < pixels.size(); i++) {
      int px = pixels.get(i)[0];
      int py = pixels.get(i)[1];
      boolean row = ry0 == ry1 && py == ry1 && Math.abs(px - rx1) == 1;
      boolean column = rx0 == rx1 && px == rx1 && Math.abs(py - ry1) == 1;
      if (row || column) {
        rx1 = px;
        ry1 = py;
        continue;
      }
      fillRun(g, ox, oy, rx0, ry0, rx1, ry1, color);
      rx0 = rx1 = px;
      ry0 = ry1 = py;
    }
    fillRun(g, ox, oy, rx0, ry0, rx1, ry1, color);
  }

  private static void fillRun(
      OwoUIGraphics g, int ox, int oy, int x0, int y0, int x1, int y1, int color) {
    g.fill(
        ox + Math.min(x0, x1),
        oy + Math.min(y0, y1),
        ox + Math.max(x0, x1) + 1,
        oy + Math.max(y0, y1) + 1,
        color);
  }

  /** Liang-Barsky clip of a segment to [0, maxX] x [0, maxY]; null when fully outside. */
  private static double[] clip(
      double x0, double y0, double x1, double y1, double maxX, double maxY) {
    double dx = x1 - x0;
    double dy = y1 - y0;
    double t0 = 0;
    double t1 = 1;
    double[] p = {-dx, dx, -dy, dy};
    double[] q = {x0, maxX - x0, y0, maxY - y0};
    for (int i = 0; i < 4; i++) {
      if (p[i] == 0) {
        if (q[i] < 0) {
          return null;
        }
        continue;
      }
      double r = q[i] / p[i];
      if (p[i] < 0) {
        if (r > t1) {
          return null;
        }
        t0 = Math.max(t0, r);
      } else {
        if (r < t0) {
          return null;
        }
        t1 = Math.min(t1, r);
      }
    }
    return new double[] {x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy};
  }
}
