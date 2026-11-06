package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.gui.widget.MarkdownWidget;
import com.disqt.disquests.client.image.ImageState;
import com.disqt.disquests.client.image.Images;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Inline images: an image link in quest content renders as an image block fetched from a local
 * fixture server (plain http, allowed by -Ddisquests.test.allowHttp=true in the run config).
 */
@IntegrationTest
@DisplayName("Inline Image Journey")
class InlineImageJourney {

  private static HttpServer server;
  private static final AtomicInteger imageRequests = new AtomicInteger();

  @BeforeAll
  static void setUp() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
    byte[] png;
    try (InputStream in = InlineImageJourney.class.getResourceAsStream("/inline-image-test.png")) {
      if (in == null) throw new IllegalStateException("fixture inline-image-test.png missing");
      png = in.readAllBytes();
    }
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/img.png",
        ex -> {
          imageRequests.incrementAndGet();
          ex.getResponseHeaders().set("Content-Type", "image/png");
          ex.sendResponseHeaders(200, png.length);
          try (OutputStream os = ex.getResponseBody()) {
            os.write(png);
          }
        });
    server.start();
  }

  @AfterAll
  static void tearDown() {
    if (server != null) server.stop(0);
  }

  private static String imageUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/img.png";
  }

  /** The content widget of the current QuestScreen, or null. Safe to call on the client thread. */
  private static MarkdownWidget widgetOf(Minecraft client) {
    if (client.gui.screen() instanceof DisquestsBaseScreen d && d.getRootComponent() != null) {
      return d.getRootComponent().childById(MarkdownWidget.class, "content-markdown");
    }
    return null;
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Image link renders as an image block, fetched once")
  void imageLinkRendersAsImage(ClientGameTestContext context) {
    given("player is on MainScreen");
    openMainScreen(context);

    when("player creates a quest whose content links the same image twice");
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", "Image Test");
    type(
        context,
        "content-field",
        "Before\nhttps://example.invalid/not-fetched.txt\n![site]("
            + imageUrl()
            + ")\nAfter\n"
            + imageUrl());
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);

    then("both image blocks load");
    context.waitFor(
        client -> {
          MarkdownWidget w = widgetOf(client);
          return w != null && w.loadedImageCount() == 2;
        },
        TIMEOUT);
    int blocks = context.computeOnClient(c -> widgetOf(c).imageBlockCount());
    assertEquals(2, blocks, "image blocks");

    and("the image was fetched from the server exactly once");
    assertEquals(1, imageRequests.get(), "requests for /img.png");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Unreachable image shows the failed box and the screen still works")
  void unreachableImageFails(ClientGameTestContext context) {
    String dead = "http://127.0.0.1:1/none.png";

    when("player replaces the content with a link to an unreachable image");
    click(context, "btn-edit");
    waitForScreen(context, QuestScreen.class);
    type(context, "content-field", "![x](" + dead + ")");
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);

    then("the block is present but marked failed");
    context.waitFor(
        client -> {
          MarkdownWidget w = widgetOf(client);
          return w != null
              && w.imageBlockCount() == 1
              && Images.cache().get(dead) instanceof ImageState.Failed;
        },
        TIMEOUT);
    int loaded = context.computeOnClient(c -> widgetOf(c).loadedImageCount());
    assertEquals(0, loaded, "loaded images");
    boolean failedState =
        context.computeOnClient(c -> Images.cache().get(dead) instanceof ImageState.Failed);
    assertTrue(failedState, "cache state is Failed");

    and("the editor still opens");
    click(context, "btn-edit");
    waitForScreen(context, QuestScreen.class);
    assertComponentExists(context, "formatting-panel");
    click(context, "btn-cancel");
  }
}
