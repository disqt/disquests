package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.BlueMapHelper;
import com.disqt.disquests.client.UrlOpener;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.map.MapMath;
import com.disqt.disquests.client.gui.map.MapView;
import com.disqt.disquests.client.gui.map.QuestPolygonLayer;
import com.disqt.disquests.client.gui.map.SharedTileCache;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the quest screen's location card: a mini map of the pin or outline, and in edit mode one
 * row of coordinate fields per vertex plus the location buttons.
 */
final class LocationCard {
  static final double DEFAULT_Y = 64;

  // Preferred mini-map heights (the XML values); short screens get less, see mapHeight.
  private static final int EDIT_MAP_HEIGHT = 200;
  private static final int VIEW_MAP_HEIGHT = 120;
  private static final int EDIT_MAP_SCREEN_DIVISOR = 4;
  private static final int VIEW_MAP_SCREEN_DIVISOR = 5;
  private static final int MIN_MAP_HEIGHT = 60;
  private static final double CARD_WIDTH_FRACTION = 0.85;
  private static final int MAP_FIT_MARGIN = 8;
  private static final int PIN_ZOOM = 2;
  private static final int FIELD_WIDTH = 50;
  private static final int ROW_HEIGHT = 14;

  /** One vertex row's fields; {@code y} is only present for a pin. */
  record VertexRow(TextFieldComponent x, @Nullable TextFieldComponent y, TextFieldComponent z) {}

  private LocationCard() {}

  // ===================== EDIT MODE =====================

  /** Fills the edit-mode card and returns the vertex rows it built (empty when unlocated). */
  static List<VertexRow> buildEdit(QuestScreen screen, FlowLayout root, Quest quest) {
    String dimension = screen.dimensionForEdit();
    int mapHeight = mapHeight(EDIT_MAP_HEIGHT, screen.height, EDIT_MAP_SCREEN_DIVISOR);
    addMiniMap(root, quest, dimension, screen.width, mapHeight);
    List<VertexRow> rows = buildVertexRows(root.childById(FlowLayout.class, "vertex-list"), quest);

    FlowLayout buttons = root.childById(FlowLayout.class, "location-buttons");
    buttons.child(
        button(
            "btn-edit-on-map",
            Component.translatable("gui.disquests.btn.edit_on_map"),
            screen::openMapEditor));
    ButtonComponent setPos =
        button(
            "btn-set-pos",
            Component.translatable("gui.disquests.btn.set_pos"),
            screen::setPlayerPosition);
    setPos.horizontalSizing(Sizing.fixed(FIELD_WIDTH));
    buttons.child(setPos);
    buttons.child(
        button(
            "btn-clear-location",
            Component.translatable("gui.disquests.btn.clear_location"),
            screen::clearCoords));
    ButtonComponent mapButton =
        button("btn-map", Component.literal("Map: " + dimension), screen::cycleMap);
    mapButton.active(quest.hasLocation());
    buttons.child(mapButton);
    return rows;
  }

  private static List<VertexRow> buildVertexRows(FlowLayout list, Quest quest) {
    List<VertexRow> rows = new ArrayList<>();
    if (quest.isPolygon()) {
      List<PointData> polygon = quest.getPolygon();
      for (int i = 0; i < polygon.size(); i++) {
        PointData p = polygon.get(i);
        TextFieldComponent x = coordField("vertex-x-" + i, format(p.x()), "X");
        TextFieldComponent z = coordField("vertex-z-" + i, format(p.z()), "Z");
        FlowLayout row = vertexRow(i);
        row.child(labelFor(Component.literal("X:"))).child(x);
        row.child(labelFor(Component.literal("Z:"))).child(z);
        list.child(row);
        rows.add(new VertexRow(x, null, z));
      }
    } else if (quest.getCoordinates() != null) {
      CoordinatesData c = quest.getCoordinates();
      TextFieldComponent x = coordField("vertex-x-0", String.valueOf((int) c.x()), "X");
      TextFieldComponent y = coordField("vertex-y-0", String.valueOf((int) c.y()), "Y");
      TextFieldComponent z = coordField("vertex-z-0", String.valueOf((int) c.z()), "Z");
      FlowLayout row = vertexRow(0);
      row.child(labelFor(Component.literal("X:"))).child(x);
      row.child(labelFor(Component.literal("Y:"))).child(y);
      row.child(labelFor(Component.literal("Z:"))).child(z);
      list.child(row);
      rows.add(new VertexRow(x, y, z));
    }
    chainTabTargets(rows);
    return rows;
  }

  private static void chainTabTargets(List<VertexRow> rows) {
    TextFieldComponent previous = null;
    for (VertexRow row : rows) {
      for (TextFieldComponent field : new TextFieldComponent[] {row.x(), row.y(), row.z()}) {
        if (field == null) continue;
        if (previous != null) previous.tabTarget(field);
        previous = field;
      }
    }
  }

  /**
   * Writes the vertex fields back into the quest: a pin's coordinates, or each outline vertex with
   * the bounding box refreshed. A row that does not parse leaves its vertex unchanged.
   */
  static void persistVertexFields(Quest quest, List<VertexRow> rows) {
    if (rows.isEmpty()) return;
    if (quest.isPolygon()) {
      List<PointData> polygon = quest.getPolygon();
      if (rows.size() != polygon.size()) return;
      List<PointData> parsed = new ArrayList<>(polygon.size());
      for (int i = 0; i < rows.size(); i++) {
        Double x = parse(rows.get(i).x());
        Double z = parse(rows.get(i).z());
        parsed.add(x != null && z != null ? new PointData(x, z) : polygon.get(i));
      }
      quest.setPolygon(parsed);
      CoordinatesData[] box = QuestLocation.boundingBox(parsed, DEFAULT_Y);
      quest.setCoordinates(box[0]);
      quest.setCoordinates2(box[1]);
    } else if (quest.getCoordinates() != null && rows.get(0).y() != null) {
      VertexRow row = rows.get(0);
      Double x = parse(row.x());
      Double y = parse(row.y());
      Double z = parse(row.z());
      if (x != null && y != null && z != null) {
        quest.setCoordinates(new CoordinatesData(x, y, z));
      }
    }
  }

  // ===================== VIEW MODE =====================

  /** Fills the view-mode card, or zero-sizes it when the quest has no location. */
  static void buildView(QuestScreen screen, FlowLayout root, Quest quest) {
    FlowLayout card = root.childById(FlowLayout.class, "location-card");
    if (!quest.hasLocation()) {
      card.clearChildren();
      card.sizing(Sizing.fixed(0), Sizing.fixed(0));
      return;
    }
    int mapHeight = mapHeight(VIEW_MAP_HEIGHT, screen.height, VIEW_MAP_SCREEN_DIVISOR);
    addMiniMap(root, quest, screen.dimensionForEdit(), screen.width, mapHeight);

    root.childById(LabelComponent.class, "coords-label")
        .text(buildCoordsText(quest).withColor(Colors.TEXT_MUTED));
    if (quest.getMap() != null) {
      root.childById(LabelComponent.class, "map-label")
          .text(Component.literal("Map: " + quest.getMap()).withColor(Colors.TEXT_MUTED));
    }

    String bluemapUrl = BlueMapHelper.buildUrl(quest);
    if (bluemapUrl != null) {
      ButtonComponent bmBtn =
          UIComponents.button(
              Component.translatable("gui.disquests.btn.view_bluemap"),
              ignored -> UrlOpener.open(bluemapUrl));
      bmBtn.sizing(Sizing.content(), Sizing.fixed(ROW_HEIGHT));
      root.childById(FlowLayout.class, "location-labels").child(bmBtn);
    }
  }

  /** "X:.. Y:.. Z:.." for a pin; the corner count and bounding box for an outline. */
  static MutableComponent buildCoordsText(Quest quest) {
    if (quest.isPolygon()) {
      CoordinatesData[] box = QuestLocation.boundingBox(quest.getPolygon(), DEFAULT_Y);
      return Component.translatable(
          "gui.disquests.label.region_summary",
          quest.getPolygon().size(),
          (int) Math.round(box[0].x()),
          (int) Math.round(box[1].x()),
          (int) Math.round(box[0].z()),
          (int) Math.round(box[1].z()));
    }
    CoordinatesData c = quest.getCoordinates();
    return Component.literal(String.format("X:%.0f Y:%.0f Z:%.0f", c.x(), c.y(), c.z()));
  }

  // ===================== MINI MAP =====================

  /**
   * Adds a non-interactive map of the quest to {@code mini-map-slot}: an outline fitted to the
   * card, a pin at zoom 2, otherwise the player (or the origin) at zoom 2.
   */
  private static void addMiniMap(
      FlowLayout root, Quest quest, String dimension, int screenWidth, int mapHeight) {
    FlowLayout slot = root.childById(FlowLayout.class, "mini-map-slot");
    slot.verticalSizing(Sizing.fixed(mapHeight));

    QuestPolygonLayer layer = new QuestPolygonLayer();
    layer.setQuests(List.of(quest));
    MapView map = new MapView(SharedTileCache.get()).interactive(false).setDimension(dimension);
    map.addLayer(layer);
    map.sizing(Sizing.fill(100), Sizing.fill(100));
    map.id("mini-map");

    if (quest.isPolygon()) {
      CoordinatesData[] box = QuestLocation.boundingBox(quest.getPolygon(), DEFAULT_Y);
      map.centerOn((box[0].x() + box[1].x()) / 2, (box[0].z() + box[1].z()) / 2);
      int cardWidth = (int) (CARD_WIDTH_FRACTION * screenWidth);
      map.setZoom(
          MapMath.fitZoom(
              box[1].x() - box[0].x(),
              box[1].z() - box[0].z(),
              cardWidth,
              mapHeight,
              MAP_FIT_MARGIN));
    } else if (quest.getCoordinates() != null) {
      map.centerOn(quest.getCoordinates().x(), quest.getCoordinates().z());
      map.setZoom(PIN_ZOOM);
    } else {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.level().dimension().identifier().getPath().equals(dimension)) {
        map.centerOn(player.getX(), player.getZ());
      } else {
        map.centerOn(0, 0);
      }
      map.setZoom(PIN_ZOOM);
    }
    slot.child(map);
  }

  /** The preferred height, shrunk to a share of short screens so the rows below stay visible. */
  private static int mapHeight(int preferred, int screenHeight, int divisor) {
    return Math.min(preferred, Math.max(MIN_MAP_HEIGHT, screenHeight / divisor));
  }

  // ===================== WIDGETS =====================

  private static FlowLayout vertexRow(int index) {
    FlowLayout row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
    row.gap(4);
    row.verticalAlignment(VerticalAlignment.CENTER);
    row.child(labelFor(Component.translatable("gui.disquests.label.vertex", index + 1)));
    return row;
  }

  private static TextFieldComponent coordField(String id, String value, String placeholder) {
    MultiLineTextFieldWidget field =
        new MultiLineTextFieldWidget(
            Minecraft.getInstance().font,
            0,
            0,
            FIELD_WIDTH,
            ROW_HEIGHT,
            value,
            placeholder,
            1,
            false);
    TextFieldComponent component = new TextFieldComponent(field);
    component.sizing(Sizing.fixed(FIELD_WIDTH), Sizing.fixed(ROW_HEIGHT));
    component.id(id);
    return component;
  }

  private static LabelComponent labelFor(Component text) {
    LabelComponent label = UIComponents.label(text.copy().withColor(Colors.TEXT_MUTED));
    label.shadow(true);
    return label;
  }

  private static ButtonComponent button(String id, Component text, Runnable action) {
    ButtonComponent button = UIComponents.button(text, ignored -> action.run());
    button.sizing(Sizing.content(), Sizing.fixed(ROW_HEIGHT));
    button.id(id);
    return button;
  }

  /** Whole numbers without a decimal point, so the field round-trips the stored value. */
  private static String format(double v) {
    return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
  }

  @Nullable
  private static Double parse(@Nullable TextFieldComponent field) {
    if (field == null) return null;
    try {
      return Double.parseDouble(field.getText().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
