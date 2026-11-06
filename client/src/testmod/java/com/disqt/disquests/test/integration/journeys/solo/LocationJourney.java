package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import io.wispforest.owo.ui.core.UIComponent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@IntegrationTest
@DisplayName("Location Journey")
class LocationJourney {

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Create a quest and enter edit mode")
  void createQuest(ClientGameTestContext context) {
    given("player is on MainScreen");
    openMainScreen(context);
    when("player creates a new quest");
    click(context, "btn-new-quest");
    then("QuestScreen opens in edit mode");
    waitForScreen(context, QuestScreen.class);
    when("player types a title");
    type(context, "title-field", "Location Test");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Pin the quest at the player, then type exact coordinates")
  void pinAndTypeCoordinates(ClientGameTestContext context) {
    when("player clicks Set Pos");
    click(context, "btn-set-pos");
    then("the location card shows one vertex row with X, Y and Z");
    // Set Pos rebuilds the edit screen
    waitForComponent(context, "vertex-x-0");
    when("player types the coordinates");
    type(context, "vertex-x-0", "100");
    type(context, "vertex-y-0", "64");
    type(context, "vertex-z-0", "200");
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("Save and verify the pin in view mode")
  void saveAndVerifyPin(ClientGameTestContext context) {
    when("player clicks Save");
    click(context, "btn-save");
    then("view mode shows the quest");
    waitForComponent(context, "btn-edit");
    and("coords label shows the typed coordinates");
    assertLabelText(context, "coords-label", "X:100 Y:64 Z:200");
    and("the location card shows a mini map");
    assertComponentExists(context, "mini-map");
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("Cycle the Map button through the three dimensions")
  void cycleMapButton(ClientGameTestContext context) {
    when("player clicks Edit");
    click(context, "btn-edit");
    waitForComponent(context, "btn-map");
    then("map button shows the player's dimension (overworld)");
    assertButtonText(context, "btn-map", "overworld");

    when("player clicks Map (overworld -> the_nether)");
    click(context, "btn-map");
    waitForScreen(context, QuestScreen.class);
    then("map button shows the_nether");
    assertButtonText(context, "btn-map", "the_nether");

    when("player clicks Map (the_nether -> the_end)");
    click(context, "btn-map");
    waitForScreen(context, QuestScreen.class);
    then("map button shows the_end");
    assertButtonText(context, "btn-map", "the_end");

    when("player clicks Map (the_end -> overworld)");
    click(context, "btn-map");
    waitForScreen(context, QuestScreen.class);
    then("map button shows overworld again");
    assertButtonText(context, "btn-map", "overworld");
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("Save and verify the map label")
  void saveAndVerifyMap(ClientGameTestContext context) {
    when("player clicks Save");
    click(context, "btn-save");
    then("view mode shows the quest");
    waitForComponent(context, "btn-edit");
    and("map label shows overworld");
    assertLabelText(context, "map-label", "Map: overworld");
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("Clear the location and verify the card is hidden")
  void clearLocation(ClientGameTestContext context) {
    when("player clicks Edit");
    click(context, "btn-edit");
    waitForComponent(context, "btn-clear-location");
    and("player clicks Clear location");
    click(context, "btn-clear-location");
    // Clear rebuilds the edit screen
    waitForScreen(context, QuestScreen.class);
    then("the vertex row is gone");
    assertComponentMissing(context, "vertex-x-0");

    when("player clicks Save");
    click(context, "btn-save");
    then("view mode shows the quest");
    waitForComponent(context, "btn-edit");
    and("the location card is zero-sized");
    assertComponent(
        context,
        "location-card",
        UIComponent.class,
        c -> c.width() == 0 && c.height() == 0,
        "location-card should be hidden when the quest has no location");
  }

  @Test
  @Order(7)
  @PlayerA
  @DisplayName("Return to MainScreen")
  void returnToMain(ClientGameTestContext context) {
    when("player closes the quest screen");
    click(context, "btn-back");
    then("MainScreen is shown");
    waitForScreen(context, MainScreen.class);
  }
}
