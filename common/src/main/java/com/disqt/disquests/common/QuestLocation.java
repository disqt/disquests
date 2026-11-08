package com.disqt.disquests.common;

import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.QuestData;
import java.util.List;

/** Geometry helpers and canonical normalisation for quest locations. */
public final class QuestLocation {
  public static final int MAX_VERTICES = 64;
  private static final double DEFAULT_Y = 64;

  private QuestLocation() {}

  public static List<PointData> rectangleToPolygon(CoordinatesData c1, CoordinatesData c2) {
    double minX = Math.min(c1.x(), c2.x());
    double maxX = Math.max(c1.x(), c2.x());
    double minZ = Math.min(c1.z(), c2.z());
    double maxZ = Math.max(c1.z(), c2.z());
    return List.of(
        new PointData(minX, minZ),
        new PointData(maxX, minZ),
        new PointData(maxX, maxZ),
        new PointData(minX, maxZ));
  }

  /** Returns {min, max}, both carrying the given y. */
  public static CoordinatesData[] boundingBox(List<PointData> polygon, double y) {
    double minX = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;
    for (PointData p : polygon) {
      minX = Math.min(minX, p.x());
      maxX = Math.max(maxX, p.x());
      minZ = Math.min(minZ, p.z());
      maxZ = Math.max(maxZ, p.z());
    }
    return new CoordinatesData[] {
      new CoordinatesData(minX, y, minZ), new CoordinatesData(maxX, y, maxZ)
    };
  }

  /** Even-odd point-in-polygon test. */
  public static boolean contains(List<PointData> polygon, double x, double z) {
    boolean inside = false;
    int n = polygon.size();
    for (int i = 0, j = n - 1; i < n; j = i++) {
      PointData a = polygon.get(i);
      PointData b = polygon.get(j);
      if ((a.z() > z) != (b.z() > z)
          && x < (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x()) {
        inside = !inside;
      }
    }
    return inside;
  }

  /** Absolute polygon area (shoelace formula). */
  public static double area(List<PointData> polygon) {
    double sum = 0;
    int n = polygon.size();
    for (int i = 0, j = n - 1; i < n; j = i++) {
      PointData a = polygon.get(j);
      PointData b = polygon.get(i);
      sum += a.x() * b.z() - b.x() * a.z();
    }
    return Math.abs(sum) / 2;
  }

  /** True if no two non-adjacent edges intersect. Fewer than 3 vertices is simple. */
  public static boolean isSimple(List<PointData> polygon) {
    int n = polygon.size();
    if (n < 3) return true;
    for (int i = 0; i < n; i++) {
      PointData a1 = polygon.get(i);
      PointData a2 = polygon.get((i + 1) % n);
      for (int j = i + 1; j < n; j++) {
        // Skip edges sharing a vertex, including the closing edge against the first.
        if (j == i + 1 || (i == 0 && j == n - 1)) continue;
        PointData b1 = polygon.get(j);
        PointData b2 = polygon.get((j + 1) % n);
        if (segmentsIntersect(a1, a2, b1, b2)) return false;
      }
    }
    return true;
  }

  public static boolean isValid(List<PointData> polygon) {
    if (polygon.size() > MAX_VERTICES) return false;
    for (PointData p : polygon) {
      if (!Double.isFinite(p.x()) || !Double.isFinite(p.z())) return false;
    }
    return true;
  }

  /** Applies the canonical location rules; see the world-map design. */
  public static QuestData normalize(QuestData q) {
    String map = DimensionKeys.normalize(q.map());
    QuestData base =
        new QuestData(
            q.id(),
            q.title(),
            q.content(),
            q.ownerUuid(),
            q.ownerName(),
            q.visibility(),
            q.contributors(),
            q.lastModified(),
            q.coordinates(),
            q.isRegion(),
            q.coordinates2(),
            map,
            q.tags(),
            q.polygon());
    double y = q.coordinates() != null ? q.coordinates().y() : DEFAULT_Y;
    List<PointData> poly = q.polygon();
    if (poly.size() == 1) {
      PointData p = poly.get(0);
      return base.withLocation(new CoordinatesData(p.x(), y, p.z()), false, null, List.of());
    }
    if (poly.size() >= 2) {
      CoordinatesData[] bb = boundingBox(poly, y);
      return base.withLocation(bb[0], true, bb[1], poly);
    }
    if (q.isRegion() && q.coordinates() != null && q.coordinates2() != null) {
      return base.withPolygon(rectangleToPolygon(q.coordinates(), q.coordinates2()));
    }
    if (!q.isRegion()) {
      return base.withLocation(q.coordinates(), false, null, poly);
    }
    return base;
  }

  private static boolean segmentsIntersect(PointData p1, PointData p2, PointData p3, PointData p4) {
    double d1 = orientation(p3, p4, p1);
    double d2 = orientation(p3, p4, p2);
    double d3 = orientation(p1, p2, p3);
    double d4 = orientation(p1, p2, p4);
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
      return true;
    }
    return (d1 == 0 && onSegment(p3, p4, p1))
        || (d2 == 0 && onSegment(p3, p4, p2))
        || (d3 == 0 && onSegment(p1, p2, p3))
        || (d4 == 0 && onSegment(p1, p2, p4));
  }

  private static double orientation(PointData a, PointData b, PointData c) {
    return (b.x() - a.x()) * (c.z() - a.z()) - (b.z() - a.z()) * (c.x() - a.x());
  }

  /** Assumes c is collinear with a-b. */
  private static boolean onSegment(PointData a, PointData b, PointData c) {
    return Math.min(a.x(), b.x()) <= c.x()
        && c.x() <= Math.max(a.x(), b.x())
        && Math.min(a.z(), b.z()) <= c.z()
        && c.z() <= Math.max(a.z(), b.z());
  }
}
