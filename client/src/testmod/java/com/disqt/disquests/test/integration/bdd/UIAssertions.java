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
        T component = UIActions.findComponentOrNull(context, type, componentId);
        boolean result = component != null && context.computeOnClient(c -> predicate.test(component));
        assertTrue(result, message);
    }

    /**
     * Assert that a label component has the expected text.
     */
    public static void assertLabelText(ClientGameTestContext context, String componentId, String expected) {
        LabelComponent label = UIActions.findComponent(context, LabelComponent.class, componentId);
        String actual = context.computeOnClient(c -> label.text().getString());
        assertEquals(expected, actual, "Label '" + componentId + "' text mismatch");
    }

    /**
     * Assert that a button component has text containing the expected substring.
     */
    public static void assertButtonText(ClientGameTestContext context, String componentId, String expectedSubstring) {
        ButtonComponent btn = UIActions.findComponent(context, ButtonComponent.class, componentId);
        String actual = context.computeOnClient(c -> btn.getMessage().getString());
        assertTrue(actual.contains(expectedSubstring),
            "Button '" + componentId + "' text '" + actual + "' does not contain '" + expectedSubstring + "'");
    }

    /**
     * Assert a component exists (non-null).
     */
    public static void assertComponentExists(ClientGameTestContext context, String componentId) {
        UIComponent comp = UIActions.findComponentOrNull(context, UIComponent.class, componentId);
        assertTrue(comp != null, "Component '" + componentId + "' not found");
    }

    /**
     * Assert a component does NOT exist (was removed or hidden).
     */
    public static void assertComponentMissing(ClientGameTestContext context, String componentId) {
        UIComponent comp = UIActions.findComponentOrNull(context, UIComponent.class, componentId);
        assertFalse(comp != null, "Component '" + componentId + "' should not exist but was found");
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
        var questList = UIActions.findComponentOrNull(context,
            io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
        int actual = questList != null ? context.computeOnClient(c -> questList.children().size()) : -1;
        assertEquals(expected, actual, "Quest list entry count mismatch");
    }

    /**
     * Assert that a confirm overlay is currently visible on the Disquests screen.
     */
    public static void assertOverlayVisible(ClientGameTestContext context, String overlayId) {
        boolean visible = context.computeOnClient(client -> {
            if (client.currentScreen instanceof DisquestsBaseScreen screen) {
                return screen.getRootComponent().childById(UIComponent.class, overlayId) != null;
            }
            return false;
        });
        assertTrue(visible, "Expected overlay '" + overlayId + "' to be visible");
    }

    /**
     * Assert that no confirm overlay is present on the current screen.
     */
    public static void assertNoOverlay(ClientGameTestContext context, String overlayId) {
        boolean absent = context.computeOnClient(client -> {
            if (client.currentScreen instanceof DisquestsBaseScreen screen) {
                return screen.getRootComponent().childById(UIComponent.class, overlayId) == null;
            }
            return true;
        });
        assertTrue(absent, "Expected no overlay '" + overlayId + "'");
    }

    /**
     * Wait for a condition with timeout, then assert it's true.
     */
    public static void waitAndAssert(ClientGameTestContext context, String description,
                                      java.util.function.Predicate<net.minecraft.client.MinecraftClient> condition) {
        context.waitFor(condition::test, UIActions.TIMEOUT);
    }
}
