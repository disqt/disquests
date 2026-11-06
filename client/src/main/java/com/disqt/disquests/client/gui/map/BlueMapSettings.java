package com.disqt.disquests.client.gui.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** BlueMap low-resolution tile layout, read from a map's settings.json. */
public record BlueMapSettings(int tileSize, int lodFactor, int lodCount) {
  public static final BlueMapSettings DEFAULT = new BlueMapSettings(500, 5, 3);

  /**
   * Parses {@code lowres.tileSize/lodFactor/lodCount}; any missing field or error gives DEFAULT.
   */
  public static BlueMapSettings parse(String json) {
    try {
      JsonObject lowres = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("lowres");
      int tileSize = tileSizeX(lowres.get("tileSize"));
      int lodFactor = lowres.get("lodFactor").getAsInt();
      int lodCount = lowres.get("lodCount").getAsInt();
      return new BlueMapSettings(tileSize, lodFactor, lodCount);
    } catch (RuntimeException e) {
      return DEFAULT;
    }
  }

  private static int tileSizeX(JsonElement e) {
    if (e.isJsonArray()) {
      return e.getAsJsonArray().get(0).getAsInt();
    }
    return e.getAsJsonObject().get("x").getAsInt();
  }
}
