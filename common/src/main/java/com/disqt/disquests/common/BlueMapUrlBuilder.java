package com.disqt.disquests.common;

import java.util.Locale;
import java.util.Map;

public class BlueMapUrlBuilder {

  /**
   * Builds a BlueMap URL that zooms to the given coordinates.
   *
   * <p>The base URL must end with a trailing slash so browsers don't trigger a 301 redirect (e.g.
   * {@code /map} -> {@code /map/}) which would strip the {@code #fragment} and land on BlueMap's
   * default view.
   *
   * <p>BlueMap hash format (10 colon-separated values): {@code
   * #mapId:x:y:z:distance:rotation:angle:tilt:ortho:controlState}
   */
  public static String buildUrl(
      String base,
      double x,
      double y,
      double z,
      String map,
      Map<String, String> mapNames,
      String defaultMap) {
    if (base == null || base.isEmpty()) return null;
    if (!base.startsWith("http://") && !base.startsWith("https://")) return null;

    if (!base.endsWith("/")) {
      base = base + "/";
    }

    String effectiveMap = map != null ? map : defaultMap;
    String mapId = mapNames.getOrDefault(effectiveMap, effectiveMap);
    return String.format(
        Locale.ROOT, "%s#%s:%.0f:%.0f:%.0f:300:0:0:0:0:perspective", base, mapId, x, y, z);
  }

  public static String buildUrlRegion(
      String base,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      String map,
      Map<String, String> mapNames,
      String defaultMap) {
    double cx = (x1 + x2) / 2;
    double cy = (y1 + y2) / 2;
    double cz = (z1 + z2) / 2;
    return buildUrl(base, cx, cy, cz, map, mapNames, defaultMap);
  }
}
