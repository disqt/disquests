package com.disqt.disquests.client.gui.widget;

import com.disqt.disquests.client.gui.helper.Colors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ToastOverlay {

  private String message;
  private int ticksRemaining;
  private static final int DURATION_TICKS = 60; // 3 seconds
  private static final int FADE_TICKS = 20; // last 1 second fades
  private static final int BG_RGB = 0x222222;
  private static final int BG_BASE_ALPHA = 0xCC;
  private static final int PADDING_X = 12;
  private static final int PADDING_Y = 6;

  public void show(String message) {
    this.message = message;
    this.ticksRemaining = DURATION_TICKS;
  }

  public void tick() {
    if (ticksRemaining > 0) ticksRemaining--;
  }

  public boolean isVisible() {
    return ticksRemaining > 0 && message != null;
  }

  public void render(GuiGraphicsExtractor context, Font textRenderer, int screenWidth, int bottomY) {
    if (!isVisible()) return;

    float alpha = ticksRemaining <= FADE_TICKS ? (float) ticksRemaining / FADE_TICKS : 1.0f;
    int alphaInt = (int) (alpha * 255);

    int textWidth = textRenderer.width(message);
    int boxWidth = textWidth + PADDING_X * 2;
    int boxHeight = textRenderer.lineHeight + PADDING_Y * 2;
    int x = (screenWidth - boxWidth) / 2;
    int y = bottomY - boxHeight - 4;

    int bgAlpha = (int) (BG_BASE_ALPHA * alpha);
    int bg = (bgAlpha << 24) | BG_RGB;
    context.fill(x, y, x + boxWidth, y + boxHeight, bg);

    int textColor = (alphaInt << 24) | (Colors.TEXT_PRIMARY & 0x00FFFFFF);
    context.text(textRenderer, message, x + PADDING_X, y + PADDING_Y, textColor, false);
  }
}
