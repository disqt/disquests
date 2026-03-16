package com.disqt.disquests.client;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.model.CoordinatesData;

public class BlueMapHelper {

    public static String buildUrl(Quest quest) {
        if (!ClientSession.hasBluemap() || quest.getCoordinates() == null) return null;
        String base = ClientSession.getBluemapUrl();
        String map = quest.getMap() != null ? quest.getMap() : "world";
        double x, y, z;
        if (quest.isRegion() && quest.getCoordinates2() != null) {
            CoordinatesData c1 = quest.getCoordinates();
            CoordinatesData c2 = quest.getCoordinates2();
            x = (c1.x() + c2.x()) / 2;
            y = (c1.y() + c2.y()) / 2;
            z = (c1.z() + c2.z()) / 2;
        } else {
            CoordinatesData c = quest.getCoordinates();
            x = c.x();
            y = c.y();
            z = c.z();
        }
        return String.format("%s/#%s:%.0f:%.0f:%.0f:50:0:0:0:0:flat", base, map, x, y, z);
    }
}
