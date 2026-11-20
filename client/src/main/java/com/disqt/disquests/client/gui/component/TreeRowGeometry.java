package com.disqt.disquests.client.gui.component;

/**
 * Where the parts of a {@link TreeRowComponent} sit, in px from the row's left edge, and which part
 * a click hits. Minecraft-free, so it can be unit-tested.
 *
 * <p>A completed quest's links are frozen: it never shows the remove zone and never drags, whatever
 * the caller asked for. The toggle only exists in read mode, for a quest with listed subquests; in
 * edit mode its slot holds the drag handle of a row that drags (part of the body, so pressing it
 * starts a drag like pressing the title).
 */
public record TreeRowGeometry(
    int depth,
    boolean editMode,
    int childCount,
    boolean locked,
    boolean completed,
    boolean removable,
    boolean draggable,
    int width) {

  public static final int LEFT = 2;
  public static final int INDENT = 10;

  /** The expand toggle's slot, wide enough to aim at; its hit area is the slot by the row. */
  public static final int TOGGLE_WIDTH = 12;

  /** The chevron: a filled triangle about as tall as the font's capitals. */
  public static final int CHEVRON_SIZE = 7;

  public static final int ICON_WIDTH = 10;
  public static final int REMOVE_WIDTH = 12;

  /** The drag handle's grip: two columns of three dots, in the toggle slot. */
  public static final int HANDLE_WIDTH = 5;

  public static final int HANDLE_HEIGHT = 7;

  /** The clickable parts of a row. */
  public enum Part {
    BODY,
    TOGGLE,
    REMOVE
  }

  public boolean hasToggle() {
    return !editMode && childCount > 0;
  }

  public boolean hasRemove() {
    return editMode && removable && !completed;
  }

  public boolean canDrag() {
    return editMode && draggable && !completed;
  }

  /** A grip at the left of a row that can be dragged (edit mode, never for a completed quest). */
  public boolean hasHandle() {
    return canDrag();
  }

  /** Left edge of the handle's grip, centred in the toggle slot. */
  public int handleX() {
    return toggleX() + (TOGGLE_WIDTH - HANDLE_WIDTH) / 2;
  }

  /** Left edge of the chevron, centred in a toggle slot starting at {@code slotX}. */
  public static int chevronX(int slotX) {
    return slotX + (TOGGLE_WIDTH - CHEVRON_SIZE) / 2;
  }

  /**
   * The chevron as fill rectangles {x0, y0, x1, y1} (end exclusive) in a {@link #CHEVRON_SIZE}
   * square from its top-left: pointing right when collapsed, down when expanded. Each column (or
   * row) of the triangle is one rectangle, two px shorter than the one before.
   */
  public static int[][] chevron(boolean expanded) {
    int steps = (CHEVRON_SIZE + 1) / 2;
    int[][] rects = new int[steps][];
    for (int i = 0; i < steps; i++) {
      rects[i] =
          expanded
              ? new int[] {i, i, CHEVRON_SIZE - i, i + 1}
              : new int[] {i, i, i + 1, CHEVRON_SIZE - i};
    }
    return rects;
  }

  public int toggleX() {
    return LEFT + depth * INDENT;
  }

  /** Where the lock or tick goes; the title starts after it when there is one. */
  public int iconX() {
    return toggleX() + TOGGLE_WIDTH;
  }

  public int titleX() {
    return iconX() + (locked || completed ? ICON_WIDTH : 0);
  }

  /** Left edge of the remove zone, or the row's width when there is none. */
  public int removeX() {
    return hasRemove() ? width - REMOVE_WIDTH : width;
  }

  /** The part under {@code relX}; locks and ticks are part of the body. */
  public Part partAt(double relX) {
    if (hasRemove() && relX >= removeX()) return Part.REMOVE;
    if (hasToggle() && relX >= toggleX() && relX < toggleX() + TOGGLE_WIDTH) return Part.TOGGLE;
    return Part.BODY;
  }
}
