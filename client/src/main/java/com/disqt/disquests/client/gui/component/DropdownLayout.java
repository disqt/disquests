package com.disqt.disquests.client.gui.component;

/**
 * Size and place of a {@link SuggestionDropdown} panel. Minecraft-free, so it can be unit-tested.
 * All coordinates are absolute GUI pixels except {@link Placement}, which is the offset owo's
 * absolute positioning expects (relative to the root's padding box).
 */
public final class DropdownLayout {

  public static final int ROW_HEIGHT = 13;
  public static final int MAX_VISIBLE_ROWS = 10;
  public static final int PADDING = 2;
  public static final int SCROLLBAR = 3;

  /** The panel's offset for {@code Positioning.absolute}. */
  public record Placement(int x, int y) {}

  /** The root the panel is mounted on: its bounds and its padding (left, top). */
  public record Root(int x, int y, int width, int height, int padLeft, int padTop) {}

  private DropdownLayout() {}

  /**
   * Horizontal room the panel takes besides its rows: padding, and the scrollbar when it scrolls.
   */
  public static int chrome(int rows) {
    return 2 * PADDING + (rows > MAX_VISIBLE_ROWS ? SCROLLBAR + 1 : 0);
  }

  /**
   * At least {@code minWidth} (the anchor's width, 0 for none), wide enough for the widest row,
   * never wider than the root.
   */
  public static int width(int minWidth, int widestRow, int rows, int rootWidth) {
    return Math.min(Math.max(minWidth, widestRow + chrome(rows)), rootWidth);
  }

  /**
   * Up to {@link #MAX_VISIBLE_ROWS} rows tall (it scrolls past that), never taller than the root.
   */
  public static int height(int rows, int rootHeight) {
    return Math.min(Math.min(rows, MAX_VISIBLE_ROWS) * ROW_HEIGHT + 2 * PADDING, rootHeight);
  }

  /**
   * Below the anchor line ({@code anchorY} to {@code anchorY + lineHeight}), or above it when it
   * does not fit below (the other way round with {@code preferAbove}); then kept inside the root.
   */
  public static Placement place(
      int anchorX,
      int anchorY,
      int lineHeight,
      int width,
      int height,
      Root root,
      boolean preferAbove) {
    int below = anchorY + lineHeight + 1;
    int above = anchorY - height - 1;
    boolean fitsBelow = below + height <= root.y() + root.height();
    boolean fitsAbove = above >= root.y();
    int top =
        preferAbove
            ? (fitsAbove || !fitsBelow ? above : below)
            : (fitsBelow || !fitsAbove ? below : above);
    top = clamp(top, root.y(), root.y() + root.height() - height);
    int left = clamp(anchorX, root.x(), root.x() + root.width() - width);
    return new Placement(left - root.x() - root.padLeft(), top - root.y() - root.padTop());
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
