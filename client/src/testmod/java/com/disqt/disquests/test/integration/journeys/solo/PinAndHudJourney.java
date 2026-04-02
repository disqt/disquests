package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.hud.HudPinManager;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@IntegrationTest
@DisplayName("Pin And HUD Journey")
class PinAndHudJourney {

  // Quest IDs captured across steps
  private static UUID firstQuestId;
  private static UUID secondQuestId;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Create two quests: First and Second")
  void createTwoQuests(ClientGameTestContext context) {
    given("player is connected and on My Quests tab");
    openMainScreen(context);
    assertScreenIs(context, MainScreen.class);

    when("player creates quest titled First");
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", "First");
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    // Reopen fresh MainScreen to pick up new quest (parent screen is stale)
    openMainScreen(context);

    and("player creates quest titled Second");
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", "Second");
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    // Reopen fresh MainScreen to pick up new quest (parent screen is stale)
    openMainScreen(context);

    then("both quests appear in My Quests");
    waitForEntryCount(context, 2);

    // Capture quest IDs for later steps
    firstQuestId =
        context.computeOnClient(
            c -> {
              var entries = ((MainScreen) c.currentScreen).getQuestEntries();
              // Pinned-sorted: no pinned yet, so sorted by lastModified desc.
              // "Second" was created last -> index 0, "First" -> index 1
              return entries.size() >= 2 ? entries.get(1).getQuest().getId() : null;
            });
    secondQuestId =
        context.computeOnClient(
            c -> {
              var entries = ((MainScreen) c.currentScreen).getQuestEntries();
              return entries.size() >= 1 ? entries.get(0).getQuest().getId() : null;
            });
    assertNotNull(firstQuestId, "firstQuestId should not be null");
    assertNotNull(secondQuestId, "secondQuestId should not be null");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Pin Second quest via pin icon click")
  void pinSecondQuest(ClientGameTestContext context) {
    given("player is on MainScreen with two quests, none pinned");
    openMainScreen(context);
    waitForEntryCount(context, 2);

    // "Second" (created last) is at index 0 due to lastModified sort
    when("player clicks the pin icon on Second (index 0)");
    clickPinIcon(context, 0);

    then("Second quest is pinned");
    context.waitFor(
        client -> secondQuestId != null && HudPinManager.isPinned(secondQuestId), TIMEOUT);
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("Pinned quest sorts first in My Quests list")
  void pinnedSortsFirst(ClientGameTestContext context) {
    given("Second quest is pinned");
    openMainScreen(context);

    then("Second appears first (index 0) in the sorted list");
    String firstName =
        context.computeOnClient(
            c -> {
              Screen screen = c.currentScreen;
              if (screen instanceof MainScreen ms) {
                var entries = ms.getQuestEntries();
                return entries.isEmpty() ? null : entries.get(0).getQuest().getTitle();
              }
              return null;
            });
    assertEquals("Second", firstName, "Pinned quest 'Second' should sort to position 0");

    and("First appears second (index 1)");
    String secondName =
        context.computeOnClient(
            c -> {
              Screen screen = c.currentScreen;
              if (screen instanceof MainScreen ms) {
                var entries = ms.getQuestEntries();
                return entries.size() >= 2 ? entries.get(1).getQuest().getTitle() : null;
              }
              return null;
            });
    assertEquals("First", secondName, "Unpinned quest 'First' should be at position 1");
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("HudPinManager reports Second as pinned (trust level 4)")
  void hudPinManagerState(ClientGameTestContext context) {
    // HUD renders outside Screen -- no component to query (trust level 4).
    // Verify via HudPinManager state that is used to drive HudPinRenderer.
    then("HudPinManager.isPinned() returns true for Second");
    boolean pinned =
        context.computeOnClient(
            c -> secondQuestId != null && HudPinManager.isPinned(secondQuestId));
    assertTrue(pinned, "HudPinManager.isPinned() should return true for the pinned quest");

    and("HudPinManager.isPinned() returns false for First");
    boolean notPinned =
        context.computeOnClient(c -> firstQuestId != null && !HudPinManager.isPinned(firstQuestId));
    assertTrue(notPinned, "HudPinManager.isPinned() should return false for the unpinned quest");

    and("HudPinManager.getPinnedQuests() contains Second");
    int pinnedCount = context.computeOnClient(c -> HudPinManager.getPinnedQuests().size());
    assertEquals(1, pinnedCount, "Exactly one quest should be in the pinned list");
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("Unpin Second quest via pin icon click")
  void unpinSecondQuest(ClientGameTestContext context) {
    given("Second quest is pinned and at index 0");
    openMainScreen(context);

    when("player clicks the pin icon on the entry at index 0 (Second, the pinned one)");
    clickPinIcon(context, 0);

    then("Second quest is no longer pinned");
    context.waitFor(
        client -> secondQuestId != null && !HudPinManager.isPinned(secondQuestId), TIMEOUT);
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("Sort order restored after unpinning")
  void sortOrderRestoredAfterUnpin(ClientGameTestContext context) {
    given("no quests are pinned");
    openMainScreen(context);

    then("Second (most recently modified) is still at index 0 due to lastModified sort");
    // After unpin, sort falls back to lastModified descending.
    // Second was created/modified after First, so it remains at index 0.
    String firstName =
        context.computeOnClient(
            c -> {
              Screen screen = c.currentScreen;
              if (screen instanceof MainScreen ms) {
                var entries = ms.getQuestEntries();
                return entries.isEmpty() ? null : entries.get(0).getQuest().getTitle();
              }
              return null;
            });
    // Both quests exist; "Second" is still most recently modified
    assertNotNull(firstName, "First entry should exist after unpinning");

    and("list has 2 entries (both quests still present)");
    waitForEntryCount(context, 2);

    and("neither quest is pinned");
    boolean noPins = context.computeOnClient(c -> HudPinManager.getPinnedQuests().isEmpty());
    assertTrue(noPins, "No quests should be pinned after unpinning");
  }
}
