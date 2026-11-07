package com.disqt.disquests.client.gui.screen;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestListOrderTest {

  private static Quest quest(String title) {
    Quest q = new Quest();
    q.setTitle(title);
    return q;
  }

  private static Quest pin(String title) {
    Quest q = quest(title);
    q.setCoordinates(new CoordinatesData(1, 64, 2));
    return q;
  }

  private static Quest polygon(String title) {
    Quest q = quest(title);
    q.setPolygon(List.of(new PointData(0, 0), new PointData(10, 0), new PointData(10, 10)));
    return q;
  }

  @Test
  void orderForList_unplacedFirst_stableWithinGroups() {
    Quest placedA = pin("placed A");
    Quest unplacedA = quest("unplaced A");
    Quest placedB = polygon("placed B");
    Quest unplacedB = quest("unplaced B");
    Quest placedC = pin("placed C");

    List<Quest> ordered =
        QuestListOrder.orderForList(List.of(placedA, unplacedA, placedB, unplacedB, placedC));

    assertEquals(List.of(unplacedA, unplacedB, placedA, placedB, placedC), ordered);
    assertEquals(List.of(), QuestListOrder.orderForList(List.of()));
  }

  @Test
  void shouldCollapseList_below640() {
    assertTrue(QuestListOrder.shouldCollapseList(0));
    assertTrue(QuestListOrder.shouldCollapseList(639));
    assertFalse(QuestListOrder.shouldCollapseList(640));
    assertFalse(QuestListOrder.shouldCollapseList(1920));
  }
}
