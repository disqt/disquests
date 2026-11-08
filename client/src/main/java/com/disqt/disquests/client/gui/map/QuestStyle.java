package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.TagColors;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.UUID;

/**
 * Pure colour and hit-priority rules for quests drawn on the map. Each quest gets a stable hue from
 * its id; closed quests show that hue desaturated. No Minecraft imports.
 */
public final class QuestStyle {
  private static final int FILL_ALPHA = 0x40;
  private static final int HOVER_ALPHA = 0x73;
  private static final int PALETTE_SIZE = 12;

  /** Twelve hues 30 degrees apart, ordered so neighbouring slots are far apart on the wheel. */
  private static final int[] PALETTE = new int[PALETTE_SIZE];

  static {
    for (int i = 0; i < PALETTE_SIZE; i++) {
      double hue = (i * 5 % PALETTE_SIZE) * (360.0 / PALETTE_SIZE);
      PALETTE[i] = TagColors.hslToArgb(hue, 0.80, 0.58, 0xFF) & 0x00FFFFFF;
    }
  }

  private QuestStyle() {}

  /** Opaque palette colour for a quest id; a null id gets the first slot. */
  public static int baseColor(UUID questId) {
    int slot = questId == null ? 0 : slot(questId);
    return 0xFF000000 | PALETTE[slot];
  }

  /** Palette slot from a mixed 64-bit hash, so ids that differ in a few bits still spread. */
  private static int slot(UUID id) {
    long h = (id.getMostSignificantBits() ^ id.getLeastSignificantBits()) * 0x9E3779B97F4A7C15L;
    return (int) Long.remainderUnsigned(h >>> 16, PALETTE_SIZE);
  }

  /** {@link #baseColor(UUID)}, blended halfway to grey for closed quests. */
  public static int baseColor(UUID questId, Visibility v) {
    int base = baseColor(questId);
    return v == Visibility.CLOSED ? desaturate(base) : base;
  }

  /** Translucent fill in the quest's colour. */
  public static int fillColor(UUID questId, boolean hovered) {
    return fillColor(questId, Visibility.OPEN, hovered);
  }

  public static int fillColor(UUID questId, Visibility v, boolean hovered) {
    int alpha = hovered ? HOVER_ALPHA : FILL_ALPHA;
    return (alpha << 24) | (baseColor(questId, v) & 0x00FFFFFF);
  }

  /** Opaque outline colour, same hue as {@link #fillColor}. */
  public static int outlineColor(UUID questId) {
    return baseColor(questId);
  }

  public static int outlineColor(UUID questId, Visibility v) {
    return baseColor(questId, v);
  }

  /** Each channel moved halfway towards the colour's grey level; alpha kept. */
  static int desaturate(int argb) {
    int r = (argb >> 16) & 0xFF;
    int g = (argb >> 8) & 0xFF;
    int b = argb & 0xFF;
    int grey = (r + g + b) / 3;
    r = (r + grey) / 2;
    g = (g + grey) / 2;
    b = (b + grey) / 2;
    return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
  }

  /** The hit with the smallest area, pins counting as 0; null when there are no hits. */
  public static Quest pickSmallest(List<Quest> hits) {
    Quest best = null;
    double bestArea = Double.POSITIVE_INFINITY;
    for (Quest q : hits) {
      double area = q.isPolygon() ? QuestLocation.area(q.getPolygon()) : 0;
      if (area < bestArea) {
        best = q;
        bestArea = area;
      }
    }
    return best;
  }
}
