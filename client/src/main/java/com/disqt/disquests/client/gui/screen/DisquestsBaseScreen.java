package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.helper.Theme;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
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
}
