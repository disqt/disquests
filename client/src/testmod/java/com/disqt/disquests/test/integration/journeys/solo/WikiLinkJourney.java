package com.disqt.disquests.test.integration.journeys.solo;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import io.wispforest.owo.ui.container.FlowLayout;
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
@DisplayName("Wiki-Link Journey")
class WikiLinkJourney {

    @BeforeAll
    static void resetServer() throws Exception {
        resetServerAndSync();
        AbortOnFailureExtension.clearFailures();
    }

    // --- helpers ---

    private void createQuest(ClientGameTestContext context, String title) {
        openMainScreen(context);
        click(context, "btn-new-quest");
        waitForScreen(context, QuestScreen.class);
        type(context, "title-field", title);
        click(context, "btn-save");
        waitForViewMode(context);
        click(context, "btn-close");
        waitForScreen(context, MainScreen.class);
    }

    /**
     * Wait for QuestScreen view mode (tag-display present, tag-editor absent).
     */
    private void waitForViewMode(ClientGameTestContext context) {
        context.waitFor(client -> {
            if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
            var root = dScreen.getRootComponent();
            if (root == null) return false;
            return root.childById(FlowLayout.class, "tag-display") != null;
        }, TIMEOUT);
        context.waitTicks(2);
    }

    /**
     * Wait for QuestScreen edit mode (tag-editor present).
     */
    private void waitForEditMode(ClientGameTestContext context) {
        context.waitFor(client -> {
            if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
            var root = dScreen.getRootComponent();
            if (root == null) return false;
            return root.childById(FlowLayout.class, "tag-editor") != null;
        }, TIMEOUT);
        context.waitTicks(2);
    }

    /**
     * Read the text of the content-field in edit mode.
     */
    private String readContentField(ClientGameTestContext context) {
        return context.computeOnClient(c -> {
            if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return null;
            var root = dScreen.getRootComponent();
            if (root == null) return null;
            var field = root.childById(
                com.disqt.disquests.client.gui.component.TextFieldComponent.class, "content-field");
            return field != null ? field.getText() : null;
        });
    }

    // --- tests ---

    @Test @Order(1) @PlayerA
    @DisplayName("Create two quests: Link Source and Link Target")
    void createTwoQuests(ClientGameTestContext context) {
        given("player is connected to the server");
        when("player creates quest 'Link Target'");
            createQuest(context, "Link Target");
        then("Link Target appears in My Quests");
            openMainScreen(context);
            waitForEntryCount(context, 1);

        when("player creates quest 'Link Source'");
            createQuest(context, "Link Source");
        then("both quests appear in My Quests");
            openMainScreen(context);
            waitForEntryCount(context, 2);
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Add wiki-link syntax in content and save")
    void addWikiLinkInContent(ClientGameTestContext context) {
        given("player is on MainScreen with two quests");
            openMainScreen(context);
            waitForEntryCount(context, 2);

        when("player opens 'Link Source' in edit mode");
            clickEntryByTitle(context, "Link Source");
            click(context, "btn-open");
            waitForViewMode(context);
            click(context, "btn-edit");
            waitForEditMode(context);

        and("types wiki-link syntax in the content field");
            type(context, "content-field", "See also [[Link Target]] for more info.");

        and("clicks Save");
            click(context, "btn-save");

        then("view mode loads without error");
            waitForViewMode(context);
            assertScreenIs(context, QuestScreen.class);
            assertLabelText(context, "title-label", "Link Source");
    }

    @Test @Order(3) @PlayerA
    @DisplayName("View mode renders content area after wiki-link save")
    void viewModeRendersContentArea(ClientGameTestContext context) {
        given("'Link Source' quest is open in view mode");
            assertScreenIs(context, QuestScreen.class);

        then("content-area component is present (MarkdownWidget rendered successfully)");
            assertComponentExists(context, "content-area");

        and("quest title is still correct");
            assertLabelText(context, "title-label", "Link Source");
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Wiki-link content survives round-trip (edit -> save -> edit)")
    void wikiLinkRoundTrip(ClientGameTestContext context) {
        given("'Link Source' is in view mode after save");
            assertScreenIs(context, QuestScreen.class);

        when("player re-enters edit mode");
            click(context, "btn-edit");
            waitForEditMode(context);

        then("content field still contains the wiki-link text");
            String content = readContentField(context);
            assertNotNull(content, "Content field should be readable");
            assertTrue(content.contains("[[Link Target]]"),
                "Content should contain '[[Link Target]]', got: " + content);

        and("player cancels to return to view mode");
            click(context, "btn-cancel");
            waitForViewMode(context);
    }

    @Test @Order(5) @PlayerA
    @DisplayName("Content with only wiki-link syntax saves and view mode is stable")
    void contentOnlyWikiLink(ClientGameTestContext context) {
        given("player returns to MainScreen");
            click(context, "btn-close");
            waitForScreen(context, MainScreen.class);
            openMainScreen(context);
            waitForEntryCount(context, 2);

        when("player opens 'Link Source' in edit mode");
            clickEntryByTitle(context, "Link Source");
            click(context, "btn-open");
            waitForViewMode(context);
            click(context, "btn-edit");
            waitForEditMode(context);

        and("replaces content with a bare wiki-link");
            type(context, "content-field", "[[Link Target]]");
            click(context, "btn-save");

        then("view mode loads without crash");
            waitForViewMode(context);
            assertScreenIs(context, QuestScreen.class);
            assertComponentExists(context, "content-area");

        and("quest is still in cache");
            context.waitFor(client ->
                ClientCache.getMyQuests().stream()
                    .anyMatch(q -> "Link Source".equals(q.getTitle())),
                TIMEOUT
            );
    }
}
