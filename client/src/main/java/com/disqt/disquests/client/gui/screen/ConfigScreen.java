package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ConfigScreen extends BaseScreen {

    private int pinnedWidth;
    private DarkButtonWidget widthLabel;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Disquests Settings"), parent);
        this.pinnedWidth = DisquestsConfig.getPinnedWidth();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int y = this.height / 2 - 20;

        // Decrease button
        this.addDrawableChild(new DarkButtonWidget(
                centerX - 80, y, 20, 20,
                Text.literal("-"), b -> adjustWidth(-10)));

        // Width display
        widthLabel = this.addDrawableChild(new DarkButtonWidget(
                centerX - 55, y, 110, 20,
                Text.literal("Pinned Width: " + pinnedWidth), b -> {}));
        widthLabel.active = false;

        // Increase button
        this.addDrawableChild(new DarkButtonWidget(
                centerX + 60, y, 20, 20,
                Text.literal("+"), b -> adjustWidth(10)));

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

    private void adjustWidth(int delta) {
        pinnedWidth = Math.max(DisquestsConfig.MIN_PINNED_WIDTH,
                Math.min(DisquestsConfig.MAX_PINNED_WIDTH, pinnedWidth + delta));
        widthLabel.setMessage(Text.literal("Pinned Width: " + pinnedWidth));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 50, Colors.TEXT_PRIMARY);
    }
}
