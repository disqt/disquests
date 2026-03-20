package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.gui.helper.DisquestsConfig;
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

    private int pinnedWidth;

    public ConfigScreen(@Nullable Screen parent) {
        super(DataSource.asset(Identifier.of("disquests", "config_screen")), parent);
        this.pinnedWidth = DisquestsConfig.getPinnedWidth();
    }

    @Override
    protected void build(FlowLayout root) {
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
                    this.close();
                });

        root.childById(ButtonComponent.class, "btn-cancel")
                .onPress(b -> this.close());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
