package com.disqt.disquests.client.image;

import java.io.IOException;

/** Fetches the raw bytes of an image URL. Called on the cache's IO executor. */
public interface ImageFetcher {
  byte[] fetch(String url) throws IOException;
}
