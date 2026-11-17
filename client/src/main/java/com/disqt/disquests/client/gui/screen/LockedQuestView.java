package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TreeRowComponent;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

/**
 * The page of a quest locked for the player: the header with a lock before the title, and in place
 * of the content what it is waiting on ({@code waiting-text}). No tags, location, people or
 * prerequisites section; the screen keeps only the Leave button, for a player holding a role.
 */
final class LockedQuestView {

  static final String LOCK_ID = "title-lock";

  private LockedQuestView() {}

  /** Turns the view model into the locked page. Call before the buttons are wired. */
  static void build(QuestScreen screen, FlowLayout root, Quest quest) {
    FlowLayout header = root.childById(FlowLayout.class, "header-row");
    LockGlyph lock = new LockGlyph();
    lock.id(LOCK_ID);
    lock.margins(Insets.left(4));
    header.child(1, lock);

    FlowLayout column = root.childById(FlowLayout.class, "quest-column");
    remove(column, "tag-display-slot");
    remove(column, "location-card");
    remove(column, "contributors-row-slot");
    UIComponent content = column.childById(UIComponent.class, "content-scroll");
    int at = column.children().indexOf(content);
    column.removeChild(content);
    column.child(at, PrerequisiteLinks.waitingText(screen, quest));
  }

  private static void remove(FlowLayout column, String id) {
    UIComponent child = column.childById(UIComponent.class, id);
    if (child != null) column.removeChild(child);
  }

  /** The padlock drawn by the tree rows, as a component. */
  private static final class LockGlyph extends BaseUIComponent {
    LockGlyph() {
      sizing(Sizing.fixed(7), Sizing.fixed(10));
    }

    @Override
    public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float d) {
      TreeRowComponent.drawLock(context, x(), y());
    }
  }
}
