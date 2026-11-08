package com.disqt.disquests.client.gui.map;

/** Identifies one BlueMap tile: map, level of detail and tile coordinates. */
public record TileKey(String mapId, int lod, int x, int z) {
  public String texturePath() {
    return "tile/" + mapId + "/" + lod + "/x" + x + "/z" + z;
  }
}
