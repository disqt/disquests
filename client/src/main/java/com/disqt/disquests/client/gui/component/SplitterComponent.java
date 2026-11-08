package com.disqt.disquests.client.gui.component;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.function.DoubleConsumer;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * A vertical drag handle between two panes. A left-button drag reports the total horizontal
 * distance since the press to {@link #onDrag}; releasing fires {@link #onCommit}.
 */
public class SplitterComponent extends BaseUIComponent {

  public static final int WIDTH = 4;

  private static final int STRIP = 0x60000000;
  private static final int STRIP_ACTIVE = 0x60FFFFFF;
  private static final int LINE = 0x50FFFFFF;
  private static final int LINE_ACTIVE = 0xC0FFFFFF;

  private DoubleConsumer onDrag = ignored -> {};
  private Runnable onCommit = () -> {};
  private boolean dragging;
  private double totalDx;

  public SplitterComponent() {
    this.sizing(Sizing.fixed(WIDTH), Sizing.fill(100));
  }

  /** Called on every drag step with the total horizontal delta since the press, in GUI px. */
  public SplitterComponent onDrag(DoubleConsumer callback) {
    this.onDrag = callback;
    return this;
  }

  /** Called once when the drag ends. */
  public SplitterComponent onCommit(Runnable callback) {
    this.onCommit = callback;
    return this;
  }

  public boolean isDragging() {
    return dragging;
  }

  @Override
  public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
    int x = this.x();
    int y = this.y();
    int w = this.width();
    int h = this.height();
    boolean active = dragging || isInBoundingBox(mouseX, mouseY);
    context.fill(x, y, x + w, y + h, active ? STRIP_ACTIVE : STRIP);
    int lineX = x + w / 2;
    context.fill(lineX, y, lineX + 1, y + h, active ? LINE_ACTIVE : LINE);
  }

  @Override
  public boolean canFocus(FocusSource source) {
    return source == FocusSource.MOUSE_CLICK;
  }

  @Override
  public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
    if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
      return false;
    }
    dragging = true;
    totalDx = 0;
    return true;
  }

  @Override
  public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
    if (!dragging) {
      return false;
    }
    totalDx += deltaX;
    onDrag.accept(totalDx);
    return true;
  }

  @Override
  public boolean onMouseUp(MouseButtonEvent click) {
    if (!dragging) {
      return false;
    }
    dragging = false;
    onCommit.run();
    return true;
  }
}
