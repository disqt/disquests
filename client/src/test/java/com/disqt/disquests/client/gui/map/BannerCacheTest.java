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

  private static final String URL = "https://x/maps/world/live/markers.json";
  private static final String ONE = "{}";

  private static FetchResult ok(String body) {
    return new FetchResult(200, body.getBytes(StandardCharsets.UTF_8), "application/json", 0);
  }

  @Test
  void failureIsEmptyAndNotRefetchedWithinInterval() {
    TileCacheTest.FakeFetcher fetcher = new TileCacheTest.FakeFetcher();
    fetcher.responses.put("https://x/maps/nether/live/markers.json", new IOException("down"));
    long[] now = {0};
    BannerCache cache = new BannerCache(BASE, fetcher, Runnable::run, () -> now[0], 1000);

    assertTrue(cache.get("nether").isEmpty());
    assertEquals(BannerCache.Status.FAILED, cache.status("nether"));
    now[0] = 999;
    assertTrue(cache.get("nether").isEmpty());
    assertEquals(1, fetcher.calls);

    // A 404 is a failure too, and maps are independent.
    assertTrue(cache.get("end").isEmpty());
    assertEquals(BannerCache.Status.FAILED, cache.status("end"));
    assertEquals(2, fetcher.calls);
  }

  @Test
  void refetchesAfterIntervalKeepingOldListUntilDone() {
    TileCacheTest.FakeFetcher fetcher = new TileCacheTest.FakeFetcher();
    fetcher.responses.put(URL, ok(BannerMarkersTest.SAMPLE));
    List<Runnable> queued = new ArrayList<>();
    long[] now = {0};
    BannerCache cache = new BannerCache(BASE, fetcher, queued::add, () -> now[0], 1000);

    cache.get("world");
    queued.remove(0).run();
    assertEquals(1, cache.get("world").size());
    now[0] = 500;
    cache.get("world");
    assertTrue(queued.isEmpty());

    now[0] = 1000;
    for (int i = 0; i < 5; i++) {
      assertEquals(1, cache.get("world").size());
    }
    assertEquals(1, queued.size());
    fetcher.responses.put(URL, ok(ONE));
    queued.remove(0).run();
    assertEquals(2, fetcher.calls);
    assertTrue(cache.get("world").isEmpty());
    assertTrue(queued.isEmpty());
  }

  @Test
  void failedRefreshKeepsListAndRetriesAfterInterval() {
    TileCacheTest.FakeFetcher fetcher = new TileCacheTest.FakeFetcher();
    fetcher.responses.put(URL, ok(BannerMarkersTest.SAMPLE));
    List<Runnable> queued = new ArrayList<>();
    long[] now = {0};
    BannerCache cache = new BannerCache(BASE, fetcher, queued::add, () -> now[0], 1000);

    cache.get("world");
    queued.remove(0).run();
    now[0] = 1000;
    fetcher.responses.put(URL, new IOException("down"));
    cache.get("world");
    queued.remove(0).run();
    assertEquals(1, cache.get("world").size());
    assertTrue(queued.isEmpty());

    now[0] = 1999;
    cache.get("world");
    assertTrue(queued.isEmpty());
    now[0] = 2000;
    cache.get("world");
    assertEquals(1, queued.size());
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
