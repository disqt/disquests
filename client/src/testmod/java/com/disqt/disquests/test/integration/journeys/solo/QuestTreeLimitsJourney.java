package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TreeRowComponent;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The tree's limits from the editor: a drag that would make four levels (the default limit is
 * three) is refused with a toast and leaves the draft as it was; completing a quest with its
 * checkbox unticked completes only that quest, and its subquests then list at the top level.
 */
@IntegrationTest
@DisplayName("Quest Tree Limits Journey")
class QuestTreeLimitsJourney {

  private static final String TOP = "Limit Top";
  private static final String MIDDLE = "Limit Middle";
  private static final String BOTTOM = "Limit Bottom";
  private static final String SIDE = "Limit Side";

  private static UUID top;
  private static UUID middle;
  private static UUID bottom;
  private static UUID side;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Build a three-level tree with a second subquest beside it")
  void buildTree(ClientGameTestContext context) {
    given("a fresh server and the My Quests tab");
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
        });
    top = createQuest(context, TOP, null);
    middle = createQuest(context, MIDDLE, null);
    bottom = createQuest(context, BOTTOM, null);
    side = createQuest(context, SIDE, null);

    when("player puts the middle, bottom and side quests under the top one, then the bottom");
    and("quest under the middle one, in the top quest's editor");
    openQuest(context, "tab-my-quests", TOP);
    enterEditor(context);
    pickSuggestion(context, "add-subquest-field", MIDDLE);
    waitForTreeRow(context, middle, l -> l.depth() == 1);
    pickSuggestion(context, "add-subquest-field", BOTTOM);
    waitForTreeRow(context, bottom, l -> l.depth() == 1);
    pickSuggestion(context, "add-subquest-field", SIDE);
    waitForTreeRow(context, side, l -> l.depth() == 1);
    click(context, "tree-hint");
    dragTreeRowOnto(context, bottom, middle);
    waitForTreeRow(context, bottom, l -> l.depth() == 2);
    saveEditor(context);

    then("the cache holds Top > Middle > Bottom and Top > Side");
    waitForQuestCondition(context, MIDDLE, true, q -> top.equals(q.getParentId()));
    waitForQuestCondition(context, BOTTOM, true, q -> middle.equals(q.getParentId()));
    waitForQuestCondition(context, SIDE, true, q -> top.equals(q.getParentId()));
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("A drag that would make four levels is refused with a toast")
  void refusedMove(ClientGameTestContext context) {
    given("the top quest's editor");
    openQuest(context, "tab-my-quests", TOP);
    enterEditor(context);
    waitForTreeRow(context, bottom, l -> l.depth() == 2);

    when("player drags the middle quest (which holds the bottom one) onto the side quest");
    dragTreeRowOnto(context, middle, side);

    then("a toast says it would be too deep");
    waitForToast(
        context,
        DisquestsBaseScreen.REFUSED_TOAST,
        t -> t.contains(MIDDLE) && t.contains("too deep, limit 3 levels"));

    and("the tree is as it was, and no row says 'not saved'");
    waitForTreeRow(context, middle, l -> l.depth() == 1);
    waitForTreeRow(context, bottom, l -> l.depth() == 2);
    waitForTreeRow(context, side, l -> l.depth() == 1);
    for (UUID id : new UUID[] {top, middle, bottom, side}) {
      TreeRowComponent.Line line = context.computeOnClient(c -> treeRow(c, id).line());
      assertFalse(line.changed() || line.takenOut(), "Row of " + id + " is marked: " + line);
    }

    when("player saves");
    saveEditor(context);

    then("nothing moved");
    context.waitTicks(seconds(1));
    assertEquals(top, parentOf(context, MIDDLE));
    assertEquals(middle, parentOf(context, BOTTOM));
    assertEquals(top, parentOf(context, SIDE));
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("Completing the top quest with the checkbox unticked completes only that quest")
  void completeOnlyThisQuest(ClientGameTestContext context) {
    given("the top quest's page");
    waitForComponent(context, "btn-complete");

    when("player presses Complete");
    click(context, "btn-complete");

    then("the dialog offers the three unfinished subquests");
    waitForText(
        context,
        DisquestsBaseScreen.CONFIRM_CHECKBOX_ID,
        "Also complete my 3 unfinished subquests"::equals);

    when("player confirms with the checkbox unticked");
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("only the top quest is completed");
    waitForQuestCondition(context, TOP, true, Quest::isCompleted);
    for (String title : new String[] {MIDDLE, BOTTOM, SIDE}) {
      waitForQuestCondition(context, title, true, q -> !q.isCompleted());
    }

    and("its subquests list at the top level of My Quests, the top quest under Archived");
    openMainScreen(context);
    click(context, "tab-my-quests");
    waitForListed(context, MIDDLE, true);
    assertListed(context, SIDE, true);
    assertListed(context, TOP, false);
    // The bottom quest is still inside the middle one, which starts collapsed
    assertListed(context, BOTTOM, false);
    assertComponentExists(context, "toggle-" + middle);
    click(context, "tab-archived");
    waitForListed(context, TOP, true);
    click(context, "tab-my-quests");
    waitForListed(context, MIDDLE, true);
  }

  // --- helpers ---

  private static UUID parentOf(ClientGameTestContext context, String title) {
    return context.computeOnClient(c -> cachedQuest(title).getParentId());
  }
}
