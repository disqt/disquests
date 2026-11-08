package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.client.net.FetchResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class BannerCacheTest {
  private static final String BASE = "https://x/";

  @Test
  void fetchesOncePerMap() {
    TileCacheTest.FakeFetcher fetcher = new TileCacheTest.FakeFetcher();
    fetcher.responses.put(
        "https://x/maps/world/live/markers.json",
        new FetchResult(
            200, BannerMarkersTest.SAMPLE.getBytes(StandardCharsets.UTF_8), "application/json", 0));
    List<Runnable> queued = new ArrayList<>();
    Executor deferred = queued::add;
    BannerCache cache = new BannerCache(BASE, fetcher, deferred);

    assertTrue(cache.get("world").isEmpty());
    assertTrue(cache.get("world").isEmpty());
    assertEquals(BannerCache.Status.LOADING, cache.status("world"));
    assertEquals(1, queued.size());
    queued.get(0).run();

    assertEquals(BannerCache.Status.LOADED, cache.status("world"));
    assertEquals("L'Amine", cache.get("world").get(0).label());
    cache.get("world");
    assertEquals(1, fetcher.calls);
  }

  @Test
  void failureIsEmptyAndNotRefetched() {
    TileCacheTest.FakeFetcher fetcher = new TileCacheTest.FakeFetcher();
    fetcher.responses.put("https://x/maps/nether/live/markers.json", new IOException("down"));
    BannerCache cache = new BannerCache(BASE, fetcher, Runnable::run);

    assertTrue(cache.get("nether").isEmpty());
    assertEquals(BannerCache.Status.FAILED, cache.status("nether"));
    assertTrue(cache.get("nether").isEmpty());
    assertEquals(1, fetcher.calls);

    // A 404 is a failure too, and maps are independent.
    assertTrue(cache.get("end").isEmpty());
    assertEquals(BannerCache.Status.FAILED, cache.status("end"));
    assertEquals(2, fetcher.calls);
  }

  @Test
  void noUrlMeansNoFetch() {
    TileCacheTest.FakeFetcher fetcher = new TileCacheTest.FakeFetcher();
    BannerCache cache = new BannerCache("", fetcher, Runnable::run);
    assertTrue(cache.get("world").isEmpty());
    assertTrue(cache.get(null).isEmpty());
    assertEquals(0, fetcher.calls);
  }
}
