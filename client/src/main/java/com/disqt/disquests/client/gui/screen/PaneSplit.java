package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.gui.component.SplitterComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * A pane with a fixed width next to a draggable {@link SplitterComponent}. The width starts from a
 * config value, follows the divider while dragging (clamped by the caller's rule) and is written
 * back to the config, and the file saved, when the drag ends.
 */
public final class PaneSplit {

  /** Which side of the divider the pane sits on. */
  public enum Side {
    LEFT,
    RIGHT
  }

  private final SplitterComponent divider;
  private int width;
  private int startWidth;

  /**
   * Applies the saved width to {@code pane} and builds its divider; the caller places {@link
   * #divider()} next to the pane.
   *
   * @param read the config value, for example {@code DisquestsClient.CONFIG::sidePanelWidth}
   * @param write its setter; the config file is saved after it
   * @param clamp the allowed width for a requested one, usually depending on the screen width
   */
  public PaneSplit(
      UIComponent pane,
      Side side,
      String dividerId,
      IntSupplier read,
      IntConsumer write,
      IntUnaryOperator clamp) {
    this.width = clamp.applyAsInt(read.getAsInt());
    this.startWidth = width;
    pane.horizontalSizing(Sizing.fixed(width));

    this.divider = new SplitterComponent();
    divider.id(dividerId);
    divider.onDrag(
        totalDx -> {
          // The divider follows the cursor: dragging away from the pane widens it.
          double dx = side == Side.RIGHT ? -totalDx : totalDx;
          int requested = clamp.applyAsInt((int) Math.round(startWidth + dx));
          if (requested != width) {
            width = requested;
            pane.horizontalSizing(Sizing.fixed(requested));
          }
        });
    divider.onCommit(
        () -> {
          // Width at the press: only drags change it, and each commit re-anchors it.
          startWidth = width;
          if (read.getAsInt() != width) {
            write.accept(width);
            // Option#set does not write the file.
            DisquestsClient.CONFIG.save();
          }
        });
  }

  public SplitterComponent divider() {
    return divider;
  }

  /** The pane's current width. */
  public int width() {
    return width;
  }
}
