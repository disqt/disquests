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
import org.lwjgl.glfw.GLFW;

@IntegrationTest
@DisplayName("Navigation Journey")
class NavigationJourney {

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Create a quest for navigation testing")
  void createQuest(ClientGameTestContext context) {
    given("player is connected to the server");
    when("player opens MainScreen and creates a quest");
    openMainScreen(context);
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", "Nav Test");
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);
    assertLabelText(context, "title-label", "Nav Test");
    then("quest is saved in view mode");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Return to MainScreen and open quest via Open button")
  void openQuestFromMainScreen(ClientGameTestContext context) {
    given("player is on QuestScreen");
    when("player closes QuestScreen and reopens quest from MainScreen");
    click(context, "btn-close");
    waitForScreen(context, MainScreen.class);
    openMainScreen(context);
    waitForEntryCount(context, 1);
    clickEntry(context, 0);
    click(context, "btn-open");
    then("QuestScreen opens");
    waitForScreen(context, QuestScreen.class);
    assertLabelText(context, "title-label", "Nav Test");
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("Mouse back button returns to MainScreen")
  void mouseBackToMainScreen(ClientGameTestContext context) {
    given("player is on QuestScreen after navigating from MainScreen");
    assertScreenIs(context, QuestScreen.class);
    when("player presses mouse back button (GLFW button 4)");
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_4);
    context.waitTicks(5);
    then("MainScreen is shown");
    waitForScreen(context, MainScreen.class);
    assertScreenIs(context, MainScreen.class);
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("Mouse forward button returns to QuestScreen")
  void mouseForwardToQuestScreen(ClientGameTestContext context) {
    given("player is on MainScreen after pressing mouse back");
    assertScreenIs(context, MainScreen.class);
    when("player presses mouse forward button (GLFW button 5)");
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_5);
    context.waitTicks(5);
    then("QuestScreen is shown");
    waitForScreen(context, QuestScreen.class);
    assertScreenIs(context, QuestScreen.class);
  }
}
