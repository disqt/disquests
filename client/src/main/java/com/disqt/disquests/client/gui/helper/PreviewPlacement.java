package com.disqt.disquests.client.gui.helper;

/**
 * Where an anchored hover card goes: beside its anchor (the tree pane, a chip), not under the
 * pointer, so the rows the player is about to click stay visible. Its left edge is the anchor's
 * right edge plus {@link #GAP}, its top the hovered row's top; when it does not fit at the right it
 * goes to the anchor's left, and it is always clamped to the screen. Minecraft-free.
 */
public final class PreviewPlacement {

  public static final int GAP = 6;

  /** A card position, its top-left corner. */
  public record Position(int x, int y) {}

  private PreviewPlacement() {}

  public static Position beside(
      int anchorLeft,
      int anchorRight,
      int rowTop,
      int cardWidth,
      int cardHeight,
      int screenWidth,
      int screenHeight) {
    int x = anchorRight + GAP;
    if (x + cardWidth > screenWidth) x = anchorLeft - GAP - cardWidth;
    x = clamp(x, 0, screenWidth - cardWidth);
    int y = clamp(rowTop, 0, screenHeight - cardHeight);
    return new Position(x, y);
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(v, Math.max(min, max)));
  }
}
