package com.disqt.disquests.client.gui.widget;

import com.disqt.disquests.client.gui.helper.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;

public class TabButtonWidget extends ButtonWidget {

    private boolean isActive = false;

    public TabButtonWidget(int x, int y, int width, int height, MutableText message, PressAction onPress) {
        super(x, y, width, height, message, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void drawIcon(DrawContext context, int x, int y, float delta) {
        int color;
        if (this.isActive()) {
            color = Colors.PANEL_BACKGROUND;
        } else if (this.isHovered()) {
            color = Colors.BUTTON_HOVER;
        } else {
            color = Colors.TAB_INACTIVE;
        }
        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, color);
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawCenteredTextWithShadow(textRenderer, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, Colors.TEXT_PRIMARY);
    }

    @Override
    protected void drawLabel(net.minecraft.client.font.DrawnTextConsumer consumer) {}

    public boolean isActive() {
        return this.isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }
}
