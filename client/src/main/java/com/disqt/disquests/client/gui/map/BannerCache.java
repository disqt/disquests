package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.net.FetchResult;
import com.disqt.disquests.client.net.HttpFetcher;
import com.disqt.disquests.client.net.TileFetcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * BlueMap banner markers per map id, fetched in the background and refreshed while read. No
 * Minecraft imports. {@link #get} never blocks.
 */
public final class BannerCache {
  public enum Status {
    LOADING,
    LOADED,
    FAILED
  }

  public static final long REFRESH_MILLIS = 60_000L;

  private final String baseUrl;
  private final TileFetcher fetcher;
  private final Executor pool;

  // Guarded by "this".
  private final Map<String, Status> status = new HashMap<>();
  private final Map<String, List<BannerMarker>> markers = new HashMap<>();
  private final Map<String, Long> lastAttempt = new HashMap<>();
  private final Set<String> inFlight = new HashSet<>();
  private final LongSupplier nowMillis;
  private final long refreshMillis;
  private boolean closed;

  public BannerCache(String baseUrl, TileFetcher fetcher, Executor pool) {
    this(baseUrl, fetcher, pool, System::currentTimeMillis, REFRESH_MILLIS);
  }

  public BannerCache(
      String baseUrl,
      TileFetcher fetcher,
      Executor pool,
      LongSupplier nowMillis,
      long refreshMillis) {
    this.nowMillis = nowMillis;
    this.refreshMillis = refreshMillis;
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

  /**
   * The last good banner list of a map; empty before the first success. Schedules a background
   * refresh when none is in flight and the last attempt is older than the refresh interval.
   */
  public List<BannerMarker> get(String mapId) {
    if (mapId == null || mapId.isEmpty()) {
      return List.of();
    }
    List<BannerMarker> current;
    synchronized (this) {
      current = markers.getOrDefault(mapId, List.of());
      if (closed || inFlight.contains(mapId)) {
        return current;
      }
      if (baseUrl.isEmpty() || !HttpFetcher.isAllowedUrl(baseUrl)) {
        status.put(mapId, Status.FAILED);
        return current;
      }
      long now = nowMillis.getAsLong();
      Long last = lastAttempt.get(mapId);
      if (last != null && now - last < refreshMillis) {
        return current;
      }
      lastAttempt.put(mapId, now);
      inFlight.add(mapId);
      if (!markers.containsKey(mapId)) {
        status.put(mapId, Status.LOADING);
      }
    }
    try {
      pool.execute(() -> load(mapId));
    } catch (RuntimeException e) {
      fail(mapId);
    }
    return current;
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
        inFlight.remove(mapId);
      }
    } catch (IOException | RuntimeException e) {
      fail(mapId);
    }
  }

  private synchronized void fail(String mapId) {
    inFlight.remove(mapId);
    if (!markers.containsKey(mapId)) {
      status.put(mapId, Status.FAILED);
    }
  }
}
