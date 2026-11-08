package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BannerMarkersTest {
  static final String SAMPLE =
      """
      {
        "world-border": {
          "label": "World Border",
          "markers": {
            "border": {"type": "shape", "label": "Border", "position": {"x": 0, "y": 0, "z": 0},
              "shape": [{"x": -1, "z": -1}, {"x": 1, "z": 1}]}
          }
        },
        "BANNER_MARKER_world_new": {
          "label": "Banners",
          "markers": {
            "abc": {
              "type": "poi",
              "label": "L'Amine",
              "position": {"x": 1301, "y": 69, "z": -2784},
              "icon": "data/images/plugins/BlueMap-BannerMarker/assets/marker_YELLOW.png.png",
              "detail": "L'Amine"
            }
          }
        }
      }
      """;

  @Test
  void parsesBannerPoiAndIgnoresOtherSets() {
    List<BannerMarker> markers = BannerMarkers.parse(SAMPLE);
    assertEquals(1, markers.size());
    BannerMarker m = markers.get(0);
    assertEquals("L'Amine", m.label());
    assertEquals(1301, m.x());
    assertEquals(-2784, m.z());
    assertEquals(0xFFFED83D, m.argb());
  }

  @Test
  void toleratesGarbage() {
    assertTrue(BannerMarkers.parse("not json").isEmpty());
    assertTrue(BannerMarkers.parse("").isEmpty());
    assertTrue(BannerMarkers.parse("[1, 2]").isEmpty());
    assertTrue(BannerMarkers.parse("{\"BANNER_MARKER_x\": 3}").isEmpty());
    assertTrue(
        BannerMarkers.parse("{\"BANNER_MARKER_x\": {\"markers\": {\"a\": {\"type\": \"poi\"}}}}")
            .isEmpty());
  }

  @Test
  void unknownOrMissingColourIsWhite() {
    assertEquals(BannerMarkers.WHITE, BannerMarkers.colorFromIcon("assets/marker_CHARTREUSE.png"));
    assertEquals(BannerMarkers.WHITE, BannerMarkers.colorFromIcon(null));
    assertEquals(BannerMarkers.WHITE, BannerMarkers.colorFromIcon("icon.png"));
    assertEquals(0xFF3AB3DA, BannerMarkers.colorFromIcon("x/marker_LIGHT_BLUE.png.png"));
  }
}
