package com.disqt.disquests.client.gui.helper;

/**
 * Deterministic tag colour palette using HSL golden-angle spacing. Same tag name always produces
 * the same colour everywhere (edit chips, view chips, quest list entries, picker).
 */
public class TagColors {

  /** Number of distinct hue slots. Chosen to give good visual separation. */
  private static final int PALETTE_SIZE = 16;

  /** Golden angle in degrees, producing maximally-spaced hues. */
  private static final double GOLDEN_ANGLE = 137.508;

  /** Pre-computed ARGB background/foreground pairs, indexed by hue slot. */
  private static final int[] BACKGROUNDS = new int[PALETTE_SIZE];

  private static final int[] FOREGROUNDS = new int[PALETTE_SIZE];

  static {
    for (int i = 0; i < PALETTE_SIZE; i++) {
      double hue = (i * GOLDEN_ANGLE) % 360.0;
      BACKGROUNDS[i] = hslToArgb(hue, 0.35, 0.22, 0xFF);
      FOREGROUNDS[i] = hslToArgb(hue, 0.65, 0.78, 0xFF);
    }
  }

  /** Returns the background ARGB colour for a tag. */
  public static int getBackground(String tag) {
    return BACKGROUNDS[slot(tag)];
  }

  /** Returns the foreground (text) ARGB colour for a tag. */
  public static int getForeground(String tag) {
    return FOREGROUNDS[slot(tag)];
  }

  /** Deterministic slot from tag name hash. */
  private static int slot(String tag) {
    return (tag.hashCode() & 0x7FFFFFFF) % PALETTE_SIZE;
  }

  /** Convert HSL + alpha to ARGB int. Hue in [0, 360), s/l in [0, 1]. */
  private static int hslToArgb(double h, double s, double l, int a) {
    double c = (1.0 - Math.abs(2.0 * l - 1.0)) * s;
    double hp = h / 60.0;
    double x = c * (1.0 - Math.abs(hp % 2.0 - 1.0));
    double r1, g1, b1;
    if (hp < 1) {
      r1 = c;
      g1 = x;
      b1 = 0;
    } else if (hp < 2) {
      r1 = x;
      g1 = c;
      b1 = 0;
    } else if (hp < 3) {
      r1 = 0;
      g1 = c;
      b1 = x;
    } else if (hp < 4) {
      r1 = 0;
      g1 = x;
      b1 = c;
    } else if (hp < 5) {
      r1 = x;
      g1 = 0;
      b1 = c;
    } else {
      r1 = c;
      g1 = 0;
      b1 = x;
    }
    double m = l - c / 2.0;
    int r = (int) Math.round((r1 + m) * 255);
    int g = (int) Math.round((g1 + m) * 255);
    int b = (int) Math.round((b1 + m) * 255);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }
}
