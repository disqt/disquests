package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.client.net.FetchResult;
import com.disqt.disquests.client.net.TileFetcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TileCacheTest {
  private static final String BASE = "https://x";
  private static final Executor DIRECT = Runnable::run;

  @TempDir Path dir;

  static class FakeFetcher implements TileFetcher {
    final Map<String, Object> responses = new HashMap<>();
    int calls;

    @Override
    public FetchResult fetch(String url) throws IOException {
      calls++;
      Object r = responses.get(url);
      if (r instanceof IOException e) {
        throw e;
      }
      if (r instanceof RuntimeException e) {
        throw e;
      }
      if (r == null) {
        return new FetchResult(404, new byte[0], "text/plain", 0);
      }
      return (FetchResult) r;
    }
  }

  static class FakeTextures implements TileTextures {
    final List<TileKey> uploads = new ArrayList<>();
    final List<TileKey> releases = new ArrayList<>();
    final List<Integer> decodeSizes = new ArrayList<>();
    final List<Object> discarded = new ArrayList<>();
    boolean fail;
    boolean failDecode;

    @Override
    public Object decode(byte[] png, int tileSize) throws IOException {
      if (failDecode) {
        throw new IOException("undecodable png");
      }
      decodeSizes.add(tileSize);
      return new Object();
    }

    @Override
    public int[] upload(TileKey key, Object decoded) throws IOException {
      if (fail) {
        throw new IOException("bad png");
      }
      uploads.add(key);
      return new int[] {500, 500};
    }

    @Override
    public void discard(Object decoded) {
      discarded.add(decoded);
    }

    @Override
    public void release(TileKey key) {
      releases.add(key);
    }
  }

  static class Deferring implements Executor {
    final List<Runnable> pending = new ArrayList<>();

    @Override
    public void execute(Runnable r) {
      pending.add(r);
    }

    void drain() {
      while (!pending.isEmpty()) {
        pending.remove(0).run();
      }
    }
  }

  private static FetchResult png() {
    return new FetchResult(200, new byte[] {1, 2, 3}, "image/png", 3600);
  }

  private final FakeFetcher fetcher = new FakeFetcher();
  private final FakeTextures textures = new FakeTextures();

  private TileCache cache(Executor pool, Executor render, Path d, int max) {
    return new TileCache(BASE, fetcher, textures, pool, render, d, max);
  }

  private TileCache cache(int max) {
    return cache(DIRECT, DIRECT, dir, max);
  }

  private TileKey key(int x) {
    return new TileKey("w", 1, x, 0);
  }

  private void serve(TileCache c, TileKey k) {
    fetcher.responses.put(c.tileUrl(k), png());
  }

  @Test
  void get_firstCallLoading_thenLoadedAfterFetch() {
    Deferring pool = new Deferring();
    Deferring render = new Deferring();
    TileCache c = cache(pool, render, dir, 4);
    TileKey k = key(0);
    serve(c, k);
    assertEquals(TileStatus.LOADING, c.get(k));
    pool.drain();
    assertEquals(TileStatus.LOADING, c.get(k));
    render.drain();
    assertEquals(TileStatus.LOADED, c.get(k));
    assertEquals(List.of(k), textures.uploads);
  }

  @Test
  void get_dedupsConcurrentRequests() {
    Deferring pool = new Deferring();
    TileCache c = cache(pool, DIRECT, dir, 4);
    TileKey k = key(0);
    serve(c, k);
    c.get(k);
    c.get(k);
    pool.drain();
    assertEquals(1, fetcher.calls);
    assertEquals(1, c.stats().fetches());
  }

  @Test
  void get_404_isMissing_andNotRefetched() {
    TileCache c = cache(4);
    TileKey k = key(0);
    c.get(k);
    assertEquals(TileStatus.MISSING, c.get(k));
    assertEquals(1, fetcher.calls);
    assertEquals(1, c.stats().missing());
  }

  @Test
  void get_ioException_isMissing_setsLastError() {
    TileCache c = cache(4);
    TileKey k = key(0);
    fetcher.responses.put(c.tileUrl(k), new IOException("boom"));
    c.get(k);
    assertEquals(TileStatus.MISSING, c.get(k));
    assertEquals("boom", c.lastError());
    TileKey ok = key(1);
    serve(c, ok);
    c.get(ok);
    assertNull(c.lastError());
  }

  @Test
  void get_wrongContentType_isMissing() {
    TileCache c = cache(4);
    TileKey k = key(0);
    fetcher.responses.put(c.tileUrl(k), new FetchResult(200, new byte[] {1}, "text/html", 60));
    c.get(k);
    assertEquals(TileStatus.MISSING, c.get(k));
    assertTrue(textures.uploads.isEmpty());
  }

  @Test
  void get_uploadFailure_isMissing() {
    TileCache c = cache(4);
    TileKey k = key(0);
    serve(c, k);
    textures.fail = true;
    c.get(k);
    assertEquals(TileStatus.MISSING, c.get(k));
    assertEquals(1, textures.discarded.size());
  }

  @Test
  void decodeFailure_isMissing() {
    TileCache c = cache(4);
    TileKey k = key(0);
    serve(c, k);
    textures.failDecode = true;
    c.get(k);
    assertEquals(TileStatus.MISSING, c.get(k));
    assertTrue(textures.uploads.isEmpty());
    assertEquals(1, c.stats().missing());
  }

  @Test
  void decode_runsOnFetchPool_withMapTileSize() {
    Deferring pool = new Deferring();
    Deferring render = new Deferring();
    TileCache c = cache(pool, render, dir, 4);
    TileKey k = key(0);
    serve(c, k);
    c.get(k);
    pool.drain();
    assertEquals(List.of(BlueMapSettings.DEFAULT.tileSize()), textures.decodeSizes);
    assertTrue(textures.uploads.isEmpty());
    render.drain();
    assertEquals(List.of(k), textures.uploads);
  }

  @Test
  void closedBeforeUpload_discardsDecodedImage() {
    Deferring render = new Deferring();
    TileCache c = cache(DIRECT, render, dir, 4);
    TileKey k = key(0);
    serve(c, k);
    c.get(k);
    c.close();
    render.drain();
    assertTrue(textures.uploads.isEmpty());
    assertEquals(1, textures.discarded.size());
  }

  @Test
  void get_nullMapId_isMissing_noFetch() {
    TileCache c = cache(4);
    assertEquals(TileStatus.MISSING, c.get(new TileKey(null, 0, 0, 0)));
    assertEquals(TileStatus.MISSING, c.get(new TileKey("", 0, 0, 0)));
    assertEquals(0, fetcher.calls);
  }

  @Test
  void lru_evictsOldest_andReleasesTexture() {
    TileCache c = cache(2);
    for (int i = 0; i < 3; i++) {
      serve(c, key(i));
      c.get(key(i));
    }
    assertEquals(1, c.stats().evictions());
    assertEquals(2, c.stats().loaded());
    assertEquals(List.of(key(0)), textures.releases);
  }

  @Test
  void lru_touchRefreshesOrder() {
    TileCache c = cache(2);
    TileKey a = key(0);
    TileKey b = key(1);
    TileKey d = key(2);
    for (TileKey k : List.of(a, b, d)) {
      serve(c, k);
    }
    c.get(a);
    c.get(b);
    assertEquals(TileStatus.LOADED, c.get(a));
    c.get(d);
    assertEquals(List.of(b), textures.releases);
  }

  @Test
  void diskCache_hitAvoidsFetch() {
    TileCache first = cache(4);
    TileKey k = key(0);
    serve(first, k);
    first.get(k);
    assertEquals(1, fetcher.calls);

    FakeFetcher other = new FakeFetcher();
    TileCache second = new TileCache(BASE, other, textures, DIRECT, DIRECT, dir, 4);
    assertEquals(TileStatus.LOADING, second.get(k));
    assertEquals(0, other.calls);
    assertEquals(TileStatus.LOADED, second.get(k));
  }

  @Test
  void diskCache_expiredEntryRefetches() throws IOException {
    TileCache first = cache(4);
    TileKey k = key(0);
    serve(first, k);
    first.get(k);
    try (var files = Files.list(dir)) {
      for (Path p : files.filter(f -> f.toString().endsWith(".meta")).toList()) {
        Files.writeString(p, Long.toString(System.currentTimeMillis() - 1000));
      }
    }
    FakeFetcher other = new FakeFetcher();
    other.responses.put(first.tileUrl(k), png());
    TileCache second = new TileCache(BASE, other, textures, DIRECT, DIRECT, dir, 4);
    second.get(k);
    assertEquals(1, other.calls);
    assertEquals(TileStatus.LOADED, second.get(k));
  }

  @Test
  void settings_fetchedOncePerMap_andDefaultOnFailure() {
    TileCache c = cache(4);
    String json = "{\"lowres\":{\"tileSize\":[100,100],\"lodFactor\":4,\"lodCount\":2}}";
    fetcher.responses.put(
        BASE + "/maps/w/settings.json",
        new FetchResult(200, json.getBytes(StandardCharsets.UTF_8), "application/json", 60));
    // DIRECT executors run the fetch inside the first call, so the first return is DEFAULT.
    assertEquals(BlueMapSettings.DEFAULT, c.settings("w"));
    assertEquals(new BlueMapSettings(100, 4, 2), c.settings("w"));
    assertEquals(1, fetcher.calls);

    assertEquals(BlueMapSettings.DEFAULT, c.settings("broken"));
    assertEquals(BlueMapSettings.DEFAULT, c.settings("broken"));
    assertEquals(2, fetcher.calls);
  }

  @Test
  void settingsFailed_trueAfter404OrIoError_falseOnSuccessOrUnknown() {
    TileCache c = cache(4);
    String json = "{\"lowres\":{\"tileSize\":[100,100],\"lodFactor\":4,\"lodCount\":2}}";
    fetcher.responses.put(
        BASE + "/maps/ok/settings.json",
        new FetchResult(200, json.getBytes(StandardCharsets.UTF_8), "application/json", 60));
    fetcher.responses.put(BASE + "/maps/down/settings.json", new IOException("refused"));

    c.settings("ok");
    c.settings("missing"); // FakeFetcher answers 404 for unknown URLs
    c.settings("down");

    assertFalse(c.settingsFailed("ok"));
    assertTrue(c.settingsFailed("missing"));
    assertTrue(c.settingsFailed("down"));
    assertFalse(c.settingsFailed("never-requested"));
    assertFalse(c.settingsFailed(null));
  }

  @Test
  void settingsFailed_falseWhileFetchPending() {
    Deferring pool = new Deferring();
    TileCache c = cache(pool, DIRECT, dir, 4);
    c.settings("missing");
    assertFalse(c.settingsFailed("missing"));
    pool.drain();
    assertTrue(c.settingsFailed("missing"));
  }

  @Test
  void isAvailable_falseForEmptyBaseUrl() {
    assertFalse(new TileCache("", fetcher, textures, DIRECT, DIRECT, dir, 4).isAvailable());
    assertFalse(new TileCache("http://x", fetcher, textures, DIRECT, DIRECT, dir, 4).isAvailable());
    assertTrue(cache(4).isAvailable());
  }

  @Test
  void tileUrl_format() {
    assertEquals(
        "https://x/maps/world_new/tiles/2/x-3/z4.png",
        cache(4).tileUrl(new TileKey("world_new", 2, -3, 4)));
    assertNotNull(
        new TileCache("https://x/", fetcher, textures, DIRECT, DIRECT, dir, 4)
            .tileUrl(new TileKey("m", 0, 0, 0)));
  }

  @Test
  void get_runtimeExceptionFromFetcher_isMissing() {
    TileCache c = cache(4);
    TileKey k = key(0);
    fetcher.responses.put(c.tileUrl(k), new IllegalStateException("oops"));
    c.get(k);
    assertEquals(TileStatus.MISSING, c.get(k));
    assertEquals(1, c.stats().fetches());
    assertEquals(1, fetcher.calls);
  }

  @Test
  void get_rejectedPool_isMissing() {
    TileCache c =
        cache(
            r -> {
              throw new java.util.concurrent.RejectedExecutionException("shut down");
            },
            DIRECT,
            dir,
            4);
    assertEquals(TileStatus.MISSING, c.get(key(0)));
    assertEquals(0, fetcher.calls);
  }
}
