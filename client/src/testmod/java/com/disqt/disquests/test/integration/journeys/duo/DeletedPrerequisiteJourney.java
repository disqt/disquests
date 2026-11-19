package com.disqt.disquests.test.integration.journeys.duo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

/**
 * A deleted prerequisite: A deletes an unfinished prerequisite of an Open follow-up, which stays
 * locked for B. A, its owner, sees the warning mark on the list row and the "a deleted quest" chip
 * in the editor; removing the chip and saving unlocks the follow-up for B.
 */
@IntegrationTest
@DisplayName("Deleted Prerequisite Journey")
class DeletedPrerequisiteJourney {

  private static final String J = "deleted-";
  private static final String STEP = "Gate Step";
  private static final String FOLLOW = "Gate Follow";

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerForDuo("deleted");
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("A makes an Open follow-up wait on an Open step")
  void a_sets_up(ClientGameTestContext context) {
    UUID step = createQuest(context, STEP, "vis-open");
    createQuest(context, FOLLOW, "vis-open");
    openQuest(context, "tab-my-quests", FOLLOW);
    enterEditor(context);
    pickSuggestion(context, "prerequisite-field", STEP);
    waitForComponent(context, "prereq-chip-" + step);
    saveEditor(context);
    waitForQuestCondition(context, FOLLOW, true, q -> q.getPrerequisites().contains(step));
    signal("ready");
  }

  @Test
  @Order(1)
  @PlayerB
  @DisplayName("B sees the follow-up locked")
  void b_sees_locked(ClientGameTestContext context) {
    await("ready", context);
    waitForCachedQuest(context, FOLLOW, Quest::isLocked);
    // The Open step is on B's board: its removal later shows the deletion has reached B
    waitForCachedQuest(context, STEP, q -> !q.isCompleted());
    signal("b-saw-locked");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("A deletes the unfinished step; the follow-up shows the warning mark")
  void a_deletes_step(ClientGameTestContext context) {
    await("b-saw-locked", context);

    when("A deletes the step");
    openQuest(context, "tab-my-quests", STEP);
    click(context, "btn-delete");
    confirmYes(context);
    waitForNotCached(context, STEP);

    then("the follow-up remembers a deleted prerequisite");
    waitForQuestCondition(context, FOLLOW, true, Quest::hasDeletedPrerequisite);

    and("its list row carries the warning mark");
    openMainScreen(context);
    click(context, "tab-my-quests");
    waitForListed(context, FOLLOW, true);
    context.waitFor(
        client -> {
          QuestEntryComponent entry = listedEntry(client, FOLLOW);
          return entry != null && entry.getMarks().needsAttention();
        },
        TIMEOUT);
    signal("step-deleted");
  }

  @Test
  @Order(2)
  @PlayerB
  @DisplayName("The follow-up stays locked for B")
  void b_still_locked(ClientGameTestContext context) {
    await("step-deleted", context);
    // The deletion has reached B once the step is gone from B's cache: then the follow-up must
    // still be locked
    waitForNotCached(context, STEP);
    assertTrue(
        (Boolean) context.computeOnClient(c -> cachedQuest(FOLLOW).isLocked()),
        "the follow-up should stay locked");
    signal("b-still-locked");
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("A removes the 'a deleted quest' chip and saves")
  void a_removes_chip(ClientGameTestContext context) {
    await("b-still-locked", context);

    when("A opens the follow-up's editor");
    openQuest(context, "tab-my-quests", FOLLOW);
    enterEditor(context);

    then("the prerequisites show the deleted quest's chip");
    waitForComponent(context, "prereq-chip-deleted");
    var chip =
        findComponent(
            context,
            com.disqt.disquests.client.gui.component.TagChipComponent.class,
            "prereq-chip-deleted");
    assertEquals("a deleted quest", context.computeOnClient(c -> chip.getTag()));

    when("A removes it and saves");
    clickChipRemove(context, "prereq-chip-deleted");
    context.waitFor(
        client ->
            onScreen(client, io.wispforest.owo.ui.core.UIComponent.class, "prereq-chip-deleted")
                == null,
        TIMEOUT);
    saveEditor(context);

    then("the follow-up no longer waits on anything");
    waitForQuestCondition(
        context, FOLLOW, true, q -> !q.hasDeletedPrerequisite() && q.getPrerequisites().isEmpty());
    signal("chip-removed");
  }

  @Test
  @Order(3)
  @PlayerB
  @DisplayName("The follow-up unlocks for B")
  void b_sees_unlock(ClientGameTestContext context) {
    await("chip-removed", context);
    waitForCachedQuest(context, FOLLOW, q -> !q.isLocked());
    openMainScreen(context);
    click(context, "tab-quest-board");
    waitForListed(context, FOLLOW, true);
    QuestEntryComponent entry = context.computeOnClient(c -> listedEntry(c, FOLLOW));
    assertFalse(
        (Boolean) context.computeOnClient(c -> entry.getMarks().locked()), "no lock any more");
    signal("b-done");
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("A waits for B to finish before the next journey")
  void a_waits_for_b(ClientGameTestContext context) {
    await("b-done", context);
  }

  // --- helpers ---

  private static void signal(String phase) {
    PhaseSync.signal(J + phase);
  }

  private static void await(String phase, ClientGameTestContext context) {
    PhaseSync.waitFor(J + phase, context, seconds(120));
  }
}
