package com.disqt.disquests.client.gui.component;

import java.util.function.ToIntFunction;

/**
 * Fits a title into a width while always keeping what follows it (a "(reason)" or a count): only
 * the title is shortened, with an ellipsis. Minecraft-free: the caller passes the text width
 * function (the font's in game).
 */
public final class TextFit {

  public static final String ELLIPSIS = "…";

  private TextFit() {}

  /**
   * The longest form of {@code title} that fits in {@code available} px with {@code " " + suffix}
   * after it (nothing after it when {@code suffix} is empty): the whole title, a prefix followed by
   * {@link #ELLIPSIS}, or "" when not even the ellipsis fits.
   */
  public static String title(
      String title, String suffix, int available, ToIntFunction<String> width) {
    int room = available - (suffix.isEmpty() ? 0 : width.applyAsInt(" " + suffix));
    if (width.applyAsInt(title) <= room) return title;
    for (int len = title.length() - 1; len > 0; len--) {
      if (Character.isHighSurrogate(title.charAt(len - 1))) continue;
      String candidate = title.substring(0, len).stripTrailing() + ELLIPSIS;
      if (width.applyAsInt(candidate) <= room) return candidate;
    }
    return "";
  }
}
