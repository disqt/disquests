package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.net.FetchResult;
import com.disqt.disquests.client.net.HttpFetcher;
import com.disqt.disquests.client.net.TileFetcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Asynchronous BlueMap tile cache: memory LRU of GPU textures over a disk cache over HTTP. Free of
 * Minecraft classes; textures and threads are injected. {@link #get} is cheap and never blocks.
 */
public final class TileCache {
  public record Stats(int loaded, int missing, int fetches, int evictions) {}

  private final String baseUrl;
  private final TileFetcher fetcher;
  private final TileTextures textures;
  private final Executor fetchPool;
  private final Executor renderThread;
  private final Path diskDir;
  private final int maxTextures;

  // All guarded by "this".
  private final LinkedHashMap<TileKey, Boolean> loaded = new LinkedHashMap<>(16, 0.75f, true);
  private final Set<TileKey> loading = new HashSet<>();
  private final Set<TileKey> missing = new HashSet<>();
  private final Map<String, BlueMapSettings> settings = new HashMap<>();
  private final Set<String> settingsRequested = new HashSet<>();
  private int fetches;
  private int evictions;
  private boolean closed;

  private volatile String lastError;

  public TileCache(
      String baseUrl,
      TileFetcher fetcher,
      TileTextures textures,
      Executor fetchPool,
      Executor renderThread,
      Path diskDir,
      int maxTextures) {
    String base = baseUrl == null ? "" : baseUrl;
    this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    this.fetcher = fetcher;
    this.textures = textures;
    this.fetchPool = fetchPool;
    this.renderThread = renderThread;
    this.diskDir = diskDir;
    this.maxTextures = maxTextures;
  }

  public boolean isAvailable() {
    return !baseUrl.isEmpty() && HttpFetcher.isAllowedUrl(baseUrl);
  }

  public String lastError() {
    return lastError;
  }

  public String tileUrl(TileKey key) {
    return baseUrl
        + "/maps/"
        + key.mapId()
        + "/tiles/"
        + key.lod()
        + "/x"
        + key.x()
        + "/z"
        + key.z()
        + ".png";
  }

  public synchronized Stats stats() {
    return new Stats(loaded.size(), missing.size(), fetches, evictions);
  }

  public TileStatus get(TileKey key) {
    if (key.mapId() == null || key.mapId().isEmpty()) {
      return TileStatus.MISSING;
    }
    synchronized (this) {
      if (closed || missing.contains(key)) {
        return TileStatus.MISSING;
      }
      if (loaded.get(key) != null) {
        return TileStatus.LOADED;
      }
      if (!loading.add(key)) {
        return TileStatus.LOADING;
      }
    }
    try {
      fetchPool.execute(() -> loadTile(key));
    } catch (RuntimeException e) {
      lastError = String.valueOf(e);
      markMissing(key);
      return TileStatus.MISSING;
    }
    return TileStatus.LOADING;
  }

  public BlueMapSettings settings(String mapId) {
    if (mapId == null || mapId.isEmpty()) {
      return BlueMapSettings.DEFAULT;
    }
    synchronized (this) {
      BlueMapSettings known = settings.get(mapId);
      if (known != null) {
        return known;
      }
      if (closed || !settingsRequested.add(mapId)) {
        return BlueMapSettings.DEFAULT;
      }
    }
    try {
      fetchPool.execute(() -> loadSettings(mapId));
    } catch (RuntimeException e) {
      lastError = String.valueOf(e);
    }
    return BlueMapSettings.DEFAULT;
  }

  public void close() {
    List<TileKey> toRelease;
    synchronized (this) {
      closed = true;
      toRelease = new ArrayList<>(loaded.keySet());
      loaded.clear();
      loading.clear();
    }
    for (TileKey key : toRelease) {
      renderThread.execute(() -> textures.release(key));
    }
  }

  private void loadSettings(String mapId) {
    try {
      loadSettingsUnchecked(mapId);
    } catch (RuntimeException e) {
      lastError = String.valueOf(e);
    }
  }

  private void loadSettingsUnchecked(String mapId) {
    FetchResult r = fetchTracked(baseUrl + "/maps/" + mapId + "/settings.json");
    if (r == null || r.status() != 200) {
      return;
    }
    BlueMapSettings parsed = BlueMapSettings.parse(new String(r.body(), StandardCharsets.UTF_8));
    synchronized (this) {
      settings.put(mapId, parsed);
    }
  }

  private void loadTile(TileKey key) {
    try {
      loadTileUnchecked(key);
    } catch (RuntimeException e) {
      lastError = String.valueOf(e);
      markMissing(key);
    }
  }

  private void loadTileUnchecked(TileKey key) {
    String url = tileUrl(key);
    byte[] png = readDisk(url);
    if (png == null) {
      FetchResult r = fetchTracked(url);
      if (r == null
          || r.status() != 200
          || r.contentType() == null
          || !r.contentType().toLowerCase(Locale.ROOT).trim().startsWith("image/png")) {
        markMissing(key);
        return;
      }
      png = r.body();
      writeDisk(url, png, r.maxAgeSeconds());
    }
    Object decoded;
    try {
      decoded = textures.decode(png, knownSettings(key.mapId()).tileSize());
    } catch (IOException | RuntimeException e) {
      deleteDisk(url);
      markMissing(key);
      return;
    }
    try {
      renderThread.execute(() -> upload(key, decoded, url));
    } catch (RuntimeException e) {
      // No render thread to hand the image to (shutting down).
      textures.discard(decoded);
      markMissing(key);
    }
  }

  /** Settings already fetched for a map, or DEFAULT; never triggers a fetch. */
  private synchronized BlueMapSettings knownSettings(String mapId) {
    BlueMapSettings known = settings.get(mapId);
    return known == null ? BlueMapSettings.DEFAULT : known;
  }

  /** Runs on the render thread. */
  private void upload(TileKey key, Object decoded, String url) {
    synchronized (this) {
      if (closed) {
        textures.discard(decoded);
        return;
      }
    }
    try {
      textures.upload(key, decoded);
    } catch (IOException | RuntimeException e) {
      textures.discard(decoded);
      deleteDisk(url);
      markMissing(key);
      return;
    }
    List<TileKey> evicted = new ArrayList<>();
    boolean wasClosed;
    synchronized (this) {
      wasClosed = closed;
      if (!wasClosed) {
        loading.remove(key);
        loaded.put(key, Boolean.TRUE);
        Iterator<TileKey> it = loaded.keySet().iterator();
        while (loaded.size() > maxTextures && it.hasNext()) {
          evicted.add(it.next());
          it.remove();
          evictions++;
        }
      }
    }
    if (wasClosed) {
      textures.release(key);
    }
    for (TileKey old : evicted) {
      renderThread.execute(() -> textures.release(old));
    }
  }

  private synchronized void markMissing(TileKey key) {
    loading.remove(key);
    missing.add(key);
  }

  /** Runs one network fetch; null on IOException (recorded in lastError). */
  private FetchResult fetchTracked(String url) {
    synchronized (this) {
      fetches++;
    }
    try {
      FetchResult r = fetcher.fetch(url);
      lastError = null;
      return r;
    } catch (IOException e) {
      lastError = e.getMessage() == null ? e.toString() : e.getMessage();
      return null;
    }
  }

  private Path file(String url, String ext) {
    return diskDir.resolve(sha1(url) + ext);
  }

  private byte[] readDisk(String url) {
    try {
      long expiry = Long.parseLong(Files.readString(file(url, ".meta")).trim());
      if (expiry <= System.currentTimeMillis()) {
        return null;
      }
      return Files.readAllBytes(file(url, ".png"));
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /** Body first, meta second, so a half-written entry is never valid. */
  private void writeDisk(String url, byte[] png, long maxAgeSeconds) {
    try {
      Files.createDirectories(diskDir);
      Files.write(file(url, ".png"), png);
      long expiry = System.currentTimeMillis() + maxAgeSeconds * 1000;
      Files.writeString(file(url, ".meta"), Long.toString(expiry));
    } catch (IOException | RuntimeException e) {
      // The memory cache still works without the disk entry.
    }
  }

  private void deleteDisk(String url) {
    try {
      Files.deleteIfExists(file(url, ".meta"));
      Files.deleteIfExists(file(url, ".png"));
    } catch (IOException | RuntimeException e) {
      // Best effort.
    }
  }

  private static String sha1(String s) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : d) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
