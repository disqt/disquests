package com.disqt.disquests.test.integration.journeys.duo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

/**
 * A second owner: A makes B an Owner of A's quest; B can complete it (and reopens it). A then gives
 * up Owner on the people screen: Save asks first, and afterwards A no longer has Complete.
 */
@IntegrationTest
@DisplayName("Second Owner Journey")
class SecondOwnerJourney {

  private static final String J = "coowner-";
  private static final String DUTY = "Shared Duty";
  private static final String B_NAME = "IntTestPlayerB";

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerForDuo("coowner");
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("A makes B an Owner of A's quest")
  void a_adds_second_owner(ClientGameTestContext context) {
    createQuest(context, DUTY, null);
    openQuest(context, "tab-my-quests", DUTY);
    openPeople(context);
    addPerson(context, B_NAME, "add-role-owner");
    savePeople(context);
    waitForQuestCondition(
        context,
        DUTY,
        true,
        q -> q.getContributors().stream().anyMatch(c -> c.getRole() == Role.OWNER));
    signal("b-is-owner");
  }

  @Test
  @Order(1)
  @PlayerB
  @DisplayName("B, an Owner, completes the quest, then reopens it")
  void b_completes(ClientGameTestContext context) {
    await("b-is-owner", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());
    waitForCachedQuest(context, DUTY, q -> q.roleOf(me) == Role.OWNER);

    when("B opens the quest");
    openQuest(context, "tab-my-quests", DUTY);

    then("B has Complete and the people screen");
    waitForComponent(context, "btn-complete");
    assertComponentExists(context, "btn-manage-people");

    when("B completes it");
    click(context, "btn-complete");
    confirmYes(context);
    then("it is completed");
    waitForCachedQuest(context, DUTY, Quest::isCompleted);

    when("B reopens it from the Archived tab");
    openQuest(context, "tab-archived", DUTY);
    click(context, "btn-reopen");
    confirmYes(context);
    waitForCachedQuest(context, DUTY, q -> !q.isCompleted());
    waitForComponent(context, "btn-complete");
    signal("b-completed-and-reopened");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("A gives up Owner: Save asks first, then A has no Complete")
  void a_gives_up_owner(ClientGameTestContext context) {
    await("b-completed-and-reopened", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());
    waitForQuestCondition(context, DUTY, true, q -> !q.isCompleted());

    when("A opens the quest's people and chooses Write for A's own row");
    openQuest(context, "tab-my-quests", DUTY);
    waitForComponent(context, "btn-complete");
    openPeople(context);
    choose(context, "role-write-" + me);

    and("presses Save");
    clickWhenActive(context, "btn-save-people");

    then("A is asked to confirm giving up Owner");
    waitForTextUnder(
        context,
        DisquestsBaseScreen.CONFIRM_OVERLAY_ID,
        t -> t.contains("You are giving up Owner on this quest."));

    when("A confirms");
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);
    waitForScreen(context, QuestScreen.class);

    then("A holds Write, and the page has Edit but no Complete");
    waitForQuestCondition(context, DUTY, true, q -> q.roleOf(me) == Role.WRITE);
    context.waitFor(
        client ->
            onScreen(client, io.wispforest.owo.ui.core.UIComponent.class, "btn-complete") == null
                && onScreen(client, io.wispforest.owo.ui.core.UIComponent.class, "btn-edit")
                    != null,
        TIMEOUT);
    assertComponentMissing(context, "btn-manage-people");
    signal("a-gave-up");
  }

  @Test
  @Order(2)
  @PlayerB
  @DisplayName("B is the only owner left")
  void b_only_owner(ClientGameTestContext context) {
    await("a-gave-up", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());
    waitForCachedQuest(
        context,
        DUTY,
        q ->
            q.roleOf(me) == Role.OWNER
                && q.getContributors().stream()
                    .filter(c -> !c.getUuid().equals(me))
                    .allMatch(c -> c.getRole() == Role.WRITE));
    signal("b-done");
  }

  @Test
  @Order(3)
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
