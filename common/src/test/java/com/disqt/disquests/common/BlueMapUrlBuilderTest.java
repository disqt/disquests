package com.disqt.disquests.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BlueMapUrlBuilderTest {

  private static final String DEFAULT_MAP = "overworld";

  @Test
  void testBlueMapUrl_withMapping() {
    Map<String, String> mapNames = Map.of("overworld", "world_new", "nether", "world_new_nether");
    String url =
        BlueMapUrlBuilder.buildUrl(
            "https://disqt.com/minecraft/map",
            100.0,
            64.0,
            -200.0,
            "overworld",
            mapNames,
            DEFAULT_MAP);
    assertEquals(
        "https://disqt.com/minecraft/map/#world_new:100:64:-200:300:0:0:0:0:perspective", url);
  }

  @Test
  void testBlueMapUrl_unknownMapFallback() {
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    String url =
        BlueMapUrlBuilder.buildUrl(
            "https://example.com", 0, 0, 0, "custom_world", mapNames, DEFAULT_MAP);
    assertEquals("https://example.com/#custom_world:0:0:0:300:0:0:0:0:perspective", url);
  }

  @Test
  void testBlueMapUrl_regionCenter() {
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    String url =
        BlueMapUrlBuilder.buildUrlRegion(
            "https://example.com",
            -1688,
            57,
            168,
            -1688,
            57,
            296,
            "overworld",
            mapNames,
            DEFAULT_MAP);
    assertEquals("https://example.com/#world_new:-1688:57:232:300:0:0:0:0:perspective", url);
  }

  @Test
  void testBlueMapUrl_emptyBaseReturnsNull() {
    assertNull(BlueMapUrlBuilder.buildUrl("", 0, 0, 0, "world", Map.of(), DEFAULT_MAP));
  }

  @Test
  void testBlueMapUrl_nullBaseReturnsNull() {
    assertNull(BlueMapUrlBuilder.buildUrl(null, 0, 0, 0, "world", Map.of(), DEFAULT_MAP));
  }

  @Test
  void testBlueMapUrl_nullMapUsesDefaultMap() {
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    String url =
        BlueMapUrlBuilder.buildUrl("https://example.com", 10, 20, 30, null, mapNames, "overworld");
    assertEquals("https://example.com/#world_new:10:20:30:300:0:0:0:0:perspective", url);
  }

  @Test
  void testBlueMapUrl_nullMapNoMappingUsesDefaultMapRaw() {
    String url =
        BlueMapUrlBuilder.buildUrl("https://example.com", 10, 20, 30, null, Map.of(), "overworld");
    assertEquals("https://example.com/#overworld:10:20:30:300:0:0:0:0:perspective", url);
  }

  @Test
  void testBlueMapUrl_baseWithTrailingSlash() {
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    String url =
        BlueMapUrlBuilder.buildUrl(
            "https://disqt.com/minecraft/map/",
            100.0,
            64.0,
            -200.0,
            "overworld",
            mapNames,
            DEFAULT_MAP);
    assertEquals(
        "https://disqt.com/minecraft/map/#world_new:100:64:-200:300:0:0:0:0:perspective", url);
  }

  @Test
  void testBlueMapUrl_baseWithoutTrailingSlash_getsSlashAdded() {
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    String urlWithSlash =
        BlueMapUrlBuilder.buildUrl(
            "https://disqt.com/minecraft/map/",
            50.0,
            70.0,
            -300.0,
            "overworld",
            mapNames,
            DEFAULT_MAP);
    String urlWithoutSlash =
        BlueMapUrlBuilder.buildUrl(
            "https://disqt.com/minecraft/map",
            50.0,
            70.0,
            -300.0,
            "overworld",
            mapNames,
            DEFAULT_MAP);
    assertEquals(urlWithSlash, urlWithoutSlash);
  }

  @Test
  void testBlueMapUrl_negativeCoordinates() {
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    String url =
        BlueMapUrlBuilder.buildUrl(
            "https://example.com", -1234.0, -56.0, -789.0, "overworld", mapNames, DEFAULT_MAP);
    assertEquals("https://example.com/#world_new:-1234:-56:-789:300:0:0:0:0:perspective", url);
  }

  @Test
  void testBlueMapUrl_largeCoordinates() {
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    String url =
        BlueMapUrlBuilder.buildUrl(
            "https://example.com", 12345.0, 128.0, -67890.0, "overworld", mapNames, DEFAULT_MAP);
    assertEquals("https://example.com/#world_new:12345:128:-67890:300:0:0:0:0:perspective", url);
  }

  @Test
  void testBlueMapUrl_invalidSchemeReturnsNull() {
    assertNull(
        BlueMapUrlBuilder.buildUrl("ftp://example.com", 0, 0, 0, "world", Map.of(), DEFAULT_MAP));
  }

  @Test
  void testBlueMapUrl_hashHasExactly10Values() {
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    String url =
        BlueMapUrlBuilder.buildUrl(
            "https://example.com", 100.0, 64.0, -200.0, "overworld", mapNames, DEFAULT_MAP);
    assertNotNull(url);
    String hash = url.substring(url.indexOf('#') + 1);
    String[] values = hash.split(":");
    assertEquals(10, values.length, "BlueMap hash must have exactly 10 colon-separated values");
  }
}
