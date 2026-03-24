package com.disqt.disquests.client.gui.helper;

import java.util.Map;

public class TagColors {

    // Background + foreground pairs for predefined tags
    private static final Map<String, int[]> PREDEFINED = Map.of(
            "overworld", new int[]{0xFF5c4a2e, 0xFFe8c86d},
            "nether",    new int[]{0xFF5c3d2e, 0xFFe8a86d},
            "the_end",   new int[]{0xFF3d2e5c, 0xFFa86de8},
            "building",  new int[]{0xFF2e4a3d, 0xFF6de8a8},
            "redstone",  new int[]{0xFF2e3d5c, 0xFF6da8e8},
            "farm",      new int[]{0xFF2e5c4a, 0xFF6de8c8}
    );

    // Palette for custom tags (hash selects index)
    private static final int[][] CUSTOM_PALETTE = {
            {0xFF4a3d2e, 0xFFd8b87d}, {0xFF2e4a4a, 0xFF6dd8d8},
            {0xFF4a2e3d, 0xFFd86da8}, {0xFF3d4a2e, 0xFFa8d86d},
            {0xFF2e3d4a, 0xFF6da8d8}, {0xFF4a2e4a, 0xFFd86dd8},
            {0xFF3d2e4a, 0xFFa86dd8}, {0xFF4a4a2e, 0xFFd8d86d},
    };

    public static int getBackground(String tag) {
        int[] colors = PREDEFINED.get(tag);
        if (colors != null) return colors[0];
        int idx = (tag.hashCode() & 0x7FFFFFFF) % CUSTOM_PALETTE.length;
        return CUSTOM_PALETTE[idx][0];
    }

    public static int getForeground(String tag) {
        int[] colors = PREDEFINED.get(tag);
        if (colors != null) return colors[1];
        int idx = (tag.hashCode() & 0x7FFFFFFF) % CUSTOM_PALETTE.length;
        return CUSTOM_PALETTE[idx][1];
    }
}
