package com.disqt.disquests.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DimensionKeysTest {
  @Test
  void nether_becomes_the_nether() {
    assertEquals("the_nether", DimensionKeys.normalize("nether"));
  }

  @Test
  void end_becomes_the_end() {
    assertEquals("the_end", DimensionKeys.normalize("end"));
  }

  @Test
  void canonical_unchanged() {
    assertEquals("the_nether", DimensionKeys.normalize("the_nether"));
  }

  @Test
  void namespace_stripped() {
    assertEquals("overworld", DimensionKeys.normalize("minecraft:overworld"));
  }

  @Test
  void null_stays_null() {
    assertNull(DimensionKeys.normalize(null));
  }

  @Test
  void unknown_passes_through_lowercased_trimmed() {
    assertEquals("custom_dim", DimensionKeys.normalize(" Custom_Dim "));
  }
}
