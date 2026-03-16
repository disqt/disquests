package com.disqt.disquests.test;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.screen.MainScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * E2E smoke test: connects to an external Paper server, waits for the Disquests
 * handshake, opens MainScreen, and verifies it loaded.
 *
 * Server coordinates come from the JVM system properties set in the
 * {@code clientGameTest} run config in client/build.gradle.kts:
 *   -Ddisquests.test.server.host=localhost
 *   -Ddisquests.test.server.port=25565
 *
 * Run with: ./gradlew :client:runClientGameTest
 */
public class DisquestsE2ETest implements FabricClientGameTest {

    /** Default timeout: 30 seconds (20 ticks/sec). */
    private static final int CONNECT_TIMEOUT_TICKS = 30 * 20;

    @Override
    public void runTest(ClientGameTestContext context) {
        String host = System.getProperty("disquests.test.server.host", "localhost");
        int port = Integer.parseInt(System.getProperty("disquests.test.server.port", "25565"));
        String address = host + ":" + port;

        // --- Step 1: Connect to the external Paper server ---
        context.runOnClient(client -> {
            ServerAddress serverAddress = ServerAddress.parse(address);
            ServerInfo serverInfo = new ServerInfo("Disquests E2E Test Server", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client, serverAddress, serverInfo, false, null);
        });

        // Wait until we are fully in-game (player is non-null, indicating world load)
        context.waitFor(client -> client.player != null, CONNECT_TIMEOUT_TICKS);

        // --- Step 2: Wait for Disquests handshake ---
        // ClientSession.isOnServer() is set to true when the HANDSHAKE S2C packet is received.
        context.waitFor(client -> ClientSession.isOnServer(), CONNECT_TIMEOUT_TICKS);

        // --- Step 3: Open MainScreen ---
        context.setScreen(MainScreen::new);
        context.waitForScreen(MainScreen.class);

        // --- Step 4: Verify MainScreen loaded ---
        boolean onCorrectScreen = context.computeOnClient(client -> client.currentScreen instanceof MainScreen);
        if (!onCorrectScreen) {
            throw new AssertionError("Expected MainScreen to be open after setScreen");
        }

        // --- Step 5: Close the screen ---
        context.setScreen(() -> null);
        context.waitForScreen(null);

        // --- Step 6: Disconnect and return to title screen ---
        context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
        context.waitForScreen(TitleScreen.class);

        // TODO: Add quest CRUD smoke test (create, save, verify in list, delete)
        // TODO: Add multi-pin test (pin multiple quests, verify HUD shows them all)
        // TODO: Add view/edit toggle test (open quest in view mode, switch to edit, save)
        // TODO: Add server quest tab test (switch to Quest Board tab, verify list loads)
        // TODO: Add collaboration flow test (request access, approve, verify contributor added)
    }
}
