package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.map.MapMath.Camera;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * Pannable, zoomable BlueMap view. Draws low-resolution tiles from a {@link TileCache}, then its
 * {@link MapLayer}s in order, and routes input to the layers. Middle-drag pans, scroll zooms.
 *
 * <p>The view takes owo focus on mouse click only, because owo routes drags and releases to the
 * focused component. It never takes keyboard-cycle focus.
 */
public class MapView extends BaseUIComponent {
  private static final Component TILES_UNAVAILABLE =
      Component.translatable("gui.disquests.map.tiles_unavailable");
  // BlueMap lowres PNGs are 501x1002: colour in the top 500x500 texels, height data below.
  private static final int TILE_TEXELS = 500;
  private static final int TEXTURE_WIDTH = 501;
  private static final int TEXTURE_HEIGHT = 1002;

  private final TileCache cache;
  private final List<MapLayer> layers = new ArrayList<>();
  private String dimension = "overworld";
  private Camera camera = new Camera(0, 0, 2);
  private boolean interactive = true;
  private Component hoverTooltip;
  private Component shownTooltip;

  public MapView(TileCache cache) {
    this.cache = cache;
  }

  // --- State ---

  public MapView setDimension(String key) {
    this.dimension = key;
    return this;
  }

  public String getDimension() {
    return dimension;
  }

  public Camera getCamera() {
    return camera;
  }

  public void setCamera(Camera camera) {
    this.camera = camera;
  }

  public void centerOn(double bx, double bz) {
    camera = new Camera(bx, bz, camera.zoom());
  }

  public void setZoom(int zoom) {
    int clamped = Math.max(0, Math.min(MapMath.MAX_ZOOM, zoom));
    camera = new Camera(camera.centerX(), camera.centerZ(), clamped);
  }

  /** Zooms by {@code delta} steps, keeping the block under {@code relX/relY} in place. */
  public void zoomBy(int delta, double relX, double relY) {
    camera = MapMath.zoomAround(camera, width(), height(), relX, relY, delta);
  }

  public MapView addLayer(MapLayer layer) {
    layers.add(layer);
    return this;
  }

  /** The first layer of the given type, or null. */
  public <T extends MapLayer> T getLayer(Class<T> type) {
    for (MapLayer layer : layers) {
      if (type.isInstance(layer)) {
        return type.cast(layer);
      }
    }
    return null;
  }

  public MapView interactive(boolean interactive) {
    this.interactive = interactive;
    return this;
  }

  /** Component-relative screen position to block {x, z}. */
  public double[] screenToBlock(double relX, double relY) {
    return MapMath.screenToBlock(camera, width(), height(), relX, relY);
  }

  /** Block position to component-relative screen {x, y}. */
  public double[] blockToScreen(double bx, double bz) {
    return MapMath.blockToScreen(camera, width(), height(), bx, bz);
  }

  /** BlueMap map id for the current dimension, or null when the server has none. */
  public String mapId() {
    return ClientSession.getBluemapMapNames().get(dimension);
  }

  /** Sets the tooltip shown for this frame; cleared at the start of every draw. */
  public void setHoverTooltip(Component tooltip) {
    this.hoverTooltip = tooltip;
  }

  public boolean tilesUnavailable() {
    return !cache.isAvailable() || cache.lastError() != null || mapId() == null;
  }

  // --- Size ---

  @Override
  protected int determineHorizontalContentSize(Sizing sizing) {
    return 200;
  }

  @Override
  protected int determineVerticalContentSize(Sizing sizing) {
    return 200;
  }

  // --- Rendering ---

  @Override
  public void draw(OwoUIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
    hoverTooltip = null;
    int x = this.x();
    int y = this.y();
    int w = this.width();
    int h = this.height();
    g.enableScissor(x, y, x + w, y + h);
    try {
      g.fill(x, y, x + w, y + h, Colors.MAP_BACKGROUND);
      drawTiles(g);
      for (MapLayer layer : layers) {
        layer.draw(this, g, mouseX, mouseY);
      }
    } finally {
      g.disableScissor();
    }
    if (tilesUnavailable()) {
      Font font = Minecraft.getInstance().font;
      g.text(font, TILES_UNAVAILABLE, x + 4, y + h - 4 - font.lineHeight, Colors.TEXT_MUTED, false);
    }
    updateTooltip();
  }

  /** Tiles (when reachable) and the tile-boundary grid, which also shows when tiles are not. */
  private void drawTiles(OwoUIGraphics g) {
    String mapId = mapId();
    boolean tiles = mapId != null && cache.isAvailable();
    BlueMapSettings settings = tiles ? cache.settings(mapId) : BlueMapSettings.DEFAULT;
    int lod = Math.max(1, Math.min(MapMath.lodForZoom(camera.zoom()), settings.lodCount()));
    double coverage = MapMath.tileCoverage(settings, lod);
    int w = width();
    int h = height();
    double[] min = screenToBlock(0, 0);
    double[] max = screenToBlock(w, h);
    int tx0 = MapMath.tileIndex(min[0], coverage);
    int tx1 = MapMath.tileIndex(max[0], coverage);
    int tz0 = MapMath.tileIndex(min[1], coverage);
    int tz1 = MapMath.tileIndex(max[1], coverage);

    if (tiles) {
      int limit = MapMath.worldLimit(dimension);
      for (int tz = tz0; tz <= tz1; tz++) {
        int sy0 = screenY(tz * coverage);
        int sy1 = screenY((tz + 1) * coverage);
        for (int tx = tx0; tx <= tx1; tx++) {
          if (!MapMath.tileInsideWorld(tx, tz, coverage, limit)) {
            continue;
          }
          TileKey key = new TileKey(mapId, lod, tx, tz);
          if (cache.get(key) != TileStatus.LOADED) {
            continue;
          }
          int sx0 = screenX(tx * coverage);
          int sx1 = screenX((tx + 1) * coverage);
          g.blit(
              RenderPipelines.GUI_TEXTURED,
              McTileTextures.id(key),
              sx0,
              sy0,
              0f,
              0f,
              sx1 - sx0,
              sy1 - sy0,
              TILE_TEXELS,
              TILE_TEXELS,
              TEXTURE_WIDTH,
              TEXTURE_HEIGHT);
        }
      }
    }

    int left = this.x();
    int top = this.y();
    for (int tx = tx0 + 1; tx <= tx1; tx++) {
      int sx = screenX(tx * coverage);
      g.fill(sx, top, sx + 1, top + h, Colors.MAP_GRID);
    }
    for (int tz = tz0 + 1; tz <= tz1; tz++) {
      int sy = screenY(tz * coverage);
      g.fill(left, sy, left + w, sy + 1, Colors.MAP_GRID);
    }
  }

  /** Absolute screen x of a block x, rounded so adjacent tiles share their edges. */
  private int screenX(double bx) {
    return this.x() + (int) Math.round(width() / 2.0 + (bx - camera.centerX()) / bpp());
  }

  private int screenY(double bz) {
    return this.y() + (int) Math.round(height() / 2.0 + (bz - camera.centerZ()) / bpp());
  }

  private double bpp() {
    return MapMath.blocksPerPixel(camera.zoom());
  }

  /** Pushes the frame's hover tooltip to owo, rebuilding it only when it changes. */
  private void updateTooltip() {
    if (hoverTooltip == null) {
      if (shownTooltip != null) {
        tooltip((List<ClientTooltipComponent>) null);
        shownTooltip = null;
      }
      return;
    }
    if (!hoverTooltip.equals(shownTooltip)) {
      tooltip(List.of(ClientTooltipComponent.create(hoverTooltip.getVisualOrderText())));
      shownTooltip = hoverTooltip;
    }
  }

  // --- Input ---

  @Override
  public boolean canFocus(FocusSource source) {
    return interactive && source == FocusSource.MOUSE_CLICK;
  }

  @Override
  public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
    if (!interactive) {
      return false;
    }
    int button = click.button();
    if (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) {
      for (MapLayer layer : layers) {
        if (layer.onMouseDown(this, click.x(), click.y(), button)) {
          break;
        }
      }
    }
    return true;
  }

  @Override
  public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
    if (!interactive) {
      return false;
    }
    int button = click.button();
    if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
      camera = MapMath.pan(camera, deltaX, deltaY);
      return true;
    }
    for (MapLayer layer : layers) {
      if (layer.onMouseDrag(this, click.x(), click.y(), button, deltaX, deltaY)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean onMouseUp(MouseButtonEvent click) {
    if (!interactive) {
      return false;
    }
    for (MapLayer layer : layers) {
      if (layer.onMouseUp(this, click.x(), click.y(), click.button())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
    if (!interactive) {
      return false;
    }
    zoomBy((int) Math.signum(amount), mouseX, mouseY);
    return true;
  }

  @Override
  public boolean onKeyPress(KeyEvent event) {
    if (!interactive) {
      return false;
    }
    for (MapLayer layer : layers) {
      if (layer.onKeyPress(this, event)) {
        return true;
      }
    }
    return false;
  }
}
