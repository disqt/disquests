package com.disqt.disquests.test.integration.harness;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal BlueMap stand-in for E2E tests. Serves one lowres tile (LOD 1, x0 z0) and the map
 * settings for any map id; every other path is a 404, like the gaps of a real BlueMap render.
 */
public final class TileFixtureServer {
  private static final Logger LOG = LoggerFactory.getLogger("Disquests.TileFixture");

  static final String SETTINGS_JSON =
      "{\"lowres\":{\"tileSize\":{\"x\":500,\"z\":500},\"lodFactor\":5,\"lodCount\":3}}";
  static final int TILE_WIDTH = 501;
  static final int TILE_HEIGHT = 1002;
  static final int COLOUR_ROWS = 501;
  static final int TILE_GREEN = 0x3A7D44;

  private static final Pattern SETTINGS_PATH = Pattern.compile("/maps/[^/]+/settings\\.json");
  private static final Pattern TILE_PATH = Pattern.compile("/maps/[^/]+/tiles/1/x0/z0\\.png");

  private static HttpServer server;
  private static ExecutorService executor;
  private static byte[] tilePng;

  private TileFixtureServer() {}

  /** Binds localhost:{@code port}. Throws IOException when the port is taken. */
  public static synchronized void start(int port) throws IOException {
    if (server != null) {
      return;
    }
    if (tilePng == null) {
      tilePng = buildTilePng();
    }
    HttpServer s = HttpServer.create(new InetSocketAddress("localhost", port), 0);
    ExecutorService pool =
        Executors.newFixedThreadPool(
            2,
            r -> {
              Thread t = new Thread(r, "disquests-tile-fixture");
              t.setDaemon(true);
              return t;
            });
    s.createContext("/", TileFixtureServer::handle);
    s.setExecutor(pool);
    s.start();
    server = s;
    executor = pool;
    LOG.info("Tile fixture serving on http://localhost:{}/", port);
  }

  public static synchronized void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  private static void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      String path = exchange.getRequestURI().getPath();
      if (!"GET".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(404, -1);
      } else if (SETTINGS_PATH.matcher(path).matches()) {
        byte[] body = SETTINGS_JSON.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        send(exchange, body);
      } else if (TILE_PATH.matcher(path).matches()) {
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=60");
        send(exchange, tilePng);
      } else {
        exchange.sendResponseHeaders(404, -1);
      }
    }
  }

  private static void send(HttpExchange exchange, byte[] body) throws IOException {
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  /** BlueMap lowres layout: colour in the top 501 rows, (here black) height data below. */
  private static byte[] buildTilePng() {
    BufferedImage image = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < COLOUR_ROWS; y++) {
      for (int x = 0; x < TILE_WIDTH; x++) {
        image.setRGB(x, y, TILE_GREEN);
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      if (!ImageIO.write(image, "png", out)) {
        throw new IOException("No PNG writer available");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }
}
