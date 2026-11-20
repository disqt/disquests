package com.disqt.disquests.test.integration.journeys.duo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

/**
 * What a quest's page says about quests its reader cannot see. A holds Write on B's quest, which
 * waits on a Private quest of B's: A's Prerequisites section says "… and something you don't have
 * access to". B's Open quest sits in A's Private quest; A removes B from it, then deletes it: B's
 * quest is kept and set to Private, and its banner names neither the deleted quest nor who deleted
 * it, since B could no longer see that quest. B keeps it Private with the banner's second button.
 */
@IntegrationTest
@DisplayName("Unseen Quests Journey")
class UnseenQuestsJourney {

  private static final String J = "unseen-";
  private static final String VAULT = "Sealed Vault";
  private static final String ERRAND = "B Errand";
  private static final String KEY = "B Key";
  private static final String DOOR = "B Door";
  private static final String A_NAME = "IntTestPlayerA";
  private static final String B_NAME = "IntTestPlayerB";

  private static final String HIDDEN = "… and something you don't have access to";

  private static UUID playerB;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerForDuo("unseen");
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("A creates a Private quest and gives B Write on it")
  void a_creates_vault(ClientGameTestContext context) {
    createQuest(context, VAULT, null);
    openQuest(context, "tab-my-quests", VAULT);
    openPeople(context);
    addPerson(context, B_NAME, "add-role-write");
    savePeople(context);
    waitForQuestCondition(context, VAULT, true, q -> !q.getContributors().isEmpty());
    playerB =
        context.computeOnClient(
            c -> cachedQuest(VAULT).getContributors().stream().findFirst().get().getUuid());
    waitForQuestCondition(context, VAULT, true, q -> q.roleOf(playerB) == Role.WRITE);
    signal("vault-ready");
  }

  @Test
  @Order(1)
  @PlayerB
  @DisplayName("B puts an Open quest in A's quest, and gives A Write on a quest with a secret step")
  void b_sets_up(ClientGameTestContext context) {
    await("vault-ready", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());
    waitForCachedQuest(context, VAULT, q -> q.roleOf(me) == Role.WRITE);

    when("B creates an Open quest and adds it from the editor of A's quest");
    createQuest(context, ERRAND, "vis-open");
    openQuest(context, "tab-my-quests", VAULT);
    enterEditor(context);
    pickSuggestion(context, "add-subquest-field", ERRAND);
    saveEditor(context);
    UUID vault = context.computeOnClient(c -> cachedQuest(VAULT).getId());
    waitForCachedQuest(context, ERRAND, q -> vault.equals(q.getParentId()));

    and("makes another quest wait on a Private quest of B's own");
    UUID key = createQuest(context, KEY, null);
    createQuest(context, DOOR, null);
    openQuest(context, "tab-my-quests", DOOR);
    enterEditor(context);
    pickSuggestion(context, "prerequisite-field", KEY);
    waitForComponent(context, "prereq-chip-" + key);
    saveEditor(context);
    waitForQuestCondition(context, DOOR, true, q -> q.getPrerequisites().contains(key));

    and("adds A to it with Write");
    openPeople(context);
    addPerson(context, A_NAME, "add-role-write");
    savePeople(context);
    waitForQuestCondition(
        context,
        DOOR,
        true,
        q -> q.getContributors().stream().anyMatch(c -> c.getRole() == Role.WRITE));
    signal("b-ready");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("A reads 'something you don't have access to', then removes B and deletes the quest")
  void a_reads_hidden_prerequisite_and_deletes(ClientGameTestContext context) {
    await("b-ready", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());

    then("B's quest reaches A with Write, not locked, waiting on something A cannot see");
    waitForCachedQuest(
        context,
        DOOR,
        q ->
            q.roleOf(me) == Role.WRITE
                && !q.isLocked()
                && q.hasHiddenPrerequisites()
                && q.getPrerequisites().isEmpty());

    when("A opens it");
    openQuest(context, "tab-my-quests", DOOR);

    then("the Prerequisites section has no line to open, only the hidden one");
    waitForTextUnder(
        context, "prerequisites-section", t -> t.equals("Prerequisites\n" + HIDDEN + "\n"));

    and("the page is an ordinary one for a Write holder: Edit, no lock, no owner's banner");
    assertComponentExists(context, "btn-edit");
    assertComponentMissing(context, "title-lock");
    assertComponentMissing(context, "waiting-text");
    assertComponentMissing(context, "deleted-prerequisite-banner");
    assertComponentMissing(context, "privatized-banner");

    when("A removes B from the Private quest that holds B's Open quest");
    openQuest(context, "tab-my-quests", VAULT);
    openPeople(context);
    click(context, "btn-remove-" + playerB);
    waitForComponent(context, "btn-undo-" + playerB);
    savePeople(context);
    waitForQuestCondition(context, VAULT, true, q -> q.roleOf(playerB) == null);

    and("deletes it: the dialog says B's quest is kept");
    waitForComponent(context, "btn-delete");
    click(context, "btn-delete");
    waitForText(
        context,
        DisquestsBaseScreen.CONFIRM_EXTRA_ID,
        "1 other belongs to another player and is kept"::equals);
    assertComponentMissing(context, DisquestsBaseScreen.CONFIRM_CHECKBOX_ID);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("the quest is gone, and B's quest with it from A's view: it was set to Private");
    waitForNotCached(context, VAULT);
    waitForNotCached(context, ERRAND);
    signal("vault-deleted");
  }

  @Test
  @Order(2)
  @PlayerB
  @DisplayName("B's banner names neither the deleted quest nor its deleter; B keeps it Private")
  void b_keeps_private(ClientGameTestContext context) {
    await("vault-deleted", context);

    then("B's quest is top level, Private, and remembers it was Open");
    waitForCachedQuest(
        context,
        ERRAND,
        q ->
            q.getParentId() == null
                && q.getVisibility() == Visibility.PRIVATE
                && q.getPrivatized() != null
                && q.getPrivatized().previous() == Visibility.OPEN);

    when("B opens it");
    openQuest(context, "tab-my-quests", ERRAND);

    then("the banner says what happened without the quest B could no longer see");
    waitForTextUnder(
        context,
        "privatized-banner",
        t ->
            t.startsWith("Set to Private on ")
                && t.contains("The quest it was in was deleted. It was ")
                && t.toLowerCase(Locale.ROOT).contains("it was open before")
                && !t.contains(VAULT)
                && !t.contains(A_NAME));
    waitForText(
        context,
        "btn-restore-visibility",
        t -> t.toLowerCase(Locale.ROOT).equals("set back to open"));
    assertButtonText(context, "btn-keep-private", "Keep Private");
    assertComponentMissing(context, "deleted-prerequisite-banner");

    when("B presses Keep Private");
    click(context, "btn-keep-private");

    then("the quest stays Private, the banner is gone and nothing is left to decide");
    waitForCachedQuest(
        context, ERRAND, q -> q.getVisibility() == Visibility.PRIVATE && q.getPrivatized() == null);
    context.waitFor(
        client -> onScreen(client, UIComponent.class, "privatized-banner") == null, TIMEOUT);
    assertComponentMissing(context, "btn-keep-private");
    assertComponentExists(context, "btn-edit");
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
