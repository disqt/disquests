package com.disqt.disquests.test.integration.journeys;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.screen.ConfirmScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.PlayerB;
import com.disqt.disquests.test.integration.harness.RconClient;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

@IntegrationTest
@DisplayName("Open Quest Journey")
class OpenQuestJourney {

    @BeforeAll
    static void resetServer() throws Exception {
        var rcon = new RconClient("localhost",
            Integer.parseInt(System.getProperty("disquests.test.rcon.port", "25575")));
        rcon.login(System.getProperty("disquests.test.rcon.password", "testpassword"));
        rcon.command("disquests reset");
        rcon.close();
        AbortOnFailureExtension.clearFailures();
        // Wait for server re-handshake to complete
        Thread.sleep(1000);
    }

    // Step 1: A creates an OPEN quest (PRIVATE -> CLOSED -> OPEN)
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

        and("PlayerA cycles visibility twice to reach OPEN (PRIVATE -> CLOSED -> OPEN)");
            click(context, "btn-visibility");
            click(context, "btn-visibility");
            assertButtonText(context, "btn-visibility", "OPEN");

        and("PlayerA saves the quest");
            click(context, "btn-save");
            waitForScreen(context, QuestScreen.class);

        then("quest is saved in view mode with OPEN visibility");
            assertLabelText(context, "title-label", "Open Quest");

        and("PlayerA closes the quest");
            click(context, "btn-close");
            waitForScreen(context, MainScreen.class);
            assertEntryCount(context, 1);

        PhaseSync.signal("open-quest-created");
    }

    // Step 2: B waits, opens Quest Board, finds the quest, clicks Join
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
            waitForEntryCount(context, 1);

        when("PlayerB selects the quest");
            clickEntry(context, 0);

        then("Join button is active for an OPEN quest");
            assertComponentExists(context, "btn-join");

        when("PlayerB clicks Join");
            click(context, "btn-join");

        then("quest appears in PlayerB's My Quests");
            waitForQuestByTitle(context, "Open Quest", true);

        PhaseSync.signal("open-quest-joined");
    }

    // Step 3: B opens the quest from My Quests, verifies content, leaves via ConfirmScreen
    @Test @Order(3) @PlayerB
    @DisplayName("B opens quest, views content, and leaves")
    void b_opens_and_leaves(ClientGameTestContext context) {
        given("PlayerB has joined Open Quest");

        when("PlayerB opens MainScreen and switches to My Quests");
            openMainScreen(context);
            // My Quests tab is default
            waitForQuestByTitle(context, "Open Quest", true);
            waitForEntryCount(context, 1);

        and("PlayerB selects the quest and opens it");
            clickEntry(context, 0);
            click(context, "btn-open");
            waitForScreen(context, QuestScreen.class);

        then("quest content is visible in view mode");
            assertLabelText(context, "title-label", "Open Quest");

        when("PlayerB clicks Leave");
            click(context, "btn-leave");

        then("ConfirmScreen appears asking to confirm leave");
            waitForScreen(context, ConfirmScreen.class);

        when("PlayerB confirms leave by clicking Yes");
            click(context, "btn-yes");

        then("quest is removed from PlayerB's My Quests");
            waitForScreen(context, MainScreen.class);
            // Wait for the quest to be removed from cache
            waitForQuestRemoved(context,
                context.computeOnClient(c ->
                    com.disqt.disquests.client.ClientCache.getServerQuests().stream()
                        .filter(q -> "Open Quest".equals(q.getTitle()))
                        .findFirst().map(q -> q.getId()).orElse(null)
                ));
            assertEntryCount(context, 0);

        and("quest is still visible on the Quest Board");
            click(context, "tab-quest-board");
            waitForQuestByTitle(context, "Open Quest", false);
            waitForEntryCount(context, 1);
    }
}
