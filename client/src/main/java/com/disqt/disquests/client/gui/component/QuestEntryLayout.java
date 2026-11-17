package com.disqt.disquests.client.gui.component;

/**
 * Where the title and the marks of a {@link QuestEntryComponent}'s first line sit, in px from the
 * entry's left edge, and how far a nested row is indented. Minecraft-free, so it can be
 * unit-tested.
 *
 * <p>From the left: the lock (locked views), the title with its subquest count; from the right: the
 * visibility, pending and owner text as before, then the warning mark, then the "NEW" mark. The
 * title gives way to the marks. Positions of absent marks are {@link #NONE}.
 */
public record QuestEntryLayout(int titleX, int titleRoom, int newX, int warningX) {

  /** The position of a mark the entry does not show. */
  public static final int NONE = Integer.MIN_VALUE;

  public static final int PAD = 4;
  public static final int LOCK_WIDTH = 10;
  public static final int GAP = 4;

  /** The first line's text, as tall as the font, starts this far down. */
  public static final int TITLE_Y = 4;

  private static final int ROW_TOP = 2;
  private static final int ROW_BOTTOM = 14;

  /**
   * @param width the entry's width
   * @param locked whether the lock is drawn before the title
   * @param rightSideWidth the visibility and owner text plus their gap, 0 when there is none
   * @param pendingWidth the owner's "(n pending)" badge, drawn left of that text; 0 when absent
   * @param newWidth the "NEW" mark's width, 0 when it is not shown
   * @param warningWidth the warning mark's width, 0 when it is not shown
   */
  public static QuestEntryLayout of(
      int width,
      boolean locked,
      int rightSideWidth,
      int pendingWidth,
      int newWidth,
      int warningWidth) {
    int titleX = PAD + (locked ? LOCK_WIDTH : 0);
    int right = width - PAD - rightSideWidth - pendingWidth;
    int warningX = NONE;
    if (warningWidth > 0) {
      warningX = right - warningWidth;
      right = warningX - GAP;
    }
    int newX = NONE;
    if (newWidth > 0) {
      newX = right - newWidth;
      right = newX - GAP;
    }
    return new QuestEntryLayout(titleX, Math.max(0, right - titleX), newX, warningX);
  }

  /** Whether the entry-relative point is on the warning mark, {@code warningWidth} wide. */
  public boolean onWarning(double relX, double relY, int warningWidth) {
    return warningX != NONE
        && relX >= warningX
        && relX < warningX + warningWidth
        && relY >= ROW_TOP
        && relY < ROW_BOTTOM;
  }

  /** The left margin of a list row at {@code depth}, before its toggle slot. */
  public static int indent(int depth) {
    return depth * TreeRowGeometry.INDENT;
  }

  /**
   * The room kept for the expand toggle on every row of a list that has at least one, so entries
   * without subquests stay aligned with their siblings; none in a list without toggles (a flat
   * list, as against an older server).
   */
  public static int toggleSlot(boolean listHasToggles) {
    return listHasToggles ? TreeRowGeometry.TOGGLE_WIDTH : 0;
  }
}
