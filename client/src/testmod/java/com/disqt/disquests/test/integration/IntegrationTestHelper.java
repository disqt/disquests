package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class IntegrationTestHelper {

    public static final int TIMEOUT = 30 * 20; // 30 seconds in ticks

    public static boolean shouldSkip(String journeyName) {
        String selected = System.getProperty("disquests.test.journey");
        return selected == null || !selected.equals(journeyName);
    }

    public static void connectAndWait(ClientGameTestContext context) {
        String host = System.getProperty("disquests.test.server.host", "localhost");
        int port = Integer.parseInt(System.getProperty("disquests.test.server.port", "25565"));
        String address = host + ":" + port;

        context.runOnClient(client -> {
            ServerAddress serverAddress = ServerAddress.parse(address);
            ServerInfo serverInfo = new ServerInfo("Integration Test", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client, serverAddress, serverInfo, false, null);
        });

        context.waitFor(client -> client.player != null, TIMEOUT);
        context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);
        context.waitTicks(10);
    }

    public static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
        context.waitForScreen(TitleScreen.class);
    }

    public static Quest waitForQuestByTitle(ClientGameTestContext context, String title, boolean myQuests) {
        context.waitFor(client -> {
            var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
            return list.stream().anyMatch(q -> title.equals(q.getTitle()));
        }, TIMEOUT);

        return context.computeOnClient(client -> {
            var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
            return list.stream().filter(q -> title.equals(q.getTitle())).findFirst().orElse(null);
        });
    }

    public static void waitForQuestRemoved(ClientGameTestContext context, UUID questId) {
        context.waitFor(client ->
            ClientCache.getMyQuests().stream().noneMatch(q -> q.getId().equals(questId)),
            TIMEOUT);
    }

    public static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }
}
