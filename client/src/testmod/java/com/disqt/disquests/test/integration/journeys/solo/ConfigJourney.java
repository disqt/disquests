package com.disqt.disquests.test.integration.journeys.solo;

import com.disqt.disquests.client.gui.helper.ColorConfig;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.helper.Theme;
import com.disqt.disquests.client.gui.screen.ConfigScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.TestContext;
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
@DisplayName("Config Journey")
class ConfigJourney {

    @BeforeAll
    static void resetServer() throws Exception {
        resetServerAndSync();
        AbortOnFailureExtension.clearFailures();
        // Reset theme to VANILLA and clear ConfigScreen static state
        // to ensure a clean starting point regardless of journey ordering.
        TestContext.get().runOnClient(c -> {
            ConfigScreen.resetOriginalTheme();
            DisquestsConfig.setTheme(Theme.VANILLA);
            Theme.VANILLA.applyColors();
            ColorConfig.loadColors();
            DisquestsConfig.save();
            c.setScreen(null);
        });
        TestContext.get().waitTicks(2);
    }

    private void openConfigScreen(ClientGameTestContext context) {
        context.runOnClient(c -> c.setScreen(new ConfigScreen(null)));
        waitForScreen(context, ConfigScreen.class);
    }

    @Test @Order(1) @PlayerA
    @DisplayName("Open ConfigScreen")
    void openConfig(ClientGameTestContext context) {
        given("player is connected");
        when("player opens ConfigScreen");
            openConfigScreen(context);
        then("ConfigScreen is displayed");
            assertScreenIs(context, ConfigScreen.class);
        and("theme button shows the current theme name");
            // Theme starts at VANILLA by default (or whatever was persisted).
            // We only verify it's a valid theme name -- the exact value depends on prior state.
            String btnText = context.computeOnClient(c -> {
                if (c.currentScreen instanceof ConfigScreen) {
                    var root = ((com.disqt.disquests.client.gui.screen.DisquestsBaseScreen) c.currentScreen).getRootComponent();
                    if (root == null) return null;
                    var btn = root.childById(io.wispforest.owo.ui.component.ButtonComponent.class, "btn-theme");
                    return btn != null ? btn.getMessage().getString() : null;
                }
                return null;
            });
            assertNotNull(btnText, "btn-theme should be present and have text");
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Cycle through all 5 themes")
    void cycleAllThemes(ClientGameTestContext context) {
        given("ConfigScreen is open, starting from VANILLA theme");
            // Reset to VANILLA so cycle is deterministic
            context.runOnClient(c -> {
                DisquestsConfig.setTheme(Theme.VANILLA);
                Theme.VANILLA.applyColors();
                com.disqt.disquests.client.gui.helper.ColorConfig.loadColors();
                c.setScreen(new ConfigScreen(null));
            });
            waitForScreen(context, ConfigScreen.class);

        when("player clicks btn-theme once (VANILLA -> FLAT)");
            click(context, "btn-theme");
            waitForScreen(context, ConfigScreen.class);
        then("button shows Flat");
            assertButtonText(context, "btn-theme", "Flat");

        when("player clicks btn-theme again (FLAT -> INSET)");
            click(context, "btn-theme");
            waitForScreen(context, ConfigScreen.class);
        then("button shows Inset");
            assertButtonText(context, "btn-theme", "Inset");

        when("player clicks btn-theme again (INSET -> FROSTED)");
            click(context, "btn-theme");
            waitForScreen(context, ConfigScreen.class);
        then("button shows Frosted");
            assertButtonText(context, "btn-theme", "Frosted");

        when("player clicks btn-theme again (FROSTED -> ACCENT_LINE)");
            click(context, "btn-theme");
            waitForScreen(context, ConfigScreen.class);
        then("button shows Accent Line");
            assertButtonText(context, "btn-theme", "Accent Line");

        when("player clicks btn-theme again (ACCENT_LINE -> VANILLA)");
            click(context, "btn-theme");
            waitForScreen(context, ConfigScreen.class);
        then("button cycles back to Vanilla");
            assertButtonText(context, "btn-theme", "Vanilla");
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Cancel reverts theme to original")
    void cancelRevertsTheme(ClientGameTestContext context) {
        given("ConfigScreen is open at VANILLA theme");
            context.runOnClient(c -> {
                DisquestsConfig.setTheme(Theme.VANILLA);
                Theme.VANILLA.applyColors();
                com.disqt.disquests.client.gui.helper.ColorConfig.loadColors();
                DisquestsConfig.save();
                c.setScreen(new ConfigScreen(null));
            });
            waitForScreen(context, ConfigScreen.class);

        when("player cycles to FLAT theme");
            click(context, "btn-theme");
            waitForScreen(context, ConfigScreen.class);
            assertButtonText(context, "btn-theme", "Flat");

        and("player clicks Cancel");
            click(context, "btn-cancel");

        then("theme reverts to VANILLA");
            context.waitFor(client -> DisquestsConfig.getTheme() == Theme.VANILLA, TIMEOUT);
            Theme currentTheme = context.computeOnClient(c -> DisquestsConfig.getTheme());
            assertEquals(Theme.VANILLA, currentTheme,
                "Cancelling should revert theme to VANILLA (the original before opening config)");
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Select FLAT theme and save")
    void selectFlatAndSave(ClientGameTestContext context) {
        given("ConfigScreen is open at VANILLA theme");
            context.runOnClient(c -> {
                DisquestsConfig.setTheme(Theme.VANILLA);
                Theme.VANILLA.applyColors();
                com.disqt.disquests.client.gui.helper.ColorConfig.loadColors();
                DisquestsConfig.save();
                c.setScreen(new ConfigScreen(null));
            });
            waitForScreen(context, ConfigScreen.class);

        when("player cycles to FLAT theme");
            click(context, "btn-theme");
            waitForScreen(context, ConfigScreen.class);
            assertButtonText(context, "btn-theme", "Flat");

        and("player clicks Save");
            click(context, "btn-save");

        then("ConfigScreen closes");
            context.waitFor(client -> !(client.currentScreen instanceof ConfigScreen), TIMEOUT);

        and("FLAT theme is persisted in config");
            Theme savedTheme = context.computeOnClient(c -> DisquestsConfig.getTheme());
            assertEquals(Theme.FLAT, savedTheme, "FLAT theme should be saved after clicking Save");
    }

    @Test @Order(5) @PlayerA
    @DisplayName("Reopen ConfigScreen and verify FLAT theme persisted")
    void verifyThemePersisted(ClientGameTestContext context) {
        given("FLAT theme was saved in the previous step");
        when("player reopens ConfigScreen");
            openConfigScreen(context);
        then("theme button shows Flat (persisted)");
            assertButtonText(context, "btn-theme", "Flat");

        and("DisquestsConfig reports FLAT theme");
            Theme currentTheme = context.computeOnClient(c -> DisquestsConfig.getTheme());
            assertEquals(Theme.FLAT, currentTheme, "DisquestsConfig should report FLAT after reload");

        // Cleanup: restore VANILLA so other journeys start from a clean state
        context.runOnClient(c -> {
            DisquestsConfig.setTheme(Theme.VANILLA);
            Theme.VANILLA.applyColors();
            com.disqt.disquests.client.gui.helper.ColorConfig.loadColors();
            DisquestsConfig.save();
            c.setScreen(null);
        });
        context.waitTicks(2);
    }
}
