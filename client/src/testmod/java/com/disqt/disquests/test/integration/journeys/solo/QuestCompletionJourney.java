package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Complete a quest, find it in the Archived tab as a read-only record, then reopen it back into My
 * Quests.
 */
@IntegrationTest
@DisplayName("Quest Completion Journey")
class QuestCompletionJourney {

  private static final String TITLE = "Done Quest";

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Create an active quest")
  void createQuest(ClientGameTestContext context) {
    given("a fresh server and the My Quests tab");
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
        });

    when("player creates and saves a quest");
    openMainScreen(context);
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", TITLE);
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);
    assertLabelText(context, "title-label", TITLE);

    then("the quest is listed in My Quests");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    waitForQuestByTitle(context, TITLE, true);
    openMainScreen(context);
    waitForListed(context, TITLE, true);
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Complete the quest: it leaves My Quests")
  void completeQuest(ClientGameTestContext context) {
    given("the quest is open in view mode");
    openMainScreen(context);
    clickEntryByTitle(context, TITLE);
    click(context, "btn-open");
    waitForScreen(context, QuestScreen.class);
    waitForComponent(context, "btn-complete");
    assertComponentMissing(context, "btn-reopen");
    assertComponentMissing(context, "completed-label");

    when("player clicks Complete and confirms");
    click(context, "btn-complete");
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("the server marks the quest completed");
    waitForQuestCondition(context, TITLE, true, Quest::isCompleted);

    and("the view returns to My Quests, which no longer lists it");
    waitForScreen(context, MainScreen.class);
    waitForListed(context, TITLE, false);
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("Archived tab lists the quest as a read-only record")
  void archivedTabShowsQuest(ClientGameTestContext context) {
    when("player switches to the Archived tab");
    openMainScreen(context);
    click(context, "tab-archived");

    then("the completed quest is listed");
    waitForListed(context, TITLE, true);

    when("player opens it");
    clickEntryByTitle(context, TITLE);
    click(context, "btn-open");
    waitForScreen(context, QuestScreen.class);

    then("it shows when it was completed, with Reopen and no Edit or Complete");
    waitForComponent(context, "completed-label");
    LabelComponent label = findComponent(context, LabelComponent.class, "completed-label");
    String text = context.computeOnClient(c -> label.text().getString());
    assertTrue(text.startsWith("Completed on"), "completed-label text was '" + text + "'");
    assertComponentExists(context, "btn-reopen");
    assertComponentMissing(context, "btn-edit");
    assertComponentMissing(context, "btn-complete");
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("Reopen the quest: it returns to My Quests")
  void reopenQuest(ClientGameTestContext context) {
    given("the archived quest is open in view mode");
    waitForComponent(context, "btn-reopen");

    when("player clicks Reopen and confirms");
    click(context, "btn-reopen");
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("the server reopens the quest");
    waitForQuestCondition(context, TITLE, true, q -> !q.isCompleted());

    and("the view rebuilds as an active quest");
    // Same-class rebuild: wait on a component only the active view has, not on the screen class.
    waitForComponent(context, "btn-complete");
    assertComponentExists(context, "btn-edit");
    assertComponentMissing(context, "completed-label");

    when("player goes back and switches to My Quests");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    waitForListed(context, TITLE, false);
    click(context, "tab-my-quests");

    then("the quest is listed in My Quests again");
    waitForListed(context, TITLE, true);
  }

  /** Waits until the current MainScreen's quest list does (or does not) show an entry. */
  private static void waitForListed(ClientGameTestContext context, String title, boolean listed) {
    context.waitFor(
        client -> {
          if (!(client.gui.screen() instanceof MainScreen screen)) return false;
          var root = screen.getRootComponent();
          FlowLayout list = root != null ? root.childById(FlowLayout.class, "quest-list") : null;
          if (list == null) return false;
          boolean found =
              list.children().stream()
                  .anyMatch(
                      child ->
                          child instanceof QuestEntryComponent entry
                              && title.equals(entry.getQuest().getTitle()));
          return found == listed;
        },
        TIMEOUT);
  }
}
