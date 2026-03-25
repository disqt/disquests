package com.disqt.disquests.test.integration.journeys.duo;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.screen.ContributorScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
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
 * All two-player journeys in one flat class.
 * Order ranges: Collaboration 1-5, OpenQuest 10-12.
 */
@IntegrationTest
@DisplayName("Two-Player Journeys")
class TwoPlayerJourneys {

    // =========================================================================
    // Collaboration: request, accept, permissions, remove
    // =========================================================================

    // Collaboration: A creates(1), B requests(1), A accepts(2), B verifies(2), A manages(3)
    @Test @Order(1) @PlayerA
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

    @Test @Order(1) @PlayerB
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
            click(context, "btn-request");

        then("button shows Requested");
            assertButtonText(context, "btn-request", "Requested");

        PhaseSync.signal("collab-request-sent");
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Collab: A sees pending request and accepts")
    void collab_a_accepts_request(ClientGameTestContext context) {
        when("PlayerA waits for request to be sent");
            PhaseSync.waitFor("collab-request-sent", context);

        and("PlayerA waits for pending request to arrive");
            waitForQuestCondition(context, "Collab Quest", true,
                q -> ClientCache.getPendingCount(q.getId()) > 0);

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
            // Wait for server to process accept and send back updated quest with contributor
            waitForQuestCondition(context, "Collab Quest", true,
                q -> !q.getContributors().isEmpty());

        and("PlayerA reopens ContributorScreen with updated data");
            // ContributorScreen doesn't auto-refresh, so reopen it via quest edit mode
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

    @Test @Order(2) @PlayerB
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

    @Test @Order(3) @PlayerA
    @DisplayName("Collab: A toggles permission and removes contributor")
    void collab_a_manages_contributor(ClientGameTestContext context) {
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

        then("confirm overlay appears");
            waitForOverlay(context, "confirm-overlay");

        when("PlayerA confirms removal");
            click(context, "btn-confirm-yes");
            waitForScreen(context, ContributorScreen.class);

        then("contributor list is empty");
            // Wait for server to process removal
            waitForQuestCondition(context, "Collab Quest", true,
                q -> q.getContributors().isEmpty());
    }

    // =========================================================================
    // OpenQuest: join, view, leave
    // =========================================================================

    @Test @Order(10) @PlayerA
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

    @Test @Order(10) @PlayerB
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
            click(context, "btn-join");

        then("quest appears in PlayerB's My Quests");
            waitForQuestByTitle(context, "Open Quest", true);

        PhaseSync.signal("open-quest-joined");
    }

    @Test @Order(11) @PlayerB
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
            waitForOverlay(context, "confirm-overlay");

        when("PlayerB confirms leave");
            click(context, "btn-confirm-yes");

        then("quest is removed from PlayerB's My Quests");
            waitForScreen(context, MainScreen.class);
            assertEntryCount(context, 0);
    }
}
