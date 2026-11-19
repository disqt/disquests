package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.KeyBinds;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.SuggestionDropdown;
import com.disqt.disquests.client.gui.helper.Colors;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public abstract class DisquestsBaseScreen extends BaseUIModelScreen<FlowLayout> {

  public static final String CONFIRM_OVERLAY_ID = "confirm-overlay";
  public static final String CONFIRM_YES_ID = "btn-confirm-yes";
  public static final String CONFIRM_NO_ID = "btn-confirm-no";
  public static final String CONFIRM_CHECKBOX_ID = "confirm-checkbox";
  public static final String CONFIRM_EXTRA_ID = "confirm-extra";

  private static final java.util.Deque<NavEntry> backStack = new java.util.ArrayDeque<>();
  private static final java.util.Deque<NavEntry> forwardStack = new java.util.ArrayDeque<>();
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

  public sealed interface NavEntry {
    record QuestNav(UUID questId, boolean editing) implements NavEntry {}

    record MainNav(ClientSession.Tab tab, String searchTerm) implements NavEntry {}
  }

  public abstract NavEntry toNavEntry();

  private static final SystemToast.SystemToastId ARCHIVED_TOAST =
      new SystemToast.SystemToastId(8000L);

  private static final SystemToast.SystemToastId REFUSED_TOAST =
      new SystemToast.SystemToastId(8002L);

  /** A toast like the server's notices, for a change refused on this screen. */
  protected void showRefusedToast(Component text) {
    if (this.minecraft != null) {
      SystemToast.add(
          this.minecraft.gui.toastManager(), REFUSED_TOAST, Component.literal("Disquests"), text);
    }
  }

  /**
   * The cached quest when it was completed after this screen took its copy, else null. Screens that
   * edit a stale copy check this before writing: the server silently drops edits to archived quests
   * and sends no correction, so writing the copy to the cache would show the quest as active again.
   */
  @Nullable
  protected static Quest archivedMeanwhile(UUID questId) {
    Quest cached = ClientCache.getQuestById(questId);
    return cached != null && cached.isCompleted() ? cached : null;
  }

  /** Tells the player their edit was dropped and shows the archived quest instead. */
  protected void showArchivedInstead(@Nullable Screen viewParent, Quest archived) {
    if (this.minecraft != null) {
      SystemToast.add(
          this.minecraft.gui.toastManager(),
          ARCHIVED_TOAST,
          Component.literal("Disquests"),
          Component.translatable("gui.disquests.toast.quest_archived"));
    }
    navigateToScreen(new QuestScreen(viewParent, archived));
  }

  @Nullable
  private Screen rebuildFromEntry(NavEntry entry) {
    if (entry instanceof NavEntry.QuestNav qn) {
      Quest quest = ClientCache.getQuestById(qn.questId());
      if (quest == null) return null;
      // Back into the editor only while the player may still edit and the quest is not locked
      // for them; otherwise the reading view (the locked page for a locked quest).
      boolean edit =
          qn.editing()
              && !quest.isLocked()
              && quest.canEdit(ClientSession.getEffectivePlayerUuid());
      // A quest rebuilt from history exists on the server: never a new quest
      return edit ? QuestScreen.editorFor(this.parent, quest) : new QuestScreen(this.parent, quest);
    } else if (entry instanceof NavEntry.MainNav mn) {
      ClientSession.setActiveTab(mn.tab());
      ClientSession.setSearchTerm(mn.searchTerm());
      return new MainScreen(this.parent);
    }
    return null;
  }

  @Override
  public void onClose() {
    if (this.minecraft != null) {
      if (parent == null) {
        clearHistory();
      }
      this.minecraft.gui.setScreen(parent);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent click, boolean simulated) {
    // A click outside an open suggestion dropdown (and its field) closes it, then goes through.
    if (this.uiAdapter != null) {
      SuggestionDropdown.closeOnClickOutside(this.uiAdapter.rootComponent, click.x(), click.y());
    }
    if (click.button() == InputConstants.MOUSE_BUTTON_4) {
      confirmLeave(this::goBack);
      return true;
    } else if (click.button() == InputConstants.MOUSE_BUTTON_5) {
      confirmLeave(this::goForward);
      return true;
    }
    return super.mouseClicked(click, simulated);
  }

  /**
   * Runs {@code leave}, which takes the player off this screen (back, forward, Escape). A screen
   * holding unsaved changes overrides this to ask first.
   */
  protected void confirmLeave(Runnable leave) {
    leave.run();
  }

  /**
   * Back to {@code screen} as the back button would go back: the history entry this screen was
   * opened from is used up and this screen becomes the forward entry. For a screen that must return
   * to a particular instance rather than one rebuilt from history.
   */
  protected void goBackTo(Screen screen) {
    if (this.minecraft == null) return;
    if (!backStack.isEmpty()) backStack.pop();
    forwardStack.push(this.toNavEntry());
    if (forwardStack.size() > MAX_HISTORY) forwardStack.removeLast();
    this.minecraft.gui.setScreen(screen);
  }

  /** Mouse back: the previous history entry, rebuilt. A screen may return elsewhere instead. */
  protected void goBack() {
    if (this.minecraft == null) return;
    while (!backStack.isEmpty()) {
      NavEntry entry = backStack.pop();
      Screen screen = rebuildFromEntry(entry);
      if (screen != null) {
        forwardStack.push(this.toNavEntry());
        if (forwardStack.size() > MAX_HISTORY) forwardStack.removeLast();
        this.minecraft.gui.setScreen(screen);
        return;
      }
    }
  }

  private void goForward() {
    if (this.minecraft == null) return;
    while (!forwardStack.isEmpty()) {
      NavEntry entry = forwardStack.pop();
      Screen screen = rebuildFromEntry(entry);
      if (screen != null) {
        backStack.push(this.toNavEntry());
        if (backStack.size() > MAX_HISTORY) backStack.removeLast();
        this.minecraft.gui.setScreen(screen);
        return;
      }
    }
  }

  @Override
  public boolean keyPressed(KeyEvent keyInput) {
    // Escape closes an open confirm dialog, not the screen. The overlay's own Escape handler sits
    // on the root and stops firing once a component inside it (the checkbox) has the focus.
    if (keyInput.isEscape() && this.uiAdapter != null) {
      if (this.uiAdapter.rootComponent.childById(OverlayContainer.class, CONFIRM_OVERLAY_ID)
          != null) {
        dismissOverlay();
        return true;
      }
      // Escape while a dropdown's own scrollbar holds the focus closes the dropdown first
      if (SuggestionDropdown.closeIfFocusedInside(
          this.uiAdapter.rootComponent, this.uiAdapter.rootComponent.focusHandler().focused())) {
        return true;
      }
    }
    // Toggle: pressing the open-GUI key while a Disquests screen is open closes it.
    // Check that no text field is focused (avoid closing while typing).
    if (KeyBinds.openGuiKey.matches(keyInput)) {
      boolean textFieldFocused = false;
      if (this.uiAdapter != null) {
        var focused = this.uiAdapter.rootComponent.focusHandler().focused();
        textFieldFocused = focused instanceof GreedyInputUIComponent;
      }
      if (!textFieldFocused) {
        // Same as Escape: a screen with unsaved changes asks first
        confirmLeave(
            () -> {
              if (this.minecraft != null) this.minecraft.gui.setScreen(null);
            });
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
  public boolean charTyped(CharacterEvent charInput) {
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
      backBtn.onPress(ignored -> this.onClose());
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
  public boolean isPauseScreen() {
    return false;
  }

  public void navigateToScreen(Screen screen) {
    if (this.minecraft != null) {
      backStack.push(this.toNavEntry());
      if (backStack.size() > MAX_HISTORY) backStack.removeLast();
      forwardStack.clear();
      this.minecraft.gui.setScreen(screen);
    }
  }

  // ===================== CONFIRM OVERLAY =====================

  protected void showConfirmOverlay(Component message, Runnable onConfirm) {
    showConfirmOverlay(message, onConfirm, () -> {});
  }

  protected void showConfirmOverlay(Component message, Runnable onConfirm, Runnable onCancel) {
    buildConfirmOverlay(message, null, null, ticked -> onConfirm.run(), onCancel);
  }

  /**
   * A confirm dialog with an optional checkbox ({@code confirm-checkbox}), unticked, under the
   * question, and an optional muted line under that ({@code confirm-extra}); Yes passes whether it
   * is ticked (false without one). Null or empty {@code extraLine} / {@code checkboxLabel} leave
   * them out.
   */
  protected void showConfirmOverlay(
      String text,
      @Nullable String extraLine,
      @Nullable String checkboxLabel,
      Consumer<Boolean> onYes) {
    buildConfirmOverlay(
        Component.literal(text),
        extraLine == null || extraLine.isEmpty() ? null : Component.literal(extraLine),
        checkboxLabel == null || checkboxLabel.isEmpty() ? null : Component.literal(checkboxLabel),
        onYes,
        () -> {});
  }

  private void buildConfirmOverlay(
      Component message,
      @Nullable Component extraLine,
      @Nullable Component checkboxLabel,
      Consumer<Boolean> onConfirm,
      Runnable onCancel) {
    if (this.uiAdapter == null) return;

    dismissOverlay();

    LabelComponent messageLabel = UIComponents.label(message);
    messageLabel.shadow(true);
    messageLabel.maxWidth(250);
    messageLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);

    SmallCheckboxComponent checkbox = null;
    if (checkboxLabel != null) {
      checkbox = UIComponents.smallCheckbox(checkboxLabel);
      checkbox.id(CONFIRM_CHECKBOX_ID);
      checkbox.checked(false);
      checkbox.labelShadow(true);
    }
    SmallCheckboxComponent box = checkbox;

    ButtonComponent yesBtn =
        UIComponents.button(
            Component.translatable("gui.disquests.btn.yes"),
            b -> {
              boolean ticked = box != null && box.checked();
              dismissOverlay();
              onConfirm.accept(ticked);
            });
    yesBtn.id(CONFIRM_YES_ID);
    yesBtn.sizing(Sizing.fixed(60), Sizing.fixed(20));

    ButtonComponent noBtn =
        UIComponents.button(
            Component.translatable("gui.disquests.btn.no"),
            b -> {
              dismissOverlay();
              onCancel.run();
            });
    noBtn.id(CONFIRM_NO_ID);
    noBtn.sizing(Sizing.fixed(60), Sizing.fixed(20));

    mountOverlay(messageLabel, checkbox, extraLine, List.of(yesBtn, noBtn));
  }

  /** One answer of a {@link #showChoiceOverlay} dialog: its button id, label and action. */
  public record Choice(String id, Component label, Runnable action) {}

  /**
   * A dialog with the question, an optional muted line ({@code confirm-extra}) and one button per
   * choice, in order; every button closes the dialog before running its action, and Escape closes
   * it without running any.
   */
  protected void showChoiceOverlay(
      Component message, @Nullable Component extraLine, List<Choice> choices) {
    if (this.uiAdapter == null) return;
    dismissOverlay();

    LabelComponent messageLabel = UIComponents.label(message);
    messageLabel.shadow(true);
    messageLabel.maxWidth(250);
    messageLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);

    List<ButtonComponent> buttons = new java.util.ArrayList<>();
    for (Choice choice : choices) {
      ButtonComponent button =
          UIComponents.button(
              choice.label(),
              b -> {
                dismissOverlay();
                choice.action().run();
              });
      button.id(choice.id());
      button.horizontalSizing(Sizing.content(4));
      button.verticalSizing(Sizing.fixed(20));
      buttons.add(button);
    }
    mountOverlay(messageLabel, null, extraLine, buttons);
  }

  private void mountOverlay(
      LabelComponent messageLabel,
      @Nullable SmallCheckboxComponent checkbox,
      @Nullable Component extraLine,
      List<ButtonComponent> buttons) {
    FlowLayout buttonRow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
    buttonRow.gap(8);
    buttons.forEach(buttonRow::child);
    buttonRow.horizontalAlignment(HorizontalAlignment.CENTER);

    FlowLayout panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
    panel.gap(12);
    panel.padding(Insets.of(16));
    panel.surface(Surface.DARK_PANEL);
    panel.horizontalAlignment(HorizontalAlignment.CENTER);
    panel.child(messageLabel);
    if (checkbox != null) {
      panel.child(checkbox);
    }
    if (extraLine != null) {
      LabelComponent extraLabel = UIComponents.label(extraLine);
      extraLabel.id(CONFIRM_EXTRA_ID);
      extraLabel.color(Color.ofArgb(Colors.TEXT_MUTED));
      extraLabel.shadow(true);
      extraLabel.maxWidth(250);
      extraLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);
      panel.child(extraLabel);
    }
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
