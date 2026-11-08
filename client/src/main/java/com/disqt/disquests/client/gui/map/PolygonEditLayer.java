package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.PointData;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Edits a quest outline on the map: left-click adds a corner or grabs one to drag, right-click
 * removes the last corner, Enter or a click on the first corner finishes. Zero corners means
 * "clear", one is a pin, two or more are an outline (three or more must not cross itself).
 *
 * <p>In {@linkplain #moveOnly(boolean) move-only mode} (the quest editor's preview) the layer only
 * drags existing corners: no adding, removing or finishing, and a drag that would make the outline
 * cross itself snaps back.
 */
public class PolygonEditLayer implements MapLayer {
  private static final double HIT_RADIUS = 6;
  private static final int VERTEX_SIZE = 5;
  private static final int FIRST_VERTEX_SIZE = 7;
  private static final int FILL_COLOR = (Colors.MAP_OPEN & 0x00FFFFFF) | 0x40000000;

  private final List<PointData> vertices;
  private Consumer<List<PointData>> onFinish;
  private Consumer<List<PointData>> onVerticesChanged;
  private String notice;
  private int dragging = -1;
  private PointData dragStart;
  private boolean finished;
  private boolean moveOnly;

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

  /**
   * Called with a copy of the corners after a drag ends with the corner moved, and after a corner
   * is added or removed.
   */
  public void onVerticesChanged(Consumer<List<PointData>> callback) {
    this.onVerticesChanged = callback;
  }

  /** Restricts the layer to dragging existing corners (see the class comment). */
  public PolygonEditLayer moveOnly(boolean moveOnly) {
    this.moveOnly = moveOnly;
    return this;
  }

  /** A transient one-line message for the user, or null. Cleared by the next successful action. */
  public String notice() {
    return notice;
  }

  /**
   * Finishes the edit: false (with a notice) when the outline has exactly two corners or crosses
   * itself, otherwise runs the finish callback and returns true. Fires at most once until the
   * corners change.
   */
  public boolean tryFinish() {
    if (finished) {
      return true;
    }
    if (vertices.size() == 2) {
      notice = Component.translatable("gui.disquests.map.need_three_corners").getString();
      return false;
    }
    if (!QuestLocation.isSimple(vertices)) {
      notice = Component.translatable("gui.disquests.map.self_intersecting").getString();
      return false;
    }
    notice = null;
    finished = true;
    if (onFinish != null) {
      onFinish.accept(vertices());
    }
    return true;
  }

  // --- Input ---

  @Override
  public boolean onMouseDown(MapView view, double relX, double relY, int button) {
    if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
      if (moveOnly) {
        return false;
      }
      dragging = -1;
      if (!vertices.isEmpty()) {
        vertices.remove(vertices.size() - 1);
        changed();
        notifyVerticesChanged();
      }
      return true;
    }
    if (button != InputConstants.MOUSE_BUTTON_LEFT) {
      return false;
    }
    int hit = hitVertex(view, relX, relY);
    if (!moveOnly && hit == 0 && vertices.size() >= 3) {
      tryFinish();
      return true;
    }
    if (hit >= 0) {
      dragging = hit;
      dragStart = vertices.get(hit);
      return true;
    }
    if (moveOnly) {
      return false;
    }
    if (vertices.size() >= QuestLocation.MAX_VERTICES) {
      notice =
          Component.translatable("gui.disquests.map.too_many_vertices", QuestLocation.MAX_VERTICES)
              .getString();
      return true;
    }
    vertices.add(blockAt(view, relX, relY));
    changed();
    notifyVerticesChanged();
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
    if (vertices.get(released).equals(dragStart)) {
      return true;
    }
    if (moveOnly && vertices.size() >= 3 && !QuestLocation.isSimple(vertices)) {
      vertices.set(released, dragStart);
      notice = Component.translatable("gui.disquests.map.self_intersecting").getString();
      return true;
    }
    notifyVerticesChanged();
    return true;
  }

  @Override
  public boolean onKeyPress(MapView view, KeyEvent event) {
    if (moveOnly || event.key() != InputConstants.KEY_RETURN) {
      return false;
    }
    tryFinish();
    return true;
  }

  private void changed() {
    notice = null;
    finished = false;
  }

  private void notifyVerticesChanged() {
    if (onVerticesChanged != null) {
      onVerticesChanged.accept(vertices());
    }
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
    MapDraw.fillPolygon(g, view, p[0], p[1], FILL_COLOR);
    if (n >= 2) {
      MapDraw.outlinePolygon(g, view, p[0], p[1], Colors.MAP_OPEN);
    }
    for (int i = 0; i < n; i++) {
      int size = i == 0 && n >= 3 && !moveOnly ? FIRST_VERTEX_SIZE : VERTEX_SIZE;
      int x = view.x() + (int) Math.round(p[0][i]) - size / 2;
      int y = view.y() + (int) Math.round(p[1][i]) - size / 2;
      g.fill(x, y, x + size, y + size, Colors.MAP_VERTEX);
    }
    // The full editor shows notices in its hint label; the preview has no room for one.
    if (moveOnly && notice != null) {
      g.text(
          Minecraft.getInstance().font,
          Component.literal(notice),
          view.x() + 4,
          view.y() + 4,
          Colors.AMBER,
          true);
    }
  }
}
