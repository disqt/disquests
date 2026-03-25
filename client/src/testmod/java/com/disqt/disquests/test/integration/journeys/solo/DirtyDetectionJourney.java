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

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Dirty Detection Journey")
class DirtyDetectionJourney {

    @BeforeAll
    static void resetServer() throws Exception {
        resetServerAndSync();
        AbortOnFailureExtension.clearFailures();
    }

    @Test @Order(1) @PlayerA
    @DisplayName("Create quest with title 'Original' and save")
    void createAndSaveQuest(ClientGameTestContext context) {
        given("player is connected and on My Quests tab");
            openMainScreen(context);
            assertScreenIs(context, MainScreen.class);

        when("player creates a new quest titled 'Original'");
            click(context, "btn-new-quest");
            waitForScreen(context, QuestScreen.class);
            type(context, "title-field", "Original");
            click(context, "btn-save");

        then("view mode shows title 'Original'");
            waitForScreen(context, QuestScreen.class);
            assertLabelText(context, "title-label", "Original");
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Edit mode: change title to 'Modified', click Cancel -> confirm overlay appears")
    void editAndCancelShowsConfirm(ClientGameTestContext context) {
        given("quest 'Original' is open in view mode");

        when("player clicks Edit");
            click(context, "btn-edit");
            waitForScreen(context, QuestScreen.class);

        and("player changes title to 'Modified'");
            type(context, "title-field", "Modified");

        and("player clicks Cancel");
            click(context, "btn-cancel");

        then("confirm overlay appears asking to discard changes");
            waitForOverlay(context, "confirm-overlay");
            assertOverlayVisible(context, "confirm-overlay");
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Click No on confirm overlay -> returns to edit with title still 'Modified'")
    void clickNoReturnsToEdit(ClientGameTestContext context) {
        given("confirm overlay is showing");
            assertOverlayVisible(context, "confirm-overlay");

        when("player clicks No (don't discard)");
            click(context, "btn-confirm-no");

        then("returns to QuestScreen in edit mode");
            waitForScreen(context, QuestScreen.class);

        and("title field still shows 'Modified'");
            // Verify the screen is editing (not view mode) by checking that title-field exists
            // and checking via the screen's isEditing state
            boolean isEditing = context.computeOnClient(c ->
                c.currentScreen instanceof QuestScreen qs && qs.isEditing());
            assertTrue(isEditing, "Should be back in edit mode after clicking No");

            // The title field should still contain 'Modified' (dirty state preserved)
            String titleText = context.computeOnClient(c -> {
                if (c.currentScreen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                    var root = dScreen.getRootComponent();
                    if (root == null) return null;
                    var field = root.childById(
                        com.disqt.disquests.client.gui.component.TextFieldComponent.class, "title-field");
                    return field != null ? field.getText() : null;
                }
                return null;
            });
            assertEquals("Modified", titleText,
                "Title field should still contain 'Modified' after clicking No");
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Click Cancel again -> confirm overlay appears again")
    void cancelAgainShowsConfirmAgain(ClientGameTestContext context) {
        given("player is back in edit mode with title 'Modified'");
            boolean isEditing = context.computeOnClient(c ->
                c.currentScreen instanceof QuestScreen qs && qs.isEditing());
            assertTrue(isEditing, "Should be in edit mode");

        when("player clicks Cancel again");
            click(context, "btn-cancel");

        then("confirm overlay appears again");
            waitForOverlay(context, "confirm-overlay");
            assertOverlayVisible(context, "confirm-overlay");
    }

    @Test @Order(5) @PlayerA
    @DisplayName("Click Yes on confirm overlay -> view mode shows original title 'Original'")
    void clickYesDiscardsChanges(ClientGameTestContext context) {
        given("confirm overlay is showing");
            assertOverlayVisible(context, "confirm-overlay");

        when("player clicks Yes (discard)");
            click(context, "btn-confirm-yes");

        then("returns to QuestScreen in view mode");
            waitForScreen(context, QuestScreen.class);
            boolean isViewing = context.computeOnClient(c ->
                c.currentScreen instanceof QuestScreen qs && !qs.isEditing());
            assertTrue(isViewing, "Should be in view mode after discarding");

        and("title label shows original value 'Original'");
            assertLabelText(context, "title-label", "Original");
    }

    @Test @Order(6) @PlayerA
    @DisplayName("Edit with no changes, Cancel goes straight to view mode (no confirm overlay)")
    void noChangesNoConfirmDialog(ClientGameTestContext context) {
        given("quest 'Original' is shown in view mode");
            assertScreenIs(context, QuestScreen.class);
            boolean isViewing = context.computeOnClient(c ->
                c.currentScreen instanceof QuestScreen qs && !qs.isEditing());
            assertTrue(isViewing, "Should be in view mode");

        when("player clicks Edit");
            click(context, "btn-edit");
            waitForScreen(context, QuestScreen.class);

        and("player makes no changes and clicks Cancel");
            click(context, "btn-cancel");

        then("no confirm overlay appears -- goes directly back to view mode");
            assertNoOverlay(context, "confirm-overlay");

        and("QuestScreen shows in view mode");
            assertScreenIs(context, QuestScreen.class);
            boolean isViewMode = context.computeOnClient(c ->
                c.currentScreen instanceof QuestScreen qs && !qs.isEditing());
            assertTrue(isViewMode, "Should be in view mode after cancelling with no changes");

        and("title label still shows 'Original'");
            assertLabelText(context, "title-label", "Original");
    }
}
