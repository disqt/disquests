package com.disqt.disquests.client.gui.component;

import static com.disqt.disquests.client.gui.component.DropdownLayout.PADDING;
import static com.disqt.disquests.client.gui.component.DropdownLayout.SCROLLBAR;

import com.disqt.disquests.client.gui.helper.Colors;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A list of suggestions under (or above) a text field. The rows live in a {@link FlowLayout} (id
 * given by the caller) inside a vertical scroll that is mounted on the screen root with absolute
 * positioning while open; not an {@code OverlayContainer}, which would centre it. The list is never
 * cut: past {@link DropdownLayout#MAX_VISIBLE_ROWS} it scrolls. The panel is at least as wide as
 * its anchor and wide enough for its widest row, up to the screen width; a row that still does not
 * fit shortens its title, never its "(reason)" or its note ({@link TextFit}).
 *
 * <p>Rows ({@code suggestion-<index>}) are reused when the items change, so typing and the arrow
 * keys do not rebuild the panel. Greyed rows show but cannot be picked or selected. The owning
 * field forwards its keys to {@link #onKey}; {@code DisquestsBaseScreen} closes the dropdown on a
 * click outside it and its anchor (the click still goes through). After a pick the anchor gets the
 * focus back.
 */
public class SuggestionDropdown<T> {

  /**
   * How one item reads: a title (shortened when needed), an optional suffix drawn after it and
   * always kept whole (the bracketed reason of a greyed row), whether it can be picked, and an
   * optional right-aligned note.
   */
  public record Entry(
      String title, @Nullable Component suffix, boolean enabled, @Nullable Component note) {}

  public static final int ROW_HEIGHT = DropdownLayout.ROW_HEIGHT;
  private static final int TEXT_INSET = 4;
  private static final int NOTE_GAP = 8;
  private static final int TEXT_Y = 2;

  private final FlowLayout root;
  private final Function<T, Entry> renderer;
  private final Consumer<T> onPick;
  private final FlowLayout list;
  private final Panel panel;
  private final List<Row> rows = new ArrayList<>();

  private List<T> items = List.of();
  private boolean[] enabled = new boolean[0];
  private int selected = SuggestionSelection.NONE;
  private boolean pickOnTab;
  private boolean preferAbove;
  private int minWidth;
  @Nullable private UIComponent anchor;

  /**
   * @param root the screen root the panel is mounted on
   * @param listId id of the {@link FlowLayout} holding the rows
   * @param renderer how an item reads
   * @param onPick called with the picked item, after the dropdown has closed
   */
  public SuggestionDropdown(
      FlowLayout root, String listId, Function<T, Entry> renderer, Consumer<T> onPick) {
    this.root = root;
    this.renderer = renderer;
    this.onPick = onPick;
    this.list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    this.list.id(listId);
    this.panel = new Panel(list, this);
    this.panel.id(listId + "-scroll");
    this.panel.padding(Insets.of(PADDING));
    this.panel.scrollbarThiccness(SCROLLBAR);
    this.panel.scrollbar(
        ScrollContainer.Scrollbar.flat(Color.ofArgb(Colors.SCROLLBAR_THUMB_INACTIVE)));
    this.panel.surface(
        Surface.flat(Colors.DROPDOWN_BACKGROUND).and(Surface.outline(Colors.DROPDOWN_OUTLINE)));
  }

  /** Tab picks the selected row too (the tag search keeps its old behaviour). */
  public SuggestionDropdown<T> pickOnTab(boolean pickOnTab) {
    this.pickOnTab = pickOnTab;
    return this;
  }

  /** Opens above the anchor when there is room, else below (default: below, else above). */
  public SuggestionDropdown<T> preferAbove(boolean preferAbove) {
    this.preferAbove = preferAbove;
    return this;
  }

  /** Minimum panel width, for example the field's; 0 (default) fits the longest row. */
  public SuggestionDropdown<T> minWidth(int minWidth) {
    this.minWidth = minWidth;
    return this;
  }

  /** Shows {@code items} under (or above) {@code anchor}; an empty list closes the dropdown. */
  public void show(List<T> items, UIComponent anchor) {
    show(items, anchor.x(), anchor.y(), anchor.height(), anchor);
  }

  /**
   * Shows {@code items} next to a line of text whose top-left corner is ({@code x}, {@code y}) in
   * screen coordinates; an empty list closes the dropdown. A click on {@code anchor} (if any) does
   * not close it, and it gets the focus back after a pick.
   */
  public void show(List<T> items, int x, int y, int lineHeight, @Nullable UIComponent anchor) {
    if (items.isEmpty()) {
      hide();
      return;
    }
    this.items = List.copyOf(items);
    this.anchor = anchor;
    Font font = Minecraft.getInstance().font;

    int n = this.items.size();
    this.enabled = new boolean[n];
    int widest = 0;
    for (int i = 0; i < n; i++) {
      Entry entry = renderer.apply(this.items.get(i));
      enabled[i] = entry.enabled();
      Row row = row(i);
      row.set(entry);
      widest = Math.max(widest, row.naturalWidth(font));
    }
    while (rows.size() > n) {
      list.removeChild(rows.removeLast());
    }

    int width = DropdownLayout.width(minWidth, widest, n, root.width());
    int height = DropdownLayout.height(n, root.height());
    // Rows stop short of the scrollbar, which owo draws inside the padding box on the right.
    int rowWidth = Math.max(0, width - DropdownLayout.chrome(n));
    for (Row row : rows) {
      row.sizing(Sizing.fixed(rowWidth), Sizing.fixed(ROW_HEIGHT));
    }
    Insets pad = root.padding().get();
    DropdownLayout.Placement place =
        DropdownLayout.place(
            x,
            y,
            lineHeight,
            width,
            height,
            new DropdownLayout.Root(
                root.x(), root.y(), root.width(), root.height(), pad.left(), pad.top()),
            preferAbove);

    panel.sizing(Sizing.fixed(width), Sizing.fixed(height));
    panel.positioning(Positioning.absolute(place.x(), place.y()));
    if (!isVisible()) {
      root.child(panel);
    }
    // Mounted (and laid out) first, so the selected row can be scrolled into view
    panel.scrollTo(0.0);
    select(SuggestionSelection.first(enabled));
  }

  public void hide() {
    if (isVisible()) {
      root.removeChild(panel);
    }
  }

  public boolean isVisible() {
    return root.children().contains(panel);
  }

  /** Whether {@code component} is this dropdown's panel or inside it. */
  public boolean owns(@Nullable UIComponent component) {
    for (UIComponent c = component; c != null; c = c.parent()) {
      if (c == panel) return true;
    }
    return false;
  }

  /** Arrow keys, Enter (and Tab when enabled) and Escape while open; true when consumed. */
  public boolean onKey(KeyEvent key) {
    if (!isVisible()) return false;
    if (key.isDown() || key.isUp()) {
      select(SuggestionSelection.move(enabled, selected, key.isDown() ? 1 : -1));
      return true;
    }
    if (key.isConfirmation() || (pickOnTab && key.isCycleFocus())) {
      if (selected != SuggestionSelection.NONE) pick(selected);
      return true;
    }
    if (key.isEscape()) {
      hide();
      return true;
    }
    return false;
  }

  /** The selected row's index, or {@link SuggestionSelection#NONE}. */
  public int selectedIndex() {
    return selected;
  }

  /**
   * Closes every open dropdown on {@code root} that the click at screen ({@code x}, {@code y})
   * missed.
   */
  public static void closeOnClickOutside(FlowLayout root, double x, double y) {
    for (UIComponent child : List.copyOf(root.children())) {
      if (child instanceof Panel p && !p.isInBoundingBox(x, y)) {
        UIComponent anchor = p.owner.anchor;
        if (anchor == null || !anchor.isInBoundingBox(x, y)) p.owner.hide();
      }
    }
  }

  /**
   * Closes the open dropdown that holds {@code focused} (its own scrollbar can take the focus);
   * true when one was closed.
   */
  public static boolean closeIfFocusedInside(FlowLayout root, UIComponent focused) {
    if (focused == null) return false;
    for (UIComponent child : List.copyOf(root.children())) {
      if (child instanceof Panel p && p.owner.owns(focused)) {
        p.owner.hide();
        return true;
      }
    }
    return false;
  }

  private Row row(int index) {
    if (index < rows.size()) return rows.get(index);
    Row row = new Row(this, index);
    row.id("suggestion-" + index);
    rows.add(row);
    list.child(row);
    return row;
  }

  private void select(int index) {
    selected = index;
    for (int i = 0; i < rows.size(); i++) rows.get(i).selected = i == index;
    if (index != SuggestionSelection.NONE) panel.reveal(index);
  }

  private void pick(int index) {
    if (index < 0 || index >= items.size() || !enabled[index]) return;
    T item = items.get(index);
    hide();
    onPick.accept(item);
    refocusAnchor();
  }

  /**
   * Gives the focus back to the anchor (a click on a row, which cannot take focus, cleared it), if
   * it is still on screen.
   */
  private void refocusAnchor() {
    if (anchor == null || !anchor.hasParent()) return;
    var focus = root.focusHandler();
    if (focus == null) return;
    if (focus.focused() != anchor) {
      focus.focus(anchor, UIComponent.FocusSource.MOUSE_CLICK);
    } else if (anchor instanceof TextFieldComponent field) {
      field.getDelegate().setFocused(true); // a setText in onPick drops the widget's own focus
    }
  }

  /**
   * The scroll holding the rows; marks the root child as a dropdown for the click-outside check.
   */
  private static final class Panel extends ScrollContainer<FlowLayout> {
    private final SuggestionDropdown<?> owner;

    Panel(FlowLayout list, SuggestionDropdown<?> owner) {
      super(ScrollDirection.VERTICAL, Sizing.fixed(1), Sizing.fixed(1), list);
      this.owner = owner;
    }

    /** Scrolls just enough for row {@code index} to be fully visible. */
    void reveal(int index) {
      int top = index * ROW_HEIGHT;
      int view = this.height - this.padding.get().vertical();
      if (top < scrollOffset) {
        scrollOffset = top;
      } else if (top + ROW_HEIGHT > scrollOffset + view) {
        scrollOffset = top + ROW_HEIGHT - view;
      }
      scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }
  }

  /**
   * One suggestion: a leaf that draws its title (shortened if needed), its kept suffix, greyed or
   * not, and its note right-aligned.
   */
  public static final class Row extends BaseUIComponent {
    private final SuggestionDropdown<?> owner;
    private final int index;
    private String title = "";
    @Nullable private Component suffix;
    private String suffixText = "";
    private boolean enabled;
    @Nullable private Component note;
    private boolean selected;

    // The title as drawn, recomputed only when the text or the room for it changes
    private int fittedFor = -1;
    private String fittedTitle = "";

    Row(SuggestionDropdown<?> owner, int index) {
      this.owner = owner;
      this.index = index;
    }

    void set(Entry entry) {
      this.title = entry.title() != null ? entry.title() : "";
      this.suffix = entry.suffix();
      this.suffixText = suffix != null ? suffix.getString() : "";
      this.enabled = entry.enabled();
      this.note = entry.note();
      this.fittedFor = -1;
    }

    /** Whether this row can be picked. */
    public boolean enabled() {
      return enabled;
    }

    /** The row's full text (title and suffix, without the note). */
    public String text() {
      return suffixText.isEmpty() ? title : title + " " + suffixText;
    }

    int naturalWidth(Font font) {
      int w = font.width(text()) + 2 * TEXT_INSET;
      if (note != null) w += NOTE_GAP + font.width(note);
      return w;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
      return naturalWidth(Minecraft.getInstance().font);
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
      return ROW_HEIGHT;
    }

    @Override
    public void draw(
        OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
      Font font = Minecraft.getInstance().font;
      int x = this.x();
      int y = this.y();
      int w = this.width();
      if (selected || (enabled && isInBoundingBox(mouseX, mouseY))) {
        context.fill(x, y, x + w, y + ROW_HEIGHT, Colors.DROPDOWN_SELECTED);
      }
      int textRight = x + w - TEXT_INSET;
      if (note != null) {
        int noteWidth = font.width(note);
        context.text(font, note, textRight - noteWidth, y + TEXT_Y, Colors.TEXT_MUTED, true);
        textRight -= noteWidth + NOTE_GAP;
      }
      int left = x + TEXT_INSET;
      int available = textRight - left;
      if (available != fittedFor) {
        fittedTitle = TextFit.title(title, suffixText, available, font::width);
        fittedFor = available;
      }
      int color = enabled ? Colors.DROPDOWN_TEXT : Colors.TEXT_DISABLED;
      int textEnd = left;
      if (!fittedTitle.isEmpty()) {
        context.text(font, fittedTitle, left, y + TEXT_Y, color, true);
        textEnd += font.width(fittedTitle + " ");
      }
      if (suffix != null) {
        context.text(font, suffix, textEnd, y + TEXT_Y, color, true);
      }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
      if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
      if (enabled) {
        owner.pick(index);
      } else {
        owner.refocusAnchor(); // the press cleared the field's focus; a greyed row keeps it open
      }
      return true;
    }
  }
}
