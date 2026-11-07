package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;

/** Pure colour and hit-priority rules for quests drawn on the map. No Minecraft imports. */
public final class QuestStyle {
  private static final int FILL_ALPHA = 0x40;
  private static final int HOVER_ALPHA = 0x73;

  private QuestStyle() {}

  /** Translucent fill: closed quests grey, open and private amber. */
  public static int fillColor(Visibility v, boolean hovered) {
    int alpha = hovered ? HOVER_ALPHA : FILL_ALPHA;
    return (alpha << 24) | (outlineColor(v) & 0x00FFFFFF);
  }

  /** Opaque outline colour, same hue as {@link #fillColor}. */
  public static int outlineColor(Visibility v) {
    int rgb = v == Visibility.CLOSED ? Colors.MAP_CLOSED : Colors.MAP_OPEN;
    return 0xFF000000 | rgb;
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
