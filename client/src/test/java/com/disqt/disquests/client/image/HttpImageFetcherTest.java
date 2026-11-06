package com.disqt.disquests.client.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.client.markdown.ImageLinkDetector;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpImageFetcherTest {

  private static HttpServer server;

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/ok.png", ex -> respond(ex, 200, "image/png", new byte[10]));
    server.createContext("/text.png", ex -> respond(ex, 200, "text/html", new byte[10]));
    server.createContext("/big.png", ex -> respond(ex, 200, "image/png", new byte[100]));
    server.createContext("/missing.png", ex -> respond(ex, 404, "text/plain", new byte[0]));
    server.start();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @BeforeEach
  void allowHttp() {
    ImageLinkDetector.setAllowHttpForTests(true);
  }

  @AfterEach
  void resetHttp() {
    ImageLinkDetector.setAllowHttpForTests(false);
  }

  private static void respond(
      com.sun.net.httpserver.HttpExchange ex, int status, String type, byte[] body)
      throws IOException {
    ex.getResponseHeaders().set("Content-Type", type);
    ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(body);
    }
  }

  private static String url(String path) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + path;
  }

  private static String messageOf(String path, long maxBytes) {
    IOException e =
        assertThrows(IOException.class, () -> new HttpImageFetcher(maxBytes).fetch(url(path)));
    return e.getMessage();
  }

  @Test
  void fetchesImageBytes() throws IOException {
    assertEquals(10, new HttpImageFetcher(1000).fetch(url("/ok.png")).length);
  }

  @Test
  void rejectsHttpWhenNotAllowed() {
    ImageLinkDetector.setAllowHttpForTests(false);
    assertTrue(messageOf("/ok.png", 1000).startsWith("scheme"));
  }

  @Test
  void rejectsNonImageContentType() {
    assertTrue(messageOf("/text.png", 1000).startsWith("content-type"));
  }

  @Test
  void rejectsOversizedBody() {
    assertTrue(messageOf("/big.png", 50).startsWith("too large"));
  }

  @Test
  void rejectsNon200() {
    assertTrue(messageOf("/missing.png", 1000).startsWith("status"));
  }
}
