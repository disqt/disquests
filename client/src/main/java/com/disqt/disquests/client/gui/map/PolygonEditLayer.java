package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.PointData;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Edits a quest outline on the map: left-click adds a corner (a left drag on empty map pans the
 * view instead, see {@link MapView}) or grabs one to drag, right-click removes the last corner,
 * Enter or a click (press and release without moving) on the first of three or more corners
 * finishes. Zero corners means "clear", one is a pin, two finish as a pin on the first, three or
 * more are an outline that must not cross itself.
 */
public class PolygonEditLayer implements MapLayer {
  private static final double HIT_RADIUS = 6;
  private static final int VERTEX_SIZE = 5;
  private static final int FIRST_VERTEX_SIZE = 7;
  private static final int FILL_ALPHA = 0x40;

  private final List<PointData> vertices;
  private Consumer<List<PointData>> onFinish;
  private String notice;
  private int dragging = -1;
  private boolean dragMoved;
  private boolean finished;
  private int color = Colors.AMBER;

  public PolygonEditLayer(List<PointData> initial) {
    this.vertices = new ArrayList<>(initial);
  }

  /** A copy of the current corners. */
  public List<PointData> vertices() {
    return List.copyOf(vertices);
  }

  public void onFinish(Consumer<List<PointData>> callback) {
    this.onFinish = callback;
  }

  /** Outline colour (opaque ARGB); the fill uses the same hue, translucent. */
  public PolygonEditLayer color(int argb) {
    this.color = 0xFF000000 | argb;
    return this;
  }

  /** A transient one-line message for the user, or null. Cleared by the next successful action. */
  public String notice() {
    return notice;
  }

  /**
   * Finishes the edit: false (with a notice) when the outline crosses itself, otherwise runs the
   * finish callback with {@link QuestLocation#pointOrPolygon the corners as a point or polygon} and
   * returns true. Fires at most once until the corners change.
   */
  public boolean tryFinish() {
    if (finished) {
      return true;
    }
    if (!QuestLocation.isSimple(vertices)) {
      notice = Component.translatable("gui.disquests.map.self_intersecting").getString();
      return false;
    }
    notice = null;
    finished = true;
    if (onFinish != null) {
      onFinish.accept(QuestLocation.pointOrPolygon(vertices()));
    }
    return true;
  }

  // --- Input ---

  /** A left press on a corner handle (within 6 px) is taken at once, so the corner drags. */
  @Override
  public boolean wantsPress(MapView view, double relX, double relY, int button) {
    return button == InputConstants.MOUSE_BUTTON_LEFT && hitVertex(view, relX, relY) >= 0;
  }

  @Override
  public boolean onMouseDown(MapView view, double relX, double relY, int button) {
    if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
      dragging = -1;
      if (!vertices.isEmpty()) {
        vertices.remove(vertices.size() - 1);
        changed();
      }
      return true;
    }
    if (button != InputConstants.MOUSE_BUTTON_LEFT) {
      return false;
    }
    int hit = hitVertex(view, relX, relY);
    if (hit >= 0) {
      // Every corner drags; a release on the first one without moving finishes (onMouseUp).
      dragging = hit;
      dragMoved = false;
      return true;
    }
    if (vertices.size() >= QuestLocation.MAX_VERTICES) {
      notice =
          Component.translatable("gui.disquests.map.too_many_vertices", QuestLocation.MAX_VERTICES)
              .getString();
      return true;
    }
    vertices.add(blockAt(view, relX, relY));
    changed();
    return true;
  }

  @Override
  public boolean onMouseDrag(
      MapView view, double relX, double relY, int button, double dx, double dy) {
    if (dragging < 0 || button != InputConstants.MOUSE_BUTTON_LEFT) {
      return false;
    }
    PointData moved = blockAt(view, relX, relY);
    if (!moved.equals(vertices.get(dragging))) {
      vertices.set(dragging, moved);
      dragMoved = true;
      changed();
    }
    return true;
  }

  @Override
  public boolean onMouseUp(MapView view, double relX, double relY, int button) {
    if (dragging < 0) {
      return false;
    }
    int released = dragging;
    dragging = -1;
    if (!dragMoved && released == 0 && vertices.size() >= 3) {
      tryFinish();
    }
    return true;
  }

  @Override
  public boolean onKeyPress(MapView view, KeyEvent event) {
    if (event.key() != InputConstants.KEY_RETURN) {
      return false;
    }
    tryFinish();
    return true;
  }

  private void changed() {
    notice = null;
    finished = false;
  }

  /** Index of the nearest corner within 6 px of the component-relative point, or -1. */
  private int hitVertex(MapView view, double relX, double relY) {
    int best = -1;
    double bestDistance = HIT_RADIUS;
    for (int i = 0; i < vertices.size(); i++) {
      PointData p = vertices.get(i);
      double[] s = view.blockToScreen(p.x(), p.z());
      double distance = Math.hypot(s[0] - relX, s[1] - relY);
      if (distance <= bestDistance) {
        best = i;
        bestDistance = distance;
      }
    }
    return best;
  }

  private static PointData blockAt(MapView view, double relX, double relY) {
    double[] block = view.screenToBlock(relX, relY);
    return new PointData(Math.round(block[0]), Math.round(block[1]));
  }

  // --- Drawing ---

  @Override
  public void draw(MapView view, OwoUIGraphics g, int mouseX, int mouseY) {
    int n = vertices.size();
    if (n == 0) {
      return;
    }
    double[][] p = MapDraw.project(view, vertices);
    MapDraw.fillPolygon(g, view, p[0], p[1], (FILL_ALPHA << 24) | (color & 0x00FFFFFF));
    if (n >= 2) {
      MapDraw.outlinePolygon(g, view, p[0], p[1], color);
    }
    for (int i = 0; i < n; i++) {
      int size = i == 0 && n >= 3 ? FIRST_VERTEX_SIZE : VERTEX_SIZE;
      int x = view.x() + (int) Math.round(p[0][i]) - size / 2;
      int y = view.y() + (int) Math.round(p[1][i]) - size / 2;
      g.fill(x, y, x + size, y + size, Colors.MAP_VERTEX);
    }
  }
}
