package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.gui.map.MapMath.Camera;
import org.junit.jupiter.api.Test;

class MapMathTest {
  private static final BlueMapSettings S = BlueMapSettings.DEFAULT;

  @Test
  void tileCoverage_lod1_500_lod2_2500_lod3_12500() {
    assertEquals(500.0, MapMath.tileCoverage(S, 1), 1e-9);
    assertEquals(2500.0, MapMath.tileCoverage(S, 2), 1e-9);
    assertEquals(12500.0, MapMath.tileCoverage(S, 3), 1e-9);
  }

  @Test
  void tileIndex_negativeFloors() {
    assertEquals(0, MapMath.tileIndex(0, 500));
    assertEquals(-1, MapMath.tileIndex(-1, 500));
    assertEquals(-1, MapMath.tileIndex(-500, 500));
    assertEquals(-2, MapMath.tileIndex(-501, 500));
  }

  @Test
  void zoomTables() {
    assertEquals(25.0, MapMath.blocksPerPixel(0));
    assertEquals(5.0, MapMath.blocksPerPixel(1));
    assertEquals(1.0, MapMath.blocksPerPixel(2));
    assertEquals(0.5, MapMath.blocksPerPixel(3));
    assertEquals(3, MapMath.lodForZoom(0));
    assertEquals(2, MapMath.lodForZoom(1));
    assertEquals(1, MapMath.lodForZoom(2));
    assertEquals(1, MapMath.lodForZoom(3));
  }

  @Test
  void blockToScreen_centreIsMiddle() {
    double[] p = MapMath.blockToScreen(new Camera(100, 200, 2), 400, 300, 100, 200);
    assertEquals(200.0, p[0], 1e-9);
    assertEquals(150.0, p[1], 1e-9);
  }

  @Test
  void blockToScreen_offsetScalesByZoom() {
    double[] p = MapMath.blockToScreen(new Camera(0, 0, 1), 400, 300, 50, -25);
    assertEquals(210.0, p[0], 1e-9);
    assertEquals(145.0, p[1], 1e-9);
  }

  @Test
  void screenToBlock_inverseAtEachZoom() {
    for (int z = 0; z <= MapMath.MAX_ZOOM; z++) {
      Camera c = new Camera(-1234.5, 678.9, z);
      double[] s = MapMath.blockToScreen(c, 400, 300, 321.0, -77.0);
      double[] b = MapMath.screenToBlock(c, 400, 300, s[0], s[1]);
      assertEquals(321.0, b[0], 1e-9);
      assertEquals(-77.0, b[1], 1e-9);
    }
  }

  @Test
  void zoomAround_keepsBlockUnderCursor() {
    Camera c = new Camera(100, 200, 1);
    double[] before = MapMath.screenToBlock(c, 400, 300, 50, 20);
    Camera n = MapMath.zoomAround(c, 400, 300, 50, 20, 1);
    assertEquals(2, n.zoom());
    double[] after = MapMath.screenToBlock(n, 400, 300, 50, 20);
    assertEquals(before[0], after[0], 1e-9);
    assertEquals(before[1], after[1], 1e-9);
  }

  @Test
  void zoomAround_clampsAtBounds() {
    Camera top = new Camera(10, 20, 3);
    Camera a = MapMath.zoomAround(top, 400, 300, 50, 20, 1);
    assertEquals(top, a);
    Camera bottom = new Camera(10, 20, 0);
    Camera b = MapMath.zoomAround(bottom, 400, 300, 50, 20, -1);
    assertEquals(bottom, b);
  }

  @Test
  void zoomAround_atWindowEdge_keepsBlock() {
    Camera c = new Camera(-300, 450, 0);
    double[][] cursors = {{0, 0}, {400, 300}};
    for (double[] cur : cursors) {
      double[] before = MapMath.screenToBlock(c, 400, 300, cur[0], cur[1]);
      Camera n = MapMath.zoomAround(c, 400, 300, cur[0], cur[1], 2);
      assertEquals(2, n.zoom());
      double[] after = MapMath.screenToBlock(n, 400, 300, cur[0], cur[1]);
      assertEquals(before[0], after[0], 1e-9);
      assertEquals(before[1], after[1], 1e-9);
    }
  }

  @Test
  void pan_dragRightMovesCentreLeft() {
    Camera n = MapMath.pan(new Camera(100, 100, 1), 10, -4);
    assertEquals(50.0, n.centerX(), 1e-9);
    assertEquals(120.0, n.centerZ(), 1e-9);
    assertEquals(1, n.zoom());
  }

  @Test
  void tileInsideWorld_rejectsBeyondLimit() {
    assertFalse(MapMath.tileInsideWorld(10, 0, 500, 5000));
    assertTrue(MapMath.tileInsideWorld(9, 0, 500, 5000));
    assertFalse(MapMath.tileInsideWorld(-11, 0, 500, 5000));
    assertTrue(MapMath.tileInsideWorld(-10, 0, 500, 5000));
    assertFalse(MapMath.tileInsideWorld(0, 10, 500, 5000));
    assertTrue(MapMath.tileInsideWorld(0, -10, 500, 5000));
  }

  @Test
  void worldLimit_nether1250() {
    assertEquals(1250, MapMath.worldLimit("the_nether"));
    assertEquals(5000, MapMath.worldLimit("overworld"));
    assertEquals(5000, MapMath.worldLimit("the_end"));
  }

  @Test
  void fitZoom_picksMostZoomedInThatFits() {
    // 150 x 50 blocks in a 200 x 100 view (192 x 92 usable): 0.5 bpp needs 300 px, 1 bpp fits.
    assertEquals(2, MapMath.fitZoom(150, 50, 200, 100, 8));
    assertEquals(MapMath.MAX_ZOOM, MapMath.fitZoom(10, 10, 200, 100, 8));
    // Height decides: 1000 blocks tall is 200 px at 5 bpp, too tall for 92 px; 40 px at 25 bpp.
    assertEquals(0, MapMath.fitZoom(10, 1000, 200, 100, 8));
  }

  @Test
  void fitZoom_fallsBackToMostZoomedOut() {
    assertEquals(0, MapMath.fitZoom(100000, 100000, 200, 100, 8));
  }
}
