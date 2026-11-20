package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.client.gui.component.TreeRowComponent;
import com.disqt.disquests.client.gui.component.TreeToggleComponent;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Builds a three-level tree from the edit screen (the "add a subquest..." field and a drag), reads
 * it in the lists and the quest page, then works the editor's draft: Cancel drops a drag, a move
 * and back leaves nothing to save, a drop on a subquest swaps, and a quest is taken out from each
 * side (the parent's remove button, the child's drop on the take-out strip).
 */
@IntegrationTest
@DisplayName("Quest Tree Journey")
class QuestTreeJourney {

  private static final String TOP = "Tree Top";
  private static final String MIDDLE = "Tree Middle";
  private static final String BOTTOM = "Tree Bottom";

  private static UUID top;
  private static UUID middle;
  private static UUID bottom;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Create three quests in the editor")
  void createQuests(ClientGameTestContext context) {
    given("a fresh server and the My Quests tab");
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
        });

    when("player creates three quests, each on its own");
    top = createQuest(context, TOP, null);
    middle = createQuest(context, MIDDLE, null);
    bottom = createQuest(context, BOTTOM, null);

    then("all three are top-level quests in My Quests");
    openMainScreen(context);
    waitForListed(context, TOP, true);
    waitForListed(context, MIDDLE, true);
    waitForListed(context, BOTTOM, true);
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Arrange three levels from the edit screen: field, then drag")
  void arrangeThreeLevels(ClientGameTestContext context) {
    given("the top quest's editor");
    openQuest(context, "tab-my-quests", TOP);
    enterEditor(context);

    when("player adds the middle quest through 'add a subquest...'");
    pickSuggestion(context, "add-subquest-field", MIDDLE);
    then("it shows under the top quest, not saved");
    waitForTreeRow(context, middle, l -> l.depth() == 1 && l.changed());

    when("player adds the bottom quest the same way");
    pickSuggestion(context, "add-subquest-field", BOTTOM);
    waitForTreeRow(context, bottom, l -> l.depth() == 1 && l.changed());
    // A click on the hint closes the field's list, which would cover the rows below it
    click(context, "tree-hint");

    and("drags the bottom quest's row onto the middle quest's row");
    dragTreeRowOnto(context, bottom, middle);

    then("the bottom quest sits inside the middle one, not saved");
    waitForTreeRow(context, bottom, l -> l.depth() == 2 && l.changed());

    when("player saves");
    saveEditor(context);

    then("the client cache holds the three-level tree");
    waitForQuestCondition(context, MIDDLE, true, q -> top.equals(q.getParentId()));
    waitForQuestCondition(context, BOTTOM, true, q -> middle.equals(q.getParentId()));
    waitForQuestCondition(context, TOP, true, q -> q.getParentId() == null);
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("The list nests the tree, collapsed by default; the page shows where it sits")
  void listAndPage(ClientGameTestContext context) {
    given("the main screen on My Quests");
    openMainScreen(context);
    click(context, "tab-my-quests");

    then("only the top quest is listed, with a toggle, collapsed, and a count of 1");
    waitForListed(context, TOP, true);
    waitForListed(context, MIDDLE, false);
    assertListed(context, BOTTOM, false);
    assertToggle(context, top, false);
    QuestEntryComponent topEntry = context.computeOnClient(c -> listedEntry(c, TOP));
    int count = context.computeOnClient(c -> topEntry.getMarks().childCount());
    assertEquals(1, count, "subquest count on the top quest's entry");

    when("player expands the top quest");
    click(context, "toggle-" + top);
    then("the middle quest shows, collapsed, with its own toggle");
    waitForListed(context, MIDDLE, true);
    assertListed(context, BOTTOM, false);
    assertToggle(context, middle, false);

    when("player expands the middle quest");
    click(context, "toggle-" + middle);
    then("the bottom quest shows");
    waitForListed(context, BOTTOM, true);

    when("player opens the middle quest");
    clickEntryByTitle(context, MIDDLE);
    click(context, "btn-open");
    waitForScreen(context, QuestScreen.class);

    then("'where this sits' shows its parent, itself highlighted, and its subquest");
    waitForComponent(context, "where-this-sits");
    waitForTreeRow(context, top, l -> l.depth() == 0 && !l.current());
    waitForTreeRow(context, middle, l -> l.depth() == 1 && l.current());
    waitForTreeRow(context, bottom, l -> l.depth() == 2 && !l.current());
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("A search shows a nested match with its ancestors expanded")
  void searchExpandsAncestors(ClientGameTestContext context) {
    given("the list with the top quest collapsed again");
    openMainScreen(context);
    click(context, "tab-my-quests");
    waitForListed(context, TOP, true);
    if (context.computeOnClient(c -> ClientSession.getExpandedQuests().contains(top))) {
      click(context, "toggle-" + top);
    }
    waitForListed(context, MIDDLE, false);

    when("player searches for the bottom quest");
    type(context, "search-box", BOTTOM);

    then("it shows with the top and middle quests, both expanded");
    waitForListed(context, BOTTOM, true);
    assertListed(context, TOP, true);
    assertListed(context, MIDDLE, true);
    assertToggle(context, top, true);
    assertToggle(context, middle, true);

    when("player clears the search");
    type(context, "search-box", "");

    then("the top quest is collapsed as before");
    waitForListed(context, MIDDLE, false);
    assertListed(context, TOP, true);
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("Cancel drops a drag after asking")
  void cancelDropsDrag(ClientGameTestContext context) {
    given("the top quest's editor");
    openQuest(context, "tab-my-quests", TOP);
    enterEditor(context);

    when("player drags the bottom quest onto the top quest");
    dragTreeRowOnto(context, bottom, top);
    waitForTreeRow(context, bottom, l -> l.depth() == 1 && l.changed());

    and("presses Cancel and confirms the discard");
    click(context, "btn-cancel");
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("the reading view is back and the tree is unchanged");
    waitForComponent(context, "btn-edit");
    waitForTreeRow(context, middle, l -> l.depth() == 1);
    assertEquals(middle, context.computeOnClient(c -> cachedQuest(BOTTOM).getParentId()));
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("A move and back leaves nothing marked and nothing to save")
  void moveAndBack(ClientGameTestContext context) {
    given("the top quest's editor");
    waitForComponent(context, "btn-edit");
    enterEditor(context);

    when("player drags the bottom quest onto the top quest, then back onto the middle one");
    dragTreeRowOnto(context, bottom, top);
    waitForTreeRow(context, bottom, l -> l.depth() == 1 && l.changed());
    dragTreeRowOnto(context, bottom, middle);

    then("it is back in place and no row says 'not saved'");
    waitForTreeRow(context, bottom, l -> l.depth() == 2 && !l.changed());
    for (UUID id : new UUID[] {top, middle, bottom}) {
      TreeRowComponent.Line line = context.computeOnClient(c -> treeRow(c, id).line());
      assertFalse(line.changed() || line.takenOut(), "Row of " + id + " is marked: " + line);
    }

    when("player presses Cancel");
    click(context, "btn-cancel");

    then("the editor closes without asking: there is nothing to save");
    waitForComponent(context, "btn-edit");
    assertComponentMissing(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
  }

  @Test
  @Order(7)
  @PlayerA
  @DisplayName("Dropping a quest onto its own subquest swaps them")
  void dropOntoSubquestSwaps(ClientGameTestContext context) {
    given("the top quest's editor");
    waitForComponent(context, "btn-edit");
    enterEditor(context);

    when("player drags the top quest onto the middle quest, its subquest");
    dragTreeRowOnto(context, top, middle);

    then("the middle quest takes its place and holds the top quest");
    waitForTreeRow(context, middle, l -> l.depth() == 0 && l.changed());
    waitForTreeRow(context, top, l -> l.depth() == 1 && l.changed());
    waitForTreeRow(context, bottom, l -> l.depth() == 1 && !l.changed());

    when("player saves");
    saveEditor(context);

    then("the cache has the middle quest on top with both others inside");
    waitForQuestCondition(context, MIDDLE, true, q -> q.getParentId() == null);
    waitForQuestCondition(context, TOP, true, q -> middle.equals(q.getParentId()));
    waitForQuestCondition(context, BOTTOM, true, q -> middle.equals(q.getParentId()));
  }

  @Test
  @Order(8)
  @PlayerA
  @DisplayName("Take out from the child's side: drop on the take-out strip")
  void takeOutWithStrip(ClientGameTestContext context) {
    given("the editor of the quest now inside the middle one");
    waitForComponent(context, "btn-edit");
    enterEditor(context);
    waitForTreeRow(context, top, l -> l.depth() == 1);

    when("player drags its own row onto the take-out strip");
    dragTreeRowOut(context, top);

    then("it is listed below the tree as taken out, not saved");
    waitForTreeRow(context, top, TreeRowComponent.Line::takenOut);

    when("player saves");
    saveEditor(context);

    then("it is a top-level quest again, with no 'where this sits' pane");
    waitForQuestCondition(context, TOP, true, q -> q.getParentId() == null);
    context.waitFor(
        client -> onScreen(client, TreeRowComponent.class, "tree-row-" + top) == null, TIMEOUT);
    assertComponentMissing(context, "where-this-sits");
  }

  @Test
  @Order(9)
  @PlayerA
  @DisplayName("Take out from the parent's side: the row's remove button")
  void takeOutWithRemove(ClientGameTestContext context) {
    given("the middle quest's editor");
    openQuest(context, "tab-my-quests", MIDDLE);
    enterEditor(context);
    waitForTreeRow(context, bottom, l -> l.depth() == 1 && l.removable());

    when("player clicks the remove button on the bottom quest's row");
    clickTreeRowRemove(context, bottom);

    then("it is listed as taken out, not saved");
    waitForTreeRow(context, bottom, TreeRowComponent.Line::takenOut);

    when("player saves");
    saveEditor(context);

    then("all three quests are top level");
    waitForQuestCondition(context, BOTTOM, true, q -> q.getParentId() == null);
    waitForQuestCondition(context, MIDDLE, true, q -> q.getParentId() == null);

    and("the list shows them all without toggles");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    openMainScreen(context);
    waitForListed(context, BOTTOM, true);
    assertListed(context, TOP, true);
    assertListed(context, MIDDLE, true);
    assertComponentMissing(context, "toggle-" + middle);
  }

  // --- helpers ---

  private static void assertToggle(ClientGameTestContext context, UUID quest, boolean expanded) {
    TreeToggleComponent toggle =
        findComponent(context, TreeToggleComponent.class, "toggle-" + quest);
    assertTrue(
        Objects.equals(expanded, context.computeOnClient(c -> toggle.isExpanded())),
        "toggle-" + quest + " expanded should be " + expanded);
  }
}
