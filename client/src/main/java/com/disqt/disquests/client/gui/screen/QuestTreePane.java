package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.data.QuestGraphs;
import com.disqt.disquests.client.gui.component.MinHeightScroll;
import com.disqt.disquests.client.gui.component.TreeRowComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.tree.QuestTreeRows;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The reading view's left pane, the quest tree: the rows of {@link QuestTreeRows#readingTree}, each
 * opening its quest, next to a divider that resizes the pane (config {@code questPaneWidth}).
 * Absent for a quest with no visible parent and no visible subquests, and against servers without
 * quest trees.
 *
 * <p>Expansion uses the session's expanded set shared with the main screen's lists, so a quest
 * opened in one place is opened in the other.
 */
final class QuestTreePane {

  static final String PANE_ID = "where-this-sits";
  static final String LIST_ID = "where-this-sits-list";
  static final String DIVIDER_ID = "quest-pane-divider";

  private static final int MIN_WIDTH = 100;
  private static final int SCROLLBAR_WIDTH = 3;

  /**
   * The pane's inset from the screen's left edge: the screen's own outer margin (the root's top
   * padding in {@code quest_screen_view.xml} and {@code quest_screen_edit.xml}).
   */
  private static final int SCREEN_MARGIN = 4;

  private final QuestScreen screen;
  private final UUID questId;
  private final FlowLayout pane;
  private final FlowLayout list;
  private final MinHeightScroll<FlowLayout> scroll;
  private boolean revealPending = true;

  private QuestTreePane(
      QuestScreen screen,
      UUID questId,
      FlowLayout pane,
      FlowLayout list,
      MinHeightScroll<FlowLayout> scroll) {
    this.screen = screen;
    this.questId = questId;
    this.pane = pane;
    this.list = list;
    this.scroll = scroll;
  }

  /**
   * Adds the pane and its divider before {@code quest-column} when there is something to show, and
   * returns it; null when there is none.
   */
  @Nullable
  static QuestTreePane attach(QuestScreen screen, FlowLayout root, UUID questId) {
    if (!ClientSession.supportsTrees() || rows(questId).isEmpty()) return null;
    Shell shell =
        shell(
            screen,
            root.childById(FlowLayout.class, "quest-body"),
            Component.translatable(QuestPage.QUEST_TREE),
            PANE_ID,
            LIST_ID);
    QuestTreePane treePane =
        new QuestTreePane(
            screen,
            questId,
            shell.pane(),
            shell.list(),
            (MinHeightScroll<FlowLayout>) shell.scroll());
    treePane.fill();
    return treePane;
  }

  /** The parts of a tree pane: the pane itself, its scrolling list and the scroll. */
  record Shell(FlowLayout pane, FlowLayout list, ScrollContainer<FlowLayout> scroll) {}

  /**
   * Builds a tree pane, shared by the reading view and the editor: a header, a scrolling list that
   * takes the height left, and the divider that resizes it (config {@code questPaneWidth}). Inserts
   * both at the start of {@code body}.
   */
  static Shell shell(
      QuestScreen screen, FlowLayout body, Component header, String paneId, String listId) {
    FlowLayout pane = UIContainers.verticalFlow(Sizing.fixed(MIN_WIDTH), Sizing.fill(100));
    pane.id(paneId);
    pane.padding(Insets.of(4));
    pane.margins(Insets.left(SCREEN_MARGIN));
    pane.gap(4);
    screen.applyThemePanel(pane);

    LabelComponent headerLabel = UIComponents.label(header);
    headerLabel.horizontalSizing(Sizing.fill(100));
    headerLabel.color(Color.ofArgb(Colors.TEXT_MUTED));
    headerLabel.shadow(true);
    pane.child(headerLabel);

    FlowLayout list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    list.id(listId);
    // owo draws the bar inside the scroll's content box and forwards a press on it to the child:
    // pad the rows clear of it so pressing the bar never acts on a row.
    list.padding(Insets.right(SCROLLBAR_WIDTH + 2));
    // No minimum height; the subclass lets the reading view carry its position across a rebuild
    ScrollContainer<FlowLayout> scroll =
        new MinHeightScroll<>(Sizing.fill(100), Sizing.expand(100), list, 0);
    scroll.scrollbarThiccness(SCROLLBAR_WIDTH);
    pane.child(scroll);

    PaneSplit split =
        new PaneSplit(
            pane,
            PaneSplit.Side.LEFT,
            DIVIDER_ID,
            DisquestsClient.CONFIG::questPaneWidth,
            DisquestsClient.CONFIG::questPaneWidth,
            // The screen's width as it is now, not when the pane was built
            requested -> PaneWidth.clamp(requested, MIN_WIDTH, screen.width * 2 / 5));
    body.child(0, pane);
    body.child(1, split.divider());
    return new Shell(pane, list, scroll);
  }

  private static List<QuestTreeRows.Row> rows(UUID questId) {
    return QuestTreeRows.readingTree(
        QuestGraphs.current(),
        ClientSession.getEffectivePlayerUuid(),
        questId,
        ClientSession.getExpandedQuests());
  }

  /** One layout pass for the clear and the refill, so the scroll keeps its place. */
  private void fill() {
    list.<FlowLayout>configure(l -> fillRows());
  }

  private void fillRows() {
    list.clearChildren();
    QuestGraph graph = QuestGraphs.current();
    for (QuestTreeRows.Row row : rows(questId)) {
      QuestData q = graph.get(row.id());
      if (q == null) continue;
      TreeRowComponent.Line line =
          new TreeRowComponent.Line(
              row.id(),
              q.title(),
              row.depth(),
              row.id().equals(questId),
              row.locked(),
              q.isCompleted(),
              false,
              false,
              false,
              false,
              row.childCount(),
              row.expanded());
      TreeRowComponent component = new TreeRowComponent(line).readMode(this::open, this::toggle);
      // Resting on another quest's row previews it right of the pane (a locked one shows nothing)
      if (!row.id().equals(questId)) screen.previewBeside(component, row.id(), pane);
      list.child(component);
    }
  }

  /**
   * Once the rows are laid out, scrolls the current quest's row into view when it lies below the
   * visible part of a long pane.
   */
  void tick() {
    if (!revealPending) return;
    TreeRowComponent current =
        list.childById(TreeRowComponent.class, "tree-row-" + questId.toString());
    if (current == null || !current.hasParent()) return;
    revealPending = false;
    if (current.y() + current.height() > scroll.y() + scroll.height()) {
      scroll.scrollTo(current);
    }
  }

  /** How far the rows are scrolled, to carry over to a rebuilt page. */
  double offset() {
    return scroll.offset();
  }

  /**
   * Restores a carried scroll position once the pane is laid out, instead of the automatic reveal
   * of the current row. False while not mounted yet.
   */
  boolean restore(double offset) {
    if (!scroll.hasParent()) return false;
    revealPending = false;
    scroll.jumpTo(offset);
    return true;
  }

  /** The current quest's row does nothing; any other opens that quest, back returning here. */
  private void open(UUID id) {
    if (!id.equals(questId)) screen.openOther(id);
  }

  /**
   * Expands or collapses a row. The current quest and the quests it sits in always show what is
   * under them, so their toggles are ignored rather than changing the shared expanded set for
   * nothing.
   */
  private void toggle(UUID id) {
    if (id.equals(questId)) return;
    for (QuestData ancestor : QuestGraphs.current().ancestors(questId)) {
      if (ancestor.id().equals(id)) return;
    }
    ClientSession.toggleExpanded(id);
    fill();
    screen.rememberShownPage();
  }
}
