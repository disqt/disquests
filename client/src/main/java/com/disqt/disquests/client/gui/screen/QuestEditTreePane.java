package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.component.DropTracker;
import com.disqt.disquests.client.gui.component.QuestSearchField;
import com.disqt.disquests.client.gui.component.TreeRowComponent;
import com.disqt.disquests.client.gui.component.TreeRowGeometry;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.tree.QuestEditDraft;
import com.disqt.disquests.client.gui.tree.QuestTreeRows;
import com.disqt.disquests.client.gui.tree.Suggestions;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.QuestData;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/**
 * The editor's left pane: the tree the quest was in when the editor opened, from {@link
 * QuestEditDraft#rows}, with nothing applied until Save. Rows drag onto other rows ({@code
 * TreeDraft.move}: attach, or swap with a subquest) or onto the dashed {@code tree-drop-out} area
 * ({@code TreeDraft.detach}); a row's remove button detaches it too. Directly under the edited
 * quest's line, the {@code add-subquest-field} ("+", framed as an input) brings in a quest from
 * outside the tree. After the tree, inside the same scroll, the quests taken out of it ("taken out,
 * not saved"; the edited quest among them while it is outside the tree, with the field under its
 * line). The drop strip and the hint sit below the scroll, so a short pane still shows them. The
 * header reads "QUEST TREE": the pane shows the whole tree, not only subquests. A refused change
 * shows a toast and changes nothing.
 *
 * <p>After an accepted change only the pane's contents are rebuilt, in one layout pass so the
 * scroll position stays, and {@code onChanged} lets the form follow. The field and its holder are
 * built once and moved, never rebuilt, so they keep the focus, the typed text and the dropdown.
 */
final class QuestEditTreePane implements TreeRowComponent.DragTarget {

  static final String PANE_ID = "edit-tree-pane";
  static final String LIST_ID = "edit-tree-list";
  static final String ADD_SUBQUEST_ID = "add-subquest-field";
  static final String DROP_OUT_ID = "tree-drop-out";
  static final String TAKEN_OUT_ID = "tree-taken-out";
  static final String HINT_ID = "tree-hint";
  static final String NOW_INSIDE_ID = "tree-now-inside";

  private static final int DASH = 3;

  private final QuestScreen screen;
  private final FlowLayout root;
  private final QuestEditDraft draft;
  private final boolean isNewQuest;
  private final Supplier<String> currentTitle;
  private final Runnable onChanged;
  private final FlowLayout list;
  private final ScrollContainer<FlowLayout> scroll;
  private final FlowLayout takenOutList;
  private final DropTracker.Zone dropOutZone;
  private final QuestSearchField field;
  private final FlowLayout fieldHolder;

  private List<TreeRowComponent> rows = List.of();
  private DropTracker tracker;
  private boolean dropOutHighlighted;
  private boolean revealPending = true;
  private String shownTitle;

  private QuestEditTreePane(
      QuestScreen screen,
      FlowLayout root,
      QuestEditDraft draft,
      boolean isNewQuest,
      Supplier<String> currentTitle,
      Runnable onChanged,
      QuestTreePane.Shell shell) {
    this.screen = screen;
    this.root = root;
    this.draft = draft;
    this.isNewQuest = isNewQuest;
    this.currentTitle = currentTitle;
    this.onChanged = onChanged;
    this.list = shell.list();
    this.scroll = shell.scroll();
    this.field = addSubquestField();
    this.fieldHolder = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
    fieldHolder.child(field);

    // After the rows, in the scrolled list, so the strip and the hint below stay in view
    this.takenOutList = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    takenOutList.id(TAKEN_OUT_ID);

    FlowLayout dropOut = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    dropOut.id(DROP_OUT_ID);
    dropOut.padding(Insets.of(4));
    dropOut.horizontalAlignment(HorizontalAlignment.CENTER);
    dropOut.surface(this::drawDropOut);
    LabelComponent dropText = UIComponents.label(Component.translatable(EditorText.DROP_OUT));
    dropText.horizontalSizing(Sizing.fill(100));
    dropText.horizontalTextAlignment(HorizontalAlignment.CENTER);
    dropText.color(Color.ofArgb(Colors.TEXT_MUTED));
    dropText.shadow(true);
    dropOut.child(dropText);
    shell.pane().child(dropOut);
    this.dropOutZone = TreeRowComponent.dropZone(dropOut, h -> dropOutHighlighted = h);

    LabelComponent hint = UIComponents.label(Component.translatable(EditorText.TREE_HINT));
    hint.id(HINT_ID);
    hint.horizontalSizing(Sizing.fill(100));
    hint.color(Color.ofArgb(Colors.TEXT_MUTED));
    hint.shadow(true);
    shell.pane().child(hint);
  }

  /**
   * Adds the pane and its divider at the start of {@code quest-body}.
   *
   * @param currentTitle the title field's text, for a new quest's own line
   * @param onChanged runs after each accepted tree change
   */
  static QuestEditTreePane attach(
      QuestScreen screen,
      FlowLayout root,
      QuestEditDraft draft,
      boolean isNewQuest,
      Supplier<String> currentTitle,
      Runnable onChanged) {
    QuestTreePane.Shell shell =
        QuestTreePane.shell(
            screen,
            root.childById(FlowLayout.class, "quest-body"),
            Component.translatable(EditorText.QUEST_TREE),
            PANE_ID,
            LIST_ID);
    QuestEditTreePane pane =
        new QuestEditTreePane(screen, root, draft, isNewQuest, currentTitle, onChanged, shell);
    pane.fill();
    return pane;
  }

  /**
   * Rebuilds the rows and the taken-out lines from the draft, moving the field under the edited
   * quest's line wherever it is. One layout pass, so the scroll keeps its place.
   */
  void fill() {
    list.<FlowLayout>configure(l -> takenOutList.<FlowLayout>configure(t -> fillRowsAndTakenOut()));
    tracker =
        new DropTracker(
            rows,
            List.of(dropOutZone),
            () -> new DropTracker.Bounds(scroll.x(), scroll.y(), scroll.width(), scroll.height()));
    // The list follows the draft; shown again where the field now sits
    if (field.dropdown().isVisible()) field.refresh();
  }

  private void fillRowsAndTakenOut() {
    UUID me = ClientSession.getEffectivePlayerUuid();
    QuestGraph g = draft.tree().effective();
    UUID questId = draft.questId();
    list.clearChildren();
    takenOutList.clearChildren();
    shownTitle = currentTitle.get();

    List<TreeRowComponent> built = new ArrayList<>();
    for (QuestTreeRows.Row row : draft.rows(me)) {
      QuestData q = g.get(row.id());
      if (q == null) continue;
      boolean current = row.id().equals(questId);
      TreeRowComponent.Line line =
          new TreeRowComponent.Line(
              row.id(),
              title(q),
              row.depth(),
              current,
              row.locked(),
              q.isCompleted(),
              draft.tree().isChanged(row.id()),
              false,
              draft.canRemove(row.id(), me),
              draft.canDrag(row.id()),
              row.childCount(),
              row.expanded());
      TreeRowComponent component =
          new TreeRowComponent(line)
              .editMode(this::remove, this)
              .removeTooltip(takeOutOf(g, q.parentId()));
      built.add(component);
      list.child(component);
      if (current) list.child(placeField(row.depth() + 1));
    }
    rows = built;
    list.child(takenOutList);

    for (QuestEditDraft.TakenOut out : draft.takenOut(me)) {
      QuestData q = g.get(out.id());
      if (q == null) continue;
      boolean current = out.id().equals(questId);
      // Its subquests went along; they stay folded under this line
      TreeRowComponent.Line line =
          new TreeRowComponent.Line(
              out.id(),
              title(q),
              0,
              current,
              false,
              q.isCompleted(),
              false,
              true,
              false,
              draft.canDrag(out.id()),
              g.children(out.id()).size(),
              false);
      takenOutList.child(new TreeRowComponent(line).editMode(this::remove, this));
      QuestData container = g.get(out.nowInside());
      if (container != null) takenOutList.child(nowInside(container));
      if (current) takenOutList.child(placeField(1));
    }
  }

  /** The edited quest's title: for a quest not saved yet, the title field as typed. */
  private String title(QuestData q) {
    if (!isNewQuest || !q.id().equals(draft.questId())) return q.title();
    return shownTitle == null || shownTitle.isBlank()
        ? Component.translatable(EditorText.NEW_QUEST).getString()
        : shownTitle;
  }

  /** "Take out of X", the remove zone's tooltip; null for a quest at the top of the tree. */
  private static Component takeOutOf(QuestGraph g, UUID parentId) {
    QuestData parent = parentId != null ? g.get(parentId) : null;
    if (parent == null) return null;
    return Component.translatable(
        EditorText.TAKE_OUT_OF, parent.title() != null ? parent.title() : "?");
  }

  /** "now inside X", under a taken-out line that sits in another quest. */
  private LabelComponent nowInside(QuestData container) {
    LabelComponent label =
        UIComponents.label(
            Component.translatable(
                EditorText.NOW_INSIDE, container.title() != null ? container.title() : "?"));
    label.id(NOW_INSIDE_ID);
    label.horizontalSizing(Sizing.fill(100));
    label.color(Color.ofArgb(Colors.TEXT_MUTED));
    label.shadow(true);
    label.margins(Insets.left(TreeRowGeometry.LEFT + TreeRowGeometry.INDENT));
    return label;
  }

  /** The field's holder, indented to {@code depth}. */
  private FlowLayout placeField(int depth) {
    int indent =
        TreeRowGeometry.LEFT + Math.min(depth, QuestTreeRows.MAX_INDENT) * TreeRowGeometry.INDENT;
    fieldHolder.padding(Insets.of(1, 1, indent, 0));
    return fieldHolder;
  }

  /** The "add a subquest..." field, built once. */
  private QuestSearchField addSubquestField() {
    UUID me = ClientSession.getEffectivePlayerUuid();
    QuestSearchField searchField =
        new QuestSearchField(
            root,
            ADD_SUBQUEST_ID,
            Component.translatable(EditorText.ADD_SUBQUEST),
            100,
            typed ->
                Suggestions.subquests(
                    draft.tree().effective(),
                    me,
                    draft.questId(),
                    typed,
                    ClientSession.getMaxQuestDepth()));
    searchField.leadingGlyph("+");
    searchField.sizing(Sizing.fill(100), Sizing.fixed(QuestSearchField.HEIGHT));
    searchField.onPick(
        picked ->
            apply(
                picked,
                draft.tree().move(picked, draft.questId(), me, ClientSession.getMaxQuestDepth())));
    return searchField;
  }

  /** Once laid out, scrolls the edited quest's row into view when it is below the visible part. */
  void tick() {
    // A new quest's own line follows the title field as the player types
    if (isNewQuest && !java.util.Objects.equals(shownTitle, currentTitle.get())) fill();
    if (!revealPending) return;
    TreeRowComponent current =
        list.childById(TreeRowComponent.class, "tree-row-" + draft.questId());
    if (current == null || !current.hasParent()) return;
    revealPending = false;
    if (current.y() + current.height() > scroll.y() + scroll.height()) scroll.scrollTo(current);
  }

  // --- changes ---

  private void remove(UUID quest) {
    apply(quest, draft.tree().detach(quest, ClientSession.getEffectivePlayerUuid()));
  }

  /**
   * A refused change: a toast worded like the server's refusals. An accepted one: the pane is
   * rebuilt once the current event is over (the row or field that fired it is still in use).
   */
  private void apply(UUID quest, Optional<Refusal> refusal) {
    if (refusal.isPresent()) {
      screen.showRefused(quest, refusal.get());
      return;
    }
    root.queue(
        () -> {
          fill();
          onChanged.run();
          screen.dropStaleFocus();
        });
  }

  // --- drag and drop ---

  @Override
  public void dragMoved(TreeRowComponent source, int absX, int absY) {
    tracker.moved(source, absX, absY);
  }

  @Override
  public void dropped(TreeRowComponent source, int absX, int absY) {
    DropTracker.Zone zone = tracker.dropped(source, absX, absY);
    if (zone == null) return;
    UUID me = ClientSession.getEffectivePlayerUuid();
    UUID quest = source.questId();
    if (zone instanceof TreeRowComponent target) {
      apply(
          quest, draft.tree().move(quest, target.questId(), me, ClientSession.getMaxQuestDepth()));
    } else if (DROP_OUT_ID.equals(zone.id())) {
      apply(quest, draft.tree().detach(quest, me));
    }
  }

  @Override
  public void dragCancelled(TreeRowComponent source) {
    tracker.clear();
  }

  /** A dashed outline, filled while a dragged row is over it. */
  private void drawDropOut(OwoUIGraphics context, ParentUIComponent component) {
    int x = component.x();
    int y = component.y();
    int w = component.width();
    int h = component.height();
    if (dropOutHighlighted) context.fill(x, y, x + w, y + h, Colors.TREE_DROP_TARGET);
    int color = dropOutHighlighted ? Colors.TEXT_PRIMARY : Colors.TEXT_MUTED;
    for (int i = 0; i < w; i += DASH * 2) {
      int end = Math.min(x + i + DASH, x + w);
      context.fill(x + i, y, end, y + 1, color);
      context.fill(x + i, y + h - 1, end, y + h, color);
    }
    for (int i = 0; i < h; i += DASH * 2) {
      int end = Math.min(y + i + DASH, y + h);
      context.fill(x, y + i, x + 1, end, color);
      context.fill(x + w - 1, y + i, x + w, end, color);
    }
  }
}
