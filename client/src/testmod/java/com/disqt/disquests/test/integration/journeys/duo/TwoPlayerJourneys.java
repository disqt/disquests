package com.disqt.disquests.test.integration.journeys.duo;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.screen.ConfirmScreen;
import com.disqt.disquests.client.gui.screen.ContributorScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import com.disqt.disquests.test.integration.harness.TwoPlayerJourney;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * All two-player journeys in one class with @Nested inner classes.
 * One @BeforeAll, one TwoPlayerSyncExtension barrier -- no cross-class signal races.
 * Order ranges: Collaboration 1-9, OpenQuest 10-19, LiveUpdate 20-29.
 */
@TwoPlayerJourney
@DisplayName("Two-Player Journeys")
class TwoPlayerJourneys {

    // =========================================================================
    // Collaboration: request, accept, permissions, remove
    // =========================================================================
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Collaboration Journey")
    class Collaboration {

        @Test @Order(1) @PlayerA
        @DisplayName("A creates CLOSED quest")
        void a_creates_closed_quest(ClientGameTestContext context) {
            given("PlayerA is connected to the server");
            context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);

            when("PlayerA opens MainScreen and creates a new quest");
                openMainScreen(context);
                click(context, "btn-new-quest");
                waitForScreen(context, QuestScreen.class);

            and("PlayerA types a title");
                type(context, "title-field", "Collab Quest");

            and("PlayerA cycles visibility to CLOSED (PRIVATE -> CLOSED)");
                click(context, "btn-visibility");
                assertButtonText(context, "btn-visibility", "CLOSED");

            and("PlayerA saves the quest");
                click(context, "btn-save");
                waitForScreen(context, QuestScreen.class);

            then("quest is saved in view mode");
                assertLabelText(context, "title-label", "Collab Quest");

            and("PlayerA closes the quest");
                click(context, "btn-close");
                waitForScreen(context, MainScreen.class);
                assertEntryCount(context, 1);

            PhaseSync.signal("collab-quest-created");
        }

        @Test @Order(2) @PlayerB
        @DisplayName("B finds quest on board and sends request")
        void b_finds_and_requests(ClientGameTestContext context) {
            given("PlayerB is connected to the server");
            context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);

            when("PlayerB waits for quest to be created");
                PhaseSync.waitFor("collab-quest-created", context);

            and("PlayerB opens MainScreen and switches to Quest Board");
                openMainScreen(context);
                click(context, "tab-quest-board");

            then("Collab Quest is visible on the Quest Board");
                waitForQuestByTitle(context, "Collab Quest", false);

            when("PlayerB selects the quest and clicks Request");
                clickEntryByTitle(context, "Collab Quest");
                click(context, "btn-request");

            then("button shows Requested");
                assertButtonText(context, "btn-request", "Requested");

            PhaseSync.signal("collab-request-sent");
        }

        @Test @Order(3) @PlayerA
        @DisplayName("A sees pending request and accepts")
        void a_accepts_request(ClientGameTestContext context) {
            when("PlayerA waits for request to be sent");
                PhaseSync.waitFor("collab-request-sent", context);

            and("PlayerA waits for pending request to arrive");
                context.waitFor(client -> ClientCache.getPendingCount(
                    ClientCache.getMyQuests().stream()
                        .filter(q -> "Collab Quest".equals(q.getTitle()))
                        .findFirst().map(q -> q.getId()).orElse(null)) > 0,
                    TIMEOUT);

            and("PlayerA opens MainScreen and selects the quest");
                openMainScreen(context);
                clickEntryByTitle(context, "Collab Quest");
                click(context, "btn-open");
                waitForScreen(context, QuestScreen.class);

            and("PlayerA enters edit mode");
                click(context, "btn-edit");
                waitForScreen(context, QuestScreen.class);

            then("contributors button shows pending count");
                assertButtonText(context, "btn-contributors", "pending");

            when("PlayerA clicks Contributors");
                click(context, "btn-contributors");
                waitForScreen(context, ContributorScreen.class);

            then("ContributorScreen shows pending request from PlayerB");
                boolean hasPending = context.computeOnClient(c -> {
                    Screen screen = c.currentScreen;
                    if (screen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                        var root = dScreen.getRootComponent();
                        if (root == null) return false;
                        FlowLayout pendingList = root.childById(FlowLayout.class, "pending-list");
                        return pendingList != null && !pendingList.children().isEmpty();
                    }
                    return false;
                });
                assertTrue(hasPending, "Expected pending requests in ContributorScreen");

            when("PlayerA clicks Accept on the first pending request");
                double[] acceptPos = context.computeOnClient(c -> {
                    Screen screen = c.currentScreen;
                    if (screen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                        var root = dScreen.getRootComponent();
                        if (root == null) throw new AssertionError("Root not found");
                        FlowLayout pendingList = root.childById(FlowLayout.class, "pending-list");
                        if (pendingList == null || pendingList.children().isEmpty())
                            throw new AssertionError("pending-list is empty");
                        var firstRow = pendingList.children().get(0);
                        if (!(firstRow instanceof FlowLayout rowLayout))
                            throw new AssertionError("First pending row is not a FlowLayout");
                        ButtonComponent acceptBtn = rowLayout.children().stream()
                            .filter(child -> child instanceof ButtonComponent)
                            .map(child -> (ButtonComponent) child)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Accept button not found in row"));
                        return new double[]{acceptBtn.x() + acceptBtn.width() / 2.0, acceptBtn.y() + acceptBtn.height() / 2.0};
                    }
                    throw new AssertionError("Not a Disquests screen");
                });
                double scale = context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
                context.getInput().setCursorPos(acceptPos[0] * scale, acceptPos[1] * scale);
                context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.waitTicks(2);
                waitForScreen(context, ContributorScreen.class);

            then("PlayerB appears in the contributor list");
                boolean hasContrib = context.computeOnClient(c -> {
                    Screen screen = c.currentScreen;
                    if (screen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                        var root = dScreen.getRootComponent();
                        if (root == null) return false;
                        FlowLayout contribList = root.childById(FlowLayout.class, "contributor-list");
                        return contribList != null && !contribList.children().isEmpty();
                    }
                    return false;
                });
                assertTrue(hasContrib, "Expected contributor in list after accept");

            PhaseSync.signal("collab-accepted");
        }

        @Test @Order(4) @PlayerB
        @DisplayName("B sees quest in My Quests after accept")
        void b_sees_quest_in_my_quests(ClientGameTestContext context) {
            when("PlayerB waits for request to be accepted");
                PhaseSync.waitFor("collab-accepted", context);

            and("PlayerB waits for quest to appear in My Quests");
                waitForQuestByTitle(context, "Collab Quest", true);

            then("quest appears in PlayerB's My Quests");
                openMainScreen(context);
                waitForEntryCount(context, 1);
                assertEntryCount(context, 1);
        }

        @Test @Order(5) @PlayerA
        @DisplayName("A toggles permission and removes contributor")
        void a_manages_contributor(ClientGameTestContext context) {
            given("PlayerA is still on ContributorScreen");
                waitForScreen(context, ContributorScreen.class);

            when("PlayerA toggles contributor's permission");
                double[] permPos = context.computeOnClient(c -> {
                    Screen screen = c.currentScreen;
                    if (screen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                        var root = dScreen.getRootComponent();
                        if (root == null) throw new AssertionError("Root not found");
                        FlowLayout contribList = root.childById(FlowLayout.class, "contributor-list");
                        if (contribList == null || contribList.children().isEmpty())
                            throw new AssertionError("contributor-list is empty");
                        var firstRow = contribList.children().get(0);
                        if (!(firstRow instanceof FlowLayout rowLayout))
                            throw new AssertionError("First contributor row is not a FlowLayout");
                        ButtonComponent permBtn = rowLayout.children().stream()
                            .filter(child -> child instanceof ButtonComponent)
                            .map(child -> (ButtonComponent) child)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Permission button not found"));
                        return new double[]{permBtn.x() + permBtn.width() / 2.0, permBtn.y() + permBtn.height() / 2.0};
                    }
                    throw new AssertionError("Not a Disquests screen");
                });
                double scale = context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
                context.getInput().setCursorPos(permPos[0] * scale, permPos[1] * scale);
                context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.waitTicks(4);
                waitForScreen(context, ContributorScreen.class);

            when("PlayerA clicks Remove on the contributor");
                double[] removePos = context.computeOnClient(c -> {
                    Screen screen = c.currentScreen;
                    if (screen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                        var root = dScreen.getRootComponent();
                        if (root == null) throw new AssertionError("Root not found");
                        FlowLayout contribList = root.childById(FlowLayout.class, "contributor-list");
                        if (contribList == null || contribList.children().isEmpty())
                            throw new AssertionError("contributor-list is empty");
                        var firstRow = contribList.children().get(0);
                        if (!(firstRow instanceof FlowLayout rowLayout))
                            throw new AssertionError("First contributor row is not a FlowLayout");
                        var buttons = rowLayout.children().stream()
                            .filter(child -> child instanceof ButtonComponent)
                            .map(child -> (ButtonComponent) child)
                            .toList();
                        if (buttons.size() < 2) throw new AssertionError("Expected 2 buttons in contributor row");
                        ButtonComponent removeBtn = buttons.get(1);
                        return new double[]{removeBtn.x() + removeBtn.width() / 2.0, removeBtn.y() + removeBtn.height() / 2.0};
                    }
                    throw new AssertionError("Not a Disquests screen");
                });
                double scale2 = context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
                context.getInput().setCursorPos(removePos[0] * scale2, removePos[1] * scale2);
                context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.waitTicks(2);

            then("ConfirmScreen appears");
                waitForScreen(context, ConfirmScreen.class);

            when("PlayerA confirms removal");
                click(context, "btn-yes");
                waitForScreen(context, ContributorScreen.class);

            then("contributor list is empty");
                boolean isEmpty = context.computeOnClient(c -> {
                    Screen screen = c.currentScreen;
                    if (screen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                        var root = dScreen.getRootComponent();
                        if (root == null) return true;
                        FlowLayout contribList = root.childById(FlowLayout.class, "contributor-list");
                        return contribList == null || contribList.children().isEmpty();
                    }
                    return true;
                });
                assertTrue(isEmpty, "Expected contributor list to be empty after removal");
        }
    }

    // =========================================================================
    // OpenQuest: join, view, leave
    // =========================================================================
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Open Quest Journey")
    class OpenQuest {

        @Test @Order(1) @PlayerA
        @DisplayName("A creates OPEN quest")
        void a_creates_open_quest(ClientGameTestContext context) {
            given("PlayerA is connected to the server");
            context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);

            when("PlayerA opens MainScreen and creates a new quest");
                openMainScreen(context);
                click(context, "btn-new-quest");
                waitForScreen(context, QuestScreen.class);

            and("PlayerA types a title");
                type(context, "title-field", "Open Quest");

            and("PlayerA cycles visibility twice to reach OPEN");
                click(context, "btn-visibility");
                click(context, "btn-visibility");
                assertButtonText(context, "btn-visibility", "OPEN");

            and("PlayerA saves the quest");
                click(context, "btn-save");
                waitForScreen(context, QuestScreen.class);

            then("quest is saved in view mode");
                assertLabelText(context, "title-label", "Open Quest");

            and("PlayerA closes the quest");
                click(context, "btn-close");
                waitForScreen(context, MainScreen.class);

            PhaseSync.signal("open-quest-created");
        }

        @Test @Order(2) @PlayerB
        @DisplayName("B joins the OPEN quest")
        void b_joins_quest(ClientGameTestContext context) {
            given("PlayerB is connected to the server");
            context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);

            when("PlayerB waits for quest to be created");
                PhaseSync.waitFor("open-quest-created", context);

            and("PlayerB opens MainScreen and switches to Quest Board");
                openMainScreen(context);
                click(context, "tab-quest-board");

            then("Open Quest is visible on the Quest Board");
                waitForQuestByTitle(context, "Open Quest", false);

            when("PlayerB selects the quest and clicks Join");
                clickEntryByTitle(context, "Open Quest");
                click(context, "btn-join");

            then("quest appears in PlayerB's My Quests");
                waitForQuestByTitle(context, "Open Quest", true);

            PhaseSync.signal("open-quest-joined");
        }

        @Test @Order(3) @PlayerB
        @DisplayName("B opens quest, views content, and leaves")
        void b_opens_and_leaves(ClientGameTestContext context) {
            given("PlayerB has joined Open Quest");

            when("PlayerB opens MainScreen");
                openMainScreen(context);
                waitForQuestByTitle(context, "Open Quest", true);

            and("PlayerB selects the quest and opens it");
                clickEntryByTitle(context, "Open Quest");
                click(context, "btn-open");
                waitForScreen(context, QuestScreen.class);

            then("quest content is visible in view mode");
                assertLabelText(context, "title-label", "Open Quest");

            when("PlayerB clicks Leave");
                click(context, "btn-leave");

            then("ConfirmScreen appears");
                waitForScreen(context, ConfirmScreen.class);

            when("PlayerB confirms leave");
                click(context, "btn-yes");

            then("quest is removed from PlayerB's My Quests");
                waitForScreen(context, MainScreen.class);
                assertEntryCount(context, 0);
        }
    }

    // =========================================================================
    // LiveUpdate: S2C update and delete propagation
    // =========================================================================
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Live Update Journey")
    class LiveUpdate {

        @Test @Order(1) @PlayerA
        @DisplayName("A creates OPEN quest with Original Title")
        void a_creates_quest(ClientGameTestContext context) {
            given("PlayerA is connected to the server");
            context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);

            when("PlayerA opens MainScreen and creates a new quest");
                openMainScreen(context);
                click(context, "btn-new-quest");
                waitForScreen(context, QuestScreen.class);

            and("PlayerA types title 'Original Title'");
                type(context, "title-field", "Original Title");

            and("PlayerA cycles visibility twice to OPEN");
                click(context, "btn-visibility");
                click(context, "btn-visibility");
                assertButtonText(context, "btn-visibility", "OPEN");

            and("PlayerA saves the quest");
                click(context, "btn-save");
                waitForScreen(context, QuestScreen.class);

            then("quest is saved with Original Title");
                assertLabelText(context, "title-label", "Original Title");

            and("PlayerA closes the quest");
                click(context, "btn-close");
                waitForScreen(context, MainScreen.class);

            PhaseSync.signal("live-created");
        }

        @Test @Order(2) @PlayerB
        @DisplayName("B joins the quest")
        void b_joins_quest(ClientGameTestContext context) {
            given("PlayerB is connected to the server");
            context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);

            when("PlayerB waits for quest to be created");
                PhaseSync.waitFor("live-created", context);

            and("PlayerB opens Quest Board and finds the quest");
                openMainScreen(context);
                click(context, "tab-quest-board");
                waitForQuestByTitle(context, "Original Title", false);

            and("PlayerB selects and joins the quest");
                clickEntryByTitle(context, "Original Title");
                click(context, "btn-join");

            then("quest appears in PlayerB's My Quests");
                waitForQuestByTitle(context, "Original Title", true);

            PhaseSync.signal("live-joined");
        }

        @Test @Order(3) @PlayerA
        @DisplayName("A edits quest title to Updated Title")
        void a_edits_title(ClientGameTestContext context) {
            when("PlayerA waits for PlayerB to join");
                PhaseSync.waitFor("live-joined", context);

            and("PlayerA opens MainScreen and selects the quest");
                openMainScreen(context);
                clickEntryByTitle(context, "Original Title");
                click(context, "btn-open");
                waitForScreen(context, QuestScreen.class);

            and("PlayerA enters edit mode");
                click(context, "btn-edit");
                waitForScreen(context, QuestScreen.class);

            and("PlayerA changes the title to 'Updated Title'");
                type(context, "title-field", "Updated Title");

            and("PlayerA saves");
                click(context, "btn-save");
                waitForScreen(context, QuestScreen.class);

            then("A sees Updated Title in view mode");
                assertLabelText(context, "title-label", "Updated Title");

            PhaseSync.signal("live-updated");
        }

        @Test @Order(4) @PlayerB
        @DisplayName("B sees Updated Title via S2C UPDATE_QUEST")
        void b_sees_update(ClientGameTestContext context) {
            when("PlayerB waits for title update");
                PhaseSync.waitFor("live-updated", context);

            and("PlayerB waits for S2C UPDATE_QUEST to arrive");
                context.waitFor(client ->
                    ClientCache.getMyQuests().stream()
                        .anyMatch(q -> "Updated Title".equals(q.getTitle())),
                    TIMEOUT);

            then("PlayerB's My Quests list shows Updated Title");
                openMainScreen(context);
                waitForQuestByTitle(context, "Updated Title", true);
                boolean hasUpdatedTitle = context.computeOnClient(c -> {
                    Screen screen = c.currentScreen;
                    if (screen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                        var root = dScreen.getRootComponent();
                        if (root == null) return false;
                        var questList = root.childById(FlowLayout.class, "quest-list");
                        if (questList == null || questList.children().isEmpty()) return false;
                        return questList.children().stream()
                            .filter(child -> child instanceof com.disqt.disquests.client.gui.component.QuestEntryComponent)
                            .map(child -> (com.disqt.disquests.client.gui.component.QuestEntryComponent) child)
                            .anyMatch(entry -> "Updated Title".equals(entry.getQuest().getTitle()));
                    }
                    return false;
                });
                assertTrue(hasUpdatedTitle, "My Quests list should contain an entry with Updated Title");
        }

        @Test @Order(5) @PlayerA
        @DisplayName("A deletes the quest")
        void a_deletes_quest(ClientGameTestContext context) {
            given("PlayerA is on QuestScreen in view mode");
                waitForScreen(context, QuestScreen.class);

            when("PlayerA clicks Delete");
                click(context, "btn-delete");
                waitForScreen(context, ConfirmScreen.class);

            and("PlayerA confirms deletion");
                click(context, "btn-yes");
                waitForScreen(context, MainScreen.class);

            then("quest is removed from PlayerA's My Quests");
                context.waitFor(client ->
                    ClientCache.getMyQuests().stream()
                        .noneMatch(q -> "Updated Title".equals(q.getTitle())),
                    TIMEOUT);

            PhaseSync.signal("live-deleted");
        }

        @Test @Order(6) @PlayerB
        @DisplayName("B sees quest removed via S2C DELETE_QUEST")
        void b_sees_deletion(ClientGameTestContext context) {
            when("PlayerB waits for quest deletion");
                PhaseSync.waitFor("live-deleted", context);

            and("PlayerB waits for S2C DELETE_QUEST to arrive");
                context.waitFor(client ->
                    ClientCache.getMyQuests().stream()
                        .noneMatch(q -> "Updated Title".equals(q.getTitle())),
                    TIMEOUT);

            then("PlayerB's My Quests is empty");
                openMainScreen(context);
                assertEntryCount(context, 0);
        }
    }
}
