package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends BaseScreen {

    private int pinnedWidth;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Disquests Settings"), parent);
        this.pinnedWidth = DisquestsConfig.getPinnedWidth();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int y = this.height / 2 - 20;

        // Pinned width slider
        double initialValue = (double) (pinnedWidth - DisquestsConfig.MIN_PINNED_WIDTH)
                / (DisquestsConfig.MAX_PINNED_WIDTH - DisquestsConfig.MIN_PINNED_WIDTH);

        this.addDrawableChild(new SliderWidget(
                centerX - 75, y, 150, 20,
                Text.literal("Pinned Width: " + pinnedWidth),
                initialValue) {
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
        });

        y += 30;

        // Save button
        this.addDrawableChild(new DarkButtonWidget(
                centerX - 55, y, 50, 20,
                Text.literal("Save"), b -> {
                    DisquestsConfig.setPinnedWidth(pinnedWidth);
                    DisquestsConfig.save();
                    this.close();
                }));

        // Cancel button
        this.addDrawableChild(new DarkButtonWidget(
                centerX + 5, y, 50, 20,
                Text.literal("Cancel"), b -> this.close()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 50, Colors.TEXT_PRIMARY);
    }
}
