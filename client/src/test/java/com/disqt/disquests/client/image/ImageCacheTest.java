package com.disqt.disquests.client.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class ImageCacheTest {

  /** Fetcher backed by a map; a missing url throws. Bytes are the text "WxH" or "bad". */
  static final class FakeFetcher implements ImageFetcher {
    final Map<String, String> bodies = new HashMap<>();
    final Map<String, Integer> calls = new HashMap<>();

    FakeFetcher body(String url, String body) {
      bodies.put(url, body);
      return this;
    }

    int calls(String url) {
      return calls.getOrDefault(url, 0);
    }

    @Override
    public byte[] fetch(String url) throws IOException {
      calls.merge(url, 1, Integer::sum);
      String body = bodies.get(url);
      if (body == null) throw new IOException("status 404");
      return body.getBytes(StandardCharsets.UTF_8);
    }
  }

  /** Texture factory that parses "WxH"; "bad" is undecodable. */
  static final class FakeTextures implements TextureFactory {
    final List<String> created = new ArrayList<>();
    final List<String> released = new ArrayList<>();

    @Override
    public Texture create(String key, byte[] bytes) throws IOException {
      String s = new String(bytes, StandardCharsets.UTF_8);
      if (!s.contains("x")) throw new IOException("not an image");
      String[] parts = s.split("x");
      created.add(key);
      return new Texture(key, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    @Override
    public void release(String key) {
      released.add(key);
    }
  }

  /** Executor that queues tasks until drained. */
  static final class QueuedExecutor implements Executor {
    final Queue<Runnable> queue = new LinkedList<>();

    @Override
    public void execute(Runnable r) {
      queue.add(r);
    }

    void drain() {
      Runnable r;
      while ((r = queue.poll()) != null) r.run();
    }
  }

  static final Executor DIRECT = Runnable::run;
  static final String A = "https://x/a.png";
  static final String B = "https://x/b.png";
  static final String C = "https://x/c.png";

  private static ImageCache cache(FakeFetcher f, FakeTextures t, Executor io, int max, long px) {
    return new ImageCache(f, t, io, DIRECT, max, px);
  }

  @Test
  void firstGetReturnsLoadingThenLoaded() {
    FakeFetcher f = new FakeFetcher().body(A, "64x32");
    QueuedExecutor io = new QueuedExecutor();
    ImageCache cache = cache(f, new FakeTextures(), io, 32, Long.MAX_VALUE);

    assertInstanceOf(ImageState.Loading.class, cache.get(A));
    io.drain();

    ImageState.Loaded loaded = assertInstanceOf(ImageState.Loaded.class, cache.get(A));
    assertEquals(ImageCache.textureKey(A), loaded.textureKey());
    assertEquals(64, loaded.width());
    assertEquals(32, loaded.height());
    assertEquals(1, cache.loadedCount());
  }

  @Test
  void sameUrlFetchedOnce() {
    FakeFetcher f = new FakeFetcher().body(A, "64x32");
    QueuedExecutor io = new QueuedExecutor();
    ImageCache cache = cache(f, new FakeTextures(), io, 32, Long.MAX_VALUE);

    cache.get(A);
    cache.get(A);
    io.drain();
    cache.get(A);

    assertEquals(1, f.calls(A));
  }

  @Test
  void failedFetchIsMemoised() {
    FakeFetcher f = new FakeFetcher();
    ImageCache cache = cache(f, new FakeTextures(), DIRECT, 32, Long.MAX_VALUE);

    ImageState.Failed failed = assertInstanceOf(ImageState.Failed.class, cache.get(A));
    assertTrue(failed.reason().startsWith("fetch"), failed.reason());
    assertInstanceOf(ImageState.Failed.class, cache.get(A));
    assertEquals(1, f.calls(A));
  }

  @Test
  void undecodableBytesAreFailed() {
    FakeFetcher f = new FakeFetcher().body(A, "bad");
    ImageCache cache = cache(f, new FakeTextures(), DIRECT, 32, Long.MAX_VALUE);

    ImageState.Failed failed = assertInstanceOf(ImageState.Failed.class, cache.get(A));
    assertTrue(failed.reason().startsWith("decode"), failed.reason());
  }

  @Test
  void lruEvictsOldestAndReleasesTexture() {
    FakeFetcher f = new FakeFetcher().body(A, "64x32").body(B, "64x32").body(C, "64x32");
    FakeTextures t = new FakeTextures();
    ImageCache cache = cache(f, t, DIRECT, 2, Long.MAX_VALUE);

    cache.get(A);
    cache.get(B);
    cache.get(A); // touch A so B is the least recently used
    cache.get(C);

    assertEquals(List.of(ImageCache.textureKey(B)), t.released);
    assertEquals(2, cache.loadedCount());
  }

  @Test
  void pixelBudgetEvicts() {
    FakeFetcher f = new FakeFetcher().body(A, "64x32").body(B, "64x32").body(C, "64x32");
    FakeTextures t = new FakeTextures();
    long twoImages = 2L * 64 * 32 * 4;
    ImageCache cache = cache(f, t, DIRECT, 32, twoImages - 1);

    cache.get(A);
    cache.get(B);
    cache.get(C);

    assertEquals(List.of(ImageCache.textureKey(A), ImageCache.textureKey(B)), t.released);
    assertEquals(1, cache.loadedCount());
  }

  @Test
  void evictedUrlReloads() {
    FakeFetcher f = new FakeFetcher().body(A, "64x32").body(B, "64x32").body(C, "64x32");
    FakeTextures t = new FakeTextures();
    QueuedExecutor io = new QueuedExecutor();
    ImageCache cache = cache(f, t, io, 2, Long.MAX_VALUE);

    cache.get(A);
    cache.get(B);
    io.drain();
    cache.get(A); // touch A so B is the least recently used
    cache.get(C);
    io.drain(); // B is evicted here

    assertInstanceOf(ImageState.Loading.class, cache.get(B));
    io.drain();
    assertInstanceOf(ImageState.Loaded.class, cache.get(B));
    assertEquals(2, f.calls(B));
  }

  @Test
  void textureKeyIsStableSha256() {
    assertEquals(
        "img/daaa96e524500fe9746cd881ce180a49b16e6c4c20d184dc2fecdb3978fdc58c",
        ImageCache.textureKey("https://x/a.png"));
  }
}
