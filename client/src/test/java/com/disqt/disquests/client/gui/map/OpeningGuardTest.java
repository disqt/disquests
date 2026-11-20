package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpeningGuardTest {

  @Test
  void aPressJustInsideTheWindowIsIgnored() {
    OpeningGuard g = new OpeningGuard(1000);
    assertFalse(g.acceptPress(1249));
  }

  @Test
  void aPressAtTheWindowsEndIsAccepted() {
    OpeningGuard g = new OpeningGuard(1000);
    assertTrue(g.acceptPress(1250));
    assertTrue(g.acceptRelease());
  }

  @Test
  void theReleaseOfAnIgnoredPressIsIgnoredToo() {
    OpeningGuard g = new OpeningGuard(1000);
    assertFalse(g.acceptPress(1100));
    assertFalse(g.acceptRelease());
  }

  @Test
  void laterGesturesPassAfterAnIgnoredOne() {
    OpeningGuard g = new OpeningGuard(1000);
    assertFalse(g.acceptPress(1100));
    assertFalse(g.acceptRelease());
    assertTrue(g.acceptPress(1300));
    assertTrue(g.acceptRelease());
  }
}
