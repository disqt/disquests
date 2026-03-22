package com.disqt.disquests.test.integration.journeys;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import io.wispforest.owo.ui.component.LabelComponent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Coordinates Journey")
class CoordinatesJourney {

    @BeforeAll
    static void resetServer() {
        resetServerAndSync();
        AbortOnFailureExtension.clearFailures();
    }

    @Test @Order(1) @PlayerA
    @DisplayName("Create a quest and enter edit mode")
    void createQuest(ClientGameTestContext context) {
        given("player is on MainScreen");
            openMainScreen(context);
        when("player creates a new quest");
            click(context, "btn-new-quest");
        then("QuestScreen opens in edit mode");
            waitForScreen(context, QuestScreen.class);
        when("player types a title");
            type(context, "title-field", "Coords Test");
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Enter X/Y/Z coordinates in fields")
    void enterCoordinates(ClientGameTestContext context) {
        when("player types X coordinate");
            type(context, "coord-x1", "100");
        and("player types Y coordinate");
            type(context, "coord-y1", "64");
        and("player types Z coordinate");
            type(context, "coord-z1", "200");
        then("coord fields show the entered values");
            // Verified by save in next step
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Save and verify coords display in view mode")
    void saveAndVerifyCoords(ClientGameTestContext context) {
        when("player clicks Save");
            click(context, "btn-save");
        then("view mode shows the quest");
            waitForScreen(context, QuestScreen.class);
        and("coords label shows the coordinates");
            assertLabelText(context, "coords-label", "X:100 Y:64 Z:200");
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Toggle region on and enter corner 2 coordinates")
    void toggleRegion(ClientGameTestContext context) {
        when("player clicks Edit");
            click(context, "btn-edit");
            waitForScreen(context, QuestScreen.class);
        and("player toggles Region on");
            click(context, "btn-region");
            // Region toggle rebuilds screen
            waitForScreen(context, QuestScreen.class);
        then("corner 2 row appears");
            assertComponentExists(context, "coord-x2");
        when("player types corner 2 coordinates");
            type(context, "coord-x2", "150");
            type(context, "coord-y2", "70");
            type(context, "coord-z2", "250");
    }

    @Test @Order(5) @PlayerA
    @DisplayName("Save and verify region display")
    void saveAndVerifyRegion(ClientGameTestContext context) {
        when("player clicks Save");
            click(context, "btn-save");
        then("view mode shows region coordinates");
            waitForScreen(context, QuestScreen.class);
        and("coords label shows region format");
            // Region: X:100-150 Y:64-70 Z:200-250
            assertComponent(context, "coords-label", LabelComponent.class,
                label -> label.text().getString().startsWith("Region:"),
                "coords-label should show Region: prefix");
    }

    @Test @Order(6) @PlayerA
    @DisplayName("Cycle Map button through all values")
    void cycleMapButton(ClientGameTestContext context) {
        when("player clicks Edit");
            click(context, "btn-edit");
            waitForScreen(context, QuestScreen.class);
        then("map button shows current value (any)");
            assertButtonText(context, "btn-map", "Map:");

        when("player clicks Map button once (any -> overworld)");
            click(context, "btn-map");
            waitForScreen(context, QuestScreen.class);
        then("map button shows overworld");
            assertButtonText(context, "btn-map", "overworld");

        when("player clicks Map button again (overworld -> the_nether)");
            click(context, "btn-map");
            waitForScreen(context, QuestScreen.class);
        then("map button shows the_nether");
            assertButtonText(context, "btn-map", "the_nether");

        when("player clicks Map button again (the_nether -> the_end)");
            click(context, "btn-map");
            waitForScreen(context, QuestScreen.class);
        then("map button shows the_end");
            assertButtonText(context, "btn-map", "the_end");
    }

    @Test @Order(7) @PlayerA
    @DisplayName("Save with map selected and verify map label")
    void saveAndVerifyMap(ClientGameTestContext context) {
        // Still in edit mode from previous step (map = the_end)
        when("player clicks Save");
            click(context, "btn-save");
        then("view mode shows the quest");
            waitForScreen(context, QuestScreen.class);
        and("map label shows the selected map");
            assertLabelText(context, "map-label", "Map: the_end");
    }

    @Test @Order(8) @PlayerA
    @DisplayName("Clear coordinates and verify no coords in view")
    void clearCoordinates(ClientGameTestContext context) {
        when("player clicks Edit");
            click(context, "btn-edit");
            waitForScreen(context, QuestScreen.class);
        and("player clicks Clear");
            click(context, "btn-clear");
            // Clear rebuilds the screen
            waitForScreen(context, QuestScreen.class);
        then("coord fields are now empty");
            // No coord2 row since region is cleared
            assertComponentMissing(context, "coord-x2");

        when("player clicks Save");
            click(context, "btn-save");
        then("view mode shows no coordinates");
            waitForScreen(context, QuestScreen.class);
        and("metadata row is zero-sized (coords hidden)");
            // When hasCoords=false, metadata-row is zero-sized: width==0 && height==0
            assertComponent(context, "metadata-row",
                io.wispforest.owo.ui.core.UIComponent.class,
                c -> c.width() == 0 && c.height() == 0,
                "metadata-row should be hidden when no coordinates set");
    }

    @Test @Order(9) @PlayerA
    @DisplayName("Return to MainScreen")
    void returnToMain(ClientGameTestContext context) {
        when("player closes the quest screen");
            click(context, "btn-close");
        then("MainScreen is shown");
            waitForScreen(context, MainScreen.class);
    }
}
