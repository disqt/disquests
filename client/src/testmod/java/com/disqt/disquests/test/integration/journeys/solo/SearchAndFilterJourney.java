package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Tests My Quests tab: create multiple quests, search, verify counts. Quest Board filtering
 * requires a second player and is tested in TwoPlayerJourneys.
 */
@IntegrationTest
@DisplayName("Search And Filter Journey")
class SearchAndFilterJourney {

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  private void createQuest(ClientGameTestContext context, String title) {
    openMainScreen(context);
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", title);
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    openMainScreen(context);
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Create three quests")
  void createQuests(ClientGameTestContext context) {
    given("player is connected to the server");
    when("player creates quest Alpha");
    createQuest(context, "Alpha");
    then("Alpha appears in My Quests");
    waitForEntryCount(context, 1);

    when("player creates quest Beta");
    createQuest(context, "Beta");
    then("Beta appears alongside Alpha");
    waitForEntryCount(context, 2);

    when("player creates quest Gamma");
    createQuest(context, "Gamma");
    then("all three quests are in My Quests");
    waitForEntryCount(context, 3);
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Search filters quests by title")
  void searchByTitle(ClientGameTestContext context) {
    given("player is on MainScreen with 3 quests");
    openMainScreen(context);
    waitForEntryCount(context, 3);

    when("player types 'Alpha' in the search box");
    type(context, "search-box", "Alpha");
    then("only Alpha is shown");
    waitForEntryCount(context, 1);

    when("player changes search to 'Beta'");
    type(context, "search-box", "Beta");
    then("only Beta is shown");
    waitForEntryCount(context, 1);

    when("player clears the search");
    type(context, "search-box", "");
    then("all three quests return");
    waitForEntryCount(context, 3);
  }
}
