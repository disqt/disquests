package com.disqt.disquests.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class BlueMapUrlBuilder {

    public static String buildUrl(String base, double x, double y, double z,
            String map, Map<String, String> mapNames) {
        if (base == null || base.isEmpty()) return null;
        if (!base.startsWith("http://") && !base.startsWith("https://")) return null;

        String mapId = map != null ? mapNames.getOrDefault(map, map) : "world";
        String encoded = URLEncoder.encode(mapId, StandardCharsets.UTF_8);
        return String.format("%s/#%s:%.0f:%.0f:%.0f:300:0:0:0:0:perspective",
                base, encoded, x, y, z);
    }

    public static String buildUrlRegion(String base,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            String map, Map<String, String> mapNames) {
        double cx = (x1 + x2) / 2;
        double cy = (y1 + y2) / 2;
        double cz = (z1 + z2) / 2;
        return buildUrl(base, cx, cy, cz, map, mapNames);
    }
}
