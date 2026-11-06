package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** Draws placed quests (polygons and pins) in the view's dimension; clicks open a quest. */
public class QuestPolygonLayer implements MapLayer {
  private static final int PIN_SIZE = 7;
  private static final int PIN_HALF = PIN_SIZE / 2;
  private static final double PIN_HIT_RADIUS = 5;

  private List<Quest> quests = List.of();
  private Consumer<Quest> onQuestClick;
  private Quest hovered;
  private Quest tooltipQuest;
  private Component tooltip;

  /** Keeps the quests that have a location; dimension filtering happens at draw time. */
  public void setQuests(List<Quest> quests) {
    List<Quest> placed = new ArrayList<>();
    for (Quest q : quests) {
      if (q.hasLocation()) {
        placed.add(q);
      }
    }
    this.quests = placed;
  }

  public void onQuestClick(Consumer<Quest> callback) {
    this.onQuestClick = callback;
  }

  /** The quest under the mouse at the last draw, or null. */
  public Quest hovered() {
    return hovered;
  }

  /** Polygons by containment, pins within 5 px; the smallest hit wins. Null when none. */
  public Quest hitTest(MapView view, double relX, double relY) {
    if (relX < 0 || relY < 0 || relX >= view.width() || relY >= view.height()) {
      return null;
    }
    double[] block = view.screenToBlock(relX, relY);
    List<Quest> hits = new ArrayList<>();
    for (Quest q : quests) {
      if (!inDimension(view, q)) {
        continue;
      }
      if (q.isPolygon()) {
        if (QuestLocation.contains(q.getPolygon(), block[0], block[1])) {
          hits.add(q);
        }
        continue;
      }
      double[] pin = pinPoint(q);
      if (pin != null) {
        double[] s = view.blockToScreen(pin[0], pin[1]);
        if (Math.hypot(s[0] - relX, s[1] - relY) <= PIN_HIT_RADIUS) {
          hits.add(q);
        }
      }
    }
    return QuestStyle.pickSmallest(hits);
  }

  @Override
  public void draw(MapView view, OwoUIGraphics g, int mouseX, int mouseY) {
    hovered = hitTest(view, mouseX - view.x(), mouseY - view.y());
    UUID me = ClientSession.getEffectivePlayerUuid();
    for (Quest q : quests) {
      if (!inDimension(view, q)) {
        continue;
      }
      boolean isHovered = q == hovered;
      boolean own = q.isOwner(me);
      if (q.isPolygon()) {
        drawPolygon(view, g, q, isHovered, own);
      } else {
        drawPin(view, g, q, isHovered, own);
      }
    }
    if (hovered != null) {
      view.setHoverTooltip(tooltipFor(hovered));
    }
  }

  @Override
  public boolean onMouseDown(MapView view, double relX, double relY, int button) {
    if (button != InputConstants.MOUSE_BUTTON_LEFT) {
      return false;
    }
    Quest hit = hitTest(view, relX, relY);
    if (hit == null) {
      return false;
    }
    if (onQuestClick != null) {
      onQuestClick.accept(hit);
    }
    return true;
  }

  private void drawPolygon(MapView view, OwoUIGraphics g, Quest q, boolean isHovered, boolean own) {
    double[][] p = MapDraw.project(view, q.getPolygon());
    MapDraw.fillPolygon(g, view, p[0], p[1], QuestStyle.fillColor(q.getVisibility(), isHovered));
    MapDraw.outlinePolygon(g, view, p[0], p[1], QuestStyle.outlineColor(q.getVisibility()));
    if (own) {
      double[][] in = MapDraw.inset(p[0], p[1], 1);
      MapDraw.outlinePolygon(g, view, in[0], in[1], Colors.MAP_OWN_OUTLINE);
    }
  }

  private void drawPin(MapView view, OwoUIGraphics g, Quest q, boolean isHovered, boolean own) {
    double[] pin = pinPoint(q);
    if (pin == null) {
      return;
    }
    double[] s = view.blockToScreen(pin[0], pin[1]);
    int x = view.x() + (int) Math.round(s[0]) - PIN_HALF;
    int y = view.y() + (int) Math.round(s[1]) - PIN_HALF;
    g.fill(x, y, x + PIN_SIZE, y + PIN_SIZE, QuestStyle.fillColor(q.getVisibility(), isHovered));
    g.drawRectOutline(x, y, PIN_SIZE, PIN_SIZE, QuestStyle.outlineColor(q.getVisibility()));
    if (own) {
      g.drawRectOutline(x + 1, y + 1, PIN_SIZE - 2, PIN_SIZE - 2, Colors.MAP_OWN_OUTLINE);
    }
  }

  private Component tooltipFor(Quest q) {
    if (q != tooltipQuest) {
      String owner = q.getOwnerName();
      String text = owner == null ? q.getTitle() : q.getTitle() + " — " + owner;
      tooltip = Component.literal(text == null ? "" : text);
      tooltipQuest = q;
    }
    return tooltip;
  }

  private static boolean inDimension(MapView view, Quest q) {
    return view.getDimension() != null && view.getDimension().equals(q.getMap());
  }

  /** Block {x, z} of a non-polygon quest, or null. */
  private static double[] pinPoint(Quest q) {
    CoordinatesData c = q.getCoordinates();
    if (c != null) {
      return new double[] {c.x(), c.z()};
    }
    List<PointData> poly = q.getPolygon();
    if (!poly.isEmpty()) {
      return new double[] {poly.get(0).x(), poly.get(0).z()};
    }
    return null;
  }
}
