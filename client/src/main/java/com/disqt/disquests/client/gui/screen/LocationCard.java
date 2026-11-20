package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.BlueMapHelper;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.UrlOpener;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.map.BannerLayer;
import com.disqt.disquests.client.gui.map.MapCameraMemory;
import com.disqt.disquests.client.gui.map.MapLayer;
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
 * Builds the quest screen's location card: a mini map of the pin or outline, and in edit mode the
 * location buttons. With the map available the edit card's mini map is the way in to the
 * full-screen map editor (a click opens it); with the map off the card has one row of coordinate
 * fields per vertex instead.
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

  /**
   * Fills the edit-mode card and returns the vertex rows it built: none when the map is available
   * or the quest is unlocated.
   */
  static List<VertexRow> buildEdit(QuestScreen screen, FlowLayout root, Quest quest) {
    String dimension = screen.dimensionForEdit();
    int[] size = mapSize(EDIT_MAP_WIDTH, EDIT_MAP_HEIGHT, screen.height, EDIT_MAP_SCREEN_DIVISOR);
    FlowLayout locationMain = root.childById(FlowLayout.class, "location-main");
    FlowLayout vertexList = root.childById(FlowLayout.class, "vertex-list");

    // With the map available the preview is the editor's way in: a click opens the full-screen
    // map editor, and there are no coordinate rows. With the map off (pre-V2 server, switched
    // off, or BlueMap unreachable) the vertex rows are the whole editor.
    boolean mapEditing = ClientSession.mapAvailable();
    List<VertexRow> rows;
    if (mapEditing) {
      rows = List.of();
      // Looked up from root: owo only sets parent() when the tree is mounted, after build().
      locationMain.removeChild(vertexList);
      MapView map = addMiniMap(root, quest, dimension, size[0], size[1]);
      map.addLayer(locationLayer(quest));
      map.onPreviewClick(screen::openMapEditor);
    } else {
      rows = buildVertexRows(vertexList, quest);
      capVertexList(locationMain, vertexList, rows.size(), size[1]);
    }

    FlowLayout buttons = root.childById(FlowLayout.class, "location-buttons");
    // Content-sized buttons in a wrapping row: on a narrow card they move to a second line.
    buttons.child(
        button(
            "btn-set-pos",
            Component.translatable("gui.disquests.btn.set_pos"),
            screen::setPlayerPosition));
    buttons.child(
        button(
            "btn-clear-location",
            Component.translatable("gui.disquests.btn.clear_location"),
            screen::clearCoords));
    ButtonComponent mapButton =
        button("btn-map", Component.literal("Map: " + dimension), screen::cycleMap);
    mapButton.active(quest.hasLocation());
    buttons.child(mapButton);
    String noticeKey = mapOffNoticeKey();
    if (!mapEditing && noticeKey != null) {
      LabelComponent outdated = labelFor(Component.translatable(noticeKey));
      outdated.id("map-server-outdated");
      outdated.horizontalSizing(Sizing.fill(100));
      root.childById(FlowLayout.class, "location-card").child(outdated);
    }
    return rows;
  }

  /**
   * Why the map is off, as a lang key: the server is too old, or BlueMap is unreachable. Null when
   * the map is on or simply switched off (server {@code world-map} or the client setting).
   */
  @Nullable
  static String mapOffNoticeKey() {
    if (!ClientSession.supportsMap()) {
      return "gui.disquests.map.server_outdated";
    }
    if (!ClientSession.isServerWorldMapEnabled()
        || !DisquestsClient.CONFIG.worldMap()
        || !ClientSession.hasBluemap()) {
      return null;
    }
    return ClientSession.mapUnreachable() ? "gui.disquests.map.map_unavailable" : null;
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

  /** The mini map's drawing of the quest's pin or outline, in the quest's colour. */
  private static MapLayer locationLayer(Quest quest) {
    // The card's map is in the quest's own dimension; draw a legacy quest with no map there too.
    QuestPolygonLayer layer = new QuestPolygonLayer().anyDimension(true);
    layer.setQuests(List.of(quest));
    return layer;
  }

  /**
   * Wraps the vertex list in a scroll area of the map's height when its rows would be taller, so a
   * many-cornered outline does not stretch the card.
   */
  private static void capVertexList(
      FlowLayout parent, FlowLayout list, int rowCount, int maxHeight) {
    // The parent is passed in: owo only sets parent() when the tree is mounted, after build().
    int height = rowCount * ROW_HEIGHT + Math.max(0, rowCount - 1) * VERTEX_LIST_GAP;
    if (height <= maxHeight || parent == null) return;
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
    if (ClientSession.mapAvailable()) {
      int[] size = mapSize(VIEW_MAP_WIDTH, VIEW_MAP_HEIGHT, screen.height, VIEW_MAP_SCREEN_DIVISOR);
      MapView map = addMiniMap(root, quest, screen.dimensionForEdit(), size[0], size[1]);
      map.addLayer(locationLayer(quest));
    }

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
   * origin) at zoom 2. With the player marker off an unlocated quest shows the remembered camera,
   * else the whole world.
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
    } else if (!ClientSession.isPlayerMarkerEnabled()) {
      MapMath.Camera remembered = ClientSession.rememberedCamera(dimension);
      map.setCamera(remembered != null ? remembered : MapCameraMemory.WORLD_VIEW);
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
