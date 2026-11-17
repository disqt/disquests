package com.disqt.disquests.client.gui.component;

/**
 * Where the parts of a {@link TreeRowComponent} sit, in px from the row's left edge, and which part
 * a click hits. Minecraft-free, so it can be unit-tested.
 *
 * <p>A completed quest's links are frozen: it never shows the remove zone and never drags, whatever
 * the caller asked for. The toggle only exists in read mode, for a quest with listed subquests.
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
  public static final int TOGGLE_WIDTH = 9;
  public static final int ICON_WIDTH = 10;
  public static final int REMOVE_WIDTH = 12;

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
