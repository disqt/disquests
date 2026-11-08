package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BlueMapSettingsTest {
  @Test
  void parse_arrayTileSize() {
    BlueMapSettings s =
        BlueMapSettings.parse(
            "{\"lowres\":{\"tileSize\":[400,400],\"lodFactor\":4,\"lodCount\":2}}");
    assertEquals(new BlueMapSettings(400, 4, 2), s);
  }

  @Test
  void parse_objectTileSize() {
    BlueMapSettings s =
        BlueMapSettings.parse(
            "{\"lowres\":{\"tileSize\":{\"x\":600,\"z\":600},\"lodFactor\":6,\"lodCount\":4}}");
    assertEquals(new BlueMapSettings(600, 6, 4), s);
  }

  @Test
  void parse_garbage_returnsDefault() {
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse("not json"));
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse("{}"));
    assertEquals(
        BlueMapSettings.DEFAULT, BlueMapSettings.parse("{\"lowres\":{\"tileSize\":[500]}}"));
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse(null));
  }

  @Test
  void parse_nonPositiveValues_returnDefault() {
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse(lowres("[0,0]", 5, 3)));
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse(lowres("[-500,-500]", 5, 3)));
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse(lowres("[20000,20000]", 5, 3)));
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse(lowres("[500,500]", 1, 3)));
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse(lowres("[500,500]", 0, 3)));
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse(lowres("[500,500]", 5, 0)));
    assertEquals(BlueMapSettings.DEFAULT, BlueMapSettings.parse(lowres("[500,500]", 5, -1)));
    assertEquals(new BlueMapSettings(1, 2, 1), BlueMapSettings.parse(lowres("[1,1]", 2, 1)));
  }

  private static String lowres(String tileSize, int lodFactor, int lodCount) {
    return "{\"lowres\":{\"tileSize\":"
        + tileSize
        + ",\"lodFactor\":"
        + lodFactor
        + ",\"lodCount\":"
        + lodCount
        + "}}";
  }

  @Test
  void default_is500_5_3() {
    assertEquals(new BlueMapSettings(500, 5, 3), BlueMapSettings.DEFAULT);
  }
}
