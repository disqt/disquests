package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.gui.component.TreeRowGeometry.Part;
import com.disqt.disquests.client.gui.helper.Colors;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One line of a quest tree, drawn by itself: a single leaf, because owo delivers drags only to the
 * focused component and a row made of labels would never receive them.
 *
 * <p>From the left: the expand toggle (read mode, when the quest has listed subquests) or the drag
 * handle (edit mode, when the row drags), a lock or a tick, the title and the subquest count, then
 * "(editing)" on the edited quest's own row; at the right, the "not saved" marker and, when
 * removable, the remove hit zone with its "Take out of X" tooltip. The current quest's row is
 * filled and marked with an accent bar, more than a hovered row. Locks and ticks are drawn only,
 * never clickable. Layout and hit zones come from {@link TreeRowGeometry}.
 *
 * <p>Read mode: a click opens the quest, the toggle expands or collapses it. Edit mode: nothing
 * navigates; the remove zone removes, and a left press on a draggable row that moves more than
 * {@link #DRAG_THRESHOLD} px starts a drag reported to the screen's {@link DragTarget} in absolute
 * coordinates (the screen hit-tests with {@link DropTracker} and calls {@link #highlighted}). A
 * completed quest never drags and never shows the remove zone.
 */
public class TreeRowComponent extends BaseUIComponent implements DropTracker.Zone {

  public static final int ROW_HEIGHT = 12;
  public static final int DRAG_THRESHOLD = 4;
  private static final int GAP = 6;
  private static final int COUNT_GAP = 4;
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

  /** The screen side of a drag; positions are absolute GUI coordinates. */
  public interface DragTarget {
    void dragMoved(TreeRowComponent source, int absX, int absY);

    void dropped(TreeRowComponent source, int absX, int absY);

    void dragCancelled(TreeRowComponent source);

    /** True while any row of this target is being dragged: rows then drop hover effects. */
    default boolean dragging() {
      return false;
    }
  }

  private static final int ACCENT_WIDTH = 2;
  private static final String EDITING_KEY = "gui.disquests.tree.editing";

  private final Line line;
  private final String title;
  private final Component marker;
  private String count;
  private Component removeTooltip;

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

  // The title as drawn, recomputed only when the room for it changes
  private int fittedFor = -1;
  private String fittedTitle = "";

  public TreeRowComponent(Line line) {
    this.line = line;
    this.title = line.title() != null ? line.title() : "";
    this.marker =
        line.takenOut()
            ? Component.translatable("gui.disquests.tree.taken_out")
            : line.changed() ? Component.translatable("gui.disquests.tree.not_saved") : null;
    this.count = TreeRowGeometry.trailing(line.childCount(), "");
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
    // The edited quest's own row says so
    String editing = line.current() ? Component.translatable(EDITING_KEY).getString() : "";
    this.count = TreeRowGeometry.trailing(line.childCount(), editing);
    this.fittedFor = -1;
    return this;
  }

  /** The tooltip of the remove zone ("Take out of X"); null for none. */
  public TreeRowComponent removeTooltip(Component tooltip) {
    this.removeTooltip = tooltip;
    return this;
  }

  /** The title as given, for the drag ghost. */
  public String title() {
    return title;
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
    TreeRowGeometry g = geometry();
    double cy = y() + ROW_HEIGHT / 2.0;
    return switch (part) {
      case BODY -> new double[] {x() + g.titleX() + 4, cy};
      case TOGGLE ->
          g.hasToggle()
              ? new double[] {x() + g.toggleX() + TreeRowGeometry.TOGGLE_WIDTH / 2.0, cy}
              : null;
      case REMOVE ->
          g.hasRemove()
              ? new double[] {x() + g.removeX() + TreeRowGeometry.REMOVE_WIDTH / 2.0, cy}
              : null;
    };
  }

  private TreeRowGeometry geometry() {
    return new TreeRowGeometry(
        line.depth(),
        editMode,
        line.childCount(),
        line.locked(),
        line.completed(),
        line.removable(),
        line.draggable(),
        width());
  }

  // --- size ---

  @Override
  protected int determineHorizontalContentSize(Sizing sizing) {
    Font font = Minecraft.getInstance().font;
    return geometry().titleX() + font.width(title) + TreeRowGeometry.REMOVE_WIDTH;
  }

  @Override
  protected int determineVerticalContentSize(Sizing sizing) {
    return ROW_HEIGHT;
  }

  // --- drawing ---

  @Override
  public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
    Font font = Minecraft.getInstance().font;
    TreeRowGeometry g = geometry();
    int x = x();
    int y = y();
    int w = width();
    // While a row is dragged only the drop target lights up, not what the cursor passes over
    boolean otherDrag = dragTarget != null && dragTarget.dragging();
    // Hover effects (fill, brighter handle and x, the x's tooltip) only where this row is the
    // topmost component: not under a dropdown or an overlay, not past the scroll's clipped edge
    boolean hovered = isInBoundingBox(mouseX, mouseY) && !otherDrag && isTopmostAt(mouseX, mouseY);
    Part hoveredPart = hovered ? g.partAt(mouseX - x) : null;

    if (highlighted) {
      context.fill(x, y, x + w, y + ROW_HEIGHT, Colors.TREE_DROP_TARGET);
      context.drawRectOutline(x, y, w, ROW_HEIGHT, Colors.TREE_DROP_OUTLINE);
    } else if (line.current()) {
      context.fill(x, y, x + w, y + ROW_HEIGHT, Colors.ENTRY_SELECTED);
      if (hovered && (!editMode || g.canDrag())) {
        context.fill(x, y, x + w, y + ROW_HEIGHT, Colors.ENTRY_HOVER);
      }
      context.fill(x, y, x + ACCENT_WIDTH, y + ROW_HEIGHT, Colors.AMBER);
    } else if (hovered && (!editMode || g.canDrag())) {
      context.fill(x, y, x + w, y + ROW_HEIGHT, Colors.ENTRY_HOVER);
    }

    if (g.hasToggle()) {
      // The same chevron as the lists' toggle, level with the title's capitals
      TreeToggleComponent.drawChevron(
          context,
          x + TreeRowGeometry.chevronX(g.toggleX()),
          y + TEXT_Y,
          line.expanded(),
          TreeToggleComponent.chevronColor(true, hoveredPart == Part.TOGGLE));
    }
    if (g.hasHandle()) {
      drawHandle(
          context,
          x + g.handleX(),
          y + (ROW_HEIGHT - TreeRowGeometry.HANDLE_HEIGHT) / 2,
          hovered && hoveredPart == Part.BODY ? Colors.TEXT_PRIMARY : Colors.TEXT_MUTED);
    }
    if (line.completed()) {
      context.text(font, "✔", x + g.iconX(), y + TEXT_Y, Colors.TREE_COMPLETED, false);
    } else if (line.locked()) {
      drawLock(context, x + g.iconX(), y + 1);
    }

    if (g.hasRemove()) {
      boolean onRemove = hoveredPart == Part.REMOVE;
      if (onRemove) {
        context.fill(x + g.removeX(), y, x + w, y + ROW_HEIGHT, Colors.ENTRY_HOVER);
        if (removeTooltip != null) context.setTooltipForNextFrame(removeTooltip, mouseX, mouseY);
      }
      context.text(
          font,
          "×",
          x + g.removeX() + 3,
          y + TEXT_Y,
          onRemove ? Colors.TREE_REMOVE_HOVER : Colors.TEXT_MUTED,
          false);
    }
    int right = x + g.removeX() - TreeRowGeometry.LEFT;
    if (marker != null) {
      right -= font.width(marker);
      context.text(font, marker, right, y + TEXT_Y, Colors.AMBER, false);
      right -= GAP;
    }

    int titleLeft = x + g.titleX();
    int available = right - titleLeft;
    if (available != fittedFor) {
      // The count is kept whole; the title gives way, down to nothing
      fittedTitle = TextFit.title(title, count, available, font::width);
      fittedFor = available;
    }
    int titleColor = line.locked() || line.completed() ? Colors.TEXT_MUTED : Colors.TEXT_PRIMARY;
    int textEnd = titleLeft;
    if (!fittedTitle.isEmpty()) {
      context.text(font, fittedTitle, titleLeft, y + TEXT_Y, titleColor, false);
      textEnd += font.width(fittedTitle) + COUNT_GAP;
    }
    if (!count.isEmpty() && font.width(count) <= right - textEnd) {
      context.text(font, count, textEnd, y + TEXT_Y, Colors.TEXT_MUTED, false);
    } // The dragged row stays in place, dimmed; its ghost follows the cursor
    if (dragging) context.fill(x, y, x + w, y + ROW_HEIGHT, Colors.TREE_SOURCE_DIM);
  }

  /** This row is what owo finds under the point (nothing covers it there, it is not clipped). */
  private boolean isTopmostAt(int x, int y) {
    var root = root();
    return root != null && root.childAt(x, y) == this;
  }

  /** The drag handle: a grip of two columns of three dots. */
  private static void drawHandle(OwoUIGraphics context, int x, int y, int color) {
    for (int row = 0; row < 3; row++) {
      int dy = y + row * 3;
      context.fill(x, dy, x + 2, dy + 1, color);
      context.fill(x + 3, dy, x + 5, dy + 1, color);
    }
  }

  /** A small padlock: shackle over a body, 7 px wide. */
  public static void drawLock(OwoUIGraphics context, int x, int y) {
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
    TreeRowGeometry g = geometry();
    Part part = g.partAt(click.x());
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
    if (g.canDrag() && dragTarget != null) {
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
    // Only the button that started the drag ends it
    if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
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
