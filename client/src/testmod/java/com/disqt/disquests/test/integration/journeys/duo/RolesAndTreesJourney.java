package com.disqt.disquests.test.integration.journeys.duo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Contributor;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TreeRowComponent;
import com.disqt.disquests.client.gui.component.TreeRowGeometry;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.*;

/**
 * The spec's hub example with two players: A owns a Private hub holding an Open, a Closed and a
 * Private subquest; B is first outside the hub, then on it with Read, then Write. A shares B's role
 * down the tree and removes B from the hub and A's subquests in one go.
 */
@IntegrationTest
@DisplayName("Roles and Trees Journey")
class RolesAndTreesJourney {

  private static final String J = "roles-";
  private static final String HUB = "Hub";
  private static final String OPEN = "Hub Open";
  private static final String CLOSED = "Hub Closed";
  private static final String PRIVATE = "Hub Private";
  private static final List<String> SUBQUESTS = List.of(OPEN, CLOSED, PRIVATE);
  private static final String B_NAME = "IntTestPlayerB";

  /** Player B's uuid, as Player A learns it from the hub's people. */
  private static UUID playerB;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerForDuo("roles");
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("A builds a Private hub with an Open, a Closed and a Private subquest")
  void a_builds_hub(ClientGameTestContext context) {
    when("A creates the four quests in the editor");
    UUID hub = createQuest(context, HUB, null);
    createQuest(context, OPEN, "vis-open");
    createQuest(context, CLOSED, "vis-closed");
    createQuest(context, PRIVATE, null);

    and("puts the three under the hub from the hub's editor");
    openQuest(context, "tab-my-quests", HUB);
    enterEditor(context);
    for (String title : SUBQUESTS) pickSuggestion(context, "add-subquest-field", title);
    saveEditor(context);

    then("the server holds the tree");
    for (String title : SUBQUESTS) {
      waitForQuestCondition(context, title, true, q -> hub.equals(q.getParentId()));
    }
    signal("hub-built");
  }

  @Test
  @Order(1)
  @PlayerB
  @DisplayName("B, not on the hub, sees none of the four")
  void b_sees_nothing(ClientGameTestContext context) {
    when("A has built the hub");
    await("hub-built", context);

    then("the Open and Closed quests, top level and visible before, are gone from B's cache");
    waitForNotCached(context, OPEN);
    waitForNotCached(context, CLOSED);
    assertNull(context.computeOnClient(c -> cachedQuest(HUB)));
    assertNull(context.computeOnClient(c -> cachedQuest(PRIVATE)));

    and("the Quest Board lists none of them");
    openMainScreen(context);
    click(context, "tab-quest-board");
    waitForListed(context, OPEN, false);
    assertListed(context, HUB, false);
    assertListed(context, CLOSED, false);
    signal("b-sees-nothing");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("A adds B to the hub with Read through the people screen")
  void a_adds_b_with_read(ClientGameTestContext context) {
    await("b-sees-nothing", context);

    when("A opens the hub's people, picks B in the chooser with Read, and saves");
    openQuest(context, "tab-my-quests", HUB);
    openPeople(context);
    addPerson(context, B_NAME, "add-role-read");
    savePeople(context);

    then("B holds Read on the hub");
    waitForQuestCondition(context, HUB, true, q -> !q.getContributors().isEmpty());
    playerB = otherPerson(context, HUB);
    waitForQuestCondition(context, HUB, true, q -> q.roleOf(playerB) == Role.READ);
    signal("b-has-read");
  }

  @Test
  @Order(2)
  @PlayerB
  @DisplayName("B, member of the hub, sees the Open and Closed subquests, not the Private one")
  void b_sees_member_view(ClientGameTestContext context) {
    await("b-has-read", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());

    then("B's cache has the hub with Read, the Open and the Closed subquest");
    waitForCachedQuest(context, HUB, q -> q.roleOf(me) == Role.READ);
    waitForCachedQuest(context, OPEN, q -> true);
    waitForCachedQuest(context, CLOSED, q -> true);
    assertNull(context.computeOnClient(c -> cachedQuest(PRIVATE)), "Private subquest leaked");

    when("B opens the hub");
    openQuest(context, "tab-my-quests", HUB);

    then("'where this sits' lists the hub and its two visible subquests, nothing more");
    UUID open = context.computeOnClient(c -> cachedQuest(OPEN).getId());
    UUID closed = context.computeOnClient(c -> cachedQuest(CLOSED).getId());
    waitForTreeRow(context, open, l -> l.depth() == 1);
    waitForTreeRow(context, closed, l -> l.depth() == 1);
    int rows = context.computeOnClient(c -> treeRowCount(c));
    assertEquals(3, rows, "rows in 'where this sits'");

    when("B opens the Open subquest from the pane");
    clickAt(context, treeRowPoint(context, open, TreeRowGeometry.Part.BODY));
    then("B can join it");
    waitForText(context, "btn-interact", "Join"::equals);

    when("B opens the Closed subquest");
    openQuest(context, "tab-my-quests", HUB);
    clickAt(context, treeRowPoint(context, closed, TreeRowGeometry.Part.BODY));
    then("B can request to join it");
    waitForText(context, "btn-interact", "Request"::equals);
    signal("b-checked-read");
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("A gives B Write on the hub; A, the last owner, has no Leave")
  void a_gives_write(ClientGameTestContext context) {
    await("b-checked-read", context);

    when("A gives B Write on the hub's people screen and saves");
    openQuest(context, "tab-my-quests", HUB);
    openPeople(context);
    choose(context, "role-write-" + playerB);
    savePeople(context);

    then("B holds Write");
    waitForQuestCondition(context, HUB, true, q -> q.roleOf(playerB) == Role.WRITE);

    and("A's page has Delete but no Leave: the last owner cannot leave");
    waitForComponent(context, "btn-delete");
    assertComponentMissing(context, "btn-leave");
    signal("b-has-write");
  }

  @Test
  @Order(3)
  @PlayerB
  @DisplayName("B with Write can edit, but not complete, delete or change links")
  void b_with_write(ClientGameTestContext context) {
    await("b-has-write", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());
    waitForCachedQuest(context, HUB, q -> q.roleOf(me) == Role.WRITE);

    when("B opens the hub");
    openQuest(context, "tab-my-quests", HUB);

    then("B has Edit and Leave, no Complete, Delete or people");
    waitForComponent(context, "btn-edit");
    assertComponentExists(context, "btn-leave");
    assertComponentMissing(context, "btn-complete");
    assertComponentMissing(context, "btn-delete");
    assertComponentMissing(context, "btn-manage-people");

    when("B opens the editor");
    enterEditor(context);

    then("the tree pane is there, the prerequisites and visibility controls are not");
    assertComponentExists(context, "edit-tree-pane");
    assertComponentMissing(context, "prerequisite-field");
    assertComponentMissing(context, "vis-open");

    and("B leaves the editor unchanged");
    click(context, "btn-cancel");
    waitForComponent(context, "btn-edit");
    signal("b-checked-write");
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("A applies B's Write to A's subquests")
  void a_applies_down(ClientGameTestContext context) {
    await("b-checked-write", context);

    when("A presses 'Apply to subquests' on B's row and confirms");
    openQuest(context, "tab-my-quests", HUB);
    openPeople(context);
    click(context, "btn-apply-down-" + playerB);
    waitForTextUnder(
        context,
        com.disqt.disquests.client.gui.screen.DisquestsBaseScreen.CONFIRM_OVERLAY_ID,
        t -> t.contains("Give " + B_NAME + " Write on your 3 subquests of Hub?"));
    confirmYes(context);

    then("B holds Write on each of A's subquests");
    for (String title : SUBQUESTS) {
      waitForQuestCondition(context, title, true, q -> q.roleOf(playerB) == Role.WRITE);
    }
    and("A leaves the people screen: nothing to save");
    click(context, "btn-cancel-people");
    waitForScreen(context, QuestScreen.class);
    signal("applied-down");
  }

  @Test
  @Order(4)
  @PlayerB
  @DisplayName("B now holds Write on the subquests, the Private one included")
  void b_gains_subquests(ClientGameTestContext context) {
    await("applied-down", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());

    then("B's cache has Write on all three subquests");
    for (String title : SUBQUESTS) {
      waitForCachedQuest(context, title, q -> q.roleOf(me) == Role.WRITE);
    }

    and("the hub's page lists the Private subquest too");
    openQuest(context, "tab-my-quests", HUB);
    UUID hidden = context.computeOnClient(c -> cachedQuest(PRIVATE).getId());
    waitForTreeRow(context, hidden, l -> l.depth() == 1);
    signal("b-checked-down");
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("A removes B from the hub and A's three subquests")
  void a_removes_with_subquests(ClientGameTestContext context) {
    await("b-checked-down", context);

    when("A presses Remove on B's row");
    openQuest(context, "tab-my-quests", HUB);
    openPeople(context);
    click(context, "btn-remove-" + playerB);

    then("A is asked whether to remove B from the subquests too");
    waitForText(context, "btn-remove-with-subquests", "From this quest and my 3 subquests"::equals);
    assertComponentExists(context, "btn-remove-this-only");

    when("A chooses 'From this quest and my 3 subquests' and saves");
    click(context, "btn-remove-with-subquests");
    waitForComponent(context, "btn-undo-" + playerB);
    savePeople(context);

    then("B holds no role on any of the four");
    waitForQuestCondition(context, HUB, true, q -> q.roleOf(playerB) == null);
    for (String title : SUBQUESTS) {
      waitForQuestCondition(context, title, true, q -> q.roleOf(playerB) == null);
    }
    signal("b-removed");
  }

  @Test
  @Order(5)
  @PlayerB
  @DisplayName("B, removed, sees none of the four again")
  void b_removed(ClientGameTestContext context) {
    await("b-removed", context);

    then("the hub and its subquests leave B's cache and lists");
    waitForNotCached(context, HUB);
    for (String title : SUBQUESTS) waitForNotCached(context, title);
    openMainScreen(context);
    click(context, "tab-my-quests");
    waitForListed(context, HUB, false);
    signal("b-done");
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("A waits for B to finish before the next journey")
  void a_waits_for_b(ClientGameTestContext context) {
    await("b-done", context);
    openMainScreen(context);
    waitForScreen(context, MainScreen.class);
  }

  // --- helpers ---

  private static void signal(String phase) {
    PhaseSync.signal(J + phase);
  }

  private static void await(String phase, ClientGameTestContext context) {
    PhaseSync.waitFor(J + phase, context, seconds(120));
  }

  /** The first person on the cached quest who is not its display owner. */
  private static UUID otherPerson(ClientGameTestContext context, String title) {
    return context.computeOnClient(
        c -> {
          Quest q = cachedQuest(title);
          return q.getContributors().stream()
              .map(Contributor::getUuid)
              .findFirst()
              .orElseThrow(() -> new AssertionError("Nobody else on " + title));
        });
  }

  /** How many rows the reading view's "where this sits" pane shows. */
  private static int treeRowCount(Minecraft client) {
    var list =
        onScreen(client, io.wispforest.owo.ui.container.FlowLayout.class, "where-this-sits-list");
    if (list == null) return 0;
    return (int) list.children().stream().filter(c -> c instanceof TreeRowComponent).count();
  }
}
