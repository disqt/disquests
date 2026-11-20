package com.disqt.disquests.client.gui.component;

/**
 * Where the ghost of a dragged tree row goes: beside the pointer, below and to the right of it, so
 * the row or strip under the pointer (the drop target) stays visible. Near the right or bottom edge
 * of the screen it flips to the other side of the pointer, and it never leaves the screen.
 * Minecraft-free, so it can be unit-tested.
 */
public final class DragGhost {

  /** Gap between the pointer and the ghost, in GUI px. */
  public static final int OFFSET_X = 10;

  public static final int OFFSET_Y = 6;

  /** Room around the title inside the ghost, and the narrowest ghost. */
  public static final int PADDING = 4;

  public static final int MIN_WIDTH = 40;

  private DragGhost() {}

  /** The ghost's width for a title {@code textWidth} px wide, at most the source row's width. */
  public static int width(int textWidth, int rowWidth) {
    int wanted = Math.max(MIN_WIDTH, textWidth + PADDING * 2);
    return Math.max(1, Math.min(wanted, rowWidth));
  }

  /** The ghost's rectangle for the pointer at ({@code mouseX}, {@code mouseY}). */
  public static DropTracker.Bounds rect(
      int mouseX, int mouseY, int width, int height, int screenWidth, int screenHeight) {
    int x = mouseX + OFFSET_X;
    if (x + width > screenWidth) x = mouseX - OFFSET_X - width;
    int y = mouseY + OFFSET_Y;
    if (y + height > screenHeight) y = mouseY - OFFSET_Y - height;
    x = clamp(x, 0, screenWidth - width);
    y = clamp(y, 0, screenHeight - height);
    return new DropTracker.Bounds(x, y, width, height);
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(v, Math.max(min, max)));
  }
}
