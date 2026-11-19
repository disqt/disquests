package com.disqt.disquests.test.integration.journeys.duo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.component.TreeRowGeometry;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
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
 * A subquest the hub's owner cannot see: B puts B's own Private quest under A's Private hub, where
 * B has Write. A's lists, "where this sits" and the complete dialog neither show nor count it; B's
 * editor for it is ordinary. When A removes B from the hub, B's editor shows the hidden-parent line
 * and Detach takes the quest out on Save.
 */
@IntegrationTest
@DisplayName("Hidden Subquest Journey")
class HiddenSubquestJourney {

  private static final String J = "hidden-";
  private static final String HUB = "Hidden Hub";
  private static final String SECRET = "B Secret";
  private static final String B_NAME = "IntTestPlayerB";

  private static UUID playerB;

  /** The hub's last change as A saw it before B's save: B's save moves it on. */
  private static long hubModified;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerForDuo("hidden");
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("A creates a Private hub and gives B Write on it")
  void a_creates_hub(ClientGameTestContext context) {
    createQuest(context, HUB, null);
    openQuest(context, "tab-my-quests", HUB);
    openPeople(context);
    addPerson(context, B_NAME, "add-role-write");
    savePeople(context);
    waitForQuestCondition(context, HUB, true, q -> !q.getContributors().isEmpty());
    playerB =
        context.computeOnClient(
            c -> cachedQuest(HUB).getContributors().stream().findFirst().get().getUuid());
    waitForQuestCondition(context, HUB, true, q -> q.roleOf(playerB) == Role.WRITE);
    hubModified = context.computeOnClient(c -> cachedQuest(HUB).getLastModified());
    signal("hub-ready");
  }

  @Test
  @Order(1)
  @PlayerB
  @DisplayName("B puts B's own Private quest under the hub")
  void b_attaches_secret(ClientGameTestContext context) {
    await("hub-ready", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());
    waitForCachedQuest(context, HUB, q -> q.roleOf(me) == Role.WRITE);

    when("B creates a Private quest and adds it from the hub's editor");
    createQuest(context, SECRET, null);
    openQuest(context, "tab-my-quests", HUB);
    enterEditor(context);
    pickSuggestion(context, "add-subquest-field", SECRET);
    saveEditor(context);

    then("it sits under the hub");
    UUID hub = context.computeOnClient(c -> cachedQuest(HUB).getId());
    waitForCachedQuest(context, SECRET, q -> hub.equals(q.getParentId()));
    signal("b-attached");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("A neither sees nor counts B's Private subquest")
  void a_does_not_see_secret(ClientGameTestContext context) {
    await("b-attached", context);

    then("A receives B's save of the hub, and A's cache does not hold the subquest");
    waitForQuestCondition(context, HUB, true, q -> q.getLastModified() > hubModified);
    assertNull(context.computeOnClient(c -> cachedQuest(SECRET)), "B's Private quest leaked");

    and("My Quests lists the hub with no toggle and nothing under it");
    openMainScreen(context);
    click(context, "tab-my-quests");
    waitForListed(context, HUB, true);
    UUID hub = context.computeOnClient(c -> cachedQuest(HUB).getId());
    assertComponentMissing(context, "toggle-" + hub);
    assertListed(context, SECRET, false);

    when("A opens the hub");
    openQuest(context, "tab-my-quests", HUB);

    then("the page has no 'where this sits' pane: the hub has no subquest A can see");
    waitForComponent(context, "btn-complete");
    assertComponentMissing(context, "where-this-sits");

    when("A presses Complete");
    click(context, "btn-complete");

    then("the dialog is the plain question: no checkbox, no count of other players' subquests");
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
    assertComponentMissing(context, DisquestsBaseScreen.CONFIRM_CHECKBOX_ID);
    assertComponentMissing(context, DisquestsBaseScreen.CONFIRM_EXTRA_ID);
    click(context, DisquestsBaseScreen.CONFIRM_NO_ID);
    waitForComponent(context, "btn-complete");
    signal("a-checked");
  }

  @Test
  @Order(2)
  @PlayerB
  @DisplayName("B's editor for the quest shows nothing unusual")
  void b_editor_is_ordinary(ClientGameTestContext context) {
    await("a-checked", context);
    UUID hub = context.computeOnClient(c -> cachedQuest(HUB).getId());
    UUID secret = context.computeOnClient(c -> cachedQuest(SECRET).getId());

    when("B opens the quest from the hub's page (it is nested under the hub) and edits it");
    openQuest(context, "tab-my-quests", HUB);
    clickAt(context, treeRowPoint(context, secret, TreeRowGeometry.Part.BODY));
    waitForText(context, "title-label", SECRET::equals);
    enterEditor(context);

    then("the pane shows it under the hub, with no hidden-parent line");
    waitForTreeRow(context, secret, l -> l.depth() == 1);
    waitForTreeRow(context, hub, l -> l.depth() == 0);
    assertComponentMissing(context, "btn-detach-hidden-parent");
    assertEquals("", context.computeOnClient(c -> textUnder(c, "hidden-parent-row")));

    and("B leaves the editor unchanged");
    click(context, "btn-cancel");
    waitForComponent(context, "btn-edit");
    signal("b-checked-editor");
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("A removes B from the hub")
  void a_removes_b(ClientGameTestContext context) {
    await("b-checked-editor", context);
    openQuest(context, "tab-my-quests", HUB);
    openPeople(context);
    click(context, "btn-remove-" + playerB);
    waitForComponent(context, "btn-undo-" + playerB);
    savePeople(context);
    waitForQuestCondition(context, HUB, true, q -> q.roleOf(playerB) == null);
    signal("b-removed");
  }

  @Test
  @Order(3)
  @PlayerB
  @DisplayName("B's editor shows the hidden-parent line; Detach takes the quest out on Save")
  void b_detaches(ClientGameTestContext context) {
    await("b-removed", context);

    then("the hub is gone from B's cache and the quest knows it sits in a hidden quest");
    waitForNotCached(context, HUB);
    waitForCachedQuest(context, SECRET, q -> q.hasHiddenParent());

    when("B opens the quest's editor");
    openQuest(context, "tab-my-quests", SECRET);
    enterEditor(context);

    then("the hidden-parent line and its Detach button show");
    waitForTextUnder(
        context, "hidden-parent-row", t -> t.contains("sits inside a quest you can't see"));
    assertComponentExists(context, "btn-detach-hidden-parent");

    when("B presses Detach");
    click(context, "btn-detach-hidden-parent");
    then("the line says 'not saved' until Save");
    waitForTextUnder(context, "hidden-parent-row", t -> t.contains("not saved"));
    saveEditor(context);

    then("the quest is top level and no longer in a hidden quest");
    waitForCachedQuest(context, SECRET, q -> !q.hasHiddenParent() && q.getParentId() == null);
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
