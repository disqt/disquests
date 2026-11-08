package com.disqt.disquests.server.papermc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {
  @Test
  void normalizeMapNames_rewritesLegacyKeys() {
    var out =
        Config.normalizeMapNames(
            Map.of(
                "overworld", "world_new",
                "nether", "world_new_nether",
                "end", "world_new_the_end"));
    assertEquals("world_new", out.get("overworld"));
    assertEquals("world_new_nether", out.get("the_nether"));
    assertEquals("world_new_the_end", out.get("the_end"));
    assertFalse(out.containsKey("nether"));
  }
}
