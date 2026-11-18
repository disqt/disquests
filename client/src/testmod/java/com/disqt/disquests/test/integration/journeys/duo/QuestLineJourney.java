package com.disqt.disquests.test.integration.journeys.duo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
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
 * A game-mastered quest line: A owns two Open chapters, the second waiting on the first with
 * auto-join, and B holds Read on the first. B sees chapter 2 locked with the three wordings of what
 * it waits on as A adds and removes a Private prerequisite B cannot see. A completes chapter 1: B
 * is auto-joined to chapter 2, which is marked new. A reopens chapter 1: chapter 2 locks again. A
 * Private chapter 3 reaches B only once A makes it Open.
 */
@IntegrationTest
@DisplayName("Quest Line Journey")
class QuestLineJourney {

  private static final String J = "line-";
  private static final String CH1 = "Chapter 1";
  private static final String CH2 = "Chapter 2";
  private static final String CH3 = "Chapter 3";
  private static final String SECRET = "Secret Step";
  private static final String B_NAME = "IntTestPlayerB";

  private static final String SOMETHING_ELSE = "… and something else you can't see yet";
  private static final String NOTHING_VISIBLE =
      "This quest isn't available yet. Something else has to be finished first.";

  private static UUID ch1;
  private static UUID ch2;
  private static UUID secret;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerForDuo("line");
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("A sets up chapter 2 to wait on chapter 1, with auto-join; B gets Read on 1")
  void a_sets_up_line(ClientGameTestContext context) {
    when("A creates two Open chapters");
    ch1 = createQuest(context, CH1, "vis-open");
    ch2 = createQuest(context, CH2, "vis-open");

    and("in chapter 2's editor picks chapter 1 as its prerequisite and ticks auto-join");
    openQuest(context, "tab-my-quests", CH2);
    enterEditor(context);
    pickSuggestion(context, "prerequisite-field", CH1);
    waitForComponent(context, "prereq-chip-" + ch1);
    tickCheckbox(context, "auto-join");
    saveEditor(context);

    then("chapter 2 waits on chapter 1 with auto-join");
    waitForQuestCondition(
        context, CH2, true, q -> q.getPrerequisites().contains(ch1) && q.isAutoJoin());

    when("A adds B to chapter 1 with Read");
    openQuest(context, "tab-my-quests", CH1);
    openPeople(context);
    addPerson(context, B_NAME, "add-role-read");
    savePeople(context);
    waitForQuestCondition(context, CH1, true, q -> !q.getContributors().isEmpty());
    signal("line-ready");
  }

  @Test
  @Order(1)
  @PlayerB
  @DisplayName("B sees chapter 2 greyed with a lock and what it waits on")
  void b_sees_locked_chapter(ClientGameTestContext context) {
    await("line-ready", context);

    then("chapter 2 reaches B as a locked view");
    waitForCachedQuest(context, CH2, Quest::isLocked);
    UUID c1 = context.computeOnClient(c -> cachedQuest(CH1).getId());

    and("the board lists it with a lock");
    openMainScreen(context);
    click(context, "tab-quest-board");
    waitForListed(context, CH2, true);
    QuestEntryComponent entry = context.computeOnClient(c -> listedEntry(c, CH2));
    assertTrue(
        (Boolean) context.computeOnClient(c -> entry.getMarks().locked()), "lock on the entry");

    when("B opens it");
    openQuest(context, "tab-quest-board", CH2);

    then("the locked page shows the lock and lists chapter 1, nothing else");
    waitForComponent(context, "title-lock");
    waitForComponent(context, "prerequisite-" + c1);
    waitForTextUnder(context, "waiting-text", t -> !t.contains(SOMETHING_ELSE));
    assertComponentMissing(context, "content-markdown");
    assertComponentMissing(context, "btn-interact");
    signal("b-saw-list");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("A adds a Private prerequisite B cannot see")
  void a_adds_hidden_prerequisite(ClientGameTestContext context) {
    await("b-saw-list", context);
    secret = createQuest(context, SECRET, null);
    editPrerequisites(context, SECRET, null);
    waitForQuestCondition(context, CH2, true, q -> q.getPrerequisites().contains(secret));
    signal("hidden-added");
  }

  @Test
  @Order(2)
  @PlayerB
  @DisplayName("B's locked page adds '… and something else you can't see yet'")
  void b_sees_something_else(ClientGameTestContext context) {
    await("hidden-added", context);
    waitForCachedQuest(context, CH2, Quest::hasHiddenPrerequisites);
    UUID c1 = context.computeOnClient(c -> cachedQuest(CH1).getId());
    openQuest(context, "tab-quest-board", CH2);
    waitForComponent(context, "prerequisite-" + c1);
    waitForTextUnder(context, "waiting-text", t -> t.contains(SOMETHING_ELSE));
    signal("b-saw-something-else");
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("A removes chapter 1 from the prerequisites: only the hidden one is left")
  void a_leaves_only_hidden(ClientGameTestContext context) {
    await("b-saw-something-else", context);
    editPrerequisites(context, null, ch1);
    waitForQuestCondition(context, CH2, true, q -> !q.getPrerequisites().contains(ch1));
    signal("only-hidden");
  }

  @Test
  @Order(3)
  @PlayerB
  @DisplayName("B's locked page says nothing it can see is left to finish")
  void b_sees_nothing_visible(ClientGameTestContext context) {
    await("only-hidden", context);
    waitForCachedQuest(context, CH2, q -> q.isLocked() && q.getPrerequisites().isEmpty());
    openQuest(context, "tab-quest-board", CH2);
    waitForTextUnder(context, "waiting-text", t -> t.contains(NOTHING_VISIBLE));
    signal("b-saw-nothing-visible");
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("A puts chapter 1 back and removes the hidden prerequisite")
  void a_restores_line(ClientGameTestContext context) {
    await("b-saw-nothing-visible", context);
    editPrerequisites(context, CH1, secret);
    waitForQuestCondition(
        context,
        CH2,
        true,
        q -> q.getPrerequisites().contains(ch1) && !q.getPrerequisites().contains(secret));
    signal("line-restored");
  }

  @Test
  @Order(4)
  @PlayerB
  @DisplayName("B's locked page lists chapter 1 alone again")
  void b_sees_list_again(ClientGameTestContext context) {
    await("line-restored", context);
    UUID c1 = context.computeOnClient(c -> cachedQuest(CH1).getId());
    waitForCachedQuest(
        context, CH2, q -> q.getPrerequisites().contains(c1) && !q.hasHiddenPrerequisites());
    openQuest(context, "tab-quest-board", CH2);
    waitForComponent(context, "prerequisite-" + c1);
    waitForTextUnder(context, "waiting-text", t -> !t.contains(SOMETHING_ELSE));
    // Back on the main screen: a page open on the quest when it unlocks counts as opening it,
    // which clears the NEW mark at once
    openMainScreen(context);
    signal("b-ready-for-unlock");
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("A completes chapter 1")
  void a_completes_chapter_1(ClientGameTestContext context) {
    await("b-ready-for-unlock", context);
    openQuest(context, "tab-my-quests", CH1);
    click(context, "btn-complete");
    confirmYes(context);
    waitForQuestCondition(context, CH1, true, Quest::isCompleted);
    waitForScreen(context, MainScreen.class);
    signal("chapter-1-completed");
  }

  @Test
  @Order(5)
  @PlayerB
  @DisplayName("B gets chapter 2 unlocked, joined with Read and marked new")
  void b_gets_unlock(ClientGameTestContext context) {
    await("chapter-1-completed", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());

    then("chapter 2 is unlocked for B and B holds Read on it (auto-join)");
    waitForCachedQuest(context, CH2, q -> !q.isLocked() && q.roleOf(me) == Role.READ);

    and("it is marked new in B's list");
    UUID c2 = context.computeOnClient(c -> cachedQuest(CH2).getId());
    // The notice that sets the mark follows the quest update
    context.waitFor(client -> ClientSession.newMarks().isNew(c2), TIMEOUT);
    openMainScreen(context);
    click(context, "tab-my-quests");
    waitForListed(context, CH2, true);
    QuestEntryComponent entry = context.computeOnClient(c -> listedEntry(c, CH2));
    assertTrue(
        (Boolean) context.computeOnClient(c -> entry.getMarks().isNew()), "NEW on the entry");
    assertFalse(
        (Boolean) context.computeOnClient(c -> entry.getMarks().locked()), "no lock any more");
    signal("b-unlocked");
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("A reopens chapter 1")
  void a_reopens_chapter_1(ClientGameTestContext context) {
    await("b-unlocked", context);
    openQuest(context, "tab-archived", CH1);
    click(context, "btn-reopen");
    confirmYes(context);
    waitForQuestCondition(context, CH1, true, q -> !q.isCompleted());
    signal("chapter-1-reopened");
  }

  @Test
  @Order(6)
  @PlayerB
  @DisplayName("Chapter 2 locks again for B, who keeps Read on it")
  void b_sees_relock(ClientGameTestContext context) {
    await("chapter-1-reopened", context);
    UUID me = context.computeOnClient(c -> ClientSession.getEffectivePlayerUuid());
    waitForCachedQuest(context, CH2, q -> q.isLocked() && q.roleOf(me) == Role.READ);
    openMainScreen(context);
    click(context, "tab-my-quests");
    waitForListed(context, CH2, true);
    QuestEntryComponent entry = context.computeOnClient(c -> listedEntry(c, CH2));
    assertTrue(
        (Boolean) context.computeOnClient(c -> entry.getMarks().locked()), "lock on the entry");
    signal("b-saw-relock");
  }

  @Test
  @Order(7)
  @PlayerA
  @DisplayName("A creates a Private chapter 3")
  void a_creates_secret_chapter(ClientGameTestContext context) {
    await("b-saw-relock", context);
    createQuest(context, CH3, null);
    signal("chapter-3-created");
  }

  @Test
  @Order(7)
  @PlayerB
  @DisplayName("B does not see the Private chapter 3")
  void b_does_not_see_chapter_3(ClientGameTestContext context) {
    await("chapter-3-created", context);
    openMainScreen(context);
    click(context, "tab-quest-board");
    waitForListed(context, CH3, false);
    click(context, "tab-my-quests");
    waitForListed(context, CH2, true);
    assertListed(context, CH3, false);
    assertNull(context.computeOnClient(c -> cachedQuest(CH3)), "Private chapter 3 leaked");
    signal("b-checked-chapter-3");
  }

  @Test
  @Order(8)
  @PlayerA
  @DisplayName("A reveals chapter 3 by making it Open")
  void a_reveals_chapter_3(ClientGameTestContext context) {
    await("b-checked-chapter-3", context);
    openQuest(context, "tab-my-quests", CH3);
    enterEditor(context);
    choose(context, "vis-open");
    saveEditor(context);
    waitForQuestCondition(context, CH3, true, q -> q.getVisibility() == Visibility.OPEN);
    signal("chapter-3-revealed");
  }

  @Test
  @Order(8)
  @PlayerB
  @DisplayName("B now sees chapter 3 on the board")
  void b_sees_chapter_3(ClientGameTestContext context) {
    await("chapter-3-revealed", context);
    waitForCachedQuest(context, CH3, q -> q.getVisibility() == Visibility.OPEN);
    openMainScreen(context);
    click(context, "tab-quest-board");
    waitForListed(context, CH3, true);
    signal("b-done");
  }

  @Test
  @Order(9)
  @PlayerA
  @DisplayName("A waits for B to finish before the next journey")
  void a_waits_for_b(ClientGameTestContext context) {
    await("b-done", context);
  }

  // --- helpers ---

  /**
   * In chapter 2's editor: picks {@code add} as a prerequisite (when not null), removes the chip of
   * {@code remove} (when not null), saves.
   */
  private static void editPrerequisites(ClientGameTestContext context, String add, UUID remove) {
    openQuest(context, "tab-my-quests", CH2);
    enterEditor(context);
    if (add != null) {
      pickSuggestion(context, "prerequisite-field", add);
      UUID added = context.computeOnClient(c -> cachedQuest(add).getId());
      waitForComponent(context, "prereq-chip-" + added);
    }
    if (remove != null) {
      clickChipRemove(context, "prereq-chip-" + remove);
      context.waitFor(
          client ->
              onScreen(client, io.wispforest.owo.ui.core.UIComponent.class, "prereq-chip-" + remove)
                  == null,
          TIMEOUT);
    }
    saveEditor(context);
    assertComponentMissing(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
  }

  private static void signal(String phase) {
    PhaseSync.signal(J + phase);
  }

  private static void await(String phase, ClientGameTestContext context) {
    PhaseSync.waitFor(J + phase, context, seconds(120));
  }
}
