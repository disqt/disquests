package com.disqt.disquests.client.gui.component;

/**
 * Which dropdown row the keyboard selects. Greyed rows are never selected: the arrows skip them and
 * stop at the first or last row that can be picked. Minecraft-free so it can be unit-tested.
 */
public final class SuggestionSelection {

  /** No row is selected: the list is empty or every row is greyed. */
  public static final int NONE = -1;

  private SuggestionSelection() {}

  /** The first row that can be picked, or {@link #NONE}. */
  public static int first(boolean[] enabled) {
    for (int i = 0; i < enabled.length; i++) {
      if (enabled[i]) return i;
    }
    return NONE;
  }

  /**
   * The next row that can be picked from {@code current} in the direction of {@code step} (+1 down,
   * -1 up); {@code current} when there is none that way. From {@link #NONE}, the first one.
   */
  public static int move(boolean[] enabled, int current, int step) {
    if (current < 0 || current >= enabled.length || !enabled[current]) return first(enabled);
    int dir = Integer.signum(step);
    if (dir == 0) return current;
    for (int i = current + dir; i >= 0 && i < enabled.length; i += dir) {
      if (enabled[i]) return i;
    }
    return current;
  }
}
