package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.KeyBinds;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public abstract class DisquestsBaseScreen extends BaseUIModelScreen<FlowLayout> {

  public static final String CONFIRM_OVERLAY_ID = "confirm-overlay";
  public static final String CONFIRM_YES_ID = "btn-confirm-yes";
  public static final String CONFIRM_NO_ID = "btn-confirm-no";

  private static final java.util.Deque<Screen> backStack = new java.util.ArrayDeque<>();
  private static final java.util.Deque<Screen> forwardStack = new java.util.ArrayDeque<>();
  private static final int MAX_HISTORY = 20;

  @Nullable protected final Screen parent;

  protected DisquestsBaseScreen(DataSource source, @Nullable Screen parent) {
    super(FlowLayout.class, source);
    this.parent = parent;
  }

  public static void clearHistory() {
    backStack.clear();
    forwardStack.clear();
  }

  @Override
  public void close() {
    if (this.client != null) {
      if (parent == null) {
        clearHistory();
      }
      this.client.setScreen(parent);
    }
  }

  @Override
  public boolean mouseClicked(Click click, boolean simulated) {
    if (click.button() == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_4) {
      goBack();
      return true;
    } else if (click.button() == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_5) {
      goForward();
      return true;
    }
    return super.mouseClicked(click, simulated);
  }

  private void goBack() {
    if (this.client == null || backStack.isEmpty()) return;
    forwardStack.push(this);
    if (forwardStack.size() > MAX_HISTORY) forwardStack.removeLast();
    this.client.setScreen(backStack.pop());
  }

  private void goForward() {
    if (this.client == null || forwardStack.isEmpty()) return;
    backStack.push(this);
    if (backStack.size() > MAX_HISTORY) backStack.removeLast();
    this.client.setScreen(forwardStack.pop());
  }

  @Override
  public boolean keyPressed(KeyInput keyInput) {
    // Toggle: pressing the open-GUI key while a Disquests screen is open closes it.
    // Check that no text field is focused (avoid closing while typing).
    if (KeyBinds.openGuiKey.matchesKey(keyInput)) {
      boolean textFieldFocused = false;
      if (this.uiAdapter != null) {
        var focused = this.uiAdapter.rootComponent.focusHandler().focused();
        textFieldFocused = focused instanceof GreedyInputUIComponent;
      }
      if (!textFieldFocused) {
        if (this.client != null) {
          this.client.setScreen(null);
        }
        return true;
      }
    }

    // BaseOwoScreen skips GreedyInputUIComponent routing when Ctrl is held,
    // which prevents Ctrl+A, Ctrl+Z, Ctrl+Y etc from reaching text fields.
    // Route ALL key events to the focused greedy component first.
    if (this.uiAdapter != null) {
      var focused = this.uiAdapter.rootComponent.focusHandler().focused();
      if (focused instanceof GreedyInputUIComponent inputComponent) {
        if (inputComponent.onKeyPress(keyInput)) {
          return true;
        }
      }
    }
    return super.keyPressed(keyInput);
  }

  @Override
  public boolean charTyped(CharInput charInput) {
    if (this.uiAdapter != null) {
      var focused = this.uiAdapter.rootComponent.focusHandler().focused();
      if (focused instanceof GreedyInputUIComponent inputComponent) {
        if (inputComponent.onCharTyped(charInput)) {
          return true;
        }
      }
    }
    return super.charTyped(charInput);
  }

  /**
   * Wires the back arrow button (btn-back) to close this screen. Call from each screen's build()
   * after the root is available.
   */
  protected void wireBackButton(FlowLayout root) {
    ButtonComponent backBtn = root.childById(ButtonComponent.class, "btn-back");
    if (backBtn != null) {
      backBtn.onPress(ignored -> this.close());
    }
  }

  protected void applyThemeRoot(FlowLayout root) {
    root.surface(DisquestsClient.CONFIG.theme().rootSurface());
  }

  protected void applyThemePanel(ParentUIComponent component) {
    component.surface(DisquestsClient.CONFIG.theme().panelSurface());
  }

  /** Returns the parent screen used for back-navigation, or null if none. */
  @Nullable
  public Screen getParentScreen() {
    return parent;
  }

  /**
   * Exposes the root component for test access. Only valid after the screen has been initialized.
   */
  public FlowLayout getRootComponent() {
    return this.uiAdapter != null ? this.uiAdapter.rootComponent : null;
  }

  @Override
  public boolean shouldPause() {
    return false;
  }

  public void navigateToScreen(Screen screen) {
    if (this.client != null) {
      backStack.push(this);
      if (backStack.size() > MAX_HISTORY) backStack.removeLast();
      forwardStack.clear();
      this.client.setScreen(screen);
    }
  }

  // ===================== CONFIRM OVERLAY =====================

  protected void showConfirmOverlay(Text message, Runnable onConfirm) {
    showConfirmOverlay(message, onConfirm, () -> {});
  }

  protected void showConfirmOverlay(Text message, Runnable onConfirm, Runnable onCancel) {
    if (this.uiAdapter == null) return;

    dismissOverlay();

    LabelComponent messageLabel = UIComponents.label(message);
    messageLabel.shadow(true);
    messageLabel.maxWidth(250);
    messageLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);

    ButtonComponent yesBtn =
        UIComponents.button(
            Text.translatable("gui.disquests.btn.yes"),
            b -> {
              dismissOverlay();
              onConfirm.run();
            });
    yesBtn.id(CONFIRM_YES_ID);
    yesBtn.sizing(Sizing.fixed(60), Sizing.fixed(20));

    ButtonComponent noBtn =
        UIComponents.button(
            Text.translatable("gui.disquests.btn.no"),
            b -> {
              dismissOverlay();
              onCancel.run();
            });
    noBtn.id(CONFIRM_NO_ID);
    noBtn.sizing(Sizing.fixed(60), Sizing.fixed(20));

    FlowLayout buttonRow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
    buttonRow.gap(8);
    buttonRow.child(yesBtn);
    buttonRow.child(noBtn);
    buttonRow.horizontalAlignment(HorizontalAlignment.CENTER);

    FlowLayout panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
    panel.gap(12);
    panel.padding(Insets.of(16));
    panel.surface(Surface.DARK_PANEL);
    panel.horizontalAlignment(HorizontalAlignment.CENTER);
    panel.child(messageLabel);
    panel.child(buttonRow);

    OverlayContainer<FlowLayout> overlay = UIContainers.overlay(panel);
    overlay.id(CONFIRM_OVERLAY_ID);
    overlay.closeOnClick(false);

    this.uiAdapter.rootComponent.child(overlay);
  }

  protected void dismissOverlay() {
    dismissOverlay(CONFIRM_OVERLAY_ID);
  }

  protected void dismissOverlay(String overlayId) {
    if (this.uiAdapter == null) return;
    var existing = this.uiAdapter.rootComponent.childById(OverlayContainer.class, overlayId);
    if (existing != null) {
      existing.remove();
    }
  }
}
