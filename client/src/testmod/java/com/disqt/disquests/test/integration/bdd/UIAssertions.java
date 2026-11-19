package com.disqt.disquests.test.integration.bdd;

import static org.junit.jupiter.api.Assertions.*;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;

/**
 * UI assertion helpers. All assertions run on the client thread via computeOnClient to ensure
 * thread safety.
 */
public final class UIAssertions {

  private UIAssertions() {}

  /** Assert a condition on a component found by ID. */
  public static <T extends UIComponent> void assertComponent(
      ClientGameTestContext context,
      String componentId,
      Class<T> type,
      Predicate<T> predicate,
      String message) {
    T component = UIActions.findComponentOrNull(context, type, componentId);
    boolean result = component != null && context.computeOnClient(c -> predicate.test(component));
    assertTrue(result, message);
  }

  /** Assert that a label component has the expected text. */
  public static void assertLabelText(
      ClientGameTestContext context, String componentId, String expected) {
    LabelComponent label = UIActions.findComponent(context, LabelComponent.class, componentId);
    String actual = context.computeOnClient(c -> label.text().getString());
    assertEquals(expected, actual, "Label '" + componentId + "' text mismatch");
  }

  /** Assert that a button component has text containing the expected substring. */
  public static void assertButtonText(
      ClientGameTestContext context, String componentId, String expectedSubstring) {
    ButtonComponent btn = UIActions.findComponent(context, ButtonComponent.class, componentId);
    String actual = context.computeOnClient(c -> btn.getMessage().getString());
    assertTrue(
        actual.contains(expectedSubstring),
        "Button '"
            + componentId
            + "' text '"
            + actual
            + "' does not contain '"
            + expectedSubstring
            + "'");
  }

  /** Assert a component exists (non-null). */
  public static void assertComponentExists(ClientGameTestContext context, String componentId) {
    UIComponent comp = UIActions.findComponentOrNull(context, UIComponent.class, componentId);
    assertNotNull(comp, "Component '" + componentId + "' not found");
  }

  /** Assert a component does NOT exist (was removed or hidden). */
  public static void assertComponentMissing(ClientGameTestContext context, String componentId) {
    UIComponent comp = UIActions.findComponentOrNull(context, UIComponent.class, componentId);
    assertNull(comp, "Component '" + componentId + "' should not exist but was found");
  }

  /** Assert the current screen is the expected type. */
  public static void assertScreenIs(
      ClientGameTestContext context, Class<? extends Screen> screenClass) {
    boolean match = context.computeOnClient(c -> screenClass.isInstance(c.gui.screen()));
    assertTrue(
        match, "Expected screen " + screenClass.getSimpleName() + " but got different screen");
  }

  /** Assert the quest list has exactly N entries. */
  public static void assertEntryCount(ClientGameTestContext context, int expected) {
    var questList =
        UIActions.findComponentOrNull(
            context, io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
    int actual = questList != null ? context.computeOnClient(c -> questList.children().size()) : -1;
    assertEquals(expected, actual, "Quest list entry count mismatch");
  }

  /** Asserts the current screen's quest list does (or does not) show an entry, right now. */
  public static void assertListed(ClientGameTestContext context, String title, boolean listed) {
    boolean found = context.computeOnClient(c -> UIActions.listedEntry(c, title) != null);
    assertEquals(listed, found, "'" + title + "' listed");
  }

  /**
   * The text a component shows: a label's text, a button's message or a checkbox's label; null when
   * the component is missing or shows no text.
   */
  public static String textOf(net.minecraft.client.Minecraft client, String componentId) {
    UIComponent c = UIActions.onScreen(client, UIComponent.class, componentId);
    if (c instanceof LabelComponent label) return label.text().getString();
    if (c instanceof ButtonComponent button) return button.getMessage().getString();
    if (c instanceof io.wispforest.owo.ui.component.SmallCheckboxComponent box) {
      return box.label().getString();
    }
    return null;
  }

  /**
   * Every label's text inside a container (the container included when it is a label), one per
   * line, in order; null when the container is missing.
   */
  public static String textUnder(net.minecraft.client.Minecraft client, String componentId) {
    UIComponent root = UIActions.onScreen(client, UIComponent.class, componentId);
    if (root == null) return null;
    StringBuilder text = new StringBuilder();
    collectText(root, text);
    return text.toString();
  }

  private static void collectText(UIComponent c, StringBuilder text) {
    if (c instanceof LabelComponent label) text.append(label.text().getString()).append('\n');
    if (c instanceof io.wispforest.owo.ui.core.ParentUIComponent parent) {
      for (UIComponent child : parent.children()) collectText(child, text);
    }
  }

  /** Waits until the component's text (see {@link #textOf}) satisfies the condition. */
  public static void waitForText(
      ClientGameTestContext context, String componentId, Predicate<String> condition) {
    String[] last = {null};
    try {
      context.waitFor(
          client -> {
            last[0] = textOf(client, componentId);
            return last[0] != null && condition.test(last[0]);
          },
          UIActions.TIMEOUT);
    } catch (Throwable t) {
      throw new AssertionError("Text of '" + componentId + "' was '" + last[0] + "'", t);
    }
  }

  /** Waits until the labels inside a container (see {@link #textUnder}) satisfy the condition. */
  public static void waitForTextUnder(
      ClientGameTestContext context, String componentId, Predicate<String> condition) {
    String[] last = {null};
    try {
      context.waitFor(
          client -> {
            last[0] = textUnder(client, componentId);
            return last[0] != null && condition.test(last[0]);
          },
          UIActions.TIMEOUT);
    } catch (Throwable t) {
      throw new AssertionError("Text under '" + componentId + "' was '" + last[0] + "'", t);
    }
  }

  /**
   * The text of the shown (or queued) system toast with this id, its lines joined by spaces, or
   * null when there is none. The message is private to {@code SystemToast}: read by reflection.
   */
  public static String toastText(
      net.minecraft.client.Minecraft client,
      net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId id) {
    var toast =
        client
            .gui
            .toastManager()
            .getToast(net.minecraft.client.gui.components.toasts.SystemToast.class, id);
    if (toast == null) return null;
    try {
      var field =
          net.minecraft.client.gui.components.toasts.SystemToast.class.getDeclaredField(
              "messageLines");
      field.setAccessible(true);
      StringBuilder text = new StringBuilder();
      for (Object line : (java.util.List<?>) field.get(toast)) {
        if (!text.isEmpty()) text.append(' ');
        ((net.minecraft.util.FormattedCharSequence) line)
            .accept(
                (index, style, codePoint) -> {
                  text.appendCodePoint(codePoint);
                  return true;
                });
      }
      return text.toString();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Cannot read the toast's text", e);
    }
  }

  /** Waits until a system toast with this id shows and its text satisfies the condition. */
  public static void waitForToast(
      ClientGameTestContext context,
      net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId id,
      Predicate<String> condition) {
    String[] last = {null};
    try {
      context.waitFor(
          client -> {
            last[0] = toastText(client, id);
            return last[0] != null && condition.test(last[0]);
          },
          UIActions.TIMEOUT);
    } catch (Throwable t) {
      throw new AssertionError("Toast text was '" + last[0] + "'", t);
    }
  }

  /** Wait for a condition with timeout, then assert it's true. */
  public static void waitAndAssert(
      ClientGameTestContext context,
      String description,
      java.util.function.Predicate<net.minecraft.client.Minecraft> condition) {
    context.waitFor(condition::test, UIActions.TIMEOUT);
  }
}
