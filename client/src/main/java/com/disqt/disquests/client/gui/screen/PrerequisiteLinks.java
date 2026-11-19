package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.data.QuestGraphs;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.tree.WaitingText;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import com.mojang.blaze3d.platform.InputConstants;
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
 * it and previews it on hover like a wiki-link; a completed one carries a tick, drawn only.
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
    for (UUID id : waiting.visible()) {
      QuestData q = graph.get(id);
      block.child(link(screen, new QuestPage.Link(id, q.title(), q.isCompleted())));
    }
    if (waiting.somethingElse()) {
      block.child(text(Component.translatable(QuestPage.WAITING_SOMETHING_ELSE), true));
    }
    return block;
  }

  /**
   * The Prerequisites section, lines from {@link QuestPage#prerequisites}: the visible
   * prerequisites, then "… and something you don't have access to", "a deleted quest" and the
   * auto-join line when they apply.
   */
  static FlowLayout section(QuestScreen screen, Quest quest) {
    QuestPage.Prerequisites lines = QuestPage.prerequisites(quest.toData(), QuestGraphs.current());
    FlowLayout block = block(screen, SECTION_ID);
    block.child(text(Component.translatable(QuestPage.PREREQUISITES), true));
    for (QuestPage.Link link : lines.links()) block.child(link(screen, link));
    if (lines.somethingHidden()) {
      block.child(text(Component.translatable(QuestPage.PREREQUISITE_HIDDEN), true));
    }
    if (lines.deleted()) {
      block.child(text(Component.translatable(QuestPage.PREREQUISITE_DELETED), true));
    }
    if (lines.autoJoin()) {
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

  private static LabelComponent link(QuestScreen screen, QuestPage.Link link) {
    MutableComponent text = Component.empty();
    if (link.completed()) {
      text.append(Component.literal("✔ ").withColor(Colors.TREE_COMPLETED & 0xFFFFFF));
    }
    String title = link.title() != null ? link.title() : "";
    text.append(
        Component.literal(title).withStyle(s -> s.withUnderlined(true).withColor(LINK_COLOR)));
    LabelComponent label = UIComponents.label(text);
    label.id(LINE_PREFIX + link.id());
    label.horizontalSizing(Sizing.fill(100));
    label.shadow(true);
    // The same preview as a wiki-link; none for a quest locked for the player
    screen.previewOnHover(label, link.id());
    label
        .mouseDown()
        .subscribe(
            (click, doubled) -> {
              if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
              screen.openOther(link.id());
              return true;
            });
    return label;
  }
}
