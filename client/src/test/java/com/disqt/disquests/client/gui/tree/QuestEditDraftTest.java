package com.disqt.disquests.client.gui.tree;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.graph;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.ids;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.client.gui.tree.QuestEditDraft.HiddenParent;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.TreeOp;
import com.disqt.disquests.common.model.TreeOp.Kind;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QuestEditDraftTest {

  private static final int DEPTH = 3;

  /**
   * Hub > E (edited, after A and B, auto-join off) > S, Hub > T, and A, B, C, D on their own; Leo
   * owns everything but Max's M.
   */
  private static final QuestGraph BASE =
      graph(
          quest("Hub"),
          quest("E").in("Hub").after("A", "B"),
          quest("S").in("E"),
          quest("T").in("Hub"),
          quest("Done").in("Hub").completed(),
          quest("A"),
          quest("B"),
          quest("C"),
          quest("D"),
          quest("M").owner(MAX));

  private static QuestEditDraft draft() {
    return new QuestEditDraft(BASE, BASE.get(id("E")));
  }

  private static QuestEditDraft draftOf(QuestGraph g, String title) {
    return new QuestEditDraft(g, g.get(id(title)));
  }

  private static LinkChanges links(
      Visibility visibility,
      List<TreeOp> ops,
      List<UUID> add,
      List<UUID> remove,
      boolean dropHidden,
      boolean dropDeleted,
      Boolean autoJoin) {
    return new LinkChanges(visibility, ops, add, remove, dropHidden, dropDeleted, autoJoin);
  }

  @Nested
  class NoChange {

    @Test
    void aFreshDraftIsCleanAndSendsNoLinks() {
      QuestEditDraft d = draft();
      assertFalse(d.isDirty());
      assertEquals(LinkChanges.NONE, d.toLinkChanges());
    }

    @Test
    void startsFromTheQuestsOwnLinks() {
      QuestEditDraft d = draft();
      assertEquals(Visibility.OPEN, d.visibility());
      assertEquals(ids("A", "B"), d.prerequisites());
      assertFalse(d.autoJoin());
      assertTrue(d.hasPrerequisites());
    }
  }

  /** One change of each kind, what it sends, and that it makes the editor dirty. */
  static Stream<Arguments> eachKindOfChange() {
    return Stream.of(
        Arguments.of(
            "visibility",
            (Consumer<QuestEditDraft>) d -> d.visibility(Visibility.CLOSED),
            links(Visibility.CLOSED, List.of(), List.of(), List.of(), false, false, null)),
        Arguments.of(
            "added prerequisite",
            (Consumer<QuestEditDraft>) d -> d.addPrerequisite(id("C")),
            links(null, List.of(), ids("C"), List.of(), false, false, null)),
        Arguments.of(
            "removed prerequisite",
            (Consumer<QuestEditDraft>) d -> d.removePrerequisite(id("A")),
            links(null, List.of(), List.of(), ids("A"), false, false, null)),
        Arguments.of(
            "auto-join",
            (Consumer<QuestEditDraft>) d -> d.autoJoin(true),
            links(null, List.of(), List.of(), List.of(), false, false, true)),
        Arguments.of(
            "tree move",
            (Consumer<QuestEditDraft>) d -> d.tree().move(id("C"), id("E"), LEO, DEPTH),
            links(
                null,
                List.of(new TreeOp(Kind.ATTACH, id("C"), id("E"))),
                List.of(),
                List.of(),
                false,
                false,
                null)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("eachKindOfChange")
  void eachChangeIsSentAloneAndMakesTheEditorDirty(
      String name, Consumer<QuestEditDraft> change, LinkChanges expected) {
    QuestEditDraft d = draft();
    change.accept(d);
    assertTrue(d.isDirty());
    assertEquals(expected, d.toLinkChanges());
  }

  @Nested
  class ChangedBack {

    @Test
    void visibilitySetBackSendsNothing() {
      QuestEditDraft d = draft();
      d.visibility(Visibility.PRIVATE);
      d.visibility(Visibility.OPEN);
      assertFalse(d.isDirty());
      assertEquals(LinkChanges.NONE, d.toLinkChanges());
    }

    @Test
    void autoJoinTickedAndUntickedSendsNothing() {
      QuestEditDraft d = draft();
      d.autoJoin(true);
      d.autoJoin(false);
      assertFalse(d.isDirty());
      assertEquals(LinkChanges.NONE, d.toLinkChanges());
    }

    @Test
    void autoJoinUntickedOnAQuestThatHadItSendsFalse() {
      QuestGraph g = graph(quest("E").after("A").autoJoin(), quest("A"));
      QuestEditDraft d = draftOf(g, "E");
      assertTrue(d.autoJoin());
      d.autoJoin(false);
      assertEquals(
          links(null, List.of(), List.of(), List.of(), false, false, false), d.toLinkChanges());
    }

    @Test
    void prerequisiteRemovedAndAddedBackSendsNothing() {
      QuestEditDraft d = draft();
      d.removePrerequisite(id("A"));
      d.addPrerequisite(id("A"));
      assertFalse(d.isDirty());
      assertEquals(LinkChanges.NONE, d.toLinkChanges());
    }

    @Test
    void prerequisiteAddedAndRemovedSendsNothing() {
      QuestEditDraft d = draft();
      d.addPrerequisite(id("C"));
      d.removePrerequisite(id("C"));
      assertEquals(LinkChanges.NONE, d.toLinkChanges());
    }

    @Test
    void treeMovedAndMovedBackSendsNothing() {
      QuestEditDraft d = draft();
      d.tree().detach(id("S"), LEO);
      d.tree().move(id("S"), id("E"), LEO, DEPTH);
      assertFalse(d.isDirty());
      assertEquals(LinkChanges.NONE, d.toLinkChanges());
    }
  }

  @Nested
  class Prerequisites {

    @Test
    void addsAreChosenMinusOriginalAndRemovesOriginalMinusChosen() {
      QuestEditDraft d = draft();
      d.removePrerequisite(id("B"));
      d.addPrerequisite(id("D"));
      d.addPrerequisite(id("C"));
      assertEquals(ids("A", "D", "C"), d.prerequisites());
      assertEquals(
          links(null, List.of(), ids("D", "C"), ids("B"), false, false, null), d.toLinkChanges());
    }

    @Test
    void addingAChosenQuestAgainKeepsOneChip() {
      QuestEditDraft d = draft();
      d.addPrerequisite(id("A"));
      assertEquals(ids("A", "B"), d.prerequisites());
      assertFalse(d.isDirty());
    }

    @Test
    void removingEveryChipLeavesNoPrerequisites() {
      QuestEditDraft d = draft();
      d.removePrerequisite(id("A"));
      d.removePrerequisite(id("B"));
      assertFalse(d.hasPrerequisites());
    }

    @Test
    void hiddenChipCountsAsAPrerequisiteUntilDropped() {
      QuestGraph g = graph(quest("E").hiddenPrerequisites());
      QuestEditDraft d = draftOf(g, "E");
      assertTrue(d.showsHiddenPrerequisites());
      assertTrue(d.hasPrerequisites());
      d.dropHidden();
      assertFalse(d.showsHiddenPrerequisites());
      assertFalse(d.hasPrerequisites());
      assertTrue(d.isDirty());
      assertEquals(
          links(null, List.of(), List.of(), List.of(), true, false, null), d.toLinkChanges());
    }

    @Test
    void deletedChipCountsAsAPrerequisiteUntilDropped() {
      QuestGraph g = graph(quest("E").deletedPrerequisite());
      QuestEditDraft d = draftOf(g, "E");
      assertTrue(d.showsDeletedPrerequisite());
      assertTrue(d.hasPrerequisites());
      d.dropDeleted();
      assertFalse(d.showsDeletedPrerequisite());
      assertFalse(d.hasPrerequisites());
      assertEquals(
          links(null, List.of(), List.of(), List.of(), false, true, null), d.toLinkChanges());
    }

    @Test
    void droppingAChipTheQuestDoesNotHaveDoesNothing() {
      QuestEditDraft d = draft();
      d.dropHidden();
      d.dropDeleted();
      assertFalse(d.isDirty());
      assertEquals(LinkChanges.NONE, d.toLinkChanges());
    }
  }

  @Test
  void everyKindTogetherIsOneLinksBlock() {
    QuestEditDraft d =
        draftOf(graph(quest("E").after("A").hiddenPrerequisites(), quest("A"), quest("C")), "E");
    d.visibility(Visibility.PRIVATE);
    d.removePrerequisite(id("A"));
    d.addPrerequisite(id("C"));
    d.dropHidden();
    d.autoJoin(true);
    d.tree().move(id("C"), id("E"), LEO, DEPTH);
    assertEquals(
        links(
            Visibility.PRIVATE,
            List.of(new TreeOp(Kind.ATTACH, id("C"), id("E"))),
            ids("C"),
            ids("A"),
            true,
            false,
            true),
        d.toLinkChanges());
  }

  @Nested
  class NewQuest {

    private final UUID fresh = UUID.fromString("00000000-0000-0000-0000-00000000abcd");

    /** The editor's quest, not in the client graph yet. */
    private QuestEditDraft newDraft() {
      QuestData provisional =
          new QuestData(
              fresh,
              "",
              "",
              LEO,
              "Leo",
              Visibility.PRIVATE,
              List.of(),
              0L,
              null,
              false,
              null,
              null,
              List.of(),
              List.of());
      return new QuestEditDraft(BASE, provisional);
    }

    @Test
    void aSubquestPickedForAQuestNotSavedYetIsAttachedToIt() {
      QuestEditDraft d = newDraft();
      assertEquals(Optional.empty(), d.tree().move(id("C"), fresh, LEO, DEPTH));
      assertEquals(
          links(
              null,
              List.of(new TreeOp(Kind.ATTACH, id("C"), fresh)),
              List.of(),
              List.of(),
              false,
              false,
              null),
          d.toLinkChanges());
    }

    @Test
    void thePaneShowsTheNewQuestsOwnLine() {
      QuestEditDraft d = newDraft();
      assertEquals(List.of(fresh), d.rows(LEO).stream().map(QuestTreeRows.Row::id).toList());
    }

    @Test
    void aQuestOwnedBySomeoneElseIsRefused() {
      QuestEditDraft d = newDraft();
      assertEquals(Optional.of(Refusal.NEED_OWNER), d.tree().move(id("M"), fresh, LEO, DEPTH));
      assertFalse(d.isDirty());
    }

    @Test
    void aNewQuestIsPrivateUnlessChanged() {
      QuestEditDraft d = newDraft();
      assertEquals(Visibility.PRIVATE, d.visibility());
      d.visibility(Visibility.OPEN);
      assertEquals(Visibility.OPEN, d.toLinkChanges().visibility());
    }
  }

  @Nested
  class Pane {

    @Test
    void rowsAreTheWholeTreeFromItsTop() {
      List<QuestTreeRows.Row> rows = draft().rows(LEO);
      assertEquals(id("Hub"), rows.get(0).id());
      assertEquals(
          java.util.Set.copyOf(ids("Hub", "E", "S", "T", "Done")),
          java.util.Set.copyOf(rows.stream().map(QuestTreeRows.Row::id).toList()));
    }

    @Test
    void aQuestInsideAnotherCanBeRemovedButNotTheTopOrACompletedOne() {
      QuestEditDraft d = draft();
      assertTrue(d.canRemove(id("S"), LEO));
      assertTrue(d.canRemove(id("E"), LEO));
      assertFalse(d.canRemove(id("Hub"), LEO));
      assertFalse(d.canRemove(id("Done"), LEO));
    }

    @Test
    void anOwnerOfTheParentCanRemoveSomeoneElsesSubquest() {
      QuestGraph g = graph(quest("E"), quest("X").owner(MAX).reader(LEO).in("E"));
      assertTrue(draftOf(g, "E").canRemove(id("X"), LEO));
    }

    @Test
    void aSubquestTakenOutIsListedBelowTheTree() {
      QuestEditDraft d = draft();
      d.tree().detach(id("T"), LEO);
      assertEquals(ids("T"), d.takenOut(LEO));
      assertFalse(d.rows(LEO).stream().anyMatch(r -> r.id().equals(id("T"))));
    }

    @Test
    void theEditedQuestMovedToAnotherTreeIsNotListedAsTakenOut() {
      QuestEditDraft d = draft();
      d.tree().move(id("E"), id("C"), LEO, DEPTH);
      assertEquals(List.of(), d.takenOut(LEO));
      assertEquals(ids("C", "E", "S"), d.rows(LEO).stream().map(QuestTreeRows.Row::id).toList());
    }

    @Test
    void hasParentFollowsTheDraft() {
      QuestEditDraft d = draft();
      assertTrue(d.hasParent());
      d.tree().detach(id("E"), LEO);
      assertFalse(d.hasParent());
    }
  }

  @Nested
  class HiddenParentLine {

    @Test
    void noLineForAQuestWithAVisibleOrNoParent() {
      assertEquals(HiddenParent.NONE, draft().hiddenParent());
    }

    @Test
    void detachingFromTheHiddenParentIsAChangeNotSavedYet() {
      QuestGraph g = graph(quest("E").hiddenParent());
      QuestEditDraft d = draftOf(g, "E");
      assertEquals(HiddenParent.INSIDE, d.hiddenParent());
      assertEquals(Optional.empty(), d.tree().detachFromHiddenParent(id("E"), LEO));
      assertEquals(HiddenParent.DETACHED, d.hiddenParent());
      assertTrue(d.isDirty());
      assertEquals(
          links(
              null,
              List.of(new TreeOp(Kind.DETACH, id("E"), null)),
              List.of(),
              List.of(),
              false,
              false,
              null),
          d.toLinkChanges());
    }
  }
}
