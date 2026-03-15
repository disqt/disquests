package com.disqt.disquests.client;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.network.ClientPacketHandler;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.client.network.RawPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class DisquestsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBinds.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KeyBinds.openGuiKey.wasPressed()) {
                if (client.currentScreen == null && ClientSession.isOnServer()) {
                    client.setScreen(new MainScreen());
                }
            }
            while (KeyBinds.pinKey.wasPressed()) {
                if (ClientSession.getPinnedQuestId() != null) {
                    PacketSender.pinQuest(null);
                    ClientSession.setPinnedQuestId(null);
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(RawPayload.ID, ClientPacketHandler::handleRawPayload);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientCache.clear();
            ClientSession.leaveServer();
        });
    }
}
