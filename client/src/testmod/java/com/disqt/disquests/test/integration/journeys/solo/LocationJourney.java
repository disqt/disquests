package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.TestContext;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.UIComponent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@IntegrationTest
@DisplayName("Location Journey")
class LocationJourney {
  private static final String TITLE = "Location Test";

  // Player's block position when Set Pos was clicked.
  private static int[] pinBlock;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  /**
   * The fallback step switches the client's {@code worldMap} setting off. AbortOnFailureExtension
   * skips later steps after a failure, but {@code @AfterAll} always runs, so the map is back on for
   * the journeys that follow in this client. The generated setter only changes memory (no save).
   */
  @AfterAll
  static void restoreWorldMap() {
    TestContext.get().runOnClient(c -> DisquestsClient.CONFIG.worldMap(true));
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
    type(context, "title-field", TITLE);
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Pin the quest at the player")
  void pinAtPlayer(ClientGameTestContext context) {
    given("the map is available, so the card has a preview and no coordinate rows");
    assertComponentExists(context, "mini-map");
    assertComponentMissing(context, "vertex-x-0");
    pinBlock =
        context.computeOnClient(
            c ->
                new int[] {
                  c.player.blockPosition().getX(),
                  c.player.blockPosition().getY(),
                  c.player.blockPosition().getZ()
                });

    when("player clicks Set Pos");
    click(context, "btn-set-pos");
    then("the editor's quest is pinned");
    // Set Pos rebuilds the edit screen
    context.waitFor(
        c -> {
          Quest q = editorQuest(c);
          return q != null && q.getCoordinates() != null;
        },
        TIMEOUT);
    waitForComponent(context, "btn-set-pos");
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
    and("the saved coordinates are the player's block position");
    waitForQuestCondition(context, TITLE, true, q -> isAtBlock(q, pinBlock));
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
    waitForMapButton(context, "the_nether");
    then("map button shows the_nether");
    assertButtonText(context, "btn-map", "the_nether");

    when("player clicks Map (the_nether -> the_end)");
    click(context, "btn-map");
    waitForMapButton(context, "the_end");
    then("map button shows the_end");
    assertButtonText(context, "btn-map", "the_end");

    when("player clicks Map (the_end -> overworld)");
    click(context, "btn-map");
    waitForMapButton(context, "overworld");
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
    then("the editor's quest has no location");
    // Clear rebuilds the edit screen
    context.waitFor(
        c -> {
          Quest q = editorQuest(c);
          return q != null && !q.hasLocation();
        },
        TIMEOUT);
    waitForComponent(context, "btn-set-pos");

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
  @DisplayName("With the world map off, type exact coordinates")
  void typeCoordinatesWithMapOff(ClientGameTestContext context) {
    given("the player switches the world map setting off");
    // Restored by restoreWorldMap (@AfterAll), which runs even if this step fails.
    context.runOnClient(c -> DisquestsClient.CONFIG.worldMap(false));

    when("player clicks Edit and Set Pos");
    click(context, "btn-edit");
    waitForComponent(context, "btn-set-pos");
    click(context, "btn-set-pos");
    // Set Pos rebuilds the edit screen; the rebuilt one has a vertex row
    waitForComponent(context, "vertex-x-0");
    then("the card has coordinate rows and no preview");
    assertComponentMissing(context, "mini-map");
    assertComponentExists(context, "vertex-x-0");

    when("player types the coordinates");
    type(context, "vertex-x-0", "100");
    type(context, "vertex-y-0", "64");
    type(context, "vertex-z-0", "200");
    and("player clicks Save");
    click(context, "btn-save");
    then("view mode shows the quest");
    waitForComponent(context, "btn-edit");
    and("coords label shows the typed coordinates");
    assertLabelText(context, "coords-label", "X:100 Y:64 Z:200");
    and("the saved coordinates are the typed ones");
    waitForQuestCondition(context, TITLE, true, q -> isAtBlock(q, new int[] {100, 64, 200}));
  }

  @Test
  @Order(8)
  @PlayerA
  @DisplayName("Return to MainScreen")
  void returnToMain(ClientGameTestContext context) {
    when("player closes the quest screen");
    click(context, "btn-back");
    then("MainScreen is shown");
    waitForScreen(context, MainScreen.class);
  }

  /** True when the quest's pin is in the block {@code xyz}. */
  private static boolean isAtBlock(Quest quest, int[] xyz) {
    var c = quest.getCoordinates();
    return c != null
        && Math.floor(c.x()) == xyz[0]
        && Math.floor(c.y()) == xyz[1]
        && Math.floor(c.z()) == xyz[2];
  }

  /** The open editor's draft quest, or null unless a QuestScreen is in edit mode. Client thread. */
  private static Quest editorQuest(Minecraft client) {
    return client.gui.screen() instanceof QuestScreen qs && qs.isEditing() ? qs.getQuest() : null;
  }

  /** Waits until the current screen's {@code btn-map} text contains {@code key}. */
  private static void waitForMapButton(ClientGameTestContext context, String key) {
    context.waitFor(
        client -> {
          ButtonComponent button = currentComponent(client, ButtonComponent.class, "btn-map");
          return button != null && button.getMessage().getString().contains(key);
        },
        TIMEOUT);
  }

  /** Looks up a component on the client thread, inside a {@code waitFor} predicate. */
  private static <T extends UIComponent> T currentComponent(
      Minecraft client, Class<T> type, String id) {
    if (client.gui.screen() instanceof DisquestsBaseScreen screen
        && screen.getRootComponent() != null) {
      return screen.getRootComponent().childById(type, id);
    }
    return null;
  }
}
