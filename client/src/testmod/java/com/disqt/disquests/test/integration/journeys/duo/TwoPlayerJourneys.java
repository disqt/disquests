package com.disqt.disquests.test.integration.journeys.duo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Contributor;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.ContributorScreen;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestListPanel;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.*;

/**
 * All two-player journeys in one flat class. Order ranges: Collaboration 1-5, OpenQuest 10-12,
 * Completion 20-21.
 */
@IntegrationTest
@DisplayName("Two-Player Journeys")
class TwoPlayerJourneys {

  // =========================================================================
  // Collaboration: request, accept, permissions, remove
  // =========================================================================

  // Collaboration: A creates(1), B requests(1), A accepts(2), B verifies(2), A manages(3)
  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Collab: A creates CLOSED quest")
  void collab_a_creates_closed_quest(ClientGameTestContext context) {
    given("PlayerA is connected to the server");
    context.waitFor(client -> ClientSession.isOnServer(), CONNECT_TIMEOUT);

    when("PlayerA opens MainScreen and creates a new quest");
    openMainScreen(context);
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);

    and("PlayerA types a title");
    type(context, "title-field", "Collab Quest");

    and("PlayerA sets visibility to CLOSED");
    selectVisibility(context, "vis-closed");

    and("PlayerA saves the quest");
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);

    then("quest is saved in view mode");
    assertLabelText(context, "title-label", "Collab Quest");

    and("PlayerA closes the quest");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    assertEntryCount(context, 1);

    PhaseSync.signal("collab-quest-created");
  }

  @Test
  @Order(1)
  @PlayerB
  @DisplayName("Collab: B finds quest on board and sends request")
  void collab_b_finds_and_requests(ClientGameTestContext context) {
    given("PlayerB is connected to the server");
    context.waitFor(client -> ClientSession.isOnServer(), CONNECT_TIMEOUT);

    when("PlayerB waits for quest to be created");
    PhaseSync.waitFor("collab-quest-created", context);

    and("PlayerB opens MainScreen and switches to Quest Board");
    openMainScreen(context);
    click(context, "tab-quest-board");

    then("Collab Quest is visible on the Quest Board");
    waitForQuestByTitle(context, "Collab Quest", false);

    when("PlayerB selects the quest and clicks Request");
    clickEntryByTitle(context, "Collab Quest");
    click(context, "btn-interact");

    then("button shows Requested");
    assertButtonText(context, "btn-interact", "Requested");

    PhaseSync.signal("collab-request-sent");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Collab: A sees pending request and accepts")
  void collab_a_accepts_request(ClientGameTestContext context) {
    when("PlayerA waits for request to be sent");
    PhaseSync.waitFor("collab-request-sent", context);

    and("PlayerA waits for pending request to arrive");
    waitForQuestCondition(
        context, "Collab Quest", true, q -> ClientCache.getPendingCount(q.getId()) > 0);

    and("PlayerA opens MainScreen and selects the quest");
    openMainScreen(context);
    clickEntryByTitle(context, "Collab Quest");
    click(context, "btn-open");
    waitForScreen(context, QuestScreen.class);

    and("PlayerA enters edit mode");
    click(context, "btn-edit");
    waitForScreen(context, QuestScreen.class);

    then("contributors button shows pending count");
    assertButtonText(context, "btn-contributors", "+ 1");

    when("PlayerA clicks Contributors");
    click(context, "btn-contributors");
    waitForScreen(context, ContributorScreen.class);

    then("ContributorScreen shows pending request from PlayerB");
    boolean hasPending =
        context.computeOnClient(
            c -> {
              Screen screen = c.gui.screen();
              if (screen
                  instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) return false;
                FlowLayout pendingList = root.childById(FlowLayout.class, "pending-list");
                return pendingList != null && !pendingList.children().isEmpty();
              }
              return false;
            });
    assertTrue(hasPending, "Expected pending requests in ContributorScreen");

    when("PlayerA clicks Accept on the first pending request");
    double[] acceptPos =
        context.computeOnClient(
            c -> {
              Screen screen = c.gui.screen();
              if (screen
                  instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) throw new AssertionError("Root not found");
                FlowLayout pendingList = root.childById(FlowLayout.class, "pending-list");
                if (pendingList == null || pendingList.children().isEmpty())
                  throw new AssertionError("pending-list is empty");
                var firstRow = pendingList.children().get(0);
                if (!(firstRow instanceof FlowLayout rowLayout))
                  throw new AssertionError("First pending row is not a FlowLayout");
                ButtonComponent acceptBtn =
                    rowLayout.children().stream()
                        .filter(child -> child instanceof ButtonComponent)
                        .map(child -> (ButtonComponent) child)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Accept button not found in row"));
                return new double[] {
                  acceptBtn.x() + acceptBtn.width() / 2.0, acceptBtn.y() + acceptBtn.height() / 2.0
                };
              }
              throw new AssertionError("Not a Disquests screen");
            });
    clickAt(context, acceptPos);
    waitForScreen(context, ContributorScreen.class);

    then("PlayerB appears in the contributor list");
    // Wait for server to process accept and send back updated quest with contributor
    waitForQuestCondition(context, "Collab Quest", true, q -> !q.getContributors().isEmpty());

    and("PlayerA reopens ContributorScreen with updated data");
    // Reopened via quest edit mode, so the people screen's parent is the editor
    openMainScreen(context);
    clickEntryByTitle(context, "Collab Quest");
    click(context, "btn-open");
    waitForScreen(context, QuestScreen.class);
    click(context, "btn-edit");
    waitForScreen(context, QuestScreen.class);
    click(context, "btn-contributors");
    waitForScreen(context, ContributorScreen.class);

    PhaseSync.signal("collab-accepted");
  }

  @Test
  @Order(2)
  @PlayerB
  @DisplayName("Collab: B sees quest in My Quests after accept")
  void collab_b_sees_quest_in_my_quests(ClientGameTestContext context) {
    when("PlayerB waits for request to be accepted");
    PhaseSync.waitFor("collab-accepted", context);

    and("PlayerB waits for quest to appear in My Quests");
    waitForQuestByTitle(context, "Collab Quest", true);

    then("quest appears in PlayerB's My Quests");
    openMainScreen(context);
    waitForEntryCount(context, 1);
    assertEntryCount(context, 1);
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("Collab: A gives the contributor Write and removes them")
  void collab_a_manages_contributor(ClientGameTestContext context) {
    given("PlayerA is still on ContributorScreen");
    waitForScreen(context, ContributorScreen.class);
    UUID playerB = firstContributor(context, "Collab Quest");

    when("PlayerA gives the contributor Write and saves");
    grantWriteAndSave(context, playerB);

    then("the contributor has Write");
    waitForQuestCondition(
        context,
        "Collab Quest",
        true,
        q ->
            q.getContributors().stream().anyMatch(c -> c.getUuid().equals(playerB) && c.canEdit()));

    when("PlayerA reopens the people screen from the same editor");
    click(context, "btn-contributors");
    waitForScreen(context, ContributorScreen.class);

    then("Write shows as the contributor's current role, saved");
    // The draft starts from the saved quest, not the editor's older copy
    waitForComponent(context, "role-write-" + playerB);
    ButtonComponent write = findComponent(context, ButtonComponent.class, "role-write-" + playerB);
    boolean writeActive = context.computeOnClient(c -> write.isActive());
    assertFalse(writeActive, "Write should show pressed");
    assertComponentMissing(context, "person-status-" + playerB);

    when("PlayerA clicks Remove on the contributor");
    click(context, "btn-remove-" + playerB);

    then("the row shows as removed, not saved, with Undo");
    // No subquests, so nothing to ask: the removal waits in the draft
    waitForComponent(context, "btn-undo-" + playerB);

    when("PlayerA saves the removal");
    clickWhenActive(context, "btn-save-people");
    waitForScreen(context, QuestScreen.class);

    then("contributor list is empty");
    // Wait for server to process removal
    waitForQuestCondition(context, "Collab Quest", true, q -> q.getContributors().isEmpty());
  }

  // =========================================================================
  // OpenQuest: join, view, leave
  // =========================================================================

  @Test
  @Order(10)
  @PlayerA
  @DisplayName("Open: A creates OPEN quest")
  void open_a_creates_open_quest(ClientGameTestContext context) {
    given("PlayerA is connected to the server");
    context.waitFor(client -> ClientSession.isOnServer(), CONNECT_TIMEOUT);

    when("PlayerA opens MainScreen and creates a new quest");
    openMainScreen(context);
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);

    and("PlayerA types a title");
    type(context, "title-field", "Open Quest");

    and("PlayerA sets visibility to OPEN");
    selectVisibility(context, "vis-open");

    and("PlayerA saves the quest");
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);

    then("quest is saved in view mode");
    assertLabelText(context, "title-label", "Open Quest");

    and("PlayerA closes the quest");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);

    PhaseSync.signal("open-quest-created");
  }

  @Test
  @Order(10)
  @PlayerB
  @DisplayName("Open: B joins the OPEN quest")
  void open_b_joins_quest(ClientGameTestContext context) {
    given("PlayerB is connected to the server");
    context.waitFor(client -> ClientSession.isOnServer(), CONNECT_TIMEOUT);

    when("PlayerB waits for quest to be created");
    PhaseSync.waitFor("open-quest-created", context);

    and("PlayerB opens MainScreen and switches to Quest Board");
    openMainScreen(context);
    click(context, "tab-quest-board");

    then("Open Quest is visible on the Quest Board");
    waitForQuestByTitle(context, "Open Quest", false);

    when("PlayerB selects the quest and clicks Join");
    clickEntryByTitle(context, "Open Quest");
    click(context, "btn-interact");

    then("quest appears in PlayerB's My Quests");
    waitForQuestByTitle(context, "Open Quest", true);

    PhaseSync.signal("open-quest-joined");
  }

  @Test
  @Order(11)
  @PlayerB
  @DisplayName("Open: B opens quest, views content, and leaves")
  void open_b_opens_and_leaves(ClientGameTestContext context) {
    given("PlayerB has joined Open Quest");

    when("PlayerB opens MainScreen");
    openMainScreen(context);
    click(context, "tab-my-quests");
    waitForQuestByTitle(context, "Open Quest", true);

    and("PlayerB selects the quest and opens it");
    clickEntryByTitle(context, "Open Quest");
    click(context, "btn-open");
    waitForScreen(context, QuestScreen.class);

    then("quest content is visible in view mode");
    assertLabelText(context, "title-label", "Open Quest");

    when("PlayerB clicks Leave");
    click(context, "btn-leave");

    then("confirm overlay appears");
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);

    when("PlayerB confirms leave");
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("quest is removed from PlayerB's My Quests");
    waitForScreen(context, MainScreen.class);
    assertEntryCount(context, 0);
  }

  // =========================================================================
  // Completion: A completes a quest B edits; B finds it in Archived
  // =========================================================================

  @Test
  @Order(20)
  @PlayerA
  @DisplayName("Completion: A creates OPEN quest")
  void completion_a_creates_open_quest(ClientGameTestContext context) {
    given("PlayerA is on the My Quests tab");
    context.waitFor(client -> ClientSession.isOnServer(), CONNECT_TIMEOUT);
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
        });

    when("PlayerA creates an OPEN quest");
    openMainScreen(context);
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", "Archive Quest");
    selectVisibility(context, "vis-open");
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);

    then("the quest is saved");
    assertLabelText(context, "title-label", "Archive Quest");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);

    PhaseSync.signal("archive-quest-created");
  }

  @Test
  @Order(20)
  @PlayerB
  @DisplayName("Completion: B joins the OPEN quest")
  void completion_b_joins_quest(ClientGameTestContext context) {
    given("PlayerB is connected to the server");
    context.waitFor(client -> ClientSession.isOnServer(), CONNECT_TIMEOUT);
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
        });
    PhaseSync.waitFor("archive-quest-created", context);

    when("PlayerB joins the quest from the Quest Board");
    openMainScreen(context);
    click(context, "tab-quest-board");
    waitForQuestByTitle(context, "Archive Quest", false);
    clickEntryByTitle(context, "Archive Quest");
    click(context, "btn-interact");

    then("the quest is in PlayerB's My Quests");
    waitForQuestByTitle(context, "Archive Quest", true);
    click(context, "tab-my-quests");

    PhaseSync.signal("archive-quest-joined");
  }

  @Test
  @Order(21)
  @PlayerA
  @DisplayName("Completion: A grants B edit rights and completes the quest")
  void completion_a_grants_and_completes(ClientGameTestContext context) {
    given("PlayerB has joined the quest");
    PhaseSync.waitFor("archive-quest-joined", context);
    waitForQuestCondition(context, "Archive Quest", true, q -> !q.getContributors().isEmpty());

    when("PlayerA gives PlayerB edit rights from the Contributors screen");
    openMainScreen(context);
    clickEntryByTitle(context, "Archive Quest");
    click(context, "btn-open");
    waitForScreen(context, QuestScreen.class);
    click(context, "btn-edit");
    waitForScreen(context, QuestScreen.class);
    click(context, "btn-contributors");
    waitForScreen(context, ContributorScreen.class);
    grantWriteAndSave(context, firstContributor(context, "Archive Quest"));
    waitForQuestCondition(
        context,
        "Archive Quest",
        true,
        q -> q.getContributors().stream().anyMatch(Contributor::canEdit));

    and("PlayerA opens the quest in view mode and completes it");
    // Leaves the untouched editor behind: nothing to save.
    openMainScreen(context);
    clickEntryByTitle(context, "Archive Quest");
    click(context, "btn-open");
    waitForScreen(context, QuestScreen.class);
    waitForComponent(context, "btn-complete");
    click(context, "btn-complete");
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);

    then("the quest is completed in PlayerA's cache");
    waitForQuestCondition(context, "Archive Quest", true, Quest::isCompleted);
    waitForScreen(context, MainScreen.class);

    PhaseSync.signal("archive-quest-completed");
  }

  @Test
  @Order(21)
  @PlayerB
  @DisplayName("Completion: B sees the quest completed in Archived")
  void completion_b_sees_archived(ClientGameTestContext context) {
    when("PlayerA completes the quest");
    PhaseSync.waitFor("archive-quest-completed", context);

    then("PlayerB's cache marks it completed");
    waitForQuestCondition(context, "Archive Quest", true, Quest::isCompleted);

    when("PlayerB opens the Archived tab");
    openMainScreen(context);
    click(context, "tab-archived");

    then("the quest is listed there");
    context.waitFor(
        client -> {
          if (!(client.gui.screen() instanceof MainScreen screen)) return false;
          var root = screen.getRootComponent();
          FlowLayout list = root != null ? root.childById(FlowLayout.class, "quest-list") : null;
          return list != null
              && QuestListPanel.entries(list).stream()
                  .anyMatch(entry -> "Archive Quest".equals(entry.getQuest().getTitle()));
        },
        TIMEOUT);

    when("PlayerB opens it");
    clickEntryByTitle(context, "Archive Quest");
    click(context, "btn-open");
    waitForScreen(context, QuestScreen.class);

    then("it is a read-only record; reopening is for owners, and PlayerB has Write");
    waitForComponent(context, "completed-label");
    LabelComponent label = findComponent(context, LabelComponent.class, "completed-label");
    String text = context.computeOnClient(c -> label.text().getString());
    assertTrue(text.startsWith("Completed on"), "completed-label text was '" + text + "'");
    assertComponentMissing(context, "btn-reopen");
    assertComponentMissing(context, "btn-edit");
    assertComponentMissing(context, "btn-leave");

    and("PlayerB returns to My Quests");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    click(context, "tab-my-quests");
  }

  /** The first contributor (not the owner) of the cached quest with this title. */
  private static UUID firstContributor(ClientGameTestContext context, String title) {
    return context.computeOnClient(
        c ->
            ClientCache.getMyQuests().stream()
                .filter(q -> title.equals(q.getTitle()))
                .flatMap(q -> q.getContributors().stream())
                .map(Contributor::getUuid)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No contributor on " + title)));
  }

  /**
   * On the people screen: presses the person's Write button, then Save once the draft shows the
   * change (Save is disabled while nothing changed). Save returns to the screen it was opened from.
   */
  private static void grantWriteAndSave(ClientGameTestContext context, UUID person) {
    click(context, "role-write-" + person);
    context.waitFor(
        client ->
            client.gui.screen() instanceof DisquestsBaseScreen screen
                && screen.getRootComponent() != null
                && screen
                        .getRootComponent()
                        .childById(ButtonComponent.class, "role-write-" + person)
                    != null
                && !screen
                    .getRootComponent()
                    .childById(ButtonComponent.class, "role-write-" + person)
                    .isActive(),
        TIMEOUT);
    clickWhenActive(context, "btn-save-people");
    waitForScreen(context, QuestScreen.class);
  }

  /** Waits until the button is enabled, then clicks it. */
  private static void clickWhenActive(ClientGameTestContext context, String id) {
    context.waitFor(
        client ->
            client.gui.screen() instanceof DisquestsBaseScreen screen
                && screen.getRootComponent() != null
                && screen.getRootComponent().childById(ButtonComponent.class, id) != null
                && screen.getRootComponent().childById(ButtonComponent.class, id).isActive(),
        TIMEOUT);
    click(context, id);
  }

  /**
   * Picks a visibility in the editor ({@code vis-private}, {@code vis-closed}, {@code vis-open})
   * and waits until it shows as the current one (pressed); it is sent with Save.
   */
  private static void selectVisibility(ClientGameTestContext context, String id) {
    click(context, id);
    context.waitFor(
        client ->
            client.gui.screen() instanceof DisquestsBaseScreen screen
                && screen.getRootComponent() != null
                && screen.getRootComponent().childById(ButtonComponent.class, id) != null
                && !screen.getRootComponent().childById(ButtonComponent.class, id).isActive(),
        TIMEOUT);
  }
}
