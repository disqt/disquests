package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.net.HttpFetcher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;

/**
 * Lazily built process-wide TileCache and BannerCache for the current server, sharing one fetcher
 * and thread pool; both are reset when leaving it.
 */
public final class SharedTileCache {
  private static TileCache instance;
  private static BannerCache banners;
  private static HttpFetcher fetcher;
  private static ExecutorService pool;

  private SharedTileCache() {}

  public static synchronized TileCache get() {
    if (instance == null) {
      ensurePool();
      Minecraft mc = Minecraft.getInstance();
      instance =
          new TileCache(
              baseUrl(),
              fetcher,
              new McTileTextures(),
              pool,
              mc::execute,
              mc.gameDirectory.toPath().resolve("disquests/tiles"),
              256);
    }
    return instance;
  }

  /** Banner markers for the current server, fetched through the tile cache's pool. */
  public static synchronized BannerCache banners() {
    if (banners == null) {
      ensurePool();
      banners = new BannerCache(baseUrl(), fetcher, pool);
    }
    return banners;
  }

  public static synchronized void reset() {
    if (instance != null) {
      instance.close();
      instance = null;
    }
    if (banners != null) {
      banners.close();
      banners = null;
    }
    if (pool != null) {
      pool.shutdownNow();
      pool = null;
    }
    fetcher = null;
  }

  private static String baseUrl() {
    String url = ClientSession.getBluemapUrl();
    return url == null ? "" : url;
  }

  private static void ensurePool() {
    if (pool == null) {
      pool =
          Executors.newFixedThreadPool(
              2,
              r -> {
                Thread t = new Thread(r, "disquests-tiles");
                t.setDaemon(true);
                return t;
              });
      fetcher = new HttpFetcher(2 * 1024 * 1024);
    }
  }
}
