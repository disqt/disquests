package com.disqt.disquests.test.integration.journeys.solo;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

@IntegrationTest
@DisplayName("Search And Filter Journey")
class SearchAndFilterJourney {

    @BeforeAll
    static void resetServer() throws Exception {
        resetServerAndSync();
        AbortOnFailureExtension.clearFailures();
    }

    // Helper: create a quest with a given title and visibility cycle count
    // visibilityCycles: 0=PRIVATE, 1=CLOSED, 2=OPEN
    private void createQuestWithVisibility(ClientGameTestContext context, String title, int visibilityCycles) {
        openMainScreen(context);
        click(context, "btn-new-quest");
        waitForScreen(context, QuestScreen.class);
        type(context, "title-field", title);
        // Cycle visibility (starts at PRIVATE)
        for (int i = 0; i < visibilityCycles; i++) {
            click(context, "btn-visibility");
            waitForScreen(context, QuestScreen.class);
        }
        click(context, "btn-save");
        waitForScreen(context, QuestScreen.class);
        click(context, "btn-close");
        waitForScreen(context, MainScreen.class);
    }

    @Test @Order(1) @PlayerA
    @DisplayName("Create quest Alpha with OPEN visibility")
    void createAlpha(ClientGameTestContext context) {
        given("player is connected to the server");
        when("player creates quest Alpha with OPEN visibility");
            // PRIVATE -> CLOSED -> OPEN = 2 cycles
            createQuestWithVisibility(context, "Alpha", 2);
        then("Alpha appears in My Quests");
            waitForEntryCount(context, 1);
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Create quest Beta with CLOSED visibility")
    void createBeta(ClientGameTestContext context) {
        when("player creates quest Beta with CLOSED visibility");
            // PRIVATE -> CLOSED = 1 cycle
            createQuestWithVisibility(context, "Beta", 1);
        then("Beta appears in My Quests alongside Alpha");
            waitForEntryCount(context, 2);
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Create quest Gamma with PRIVATE visibility")
    void createGamma(ClientGameTestContext context) {
        when("player creates quest Gamma with PRIVATE visibility (default)");
            createQuestWithVisibility(context, "Gamma", 0);
        then("Gamma appears in My Quests");
            waitForEntryCount(context, 3);
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Switch to Quest Board tab and verify OPEN/CLOSED quests visible")
    void switchToQuestBoard(ClientGameTestContext context) {
        given("player is on MainScreen with 3 quests");
            openMainScreen(context);
        when("player clicks Quest Board tab");
            click(context, "tab-quest-board");
            context.waitTicks(4);
        then("filter row appears");
            assertComponentExists(context, "filter-row");
        and("Alpha (OPEN) and Beta (CLOSED) are visible (Gamma is PRIVATE, not shown)");
            waitForEntryCount(context, 2);
    }

    @Test @Order(5) @PlayerA
    @DisplayName("Click Open filter - only Alpha shown")
    void filterOpen(ClientGameTestContext context) {
        when("player clicks the Open filter");
            click(context, "filter-open");
            context.waitTicks(2);
        then("only Alpha (OPEN) is shown");
            waitForEntryCount(context, 1);
    }

    @Test @Order(6) @PlayerA
    @DisplayName("Click Closed filter - only Beta shown")
    void filterClosed(ClientGameTestContext context) {
        when("player clicks the Closed filter");
            click(context, "filter-closed");
            context.waitTicks(2);
        then("only Beta (CLOSED) is shown");
            waitForEntryCount(context, 1);
    }

    @Test @Order(7) @PlayerA
    @DisplayName("Click All filter - both Alpha and Beta shown")
    void filterAll(ClientGameTestContext context) {
        when("player clicks the All filter");
            click(context, "filter-all");
            context.waitTicks(2);
        then("both Alpha (OPEN) and Beta (CLOSED) are shown");
            waitForEntryCount(context, 2);
    }

    @Test @Order(8) @PlayerA
    @DisplayName("Search for Alpha - only Alpha shown")
    void searchAlpha(ClientGameTestContext context) {
        when("player types 'Alpha' in the search box");
            type(context, "search-box", "Alpha");
            context.waitTicks(2);
        then("only Alpha is shown");
            waitForEntryCount(context, 1);
    }

    @Test @Order(9) @PlayerA
    @DisplayName("Clear search - both quests return")
    void clearSearch(ClientGameTestContext context) {
        when("player clears the search box");
            // Click the search box and select-all+delete to clear it
            click(context, "search-box");
            context.waitTicks(1);
            context.getInput().holdControl();
            context.getInput().pressKey(GLFW.GLFW_KEY_A);
            context.getInput().releaseControl();
            context.getInput().pressKey(GLFW.GLFW_KEY_DELETE);
            context.waitTicks(2);
        then("both Alpha and Beta are shown again");
            waitForEntryCount(context, 2);
    }

    @Test @Order(10) @PlayerA
    @DisplayName("Switch back to My Quests tab - all 3 quests shown, filter row hidden")
    void switchToMyQuests(ClientGameTestContext context) {
        when("player clicks My Quests tab");
            click(context, "tab-my-quests");
            context.waitTicks(4);
        then("all 3 quests are shown in My Quests");
            waitForEntryCount(context, 3);
        and("filter row is zero-sized (hidden on My Quests tab)");
            assertComponent(context, "filter-row",
                io.wispforest.owo.ui.core.UIComponent.class,
                c -> c.width() == 0 && c.height() == 0,
                "filter-row should be hidden on My Quests tab");
    }
}
