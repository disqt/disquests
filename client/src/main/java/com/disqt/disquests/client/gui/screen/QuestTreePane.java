package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.data.QuestGraphs;
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
 * The reading view's left pane, "where this sits": the rows of {@link QuestTreeRows#whereThisSits},
 * each opening its quest, next to a divider that resizes the pane (config {@code questPaneWidth}).
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

  private final QuestScreen screen;
  private final UUID questId;
  private final FlowLayout list;
  private final ScrollContainer<FlowLayout> scroll;
  private boolean revealPending = true;

  private QuestTreePane(
      QuestScreen screen, UUID questId, FlowLayout list, ScrollContainer<FlowLayout> scroll) {
    this.screen = screen;
    this.questId = questId;
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
    FlowLayout body = root.childById(FlowLayout.class, "quest-body");

    FlowLayout pane = UIContainers.verticalFlow(Sizing.fixed(MIN_WIDTH), Sizing.fill(100));
    pane.id(PANE_ID);
    pane.padding(Insets.of(4));
    pane.gap(4);
    screen.applyThemePanel(pane);

    LabelComponent header = UIComponents.label(Component.translatable(QuestPage.WHERE_THIS_SITS));
    header.horizontalSizing(Sizing.fill(100));
    header.color(Color.ofArgb(Colors.TEXT_MUTED));
    header.shadow(true);
    pane.child(header);

    FlowLayout list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    list.id(LIST_ID);
    // owo draws the bar inside the scroll's content box and forwards a press on it to the child:
    // pad the rows clear of it so pressing the bar never opens a quest.
    list.padding(Insets.right(SCROLLBAR_WIDTH + 2));
    ScrollContainer<FlowLayout> scroll =
        UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(100), list);
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

    QuestTreePane treePane = new QuestTreePane(screen, questId, list, scroll);
    treePane.fill();
    return treePane;
  }

  private static List<QuestTreeRows.Row> rows(UUID questId) {
    return QuestTreeRows.whereThisSits(
        QuestGraphs.current(),
        ClientSession.getEffectivePlayerUuid(),
        questId,
        ClientSession.getExpandedQuests());
  }

  private void fill() {
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
      list.child(new TreeRowComponent(line).readMode(this::open, this::toggle));
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

  /** The current quest's row does nothing; any other opens that quest, back returning here. */
  private void open(UUID id) {
    if (!id.equals(questId)) screen.openOther(id);
  }

  /**
   * Expands or collapses a row. The current quest's subquests always show, so its toggle is ignored
   * rather than changing the shared expanded set for nothing.
   */
  private void toggle(UUID id) {
    if (id.equals(questId)) return;
    ClientSession.toggleExpanded(id);
    fill();
    screen.rememberShownPage();
  }
}
