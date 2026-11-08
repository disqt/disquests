package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import java.util.ArrayList;
import java.util.List;

/** Pure list-ordering and layout rules for the home screen, kept free of Minecraft types. */
final class QuestListOrder {
  private static final int COLLAPSE_BELOW_WIDTH = 640;

  /** Narrowest side panel: fits the tab and action rows. */
  static final int MIN_PANEL_WIDTH = 180;

  private QuestListOrder() {}

  /** Unplaced quests first, then placed ones; each group keeps the incoming order. */
  static List<Quest> orderForList(List<Quest> filtered) {
    List<Quest> unplaced = new ArrayList<>();
    List<Quest> placed = new ArrayList<>();
    for (Quest q : filtered) {
      (q.hasLocation() ? placed : unplaced).add(q);
    }
    unplaced.addAll(placed);
    return unplaced;
  }

  /** The side panel starts hidden on GUIs narrower than 640 scaled pixels. */
  static boolean shouldCollapseList(int guiWidth) {
    return guiWidth < COLLAPSE_BELOW_WIDTH;
  }

  /** Clamps a side panel width to [180, max(180, 60 % of the GUI width)]. */
  static int clampPanelWidth(int requested, int guiWidth) {
    int max = Math.max(MIN_PANEL_WIDTH, guiWidth * 3 / 5);
    return Math.max(MIN_PANEL_WIDTH, Math.min(max, requested));
  }
}
