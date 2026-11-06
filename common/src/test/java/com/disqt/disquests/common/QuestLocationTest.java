package com.disqt.disquests.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestLocationTest {

  private static List<PointData> lShape() {
    return List.of(
        new PointData(0, 0),
        new PointData(10, 0),
        new PointData(10, 4),
        new PointData(4, 4),
        new PointData(4, 10),
        new PointData(0, 10));
  }

  private static QuestData quest(
      CoordinatesData c1, boolean region, CoordinatesData c2, String map, List<PointData> poly) {
    return new QuestData(
        UUID.randomUUID(),
        "t",
        "c",
        UUID.randomUUID(),
        "o",
        Visibility.PRIVATE,
        List.of(),
        0L,
        c1,
        region,
        c2,
        map,
        List.of(),
        poly);
  }

  @Test
  void rectangleToPolygon_ordersCornersClockwiseFromMin() {
    List<PointData> p =
        QuestLocation.rectangleToPolygon(
            new CoordinatesData(20, 70, 5), new CoordinatesData(10, 60, -5));
    assertEquals(
        List.of(
            new PointData(10, -5),
            new PointData(20, -5),
            new PointData(20, 5),
            new PointData(10, 5)),
        p);
  }

  @Test
  void boundingBox_ofTriangle() {
    CoordinatesData[] bb =
        QuestLocation.boundingBox(
            List.of(new PointData(0, 0), new PointData(10, 0), new PointData(5, 8)), 64);
    assertEquals(new CoordinatesData(0, 64, 0), bb[0]);
    assertEquals(new CoordinatesData(10, 64, 8), bb[1]);
  }

  @Test
  void contains_insideConcaveShape() {
    assertFalse(QuestLocation.contains(lShape(), 7, 7));
    assertTrue(QuestLocation.contains(lShape(), 2, 7));
    assertTrue(QuestLocation.contains(lShape(), 7, 2));
  }

  @Test
  void contains_outside() {
    assertFalse(QuestLocation.contains(lShape(), -1, -1));
    assertFalse(QuestLocation.contains(lShape(), 20, 2));
  }

  @Test
  void area_square100() {
    assertEquals(
        100.0,
        QuestLocation.area(
            List.of(
                new PointData(0, 0),
                new PointData(10, 0),
                new PointData(10, 10),
                new PointData(0, 10))),
        1e-9);
  }

  @Test
  void isSimple_bowtieIsNot() {
    assertFalse(
        QuestLocation.isSimple(
            List.of(
                new PointData(0, 0),
                new PointData(10, 10),
                new PointData(10, 0),
                new PointData(0, 10))));
  }

  @Test
  void isSimple_concaveIs() {
    assertTrue(QuestLocation.isSimple(lShape()));
  }

  @Test
  void isValid_rejects65Vertices_andNaN() {
    List<PointData> many = new ArrayList<>();
    for (int i = 0; i < 65; i++) many.add(new PointData(i, i * i));
    assertFalse(QuestLocation.isValid(many));
    assertTrue(QuestLocation.isValid(many.subList(0, 64)));
    assertFalse(QuestLocation.isValid(List.of(new PointData(Double.NaN, 0))));
    assertFalse(QuestLocation.isValid(List.of(new PointData(0, Double.POSITIVE_INFINITY))));
  }

  @Test
  void normalize_singleVertexBecomesPin_keepsExistingY() {
    QuestData q =
        QuestLocation.normalize(
            quest(
                new CoordinatesData(1, 70, 2),
                true,
                new CoordinatesData(3, 70, 4),
                "overworld",
                List.of(new PointData(5, 6))));
    assertEquals(new CoordinatesData(5, 70, 6), q.coordinates());
    assertTrue(q.polygon().isEmpty());
    assertFalse(q.isRegion());
    assertNull(q.coordinates2());
  }

  @Test
  void normalize_polygonSetsBoundingBoxLegacyFields() {
    QuestData q =
        QuestLocation.normalize(
            quest(
                null,
                false,
                null,
                "overworld",
                List.of(new PointData(0, 0), new PointData(10, 0), new PointData(5, 8))));
    assertTrue(q.isRegion());
    assertEquals(new CoordinatesData(0, 64, 0), q.coordinates());
    assertEquals(new CoordinatesData(10, 64, 8), q.coordinates2());
    assertEquals(3, q.polygon().size());
  }

  @Test
  void normalize_legacyRectangleGainsPolygon() {
    CoordinatesData c1 = new CoordinatesData(0, 65, 0);
    CoordinatesData c2 = new CoordinatesData(10, 65, 10);
    QuestData q = QuestLocation.normalize(quest(c1, true, c2, "overworld", List.of()));
    assertEquals(4, q.polygon().size());
    assertEquals(c1, q.coordinates());
    assertEquals(c2, q.coordinates2());
    assertTrue(q.isRegion());
  }

  @Test
  void normalize_pointWithoutRegionDropsCorner2() {
    QuestData q =
        QuestLocation.normalize(
            quest(
                new CoordinatesData(1, 2, 3),
                false,
                new CoordinatesData(4, 5, 6),
                "overworld",
                List.of()));
    assertNull(q.coordinates2());
    assertEquals(new CoordinatesData(1, 2, 3), q.coordinates());
    assertTrue(q.polygon().isEmpty());
  }

  @Test
  void normalize_mapKeyNormalised() {
    QuestData q = QuestLocation.normalize(quest(null, false, null, "nether", List.of()));
    assertEquals("the_nether", q.map());
  }
}
