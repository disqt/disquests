package com.disqt.disquests.common;

/**
 * Quest text uses {@code \n} line ends only. Text pasted from a Windows clipboard arrives with
 * {@code \r\n}; Minecraft's font has no glyph for {@code \r} and draws it as a boxed "CR".
 */
public final class LineEndings {

  private LineEndings() {}

  /** {@code \r\n} and a lone {@code \r} become {@code \n}; null stays null. */
  public static String normalize(String text) {
    if (text == null || text.indexOf('\r') < 0) return text;
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }
}
