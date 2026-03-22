package com.disqt.disquests.test.integration.journeys;

import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
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
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Undo Redo Journey")
class UndoRedoJourney {

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
            // Clear content field first (it may contain the default empty string)
            click(context, "content-field");
            context.waitTicks(1);

        when("player types 'Hello' in content field");
            // Use typeChars directly to avoid the Ctrl+A+Delete clear that 'type()' does,
            // since we want to test undo of typed text specifically.
            // First ensure field is cleared, then type without clearing
            context.getInput().holdControl();
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_A);
            context.getInput().releaseControl();
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE);
            context.waitTicks(1);
            context.getInput().typeChars("Hello");
            context.waitTicks(2);

        then("content field shows 'Hello'");
            String text = getContentFieldText(context);
            assertEquals("Hello", text, "Content field should contain 'Hello'");
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Ctrl+Z reverts content to empty")
    void undoClearsHello(ClientGameTestContext context) {
        given("content field contains 'Hello'");
        when("player presses Ctrl+Z (undo)");
            // Click field to ensure focus
            click(context, "content-field");
            context.waitTicks(1);
            undo(context);

        then("content field reverts to empty");
            String text = getContentFieldText(context);
            // After undo, the typed text should be removed.
            // MultiLineTextFieldWidget's UndoManager undoes the last edit.
            assertNotNull(text, "content-field should still be present");
            assertTrue(text.isEmpty(), "Content field should be empty after undo, was: '" + text + "'");
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Ctrl+Y redoes 'Hello'")
    void redoRestoresHello(ClientGameTestContext context) {
        given("content field is empty after undo");
        when("player presses Ctrl+Y (redo)");
            click(context, "content-field");
            context.waitTicks(1);
            redo(context);

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
        when("player presses Ctrl+Z (undo)");
            click(context, "content-field");
            context.waitTicks(1);
            undo(context);

        then("content field reverts to 'Hello'");
            String text = getContentFieldText(context);
            assertEquals("Hello", text,
                "Content field should show 'Hello' after undoing ' World'");
    }

    @Test @Order(7) @PlayerA
    @DisplayName("Ctrl+Z again reverts to empty")
    void undoHello(ClientGameTestContext context) {
        given("content field shows 'Hello'");
        when("player presses Ctrl+Z again");
            click(context, "content-field");
            context.waitTicks(1);
            undo(context);

        then("content field is empty");
            String text = getContentFieldText(context);
            assertNotNull(text, "content-field should still be present");
            assertTrue(text.isEmpty(),
                "Content field should be empty after undoing 'Hello', was: '" + text + "'");
    }
}
