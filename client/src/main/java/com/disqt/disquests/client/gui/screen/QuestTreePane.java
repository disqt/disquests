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

  private final QuestScreen screen;
  private final UUID questId;
  private final FlowLayout list;

  private QuestTreePane(QuestScreen screen, UUID questId, FlowLayout list) {
    this.screen = screen;
    this.questId = questId;
    this.list = list;
  }

  /**
   * Adds the pane and its divider before {@code quest-column} when there is something to show.
   * {@code guiWidth} bounds the pane's width.
   */
  static void attach(QuestScreen screen, FlowLayout root, UUID questId, int guiWidth) {
    if (!ClientSession.supportsTrees() || rows(questId).isEmpty()) return;
    FlowLayout body = root.childById(FlowLayout.class, "quest-body");

    FlowLayout pane = UIContainers.verticalFlow(Sizing.fixed(MIN_WIDTH), Sizing.fill(100));
    pane.id(PANE_ID);
    pane.padding(Insets.of(4));
    pane.gap(4);
    screen.applyThemePanel(pane);

    LabelComponent header = UIComponents.label(Component.translatable(QuestPage.WHERE_THIS_SITS));
    header.color(Color.ofArgb(Colors.TEXT_MUTED));
    header.shadow(true);
    pane.child(header);

    FlowLayout list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    list.id(LIST_ID);
    ScrollContainer<FlowLayout> scroll =
        UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(100), list);
    pane.child(scroll);

    PaneSplit split =
        new PaneSplit(
            pane,
            PaneSplit.Side.LEFT,
            DIVIDER_ID,
            DisquestsClient.CONFIG::questPaneWidth,
            DisquestsClient.CONFIG::questPaneWidth,
            requested -> PaneWidth.clamp(requested, MIN_WIDTH, guiWidth * 2 / 5));
    body.child(0, pane);
    body.child(1, split.divider());

    new QuestTreePane(screen, questId, list).fill();
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

  /** The current quest's row does nothing; any other opens that quest, back returning here. */
  private void open(UUID id) {
    if (!id.equals(questId)) screen.openOther(id);
  }

  private void toggle(UUID id) {
    ClientSession.toggleExpanded(id);
    fill();
  }
}
