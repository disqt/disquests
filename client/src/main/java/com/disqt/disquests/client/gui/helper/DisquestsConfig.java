package com.disqt.disquests.client.gui.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DisquestsConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Disquests");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("disquests").resolve("config.json");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int MIN_PINNED_WIDTH = 100;
    public static final int MAX_PINNED_WIDTH = 400;
    private static int pinnedWidth = 200;
    private static Theme theme = Theme.FROSTED;

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            ConfigData data = GSON.fromJson(json, ConfigData.class);
            if (data != null) {
                pinnedWidth = Math.max(MIN_PINNED_WIDTH, Math.min(MAX_PINNED_WIDTH, data.pinnedWidth));
                if (data.theme != null) {
                    try {
                        theme = Theme.valueOf(data.theme);
                    } catch (IllegalArgumentException e) {
                        theme = Theme.FROSTED;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load disquests config, using defaults", e);
        }
    }

    public static void setPinnedWidth(int width) {
        pinnedWidth = Math.max(MIN_PINNED_WIDTH, Math.min(MAX_PINNED_WIDTH, width));
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            ConfigData data = new ConfigData();
            data.pinnedWidth = pinnedWidth;
            data.theme = theme.name();
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.warn("Failed to save disquests config", e);
        }
    }

    public static int getPinnedWidth() { return pinnedWidth; }

    public static Theme getTheme() { return theme; }
    public static void setTheme(Theme t) { theme = t; }

    private static class ConfigData {
        int pinnedWidth = 200;
        String theme = "FROSTED";
    }
}
