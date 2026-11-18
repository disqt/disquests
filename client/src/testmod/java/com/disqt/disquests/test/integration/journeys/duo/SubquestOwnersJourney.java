package com.disqt.disquests.test.integration.journeys.duo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.SuggestionDropdown;
import com.disqt.disquests.client.gui.component.TreeRowGeometry;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

/**
 * A quest tree whose subquests belong to two players: B puts B's own quest under A's quest (Write
 * on it is enough), A sees B's quest greyed in an "add a subquest..." list ("you need Owner on it,
 * you have Read"), completes the parent with its checkbox (A's subquest completes, B's stays
 * active), reopens it, then deletes it with the checkbox ticked (A's subquest goes, B's is kept and
 * set to Private, B restores it from the banner) and another tree with it unticked (A's subquest is
 * kept as it was).
 */
@IntegrationTest
@DisplayName("Subquest Owners Journey")
class SubquestOwnersJourney {

  private static final String J = "owners-";
  private static final String BASE = "Base Camp";
  private static final String MINE = "Base Child";
  private static final String THEIRS = "B Side Quest";
  private static final String CAMP = "Second Camp";
  private static final String CAMP_CHILD = "Second Camp Child";
  private static final String B_NAME = "IntTestPlayerB";

  private static UUID base;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerForDuo("owners");
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("A builds a Private quest with A's own subquest and gives B Write on it")
  void a_builds_base(ClientGameTestContext context) {
    when("A creates the quest and its subquest and links them in the editor");
    base = createQuest(context, BASE, null);
    createQuest(context, MINE, null);
    openQuest(context, "tab-my-quests", BASE);
    enterEditor(context);
    pickSuggestion(context, "add-subquest-field", MINE);
    saveEditor(context);
    waitForQuestCondition(context, MINE, true, q -> base.equals(q.getParentId()));

    and("adds B with Write");
    openPeople(context);
    addPerson(context, B_NAME, "add-role-write");
    savePeople(context);
    waitForQuestCondition(
        context,
        BASE,
        true,
        q -> q.getContributors().stream().anyMatch(c -> c.getRole() == Role.WRITE));
    signal("base-ready");
  }

  @Test
  @Order(1)
  @PlayerB
  @DisplayName("B puts B's own Open quest under A's quest")
  void b_attaches_own_quest(ClientGameTestContext context) {
    await("base-ready", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());
    waitForCachedQuest(context, BASE, q -> q.roleOf(me) == Role.WRITE);

    when("B creates an Open quest");
    createQuest(context, THEIRS, "vis-open");

    and("adds it under A's quest from that quest's editor (Owner on it, Write on the target)");
    openQuest(context, "tab-my-quests", BASE);
    enterEditor(context);
    pickSuggestion(context, "add-subquest-field", THEIRS);
    saveEditor(context);

    then("it sits under A's quest");
    UUID baseId = context.computeOnClient(c -> cachedQuest(BASE).getId());
    waitForCachedQuest(context, THEIRS, q -> baseId.equals(q.getParentId()));
    signal("b-attached");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("A joins B's subquest and finds it greyed in an 'add a subquest...' list")
  void a_sees_greyed_suggestion(ClientGameTestContext context) {
    await("b-attached", context);
    waitForCachedQuest(context, THEIRS, q -> base.equals(q.getParentId()));
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());
    UUID theirs = context.computeOnClient(c -> cachedQuest(THEIRS).getId());
    UUID mine = context.computeOnClient(c -> cachedQuest(MINE).getId());

    when("A opens B's quest from the parent's page and joins it");
    openQuest(context, "tab-my-quests", BASE);
    clickAt(context, treeRowPoint(context, theirs, TreeRowGeometry.Part.BODY));
    waitForText(context, "btn-interact", "Join"::equals);
    click(context, "btn-interact");
    waitForCachedQuest(context, THEIRS, q -> q.roleOf(me) == Role.READ);

    and("types its title in the 'add a subquest...' field of A's own subquest");
    openQuest(context, "tab-my-quests", BASE);
    clickAt(context, treeRowPoint(context, mine, TreeRowGeometry.Part.BODY));
    waitForComponent(context, "btn-edit");
    enterEditor(context);
    SuggestionDropdown.Row row =
        typeAndFindSuggestion(context, "add-subquest-field", THEIRS, THEIRS);

    then("it is listed greyed, with the reason and A's role");
    assertEquals(
        THEIRS + " (you need Owner on it, you have Read)",
        context.computeOnClient(c -> row.text()));
    assertFalse((Boolean) context.computeOnClient(c -> row.enabled()), "the row should be greyed");

    and("A leaves the editor");
    click(context, "btn-cancel");
    if (context.computeOnClient(
        c ->
            onScreen(
                    c,
                    io.wispforest.owo.ui.core.UIComponent.class,
                    DisquestsBaseScreen.CONFIRM_OVERLAY_ID)
                != null)) {
      click(context, DisquestsBaseScreen.CONFIRM_YES_ID);
    }
    waitForComponent(context, "btn-edit");
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("A completes the parent and A's subquest; B's stays active")
  void a_completes_with_checkbox(ClientGameTestContext context) {
    when("A presses Complete on the parent");
    openQuest(context, "tab-my-quests", BASE);
    click(context, "btn-complete");

    then("the dialog offers A's one subquest and says B's stays active");
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_CHECKBOX_ID);
    waitForText(
        context,
        DisquestsBaseScreen.CONFIRM_CHECKBOX_ID,
        "Also complete my 1 unfinished subquest"::equals);
    waitForText(
        context,
        DisquestsBaseScreen.CONFIRM_EXTRA_ID,
        "1 other belongs to another player and stays active"::equals);

    when("A ticks it and confirms");
    tickConfirmCheckbox(context);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("the parent and A's subquest are completed, B's is not");
    waitForQuestCondition(context, BASE, true, Quest::isCompleted);
    waitForQuestCondition(context, MINE, true, Quest::isCompleted);
    waitForCachedQuest(context, THEIRS, q -> !q.isCompleted());
    signal("completed");
  }

  @Test
  @Order(3)
  @PlayerB
  @DisplayName("B's subquest stays active and is listed as a top-level quest")
  void b_sees_own_quest_on_top(ClientGameTestContext context) {
    await("completed", context);

    then("the parent is archived; B's quest is active and keeps its link");
    waitForCachedQuest(context, BASE, Quest::isCompleted);
    UUID baseId = context.computeOnClient(c -> cachedQuest(BASE).getId());
    waitForCachedQuest(context, THEIRS, q -> !q.isCompleted() && baseId.equals(q.getParentId()));

    and("My Quests lists it at the top level, without the archived parent");
    openMainScreen(context);
    click(context, "tab-my-quests");
    waitForListed(context, THEIRS, true);
    assertListed(context, BASE, false);
    signal("b-checked-completed");
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("A reopens the parent, then deletes it with the checkbox ticked")
  void a_reopens_and_deletes_ticked(ClientGameTestContext context) {
    await("b-checked-completed", context);

    when("A reopens the parent from the Archived tab");
    openQuest(context, "tab-archived", BASE);
    click(context, "btn-reopen");
    confirmYes(context);
    waitForQuestCondition(context, BASE, true, q -> !q.isCompleted());
    waitForComponent(context, "btn-delete");

    and("deletes it, ticking 'Also delete my 1 subquest'");
    click(context, "btn-delete");
    waitForText(
        context, DisquestsBaseScreen.CONFIRM_CHECKBOX_ID, "Also delete my 1 subquest"::equals);
    waitForText(
        context,
        DisquestsBaseScreen.CONFIRM_EXTRA_ID,
        "1 other belongs to another player and is kept"::equals);
    tickConfirmCheckbox(context);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("the parent and A's subquest are gone; B's quest is kept, top level and Private");
    waitForNotCached(context, BASE);
    waitForNotCached(context, MINE);
    waitForCachedQuest(
        context, THEIRS, q -> q.getParentId() == null && q.getVisibility() == Visibility.PRIVATE);
    signal("deleted-ticked");
  }

  @Test
  @Order(4)
  @PlayerB
  @DisplayName("B finds the quest set to Private with a banner and restores it")
  void b_restores_visibility(ClientGameTestContext context) {
    await("deleted-ticked", context);

    then("B's quest is top level, Private, and remembers why");
    waitForCachedQuest(
        context,
        THEIRS,
        q ->
            q.getParentId() == null
                && q.getVisibility() == Visibility.PRIVATE
                && q.getPrivatized() != null);

    when("B opens it");
    openQuest(context, "tab-my-quests", THEIRS);

    then("the banner says what happened");
    waitForTextUnder(
        context,
        "privatized-banner",
        t ->
            t.contains("\"" + BASE + "\", was deleted by IntTestPlayerA")
                && t.toLowerCase(java.util.Locale.ROOT).contains("it was open before"));

    when("B presses the button to set it back to Open");
    click(context, "btn-restore-visibility");

    then("the quest is Open again and the banner is gone");
    waitForCachedQuest(
        context, THEIRS, q -> q.getVisibility() == Visibility.OPEN && q.getPrivatized() == null);
    context.waitFor(
        client ->
            onScreen(client, io.wispforest.owo.ui.core.UIComponent.class, "privatized-banner")
                == null,
        TIMEOUT);
    signal("b-restored");
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("A deletes another parent with the checkbox unticked: A's subquest is kept")
  void a_deletes_unticked(ClientGameTestContext context) {
    await("b-restored", context);

    given("an Open quest holding A's own Open subquest");
    UUID camp = createQuest(context, CAMP, "vis-open");
    createQuest(context, CAMP_CHILD, "vis-open");
    openQuest(context, "tab-my-quests", CAMP);
    enterEditor(context);
    pickSuggestion(context, "add-subquest-field", CAMP_CHILD);
    saveEditor(context);
    waitForQuestCondition(context, CAMP_CHILD, true, q -> camp.equals(q.getParentId()));

    when("A deletes the parent, leaving 'Also delete my 1 subquest' unticked");
    click(context, "btn-delete");
    waitForText(
        context, DisquestsBaseScreen.CONFIRM_CHECKBOX_ID, "Also delete my 1 subquest"::equals);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("the subquest is kept, top level, still Open (nothing widened)");
    waitForNotCached(context, CAMP);
    waitForQuestCondition(
        context,
        CAMP_CHILD,
        true,
        q ->
            q.getParentId() == null
                && q.getVisibility() == Visibility.OPEN
                && q.getPrivatized() == null);
    waitForScreen(context, MainScreen.class);
    signal("a-done");
  }

  @Test
  @Order(5)
  @PlayerB
  @DisplayName("B waits for A to finish before the next journey")
  void b_waits_for_a(ClientGameTestContext context) {
    await("a-done", context);
  }

  // --- helpers ---

  private static void signal(String phase) {
    PhaseSync.signal(J + phase);
  }

  private static void await(String phase, ClientGameTestContext context) {
    PhaseSync.waitFor(J + phase, context, seconds(120));
  }
}
