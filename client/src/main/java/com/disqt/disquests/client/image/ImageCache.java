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
import java.util.function.LongSupplier;

/**
 * Session cache of decoded images keyed by URL. Fetch and decode run on {@code ioExecutor}; texture
 * upload and release run on {@code renderExecutor}. Loaded entries are evicted least recently used
 * once there are more than {@code maxEntries} or more than {@code maxPixelBytes} of pixels (width ×
 * height × 4), except entries requested within the last {@link #PIN_WINDOW_NANOS}: an open quest
 * asks for its images every frame, and evicting those would refetch them in a loop, so the budget
 * may be exceeded by what is on screen. Failures are remembered for the session. Images over {@link
 * #MAX_SIDE} pixels per side or {@link #MAX_PIXELS} pixels are refused. Plain Java: no Minecraft
 * types.
 */
public final class ImageCache {

  public static final int MAX_SIDE = 4096;
  public static final long MAX_PIXELS = 16_000_000L;
  static final long PIN_WINDOW_NANOS = 2_000_000_000L;

  private static final class Entry {
    final ImageState.Loaded state;
    long lastAccess;

    Entry(ImageState.Loaded state, long lastAccess) {
      this.state = state;
      this.lastAccess = lastAccess;
    }
  }

  private final ImageFetcher fetcher;
  private final TextureFactory textures;
  private final Executor ioExecutor;
  private final Executor renderExecutor;
  private final int maxEntries;
  private final long maxPixelBytes;
  private final LongSupplier clock;

  private final LinkedHashMap<String, Entry> loaded = new LinkedHashMap<>(16, 0.75f, true);
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
    this(
        fetcher, textures, ioExecutor, renderExecutor, maxEntries, maxPixelBytes, System::nanoTime);
  }

  public ImageCache(
      ImageFetcher fetcher,
      TextureFactory textures,
      Executor ioExecutor,
      Executor renderExecutor,
      int maxEntries,
      long maxPixelBytes,
      LongSupplier clock) {
    this.fetcher = fetcher;
    this.textures = textures;
    this.ioExecutor = ioExecutor;
    this.renderExecutor = renderExecutor;
    this.maxEntries = maxEntries;
    this.maxPixelBytes = maxPixelBytes;
    this.clock = clock;
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
      ImageState known = peekAndTouch(url);
      if (known != null) return known;
      if (!inFlight.add(url)) return new ImageState.Loading();
    }
    ioExecutor.execute(() -> fetchAndDecode(url));
    // With a direct executor the fetch has already settled; report what it produced.
    synchronized (this) {
      ImageState known = peekAndTouch(url);
      return known != null ? known : new ImageState.Loading();
    }
  }

  public synchronized int loadedCount() {
    return loaded.size();
  }

  /**
   * Failed or Loaded state if known (touching a loaded entry), else null. Caller holds the lock.
   */
  private ImageState peekAndTouch(String url) {
    ImageState.Failed f = failed.get(url);
    if (f != null) return f;
    Entry e = loaded.get(url); // access-ordered map: this moves the entry to the tail
    if (e != null) {
      e.lastAccess = clock.getAsLong();
      return e.state;
    }
    return null;
  }

  private void fetchAndDecode(String url) {
    byte[] bytes;
    try {
      bytes = fetcher.fetch(url);
    } catch (Exception e) {
      fail(url, "fetch: " + e.getMessage());
      return;
    }
    TextureFactory.Decoded decoded;
    try {
      decoded = textures.decode(bytes);
    } catch (Exception e) {
      fail(url, "decode: " + e.getMessage());
      return;
    }
    int w = decoded.width();
    int h = decoded.height();
    if (w > MAX_SIDE || h > MAX_SIDE || (long) w * h > MAX_PIXELS) {
      decoded.close();
      fail(url, "decode: too large " + w + "x" + h);
      return;
    }
    renderExecutor.execute(() -> upload(url, decoded));
  }

  private void upload(String url, TextureFactory.Decoded decoded) {
    String key = textureKey(url);
    TextureFactory.Texture tex;
    try {
      tex = textures.upload(key, decoded);
    } catch (Exception e) {
      decoded.close();
      fail(url, "decode: upload failed: " + e.getMessage());
      return;
    }
    List<String> evicted = new ArrayList<>();
    synchronized (this) {
      long now = clock.getAsLong();
      inFlight.remove(url);
      loaded.put(url, new Entry(new ImageState.Loaded(tex.key(), tex.width(), tex.height()), now));
      pixelBytes += pixelBytes(tex.width(), tex.height());
      Iterator<Map.Entry<String, Entry>> it = loaded.entrySet().iterator();
      while ((loaded.size() > maxEntries || pixelBytes > maxPixelBytes) && it.hasNext()) {
        Map.Entry<String, Entry> candidate = it.next();
        if (now - candidate.getValue().lastAccess < PIN_WINDOW_NANOS) continue; // on screen
        ImageState.Loaded s = candidate.getValue().state;
        pixelBytes -= pixelBytes(s.width(), s.height());
        evicted.add(s.textureKey());
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
