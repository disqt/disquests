package com.disqt.disquests.client.image;

import java.io.IOException;

/**
 * Decodes image bytes and turns the result into a GPU texture. {@link #decode} runs on the cache's
 * IO executor (it may be slow); {@link #upload} and {@link #release} run on the render executor.
 */
public interface TextureFactory {

  /** A decoded image waiting to be uploaded. Closing it frees the pixel memory. */
  interface Decoded extends AutoCloseable {
    int width();

    int height();

    @Override
    void close();
  }

  record Texture(String key, int width, int height) {}

  /** Decodes bytes into pixels. IO thread. Throws on anything that is not a decodable image. */
  Decoded decode(byte[] bytes) throws IOException;

  /**
   * Registers {@code decoded} as a texture under {@code key}. Render thread. On success the factory
   * owns (and eventually frees) the decoded pixels; on failure the caller closes {@code decoded}.
   */
  Texture upload(String key, Decoded decoded) throws IOException;

  void release(String key);
}
