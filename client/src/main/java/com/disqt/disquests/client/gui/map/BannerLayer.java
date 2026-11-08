package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.gui.helper.Colors;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Draws BlueMap banner markers of the view's map as small pennants in their banner colour, with
 * their label to the right. Purely decorative: never takes input.
 */
public class BannerLayer implements MapLayer {
  /** Glyph height in pixels; the pole runs the full height, the flag the top 5 px. */
  private static final int HEIGHT = 7;

  private static final int FLAG_WIDTH = 5;
  private static final int FLAG_HEIGHT = 5;
  private static final int POLE_COLOR = 0xFFD0D0D0;
  private static final int LABEL_GAP = 2;

  private final BannerCache cache;
  private boolean showLabels = true;

  public BannerLayer(BannerCache cache) {
    this.cache = cache;
  }

  public BannerLayer showLabels(boolean showLabels) {
    this.showLabels = showLabels;
    return this;
  }

  @Override
  public void draw(MapView view, OwoUIGraphics g, int mouseX, int mouseY) {
    List<BannerMarker> banners = cache.get(view.mapId());
    if (banners.isEmpty()) {
      return;
    }
    Font font = Minecraft.getInstance().font;
    int w = view.width();
    int h = view.height();
    for (BannerMarker b : banners) {
      double[] s = view.blockToScreen(b.x(), b.z());
      // The pole's foot sits on the banner's block.
      int px = (int) Math.round(s[0]);
      int foot = (int) Math.round(s[1]);
      int top = foot - HEIGHT + 1;
      if (px > w || top > h || foot < 0 || (!showLabels && px < -FLAG_WIDTH - 1)) {
        continue;
      }
      int ax = view.x() + px;
      int ay = view.y() + top;
      g.fill(ax, ay, ax + 1, ay + HEIGHT, POLE_COLOR);
      // Pennant: 1 px columns narrowing to a point on the right of the pole.
      for (int i = 0; i < FLAG_WIDTH; i++) {
        int inset = i / 2;
        g.fill(ax + 1 + i, ay + inset, ax + 2 + i, ay + FLAG_HEIGHT - inset, b.argb());
      }
      if (showLabels && b.label() != null && !b.label().isEmpty()) {
        g.text(
            font,
            b.label(),
            ax + 1 + FLAG_WIDTH + LABEL_GAP,
            ay + (HEIGHT - font.lineHeight) / 2 + 1,
            Colors.TEXT_PRIMARY,
            true);
      }
    }
  }
}
