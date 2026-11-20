package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The Prerequisites section of a quest's page, as its owner reads it: a line per prerequisite that
 * opens it (mouse back returns to the page), the auto-join line, a tick once the prerequisite is
 * completed, and for a deleted prerequisite the "a deleted quest" line with the owner's banner.
 */
@IntegrationTest
@DisplayName("Prerequisite Section Journey")
class PrerequisiteSectionJourney {

  private static final String STEP = "Section Step";
  private static final String DOOMED = "Section Doomed";
  private static final String FOLLOW = "Section Follow";

  private static final String AUTO_JOIN = "When this unlocks, add the players from those quests";
  private static final String HIDDEN = "… and something you don't have access to";
  private static final String DELETED = "a deleted quest";
  private static final String DELETED_BANNER =
      "This quest waits on a deleted quest and stays locked. Open Edit to remove that link.";

  private static UUID step;
  private static UUID doomed;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("A quest waiting on two others lists them in its Prerequisites section")
  void sectionListsPrerequisites(ClientGameTestContext context) {
    given("a fresh server, the My Quests tab and three quests");
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
        });
    step = createQuest(context, STEP, null);
    doomed = createQuest(context, DOOMED, null);
    // Auto-join can be ticked only on an Open quest
    createQuest(context, FOLLOW, "vis-open");

    then("a quest without prerequisites has no Prerequisites section");
    openQuest(context, "tab-my-quests", FOLLOW);
    waitForComponent(context, "btn-edit");
    assertComponentMissing(context, "prerequisites-section");

    when("player picks both as prerequisites in the editor, ticking auto-join, and saves");
    enterEditor(context);
    pickSuggestion(context, "prerequisite-field", STEP);
    waitForComponent(context, "prereq-chip-" + step);
    tickCheckbox(context, "auto-join");
    pickSuggestion(context, "prerequisite-field", DOOMED);
    waitForComponent(context, "prereq-chip-" + doomed);
    saveEditor(context);
    waitForQuestCondition(
        context,
        FOLLOW,
        true,
        q ->
            q.getPrerequisites().contains(step)
                && q.getPrerequisites().contains(doomed)
                && q.isAutoJoin());

    then("the page shows the section: its header, a line per prerequisite, the auto-join line");
    waitForText(context, "prerequisite-" + step, STEP::equals);
    waitForText(context, "prerequisite-" + doomed, DOOMED::equals);
    waitForTextUnder(
        context,
        "prerequisites-section",
        t ->
            t.startsWith("Prerequisites\n")
                && t.contains(AUTO_JOIN)
                && !t.contains(HIDDEN)
                && !t.contains(DELETED));

    and("the owner's page is not the locked one and carries no banner");
    assertComponentMissing(context, "title-lock");
    assertComponentMissing(context, "waiting-text");
    assertComponentMissing(context, "deleted-prerequisite-banner");
    assertComponentMissing(context, "privatized-banner");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("A prerequisite line opens that quest; mouse back returns to the page")
  void lineOpensPrerequisite(ClientGameTestContext context) {
    given("the page of the quest that waits");
    openQuest(context, "tab-my-quests", FOLLOW);
    waitForComponent(context, "prerequisite-" + step);

    when("player clicks the first prerequisite's title");
    clickLineTitle(context, "prerequisite-" + step);

    then("that quest's page opens");
    waitForText(context, "title-label", STEP::equals);
    assertComponentMissing(context, "prerequisites-section");

    when("player presses the mouse's back button");
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_4);

    then("the page of the quest that waits is back, with its section");
    waitForText(context, "title-label", FOLLOW::equals);
    waitForComponent(context, "prerequisite-" + step);
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("A completed prerequisite's line carries a tick")
  void completedPrerequisiteIsTicked(ClientGameTestContext context) {
    when("player completes the first prerequisite");
    openQuest(context, "tab-my-quests", STEP);
    click(context, "btn-complete");
    confirmYes(context);
    waitForQuestCondition(context, STEP, true, Quest::isCompleted);
    waitForScreen(context, MainScreen.class);

    then("its line on the waiting quest's page starts with a tick; the other line has none");
    openQuest(context, "tab-my-quests", FOLLOW);
    waitForText(context, "prerequisite-" + step, ("✔ " + STEP)::equals);
    waitForText(context, "prerequisite-" + doomed, DOOMED::equals);

    and("the ticked line still opens the archived quest");
    clickLineTitle(context, "prerequisite-" + step);
    waitForText(context, "title-label", STEP::equals);
    waitForComponent(context, "btn-reopen");
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("A deleted prerequisite shows as 'a deleted quest', with the owner's banner")
  void deletedPrerequisiteShowsBannerAndLine(ClientGameTestContext context) {
    when("player deletes the unfinished prerequisite");
    openQuest(context, "tab-my-quests", DOOMED);
    click(context, "btn-delete");
    confirmYes(context);
    waitForNotCached(context, DOOMED);
    waitForQuestCondition(context, FOLLOW, true, Quest::hasDeletedPrerequisite);

    and("opens the quest that waited on it");
    openQuest(context, "tab-my-quests", FOLLOW);

    then("the banner under the header tells the owner what to do");
    waitForTextUnder(context, "deleted-prerequisite-banner", t -> t.equals(DELETED_BANNER + "\n"));
    assertComponentMissing(context, "privatized-banner");
    assertComponentMissing(context, "btn-restore-visibility");

    and("the section keeps the completed one's line and names the other 'a deleted quest'");
    waitForText(context, "prerequisite-" + step, ("✔ " + STEP)::equals);
    assertComponentMissing(context, "prerequisite-" + doomed);
    waitForTextUnder(
        context,
        "prerequisites-section",
        t -> t.contains(DELETED) && t.contains(AUTO_JOIN) && !t.contains(HIDDEN));
  }

  /** Clicks a prerequisite line on its title (the line is as wide as the section). */
  private static void clickLineTitle(ClientGameTestContext context, String lineId) {
    UIComponent line = findComponent(context, UIComponent.class, lineId);
    // Past the tick of a completed line, inside the title
    clickAt(
        context,
        context.computeOnClient(c -> new double[] {line.x() + 20, line.y() + line.height() / 2.0}));
  }
}
