package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.map.MapView;
import com.disqt.disquests.client.gui.map.PolygonEditLayer;
import com.disqt.disquests.client.gui.map.QuestPolygonLayer;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.MapEditScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.component.LabelComponent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Drawing a quest outline on the full-screen map editor: the editor's location preview ({@code
 * mini-map}) opens it, corners are added by clicking, the last one removed with a right click,
 * Enter finishes, the saved region shows on the board, a self-intersecting outline is rejected, and
 * two corners come back as a pin on the first. Corners are 60 blocks (60 px at zoom 2) apart, well
 * outside the 6 px first-corner hit radius.
 */
@IntegrationTest
@DisplayName("Map Edit Journey")
class MapEditJourney {
  private static final String TITLE = "Drawn Quest";
  private static final String POINT_TITLE = "Point Quest";

  // Player's rounded block position, shared across the ordered steps.
  private static int px;
  private static int pz;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  // --- Helpers ---

  /** The current edit layer's corners. */
  private static int vertexCount(ClientGameTestContext context) {
    MapView view = mapView(context);
    return context.computeOnClient(c -> view.getLayer(PolygonEditLayer.class).vertices().size());
  }

  /** Right-click the centre of the current map through the real input pipeline. */
  private static void rightClickMapCentre(ClientGameTestContext context) {
    double[] centre = componentCenter(context, mapView(context));
    double scale = scaleFactor(context);
    context.getInput().setCursorPos(centre[0] * scale, centre[1] * scale);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
    context.waitTicks(2);
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
    // Centroid of (px-30, pz-30), (px+30, pz-30), (px, pz+30)
    double[] rel = view.blockToScreen(px, pz - 10);
    return layer.hitTest(view, rel[0], rel[1]);
  }

  /** The open editor's draft quest, or null unless a QuestScreen is in edit mode. Client thread. */
  private static Quest editorQuest(Minecraft client) {
    return client.gui.screen() instanceof QuestScreen qs && qs.isEditing() ? qs.getQuest() : null;
  }

  /** The current screen's {@code title-field} text, or null. Client thread only. */
  private static String titleFieldText(Minecraft client) {
    if (client.gui.screen() instanceof DisquestsBaseScreen screen
        && screen.getRootComponent() != null) {
      TextFieldComponent field =
          screen.getRootComponent().childById(TextFieldComponent.class, "title-field");
      return field != null ? field.getText() : null;
    }
    return null;
  }

  /** The current screen's {@code hint-label} text, or null. Client thread only. */
  private static String hintText(Minecraft client) {
    if (client.gui.screen() instanceof DisquestsBaseScreen screen
        && screen.getRootComponent() != null) {
      LabelComponent label =
          screen.getRootComponent().childById(LabelComponent.class, "hint-label");
      return label != null ? label.text().getString() : null;
    }
    return null;
  }

  // --- Steps ---

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Start a location from an empty preview")
  void startFromEmptyPreview(ClientGameTestContext context) {
    given("player is on MainScreen with the My Quests tab");
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
        });
    int[] player =
        context.computeOnClient(
            c -> new int[] {(int) Math.round(c.player.getX()), (int) Math.round(c.player.getZ())});
    px = player[0];
    pz = player[1];
    openMainScreen(context);

    when("player creates a new quest and types a title");
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", TITLE);

    and("player clicks the empty location preview");
    click(context, "mini-map");
    then("the map editor opens");
    waitForMapEditor(context);
    assertEquals(0, vertexCount(context), "an empty preview should open an empty drawing");

    when("player cancels");
    click(context, "btn-map-cancel");
    then("the quest editor is back with no location");
    waitForScreen(context, QuestScreen.class);
    // Only edit mode has btn-set-pos
    waitForComponent(context, "btn-set-pos");
    boolean located = context.computeOnClient(c -> editorQuest(c).hasLocation());
    assertFalse(located, "the quest should have no location after Cancel");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Open the map editor from the preview")
  void openEditorOnMap(ClientGameTestContext context) {
    when("player clicks the location preview");
    click(context, "mini-map");
    then("the map editor opens");
    waitForMapEditor(context);
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("Click three corners")
  void drawThreeCorners(ClientGameTestContext context) {
    when("player clicks three corners around their position");
    clickMapAtBlock(context, px - 30, pz - 30);
    clickMapAtBlock(context, px + 30, pz - 30);
    clickMapAtBlock(context, px, pz + 30);
    then("the edit layer has three corners");
    assertEquals(3, vertexCount(context), "corner count after three clicks");
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("Right click removes the last corner, a click adds it back")
  void rightClickRemovesAndReadds(ClientGameTestContext context) {
    when("player right-clicks the map");
    rightClickMapCentre(context);
    then("the last corner is removed");
    assertEquals(2, vertexCount(context), "corner count after right click");

    when("player clicks the third corner again");
    clickMapAtBlock(context, px, pz + 30);
    then("there are three corners again");
    assertEquals(3, vertexCount(context), "corner count after re-adding");
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("Enter finishes and returns to the editor")
  void enterFinishesAndReturnsToEditor(ClientGameTestContext context) {
    when("player presses Enter");
    pressKey(context, InputConstants.KEY_RETURN);
    then("the quest editor is back");
    waitForScreen(context, QuestScreen.class);
    waitForComponent(context, "btn-set-pos");
    // applyMapLocation rebuilds the editor; wait for the rebuilt one's three corners
    context.waitFor(
        c -> {
          Quest q = editorQuest(c);
          return q != null && q.getPolygon().size() == 3;
        },
        TIMEOUT);
    and("the quest's first corner is the first click");
    double[] first =
        context.computeOnClient(
            c -> {
              var p = editorQuest(c).getPolygon().get(0);
              return new double[] {p.x(), p.z()};
            });
    assertEquals(px - 30, first[0], 1e-9, "first corner x");
    assertEquals(pz - 30, first[1], 1e-9, "first corner z");
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("Save and see the region in view mode and on the board")
  void saveAndReopenShowsPolygon(ClientGameTestContext context) {
    when("player clicks Save");
    click(context, "btn-save");
    waitForQuestCondition(context, TITLE, true, q -> q.getPolygon().size() == 3);
    // Only view mode has btn-edit
    waitForComponent(context, "btn-edit");

    then("the server stores the three corners");
    // The save already wrote the local cache, so only a fresh sync proves the round-trip. Clearing
    // the cache would close the open QuestScreen; the sync replaces the quest under the same id.
    long before = context.computeOnClient(c -> ClientCache.getVersion());
    context.runOnClient(c -> PacketSender.requestSync());
    context.waitFor(c -> ClientCache.getVersion() != before, TIMEOUT);
    waitForQuestCondition(
        context,
        TITLE,
        true,
        q ->
            q.getPolygon().size() == 3
                && q.getPolygon().get(0).x() == px - 30
                && q.getPolygon().get(0).z() == pz - 30);

    and("view mode shows the outline on the map, without a line of corner coordinates");
    waitForComponent(context, "mini-map");
    assertComponentMissing(context, "coords-label");

    when("player goes back to the board");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    then("hit-testing the board at the triangle's centroid finds the quest");
    context.waitFor(
        client -> {
          Quest hit = hitAtCentroid(client);
          return hit != null && TITLE.equals(hit.getTitle());
        },
        TIMEOUT);
  }

  @Test
  @Order(7)
  @PlayerA
  @DisplayName("A self-intersecting outline is rejected")
  void selfIntersectionRejected(ClientGameTestContext context) {
    given("player opens the quest from the board and enters edit mode");
    clickMapAtBlock(context, px, pz - 10);
    waitForScreen(context, QuestScreen.class);
    waitForComponent(context, "btn-edit");
    click(context, "btn-edit");
    // Only edit mode has btn-set-pos
    waitForComponent(context, "btn-set-pos");

    when("player opens the map editor from the preview and clears the location");
    click(context, "mini-map");
    waitForMapEditor(context);
    click(context, "btn-map-clear");
    // Clear rebuilds the editor; MapEditScreen has no btn-set-pos
    waitForComponent(context, "btn-set-pos");
    then("the quest has no location");
    context.waitFor(
        c -> {
          Quest q = editorQuest(c);
          return q != null && !q.hasLocation();
        },
        TIMEOUT);

    when("player reopens the map editor and draws a bowtie");
    click(context, "mini-map");
    waitForMapEditor(context);
    clickMapAtBlock(context, px - 30, pz - 30);
    clickMapAtBlock(context, px + 30, pz + 30);
    clickMapAtBlock(context, px + 30, pz - 30);
    clickMapAtBlock(context, px - 30, pz + 30);
    assertEquals(4, vertexCount(context), "bowtie corner count");

    and("player presses Enter");
    pressKey(context, InputConstants.KEY_RETURN);
    then("the map editor stays open with the self-intersection notice");
    String expected =
        context.computeOnClient(
            c -> Component.translatable("gui.disquests.map.self_intersecting").getString());
    // hint-label is refreshed in tick()
    context.waitFor(client -> expected.equals(hintText(client)), TIMEOUT);
    assertScreenIs(context, MapEditScreen.class);

    when("player cancels");
    click(context, "btn-map-cancel");
    then("the quest editor is back");
    waitForScreen(context, QuestScreen.class);
    waitForComponent(context, "btn-set-pos");
  }

  @Test
  @Order(8)
  @PlayerA
  @DisplayName("Two corners become a point")
  void twoCornersBecomeAPoint(ClientGameTestContext context) {
    given("player is in the editor with no location and types a new title");
    type(context, "title-field", POINT_TITLE);

    when("player opens the map editor from the preview and places two corners");
    click(context, "mini-map");
    waitForMapEditor(context);
    clickMapAtBlock(context, px - 30, pz - 30);
    clickMapAtBlock(context, px + 30, pz + 30);
    assertEquals(2, vertexCount(context), "corner count after two clicks");

    and("player presses Done");
    click(context, "btn-map-done");
    then("the quest editor is back");
    waitForScreen(context, QuestScreen.class);
    waitForComponent(context, "btn-set-pos");
    // applyMapLocation rebuilds the editor; wait for the rebuilt one's pin
    context.waitFor(
        c -> {
          Quest q = editorQuest(c);
          return q != null && q.getCoordinates() != null;
        },
        TIMEOUT);

    and("the quest is a point at the first corner");
    Quest quest = context.computeOnClient(MapEditJourney::editorQuest);
    boolean noPolygon = context.computeOnClient(c -> quest.getPolygon().isEmpty());
    assertTrue(noPolygon, "polygon should be empty");
    assertEquals(px - 30, context.computeOnClient(c -> quest.getCoordinates().x()), 1e-9, "pin x");
    assertEquals(pz - 30, context.computeOnClient(c -> quest.getCoordinates().z()), 1e-9, "pin z");

    and("the title typed before opening the map is still in the title field");
    assertEquals(
        POINT_TITLE,
        context.computeOnClient(MapEditJourney::titleFieldText),
        "title-field text after Done");
  }
}
