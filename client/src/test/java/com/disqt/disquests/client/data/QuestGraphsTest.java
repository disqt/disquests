package com.disqt.disquests.client.data;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestGraphsTest {

  private static final UUID OWNER = UUID.randomUUID();

  private static Quest quest(UUID id, UUID parent) {
    return Quest.fromNetwork(
        new QuestData(
                id,
                "T",
                "C",
                OWNER,
                "O",
                Visibility.OPEN,
                List.of(),
                0L,
                null,
                false,
                null,
                null,
                List.of())
            .withParent(parent));
  }

  @Test
  void buildsOneGraphOverBothListsWithoutDuplicates() {
    UUID root = UUID.randomUUID();
    UUID child = UUID.randomUUID();
    Quest rootQuest = quest(root, null);
    Quest childQuest = quest(child, root);
    QuestGraph graph =
        QuestGraphs.build(List.of(rootQuest, childQuest), List.of(childQuest, rootQuest));
    assertEquals(2, graph.all().size());
    assertEquals(List.of(child), graph.children(root).stream().map(QuestData::id).toList());
  }

  @Test
  void questInMyListWinsOverAStaleServerCopy() {
    UUID id = UUID.randomUUID();
    Quest mine = quest(id, null);
    mine.setTitle("mine");
    Quest stale = quest(id, null);
    stale.setTitle("stale");
    QuestGraph graph = QuestGraphs.build(List.of(mine), List.of(stale));
    assertEquals("mine", graph.get(id).title());
  }
}
