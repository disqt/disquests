package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.gui.helper.Colors;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * One line of a quest tree, drawn by itself: a single leaf, because owo delivers drags only to the
 * focused component and a row made of labels would never receive them.
 *
 * <p>From the left: the expand toggle (when the quest has listed subquests), a lock or a tick, the
 * title and the subquest count; at the right, the "not saved" marker and, when removable, the
 * remove hit zone. Locks and ticks are drawn only, never clickable.
 *
 * <p>Read mode: a click opens the quest, the toggle expands or collapses it. Edit mode: nothing
 * navigates; the remove zone removes, and a press on a draggable row that moves more than {@link
 * #DRAG_THRESHOLD} px starts a drag reported to the screen's {@link DragTarget} in absolute
 * coordinates (the screen hit-tests with {@link DropTracker} and calls {@link #highlighted}).
 */
public class TreeRowComponent extends BaseUIComponent implements DropTracker.Zone {

  public static final int ROW_HEIGHT = 12;
  public static final int INDENT = 10;
  public static final int DRAG_THRESHOLD = 4;
  private static final int LEFT = 2;
  private static final int TOGGLE_WIDTH = 9;
  private static final int ICON_WIDTH = 10;
  private static final int REMOVE_WIDTH = 12;
  private static final int GAP = 6;
  private static final int TEXT_Y = 2;

  /**
   * What the row shows. {@code depth} is the indent level; {@code current} highlights the quest
   * being read or edited; {@code changed} draws "not saved", {@code takenOut} "taken out, not
   * saved"; {@code removable} draws the remove zone (edit mode); {@code childCount} > 0 draws the
   * toggle, open when {@code expanded}, and the count.
   */
  public record Line(
      UUID questId,
      String title,
      int depth,
      boolean current,
      boolean locked,
      boolean completed,
      boolean changed,
      boolean takenOut,
      boolean removable,
      boolean draggable,
      int childCount,
      boolean expanded) {}

  /** The clickable parts of a row, for {@link #pointFor}. */
  public enum Part {
    BODY,
    TOGGLE,
    REMOVE
  }

  /** The screen side of a drag; positions are absolute GUI coordinates. */
  public interface DragTarget {
    void dragMoved(TreeRowComponent source, int absX, int absY);

    void dropped(TreeRowComponent source, int absX, int absY);

    void dragCancelled(TreeRowComponent source);
  }

  private final Line line;
  private final Component marker;
  private final Component count;

  private boolean editMode;
  private Consumer<UUID> onOpen = id -> {};
  private Consumer<UUID> onToggle = id -> {};
  private Consumer<UUID> onRemove = id -> {};
  private DragTarget dragTarget;

  private boolean highlighted;
  private boolean pressed;
  private boolean dragging;
  private double pressX;
  private double pressY;

  public TreeRowComponent(Line line) {
    this.line = line;
    this.marker =
        line.takenOut()
            ? Component.translatable("gui.disquests.tree.taken_out")
            : line.changed() ? Component.translatable("gui.disquests.tree.not_saved") : null;
    this.count = line.childCount() > 0 ? Component.literal("(" + line.childCount() + ")") : null;
    this.id("tree-row-" + line.questId());
    this.sizing(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
  }

  /** Read mode: a click opens the quest, the toggle expands or collapses it. */
  public TreeRowComponent readMode(Consumer<UUID> onOpen, Consumer<UUID> onToggle) {
    this.editMode = false;
    this.onOpen = onOpen;
    this.onToggle = onToggle;
    return this;
  }

  /**
   * Edit mode: no navigation; the remove zone calls {@code onRemove}, drags go to {@code target}.
   */
  public TreeRowComponent editMode(Consumer<UUID> onRemove, DragTarget target) {
    this.editMode = true;
    this.onRemove = onRemove;
    this.dragTarget = target;
    return this;
  }

  public UUID questId() {
    return line.questId();
  }

  public Line line() {
    return line;
  }

  public boolean isDragging() {
    return dragging;
  }

  /** Drawn as the drop target under the cursor during a drag. */
  @Override
  public void highlighted(boolean highlighted) {
    this.highlighted = highlighted;
  }

  public boolean isHighlighted() {
    return highlighted;
  }

  /** Stops a drag in progress and tells the target it was cancelled. */
  public void cancelDrag() {
    pressed = false;
    if (dragging) {
      dragging = false;
      if (dragTarget != null) dragTarget.dragCancelled(this);
    }
  }

  /**
   * The absolute centre of {@code part}, for the E2E helpers to click like a player; null when the
   * row does not have that part in its current mode.
   */
  public double[] pointFor(Part part) {
    double cy = y() + ROW_HEIGHT / 2.0;
    return switch (part) {
      case BODY -> new double[] {x() + titleX() + 4, cy};
      case TOGGLE -> hasToggle() ? new double[] {x() + toggleX() + TOGGLE_WIDTH / 2.0, cy} : null;
      case REMOVE -> hasRemove() ? new double[] {x() + width() - REMOVE_WIDTH / 2.0, cy} : null;
    };
  }

  // --- geometry, relative to the row ---

  private boolean hasToggle() {
    return !editMode && line.childCount() > 0;
  }

  private boolean hasRemove() {
    return editMode && line.removable();
  }

  private int toggleX() {
    return LEFT + line.depth() * INDENT;
  }

  private int iconX() {
    return toggleX() + TOGGLE_WIDTH;
  }

  private int titleX() {
    return iconX() + (line.locked() || line.completed() ? ICON_WIDTH : 0);
  }

  private Part partAt(double relX) {
    if (hasRemove() && relX >= width() - REMOVE_WIDTH) return Part.REMOVE;
    if (hasToggle() && relX >= toggleX() && relX < toggleX() + TOGGLE_WIDTH) return Part.TOGGLE;
    return Part.BODY;
  }

  // --- size ---

  @Override
  protected int determineHorizontalContentSize(Sizing sizing) {
    Font font = Minecraft.getInstance().font;
    return titleX() + font.width(line.title() != null ? line.title() : "") + REMOVE_WIDTH;
  }

  @Override
  protected int determineVerticalContentSize(Sizing sizing) {
    return ROW_HEIGHT;
  }

  // --- drawing ---

  @Override
  public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
    Font font = Minecraft.getInstance().font;
    int x = x();
    int y = y();
    int w = width();
    boolean hovered = isInBoundingBox(mouseX, mouseY);

    if (highlighted) {
      context.fill(x, y, x + w, y + ROW_HEIGHT, Colors.TREE_DROP_TARGET);
      context.drawRectOutline(x, y, w, ROW_HEIGHT, Colors.TEXT_PRIMARY);
    } else if (line.current() || dragging) {
      context.fill(x, y, x + w, y + ROW_HEIGHT, Colors.ENTRY_SELECTED);
    } else if (hovered && (!editMode || line.draggable())) {
      context.fill(x, y, x + w, y + ROW_HEIGHT, Colors.ENTRY_HOVER);
    }

    if (hasToggle()) {
      boolean toggleHovered = hovered && partAt(mouseX - x) == Part.TOGGLE;
      context.text(
          font,
          line.expanded() ? "▾" : "▸",
          x + toggleX() + 1,
          y + TEXT_Y,
          toggleHovered ? Colors.TEXT_PRIMARY : Colors.TEXT_MUTED,
          false);
    }
    if (line.completed()) {
      context.text(font, "✔", x + iconX(), y + TEXT_Y, Colors.TREE_COMPLETED, false);
    } else if (line.locked()) {
      drawLock(context, x + iconX(), y + 1);
    }

    int right = x + w - (hasRemove() ? REMOVE_WIDTH : 0) - LEFT;
    if (hasRemove()) {
      boolean removeHovered = hovered && partAt(mouseX - x) == Part.REMOVE;
      context.text(
          font,
          "×",
          x + w - REMOVE_WIDTH + 3,
          y + TEXT_Y,
          removeHovered ? Colors.BADGE_RED : Colors.TEXT_MUTED,
          false);
    }
    if (marker != null) {
      int markerWidth = font.width(marker);
      right -= markerWidth;
      context.text(font, marker, right, y + TEXT_Y, Colors.AMBER, false);
      right -= GAP;
    }

    int titleLeft = x + titleX();
    int countWidth = count != null ? font.width(count) + 4 : 0;
    int available = Math.max(1, right - titleLeft - countWidth);
    String title = line.title() != null ? line.title() : "";
    List<FormattedCharSequence> lines = font.split(Component.literal(title), available);
    int titleColor = line.locked() || line.completed() ? Colors.TEXT_MUTED : Colors.TEXT_PRIMARY;
    int titleEnd = titleLeft;
    if (!lines.isEmpty()) {
      context.text(font, lines.getFirst(), titleLeft, y + TEXT_Y, titleColor, false);
      titleEnd += font.width(lines.getFirst());
    }
    if (count != null) {
      context.text(font, count, titleEnd + 4, y + TEXT_Y, Colors.TEXT_MUTED, false);
    }
  }

  /** A small padlock: shackle over a body, 7 px wide. */
  private static void drawLock(OwoUIGraphics context, int x, int y) {
    int color = Colors.TEXT_MUTED;
    context.fill(x + 2, y + 1, x + 5, y + 2, color);
    context.fill(x + 1, y + 2, x + 2, y + 5, color);
    context.fill(x + 5, y + 2, x + 6, y + 5, color);
    context.fill(x, y + 5, x + 7, y + 10, color);
  }

  // --- input ---

  @Override
  public boolean canFocus(FocusSource source) {
    return source == FocusSource.MOUSE_CLICK;
  }

  @Override
  public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
    if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
    Part part = partAt(click.x());
    if (!editMode) {
      if (part == Part.TOGGLE) {
        onToggle.accept(line.questId());
      } else {
        onOpen.accept(line.questId());
      }
      return true;
    }
    if (part == Part.REMOVE) {
      onRemove.accept(line.questId());
      return true;
    }
    if (line.draggable() && dragTarget != null) {
      pressed = true;
      pressX = click.x();
      pressY = click.y();
    }
    return true;
  }

  @Override
  public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
    if (!pressed) return false;
    if (!dragging) {
      double dx = click.x() - pressX;
      double dy = click.y() - pressY;
      if (dx * dx + dy * dy <= DRAG_THRESHOLD * DRAG_THRESHOLD) return true;
      dragging = true;
    }
    // owo passes coordinates relative to this component
    dragTarget.dragMoved(this, (int) (x() + click.x()), (int) (y() + click.y()));
    return true;
  }

  @Override
  public boolean onMouseUp(MouseButtonEvent click) {
    boolean wasPressed = pressed;
    pressed = false;
    if (dragging) {
      dragging = false;
      dragTarget.dropped(this, (int) (x() + click.x()), (int) (y() + click.y()));
      return true;
    }
    return wasPressed;
  }

  @Override
  public void onFocusLost() {
    super.onFocusLost();
    cancelDrag();
  }

  /** A drop zone for {@link DropTracker} made from any component (for example a drop strip). */
  public static DropTracker.Zone dropZone(UIComponent component, Consumer<Boolean> highlight) {
    return DropTracker.area(
        component.id(), component::x, component::y, component::width, component::height, highlight);
  }
}
