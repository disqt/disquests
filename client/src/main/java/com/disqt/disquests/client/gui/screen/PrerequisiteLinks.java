package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.data.QuestGraphs;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.tree.WaitingText;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The prerequisite lists of the reading view: what a locked quest is waiting on ({@code
 * waiting-text}) and the Prerequisites section shown to Write and Owner ({@code
 * prerequisites-section}). Each visible prerequisite is a line {@code prerequisite-<id>} that opens
 * it; a completed one carries a tick, drawn only.
 */
final class PrerequisiteLinks {

  static final String WAITING_ID = "waiting-text";
  static final String SECTION_ID = "prerequisites-section";
  static final String LINE_PREFIX = "prerequisite-";

  private static final int LINK_COLOR = 0xe8a86d;

  private PrerequisiteLinks() {}

  /** The three spec sentences: the list, the list and "something else", or "not available". */
  static FlowLayout waitingText(QuestScreen screen, Quest quest) {
    QuestGraph graph = QuestGraphs.current();
    WaitingText.Waiting waiting = WaitingText.of(quest, graph);
    FlowLayout block = block(screen, WAITING_ID);
    if (waiting.nothingVisible()) {
      block.child(text(Component.translatable(QuestPage.WAITING_NOTHING_VISIBLE), false));
      return block;
    }
    block.child(text(Component.translatable(QuestPage.WAITING_PREREQUISITES), true));
    for (UUID id : waiting.visible()) block.child(link(screen, graph, id));
    if (waiting.somethingElse()) {
      block.child(text(Component.translatable(QuestPage.WAITING_SOMETHING_ELSE), true));
    }
    return block;
  }

  /**
   * The Prerequisites section: one line per visible prerequisite, then the lines for hidden and
   * deleted ones, then a muted line when auto-join is on.
   */
  static FlowLayout section(QuestScreen screen, Quest quest) {
    QuestGraph graph = QuestGraphs.current();
    FlowLayout block = block(screen, SECTION_ID);
    block.child(text(Component.translatable(QuestPage.PREREQUISITES), true));
    for (UUID id : quest.getPrerequisites()) {
      if (graph.get(id) != null) block.child(link(screen, graph, id));
    }
    if (quest.hasHiddenPrerequisites()) {
      block.child(text(Component.translatable(QuestPage.PREREQUISITE_HIDDEN), true));
    }
    if (quest.hasDeletedPrerequisite()) {
      block.child(text(Component.translatable(QuestPage.PREREQUISITE_DELETED), true));
    }
    if (quest.isAutoJoin()) {
      block.child(text(Component.translatable(QuestPage.PREREQUISITE_AUTO_JOIN), true));
    }
    return block;
  }

  private static FlowLayout block(QuestScreen screen, String id) {
    FlowLayout block = UIContainers.verticalFlow(Sizing.fill(85), Sizing.content());
    block.id(id);
    block.padding(Insets.of(4));
    block.gap(3);
    block.margins(Insets.top(2));
    screen.applyThemePanel(block);
    return block;
  }

  private static LabelComponent text(Component text, boolean muted) {
    LabelComponent label = UIComponents.label(text);
    label.horizontalSizing(Sizing.fill(100));
    label.color(Color.ofArgb(muted ? Colors.TEXT_MUTED : Colors.TEXT_PRIMARY));
    label.shadow(true);
    return label;
  }

  private static LabelComponent link(QuestScreen screen, QuestGraph graph, UUID id) {
    QuestData q = graph.get(id);
    MutableComponent text = Component.empty();
    if (q.isCompleted()) {
      text.append(Component.literal("✔ ").withColor(Colors.TREE_COMPLETED & 0xFFFFFF));
    }
    String title = q.title() != null ? q.title() : "";
    text.append(
        Component.literal(title).withStyle(s -> s.withUnderlined(true).withColor(LINK_COLOR)));
    LabelComponent label = UIComponents.label(text);
    label.id(LINE_PREFIX + id);
    label.shadow(true);
    label
        .mouseDown()
        .subscribe(
            (click, doubled) -> {
              screen.openOther(id);
              return true;
            });
    return label;
  }
}
