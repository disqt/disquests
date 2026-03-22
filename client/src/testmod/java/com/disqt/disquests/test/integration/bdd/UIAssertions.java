package com.disqt.disquests.test.integration.bdd;

import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.UIComponent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI assertion helpers. All assertions run on the client thread
 * via computeOnClient to ensure thread safety.
 */
public final class UIAssertions {

    private UIAssertions() {}

    /**
     * Assert a condition on a component found by ID.
     */
    public static <T extends UIComponent> void assertComponent(
            ClientGameTestContext context, String componentId, Class<T> type, Predicate<T> predicate, String message) {
        boolean result = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) return false;
                T component = root.childById(type, componentId);
                if (component == null) return false;
                return predicate.test(component);
            }
            return false;
        });
        assertTrue(result, message);
    }

    /**
     * Assert that a label component has the expected text.
     */
    public static void assertLabelText(ClientGameTestContext context, String componentId, String expected) {
        String actual = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) return null;
                LabelComponent label = root.childById(LabelComponent.class, componentId);
                if (label == null) return null;
                return label.text().getString();
            }
            return null;
        });
        assertNotNull(actual, "Label '" + componentId + "' not found");
        assertEquals(expected, actual, "Label '" + componentId + "' text mismatch");
    }

    /**
     * Assert that a button component has text containing the expected substring.
     */
    public static void assertButtonText(ClientGameTestContext context, String componentId, String expectedSubstring) {
        String actual = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) return null;
                ButtonComponent btn = root.childById(ButtonComponent.class, componentId);
                if (btn == null) return null;
                return btn.getMessage().getString();
            }
            return null;
        });
        assertNotNull(actual, "Button '" + componentId + "' not found");
        assertTrue(actual.contains(expectedSubstring),
            "Button '" + componentId + "' text '" + actual + "' does not contain '" + expectedSubstring + "'");
    }

    /**
     * Assert a component exists (non-null).
     */
    public static void assertComponentExists(ClientGameTestContext context, String componentId) {
        boolean exists = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) return false;
                return root.childById(UIComponent.class, componentId) != null;
            }
            return false;
        });
        assertTrue(exists, "Component '" + componentId + "' not found");
    }

    /**
     * Assert a component does NOT exist (was removed or hidden).
     */
    public static void assertComponentMissing(ClientGameTestContext context, String componentId) {
        boolean exists = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) return false;
                return root.childById(UIComponent.class, componentId) != null;
            }
            return false;
        });
        assertFalse(exists, "Component '" + componentId + "' should not exist but was found");
    }

    /**
     * Assert the current screen is the expected type.
     */
    public static void assertScreenIs(ClientGameTestContext context, Class<? extends Screen> screenClass) {
        boolean match = context.computeOnClient(c -> screenClass.isInstance(c.currentScreen));
        assertTrue(match, "Expected screen " + screenClass.getSimpleName() + " but got different screen");
    }

    /**
     * Assert the quest list has exactly N entries.
     */
    public static void assertEntryCount(ClientGameTestContext context, int expected) {
        int actual = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) return -1;
                var questList = root.childById(
                    io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
                return questList != null ? questList.children().size() : -1;
            }
            return -1;
        });
        assertEquals(expected, actual, "Quest list entry count mismatch");
    }

    /**
     * Wait for a condition with timeout, then assert it's true.
     */
    public static void waitAndAssert(ClientGameTestContext context, String description,
                                      java.util.function.Predicate<net.minecraft.client.MinecraftClient> condition) {
        context.waitFor(condition::test, UIActions.TIMEOUT);
    }
}
