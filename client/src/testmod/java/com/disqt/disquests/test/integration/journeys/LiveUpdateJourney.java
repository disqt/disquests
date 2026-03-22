package com.disqt.disquests.test.integration.journeys;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.screen.ConfirmScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import com.disqt.disquests.test.integration.harness.TwoPlayerJourney;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@TwoPlayerJourney
@DisplayName("Live Update Journey")
class LiveUpdateJourney {

    // Step 1: A creates an OPEN quest with "Original Title"
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

        and("PlayerA cycles visibility twice to OPEN (PRIVATE -> CLOSED -> OPEN)");
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

    // Step 2: B waits, opens Quest Board, joins the quest
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
            waitForEntryCount(context, 1);

        and("PlayerB selects and joins the quest");
            clickEntry(context, 0);
            click(context, "btn-join");

        then("quest appears in PlayerB's My Quests");
            waitForQuestByTitle(context, "Original Title", true);

        PhaseSync.signal("live-joined");
    }

    // Step 3: A waits, edits title to "Updated Title", saves
    @Test @Order(3) @PlayerA
    @DisplayName("A edits quest title to Updated Title")
    void a_edits_title(ClientGameTestContext context) {
        when("PlayerA waits for PlayerB to join");
            PhaseSync.waitFor("live-joined", context);

        and("PlayerA opens MainScreen and selects the quest");
            openMainScreen(context);
            waitForEntryCount(context, 1);
            clickEntry(context, 0);
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

    // Step 4: B waits, opens MainScreen, verifies entry shows "Updated Title"
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
            waitForEntryCount(context, 1);
            // Verify via entry titles
            String entryTitle = context.computeOnClient(c -> {
                net.minecraft.client.gui.screen.Screen screen = c.currentScreen;
                if (screen instanceof com.disqt.disquests.client.gui.screen.DisquestsBaseScreen dScreen) {
                    var root = dScreen.getRootComponent();
                    if (root == null) return null;
                    var questList = root.childById(
                        io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
                    if (questList == null || questList.children().isEmpty()) return null;
                    var first = questList.children().get(0);
                    if (first instanceof com.disqt.disquests.client.gui.component.QuestEntryComponent entry) {
                        return entry.getQuest().getTitle();
                    }
                    return null;
                }
                return null;
            });
            assertEquals("Updated Title", entryTitle, "My Quests entry should show Updated Title");
    }

    // Step 5: A deletes the quest
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
            assertEntryCount(context, 0);

        PhaseSync.signal("live-deleted");
    }

    // Step 6: B waits, verifies quest removed from My Quests
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
