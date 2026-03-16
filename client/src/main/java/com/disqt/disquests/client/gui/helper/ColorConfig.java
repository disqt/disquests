package com.disqt.disquests.client.gui.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("Disquests");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern RGBA_PATTERN = Pattern.compile("rgba\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*([0-9.]+)\\s*\\)");
    private static final Pattern RGB_PATTERN = Pattern.compile("rgb\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("disquests/colors.json");

    public static void loadColors() {
        try {
            File configFile = CONFIG_PATH.toFile();
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    Map<String, String> colorMap = GSON.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
                    if (colorMap != null) {
                        applyColors(colorMap);
                    }
                }
            } else {
                saveDefaultColors();
            }
        } catch (IOException e) {
            LOGGER.error("Error loading color config", e);
        }
    }

    private static void applyColors(Map<String, String> colorMap) {
        for (Map.Entry<String, String> entry : colorMap.entrySet()) {
            try {
                Field field = Colors.class.getField(entry.getKey());
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                    int colorValue = parseColor(entry.getValue());
                    field.set(null, colorValue);
                }
            } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
                LOGGER.warn("Could not apply color '{}' with value '{}': {}", entry.getKey(), entry.getValue(), e.getMessage());
            } catch (Exception e) {
                LOGGER.error("An unexpected error occurred while applying color '{}'", entry.getKey(), e);
            }
        }
    }

    private static void saveDefaultColors() {
        try {
            File configFile = CONFIG_PATH.toFile();
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(Colors.getColorsAsMap(), writer);
            }
        } catch (IOException e) {
            LOGGER.error("Error saving default color config", e);
        }
    }

    private static int parseColor(String colorString) {
        if (colorString == null || colorString.trim().isEmpty()) {
            throw new IllegalArgumentException("Color string cannot be null or empty.");
        }
        String trimmed = colorString.trim();
        if (trimmed.startsWith("#")) {
            long longVal;
            try {
                longVal = Long.parseLong(trimmed.substring(1), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid hex color: " + trimmed, e);
            }
            if (trimmed.length() == 7) return (int) (0xFF000000L | longVal);
            if (trimmed.length() == 9) return (int) longVal;
            throw new IllegalArgumentException("Invalid hex color length: " + trimmed);
        }
        if (trimmed.startsWith("rgba")) {
            Matcher m = RGBA_PATTERN.matcher(trimmed);
            if (m.matches()) {
                int r = Integer.parseInt(m.group(1));
                int g = Integer.parseInt(m.group(2));
                int b = Integer.parseInt(m.group(3));
                int alpha = (int) (Float.parseFloat(m.group(4)) * 255);
                return (alpha & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
            }
        }
        if (trimmed.startsWith("rgb")) {
            Matcher m = RGB_PATTERN.matcher(trimmed);
            if (m.matches()) {
                int r = Integer.parseInt(m.group(1));
                int g = Integer.parseInt(m.group(2));
                int b = Integer.parseInt(m.group(3));
                return 0xFF000000 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
            }
        }
        throw new IllegalArgumentException("Invalid color format: " + colorString);
    }
}
