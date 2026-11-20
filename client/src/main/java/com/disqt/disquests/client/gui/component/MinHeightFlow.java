package com.disqt.disquests.client.gui.component;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;

/**
 * A horizontal flow that never gets shorter than a minimum height, like {@link MinHeightScroll}: an
 * {@code expand} row sharing a column with content-sized siblings would otherwise shrink to nothing
 * on a short screen. Children sized {@code fill} follow the kept height.
 */
public class MinHeightFlow extends FlowLayout {

  private final int minHeight;

  public MinHeightFlow(Sizing horizontal, Sizing vertical, int minHeight) {
    super(horizontal, vertical, Algorithm.HORIZONTAL);
    this.minHeight = minHeight;
  }

  @Override
  protected void applySizing() {
    super.applySizing();
    if (this.height < minHeight) this.height = minHeight;
  }
}
