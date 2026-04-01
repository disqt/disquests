package com.disqt.disquests.client.gui.helper;

import io.wispforest.owo.ui.core.OwoUIGraphics;

/** Shared utility for drawing rounded rectangles via pixel fill. */
public final class RoundedRect {

  /**
   * Pre-computed insets for corner radii 0..4. For radius r, INSETS[r][row] gives the number of
   * pixels to skip at distance row from the corner edge (circle approximation).
   */
  private static final int[][] INSETS = new int[5][];

  static {
    for (int r = 0; r < INSETS.length; r++) {
      INSETS[r] = new int[r];
      for (int dy = 0; dy < r; dy++) {
        double d = Math.sqrt(r * r - (r - dy - 0.5) * (r - dy - 0.5));
        INSETS[r][dy] = r - Math.max(0, (int) Math.round(d));
      }
    }
  }

  /**
   * Draw a filled rounded rectangle. Radius is clamped to [0, 4] and half the smaller dimension.
   */
  public static void draw(OwoUIGraphics ctx, int x, int y, int w, int h, int color) {
    int r = Math.min(4, Math.min(w / 2, h / 2));
    if (r <= 0) {
      ctx.fill(x, y, x + w, y + h, color);
      return;
    }
    // Central body
    ctx.fill(x + r, y, x + w - r, y + h, color);
    // Side columns
    ctx.fill(x, y + r, x + r, y + h - r, color);
    ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
    // Corner pixel rows
    int[] inset = INSETS[r];
    for (int dy = 0; dy < r; dy++) {
      int skip = inset[dy];
      ctx.fill(x + skip, y + dy, x + r, y + dy + 1, color);
      ctx.fill(x + w - r, y + dy, x + w - skip, y + dy + 1, color);
      ctx.fill(x + skip, y + h - 1 - dy, x + r, y + h - dy, color);
      ctx.fill(x + w - r, y + h - 1 - dy, x + w - skip, y + h - dy, color);
    }
  }

  private RoundedRect() {}
}
