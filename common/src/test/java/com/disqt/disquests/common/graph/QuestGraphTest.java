package com.disqt.disquests.common.graph;

import static com.disqt.disquests.common.graph.GraphFixtures.ANNA;
import static com.disqt.disquests.common.graph.GraphFixtures.JUJU;
import static com.disqt.disquests.common.graph.GraphFixtures.LEO;
import static com.disqt.disquests.common.graph.GraphFixtures.MAX;
import static com.disqt.disquests.common.graph.GraphFixtures.STRANGER;
import static com.disqt.disquests.common.graph.GraphFixtures.graph;
import static com.disqt.disquests.common.graph.GraphFixtures.id;
import static com.disqt.disquests.common.graph.GraphFixtures.player;
import static com.disqt.disquests.common.graph.GraphFixtures.quest;
import static com.disqt.disquests.common.graph.GraphFixtures.titleSet;
import static com.disqt.disquests.common.graph.GraphFixtures.titles;
import static com.disqt.disquests.common.graph.GraphFixtures.visibleTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.disqt.disquests.common.model.Role;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuestGraphTest {

  @Nested
  class Visibility {

    /**
     * The spec's example: Westenheim hub is Private, JUJU is a member, LEO owns it. Église is MAX's
     * Private quest under the hub, with ANNA on it.
     */
    private final QuestGraph hub =
        graph(
            quest("hub").owner(LEO).reader(JUJU),
            quest("Intérieurs").owner(LEO).open().in("hub"),
            quest("Wagons").owner(LEO).closed().in("hub"),
            quest("Église").owner(MAX).reader(ANNA).in("hub"));

    @ParameterizedTest(name = "{0} sees [{1}]")
    @CsvSource({
      "stranger, ''",
      "juju,     hub;Intérieurs;Wagons",
      "leo,      hub;Intérieurs;Wagons",
      "anna,     Église",
      "max,      Église",
    })
    void hubExample(String viewer, String expected) {
      assertEquals(titleSet(expected), visibleTo(hub, player(viewer)));
    }

    @ParameterizedTest(name = "top-level {0} visible to a stranger: {1}")
    @CsvSource({"OPEN, true", "CLOSED, true", "PRIVATE, false"})
    void topLevelQuestsBehaveAsToday(
        com.disqt.disquests.common.model.Visibility visibility, boolean visible) {
      QuestGraph g = graph(quest("solo").visibility(visibility));
      assertEquals(visible, g.canSee(STRANGER, id("solo")));
    }

    @Test
    void roleHolderSeesQuestUnderParentTheyCannotSee() {
      QuestGraph g =
          graph(
              quest("secret").owner(LEO),
              quest("inner").owner(LEO).open().reader(JUJU).in("secret"));
      assertEquals(titleSet("inner"), visibleTo(g, JUJU));
    }

    @Test
    void missingParentCountsAsNoParent() {
      QuestGraph g = graph(quest("orphan").open().in("deleted"));
      assertEquals(titleSet("orphan"), visibleTo(g, STRANGER));
    }

    @Test
    void unknownQuestIsNotVisible() {
      assertEquals(false, hub.canSee(LEO, id("nothing")));
    }

    /**
     * chapter 2 waits on chapter 1 and holds "side". Who sees "side" depends on whether chapter 2
     * is locked for them, unless they hold a role on "side" itself.
     */
    @ParameterizedTest(name = "role {0} on chapter 2, role {1} on side: sees [{2}]")
    @CsvSource({
      "NONE,  NONE,  chapter 1;chapter 2",
      "READ,  NONE,  chapter 1;chapter 2",
      "WRITE, NONE,  chapter 1;chapter 2;side",
      "OWNER, NONE,  chapter 1;chapter 2;side",
      "NONE,  READ,  chapter 1;chapter 2;side",
      "READ,  READ,  chapter 1;chapter 2;side",
    })
    void childOfQuestLockedForViewerIsHiddenUnlessTheyHoldARoleOnIt(
        String onChapter2, String onSide, String expected) {
      GraphFixtures.QuestBuilder chapter2 = quest("chapter 2").owner(LEO).open().after("chapter 1");
      GraphFixtures.QuestBuilder side = quest("side").owner(LEO).open().in("chapter 2");
      if (!onChapter2.equals("NONE")) chapter2.role(JUJU, Role.valueOf(onChapter2));
      if (!onSide.equals("NONE")) side.role(JUJU, Role.valueOf(onSide));
      QuestGraph g = graph(quest("chapter 1").owner(LEO).open(), chapter2, side);

      assertEquals(titleSet(expected), visibleTo(g, JUJU));
    }

    @Test
    void childOfQuestNotLockedForAnyoneOnceItsPrerequisiteIsCompleted() {
      QuestGraph g =
          graph(
              quest("chapter 1").owner(LEO).open().completed(),
              quest("chapter 2").owner(LEO).open().after("chapter 1"),
              quest("side").owner(LEO).open().in("chapter 2"));
      assertEquals(titleSet("chapter 1;chapter 2;side"), visibleTo(g, STRANGER));
    }
  }

  @Nested
  class Locking {

    @Test
    void deletedPrerequisiteLocks() {
      QuestGraph g = graph(quest("waiting").open().after("gone"));
      assertEquals(true, g.locked(id("waiting")));
    }

    @ParameterizedTest(name = "first completed {0}, second completed {1}: locked {2}")
    @CsvSource({
      "false, false, true",
      "true, false, true",
      "false, true, true",
      "true, true, false"
    })
    void allPrerequisitesAreRequired(boolean firstDone, boolean secondDone, boolean locked) {
      GraphFixtures.QuestBuilder first = quest("first").open();
      GraphFixtures.QuestBuilder second = quest("second").open();
      if (firstDone) first.completed();
      if (secondDone) second.completed();
      QuestGraph g = graph(first, second, quest("waiting").open().after("first", "second"));
      assertEquals(locked, g.locked(id("waiting")));
    }

    @Test
    void questWithoutPrerequisitesIsNotLocked() {
      assertEquals(false, graph(quest("free").open()).locked(id("free")));
    }

    @Test
    void completedQuestIsNeverLocked() {
      QuestGraph g =
          graph(quest("first").open(), quest("done").open().completed().after("first", "gone"));
      assertEquals(false, g.locked(id("done")));
      assertEquals(false, g.lockedFor(STRANGER, id("done")));
    }

    @ParameterizedTest(name = "role {0}: locked for them {1}")
    @CsvSource({"NONE, true", "READ, true", "WRITE, false", "OWNER, false"})
    void writeAndOwnerAreNeverLockedOut(String role, boolean lockedFor) {
      GraphFixtures.QuestBuilder waiting = quest("waiting").owner(LEO).open().after("first");
      if (!role.equals("NONE")) waiting.role(JUJU, Role.valueOf(role));
      QuestGraph g = graph(quest("first").open(), waiting);
      assertEquals(lockedFor, g.lockedFor(JUJU, id("waiting")));
    }

    @Test
    void displayOwnerIsNeverLockedOut() {
      QuestGraph g = graph(quest("first").open(), quest("waiting").owner(LEO).after("first"));
      assertEquals(false, g.lockedFor(LEO, id("waiting")));
    }

    @Test
    void unknownQuestIsNotLocked() {
      assertEquals(false, graph().locked(id("nothing")));
    }
  }

  @Nested
  class Shape {

    /** root > a > a1, root > a > a2, root > b; lone stands alone. */
    private final QuestGraph tree =
        graph(
            quest("root"),
            quest("a").in("root"),
            quest("a1").in("a"),
            quest("a2").in("a"),
            quest("b").in("root"),
            quest("lone"));

    @Test
    void getAndAll() {
      assertEquals("a1", tree.get(id("a1")).title());
      assertNull(tree.get(id("nothing")));
      assertEquals(List.of("root", "a", "a1", "a2", "b", "lone"), titles(tree.all()));
    }

    @Test
    void childrenInInputOrder() {
      assertEquals(List.of("a", "b"), titles(tree.children(id("root"))));
      assertEquals(List.of(), titles(tree.children(id("lone"))));
    }

    @Test
    void ancestorsNearestFirst() {
      assertEquals(List.of("a", "root"), titles(tree.ancestors(id("a2"))));
      assertEquals(List.of(), titles(tree.ancestors(id("root"))));
    }

    @Test
    void descendantsOfTheWholeSubtree() {
      assertEquals(List.of("a", "b", "a1", "a2"), titles(tree.descendants(id("root"))));
      assertEquals(List.of(), titles(tree.descendants(id("b"))));
    }

    @ParameterizedTest(name = "{0}: depth {1}, height {2}")
    @CsvSource({"root, 1, 3", "a, 2, 2", "a1, 3, 1", "b, 2, 1", "lone, 1, 1"})
    void depthAndHeight(String quest, int depth, int height) {
      assertEquals(List.of(depth, height), List.of(tree.depth(id(quest)), tree.height(id(quest))));
    }

    @Test
    void missingParentIsTopLevel() {
      QuestGraph g = graph(quest("orphan").in("deleted"));
      assertEquals(List.of(), g.ancestors(id("orphan")));
      assertEquals(1, g.depth(id("orphan")));
    }

    @Test
    void corruptLoopDoesNotHang() {
      QuestGraph g =
          graph(quest("x").open().in("y"), quest("y").open().in("x"), quest("z").open().in("x"));
      UUID x = id("x");
      assertTimeoutPreemptively(
          Duration.ofSeconds(5),
          () -> {
            assertEquals(QuestGraph.MAX_WALK, g.ancestors(x).size());
            assertEquals(QuestGraph.MAX_WALK + 1, g.depth(x));
            assertEquals(List.of("y", "z", "x"), titles(g.descendants(x)));
            g.height(x);
            assertEquals(false, g.canSee(STRANGER, id("z")));
          });
    }
  }
}
