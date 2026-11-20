package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewClickTest {

  @Test
  void aLeftPressAndReleaseInPlaceIsAClick() {
    PreviewClick c = new PreviewClick();
    c.press(10, 10, true);
    assertTrue(c.release(12, 9, true, true));
  }

  @Test
  void aDragIsNotAClick() {
    PreviewClick c = new PreviewClick();
    c.press(10, 10, true);
    assertFalse(c.release(14, 10, true, true));
  }

  @Test
  void releasingOutsideOrWithAnotherButtonIsNotAClick() {
    PreviewClick c = new PreviewClick();
    c.press(10, 10, true);
    assertFalse(c.release(10, 10, true, false));
    c.press(10, 10, false);
    assertFalse(c.release(10, 10, false, true));
    c.press(10, 10, true);
    assertFalse(c.release(10, 10, false, true));
  }

  @Test
  void aReleaseWithoutAPressOrAfterCancelIsNotAClick() {
    PreviewClick c = new PreviewClick();
    assertFalse(c.release(10, 10, true, true));
    c.press(10, 10, true);
    c.cancel();
    assertFalse(c.release(10, 10, true, true));
  }
}
