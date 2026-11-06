package com.disqt.disquests.client.gui.map;

import java.io.IOException;

/** Uploads decoded tiles as GPU textures. Called on the render thread only. */
public interface TileTextures {
  /** Returns {width, height} of the uploaded texture. */
  int[] upload(TileKey key, byte[] png) throws IOException;

  void release(TileKey key);
}
