package com.disqt.disquests.client.gui.map;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.input.KeyEvent;

/**
 * One drawing and input layer of a {@link MapView}. Coordinates passed to the input hooks are
 * relative to the view's top-left corner; {@code mouseX/mouseY} in {@link #draw} are absolute.
 */
public interface MapLayer {
  void draw(MapView view, OwoUIGraphics g, int mouseX, int mouseY);

  /**
   * True when this layer must get a press right away (for example a draggable handle under the
   * cursor) instead of after release; the view then sends it the press, drags and release and does
   * not pan.
   */
  default boolean wantsPress(MapView view, double relX, double relY, int button) {
    return false;
  }

  default boolean onMouseDown(MapView view, double relX, double relY, int button) {
    return false;
  }

  default boolean onMouseDrag(
      MapView view, double relX, double relY, int button, double dx, double dy) {
    return false;
  }

  default boolean onMouseUp(MapView view, double relX, double relY, int button) {
    return false;
  }

  default boolean onKeyPress(MapView view, KeyEvent event) {
    return false;
  }
}
