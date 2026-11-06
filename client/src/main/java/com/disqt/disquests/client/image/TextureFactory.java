package com.disqt.disquests.client.image;

import java.io.IOException;

/**
 * Turns image bytes into a GPU texture and releases it again. {@link #create} and {@link #release}
 * are always invoked on the cache's render executor.
 */
public interface TextureFactory {

  record Texture(String key, int width, int height) {}

  Texture create(String key, byte[] bytes) throws IOException;

  void release(String key);
}
