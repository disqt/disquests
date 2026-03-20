package com.disqt.disquests.common;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BlueMapUrlBuilderTest {

    @Test
    void testBlueMapUrl_withMapping() {
        Map<String, String> mapNames = Map.of("overworld", "world_new", "nether", "world_new_nether");
        String url = BlueMapUrlBuilder.buildUrl("https://disqt.com/minecraft/map",
                100.0, 64.0, -200.0, "overworld", mapNames);
        assertEquals("https://disqt.com/minecraft/map/#world_new:100:64:-200:300:0:0:0:0:perspective", url);
    }

    @Test
    void testBlueMapUrl_unknownMapFallback() {
        Map<String, String> mapNames = Map.of("overworld", "world_new");
        String url = BlueMapUrlBuilder.buildUrl("https://example.com",
                0, 0, 0, "custom_world", mapNames);
        assertEquals("https://example.com/#custom_world:0:0:0:300:0:0:0:0:perspective", url);
    }

    @Test
    void testBlueMapUrl_regionCenter() {
        Map<String, String> mapNames = Map.of("overworld", "world_new");
        String url = BlueMapUrlBuilder.buildUrlRegion("https://example.com",
                -1688, 57, 168, -1688, 57, 296, "overworld", mapNames);
        assertEquals("https://example.com/#world_new:-1688:57:232:300:0:0:0:0:perspective", url);
    }

    @Test
    void testBlueMapUrl_emptyBaseReturnsNull() {
        assertNull(BlueMapUrlBuilder.buildUrl("", 0, 0, 0, "world", Map.of()));
    }

    @Test
    void testBlueMapUrl_nullBaseReturnsNull() {
        assertNull(BlueMapUrlBuilder.buildUrl(null, 0, 0, 0, "world", Map.of()));
    }

    @Test
    void testBlueMapUrl_nullMapDefaultsToWorld() {
        String url = BlueMapUrlBuilder.buildUrl("https://example.com",
                10, 20, 30, null, Map.of());
        assertEquals("https://example.com/#world:10:20:30:300:0:0:0:0:perspective", url);
    }
}
