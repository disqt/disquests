package com.disqt.disquests.test;

import com.disqt.disquests.client.ClientSession;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;

/**
 * Shared utilities for Disquests E2E tests.
 */
public class TestHelper {

    private static final String SERVER_HOST =
            System.getProperty("disquests.test.server.host", "localhost");
    private static final int SERVER_PORT =
            Integer.getInteger("disquests.test.server.port", 25565);
    private static final int RCON_PORT =
            Integer.getInteger("disquests.test.rcon.port", 25575);
    private static final String RCON_PASSWORD =
            System.getProperty("disquests.test.rcon.password", "testpassword");

    /**
     * Connect the client to the external Paper server.
     * Waits up to 30 seconds (600 ticks) for the client to be fully in-world.
     */
    public static void connectToServer(ClientGameTestContext context) {
        String address = SERVER_HOST + ":" + SERVER_PORT;
        context.runOnClient(client -> {
            ServerInfo info = new ServerInfo("Test", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client,
                    ServerAddress.parse(address), info, false, null);
        });
        // Wait for the client to be in-game (no screen open + world loaded)
        context.waitFor(client -> client.currentScreen == null && client.world != null, 600);
    }

    /**
     * Wait for the Disquests plugin handshake to complete.
     * The server sends a HANDSHAKE packet which sets ClientSession.isOnServer().
     */
    public static void waitForHandshake(ClientGameTestContext context) {
        context.waitFor(client -> ClientSession.isOnServer(), 200);
    }

    /**
     * Disconnect from the server and wait for the client to return to the menu.
     */
    public static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (client.world != null) {
                client.world.disconnect();
            }
        });
        // Wait for disconnect to complete
        context.waitFor(client -> client.world == null, 200);
        context.waitTicks(20);
    }

    /**
     * Send an RCON command to the Paper server. Opens a new connection each call.
     */
    public static String rconCommand(String cmd) {
        try (RconClient rcon = new RconClient(SERVER_HOST, RCON_PORT, RCON_PASSWORD)) {
            return rcon.command(cmd);
        } catch (IOException e) {
            throw new RuntimeException("RCON failed: " + cmd, e);
        }
    }
}
