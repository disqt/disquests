package net.atif.buildnotes.test;

import net.atif.buildnotes.client.ClientSession;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.io.IOException;

public class TestHelper {
    private static final String SERVER_HOST = System.getProperty("buildnotes.test.server.host", "localhost");
    private static final int SERVER_PORT = Integer.getInteger("buildnotes.test.server.port", 25565);
    private static final int RCON_PORT = Integer.getInteger("buildnotes.test.rcon.port", 25575);
    private static final String RCON_PASSWORD = System.getProperty("buildnotes.test.rcon.password", "testpassword");

    public static void connectToServer(ClientGameTestContext context) {
        String address = SERVER_HOST + ":" + SERVER_PORT;
        context.runOnClient(client -> {
            ServerInfo serverInfo = new ServerInfo("Test Server", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(address), serverInfo, false, (CookieStorage) null);
        });
        // Wait for client to be in-game
        context.waitFor(client -> client.currentScreen == null && client.world != null, 600);
    }

    public static void waitForHandshake(ClientGameTestContext context) {
        context.waitFor(client -> ClientSession.isOnServer(), 200);
    }

    public static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.disconnect(Text.literal("Test disconnect"));
        });
        context.waitFor(client -> client.world == null, 200);
        context.waitTicks(20);
    }

    public static String rconCommand(String command) {
        try (RconClient rcon = new RconClient(SERVER_HOST, RCON_PORT, RCON_PASSWORD)) {
            return rcon.command(command);
        } catch (IOException e) {
            throw new RuntimeException("RCON command failed: " + command, e);
        }
    }
}
