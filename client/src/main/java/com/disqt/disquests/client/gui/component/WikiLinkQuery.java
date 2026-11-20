package com.disqt.disquests.client.gui.component;

/** Finds the wiki-link being typed at the cursor. Minecraft-free, so it can be unit-tested. */
public final class WikiLinkQuery {

  private WikiLinkQuery() {}

  /**
   * The text typed after the last {@code [[} before {@code cursor} when no {@code ]]} closes it
   * before the cursor, or null when the cursor is not inside a wiki-link being written.
   */
  public static String at(String text, int cursor) {
    if (text == null || cursor < 2 || cursor > text.length()) return null;
    // A "[[" straddling the cursor is not one being written: no link is open there.
    int open = text.lastIndexOf("[[", cursor - 1);
    if (open < 0 || open + 2 > cursor) return null;
    String afterOpen = text.substring(open + 2, cursor);
    return afterOpen.contains("]]") ? null : afterOpen;
  }
}
