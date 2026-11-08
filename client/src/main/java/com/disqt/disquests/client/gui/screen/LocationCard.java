package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.BlueMapHelper;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.UrlOpener;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.map.BannerLayer;
import com.disqt.disquests.client.gui.map.MapMath;
import com.disqt.disquests.client.gui.map.MapView;
import com.disqt.disquests.client.gui.map.PolygonEditLayer;
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
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

  // Mini-map sizes (the slots in the XML are content-sized; these are the only source). Short
  // screens get a smaller map with the same aspect, see mapSize.
  private static final int EDIT_MAP_WIDTH = 220;
  private static final int EDIT_MAP_HEIGHT = 140;
  private static final int VIEW_MAP_WIDTH = 220;
  private static final int VIEW_MAP_HEIGHT = 110;
  private static final int EDIT_MAP_SCREEN_DIVISOR = 4;
  private static final int VIEW_MAP_SCREEN_DIVISOR = 5;
  private static final int MAP_FIT_MARGIN = 8;
  private static final int PIN_ZOOM = 2;
  private static final int FIELD_WIDTH = 50;
  private static final int ROW_HEIGHT = 14;
  private static final int VERTEX_LIST_GAP = 2;
  private static final int SCROLLBAR_WIDTH = 3;

  /** One vertex row's fields; {@code y} is only present for a pin. */
  record VertexRow(TextFieldComponent x, @Nullable TextFieldComponent y, TextFieldComponent z) {}

  /**
   * The quest's location as edit mode found it, so Cancel can tell whether it changed and put it
   * back. An outline compares by its corners (its bounding-box fields follow from them), a pin by
   * its coordinates.
   */
  record LocationSnapshot(
      @Nullable CoordinatesData coordinates,
      @Nullable CoordinatesData coordinates2,
      boolean isRegion,
      @Nullable String map,
      List<PointData> polygon) {
    static LocationSnapshot of(Quest quest) {
      return new LocationSnapshot(
          quest.getCoordinates(),
          quest.getCoordinates2(),
          quest.isRegion(),
          quest.getMap(),
          List.copyOf(quest.getPolygon()));
    }

    boolean sameLocation(Quest quest) {
      if (!Objects.equals(map, quest.getMap()) || !polygon.equals(quest.getPolygon())) {
        return false;
      }
      return !polygon.isEmpty() || Objects.equals(coordinates, quest.getCoordinates());
    }

    void restore(Quest quest) {
      quest.setCoordinates(coordinates);
      quest.setCoordinates2(coordinates2);
      quest.setRegion(isRegion);
      quest.setMap(map);
      quest.setPolygon(new ArrayList<>(polygon));
    }
  }

  private LocationCard() {}

  // ===================== EDIT MODE =====================

  /** Fills the edit-mode card and returns the vertex rows it built (empty when unlocated). */
  static List<VertexRow> buildEdit(QuestScreen screen, FlowLayout root, Quest quest) {
    String dimension = screen.dimensionForEdit();
    int[] size = mapSize(EDIT_MAP_WIDTH, EDIT_MAP_HEIGHT, screen.height, EDIT_MAP_SCREEN_DIVISOR);
    FlowLayout vertexList = root.childById(FlowLayout.class, "vertex-list");
    List<VertexRow> rows = buildVertexRows(vertexList, quest);
    capVertexList(vertexList, rows.size(), size[1]);

    // The preview drags existing corners in place; adding, removing and finishing stay in the
    // full-screen editor. A drag updates the quest and the fields without rebuilding the screen.
    PolygonEditLayer layer = new PolygonEditLayer(locationVertices(quest)).moveOnly(true);
    layer.onVerticesChanged(
        vertices -> {
          List<PointData> before = locationVertices(quest);
          screen.moveMapLocation(vertices);
          refreshVertexFields(rows, before, vertices);
        });
    MapView map = addMiniMap(root, quest, dimension, size[0], size[1]);
    map.addLayer(layer);
    map.interactive(true);

    FlowLayout buttons = root.childById(FlowLayout.class, "location-buttons");
    // A pre-V2 server cannot store outlines from the map editor; the vertex rows stay the editor.
    boolean mapEditing = ClientSession.supportsMap();
    if (mapEditing) {
      buttons.child(
          button(
              "btn-edit-on-map",
              Component.translatable("gui.disquests.btn.edit_on_map"),
              screen::openMapEditor));
    }
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
    if (!mapEditing) {
      LabelComponent outdated =
          labelFor(Component.translatable("gui.disquests.map.server_outdated"));
      outdated.id("map-server-outdated");
      outdated.horizontalSizing(Sizing.fill(100));
      root.childById(FlowLayout.class, "location-card").child(outdated);
    }
    return rows;
  }

  private static List<VertexRow> buildVertexRows(FlowLayout list, Quest quest) {
    List<VertexRow> rows = new ArrayList<>();
    if (quest.isPolygon()) {
      List<PointData> polygon = quest.getPolygon();
      for (int i = 0; i < polygon.size(); i++) {
        PointData p = polygon.get(i);
        TextFieldComponent x = coordField("vertex-x-" + i, vertexText(p.x()), "X");
        TextFieldComponent z = coordField("vertex-z-" + i, vertexText(p.z()), "Z");
        FlowLayout row = vertexRow(i);
        row.child(labelFor(Component.literal("X:"))).child(x);
        row.child(labelFor(Component.literal("Z:"))).child(z);
        list.child(row);
        rows.add(new VertexRow(x, null, z));
      }
    } else if (quest.getCoordinates() != null) {
      CoordinatesData c = quest.getCoordinates();
      TextFieldComponent x = coordField("vertex-x-0", pinText(c.x()), "X");
      TextFieldComponent y = coordField("vertex-y-0", pinText(c.y()), "Y");
      TextFieldComponent z = coordField("vertex-z-0", pinText(c.z()), "Z");
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

  /** The quest's outline corners, its pin as one corner, or nothing. */
  private static List<PointData> locationVertices(Quest quest) {
    if (quest.isPolygon()) {
      return List.copyOf(quest.getPolygon());
    }
    CoordinatesData c = quest.getCoordinates();
    return c != null ? List.of(new PointData(c.x(), c.z())) : List.of();
  }

  /**
   * Rewrites the fields of the corners a preview drag moved. Other rows keep their text, so a value
   * typed but not yet saved survives a drag of another corner; a pin keeps its Y field.
   */
  private static void refreshVertexFields(
      List<VertexRow> rows, List<PointData> before, List<PointData> after) {
    if (rows.size() != after.size()) return;
    for (int i = 0; i < rows.size(); i++) {
      PointData p = after.get(i);
      if (i < before.size() && p.equals(before.get(i))) continue;
      VertexRow row = rows.get(i);
      boolean pin = row.y() != null;
      row.x().getDelegate().setText(pin ? pinText(p.x()) : vertexText(p.x()));
      row.z().getDelegate().setText(pin ? pinText(p.z()) : vertexText(p.z()));
    }
  }

  /**
   * Wraps the vertex list in a scroll area of the map's height when its rows would be taller, so a
   * many-cornered outline does not stretch the card.
   */
  private static void capVertexList(FlowLayout list, int rowCount, int maxHeight) {
    int height = rowCount * ROW_HEIGHT + Math.max(0, rowCount - 1) * VERTEX_LIST_GAP;
    if (height <= maxHeight || !(list.parent() instanceof FlowLayout parent)) return;
    int index = parent.children().indexOf(list);
    parent.removeChild(list);
    ScrollContainer<FlowLayout> scroll =
        UIContainers.verticalScroll(Sizing.content(), Sizing.fixed(maxHeight), list);
    scroll.scrollbarThiccness(SCROLLBAR_WIDTH);
    // owo draws the bar inside the scroll's content box; pad the list so it clears the fields.
    list.padding(Insets.right(SCROLLBAR_WIDTH + 2));
    parent.child(index, scroll);
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

  /** True when a vertex field's text differs from what the card filled it with. */
  static boolean vertexFieldsEdited(Quest quest, List<VertexRow> rows) {
    if (rows.isEmpty()) return false;
    if (quest.isPolygon()) {
      List<PointData> polygon = quest.getPolygon();
      if (rows.size() != polygon.size()) return false;
      for (int i = 0; i < rows.size(); i++) {
        if (edited(rows.get(i).x(), vertexText(polygon.get(i).x()))
            || edited(rows.get(i).z(), vertexText(polygon.get(i).z()))) {
          return true;
        }
      }
      return false;
    }
    CoordinatesData c = quest.getCoordinates();
    if (c == null) return false;
    VertexRow row = rows.get(0);
    return edited(row.x(), pinText(c.x()))
        || edited(row.y(), pinText(c.y()))
        || edited(row.z(), pinText(c.z()));
  }

  private static boolean edited(@Nullable TextFieldComponent field, String shown) {
    return field != null && !field.getText().trim().equals(shown);
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
    int[] size = mapSize(VIEW_MAP_WIDTH, VIEW_MAP_HEIGHT, screen.height, VIEW_MAP_SCREEN_DIVISOR);
    MapView map = addMiniMap(root, quest, screen.dimensionForEdit(), size[0], size[1]);
    QuestPolygonLayer layer = new QuestPolygonLayer();
    layer.setQuests(List.of(quest));
    map.addLayer(layer);

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
   * Adds a map of the quest to {@code mini-map-slot}, with only the banner layer (no labels) and
   * non-interactive: an outline fitted to the map, a pin at zoom 2, otherwise the player (or the
   * origin) at zoom 2.
   */
  private static MapView addMiniMap(
      FlowLayout root, Quest quest, String dimension, int mapWidth, int mapHeight) {
    FlowLayout slot = root.childById(FlowLayout.class, "mini-map-slot");
    MapView map = new MapView(SharedTileCache.get()).interactive(false).setDimension(dimension);
    map.sizing(Sizing.fixed(mapWidth), Sizing.fixed(mapHeight));
    map.id("mini-map");
    map.addLayer(new BannerLayer(SharedTileCache.banners()).showLabels(false));

    if (quest.isPolygon()) {
      CoordinatesData[] box = QuestLocation.boundingBox(quest.getPolygon(), DEFAULT_Y);
      map.centerOn((box[0].x() + box[1].x()) / 2, (box[0].z() + box[1].z()) / 2);
      map.setZoom(
          MapMath.fitZoom(
              box[1].x() - box[0].x(),
              box[1].z() - box[0].z(),
              mapWidth,
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
    return map;
  }

  /**
   * The preferred {width, height}, shrunk on short screens to at most {@code screenHeight /
   * divisor} tall with the same aspect, so the rows below stay visible.
   */
  private static int[] mapSize(int width, int height, int screenHeight, int divisor) {
    int h = Math.max(1, Math.min(height, screenHeight / divisor));
    return new int[] {(int) Math.round((double) width * h / height), h};
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

  /** A pin field shows the block coordinate. */
  private static String pinText(double v) {
    return String.valueOf((int) v);
  }

  /** Whole numbers without a decimal point, so the field round-trips the stored value. */
  private static String vertexText(double v) {
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
