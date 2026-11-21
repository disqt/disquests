package com.disqt.disquests.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class LineEndingsTest {

  @Test
  void windowsLineEndsBecomeNewlines() {
    assertEquals("a\nb\n\nc", LineEndings.normalize("a\r\nb\r\n\r\nc"));
  }

  @Test
  void aLoneCarriageReturnBecomesANewline() {
    assertEquals("a\nb", LineEndings.normalize("a\rb"));
  }

  @Test
  void textWithoutCarriageReturnsIsReturnedAsIs() {
    String text = "a\nb";
    assertSame(text, LineEndings.normalize(text));
  }

  @Test
  void nullStaysNull() {
    assertNull(LineEndings.normalize(null));
  }
}
