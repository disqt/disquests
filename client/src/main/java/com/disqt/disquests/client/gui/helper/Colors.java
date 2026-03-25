package com.disqt.disquests.client.gui.helper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable color constants used by all Disquests UI components.
 * Values are set by {@link Theme#applyColors()} on startup and
 * whenever the user changes the theme via owo-config.
 */
public final class Colors {

    // --- Panel ---
    public static int PANEL_BACKGROUND = 0x77000000;

    // --- Text ---
    public static int TEXT_PRIMARY = 0xFFFFFFFF;
    public static int TEXT_MUTED = 0xFFCCCCCC;
    public static int TEXT_DISABLED = 0xFF888888;

    // --- Selection ---
    public static int SELECTION_BACKGROUND = 0x8855AADD;

    // --- Caret ---
    public static int CARET_PRIMARY = 0xFFFFFFFF;

    // --- Buttons ---
    public static int BUTTON_DISABLED = 0x44000000;
    public static int BUTTON_HOVER = 0xAA000000;

    // --- Tabs ---
    public static int TAB_INACTIVE = 0x44000000;

    // --- Scrollbar ---
    public static int SCROLLBAR_THUMB_INACTIVE = 0x88FFFFFF;
    public static int SCROLLBAR_THUMB_ACTIVE = 0xFFFFFFFF;

    // --- Background ---
    public static int GRADIENT_START = 0xFF101010;
    public static int GRADIENT_END = 0xFF101010;

    // --- Fade Gradient ---
    public static int FADE_GRADIENT_TOP = 0x60000000;
    public static int FADE_GRADIENT_BOTTOM = 0x00000000;

    // --- Accent ---
    public static int AMBER = 0xFFFFAA33;

    // --- Badge ---
    public static int BADGE_RED = 0xFFCC3333;

    // --- Entry Highlights ---
    public static int ENTRY_HOVER = 0x22FFFFFF;
    public static int ENTRY_SELECTED = 0x44FFFFFF;

    // --- Accent Line ---
    public static int ACCENT_LINE_ACTIVE = 0x00000000;
    public static int ACCENT_LINE_INACTIVE = 0x00000000;

    private Colors() {}

    public static void applyTheme(Theme theme) {
        theme.applyColors();
    }

    public static Map<String, String> getColorsAsMap() {
        Map<String, String> colorMap = new LinkedHashMap<>();
        for (Field field : Colors.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                try {
                    int colorValue = (Integer) field.get(null);
                    int a = (colorValue >> 24) & 0xFF;
                    int r = (colorValue >> 16) & 0xFF;
                    int g = (colorValue >> 8) & 0xFF;
                    int b = colorValue & 0xFF;
                    float alpha = a / 255.0f;

                    colorMap.put(field.getName(), String.format("rgba(%d, %d, %d, %.3f)", r, g, b, alpha));
                } catch (IllegalAccessException e) {
                    // ignore
                }
            }
        }
        return colorMap;
    }
}
