package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.gui.helper.RoundedRect;
import com.disqt.disquests.client.gui.helper.TagColors;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;

/**
 * A rounded-rectangle tag chip component. Renders the tag name on a coloured background with
 * optional remove ("x") button. Provides hover highlight and click press feedback.
 */
public class TagChipComponent extends BaseUIComponent {

  private static final int H_PADDING = 5;
  private static final int V_PADDING = 2;
  private static final int REMOVE_AREA_WIDTH = 10;

  /** Additive brightness applied to the background when hovered. */
  private static final int HOVER_BRIGHTEN = 0x22;

  /** Subtractive brightness applied to the background when pressed. */
  private static final int PRESS_DARKEN = 0x20;

  /** Duration in ticks for the press flash effect. */
  private static final int PRESS_FLASH_TICKS = 3;

  private final String tag;
  private final int bgColor;
  private final int fgColor;
  private final boolean showRemove;
  private Consumer<String> onRemove;
  private Consumer<String> onSelect;

  private boolean hovered;
  private boolean removeHovered;
  private int pressFlashRemaining;

  /** Creates a chip for the given tag. If showRemove is true, an "x" button is rendered. */
  public TagChipComponent(String tag, boolean showRemove) {
    this.tag = tag;
    this.bgColor = TagColors.getBackground(tag);
    this.fgColor = TagColors.getForeground(tag);
    this.showRemove = showRemove;
  }

  /** Creates a read-only chip (no remove button). */
  public TagChipComponent(String tag) {
    this(tag, false);
  }

  public TagChipComponent onRemove(Consumer<String> callback) {
    this.onRemove = callback;
    return this;
  }

  public TagChipComponent onSelect(Consumer<String> callback) {
    this.onSelect = callback;
    return this;
  }

  public String getTag() {
    return tag;
  }

  @Override
  protected int determineHorizontalContentSize(Sizing sizing) {
    TextRenderer tr = MinecraftClient.getInstance().textRenderer;
    int textWidth = tr.getWidth(tag);
    int width = H_PADDING + textWidth + H_PADDING;
    if (showRemove) {
      width += REMOVE_AREA_WIDTH;
    }
    return width;
  }

  @Override
  protected int determineVerticalContentSize(Sizing sizing) {
    return MinecraftClient.getInstance().textRenderer.fontHeight + V_PADDING * 2;
  }

  @Override
  public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
    int x = this.x();
    int y = this.y();
    int w = this.width();
    int h = this.height();
    TextRenderer tr = MinecraftClient.getInstance().textRenderer;

    // Update hover state from mouse position
    this.hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    this.removeHovered = this.hovered && showRemove && mouseX >= x + w - REMOVE_AREA_WIDTH;

    // Tick press flash
    if (pressFlashRemaining > 0) {
      pressFlashRemaining--;
    }

    // Compute effective background color
    int effectiveBg = bgColor;
    if (pressFlashRemaining > 0) {
      effectiveBg = darkenColor(effectiveBg, PRESS_DARKEN);
    } else if (hovered) {
      effectiveBg = brightenColor(effectiveBg, HOVER_BRIGHTEN);
    }

    RoundedRect.draw(context, x, y, w, h, effectiveBg);

    // Tag text
    context.drawText(tr, Text.literal(tag), x + H_PADDING, y + V_PADDING, fgColor, false);

    // Remove "x" indicator
    if (showRemove) {
      int xBtnColor = removeHovered ? brightenColor(fgColor, 0x30) : fgColor;
      int xBtnX = x + w - REMOVE_AREA_WIDTH + (REMOVE_AREA_WIDTH - tr.getWidth("x")) / 2;
      context.drawText(tr, Text.literal("x"), xBtnX, y + V_PADDING, xBtnColor, false);
    }
  }

  @Override
  public boolean onMouseDown(Click click, boolean doubled) {
    if (click.button() != 0) return false;

    pressFlashRemaining = PRESS_FLASH_TICKS;

    if (showRemove && onRemove != null) {
      double relX = click.x();
      if (relX >= this.width() - REMOVE_AREA_WIDTH) {
        onRemove.accept(tag);
        return true;
      }
    }

    if (onSelect != null) {
      onSelect.accept(tag);
      return true;
    }
    return false;
  }

  /** Brighten each RGB channel by amount, clamped to 255. Alpha is preserved. */
  private static int brightenColor(int argb, int amount) {
    int a = (argb >> 24) & 0xFF;
    int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
    int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
    int b = Math.min(255, (argb & 0xFF) + amount);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  /** Darken each RGB channel by amount, clamped to 0. Alpha is preserved. */
  private static int darkenColor(int argb, int amount) {
    int a = (argb >> 24) & 0xFF;
    int r = Math.max(0, ((argb >> 16) & 0xFF) - amount);
    int g = Math.max(0, ((argb >> 8) & 0xFF) - amount);
    int b = Math.max(0, (argb & 0xFF) - amount);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }
}
