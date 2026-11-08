package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestStyleTest {
  private static final int RGB = 0x00FFFFFF;

  @Test
  void fillColor_openAmber_closedGrey_privateAmber() {
    assertEquals(Colors.MAP_OPEN & RGB, QuestStyle.fillColor(Visibility.OPEN, false) & RGB);
    assertEquals(Colors.MAP_CLOSED & RGB, QuestStyle.fillColor(Visibility.CLOSED, false) & RGB);
    assertEquals(Colors.MAP_OPEN & RGB, QuestStyle.fillColor(Visibility.PRIVATE, false) & RGB);
    assertEquals(0x40, QuestStyle.fillColor(Visibility.OPEN, false) >>> 24);
  }

  @Test
  void fillColor_hoverRaisesAlpha() {
    assertEquals(0x73, QuestStyle.fillColor(Visibility.OPEN, true) >>> 24);
    assertEquals(0x73, QuestStyle.fillColor(Visibility.CLOSED, true) >>> 24);
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
