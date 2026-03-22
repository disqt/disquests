package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.gui.helper.ColorConfig;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.helper.Theme;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class ConfigScreen extends DisquestsBaseScreen {

    private static Theme originalThemeBeforeConfig = null;

    /** Resets the static theme tracking state. Used by tests to ensure clean starting state. */
    public static void resetOriginalTheme() {
        originalThemeBeforeConfig = null;
    }

    private int pinnedWidth;
    private Theme selectedTheme;

    public ConfigScreen(@Nullable Screen parent) {
        super(DataSource.asset(Identifier.of("disquests", "config_screen")), parent);
        this.pinnedWidth = DisquestsConfig.getPinnedWidth();
        this.selectedTheme = DisquestsConfig.getTheme();
        if (originalThemeBeforeConfig == null) {
            originalThemeBeforeConfig = this.selectedTheme;
        }
    }

    @Override
    protected void build(FlowLayout root) {
        applyThemeRoot(root);
        applyThemePanel(root.childById(FlowLayout.class, "panel"));

        // Wire theme cycle button
        ButtonComponent themeBtn = root.childById(ButtonComponent.class, "btn-theme");
        themeBtn.setMessage(Text.literal(selectedTheme.displayName()));
        themeBtn.onPress(b -> {
            Theme next = selectedTheme.next();
            DisquestsConfig.setTheme(next);
            next.applyColors();
            ColorConfig.loadColors();
            this.client.setScreen(new ConfigScreen(this.parent));
        });

        // Create vanilla slider and wrap for owo-ui
        double initialValue = (double) (pinnedWidth - DisquestsConfig.MIN_PINNED_WIDTH)
                / (DisquestsConfig.MAX_PINNED_WIDTH - DisquestsConfig.MIN_PINNED_WIDTH);

        SliderWidget slider = new SliderWidget(0, 0, 150, 20,
                Text.literal("Pinned Width: " + pinnedWidth), initialValue) {
            @Override
            protected void updateMessage() {
                int val = DisquestsConfig.MIN_PINNED_WIDTH
                        + (int) (this.value * (DisquestsConfig.MAX_PINNED_WIDTH - DisquestsConfig.MIN_PINNED_WIDTH));
                this.setMessage(Text.literal("Pinned Width: " + val));
            }

            @Override
            protected void applyValue() {
                pinnedWidth = DisquestsConfig.MIN_PINNED_WIDTH
                        + (int) (this.value * (DisquestsConfig.MAX_PINNED_WIDTH - DisquestsConfig.MIN_PINNED_WIDTH));
            }
        };

        FlowLayout sliderRow = root.childById(FlowLayout.class, "slider-row");
        sliderRow.child(UIComponents.wrapVanillaWidget(slider)
                .sizing(Sizing.fixed(150), Sizing.fixed(20)));

        root.childById(ButtonComponent.class, "btn-save")
                .onPress(b -> {
                    DisquestsConfig.setPinnedWidth(pinnedWidth);
                    DisquestsConfig.save();
                    originalThemeBeforeConfig = null;
                    this.close();
                });

        root.childById(ButtonComponent.class, "btn-cancel")
                .onPress(b -> {
                    if (originalThemeBeforeConfig != null) {
                        DisquestsConfig.setTheme(originalThemeBeforeConfig);
                        originalThemeBeforeConfig.applyColors();
                        ColorConfig.loadColors();
                    }
                    originalThemeBeforeConfig = null;
                    this.close();
                });
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
