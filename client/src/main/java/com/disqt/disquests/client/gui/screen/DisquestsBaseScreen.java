package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.helper.Theme;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
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
        root.surface(DisquestsConfig.getTheme().rootSurface());
    }

    protected void applyThemePanel(ParentUIComponent component) {
        component.surface(DisquestsConfig.getTheme().panelSurface());
    }

    /**
     * Exposes the root component for test access.
     * Only valid after the screen has been initialized.
     */
    public FlowLayout getRootComponent() {
        return this.uiAdapter != null ? this.uiAdapter.rootComponent : null;
    }
}
