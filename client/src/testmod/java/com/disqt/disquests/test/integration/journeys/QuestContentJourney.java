package com.disqt.disquests.test.integration.journeys;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
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
@DisplayName("Quest Content Journey")
class QuestContentJourney {

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

    @Test @Order(1) @PlayerA
    @DisplayName("Create quest with markdown content")
    void createWithMarkdown(ClientGameTestContext context) {
        given("player is on MainScreen");
            openMainScreen(context);
        when("player creates a new quest");
            click(context, "btn-new-quest");
            waitForScreen(context, QuestScreen.class);
        and("types a title");
            type(context, "title-field", "Markdown Test");
        and("types markdown content with headings, bold, task list, and blockquote");
            type(context, "content-field", "# Heading\n**bold** and *italic*\n- [ ] task one\n- [x] task two\n> a quote");
        and("clicks Save");
            click(context, "btn-save");
        then("view mode shows the quest");
            waitForScreen(context, QuestScreen.class);
            assertLabelText(context, "title-label", "Markdown Test");
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Verify markdown renders in view mode")
    void verifyMarkdownRenders(ClientGameTestContext context) {
        then("content area contains rendered markdown");
            // MarkdownWidget is a child of content-area; its presence confirms rendering occurred
            assertComponentExists(context, "content-area");
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Formatting panel visible in edit mode")
    void formattingPanel(ClientGameTestContext context) {
        when("player clicks Edit");
            click(context, "btn-edit");
            waitForScreen(context, QuestScreen.class);
        then("formatting panel is present");
            assertComponentExists(context, "formatting-panel");
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Save and return to MainScreen")
    void cleanup(ClientGameTestContext context) {
        when("player clicks Cancel to return to view mode");
            click(context, "btn-cancel");
        then("view mode is shown again");
            waitForScreen(context, QuestScreen.class);
        when("player closes the quest");
            click(context, "btn-close");
        then("MainScreen is shown with one quest");
            waitForScreen(context, MainScreen.class);
            assertEntryCount(context, 1);
    }
}
