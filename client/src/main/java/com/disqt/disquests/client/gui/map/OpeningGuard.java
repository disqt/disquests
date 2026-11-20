package com.disqt.disquests.client.gui.map;

/**
 * Drops a left press that arrives too soon after a screen opened, with its release. The click that
 * opens the map editor from the location preview can be the first half of a double-click; its
 * second press would otherwise land on the new screen's map and add a corner. Minecraft-free; the
 * caller passes the time and decides which button is the left one.
 */
public final class OpeningGuard {
  /** A left press earlier than this after opening is ignored. */
  public static final long WINDOW_MS = 250;

  private final long openedAtMs;
  private boolean ignoringPress;

  public OpeningGuard(long openedAtMs) {
    this.openedAtMs = openedAtMs;
  }

  /**
   * True when this left press should reach the screen; remembers an ignored one for its release.
   */
  public boolean acceptPress(long nowMs) {
    ignoringPress = nowMs - openedAtMs < WINDOW_MS;
    return !ignoringPress;
  }

  /** True when this left release should reach the screen: false when its press was ignored. */
  public boolean acceptRelease() {
    boolean accept = !ignoringPress;
    ignoringPress = false;
    return accept;
  }
}
