package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.net.HttpFetcher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;

/** Lazily built process-wide TileCache for the current server; reset when leaving it. */
public final class SharedTileCache {
  private static TileCache instance;
  private static ExecutorService pool;

  private SharedTileCache() {}

  public static synchronized TileCache get() {
    if (instance == null) {
      String url = ClientSession.getBluemapUrl();
      pool =
          Executors.newFixedThreadPool(
              2,
              r -> {
                Thread t = new Thread(r, "disquests-tiles");
                t.setDaemon(true);
                return t;
              });
      Minecraft mc = Minecraft.getInstance();
      instance =
          new TileCache(
              url == null ? "" : url,
              new HttpFetcher(2 * 1024 * 1024),
              new McTileTextures(),
              pool,
              mc::execute,
              mc.gameDirectory.toPath().resolve("disquests/tiles"),
              256);
    }
    return instance;
  }

  public static synchronized void reset() {
    if (instance != null) {
      instance.close();
      instance = null;
      pool.shutdownNow();
      pool = null;
    }
  }
}
