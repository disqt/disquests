package com.disqt.disquests.client.gui.screen;

import java.util.function.IntUnaryOperator;

/** The arithmetic of {@link PaneSplit}, Minecraft-free so it can be unit-tested. */
public final class PaneWidth {

  private PaneWidth() {}

  /**
   * The pane's width after the divider moved {@code totalDx} px since the press: the divider
   * follows the cursor, so moving away from the pane widens it (left for a pane on the right).
   */
  public static int afterDrag(
      int startWidth, double totalDx, boolean paneOnRight, IntUnaryOperator clamp) {
    double dx = paneOnRight ? -totalDx : totalDx;
    return clamp.applyAsInt((int) Math.round(startWidth + dx));
  }

  /**
   * {@code requested} kept between {@code min} and {@code max} ({@code min} wins if they cross).
   */
  public static int clamp(int requested, int min, int max) {
    return Math.max(min, Math.min(max, requested));
  }
}
