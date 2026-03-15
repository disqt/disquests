package net.atif.buildnotes.client;

import net.atif.buildnotes.data.ColorConfig;
import net.atif.buildnotes.data.TabType;
import net.atif.buildnotes.gui.helper.Colors;
import net.atif.buildnotes.gui.screen.MainScreen;
import net.atif.buildnotes.hud.HudPinManager;
import net.atif.buildnotes.network.ClientPacketHandler;
import net.atif.buildnotes.network.RawPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public class BuildnotesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ColorConfig.loadColors();
        KeyBinds.register();
        HudPinManager.init(FabricLoader.getInstance().getConfigDir().resolve("buildnotes"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KeyBinds.openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    Colors.reload();
                    client.setScreen(new MainScreen(TabType.NOTES));
                }
            }
            while (KeyBinds.pinKey.wasPressed()) {
                // Toggle pin off if something is pinned, otherwise no-op
                if (HudPinManager.getPinnedNoteId() != null) {
                    HudPinManager.unpin();
                }
            }
        });

        // Single receiver for all BuildNotes S2C packets
        ClientPlayNetworking.registerGlobalReceiver(RawPayload.ID, ClientPacketHandler::handleRawPayload);

        // Clear caches on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientCache.clear();
            ClientImageTransferManager.clearFailedDownloads();
            ClientSession.leaveServer();
        });
    }
}
