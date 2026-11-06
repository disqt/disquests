package com.disqt.disquests.client.image;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client's single {@link ImageCache}, wired to HTTP, the texture manager and the render thread.
 */
public final class Images {

  private static final int MAX_ENTRIES = 32;
  private static final long MAX_PIXEL_BYTES = 64L * 1024 * 1024;
  private static final Logger LOGGER = LoggerFactory.getLogger("Disquests.Images");
  private static volatile ImageCache cache;

  private Images() {}

  public static ImageCache cache() {
    ImageCache c = cache;
    if (c == null) {
      synchronized (Images.class) {
        c = cache;
        if (c == null) {
          AtomicInteger n = new AtomicInteger();
          ThreadFactory daemon =
              r -> {
                Thread t = new Thread(r, "disquests-image-" + n.getAndIncrement());
                t.setDaemon(true);
                return t;
              };
          c =
              new ImageCache(
                  new HttpImageFetcher(),
                  new MinecraftTextureFactory(),
                  Executors.newFixedThreadPool(2, daemon),
                  Minecraft.getInstance()::execute,
                  MAX_ENTRIES,
                  MAX_PIXEL_BYTES);
          c.setFailureListener((url, reason) -> LOGGER.warn("Image {} failed: {}", url, reason));
          cache = c;
        }
      }
    }
    return c;
  }

  /** {@code disquests:<textureKey>}, e.g. {@code disquests:img/<sha256>}. */
  public static Identifier textureId(String textureKey) {
    return Identifier.fromNamespaceAndPath("disquests", textureKey);
  }
}
