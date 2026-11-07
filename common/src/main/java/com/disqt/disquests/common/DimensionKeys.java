package com.disqt.disquests.common;

import java.util.Locale;

/** Canonical dimension keys, without namespace. */
public final class DimensionKeys {
  public static final String OVERWORLD = "overworld";
  public static final String THE_NETHER = "the_nether";
  public static final String THE_END = "the_end";

  private DimensionKeys() {}

  /** Lowercases, trims, strips a namespace and maps short names to canonical keys. */
  public static String normalize(String key) {
    if (key == null) return null;
    String k = key.trim().toLowerCase(Locale.ROOT);
    int colon = k.indexOf(':');
    if (colon >= 0) k = k.substring(colon + 1);
    return switch (k) {
      case "nether" -> THE_NETHER;
      case "end" -> THE_END;
      default -> k;
    };
  }
}
