package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.Messages;
import com.disqt.disquests.client.gui.tree.SuggestionText;
import com.disqt.disquests.client.gui.tree.Suggestions.Suggestion;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A one-line field that suggests quests ("add a subquest...", "Prerequisites"). Focusing it opens
 * the dropdown with what the empty text suggests; typing refreshes it. Refused quests are listed
 * greyed with the reason in brackets. Picking one passes its id on, clears the text and keeps the
 * focus in the field. Rows are {@code suggestion-<index>} in the {@code <id>-suggestions} list.
 *
 * <p>Drawn as an input: a dark inset with a frame (brighter while focused), the placeholder in the
 * theme's muted colour, and optionally a leading glyph such as "+".
 */
public class QuestSearchField extends TextFieldComponent {

  public static final int HEIGHT = 14;

  private final FlowLayout root;
  private final Function<String, List<Suggestion>> source;
  private final SuggestionDropdown<Suggestion> dropdown;
  private Consumer<UUID> onPick = id -> {};
  private String shownFor;
  private String glyph;

  /**
   * @param root the screen root, where the dropdown is mounted
   * @param id the field's component id ({@code add-subquest-field}, {@code prerequisite-field})
   * @param placeholder grey text while empty
   * @param width the field's preferred width
   * @param source the suggestions for the text typed so far
   */
  public QuestSearchField(
      FlowLayout root,
      String id,
      Component placeholder,
      int width,
      Function<String, List<Suggestion>> source) {
    super(
        new MultiLineTextFieldWidget(
            Minecraft.getInstance().font,
            0,
            0,
            width,
            HEIGHT,
            "",
            placeholder.getString(),
            1,
            false));
    this.root = root;
    this.source = source;
    this.id(id);
    getDelegate().setMutedPlaceholder(true);
    this.dropdown =
        new SuggestionDropdown<>(root, id + "-suggestions", QuestSearchField::entry, this::picked);
    setAutocomplete(dropdown, this::refreshIfChanged);
  }

  /** A glyph drawn at the left inside the frame ("+" for "add"); the text starts after it. */
  public QuestSearchField leadingGlyph(String glyph) {
    this.glyph = glyph;
    lead(Minecraft.getInstance().font.width(glyph) + GLYPH_GAP);
    return this;
  }

  private static final int GLYPH_GAP = 1;
  private static final int TEXT_INSET = 5;

  @Override
  public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
    int x = x();
    int y = y();
    int w = width();
    int h = height();
    context.fill(x, y, x + w, y + h, Colors.FIELD_BACKGROUND);
    context.drawRectOutline(
        x,
        y,
        w,
        h,
        getDelegate().isFocused() ? Colors.FIELD_OUTLINE_FOCUSED : Colors.FIELD_OUTLINE);
    if (glyph != null) {
      var font = Minecraft.getInstance().font;
      // On the delegate's first line (it draws its text 5 px in from the top and the left)
      context.text(font, glyph, x + 4, y + TEXT_INSET, Colors.TEXT_MUTED, false);
    }
    super.draw(context, mouseX, mouseY, partialTicks, delta);
  }

  /** Called with the picked quest's id. */
  public QuestSearchField onPick(Consumer<UUID> onPick) {
    this.onPick = onPick;
    return this;
  }

  public SuggestionDropdown<Suggestion> dropdown() {
    return dropdown;
  }

  /** Lists the suggestions for the current text again (after the caller's data changed). */
  public void refresh() {
    shownFor = getText();
    dropdown.minWidth(this.width()).show(source.apply(shownFor), this);
  }

  /** After a key: only a changed text reopens the list, so Escape and caret moves leave it shut. */
  private void refreshIfChanged() {
    if (!Objects.equals(shownFor, getText())) refresh();
  }

  private static SuggestionDropdown.Entry entry(Suggestion s) {
    SuggestionText.Line line = SuggestionText.of(s, ClientSession.getMaxQuestDepth());
    return new SuggestionDropdown.Entry(
        line.title(),
        line.reason() != null ? Messages.toComponent(line.reason()) : null,
        line.enabled(),
        line.note() != null ? Messages.toComponent(line.note()) : null);
  }

  /** The dropdown gives the focus back to the field after this. */
  private void picked(Suggestion s) {
    onPick.accept(s.id());
    if (!hasParent()) return; // the caller rebuilt its pane without this field
    getDelegate().setText("");
    refresh();
  }

  @Override
  public void onFocusGained(FocusSource source) {
    super.onFocusGained(source);
    refresh();
  }

  @Override
  public void onFocusLost() {
    super.onFocusLost();
    // Deferred: a click on a dropdown row takes the focus before the row sees the click, so
    // closing right away would remove the row under the cursor; a pick or a click on a greyed row
    // focuses the field again. The dropdown's own scrollbar may hold the focus meanwhile.
    root.queue(
        () -> {
          var focus = root.focusHandler();
          UIComponent focused = focus != null ? focus.focused() : null;
          if (focused != this && !dropdown.owns(focused)) dropdown.hide();
        });
  }

  @Override
  public void dismount(DismountReason reason) {
    super.dismount(reason);
    if (reason == DismountReason.REMOVED) dropdown.hide();
  }
}
