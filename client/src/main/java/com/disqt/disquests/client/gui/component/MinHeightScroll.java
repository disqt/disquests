package com.disqt.disquests.client.gui.component;

import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.util.Mth;

/**
 * A vertical scroll that never gets shorter than a minimum height, and whose position can be read
 * and set, so a rebuilt page can keep the reader's place. owo's sizings have no minimum, so an
 * {@code expand} scroll sharing a column with content-sized siblings would shrink to nothing on a
 * short screen; this keeps a usable strip instead (0 for no minimum).
 */
public class MinHeightScroll<C extends UIComponent> extends ScrollContainer<C> {

  private final int minHeight;

  public MinHeightScroll(Sizing horizontal, Sizing vertical, C child, int minHeight) {
    super(ScrollDirection.VERTICAL, horizontal, vertical, child);
    this.minHeight = minHeight;
  }

  @Override
  protected void applySizing() {
    super.applySizing();
    if (this.height < minHeight) this.height = minHeight;
  }

  /** How far the content is scrolled, in GUI px (the target, not the animation). */
  public double offset() {
    return scrollOffset;
  }

  /** Jumps to {@code offset} without animating, clamped to the content. Call after layout. */
  public void jumpTo(double offset) {
    scrollOffset = Mth.clamp(offset, 0, maxScroll);
    currentScrollPosition = scrollOffset;
  }
}
