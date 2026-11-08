package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.map.BannerLayer;
import com.disqt.disquests.client.gui.map.MapView;
import com.disqt.disquests.client.gui.map.PlayerMarkerLayer;
import com.disqt.disquests.client.gui.map.PolygonEditLayer;
import com.disqt.disquests.client.gui.map.QuestStyle;
import com.disqt.disquests.client.gui.map.SharedTileCache;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Sizing;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Full-screen map editor for a quest's location, shown modally over its {@link QuestScreen}. Opened
 * with {@code setScreen}, not {@code navigateToScreen}, so it never enters the back history.
 * Finishing applies the drawn shape to the editor; cancelling returns to it unchanged.
 */
public class MapEditScreen extends DisquestsBaseScreen {
  private static final int INITIAL_ZOOM = 2;
  private static final int HINT_MIN_WIDTH = 100;
  private static final Component EDIT_HINT = Component.translatable("gui.disquests.map.edit_hint");

  private final QuestScreen editor;
  private final String dimension;
  private final List<PointData> initial;
  private MapView mapView;
  private PolygonEditLayer layer;
  private LabelComponent hintLabel;
  private String shownNotice;

  public MapEditScreen(QuestScreen editor, String dimension, List<PointData> initial) {
    super(
        DataSource.asset(Identifier.fromNamespaceAndPath("disquests", "map_edit_screen")), editor);
    this.editor = editor;
    this.dimension = dimension;
    this.initial = List.copyOf(initial);
  }

  @Override
  public NavEntry toNavEntry() {
    return editor.toNavEntry();
  }

  public MapView getMapView() {
    return mapView;
  }

  @Override
  protected void build(FlowLayout root) {
    applyThemeRoot(root);

    this.layer = new PolygonEditLayer(initial);
    Quest quest = editor.getQuest();
    this.layer.color(QuestStyle.outlineColor(quest.getId(), quest.getVisibility()));
    this.layer.onFinish(editor::applyMapLocation);
    this.mapView = new MapView(SharedTileCache.get());
    this.mapView.interactive(true).setDimension(dimension);
    this.mapView
        .addLayer(new BannerLayer(SharedTileCache.banners()))
        .addLayer(new PlayerMarkerLayer())
        .addLayer(layer);
    this.mapView.sizing(Sizing.fill(100), Sizing.fill(100));
    this.mapView.id("map-view");
    root.childById(FlowLayout.class, "map-slot").child(mapView);
    mapView.setZoom(INITIAL_ZOOM);
    centreCamera();

    this.hintLabel = root.childById(LabelComponent.class, "hint-label");
    hintLabel.text(EDIT_HINT);
    hintLabel.maxWidth(Math.max(HINT_MIN_WIDTH, this.width - 16));
    hintLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);

    ButtonComponent done = root.childById(ButtonComponent.class, "btn-map-done");
    done.setMessage(Component.translatable("gui.disquests.btn.done"));
    done.onPress(ignored -> layer.tryFinish());
    ButtonComponent clear = root.childById(ButtonComponent.class, "btn-map-clear");
    clear.setMessage(Component.translatable("gui.disquests.btn.clear_location"));
    clear.onPress(ignored -> editor.applyMapLocation(List.of()));
    ButtonComponent cancel = root.childById(ButtonComponent.class, "btn-map-cancel");
    cancel.setMessage(Component.translatable("gui.disquests.btn.cancel_map"));
    cancel.onPress(ignored -> cancel());
  }

  /** The initial shape's bounding-box centre, else the player (same dimension), else (0, 0). */
  private void centreCamera() {
    if (!initial.isEmpty()) {
      CoordinatesData[] box = QuestLocation.boundingBox(initial, 0);
      mapView.centerOn((box[0].x() + box[1].x()) / 2, (box[0].z() + box[1].z()) / 2);
      return;
    }
    LocalPlayer player = minecraft != null ? minecraft.player : null;
    if (player != null && player.level().dimension().identifier().getPath().equals(dimension)) {
      mapView.centerOn(player.getX(), player.getZ());
    } else {
      mapView.centerOn(0, 0);
    }
  }

  @Override
  public void tick() {
    super.tick();
    String notice = layer != null ? layer.notice() : null;
    if (hintLabel != null && !Objects.equals(notice, shownNotice)) {
      hintLabel.text(notice != null ? Component.literal(notice) : EDIT_HINT);
      shownNotice = notice;
    }
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (event.key() == InputConstants.KEY_ESCAPE) {
      cancel();
      return true;
    }
    // Once clicked, the map view holds owo focus and super already routes keys to its layers.
    if (super.keyPressed(event)) {
      return true;
    }
    // Before the first click nothing is focused, so Enter would never reach the edit layer.
    if (event.key() == InputConstants.KEY_RETURN && mapView != null) {
      return mapView.onKeyPress(event);
    }
    return false;
  }

  @Override
  public void onClose() {
    cancel();
  }

  private void cancel() {
    if (minecraft != null) {
      minecraft.gui.setScreen(editor);
    }
  }
}
