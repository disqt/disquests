package com.disqt.disquests.client.gui.component;

import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

/**
 * A vertical scroll that never gets shorter than a minimum height. owo's sizings have no minimum,
 * so an {@code expand} scroll sharing a column with content-sized siblings would shrink to nothing
 * on a short screen; this keeps a usable strip instead.
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
}
