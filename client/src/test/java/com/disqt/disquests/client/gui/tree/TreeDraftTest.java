package com.disqt.disquests.client.gui.tree;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.graph;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.ids;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.parents;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.replay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.TreeOp;
import com.disqt.disquests.common.model.TreeOp.Kind;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class TreeDraftTest {

  private static final int DEPTH = 3;

  /** P > X > Y, and A, B, C on their own; Leo owns everything but Max's M. */
  private static final QuestGraph BASE =
      graph(
          quest("P"),
          quest("X").in("P"),
          quest("Y").in("X"),
          quest("A"),
          quest("B"),
          quest("C"),
          quest("M").owner(MAX));

  private static TreeOp attach(String quest, String target) {
    return new TreeOp(Kind.ATTACH, id(quest), id(target));
  }

  private static TreeOp detach(String quest) {
    return new TreeOp(Kind.DETACH, id(quest), null);
  }

  private static TreeOp swap(String container, String descendant) {
    return new TreeOp(Kind.SWAP, id(container), id(descendant));
  }

  private static UUID parentIn(QuestGraph g, String title) {
    return g.get(id(title)).parentId();
  }

  /**
   * Replaying the draft's ops on the base tree is accepted op by op and gives its effective tree.
   */
  private static void assertReplays(TreeDraft draft) {
    QuestGraph replayed = replay(BASE, LEO, DEPTH, draft.ops());
    assertEquals(parents(draft.effective()), parents(replayed));
  }

  private static TreeDraft draft() {
    return new TreeDraft(BASE);
  }

  @Nested
  class Ops {

    @Test
    void aNewDraftSendsNothing() {
      TreeDraft d = draft();
      assertEquals(List.of(), d.ops());
      assertTrue(d.isEmpty());
      assertEquals(parents(BASE), parents(d.effective()));
    }

    @Test
    void moveThenMoveBackSendsNothing() {
      TreeDraft d = draft();
      assertEquals(Optional.empty(), d.move(id("A"), id("B"), LEO, DEPTH));
      assertEquals(Optional.empty(), d.detach(id("A"), LEO));
      assertEquals(Optional.empty(), d.move(id("X"), id("C"), LEO, DEPTH));
      assertEquals(Optional.empty(), d.move(id("X"), id("P"), LEO, DEPTH));
      assertEquals(List.of(), d.ops());
      assertTrue(d.isEmpty());
      assertFalse(d.isChanged(id("A")));
      assertFalse(d.isChanged(id("X")));
      assertReplays(d);
    }

    @Test
    void aIntoBThenBIntoC() {
      TreeDraft d = draft();
      assertEquals(Optional.empty(), d.move(id("A"), id("B"), LEO, DEPTH));
      assertEquals(Optional.empty(), d.move(id("B"), id("C"), LEO, DEPTH));
      assertEquals(List.of(attach("A", "B"), attach("B", "C")), d.ops());
      assertEquals(id("C"), parentIn(d.effective(), "B"));
      assertEquals(id("B"), parentIn(d.effective(), "A"));
      assertReplays(d);
    }

    @Test
    void aQuestMovedTwiceKeepsOnlyItsLastMove() {
      TreeDraft d = draft();
      d.move(id("A"), id("B"), LEO, DEPTH);
      d.move(id("A"), id("C"), LEO, DEPTH);
      assertEquals(List.of(attach("A", "C")), d.ops());
      assertReplays(d);
    }

    @Test
    void droppingAQuestOntoItsOwnSubquestSwapsThem() {
      TreeDraft d = draft();
      assertEquals(Optional.empty(), d.move(id("X"), id("Y"), LEO, DEPTH));
      assertEquals(List.of(swap("X", "Y")), d.ops());
      assertEquals(id("P"), parentIn(d.effective(), "Y"));
      assertEquals(id("Y"), parentIn(d.effective(), "X"));
      assertTrue(d.isChanged(id("X")));
      assertTrue(d.isChanged(id("Y")));
      assertReplays(d);
    }

    @Test
    void aSwapThenMovingOneOfTheTwoKeepsBoth() {
      TreeDraft d = draft();
      d.move(id("X"), id("Y"), LEO, DEPTH);
      assertEquals(Optional.empty(), d.move(id("Y"), id("C"), LEO, DEPTH));
      assertEquals(List.of(swap("X", "Y"), attach("Y", "C")), d.ops());
      assertEquals(id("C"), parentIn(d.effective(), "Y"));
      assertEquals(id("Y"), parentIn(d.effective(), "X"));
      assertReplays(d);
    }

    @Test
    void aSwapAndItsReverseSendNothing() {
      TreeDraft d = draft();
      d.move(id("X"), id("Y"), LEO, DEPTH);
      assertEquals(Optional.empty(), d.move(id("Y"), id("X"), LEO, DEPTH));
      assertEquals(List.of(), d.ops());
      assertReplays(d);
    }

    @Test
    void detachThenReattachElsewhereIsOneMove() {
      TreeDraft d = draft();
      d.detach(id("X"), LEO);
      assertEquals(Optional.empty(), d.move(id("X"), id("B"), LEO, DEPTH));
      assertEquals(List.of(attach("X", "B")), d.ops());
      assertReplays(d);
    }

    @Test
    void detachingTheEditedQuest() {
      TreeDraft d = draft();
      assertEquals(Optional.empty(), d.detach(id("X"), LEO));
      assertEquals(List.of(detach("X")), d.ops());
      assertTrue(d.isChanged(id("X")));
      assertFalse(d.isChanged(id("Y")));
      assertFalse(d.isEmpty());
      assertReplays(d);
    }

    @Test
    void aMoveThatIsOnlyPossibleAfterAnotherKeepsTheOrder() {
      // P > X > Y is full at depth 3: A fits under Y only once X is out of P.
      TreeDraft d = draft();
      assertEquals(Optional.of(Refusal.TOO_DEEP), d.move(id("A"), id("Y"), LEO, DEPTH));
      assertEquals(Optional.empty(), d.detach(id("X"), LEO));
      assertEquals(Optional.empty(), d.move(id("A"), id("Y"), LEO, DEPTH));
      assertEquals(List.of(detach("X"), attach("A", "Y")), d.ops());
      assertReplays(d);
    }

    @Test
    void aQuestInsideAHiddenQuestMovedAwayAndBackIsStillTakenOutOfIt() {
      // H holds A on the server, but Leo cannot see H: A shows as top-level. Moving it into B and
      // back out really takes it out of H, so both ops are sent.
      QuestGraph g = graph(quest("A").hiddenParent(), quest("B"));
      TreeDraft d = new TreeDraft(g);
      assertEquals(Optional.empty(), d.move(id("A"), id("B"), LEO, DEPTH));
      assertEquals(Optional.empty(), d.detach(id("A"), LEO));
      assertEquals(List.of(attach("A", "B"), detach("A")), d.ops());
      assertTrue(d.isChanged(id("A")));
    }

    @Test
    void aSwapPutsTheSubquestIntoTheHiddenPlaceOfItsContainer() {
      QuestGraph g = graph(quest("X").hiddenParent(), quest("Y").in("X"));
      TreeDraft d = new TreeDraft(g);
      d.move(id("X"), id("Y"), LEO, DEPTH);
      assertEquals(List.of(swap("X", "Y")), d.ops());
      assertTrue(d.effective().get(id("Y")).hiddenParent());
      assertFalse(d.effective().get(id("X")).hiddenParent());
      assertEquals(id("Y"), parentIn(d.effective(), "X"));
    }
  }

  @Nested
  class Refusals {

    @Test
    void aRefusedMoveLeavesTheDraftUnchanged() {
      TreeDraft d = draft();
      d.move(id("A"), id("B"), LEO, DEPTH);
      List<TreeOp> before = d.ops();
      assertEquals(Optional.of(Refusal.TOO_DEEP), d.move(id("B"), id("Y"), LEO, DEPTH));
      assertEquals(Optional.of(Refusal.NEED_OWNER), d.move(id("M"), id("C"), LEO, DEPTH));
      assertEquals(Optional.of(Refusal.ALREADY_THERE), d.detach(id("C"), LEO));
      assertEquals(Optional.of(Refusal.SELF), d.move(id("C"), id("C"), LEO, DEPTH));
      assertEquals(before, d.ops());
      assertEquals(id("B"), parentIn(d.effective(), "A"));
      assertFalse(d.isChanged(id("B")));
    }

    @Test
    void movesAreCheckedAgainstTheDraftNotTheBase() {
      TreeDraft d = draft();
      d.move(id("A"), id("B"), LEO, DEPTH);
      // B now holds A, so moving B into A would be a loop were it not a swap.
      assertEquals(Optional.empty(), d.move(id("B"), id("A"), LEO, DEPTH));
      assertEquals(id("A"), parentIn(d.effective(), "B"));
      assertEquals(null, parentIn(d.effective(), "A"));
      // A swap of two quests that are not nested any more is the combined move the server knows.
      assertEquals(List.of(swap("B", "A")), d.ops());
      assertReplays(d);
    }
  }

  @Nested
  class TakenOut {

    @Test
    void detachedQuestsAndWhatIsInsideThemAreTakenOut() {
      TreeDraft d = draft();
      d.detach(id("X"), LEO);
      assertEquals(ids("X", "Y"), d.takenOut(id("P")));
    }

    @Test
    void aQuestMovedToAnotherTreeIsTakenOut() {
      TreeDraft d = draft();
      d.move(id("Y"), id("A"), LEO, DEPTH);
      assertEquals(ids("Y"), d.takenOut(id("P")));
    }

    @Test
    void aMoveInsideTheTreeTakesNothingOut() {
      TreeDraft d = draft();
      d.move(id("Y"), id("P"), LEO, DEPTH);
      assertEquals(List.of(), d.takenOut(id("P")));
    }

    @Test
    void swappingTheTopKeepsTheTreeTogether() {
      TreeDraft d = draft();
      d.move(id("P"), id("X"), LEO, DEPTH);
      assertEquals(List.of(), d.takenOut(id("P")));
    }
  }

  /**
   * Random move and detach sequences over a small forest: whatever the player did, the ops the
   * draft sends replay on the base tree op by op and give the tree the player saw, and no op is
   * left for a quest that ends up where it started unless dropping it changes the outcome.
   */
  @TestFactory
  Stream<DynamicTest> randomDraftsReplayToTheirEffectiveTree() {
    QuestGraph forest =
        graph(
            quest("r0"),
            quest("r1").in("r0"),
            quest("r2").in("r1"),
            quest("r3"),
            quest("r4").in("r3"),
            quest("r5"),
            quest("r6"),
            quest("r7").in("r6"));
    List<UUID> all = forest.all().stream().map(QuestData::id).toList();
    return IntStream.range(0, 200)
        .mapToObj(
            seed ->
                DynamicTest.dynamicTest(
                    "seed " + seed,
                    () -> {
                      Random random = new Random(seed);
                      TreeDraft d = new TreeDraft(forest);
                      for (int step = 0; step < 8; step++) {
                        UUID a = all.get(random.nextInt(all.size()));
                        UUID b = all.get(random.nextInt(all.size()));
                        if (random.nextInt(4) == 0) d.detach(a, LEO);
                        else d.move(a, b, LEO, DEPTH);
                      }
                      QuestGraph replayed = replay(forest, LEO, DEPTH, d.ops());
                      assertEquals(parents(d.effective()), parents(replayed));
                      assertEquals(d.ops().isEmpty(), parents(forest).equals(parents(replayed)));
                    }));
  }
}
