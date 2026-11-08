package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.net.FetchResult;
import com.disqt.disquests.client.net.HttpFetcher;
import com.disqt.disquests.client.net.TileFetcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * BlueMap banner markers per map id, fetched once per session in the background. No Minecraft
 * imports. {@link #get} never blocks.
 */
public final class BannerCache {
  public enum Status {
    LOADING,
    LOADED,
    FAILED
  }

  private final String baseUrl;
  private final TileFetcher fetcher;
  private final Executor pool;

  // Guarded by "this".
  private final Map<String, Status> status = new HashMap<>();
  private final Map<String, List<BannerMarker>> markers = new HashMap<>();
  private boolean closed;

  public BannerCache(String baseUrl, TileFetcher fetcher, Executor pool) {
    String base = baseUrl == null ? "" : baseUrl;
    this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    this.fetcher = fetcher;
    this.pool = pool;
  }

  public String markersUrl(String mapId) {
    return baseUrl + "/maps/" + mapId + "/live/markers.json";
  }

  /** The status for a map id, or null when it was never requested. */
  public synchronized Status status(String mapId) {
    return status.get(mapId);
  }

  /** The banners of a map; empty while loading or after a failure. Starts the fetch once. */
  public List<BannerMarker> get(String mapId) {
    if (mapId == null || mapId.isEmpty()) {
      return List.of();
    }
    synchronized (this) {
      List<BannerMarker> known = markers.get(mapId);
      if (known != null) {
        return known;
      }
      if (closed || status.containsKey(mapId)) {
        return List.of();
      }
      if (baseUrl.isEmpty() || !HttpFetcher.isAllowedUrl(baseUrl)) {
        status.put(mapId, Status.FAILED);
        return List.of();
      }
      status.put(mapId, Status.LOADING);
    }
    try {
      pool.execute(() -> load(mapId));
    } catch (RuntimeException e) {
      fail(mapId);
    }
    return List.of();
  }

  public synchronized void close() {
    closed = true;
  }

  private void load(String mapId) {
    try {
      FetchResult r = fetcher.fetch(markersUrl(mapId));
      if (r == null || r.status() != 200) {
        fail(mapId);
        return;
      }
      List<BannerMarker> parsed =
          List.copyOf(BannerMarkers.parse(new String(r.body(), StandardCharsets.UTF_8)));
      synchronized (this) {
        markers.put(mapId, parsed);
        status.put(mapId, Status.LOADED);
      }
    } catch (IOException | RuntimeException e) {
      fail(mapId);
    }
  }

  private synchronized void fail(String mapId) {
    status.put(mapId, Status.FAILED);
  }
}
