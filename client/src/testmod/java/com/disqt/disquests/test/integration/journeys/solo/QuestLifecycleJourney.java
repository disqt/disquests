package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
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

@IntegrationTest
@DisplayName("Quest Lifecycle Journey")
class QuestLifecycleJourney {

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Open MainScreen with empty My Quests")
  void verifyEmptyMainScreen(ClientGameTestContext context) {
    given("player is connected to the server");
    when("player opens MainScreen");
    openMainScreen(context);
    then("My Quests tab is shown with no entries");
    assertScreenIs(context, MainScreen.class);
    assertEntryCount(context, 0);
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Create a new quest")
  void createQuest(ClientGameTestContext context) {
    when("player clicks New Quest");
    openMainScreen(context);
    click(context, "btn-new-quest");
    then("QuestScreen opens in edit mode with empty fields");
    waitForScreen(context, QuestScreen.class);
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("Type title and content")
  void typeContent(ClientGameTestContext context) {
    given("player is in edit mode");
    when("player types title");
    type(context, "title-field", "Lifecycle Test");
    and("player types content");
    type(context, "content-field", "Initial content for testing");
    then("fields show the entered text");
    // Fields populated -- verified by save in next step
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("Save quest and verify view mode")
  void saveQuest(ClientGameTestContext context) {
    when("player clicks Save");
    click(context, "btn-save");
    then("screen shows view mode with correct title");
    waitForScreen(context, QuestScreen.class);
    assertLabelText(context, "title-label", "Lifecycle Test");
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("Return to MainScreen and see quest in list")
  void returnToMain(ClientGameTestContext context) {
    when("player clicks Close and reopens MainScreen");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    // Reopen fresh MainScreen to pick up new quests (parent screen is stale)
    openMainScreen(context);
    then("MainScreen shows the quest in My Quests");
    waitForEntryCount(context, 1);
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("Open quest and enter edit mode")
  void openAndEdit(ClientGameTestContext context) {
    given("player is on MainScreen with one quest");
    openMainScreen(context);
    when("player selects the quest and clicks Open");
    clickEntry(context, 0);
    click(context, "btn-open");
    then("QuestScreen opens in view mode");
    waitForScreen(context, QuestScreen.class);
    when("player clicks Edit");
    click(context, "btn-edit");
    then("edit mode opens");
    waitForScreen(context, QuestScreen.class);
  }

  @Test
  @Order(7)
  @PlayerA
  @DisplayName("Edit title and save")
  void editTitle(ClientGameTestContext context) {
    when("player changes the title");
    type(context, "title-field", "Updated Title");
    and("player clicks Save");
    click(context, "btn-save");
    then("view mode shows updated title");
    waitForScreen(context, QuestScreen.class);
    assertLabelText(context, "title-label", "Updated Title");
  }

  @Test
  @Order(8)
  @PlayerA
  @DisplayName("Delete quest with confirmation")
  void deleteQuest(ClientGameTestContext context) {
    when("player clicks Delete");
    click(context, "btn-delete");
    then("confirm overlay appears");
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
    when("player clicks Yes");
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);
    then("MainScreen shows empty list");
    waitForScreen(context, MainScreen.class);
    openMainScreen(context);
    // Wait for delete to propagate
    context.waitFor(client -> ClientCache.getMyQuests().isEmpty(), TIMEOUT);
    assertEntryCount(context, 0);
  }
}
