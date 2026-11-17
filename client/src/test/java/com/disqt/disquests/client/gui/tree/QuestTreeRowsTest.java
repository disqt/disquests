package com.disqt.disquests.client.gui.tree;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.JUJU;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.graph;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.ClientSession.QuestFilter;
import com.disqt.disquests.client.ClientSession.Tab;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.ListOptions;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.Row;
import com.disqt.disquests.common.graph.QuestGraph;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuestTreeRowsTest {

  /** A row the player holds no role on, not locked. */
  private static Row row(String title, int depth, int children, boolean expanded) {
    return new Row(id(title), depth, children, expanded, false, false);
  }

  /** A row the player holds a role on, not locked. */
  private static Row mine(String title, int depth, int children, boolean expanded) {
    return new Row(id(title), depth, children, expanded, false, true);
  }

  private static Set<UUID> expanded(String... titles) {
    return Set.copyOf(Arrays.stream(titles).map(TreeFixtures::id).toList());
  }

  /** Leo's hub with three subquests, one of them Max's, and a sub-subquest. */
  private static final QuestGraph HUB =
      graph(
          quest("hub").modified(10),
          quest("Intérieurs").owner(MAX).in("hub").modified(30),
          quest("Wagons").in("hub").modified(20),
          quest("Roues").owner(MAX).in("Wagons").tags("bois").modified(5),
          quest("Gare").modified(40));

  @Nested
  class MyQuests {

    @Test
    void subquestsAreCollapsedByDefault() {
      assertEquals(
          List.of(mine("Gare", 0, 0, false), mine("hub", 0, 2, false)),
          QuestTreeRows.forTab(HUB, LEO, Tab.MY_QUESTS, "", Set.of()));
    }

    @Test
    void expandedQuestsShowTheirSubquestsNewestFirst() {
      assertEquals(
          List.of(
              mine("Gare", 0, 0, false),
              mine("hub", 0, 2, true),
              row("Intérieurs", 1, 0, false),
              mine("Wagons", 1, 1, true),
              row("Roues", 2, 0, false)),
          QuestTreeRows.forTab(HUB, LEO, Tab.MY_QUESTS, "", expanded("hub", "Wagons")));
    }

    @Test
    void aCollapsedQuestHidesEverythingBelowItEvenWhenExpanded() {
      assertEquals(
          List.of(mine("Gare", 0, 0, false), mine("hub", 0, 2, false)),
          QuestTreeRows.forTab(HUB, LEO, Tab.MY_QUESTS, "", expanded("Wagons")));
    }

    @Test
    void aSubquestLeftActiveUnderAnArchivedParentIsTopLevel() {
      QuestGraph g =
          graph(
              quest("Gare").completed(),
              quest("Roues").in("Gare").modified(2),
              quest("Quai").owner(MAX).in("Gare").modified(3));
      assertEquals(
          List.of(mine("Roues", 0, 0, false)),
          QuestTreeRows.forTab(g, LEO, Tab.MY_QUESTS, "", Set.of()));
    }

    @Test
    void aSubquestOfAnotherPlayersQuestIsTopLevelForItsMember() {
      QuestGraph g =
          graph(quest("hub").owner(MAX), quest("Wagons").owner(MAX).in("hub").reader(LEO));
      assertEquals(
          List.of(mine("Wagons", 0, 0, false)),
          QuestTreeRows.forTab(g, LEO, Tab.MY_QUESTS, "", Set.of()));
    }

    @Test
    void anActiveQuestUnderACompletedSubquestIsTopLevel() {
      QuestGraph g =
          graph(
              quest("hub"),
              quest("Wagons").owner(MAX).in("hub").completed(),
              quest("Roues").owner(MAX).in("Wagons").modified(5));
      assertEquals(
          List.of(row("Roues", 0, 0, false), mine("hub", 0, 0, false)),
          QuestTreeRows.forTab(g, LEO, Tab.MY_QUESTS, "", Set.of()));
    }

    @Test
    void pinnedQuestsComeFirstAmongSiblings() {
      assertEquals(
          List.of(
              mine("hub", 0, 2, true),
              mine("Wagons", 1, 1, false),
              row("Intérieurs", 1, 0, false),
              mine("Gare", 0, 0, false)),
          QuestTreeRows.forTab(
              HUB,
              LEO,
              Tab.MY_QUESTS,
              "",
              expanded("hub"),
              new ListOptions(expanded("hub", "Wagons"), QuestFilter.ALL, false)));
    }

    @Test
    void unplacedQuestsComeFirstOnTheMapLayout() {
      QuestGraph g =
          graph(
              quest("A").placed().modified(9),
              quest("B").modified(1),
              quest("C").placed().modified(5));
      assertEquals(
          List.of(mine("B", 0, 0, false), mine("A", 0, 0, false), mine("C", 0, 0, false)),
          QuestTreeRows.forTab(
              g,
              LEO,
              Tab.MY_QUESTS,
              "",
              Set.of(),
              new ListOptions(Set.of(), QuestFilter.ALL, true)));
    }

    @Test
    void lockedQuestsAreListedAfterTheOthersByTitle() {
      QuestGraph g =
          graph(
              quest("hub").owner(MAX).reader(LEO),
              quest("Zinc").owner(MAX).in("hub").reader(LEO).locked(),
              quest("Acier").owner(MAX).in("hub").reader(LEO).locked(),
              quest("Bois").owner(MAX).in("hub").reader(LEO).modified(1),
              quest("Cuivre").owner(MAX).in("hub").reader(LEO).modified(2));
      assertEquals(
          List.of(
              mine("hub", 0, 4, true),
              mine("Cuivre", 1, 0, false),
              mine("Bois", 1, 0, false),
              new Row(id("Acier"), 1, 0, false, true, true),
              new Row(id("Zinc"), 1, 0, false, true, true)),
          QuestTreeRows.forTab(g, LEO, Tab.MY_QUESTS, "", expanded("hub")));
    }

    @Test
    void indentationStopsAtSixLevels() {
      String[] chain = {"l0", "l1", "l2", "l3", "l4", "l5", "l6", "l7"};
      TreeFixtures.Q[] quests = new TreeFixtures.Q[chain.length];
      for (int i = 0; i < chain.length; i++) {
        quests[i] = quest(chain[i]);
        if (i > 0) quests[i].in(chain[i - 1]);
      }
      List<Integer> depths =
          QuestTreeRows.forTab(graph(quests), LEO, Tab.MY_QUESTS, "", expanded(chain)).stream()
              .map(Row::depth)
              .toList();
      assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 6), depths);
    }
  }

  @Nested
  class Search {

    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource({"roues", "ROUES", "#bois", "#BOIS"})
    void aMatchShowsWithItsAncestorsExpandedAndOtherBranchesOmitted(String search) {
      assertEquals(
          List.of(mine("hub", 0, 1, true), mine("Wagons", 1, 1, true), row("Roues", 2, 0, false)),
          QuestTreeRows.forTab(HUB, LEO, Tab.MY_QUESTS, search, Set.of()));
    }

    @Test
    void aMatchingParentShowsAloneWhenNothingUnderItMatches() {
      assertEquals(
          List.of(mine("hub", 0, 0, false)),
          QuestTreeRows.forTab(HUB, LEO, Tab.MY_QUESTS, "hub", expanded("hub")));
    }

    @Test
    void theDescriptionMatchesToo() {
      QuestGraph g = graph(quest("A").content("Une GRANDE gare"), quest("B"));
      assertEquals(
          List.of(mine("A", 0, 0, false)),
          QuestTreeRows.forTab(g, LEO, Tab.MY_QUESTS, "grande", Set.of()));
    }

    @Test
    void aTagFilterNeedsTheWholeTag() {
      assertEquals(List.of(), QuestTreeRows.forTab(HUB, LEO, Tab.MY_QUESTS, "#boi", Set.of()));
    }
  }

  @Nested
  class QuestBoard {

    /** Max's quests seen by Juju, plus one Juju is on and one Private one Juju is on. */
    private static final QuestGraph BOARD =
        graph(
            quest("hub").owner(MAX).modified(5),
            quest("Wagons").owner(MAX).closed().in("hub").modified(4),
            quest("Roues").owner(MAX).in("Wagons").modified(3),
            quest("Quai").owner(MAX).in("hub").locked(),
            quest("Done").owner(MAX).completed(),
            quest("Mine").owner(MAX).reader(JUJU).modified(9),
            quest("Under mine").owner(MAX).in("Mine").modified(8),
            quest("Secret").owner(MAX).privateQuest().reader(JUJU));

    @Test
    void listsActiveQuestsWithoutARoleNestedAndCollapsed() {
      assertEquals(
          List.of(row("Under mine", 0, 0, false), row("hub", 0, 2, false)),
          QuestTreeRows.forTab(BOARD, JUJU, Tab.SERVER_QUESTS, "", Set.of()));
    }

    @Test
    void lockedQuestsAreListedGreyed() {
      assertEquals(
          List.of(
              row("Under mine", 0, 0, false),
              row("hub", 0, 2, true),
              row("Wagons", 1, 1, false),
              new Row(id("Quai"), 1, 0, false, true, false)),
          QuestTreeRows.forTab(BOARD, JUJU, Tab.SERVER_QUESTS, "", expanded("hub")));
    }

    @Test
    void theOpenClosedFilterTakesOutTheOthersAndTheirSubquestsGoTopLevel() {
      assertEquals(
          List.of(row("Wagons", 0, 0, false)),
          QuestTreeRows.forTab(
              BOARD,
              JUJU,
              Tab.SERVER_QUESTS,
              "",
              Set.of(),
              new ListOptions(Set.of(), QuestFilter.CLOSED, false)));
    }

    @Test
    void pinsDoNotReorderTheBoard() {
      assertEquals(
          List.of(row("Under mine", 0, 0, false), row("hub", 0, 2, false)),
          QuestTreeRows.forTab(
              BOARD,
              JUJU,
              Tab.SERVER_QUESTS,
              "",
              Set.of(),
              new ListOptions(expanded("hub"), QuestFilter.ALL, false)));
    }
  }

  @Nested
  class Archived {

    @Test
    void completedQuestsWithARoleFlatNewestFirst() {
      QuestGraph g =
          graph(
              quest("old").completed(100),
              quest("new").completed(300),
              quest("child").in("new").completed(200),
              quest("not mine").owner(MAX).completed(400),
              quest("active"));
      assertEquals(
          List.of(mine("new", 0, 0, false), mine("child", 0, 0, false), mine("old", 0, 0, false)),
          QuestTreeRows.forTab(g, LEO, Tab.ARCHIVED, "", expanded("new")));
    }

    @Test
    void searchFiltersTheArchive() {
      QuestGraph g = graph(quest("old").completed(100), quest("new").completed(300));
      assertEquals(
          List.of(mine("old", 0, 0, false)),
          QuestTreeRows.forTab(g, LEO, Tab.ARCHIVED, "ol", Set.of()));
    }
  }

  @Nested
  class WhereThisSits {

    /** top > mid > (quest, sibling, done) ; quest > child > grandchild */
    private static final QuestGraph TREE =
        graph(
            quest("top"),
            quest("mid").in("top"),
            quest("quest").in("mid").modified(5),
            quest("sibling").owner(MAX).in("mid").modified(9),
            quest("done").in("mid").completed().modified(1),
            quest("child").in("quest"),
            quest("grandchild").in("child"),
            quest("cousin").in("sibling"),
            quest("alone"));

    @Test
    void emptyForAQuestWithNoParentAndNoSubquests() {
      assertEquals(List.of(), QuestTreeRows.whereThisSits(TREE, LEO, id("alone"), Set.of()));
    }

    @Test
    void ancestorsThenSiblingsWithTheQuestThenItsSubquests() {
      assertEquals(
          List.of(
              mine("top", 0, 1, true),
              mine("mid", 1, 3, true),
              row("sibling", 2, 1, false),
              mine("quest", 2, 1, true),
              mine("child", 3, 1, false),
              mine("done", 2, 0, false)),
          QuestTreeRows.whereThisSits(TREE, LEO, id("quest"), Set.of()));
    }

    @Test
    void deeperLevelsAndSiblingsOpenWhenExpanded() {
      assertEquals(
          List.of(
              mine("top", 0, 1, true),
              mine("mid", 1, 3, true),
              row("sibling", 2, 1, true),
              mine("cousin", 3, 0, false),
              mine("quest", 2, 1, true),
              mine("child", 3, 1, true),
              mine("grandchild", 4, 0, false),
              mine("done", 2, 0, false)),
          QuestTreeRows.whereThisSits(
              TREE, LEO, id("quest"), expanded("child", "sibling", "grandchild")));
    }

    @Test
    void aTopLevelQuestWithSubquestsShowsItselfAndThem() {
      assertEquals(
          List.of(mine("top", 0, 1, true), mine("mid", 1, 3, false)),
          QuestTreeRows.whereThisSits(TREE, LEO, id("top"), Set.of()));
    }

    @Test
    void wholeTreeShowsEverythingUnderTheTopAncestorExpanded() {
      assertEquals(
          List.of(
              mine("top", 0, 1, true),
              mine("mid", 1, 3, true),
              row("sibling", 2, 1, true),
              mine("cousin", 3, 0, false),
              mine("quest", 2, 1, true),
              mine("child", 3, 1, true),
              mine("grandchild", 4, 0, false),
              mine("done", 2, 0, false)),
          QuestTreeRows.wholeTree(TREE, LEO, id("child")));
    }

    @Test
    void wholeTreeOfALoneQuestIsItsOwnLine() {
      assertEquals(
          List.of(mine("alone", 0, 0, false)), QuestTreeRows.wholeTree(TREE, LEO, id("alone")));
    }
  }
}
