package com.disqt.disquests.client.gui.map;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.graph;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.ids;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.gui.tree.QuestTreeRows.BoardFilter;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.ListOptions;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.Tab;
import com.disqt.disquests.common.graph.QuestGraph;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MapQuestFilterTest {

  /**
   * Leo's hub with two of Max's subquests, one with a sub-subquest; Leo's Gare with a subquest Leo
   * reads and a subquest locked for him; Max's top-level quests on the board, one of them locked.
   */
  private static final QuestGraph WORLD =
      graph(
          quest("hub").modified(10),
          quest("Intérieurs").owner(MAX).in("hub").modified(30),
          quest("Wagons").owner(MAX).in("hub").modified(20),
          quest("Roues").owner(MAX).in("Wagons").modified(5),
          quest("Gare").modified(40),
          quest("Quai").owner(MAX).reader(LEO).in("Gare").modified(3),
          quest("Cave").owner(MAX).in("Gare").locked(),
          quest("Tour").owner(MAX).modified(7),
          quest("Phare").owner(MAX).closed().modified(8),
          quest("Mur").owner(MAX).locked(),
          quest("Grenier").owner(MAX).privateQuest().reader(LEO).in("Tour").modified(2));

  private static Set<UUID> visible(UUID selected, Tab tab) {
    return Set.copyOf(MapQuestFilter.visible(WORLD, LEO, selected, tab));
  }

  private static Set<UUID> titled(String... titles) {
    return Set.copyOf(ids(titles));
  }

  @Nested
  class WithoutSelection {

    @Test
    void myQuestsDrawTopLevelQuestsAndSubquestsIHoldARoleOn() {
      assertEquals(titled("hub", "Gare", "Quai", "Grenier"), visible(null, Tab.MY_QUESTS));
    }

    @Test
    void theBoardDrawsItsTopLevelQuestsButNeverALockedOne() {
      assertEquals(titled("Tour", "Phare"), visible(null, Tab.QUEST_BOARD));
    }

    @Test
    void anUnknownSelectionAddsNothing() {
      assertEquals(
          titled("hub", "Gare", "Quai", "Grenier"), visible(UUID.randomUUID(), Tab.MY_QUESTS));
    }
  }

  @Nested
  class WithSelection {

    @ParameterizedTest(name = "selecting {0} draws the whole tree of hub")
    @CsvSource({"hub", "Wagons", "Roues", "Intérieurs"})
    void anyQuestOfATreeDrawsTheWholeTree(String selected) {
      assertEquals(
          titled("hub", "Intérieurs", "Wagons", "Roues", "Gare", "Quai", "Grenier"),
          visible(id(selected), Tab.MY_QUESTS));
    }

    @Test
    void aLockedQuestInTheSelectedTreeIsStillNotDrawn() {
      assertEquals(titled("hub", "Gare", "Quai", "Grenier"), visible(id("Gare"), Tab.MY_QUESTS));
    }

    @Test
    void theSelectedTreeIsLimitedToTheTabsQuests() {
      // Tour is on the board; Grenier, inside it, is Leo's and not on the board
      assertEquals(titled("Tour", "Phare"), visible(id("Tour"), Tab.QUEST_BOARD));
    }

    @Test
    void onTheBoardSelectingASubquestDrawsItsTree() {
      assertEquals(
          titled("Tour", "Phare", "Intérieurs", "Wagons", "Roues"),
          visible(id("Roues"), Tab.QUEST_BOARD));
    }
  }

  @Nested
  class ListSettings {

    @Test
    void theOpenClosedFilterNarrowsTheBoard() {
      ListOptions open = new ListOptions(Set.of(), BoardFilter.OPEN, false);
      assertEquals(
          List.of(id("Tour")), MapQuestFilter.visible(WORLD, LEO, null, Tab.QUEST_BOARD, "", open));
    }

    @Test
    void aSearchNarrowsTheMapLikeTheList() {
      assertEquals(
          List.of(id("Gare")),
          MapQuestFilter.visible(WORLD, LEO, null, Tab.MY_QUESTS, "gare", ListOptions.DEFAULT));
    }

    @Test
    void theArchivedTabDrawsTheActiveMyQuestsWhateverTheSearch() {
      assertEquals(
          titled("hub", "Gare", "Quai", "Grenier"),
          Set.copyOf(
              MapQuestFilter.visible(WORLD, LEO, null, Tab.ARCHIVED, "gare", ListOptions.DEFAULT)));
    }

    @Test
    void quests_comeInListOrder() {
      assertEquals(
          ids("Gare", "Quai", "hub", "Grenier"),
          MapQuestFilter.visible(WORLD, LEO, null, Tab.MY_QUESTS));
    }
  }

  @Nested
  class Completion {

    @Test
    void completedQuestsAreNeverDrawnAndTheirActiveSubquestsAreTopLevel() {
      QuestGraph g =
          graph(
              quest("Gare").completed(),
              quest("Roues").in("Gare").modified(2),
              quest("Quai").owner(MAX).in("Roues").modified(3));
      assertEquals(List.of(id("Roues")), MapQuestFilter.visible(g, LEO, null, Tab.MY_QUESTS));
      assertEquals(List.of(id("Roues")), MapQuestFilter.visible(g, LEO, null, Tab.ARCHIVED));
    }
  }

  @Nested
  class OlderServer {

    @Test
    void withoutParentsEveryListedQuestIsDrawn() {
      QuestGraph g =
          graph(
              quest("Gare").modified(3),
              quest("hub").modified(2),
              quest("Tour").owner(MAX).modified(1));
      assertEquals(ids("Gare", "hub"), MapQuestFilter.visible(g, LEO, null, Tab.MY_QUESTS));
      assertEquals(ids("Tour"), MapQuestFilter.visible(g, LEO, null, Tab.QUEST_BOARD));
    }
  }
}
