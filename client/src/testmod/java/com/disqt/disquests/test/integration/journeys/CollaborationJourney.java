package com.disqt.disquests.test.integration.journeys;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.screen.ConfirmScreen;
import com.disqt.disquests.client.gui.screen.ContributorScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Collaboration Journey")
class CollaborationJourney {

    @BeforeAll
    static void resetServer() {
        resetLocalState();
        AbortOnFailureExtension.clearFailures();
    }

    // Step 1: A creates a CLOSED quest and signals B
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
            // Verify visibility shows CLOSED
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

    // Step 2: B waits, opens Quest Board, finds the quest, sends a request
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
            waitForEntryCount(context, 1);

        when("PlayerB selects the quest and clicks Request");
            clickEntry(context, 0);
            click(context, "btn-request");

        then("button shows Requested");
            assertButtonText(context, "btn-request", "Requested");

        PhaseSync.signal("collab-request-sent");
    }

    // Step 3: A waits, opens the quest in edit mode, verifies pending, opens ContributorScreen, accepts
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
            waitForEntryCount(context, 1);
            clickEntry(context, 0);
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
            // Verify pending-list has at least one child row
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
            // Accept button is first ButtonComponent in the first child row of pending-list
            double[] acceptPos = context.computeOnClient(c -> {
                Screen screen = c.currentScreen;
                if (screen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                    var root = dScreen.getRootComponent();
                    if (root == null) throw new AssertionError("Root not found");
                    FlowLayout pendingList = root.childById(FlowLayout.class, "pending-list");
                    if (pendingList == null || pendingList.children().isEmpty())
                        throw new AssertionError("pending-list is empty");
                    // First row in pending-list
                    var firstRow = pendingList.children().get(0);
                    if (!(firstRow instanceof FlowLayout rowLayout))
                        throw new AssertionError("First pending row is not a FlowLayout");
                    // Find first ButtonComponent (Accept)
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

            // Screen rebuilds after accept -- wait for ContributorScreen to reopen
            waitForScreen(context, ContributorScreen.class);

        then("PlayerB appears in the contributor list");
            // contributor-list should now have one entry
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

    // Step 4: B waits, verifies quest appears in My Quests
    @Test @Order(4) @PlayerB
    @DisplayName("B sees quest in My Quests after accept")
    void b_sees_quest_in_my_quests(ClientGameTestContext context) {
        when("PlayerB waits for request to be accepted");
            PhaseSync.waitFor("collab-accepted", context);

        and("PlayerB waits for quest to appear in My Quests");
            waitForQuestByTitle(context, "Collab Quest", true);

        then("quest appears in PlayerB's My Quests");
            openMainScreen(context);
            // My Quests tab is shown by default
            waitForEntryCount(context, 1);
            assertEntryCount(context, 1);
    }

    // Step 5: A toggles permission and removes contributor
    @Test @Order(5) @PlayerA
    @DisplayName("A toggles permission and removes contributor")
    void a_manages_contributor(ClientGameTestContext context) {
        given("PlayerA is still on ContributorScreen");
            waitForScreen(context, ContributorScreen.class);

        when("PlayerA toggles contributor's permission");
            // Permission toggle button is first ButtonComponent in the contributor row
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
                    // First button = permission toggle
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
            // Screen rebuilds after permission toggle
            waitForScreen(context, ContributorScreen.class);

        then("contributor list still has the entry (permission toggled)");
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
            assertTrue(hasContrib, "Expected contributor still in list after permission toggle");

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
                    // Second button = Remove
                    var buttons = rowLayout.children().stream()
                        .filter(child -> child instanceof ButtonComponent)
                        .map(child -> (ButtonComponent) child)
                        .toList();
                    if (buttons.size() < 2) throw new AssertionError("Expected 2 buttons in contributor row, got " + buttons.size());
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
