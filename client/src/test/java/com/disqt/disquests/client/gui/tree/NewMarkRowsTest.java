package com.disqt.disquests.client.gui.tree;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.graph;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.gui.tree.QuestTreeRows.Row;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.Tab;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NewMarkRowsTest {

  /** Leo's hub with Max's Wagons inside, Max's Roues inside that; Leo's Gare beside them. */
  private static final QuestGraph G =
      graph(
          quest("hub").modified(10),
          quest("Wagons").owner(MAX).in("hub").modified(20),
          quest("Roues").owner(MAX).in("Wagons").modified(5),
          quest("Gare").modified(40));

  private static List<Row> fullyExpanded(Tab tab) {
    Set<UUID> all = new HashSet<>();
    for (QuestData q : G.all()) all.add(q.id());
    return QuestTreeRows.forTab(G, LEO, tab, "", all);
  }

  private static Set<UUID> titled(String spaceSeparated) {
    if (spaceSeparated == null) return Set.of();
    return Set.copyOf(TreeFixtures.ids(spaceSeparated.split(" ")));
  }

  @ParameterizedTest(name = "{0}: new {1} marks {2}")
  @CsvSource({
    "MY_QUESTS, Roues, Roues Wagons hub",
    "MY_QUESTS, Wagons, Wagons hub",
    "MY_QUESTS, Gare, Gare",
    "MY_QUESTS, Roues Gare, Roues Wagons hub Gare",
    "MY_QUESTS, , ",
    // hub is not on the board: the walk stops at the top of the board's rows
    "QUEST_BOARD, Roues, Roues Wagons",
    // Gare is not on the board: a quest that is not listed marks nothing
    "QUEST_BOARD, Gare, ",
  })
  void aRowShowsNewWhenItOrAnyQuestListedBeneathItIsNew(
      Tab tab, String newTitles, String expected) {
    Set<UUID> isNew = titled(newTitles);
    assertEquals(titled(expected), NewMarkRows.showingNew(G, fullyExpanded(tab), isNew::contains));
  }
}
