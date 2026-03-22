package com.disqt.disquests.test.integration.journeys;

import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
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
@DisplayName("Undo Redo Journey")
class UndoRedoJourney {

    @BeforeAll
    static void resetServer() {
        resetServerAndSync();
        AbortOnFailureExtension.clearFailures();
    }

    /** Read the text from the content-field TextFieldComponent. */
    private String getContentFieldText(ClientGameTestContext context) {
        return context.computeOnClient(c -> {
            if (c.currentScreen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) return null;
                TextFieldComponent field = root.childById(TextFieldComponent.class, "content-field");
                return field != null ? field.getText() : null;
            }
            return null;
        });
    }

    @Test @Order(1) @PlayerA
    @DisplayName("Create quest and enter edit mode")
    void createQuestAndEnterEditMode(ClientGameTestContext context) {
        given("player is connected and on My Quests tab");
            openMainScreen(context);
            assertScreenIs(context, MainScreen.class);

        when("player creates a new quest");
            click(context, "btn-new-quest");
            waitForScreen(context, QuestScreen.class);
            type(context, "title-field", "Undo Test Quest");

        then("QuestScreen is open in edit mode with empty content field");
            // The content field starts empty for a new quest
            String contentText = getContentFieldText(context);
            assertNotNull(contentText, "content-field should be present in edit mode");
            // Content is initially empty or blank for new quest
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Type 'Hello' in content field")
    void typeHello(ClientGameTestContext context) {
        given("player is in edit mode with empty content field");

        when("player types 'Hello' in content field");
            // Use type() which clears field then types new text
            type(context, "content-field", "Hello");

        then("content field shows 'Hello'");
            String text = getContentFieldText(context);
            assertEquals("Hello", text, "Content field should contain 'Hello'");
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Ctrl+Z reverts content to empty")
    void undoClearsHello(ClientGameTestContext context) {
        given("content field contains 'Hello'");
        when("player presses Ctrl+Z repeatedly to undo all 5 characters");
            // Click field to ensure focus
            click(context, "content-field");
            context.waitTicks(1);
            // Each typeChars character creates a separate undo action,
            // so we need 5 undos for "Hello" (5 chars)
            undoN(context, 5);

        then("content field reverts to empty");
            String text = getContentFieldText(context);
            assertNotNull(text, "content-field should still be present");
            assertTrue(text.isEmpty(), "Content field should be empty after undo, was: '" + text + "'");
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Ctrl+Y redoes 'Hello'")
    void redoRestoresHello(ClientGameTestContext context) {
        given("content field is empty after undo");
        when("player presses Ctrl+Y repeatedly to redo all 5 characters");
            click(context, "content-field");
            context.waitTicks(1);
            redoN(context, 5);

        then("content field shows 'Hello' again");
            String text = getContentFieldText(context);
            assertEquals("Hello", text, "Content field should contain 'Hello' after redo");
    }

    @Test @Order(5) @PlayerA
    @DisplayName("Type ' World' to append, field shows 'Hello World'")
    void appendWorld(ClientGameTestContext context) {
        given("content field shows 'Hello'");
        when("player appends ' World' to the content field");
            appendText(context, "content-field", " World");

        then("content field shows 'Hello World'");
            String text = getContentFieldText(context);
            assertEquals("Hello World", text,
                "Content field should contain 'Hello World' after appending ' World'");
    }

    @Test @Order(6) @PlayerA
    @DisplayName("Ctrl+Z reverts to 'Hello'")
    void undoWorld(ClientGameTestContext context) {
        given("content field shows 'Hello World'");
        when("player presses Ctrl+Z to undo ' World' (6 chars)");
            click(context, "content-field");
            context.waitTicks(1);
            // " World" is 6 characters, so 6 undos
            undoN(context, 6);

        then("content field reverts to 'Hello'");
            String text = getContentFieldText(context);
            assertEquals("Hello", text,
                "Content field should show 'Hello' after undoing ' World'");
    }

    @Test @Order(7) @PlayerA
    @DisplayName("Ctrl+Z again reverts to empty")
    void undoHello(ClientGameTestContext context) {
        given("content field shows 'Hello'");
        when("player presses Ctrl+Z to undo 'Hello' (5 chars)");
            click(context, "content-field");
            context.waitTicks(1);
            undoN(context, 5);

        then("content field is empty");
            String text = getContentFieldText(context);
            assertNotNull(text, "content-field should still be present");
            assertTrue(text.isEmpty(),
                "Content field should be empty after undoing 'Hello', was: '" + text + "'");
    }
}
