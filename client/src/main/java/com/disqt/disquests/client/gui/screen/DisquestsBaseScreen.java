package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.DisquestsClient;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public abstract class DisquestsBaseScreen extends BaseUIModelScreen<FlowLayout> {

    @Nullable
    protected final Screen parent;

    protected DisquestsBaseScreen(DataSource source, @Nullable Screen parent) {
        super(FlowLayout.class, source);
        this.parent = parent;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
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

    protected void applyThemeRoot(FlowLayout root) {
        root.surface(DisquestsClient.CONFIG.theme().rootSurface());
    }

    protected void applyThemePanel(ParentUIComponent component) {
        component.surface(DisquestsClient.CONFIG.theme().panelSurface());
    }

    /**
     * Exposes the root component for test access.
     * Only valid after the screen has been initialized.
     */
    public FlowLayout getRootComponent() {
        return this.uiAdapter != null ? this.uiAdapter.rootComponent : null;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected void navigateToScreen(Screen screen) {
        if (this.client != null) {
            this.client.setScreen(screen);
        }
    }

    // ===================== CONFIRM OVERLAY =====================

    protected void showConfirmOverlay(Text message, Runnable onConfirm) {
        showConfirmOverlay(message, onConfirm, () -> {});
    }

    protected void showConfirmOverlay(Text message, Runnable onConfirm, Runnable onCancel) {
        if (this.uiAdapter == null) return;

        // Dismiss any existing overlay first
        dismissOverlay();

        // Message label
        LabelComponent messageLabel = UIComponents.label(message);
        messageLabel.shadow(true);
        messageLabel.maxWidth(250);
        messageLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);

        // Yes button
        ButtonComponent yesBtn = UIComponents.button(Text.literal("Yes"), b -> {
            dismissOverlay();
            onConfirm.run();
        });
        yesBtn.id("btn-confirm-yes");
        yesBtn.sizing(Sizing.fixed(60), Sizing.fixed(20));

        // No button
        ButtonComponent noBtn = UIComponents.button(Text.literal("No"), b -> {
            dismissOverlay();
            onCancel.run();
        });
        noBtn.id("btn-confirm-no");
        noBtn.sizing(Sizing.fixed(60), Sizing.fixed(20));

        // Button row
        FlowLayout buttonRow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        buttonRow.gap(8);
        buttonRow.child(yesBtn);
        buttonRow.child(noBtn);
        buttonRow.horizontalAlignment(HorizontalAlignment.CENTER);

        // Panel
        FlowLayout panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        panel.gap(12);
        panel.padding(Insets.of(16));
        panel.surface(Surface.DARK_PANEL);
        panel.horizontalAlignment(HorizontalAlignment.CENTER);
        panel.child(messageLabel);
        panel.child(buttonRow);

        // Overlay
        OverlayContainer<FlowLayout> overlay = UIContainers.overlay(panel);
        overlay.id("confirm-overlay");
        overlay.closeOnClick(false);

        this.uiAdapter.rootComponent.child(overlay);
    }

    protected void dismissOverlay() {
        if (this.uiAdapter == null) return;
        var existing = this.uiAdapter.rootComponent.childById(OverlayContainer.class, "confirm-overlay");
        if (existing != null) {
            existing.remove();
        }
    }
}
