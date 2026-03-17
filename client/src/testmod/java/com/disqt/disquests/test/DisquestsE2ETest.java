package com.disqt.disquests.test;

import com.disqt.disquests.client.ClientCache;
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
 * handshake, opens MainScreen, verifies tabs and quest board, then disconnects.
 *
 * Server coordinates come from JVM system properties:
 *   -Ddisquests.test.server.host=localhost
 *   -Ddisquests.test.server.port=25565
 *
 * Run with: ./gradlew :client:runClientGameTest
 */
public class DisquestsE2ETest implements FabricClientGameTest {

    private static final int TIMEOUT = 30 * 20; // 30 seconds

    @Override
    public void runTest(ClientGameTestContext context) {
        String host = System.getProperty("disquests.test.server.host", "localhost");
        int port = Integer.parseInt(System.getProperty("disquests.test.server.port", "25565"));
        String address = host + ":" + port;

        // --- Step 1: Connect to Paper server ---
        context.runOnClient(client -> {
            ServerAddress serverAddress = ServerAddress.parse(address);
            ServerInfo serverInfo = new ServerInfo("E2E Test", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client, serverAddress, serverInfo, false, null);
        });
        context.waitFor(client -> client.player != null, TIMEOUT);

        // --- Step 2: Wait for Disquests handshake ---
        context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);

        // --- Step 3: Open MainScreen ---
        context.setScreen(MainScreen::new);
        context.waitForScreen(MainScreen.class);

        // --- Step 4: Verify MainScreen loaded ---
        boolean onMainScreen = context.computeOnClient(client ->
                client.currentScreen instanceof MainScreen);
        if (!onMainScreen) {
            throw new AssertionError("Expected MainScreen");
        }

        // --- Step 5: Verify Quest Board has seeded quests ---
        // Server quests are synced after handshake; wait for them
        context.waitFor(client -> ClientCache.getServerQuests().size() >= 2, TIMEOUT);

        int serverQuestCount = context.computeOnClient(client ->
                ClientCache.getServerQuests().size());
        if (serverQuestCount < 2) {
            throw new AssertionError("Expected at least 2 server quests on Quest Board, got " + serverQuestCount);
        }

        // --- Step 6: Close MainScreen ---
        context.setScreen(() -> null);
        context.waitForScreen(null);

        // --- Step 7: Disconnect and return to title screen ---
        context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
        context.waitForScreen(TitleScreen.class);
    }
}
