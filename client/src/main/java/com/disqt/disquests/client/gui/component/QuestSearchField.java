package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.helper.Messages;
import com.disqt.disquests.client.gui.tree.SuggestionText;
import com.disqt.disquests.client.gui.tree.Suggestions.Suggestion;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import io.wispforest.owo.ui.container.FlowLayout;
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
 */
public class QuestSearchField extends TextFieldComponent {

  public static final int HEIGHT = 14;

  private final FlowLayout root;
  private final Function<String, List<Suggestion>> source;
  private final SuggestionDropdown<Suggestion> dropdown;
  private Consumer<UUID> onPick = id -> {};
  private String shownFor;

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
    this.dropdown =
        new SuggestionDropdown<>(root, id + "-suggestions", QuestSearchField::entry, this::picked);
    setAutocomplete(dropdown, this::refreshIfChanged);
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
    dropdown.width(this.width()).show(source.apply(shownFor), this);
  }

  private void refreshIfChanged() {
    if (!Objects.equals(shownFor, getText()) || !dropdown.isVisible()) refresh();
  }

  private static SuggestionDropdown.Entry entry(Suggestion s) {
    SuggestionText.Line line = SuggestionText.of(s, ClientSession.getMaxQuestDepth());
    return new SuggestionDropdown.Entry(
        Messages.toComponent(line.text()),
        line.enabled(),
        line.note() != null ? Messages.toComponent(line.note()) : null);
  }

  private void picked(Suggestion s) {
    onPick.accept(s.id());
    getDelegate().setText("");
    var focus = focusHandler();
    if (focus != null && focus.focused() != this) {
      focus.focus(this, FocusSource.MOUSE_CLICK); // refreshes through onFocusGained
    } else {
      getDelegate().setFocused(true); // setText drops the delegate's focus
      refresh();
    }
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
    // closing right away would remove the row under the cursor. A pick focuses the field again.
    root.queue(
        () -> {
          var focus = focusHandler();
          if (focus == null || focus.focused() != this) dropdown.hide();
        });
  }
}
