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

  /** Texture factory that parses "WxH"; "bad" is undecodable. Tracks decoded images not closed. */
  static final class FakeTextures implements TextureFactory {
    final List<String> created = new ArrayList<>();
    final List<String> released = new ArrayList<>();
    int openDecoded = 0;

    @Override
    public Decoded decode(byte[] bytes) throws IOException {
      String s = new String(bytes, StandardCharsets.UTF_8);
      if (!s.contains("x")) throw new IOException("not an image");
      String[] parts = s.split("x");
      int w = Integer.parseInt(parts[0]);
      int h = Integer.parseInt(parts[1]);
      openDecoded++;
      return new Decoded() {
        @Override
        public int width() {
          return w;
        }

        @Override
        public int height() {
          return h;
        }

        @Override
        public void close() {
          openDecoded--;
        }
      };
    }

    @Override
    public Texture upload(String key, Decoded decoded) {
      created.add(key);
      decoded.close();
      return new Texture(key, decoded.width(), decoded.height());
    }

    @Override
    public void release(String key) {
      released.add(key);
    }
  }

  /** Manual clock in nanoseconds. */
  static final class FakeClock {
    long now = 0;

    void advanceSeconds(long s) {
      now += s * 1_000_000_000L;
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
    FakeClock clock = new FakeClock();
    clock.advanceSeconds(10); // start well past the pin window
    return new ImageCache(f, t, io, DIRECT, max, px, () -> clock.now);
  }

  private static ImageCache cache(
      FakeFetcher f, FakeTextures t, Executor io, int max, long px, FakeClock clock) {
    return new ImageCache(f, t, io, DIRECT, max, px, () -> clock.now);
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
    FakeClock clock = new FakeClock();
    ImageCache cache = cache(f, t, DIRECT, 2, Long.MAX_VALUE, clock);

    cache.get(A);
    cache.get(B);
    cache.get(A); // touch A so B is the least recently used
    clock.advanceSeconds(3); // both fall out of the pin window
    cache.get(C);

    assertEquals(List.of(ImageCache.textureKey(B)), t.released);
    assertEquals(2, cache.loadedCount());
  }

  @Test
  void pixelBudgetEvicts() {
    FakeFetcher f = new FakeFetcher().body(A, "64x32").body(B, "64x32").body(C, "64x32");
    FakeTextures t = new FakeTextures();
    long twoImages = 2L * 64 * 32 * 4;
    FakeClock clock = new FakeClock();
    ImageCache cache = cache(f, t, DIRECT, 32, twoImages - 1, clock);

    cache.get(A);
    clock.advanceSeconds(3);
    cache.get(B);
    clock.advanceSeconds(3);
    cache.get(C);

    assertEquals(List.of(ImageCache.textureKey(A), ImageCache.textureKey(B)), t.released);
    assertEquals(1, cache.loadedCount());
  }

  @Test
  void evictedUrlReloads() {
    FakeFetcher f = new FakeFetcher().body(A, "64x32").body(B, "64x32").body(C, "64x32");
    FakeTextures t = new FakeTextures();
    QueuedExecutor io = new QueuedExecutor();
    FakeClock clock = new FakeClock();
    ImageCache cache = cache(f, t, io, 2, Long.MAX_VALUE, clock);

    cache.get(A);
    cache.get(B);
    io.drain();
    cache.get(A); // touch A so B is the least recently used
    clock.advanceSeconds(3);
    cache.get(C);
    io.drain(); // B is evicted here

    assertInstanceOf(ImageState.Loading.class, cache.get(B));
    io.drain();
    assertInstanceOf(ImageState.Loaded.class, cache.get(B));
    assertEquals(2, f.calls(B));
  }

  @Test
  void recentlyRequestedEntriesAreNotEvicted() {
    // A quest whose images exceed the budget must not thrash: entries requested within the
    // pin window (an open widget asks every frame) stay loaded even over budget.
    FakeFetcher f = new FakeFetcher().body(A, "64x32").body(B, "64x32").body(C, "64x32");
    FakeTextures t = new FakeTextures();
    long oneImage = 64L * 32 * 4;
    FakeClock clock = new FakeClock();
    ImageCache cache = cache(f, t, DIRECT, 32, oneImage, clock);

    for (int frame = 0; frame < 5; frame++) {
      cache.get(A);
      cache.get(B);
    }

    assertEquals(1, f.calls(A));
    assertEquals(1, f.calls(B));
    assertEquals(2, cache.loadedCount());
    assertTrue(t.released.isEmpty());

    clock.advanceSeconds(3);
    cache.get(C);

    assertEquals(List.of(ImageCache.textureKey(A), ImageCache.textureKey(B)), t.released);
    assertEquals(1, cache.loadedCount());
  }

  @Test
  void oversizedDimensionsAreRejectedBeforeUpload() {
    FakeFetcher f = new FakeFetcher().body(A, "5000x10").body(B, "4000x4001");
    FakeTextures t = new FakeTextures();
    ImageCache cache = cache(f, t, DIRECT, 32, Long.MAX_VALUE);

    ImageState.Failed wide = assertInstanceOf(ImageState.Failed.class, cache.get(A));
    assertTrue(wide.reason().startsWith("decode: too large"), wide.reason());
    ImageState.Failed many = assertInstanceOf(ImageState.Failed.class, cache.get(B));
    assertTrue(many.reason().startsWith("decode: too large"), many.reason());
    assertTrue(t.created.isEmpty());
    assertEquals(0, t.openDecoded, "rejected images are closed");
  }

  @Test
  void decodeHappensOnTheIoExecutorNotTheRenderExecutor() {
    FakeFetcher f = new FakeFetcher().body(A, "64x32");
    FakeTextures t = new FakeTextures();
    QueuedExecutor io = new QueuedExecutor();
    QueuedExecutor render = new QueuedExecutor();
    ImageCache cache = new ImageCache(f, t, io, render, 32, Long.MAX_VALUE, () -> 0L);

    cache.get(A);
    io.drain(); // fetch + decode
    assertEquals(1, t.openDecoded, "decoded on io, not yet uploaded");
    assertTrue(t.created.isEmpty());
    render.drain(); // upload
    assertEquals(0, t.openDecoded);
    assertEquals(List.of(ImageCache.textureKey(A)), t.created);
    assertInstanceOf(ImageState.Loaded.class, cache.get(A));
  }

  @Test
  void textureKeyIsStableSha256() {
    assertEquals(
        "img/daaa96e524500fe9746cd881ce180a49b16e6c4c20d184dc2fecdb3978fdc58c",
        ImageCache.textureKey("https://x/a.png"));
  }
}
