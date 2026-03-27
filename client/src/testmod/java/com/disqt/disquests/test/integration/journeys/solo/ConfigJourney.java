package com.disqt.disquests.test.integration.journeys.solo;

import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.gui.helper.Theme;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.TestContext;
import io.wispforest.owo.config.ui.ConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Config Journey")
class ConfigJourney {

    @BeforeAll
    static void resetServer() throws Exception {
        resetServerAndSync();
        AbortOnFailureExtension.clearFailures();
        // Reset theme to FROSTED (the default) to ensure a clean starting point
        TestContext.get().runOnClient(c -> {
            DisquestsClient.CONFIG.theme(Theme.FROSTED);
            DisquestsClient.CONFIG.save();
            c.setScreen(null);
        });
        TestContext.get().waitTicks(2);
    }

    @Test @Order(1) @PlayerA
    @DisplayName("JSON5 lang loading works")
    void langKeysResolve(ClientGameTestContext context) {
        given("the mod is loaded with JSON5 lang files");
        when("we resolve a known translation key");
            String resolved = context.computeOnClient(c ->
                net.minecraft.text.Text.translatable("key.category.disquests.main").getString()
            );
        then("the translation resolves to the expected value");
            assertEquals("Disquests", resolved,
                "JSON5 lang key 'key.category.disquests.main' should resolve to 'Disquests'");
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Open owo-config screen")
    void openOwoConfigScreen(ClientGameTestContext context) {
        given("player is connected");
        when("player opens the owo-config screen");
            context.runOnClient(c ->
                c.setScreen(ConfigScreen.create(DisquestsClient.CONFIG, null)));
            waitForScreen(context, ConfigScreen.class);
        then("owo-config ConfigScreen is displayed");
            boolean isConfigScreen = context.computeOnClient(c ->
                c.currentScreen instanceof ConfigScreen);
            assertTrue(isConfigScreen, "Expected owo-config ConfigScreen to be open");
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Close config screen and verify config API works")
    void closeAndVerifyConfig(ClientGameTestContext context) {
        given("owo-config screen is open");
        when("player closes the config screen");
            context.runOnClient(c -> c.setScreen(null));
            context.waitTicks(2);
        then("config values are accessible via the wrapper API");
            Theme currentTheme = context.computeOnClient(c -> DisquestsClient.CONFIG.theme());
            assertNotNull(currentTheme, "CONFIG.theme() should return a non-null Theme");
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Theme change persists through config API")
    void themeChangePersists(ClientGameTestContext context) {
        given("theme is set to FROSTED (default)");
            context.runOnClient(c -> {
                DisquestsClient.CONFIG.theme(Theme.FROSTED);
                DisquestsClient.CONFIG.save();
            });
            context.waitTicks(2);

        when("player changes theme to FLAT via config API");
            context.runOnClient(c -> {
                DisquestsClient.CONFIG.theme(Theme.FLAT);
                DisquestsClient.CONFIG.save();
            });
            context.waitTicks(2);

        then("theme is FLAT");
            Theme savedTheme = context.computeOnClient(c -> DisquestsClient.CONFIG.theme());
            assertEquals(Theme.FLAT, savedTheme, "Theme should be FLAT after saving");

        // Cleanup: restore default theme so other journeys start from a clean state
        context.runOnClient(c -> {
            DisquestsClient.CONFIG.theme(Theme.FROSTED);
            DisquestsClient.CONFIG.save();
            c.setScreen(null);
        });
        context.waitTicks(2);
    }
}
