package net.atif.buildnotes.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers the single RawPayload channel for both C2S and S2C directions.
 * All BuildNotes packet types are multiplexed through this one channel.
 */
public class ModPackets {

    public static void registerAll() {
        PayloadTypeRegistry.playC2S().register(RawPayload.ID, RawPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RawPayload.ID, RawPayload.CODEC);
    }
}
