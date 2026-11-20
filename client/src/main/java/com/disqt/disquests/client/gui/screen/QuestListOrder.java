package com.disqt.disquests.client.gui.screen;

/**
 * Pure layout rules for the home screen, kept free of Minecraft types. The lists' order lives in
 * {@link com.disqt.disquests.client.gui.tree.QuestTreeRows}.
 */
final class QuestListOrder {
  private static final int COLLAPSE_BELOW_WIDTH = 640;

  /** Narrowest side panel: fits the tab and action rows. */
  static final int MIN_PANEL_WIDTH = 216;

  private QuestListOrder() {}

  /** The side panel starts hidden on GUIs narrower than 640 scaled pixels. */
  static boolean shouldCollapseList(int guiWidth) {
    return guiWidth < COLLAPSE_BELOW_WIDTH;
  }

  /** Clamps a side panel width to [216, max(216, 60 % of the GUI width)]. */
  static int clampPanelWidth(int requested, int guiWidth) {
    int max = Math.max(MIN_PANEL_WIDTH, guiWidth * 3 / 5);
    return Math.max(MIN_PANEL_WIDTH, Math.min(max, requested));
  }
}
