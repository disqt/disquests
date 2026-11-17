package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.component.QuestSearchField;
import com.disqt.disquests.client.gui.component.TagChipComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.tree.QuestEditDraft;
import com.disqt.disquests.client.gui.tree.Suggestions;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The editor's link controls under the form, all part of the draft until Save: for an owner whose
 * quest sits inside a quest they cannot see, the read-only line with {@code
 * btn-detach-hidden-parent}; the three visibility buttons ({@code vis-private}, {@code vis-closed},
 * {@code vis-open}) with the setting's explanation beside them; the Prerequisites field ({@code
 * prerequisite-field}) and its removable chips ({@code prereq-chip-<id>}, {@code
 * prereq-chip-hidden}, {@code prereq-chip-deleted}); and the {@code auto-join} checkbox, shown when
 * there are prerequisites and usable only while the draft's visibility is Open.
 *
 * <p>Owners only; prerequisites, auto-join and the hidden-parent line also need a server with quest
 * trees. Fills the {@code links-form} container of the edit XML, which is removed when there is
 * nothing to show.
 */
final class QuestLinksForm {

  static final String ID = "links-form";
  static final String DETACH_HIDDEN_PARENT_ID = "btn-detach-hidden-parent";
  static final String PREREQUISITE_FIELD_ID = "prerequisite-field";
  static final String AUTO_JOIN_ID = "auto-join";
  static final String CHIP_PREFIX = "prereq-chip-";

  private final QuestScreen screen;
  private final FlowLayout root;
  private final QuestEditDraft draft;
  private final boolean trees;
  private final Runnable onTreeChanged;
  private final FlowLayout hiddenParentRow;
  private final FlowLayout visibilityRow;
  private final FlowLayout chips;
  private final FlowLayout autoJoinRow;

  private QuestLinksForm(
      QuestScreen screen,
      FlowLayout root,
      FlowLayout form,
      QuestEditDraft draft,
      boolean trees,
      Runnable onTreeChanged) {
    this.screen = screen;
    this.root = root;
    this.draft = draft;
    this.trees = trees;
    this.onTreeChanged = onTreeChanged;

    hiddenParentRow = row();
    hiddenParentRow.id("hidden-parent-row");
    form.child(hiddenParentRow);

    visibilityRow = row();
    visibilityRow.id("visibility-row");
    form.child(visibilityRow);

    if (trees) {
      FlowLayout prerequisiteRow = row();
      prerequisiteRow.id("prerequisite-row");
      prerequisiteRow.child(label(Component.translatable(EditorText.PREREQUISITES), false));
      UUID me = ClientSession.getEffectivePlayerUuid();
      QuestSearchField field =
          new QuestSearchField(
              root,
              PREREQUISITE_FIELD_ID,
              Component.translatable(EditorText.PREREQUISITE_PLACEHOLDER),
              150,
              typed ->
                  Suggestions.prerequisites(
                      draft.tree().effective(), me, draft.questId(), draft.prerequisites(), typed));
      field.sizing(Sizing.expand(100), Sizing.fixed(QuestSearchField.HEIGHT));
      field.onPick(
          picked -> {
            draft.addPrerequisite(picked);
            root.queue(this::refresh);
          });
      prerequisiteRow.child(field);
      form.child(prerequisiteRow);
    }

    chips = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
    chips.id("prereq-chips");
    chips.gap(3);
    form.child(chips);

    autoJoinRow = row();
    autoJoinRow.id("auto-join-row");
    form.child(autoJoinRow);
  }

  /**
   * Fills {@code links-form} for an owner and returns the controls; removes the container and
   * returns null for anyone else.
   *
   * @param onTreeChanged runs after the hidden-parent Detach, so the tree pane follows
   */
  static QuestLinksForm attach(
      QuestScreen screen,
      FlowLayout root,
      QuestEditDraft draft,
      boolean owner,
      Runnable onTreeChanged) {
    FlowLayout form = root.childById(FlowLayout.class, ID);
    if (!owner) {
      // parent() is still null inside build(): remove it through the column
      root.childById(FlowLayout.class, "quest-column").removeChild(form);
      return null;
    }
    QuestLinksForm links =
        new QuestLinksForm(screen, root, form, draft, ClientSession.supportsTrees(), onTreeChanged);
    links.refresh();
    return links;
  }

  /** Rebuilds what follows the draft: the hidden-parent line, the buttons, chips and checkbox. */
  void refresh() {
    fillHiddenParent();
    fillVisibility();
    fillChips();
    fillAutoJoin();
  }

  private void fillHiddenParent() {
    hiddenParentRow.clearChildren();
    QuestEditDraft.HiddenParent state = trees ? draft.hiddenParent() : null;
    if (state == null || state == QuestEditDraft.HiddenParent.NONE) {
      hiddenParentRow.margins(Insets.none());
      return;
    }
    hiddenParentRow.margins(Insets.bottom(2));
    MutableComponent text = Component.translatable(EditorText.HIDDEN_PARENT);
    if (state == QuestEditDraft.HiddenParent.DETACHED) {
      text.append(Component.literal("  "))
          .append(Component.translatable(EditorText.NOT_SAVED).withColor(Colors.AMBER));
    }
    LabelComponent line = label(text, true);
    line.horizontalSizing(Sizing.expand(100));
    hiddenParentRow.child(line);
    if (state == QuestEditDraft.HiddenParent.INSIDE) {
      ButtonComponent detach =
          UIComponents.button(
              Component.translatable(EditorText.DETACH),
              ignored -> {
                var refusal =
                    draft
                        .tree()
                        .detachFromHiddenParent(
                            draft.questId(), ClientSession.getEffectivePlayerUuid());
                if (refusal.isPresent()) {
                  screen.showRefused(draft.questId(), refusal.get());
                } else {
                  root.queue(onTreeChanged);
                }
              });
      detach.id(DETACH_HIDDEN_PARENT_ID);
      detach.sizing(Sizing.content(), Sizing.fixed(16));
      hiddenParentRow.child(detach);
    }
  }

  private void fillVisibility() {
    visibilityRow.clearChildren();
    visibilityRow.child(label(Component.translatable(EditorText.VISIBILITY), false));
    for (Visibility v : Visibility.values()) {
      ButtonComponent button =
          UIComponents.button(
              Component.translatable(EditorText.visibility(v)),
              ignored -> {
                draft.visibility(v);
                root.queue(this::refresh);
              });
      button.id("vis-" + v.name().toLowerCase(Locale.ROOT));
      button.sizing(Sizing.content(), Sizing.fixed(16));
      // The current setting shows pressed, as the main screen's tabs and filters do
      button.active(draft.visibility() != v);
      visibilityRow.child(button);
    }
    MutableComponent hint = Component.translatable(EditorText.visibilityHint(draft.visibility()));
    if (trees && draft.hasParent()) {
      hint.append(Component.literal(" "))
          .append(Component.translatable(EditorText.VISIBILITY_IN_PARENT));
    }
    LabelComponent hintLabel = label(hint, true);
    hintLabel.id("visibility-hint");
    hintLabel.horizontalSizing(Sizing.expand(100));
    visibilityRow.child(hintLabel);
  }

  private void fillChips() {
    chips.clearChildren();
    if (!trees) return;
    for (UUID id : draft.prerequisites()) {
      QuestData q = draft.tree().effective().get(id);
      String title = q != null && q.title() != null ? q.title() : "?";
      chips.child(chip(CHIP_PREFIX + id, title, () -> draft.removePrerequisite(id)));
    }
    if (draft.showsHiddenPrerequisites()) {
      chips.child(
          chip(
              CHIP_PREFIX + "hidden",
              Component.translatable(EditorText.PREREQUISITE_HIDDEN).getString(),
              draft::dropHidden));
    }
    if (draft.showsDeletedPrerequisite()) {
      chips.child(
          chip(
              CHIP_PREFIX + "deleted",
              Component.translatable(EditorText.PREREQUISITE_DELETED).getString(),
              draft::dropDeleted));
    }
  }

  private TagChipComponent chip(String id, String text, Runnable remove) {
    TagChipComponent chip =
        new TagChipComponent(text, true)
            .onRemove(
                ignored -> {
                  remove.run();
                  root.queue(this::refresh);
                });
    chip.id(id);
    return chip;
  }

  private void fillAutoJoin() {
    autoJoinRow.clearChildren();
    if (!trees || !draft.hasPrerequisites()) return;
    boolean usable = draft.visibility() == Visibility.OPEN;
    MutableComponent text = Component.translatable(EditorText.AUTO_JOIN);
    if (!usable) {
      text.append(Component.literal(" ")).append(Component.translatable(EditorText.OPEN_ONLY));
      text.withColor(Colors.TEXT_MUTED);
    }
    AutoJoinCheckbox box = new AutoJoinCheckbox(text, usable);
    box.id(AUTO_JOIN_ID);
    box.checked(draft.autoJoin());
    box.labelShadow(true);
    box.onChanged().subscribe(draft::autoJoin);
    autoJoinRow.child(box);
  }

  // --- helpers ---

  private static FlowLayout row() {
    FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
    row.gap(4);
    row.verticalAlignment(VerticalAlignment.CENTER);
    return row;
  }

  private static LabelComponent label(Component text, boolean muted) {
    LabelComponent label = UIComponents.label(text);
    label.shadow(true);
    if (muted) label.color(Color.ofArgb(Colors.TEXT_MUTED));
    return label;
  }

  /** owo's checkbox has no disabled state: this one ignores input while not usable. */
  static final class AutoJoinCheckbox extends SmallCheckboxComponent {
    private final boolean usable;

    AutoJoinCheckbox(Component label, boolean usable) {
      super(label);
      this.usable = usable;
    }

    boolean usable() {
      return usable;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
      return usable && super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onKeyPress(KeyEvent input) {
      return usable && super.onKeyPress(input);
    }

    @Override
    public boolean canFocus(FocusSource source) {
      return usable && super.canFocus(source);
    }
  }
}
