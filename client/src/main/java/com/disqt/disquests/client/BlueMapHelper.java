package com.disqt.disquests.client;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.BlueMapUrlBuilder;
import com.disqt.disquests.common.model.CoordinatesData;

import java.util.Map;

public class BlueMapHelper {

    public static String buildUrl(Quest quest) {
        String base = ClientSession.getBluemapUrl();
        if (base == null || base.isEmpty()) base = "https://disqt.com/minecraft/map";
        if (quest.getCoordinates() == null) return null;

        Map<String, String> mapNames = ClientSession.getBluemapMapNames();
        String map = quest.getMap();

        if (quest.isRegion() && quest.getCoordinates2() != null) {
            CoordinatesData c1 = quest.getCoordinates();
            CoordinatesData c2 = quest.getCoordinates2();
            return BlueMapUrlBuilder.buildUrlRegion(base,
                    c1.x(), c1.y(), c1.z(), c2.x(), c2.y(), c2.z(), map, mapNames);
        } else {
            CoordinatesData c = quest.getCoordinates();
            return BlueMapUrlBuilder.buildUrl(base, c.x(), c.y(), c.z(), map, mapNames);
        }
    }
}
