package com.disqt.disquests.client.gui.map;

/**
 * Tells a click on a preview map from a drag or a stray release: a left press, then a left release
 * inside the component at most {@link #SLOP} pixels from the press in each axis. Any release ends
 * the gesture. Free of Minecraft and owo; the caller decides which button is the left one.
 */
public final class PreviewClick {
  /** Pixels the cursor may move between press and release, in each axis, and still click. */
  public static final double SLOP = 3;

  private boolean pressed;
  private double pressX;
  private double pressY;

  /** Starts a gesture; only a left press can become a click. */
  public void press(double x, double y, boolean leftButton) {
    pressed = leftButton;
    pressX = x;
    pressY = y;
  }

  /** Ends the gesture; true when the press and this release count as a click. */
  public boolean release(double x, double y, boolean leftButton, boolean inside) {
    boolean wasPressed = pressed;
    pressed = false;
    return wasPressed
        && leftButton
        && inside
        && Math.abs(x - pressX) <= SLOP
        && Math.abs(y - pressY) <= SLOP;
  }

  /** Drops a pending press, for example when the component loses focus. */
  public void cancel() {
    pressed = false;
  }
}
