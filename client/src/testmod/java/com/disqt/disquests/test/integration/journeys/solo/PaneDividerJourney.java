package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.gui.component.SplitterComponent;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.TestContext;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.container.FlowLayout;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The dividers that resize a pane: the quest page's tree pane ({@code quest-pane-divider}, pane on
 * the left) and the map home's quest list ({@code panel-divider}, pane on the right). The pane
 * follows the divider while the mouse is held, stops at its smallest and largest width, and the
 * width is saved when the mouse is released, so the next page opens with it.
 */
@IntegrationTest
@DisplayName("Pane Divider Journey")
class PaneDividerJourney {

  private static final String TOP = "Pane Top";
  private static final String CHILD = "Pane Child";

  private static final String TREE_PANE = "where-this-sits";
  private static final String TREE_DIVIDER = "quest-pane-divider";
  private static final String SIDE_PANEL = "side-panel";
  private static final String PANEL_DIVIDER = "panel-divider";

  /** The tree pane's smallest width, and the width the journey starts it from. */
  private static final int TREE_MIN = 100;

  private static final int TREE_START = 150;

  /** The side panel's smallest width, which is also its default. */
  private static final int PANEL_MIN = 216;

  private static int savedTreeWidth;
  private static int savedPanelWidth;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
    ClientGameTestContext context = TestContext.get();
    savedTreeWidth = context.computeOnClient(c -> DisquestsClient.CONFIG.questPaneWidth());
    savedPanelWidth = context.computeOnClient(c -> DisquestsClient.CONFIG.sidePanelWidth());
    setWidths(context, TREE_START, PANEL_MIN);
  }

  /** The widths are client config, kept in a file: put back what the journey found. */
  @AfterAll
  static void restoreWidths() {
    ClientGameTestContext context = TestContext.get();
    // A drag left unfinished by a failed step must not hold the button for the next journey
    context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
    setWidths(context, savedTreeWidth, savedPanelWidth);
  }

  private static void setWidths(ClientGameTestContext context, int tree, int panel) {
    context.runOnClient(
        c -> {
          DisquestsClient.CONFIG.questPaneWidth(tree);
          DisquestsClient.CONFIG.sidePanelWidth(panel);
          DisquestsClient.CONFIG.save();
        });
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("The tree pane follows its divider and the width is saved on release")
  void treePaneFollowsDivider(ClientGameTestContext context) {
    given("a quest with a subquest, so its page has the tree pane");
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
        });
    UUID top = createQuest(context, TOP, null);
    createQuest(context, CHILD, null);
    openQuest(context, "tab-my-quests", TOP);
    enterEditor(context);
    pickSuggestion(context, "add-subquest-field", CHILD);
    saveEditor(context);
    waitForQuestCondition(context, CHILD, true, q -> top.equals(q.getParentId()));
    openQuest(context, "tab-my-quests", TOP);
    waitForComponent(context, TREE_DIVIDER);

    then("the pane opens at the saved width");
    waitForWidth(context, TREE_PANE, TREE_START);
    assertTrue(TREE_START + 40 < treeMax(context), "the screen is wide enough for the drag");

    when("player presses the divider and moves 40 px away from the pane");
    double[] from = pressDivider(context, TREE_DIVIDER);
    moveTo(context, from, from[0] + 40);

    then("the pane is 40 px wider while the mouse is still held, and nothing is saved yet");
    waitForWidth(context, TREE_PANE, TREE_START + 40);
    assertTrue(dragging(context, TREE_DIVIDER), "the divider is being dragged");
    assertEquals(TREE_START, treeConfig(context), "saved only on release");

    when("player releases the mouse");
    release(context);

    then("the new width is saved");
    context.waitFor(c -> DisquestsClient.CONFIG.questPaneWidth() == TREE_START + 40, TIMEOUT);
    assertFalse(dragging(context, TREE_DIVIDER), "the drag has ended");
    assertEquals(TREE_START + 40, paneWidth(context, TREE_PANE));
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("The tree pane stops at its largest and smallest width")
  void treePaneIsClamped(ClientGameTestContext context) {
    given("the quest page, its pane 190 px wide");
    openQuest(context, "tab-my-quests", TOP);
    waitForComponent(context, TREE_DIVIDER);
    waitForWidth(context, TREE_PANE, TREE_START + 40);
    int max = treeMax(context);
    int screenWidth = screenWidth(context);

    when("player drags the divider to the right edge of the screen");
    double[] from = pressDivider(context, TREE_DIVIDER);
    moveTo(context, from, screenWidth - 3);
    release(context);

    then("the pane stops at two fifths of the screen's width");
    waitForWidth(context, TREE_PANE, max);
    context.waitFor(c -> DisquestsClient.CONFIG.questPaneWidth() == max, TIMEOUT);

    when("player drags it back by 30 px: each drag starts from the width the pane has");
    from = pressDivider(context, TREE_DIVIDER);
    moveTo(context, from, from[0] - 30);
    release(context);

    then("the pane is 30 px narrower than its largest width");
    waitForWidth(context, TREE_PANE, max - 30);
    context.waitFor(c -> DisquestsClient.CONFIG.questPaneWidth() == max - 30, TIMEOUT);

    when("player drags the divider to the left edge of the screen");
    from = pressDivider(context, TREE_DIVIDER);
    moveTo(context, from, 2);
    release(context);

    then("the pane stops at its smallest width");
    waitForWidth(context, TREE_PANE, TREE_MIN);
    context.waitFor(c -> DisquestsClient.CONFIG.questPaneWidth() == TREE_MIN, TIMEOUT);

    when("player presses and releases the divider without moving");
    pressDivider(context, TREE_DIVIDER);
    release(context);

    then("nothing changes");
    assertEquals(TREE_MIN, paneWidth(context, TREE_PANE));
    assertEquals(TREE_MIN, treeConfig(context));

    and("the page opens with the saved width the next time");
    openQuest(context, "tab-my-quests", TOP);
    waitForComponent(context, TREE_DIVIDER);
    waitForWidth(context, TREE_PANE, TREE_MIN);
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("The map home's quest list widens when its divider moves left, within its limits")
  void sidePanelFollowsDivider(ClientGameTestContext context) {
    given("the map home with its quest list on the right, at its smallest width");
    openMainScreen(context);
    waitForComponent(context, PANEL_DIVIDER);
    waitForWidth(context, SIDE_PANEL, PANEL_MIN);
    int screenWidth = screenWidth(context);
    int max = Math.max(PANEL_MIN, screenWidth * 3 / 5);
    assertTrue(PANEL_MIN + 50 < max, "the screen is wide enough for the drag");

    when("player drags the divider 50 px to the left, away from the list");
    double[] from = pressDivider(context, PANEL_DIVIDER);
    moveTo(context, from, from[0] - 50);

    then("the list is 50 px wider");
    waitForWidth(context, SIDE_PANEL, PANEL_MIN + 50);
    release(context);
    context.waitFor(c -> DisquestsClient.CONFIG.sidePanelWidth() == PANEL_MIN + 50, TIMEOUT);

    when("player drags the divider to the left edge of the screen");
    from = pressDivider(context, PANEL_DIVIDER);
    moveTo(context, from, 2);
    release(context);

    then("the list stops at three fifths of the screen's width");
    waitForWidth(context, SIDE_PANEL, max);
    context.waitFor(c -> DisquestsClient.CONFIG.sidePanelWidth() == max, TIMEOUT);

    when("player drags the divider to the right edge of the screen");
    from = pressDivider(context, PANEL_DIVIDER);
    moveTo(context, from, screenWidth - 3);
    release(context);

    then("the list stops at its smallest width");
    waitForWidth(context, SIDE_PANEL, PANEL_MIN);
    context.waitFor(c -> DisquestsClient.CONFIG.sidePanelWidth() == PANEL_MIN, TIMEOUT);
  }

  // --- helpers ---

  /** Moves the cursor onto the divider and holds the left button; returns where it pressed. */
  private static double[] pressDivider(ClientGameTestContext context, String dividerId) {
    SplitterComponent divider = findComponent(context, SplitterComponent.class, dividerId);
    double[] at = componentCenter(context, divider);
    double scale = scaleFactor(context);
    context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
    context.waitTicks(1);
    context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(1);
    return at;
  }

  /** Moves the held cursor sideways to {@code toX} (GUI px) in small steps, as a hand does. */
  private static void moveTo(ClientGameTestContext context, double[] from, double toX) {
    double scale = scaleFactor(context);
    int steps = 8;
    for (int i = 1; i <= steps; i++) {
      double x = from[0] + (toX - from[0]) * i / steps;
      context.getInput().setCursorPos(x * scale, from[1] * scale);
      context.waitTicks(1);
    }
  }

  private static void release(ClientGameTestContext context) {
    context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
  }

  private static void waitForWidth(ClientGameTestContext context, String paneId, int expected) {
    int[] last = {-1};
    try {
      context.waitFor(
          client -> {
            FlowLayout pane = onScreen(client, FlowLayout.class, paneId);
            last[0] = pane == null ? -1 : pane.width();
            return last[0] == expected;
          },
          TIMEOUT);
    } catch (Throwable t) {
      throw new AssertionError(
          "Width of '" + paneId + "' was " + last[0] + ", expected " + expected, t);
    }
  }

  private static int paneWidth(ClientGameTestContext context, String paneId) {
    FlowLayout pane = findComponent(context, FlowLayout.class, paneId);
    return context.computeOnClient(c -> pane.width());
  }

  private static boolean dragging(ClientGameTestContext context, String dividerId) {
    SplitterComponent divider = findComponent(context, SplitterComponent.class, dividerId);
    return context.computeOnClient(c -> divider.isDragging());
  }

  private static int treeConfig(ClientGameTestContext context) {
    return context.computeOnClient(c -> DisquestsClient.CONFIG.questPaneWidth());
  }

  private static int screenWidth(ClientGameTestContext context) {
    return context.computeOnClient(c -> c.gui.screen().width);
  }

  /** The tree pane's largest width: two fifths of the screen's. */
  private static int treeMax(ClientGameTestContext context) {
    return screenWidth(context) * 2 / 5;
  }
}
