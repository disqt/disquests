package com.disqt.disquests.client.gui.map;

import java.io.IOException;

/**
 * Turns tile PNGs into GPU textures in two steps: {@link #decode} runs on a fetch thread so the
 * render thread never decodes images; {@link #upload} and {@link #release} run on the render
 * thread.
 */
public interface TileTextures {
  /**
   * Decodes a tile PNG and keeps only its top-left {@code tileSize x tileSize} colour region (the
   * rest of a BlueMap lowres tile is height data). Runs on a fetch thread.
   */
  Object decode(byte[] png, int tileSize) throws IOException;

  /**
   * Uploads a {@link #decode decoded} image and takes ownership of it. Returns {width, height} of
   * the texture. Render thread only.
   */
  int[] upload(TileKey key, Object decoded) throws IOException;

  /** Frees a decoded image that will never be uploaded. Any thread. */
  void discard(Object decoded);

  void release(TileKey key);
}
