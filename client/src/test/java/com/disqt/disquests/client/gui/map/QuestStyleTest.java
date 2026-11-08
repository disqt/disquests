package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Visibility;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestStyleTest {
  private static final int RGB = 0x00FFFFFF;

  private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void baseColor_stablePerIdAndDistinctAcrossIds() {
    assertEquals(QuestStyle.baseColor(A), QuestStyle.baseColor(UUID.fromString(A.toString())));
    assertNotEquals(QuestStyle.baseColor(A) & RGB, QuestStyle.baseColor(B) & RGB);
    assertEquals(0xFF, QuestStyle.baseColor(A) >>> 24);
  }

  @Test
  void palette_spansTwelveColours() {
    Set<Integer> seen = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      seen.add(QuestStyle.baseColor(UUID.nameUUIDFromBytes(new byte[] {(byte) i, 42})));
    }
    assertEquals(12, seen.size());
  }

  @Test
  void fillAndOutline_shareTheQuestHue() {
    assertEquals(QuestStyle.baseColor(A) & RGB, QuestStyle.fillColor(A, false) & RGB);
    assertEquals(QuestStyle.baseColor(A), QuestStyle.outlineColor(A));
    assertEquals(0x40, QuestStyle.fillColor(A, false) >>> 24);
    assertEquals(
        QuestStyle.fillColor(A, false), QuestStyle.fillColor(A, Visibility.PRIVATE, false));
  }

  @Test
  void fillColor_hoverRaisesAlpha() {
    assertEquals(0x73, QuestStyle.fillColor(A, true) >>> 24);
    assertEquals(0x73, QuestStyle.fillColor(A, Visibility.CLOSED, true) >>> 24);
  }

  @Test
  void closed_isDesaturated() {
    for (UUID id : List.of(A, B)) {
      int open = QuestStyle.outlineColor(id, Visibility.OPEN);
      int closed = QuestStyle.outlineColor(id, Visibility.CLOSED);
      assertTrue(saturation(closed) < saturation(open));
      assertEquals(0xFF, closed >>> 24);
    }
  }

  @Test
  void palette_avoidsPlayerBlueAndAmber() {
    for (int i = 0; i < 200; i++) {
      int c = QuestStyle.baseColor(UUID.nameUUIDFromBytes(new byte[] {(byte) i, 7}));
      assertTrue(hueDistance(hue(c), 210) >= 10, "near player blue: " + Integer.toHexString(c));
      assertTrue(hueDistance(hue(c), 35) >= 5, "near amber: " + Integer.toHexString(c));
    }
  }

  private static double hueDistance(double a, double b) {
    double d = Math.abs(a - b) % 360;
    return Math.min(d, 360 - d);
  }

  private static double hue(int rgb) {
    double r = ((rgb >> 16) & 0xFF) / 255.0;
    double g = ((rgb >> 8) & 0xFF) / 255.0;
    double b = (rgb & 0xFF) / 255.0;
    double max = Math.max(r, Math.max(g, b));
    double d = max - Math.min(r, Math.min(g, b));
    if (d == 0) {
      return 0;
    }
    double h;
    if (max == r) {
      h = ((g - b) / d) % 6;
    } else if (max == g) {
      h = (b - r) / d + 2;
    } else {
      h = (r - g) / d + 4;
    }
    return (h * 60 + 360) % 360;
  }

  private static double saturation(int rgb) {
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;
    int max = Math.max(r, Math.max(g, b));
    int min = Math.min(r, Math.min(g, b));
    return max == 0 ? 0 : (max - min) / (double) max;
  }

  @Test
  void pickSmallest_choosesSmallestArea() {
    Quest big = polygon(0, 0, 100, 100);
    Quest small = polygon(10, 10, 20, 20);
    assertSame(small, QuestStyle.pickSmallest(List.of(big, small)));
    assertSame(small, QuestStyle.pickSmallest(List.of(small, big)));
  }

  @Test
  void pickSmallest_pinBeatsPolygon() {
    Quest poly = polygon(0, 0, 10, 10);
    Quest pin = new Quest();
    pin.setCoordinates(new CoordinatesData(5, 64, 5));
    assertSame(pin, QuestStyle.pickSmallest(List.of(poly, pin)));
  }

  @Test
  void pickSmallest_emptyIsNull() {
    assertNull(QuestStyle.pickSmallest(List.of()));
  }

  private static Quest polygon(double x0, double z0, double x1, double z1) {
    Quest q = new Quest();
    q.setPolygon(
        List.of(
            new PointData(x0, z0),
            new PointData(x1, z0),
            new PointData(x1, z1),
            new PointData(x0, z1)));
    return q;
  }
}
