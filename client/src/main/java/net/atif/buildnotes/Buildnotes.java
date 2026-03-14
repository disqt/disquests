package net.atif.buildnotes;

import net.atif.buildnotes.network.ModPackets;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Buildnotes implements ModInitializer {
    public static final String MOD_ID = "buildnotes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("BuildNotes Initialized!");
        ModPackets.registerAll();
    }
}
