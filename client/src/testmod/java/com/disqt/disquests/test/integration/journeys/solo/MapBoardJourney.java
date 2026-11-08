package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.client.gui.map.MapMath;
import com.disqt.disquests.client.gui.map.MapView;
import com.disqt.disquests.client.gui.map.PlayerMarkerLayer;
import com.disqt.disquests.client.gui.map.QuestPolygonLayer;
import com.disqt.disquests.client.gui.map.SharedTileCache;
import com.disqt.disquests.client.gui.map.TileCache;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.DimensionKeys;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Visibility;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The map board home screen against the harness tile fixture (TileFixtureServer): camera, tile
 * loading, polygon hit-testing and clicks, the unplaced group, dimension switching, zoom, the
 * unreachable-tiles fallback and the legacy home for V1 servers.
 */
@IntegrationTest
@DisplayName("Map Board Journey")
class MapBoardJourney {
  private static final String MAP_QUEST = "Map Quest";
  private static final String NO_PLACE = "No Place";
  private static final String DEAD_URL = "http://localhost:1/";

  // Shared across the ordered steps (JUnit builds a new instance per test method).
  private static String handshakeUrl;
  private static String playerDimension;
  private static double px;
  private static double pz;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  // --- Helpers ---

  /** Block {x, z} of the Map Quest triangle's centroid. */
  private static double[] centroid() {
    return new double[] {px, pz - 20.0 / 3.0};
  }

  /** Hit-test the current MainScreen's map at the triangle's centroid. Client thread only. */
  private static Quest hitAtCentroid(Minecraft client) {
    if (!(client.gui.screen() instanceof MainScreen screen)) {
      return null;
    }
    MapView view = screen.getMapView();
    if (view == null) {
      return null;
    }
    QuestPolygonLayer layer = view.getLayer(QuestPolygonLayer.class);
    if (layer == null) {
      return null;
    }
    double[] c = centroid();
    double[] rel = view.blockToScreen(c[0], c[1]);
    return layer.hitTest(view, rel[0], rel[1]);
  }

  private static boolean hitsMapQuest(Minecraft client) {
    Quest hit = hitAtCentroid(client);
    return hit != null && MAP_QUEST.equals(hit.getTitle());
  }

  private static String statsDescription(ClientGameTestContext context) {
    return context.computeOnClient(
        c -> {
          TileCache cache = SharedTileCache.get();
          return cache.stats() + " lastError=" + cache.lastError() + " url=" + handshakeUrl;
        });
  }

  // --- Steps ---

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Board shows the map centred on the player")
  void boardShowsMapCentredOnPlayer(ClientGameTestContext context) throws Exception {
    given("a fresh server and the My Quests tab");
    resetServerAndSync();
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
          // Fresh tile cache so the next step measures this journey's fetches.
          SharedTileCache.reset();
        });
    handshakeUrl = context.computeOnClient(c -> ClientSession.getBluemapUrl());
    assertNotNull(handshakeUrl, "handshake carried no bluemap-url");
    assertFalse(
        handshakeUrl.isEmpty(),
        "handshake bluemap-url is empty: ensureServer should point it at the tile fixture");
    playerDimension = context.computeOnClient(c -> c.level.dimension().identifier().getPath());

    when("player opens MainScreen");
    openMainScreen(context);
    then("the map view is shown");
    MapView view = mapView(context);
    and("the camera is at zoom 2, centred on the player, in the player's dimension");
    MapMath.Camera camera = context.computeOnClient(c -> view.getCamera());
    double[] player = context.computeOnClient(c -> new double[] {c.player.getX(), c.player.getZ()});
    assertEquals(2, camera.zoom(), "initial zoom");
    assertEquals(player[0], camera.centerX(), 1.0, "camera centre x vs player x");
    assertEquals(player[1], camera.centerZ(), 1.0, "camera centre z vs player z");
    assertEquals(
        playerDimension, context.computeOnClient(c -> view.getDimension()), "map dimension");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("The fixture tile loads and its neighbours are missing")
  void tileFromFixtureLoads(ClientGameTestContext context) {
    when("the map draws around spawn");
    then("tile x0 z0 loads from the fixture and the neighbouring 404s are recorded as missing");
    try {
      context.waitFor(
          client -> {
            TileCache.Stats s = SharedTileCache.get().stats();
            return s.loaded() >= 1 && s.missing() >= 1;
          },
          seconds(15));
    } catch (Throwable t) {
      fail("Tiles did not load from the fixture: " + statsDescription(context), t);
    }
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("A polygon quest renders and opens on click")
  void polygonQuestRendersAndOpensOnClick(ClientGameTestContext context) {
    given("a triangle quest around the player, saved straight to the server");
    double[] player = context.computeOnClient(c -> new double[] {c.player.getX(), c.player.getZ()});
    px = player[0];
    pz = player[1];
    context.runOnClient(
        c -> {
          Quest quest = new Quest();
          quest.setId(UUID.randomUUID());
          quest.setTitle(MAP_QUEST);
          quest.setContent("");
          quest.setVisibility(Visibility.PRIVATE);
          quest.setOwnerUuid(ClientSession.getEffectivePlayerUuid());
          quest.setOwnerName(c.getUser().getName());
          quest.setLastModified(System.currentTimeMillis() / 1000);
          quest.setContributors(new ArrayList<>());
          quest.setMap(playerDimension);
          quest.setPolygon(
              List.of(
                  new PointData(px - 20, pz - 20),
                  new PointData(px + 20, pz - 20),
                  new PointData(px, pz + 20)));
          PacketSender.saveQuest(
              quest.getId(),
              quest.getTitle(),
              quest.getContent(),
              quest.getCoordinates(),
              quest.isRegion(),
              quest.getCoordinates2(),
              quest.getMap(),
              quest.getTags(),
              quest.getPolygon());
        });
    waitForQuestByTitle(context, MAP_QUEST, true);

    then("hit-testing the map at the triangle's centroid finds the quest");
    context.waitFor(MapBoardJourney::hitsMapQuest, TIMEOUT);

    when("player clicks inside the triangle");
    clickMapAtBlock(context, px, pz - 7);
    then("the quest opens");
    waitForScreen(context, QuestScreen.class);
    assertLabelText(context, "title-label", MAP_QUEST);

    when("player goes back");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("An unplaced quest is listed first, under its header")
  void unplacedQuestListedFirst(ClientGameTestContext context) {
    given("player is on MainScreen");
    openMainScreen(context);
    when("player creates a quest without a location");
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", NO_PLACE);
    click(context, "btn-save");
    waitForScreen(context, QuestScreen.class);
    waitForQuestByTitle(context, NO_PLACE, true);
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    // Reopen a fresh MainScreen (the parent screen may be stale)
    openMainScreen(context);

    then("the list starts with the unplaced header, then the unplaced quest");
    FlowLayout questList = findComponent(context, FlowLayout.class, "quest-list");
    try {
      context.waitFor(
          client -> {
            var children = questList.children();
            return children.size() >= 3
                && children.get(0) instanceof LabelComponent header
                && "unplaced-header".equals(header.id())
                && children.get(1) instanceof QuestEntryComponent entry
                && NO_PLACE.equals(entry.getQuest().getTitle());
          },
          TIMEOUT);
    } catch (Throwable t) {
      String order =
          context.computeOnClient(
              c -> {
                StringBuilder sb = new StringBuilder();
                for (var child : questList.children()) {
                  if (child instanceof QuestEntryComponent e) {
                    sb.append("[entry ").append(e.getQuest().getTitle()).append("] ");
                  } else {
                    sb.append("[").append(child.id()).append("] ");
                  }
                }
                return sb.toString();
              });
      fail("Unexpected quest-list order: " + order, t);
    }

    and("both quests are visible");
    List<String> titles =
        context.computeOnClient(
            c ->
                ((MainScreen) c.gui.screen())
                    .visibleQuests().stream().map(Quest::getTitle).toList());
    assertTrue(titles.contains(MAP_QUEST), "visibleQuests should contain Map Quest: " + titles);
    assertTrue(titles.contains(NO_PLACE), "visibleQuests should contain No Place: " + titles);
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("Switching dimension hides quests from other dimensions")
  void dimensionSwitchHidesOtherDimensionQuests(ClientGameTestContext context) {
    given("the map shows the Map Quest");
    context.waitFor(MapBoardJourney::hitsMapQuest, TIMEOUT);

    when("player selects The End");
    click(context, "btn-dim-the_end");
    then("the map shows the_end and the overworld triangle is gone");
    MapView view = mapView(context);
    assertEquals("the_end", context.computeOnClient(c -> view.getDimension()), "map dimension");
    assertNull(
        context.computeOnClient(MapBoardJourney::hitAtCentroid),
        "no quest should be hit in the_end");

    when("player clicks Centre on me");
    click(context, "btn-centre-me");
    then("the map is back in the player's dimension");
    assertEquals(
        playerDimension, context.computeOnClient(c -> view.getDimension()), "map dimension");
    context.waitFor(MapBoardJourney::hitsMapQuest, TIMEOUT);
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("Scrolling zooms around the cursor")
  void zoomAroundCursorChangesZoom(ClientGameTestContext context) {
    given("the map is at zoom 2");
    MapView view = mapView(context);
    int startZoom = context.computeOnClient(c -> view.getCamera().zoom());
    assertEquals(2, startZoom, "starting zoom");
    double[] centre = componentCenter(context, view);

    when("player scrolls down over the map");
    scrollAt(context, centre, -1);
    then("zoom drops to 1");
    context.waitFor(client -> view.getCamera().zoom() == 1, TIMEOUT);

    when("player scrolls up over the map");
    scrollAt(context, centre, 1);
    then("zoom returns to 2");
    context.waitFor(client -> view.getCamera().zoom() == 2, TIMEOUT);
  }

  @Test
  @Order(7)
  @PlayerA
  @DisplayName("The board stays usable when tiles are unreachable")
  void boardUsableWhenTilesUnreachable(ClientGameTestContext context) {
    given("the BlueMap URL points at a dead port");
    try {
      MainScreen screen =
          context.computeOnClient(
              c -> {
                ClientSession.setBluemapUrl(DEAD_URL);
                SharedTileCache.reset();
                MainScreen s = new MainScreen(null);
                c.gui.setScreen(s);
                return s;
              });
      waitForScreen(context, MainScreen.class);

      then("the map reports tiles unavailable");
      try {
        context.waitFor(
            client -> screen.getMapView() != null && screen.getMapView().tilesUnavailable(),
            TIMEOUT);
      } catch (Throwable t) {
        fail("tilesUnavailable() never became true: " + statsDescription(context), t);
      }
      and("the polygon quest is still drawn and hit-testable");
      context.waitFor(MapBoardJourney::hitsMapQuest, TIMEOUT);
    } finally {
      context.runOnClient(
          c -> {
            ClientSession.setBluemapUrl(handshakeUrl);
            SharedTileCache.reset();
          });
    }
  }

  @Test
  @Order(8)
  @PlayerA
  @DisplayName("A V1 server shows the legacy home")
  void v1ServerShowsLegacyHome(ClientGameTestContext context) {
    given("the server reports protocol V1");
    try {
      context.runOnClient(
          c -> {
            ClientSession.setServerProtocolVersion(ProtocolVersion.V1);
            c.gui.setScreen(new MainScreen(null));
          });
      waitForScreen(context, MainScreen.class);

      then("the legacy home shows the outdated-server notice and no map");
      waitForComponent(context, "server-notice");
      assertComponentExists(context, "server-notice");
      assertNull(
          findComponentOrNull(context, MapView.class, "map-view"),
          "map-view should not exist on the legacy home");
    } finally {
      context.runOnClient(c -> ClientSession.setServerProtocolVersion(ProtocolVersion.V2));
    }
  }

  @Test
  @Order(9)
  @PlayerA
  @DisplayName("A server with world-map off shows the plain list")
  void worldMapOffShowsPlainList(ClientGameTestContext context) {
    given("the server switches the world map off");
    try {
      context.runOnClient(
          c -> {
            ClientSession.setServerWorldMapEnabled(false);
            c.gui.setScreen(new MainScreen(null));
          });
      waitForScreen(context, MainScreen.class);
      waitForComponent(context, "tab-row");

      then("the legacy home shows no notice and no map");
      assertNull(
          findComponentOrNull(context, LabelComponent.class, "server-notice"),
          "server-notice should not show when the map is simply switched off");
      assertNull(
          findComponentOrNull(context, MapView.class, "map-view"),
          "map-view should not exist when the world map is off");
    } finally {
      context.runOnClient(c -> ClientSession.setServerWorldMapEnabled(true));
    }
  }

  @Test
  @Order(10)
  @PlayerA
  @DisplayName("A server with player-marker off hides the marker and opens on the world")
  void playerMarkerOffOpensOnWorld(ClientGameTestContext context) {
    given("the server switches the player marker off and no camera is remembered");
    try {
      context.runOnClient(
          c -> {
            ClientSession.setServerPlayerMarkerEnabled(false);
            // Close first: the outgoing screen remembers its camera in removed().
            c.gui.setScreen(null);
            ClientSession.forgetCameras();
            c.gui.setScreen(new MainScreen(null));
          });
      waitForScreen(context, MainScreen.class);
      waitForComponent(context, "map-view");

      then("the map shows without the player marker or the Centre on me button");
      MapView view = mapView(context);
      assertNull(
          findComponentOrNull(context, ButtonComponent.class, "btn-centre-me"),
          "btn-centre-me should be removed when the player marker is off");
      assertNull(
          context.computeOnClient(c -> view.getLayer(PlayerMarkerLayer.class)),
          "no PlayerMarkerLayer when the player marker is off");

      and("the camera shows the whole world in the server's default dimension");
      MapMath.Camera camera = context.computeOnClient(c -> view.getCamera());
      assertEquals(0, camera.zoom(), "world-view zoom");
      assertEquals(0.0, camera.centerX(), 1e-9, "camera centre x");
      assertEquals(0.0, camera.centerZ(), 1e-9, "camera centre z");
      String expected =
          context.computeOnClient(
              c -> DimensionKeys.normalize(ClientSession.getBluemapDefaultMap()));
      assertEquals(expected, context.computeOnClient(c -> view.getDimension()), "map dimension");
    } finally {
      context.runOnClient(
          c -> {
            ClientSession.setServerPlayerMarkerEnabled(true);
            ClientSession.forgetCameras();
          });
    }
  }
}
