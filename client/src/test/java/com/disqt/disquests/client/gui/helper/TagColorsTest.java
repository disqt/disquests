package com.disqt.disquests.client.gui.helper;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TagColorsTest {

  @Test
  void sameTag_alwaysReturnsSameColor() {
    int bg1 = TagColors.getBackground("redstone");
    int bg2 = TagColors.getBackground("redstone");
    assertEquals(bg1, bg2);

    int fg1 = TagColors.getForeground("redstone");
    int fg2 = TagColors.getForeground("redstone");
    assertEquals(fg1, fg2);
  }

  @Test
  void differentTags_usuallyReturnDifferentColors() {
    // Not guaranteed for all pairs (hash collisions), but these specific
    // tags should map to different slots
    int bg1 = TagColors.getBackground("building");
    int bg2 = TagColors.getBackground("nether");
    assertNotEquals(bg1, bg2, "Expected different colors for 'building' and 'nether'");
  }

  @Test
  void colors_haveFullAlpha() {
    int bg = TagColors.getBackground("overworld");
    int fg = TagColors.getForeground("overworld");
    assertEquals(0xFF, (bg >> 24) & 0xFF, "Background should have full alpha");
    assertEquals(0xFF, (fg >> 24) & 0xFF, "Foreground should have full alpha");
  }

  @Test
  void background_isDarkerThanForeground() {
    // Background has lightness 0.22, foreground 0.78
    // So foreground luminance should always be higher
    String tag = "farm";
    int bg = TagColors.getBackground(tag);
    int fg = TagColors.getForeground(tag);

    int bgLuminance = ((bg >> 16) & 0xFF) + ((bg >> 8) & 0xFF) + (bg & 0xFF);
    int fgLuminance = ((fg >> 16) & 0xFF) + ((fg >> 8) & 0xFF) + (fg & 0xFF);
    assertTrue(fgLuminance > bgLuminance, "Foreground should be lighter than background");
  }

  @Test
  void emptyTag_doesNotThrow() {
    assertDoesNotThrow(() -> TagColors.getBackground(""));
    assertDoesNotThrow(() -> TagColors.getForeground(""));
  }
}
