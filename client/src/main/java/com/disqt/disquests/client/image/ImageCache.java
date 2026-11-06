package com.disqt.disquests.client.image;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Session cache of decoded images keyed by URL. Fetches run on {@code ioExecutor}; texture creation
 * and release run on {@code renderExecutor}. Loaded entries are evicted least recently used once
 * there are more than {@code maxEntries} or more than {@code maxPixelBytes} of pixels (width ×
 * height × 4). Failures are remembered for the session. Plain Java: no Minecraft types.
 */
public final class ImageCache {

  private final ImageFetcher fetcher;
  private final TextureFactory textures;
  private final Executor ioExecutor;
  private final Executor renderExecutor;
  private final int maxEntries;
  private final long maxPixelBytes;

  private final LinkedHashMap<String, ImageState.Loaded> loaded =
      new LinkedHashMap<>(16, 0.75f, true);
  private final Set<String> inFlight = new HashSet<>();
  private final Map<String, ImageState.Failed> failed = new HashMap<>();
  private long pixelBytes = 0;

  public ImageCache(
      ImageFetcher fetcher,
      TextureFactory textures,
      Executor ioExecutor,
      Executor renderExecutor,
      int maxEntries,
      long maxPixelBytes) {
    this.fetcher = fetcher;
    this.textures = textures;
    this.ioExecutor = ioExecutor;
    this.renderExecutor = renderExecutor;
    this.maxEntries = maxEntries;
    this.maxPixelBytes = maxPixelBytes;
  }

  /** Texture key for a URL: {@code img/<sha256 hex of the url>}. */
  public static String textureKey(String url) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return "img/" + HexFormat.of().formatHex(md.digest(url.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Current state for {@code url}; starts a fetch the first time an unknown URL is asked for. */
  public ImageState get(String url) {
    synchronized (this) {
      ImageState.Failed f = failed.get(url);
      if (f != null) return f;
      ImageState.Loaded l = loaded.get(url); // access-ordered: this touches the entry
      if (l != null) return l;
      if (!inFlight.add(url)) return new ImageState.Loading();
    }
    ioExecutor.execute(() -> fetch(url));
    // With a direct executor the fetch has already settled; report what it produced.
    return peek(url);
  }

  private synchronized ImageState peek(String url) {
    ImageState.Failed f = failed.get(url);
    if (f != null) return f;
    ImageState.Loaded l = loaded.get(url);
    if (l != null) return l;
    return new ImageState.Loading();
  }

  public synchronized int loadedCount() {
    return loaded.size();
  }

  private void fetch(String url) {
    byte[] bytes;
    try {
      bytes = fetcher.fetch(url);
    } catch (Exception e) {
      fail(url, "fetch: " + e.getMessage());
      return;
    }
    renderExecutor.execute(() -> decode(url, bytes));
  }

  private void decode(String url, byte[] bytes) {
    String key = textureKey(url);
    TextureFactory.Texture tex;
    try {
      tex = textures.create(key, bytes);
    } catch (Exception e) {
      fail(url, "decode: " + e.getMessage());
      return;
    }
    List<String> evicted = new ArrayList<>();
    synchronized (this) {
      inFlight.remove(url);
      loaded.put(url, new ImageState.Loaded(tex.key(), tex.width(), tex.height()));
      pixelBytes += pixelBytes(tex.width(), tex.height());
      Iterator<Map.Entry<String, ImageState.Loaded>> it = loaded.entrySet().iterator();
      while ((loaded.size() > maxEntries || pixelBytes > maxPixelBytes) && it.hasNext()) {
        Map.Entry<String, ImageState.Loaded> oldest = it.next();
        if (oldest.getKey().equals(url)) continue; // never evict the entry just added
        pixelBytes -= pixelBytes(oldest.getValue().width(), oldest.getValue().height());
        evicted.add(oldest.getValue().textureKey());
        it.remove();
      }
    }
    for (String k : evicted) textures.release(k);
  }

  private synchronized void fail(String url, String reason) {
    inFlight.remove(url);
    failed.put(url, new ImageState.Failed(reason));
  }

  private static long pixelBytes(int width, int height) {
    return (long) width * height * 4L;
  }
}
