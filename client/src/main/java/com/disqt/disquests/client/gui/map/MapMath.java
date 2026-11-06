package com.disqt.disquests.client.gui.map;

/** Pure camera and tile maths for the world map. Screen x follows block x, screen y follows z. */
public final class MapMath {
  public static final int MAX_ZOOM = 3;

  private static final double[] BLOCKS_PER_PIXEL = {25, 5, 1, 0.5};
  private static final int[] LOD_FOR_ZOOM = {3, 2, 1, 1};

  private MapMath() {}

  public record Camera(double centerX, double centerZ, int zoom) {}

  public static double blocksPerPixel(int zoom) {
    return BLOCKS_PER_PIXEL[zoom];
  }

  public static int lodForZoom(int zoom) {
    return LOD_FOR_ZOOM[zoom];
  }

  /** Blocks covered by one tile at the given LOD (1-based). */
  public static double tileCoverage(BlueMapSettings s, int lod) {
    return s.tileSize() * Math.pow(s.lodFactor(), lod - 1);
  }

  public static int tileIndex(double block, double coverage) {
    return (int) Math.floor(block / coverage);
  }

  public static int worldLimit(String dimension) {
    return "the_nether".equals(dimension) ? 1250 : 5000;
  }

  /** True when any part of the tile lies within [-limit, limit) on both axes. */
  public static boolean tileInsideWorld(int tileX, int tileZ, double coverage, int limit) {
    return tileX * coverage < limit
        && (tileX + 1) * coverage > -limit
        && tileZ * coverage < limit
        && (tileZ + 1) * coverage > -limit;
  }

  public static double[] blockToScreen(Camera c, int viewW, int viewH, double bx, double bz) {
    double bpp = blocksPerPixel(c.zoom());
    return new double[] {
      viewW / 2.0 + (bx - c.centerX()) / bpp, viewH / 2.0 + (bz - c.centerZ()) / bpp
    };
  }

  public static double[] screenToBlock(Camera c, int viewW, int viewH, double relX, double relY) {
    double bpp = blocksPerPixel(c.zoom());
    return new double[] {
      c.centerX() + (relX - viewW / 2.0) * bpp, c.centerZ() + (relY - viewH / 2.0) * bpp
    };
  }

  public static Camera zoomAround(
      Camera c, int viewW, int viewH, double relX, double relY, int delta) {
    int zoom = Math.max(0, Math.min(MAX_ZOOM, c.zoom() + delta));
    if (zoom == c.zoom()) {
      return c;
    }
    double[] b = screenToBlock(c, viewW, viewH, relX, relY);
    double bpp = blocksPerPixel(zoom);
    return new Camera(b[0] - (relX - viewW / 2.0) * bpp, b[1] - (relY - viewH / 2.0) * bpp, zoom);
  }

  public static Camera pan(Camera c, double dxPixels, double dyPixels) {
    double bpp = blocksPerPixel(c.zoom());
    return new Camera(c.centerX() - dxPixels * bpp, c.centerZ() - dyPixels * bpp, c.zoom());
  }
}
