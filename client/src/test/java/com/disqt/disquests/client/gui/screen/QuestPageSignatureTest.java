package com.disqt.disquests.client.gui.screen;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.ANNA;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.JUJU;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.disqt.disquests.client.gui.screen.QuestPage.Actions;
import com.disqt.disquests.client.gui.screen.QuestPage.Interact;
import com.disqt.disquests.client.gui.screen.QuestPage.Link;
import com.disqt.disquests.client.gui.screen.QuestPage.PageSignature;
import com.disqt.disquests.client.gui.screen.QuestPage.Prerequisites;
import com.disqt.disquests.client.gui.tree.TreeFixtures.Q;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class QuestPageSignatureTest {

  @Nested
  class InteractButton {

    private static final Actions NO_ROLE =
        new Actions(true, false, false, false, false, false, false, false, false, false);
    private static final Actions MEMBER =
        new Actions(false, true, true, false, false, false, false, false, false, false);

    @ParameterizedTest
    @EnumSource(Visibility.class)
    void byVisibilityForAPlayerWithoutARole(Visibility v) {
      Interact expected =
          switch (v) {
            case OPEN -> Interact.JOIN;
            case CLOSED -> Interact.REQUEST;
            case PRIVATE -> Interact.NONE;
          };
      assertEquals(expected, QuestPage.interact(NO_ROLE, v));
    }

    @ParameterizedTest
    @EnumSource(Visibility.class)
    void noneForAMember(Visibility v) {
      assertEquals(Interact.NONE, QuestPage.interact(MEMBER, v));
    }
  }

  @Nested
  class PrerequisiteLines {

    @Test
    void visibleOnesAreLinksTickedWhenCompleted() {
      QuestGraph g =
          QuestGraph.of(
              List.of(
                  quest("Dig").owner(LEO).completed().build(),
                  quest("Mine").owner(LEO).build(),
                  quest("Gare").owner(LEO).after("Dig", "Mine").build()));
      assertEquals(
          new Prerequisites(
              List.of(new Link(id("Dig"), "Dig", true), new Link(id("Mine"), "Mine", false)),
              false,
              false,
              false),
          QuestPage.prerequisites(g.get(id("Gare")), g));
    }

    @Test
    void aPrerequisiteTheClientDoesNotHaveReadsAsHidden() {
      QuestGraph g = QuestGraph.of(List.of(quest("Gare").owner(LEO).after("Dig").build()));
      assertEquals(
          new Prerequisites(List.of(), true, false, false),
          QuestPage.prerequisites(g.get(id("Gare")), g));
    }

    @Test
    void withADeletedPrerequisiteAMissingIdIsTheDeletedQuest() {
      QuestGraph g =
          QuestGraph.of(
              List.of(quest("Gare").owner(LEO).after("Dig").deletedPrerequisite().build()));
      assertEquals(
          new Prerequisites(List.of(), false, true, false),
          QuestPage.prerequisites(g.get(id("Gare")), g));
    }

    @Test
    void hiddenStillShowsBesideADeletedOne() {
      QuestGraph g =
          QuestGraph.of(
              List.of(
                  quest("Gare")
                      .owner(LEO)
                      .after("Dig")
                      .deletedPrerequisite()
                      .hiddenPrerequisites()
                      .build()));
      assertEquals(
          new Prerequisites(List.of(), true, true, false),
          QuestPage.prerequisites(g.get(id("Gare")), g));
    }

    @Test
    void flagsCarryOver() {
      QuestData gare =
          withAutoJoin(
              quest("Gare").owner(LEO).hiddenPrerequisites().deletedPrerequisite().build());
      QuestGraph g = QuestGraph.of(List.of(gare));
      assertEquals(
          new Prerequisites(List.of(), true, true, true), QuestPage.prerequisites(gare, g));
    }
  }

  @Nested
  class Signature {

    /** Leo's hub, Max's Gare inside it with Juju on Write, Roues inside Gare, Gare after Dig. */
    private static List<Q> base() {
      return new ArrayList<>(
          List.of(
              quest("Hub").owner(LEO),
              quest("Gare").owner(MAX).writer(JUJU).in("Hub").after("Dig"),
              quest("Roues").owner(MAX).in("Gare"),
              quest("Dig").owner(LEO),
              quest("Other").owner(LEO).content("one")));
    }

    private static PageSignature signature(List<QuestData> quests) {
      QuestGraph g = QuestGraph.of(quests);
      return QuestPage.signature(g.get(id("Gare")), g, JUJU, Set.of(), true);
    }

    private static List<QuestData> built(List<Q> qs) {
      return qs.stream().map(Q::build).toList();
    }

    /** The base graph with the quest titled {@code title} replaced by {@code change}. */
    private static List<QuestData> with(String title, Q change) {
      List<Q> qs = base();
      qs.replaceAll(q -> q.build().title().equals(title) ? change : q);
      return built(qs);
    }

    private static List<QuestData> edited(Consumer<List<QuestData>> edit) {
      List<QuestData> qs = new ArrayList<>(built(base()));
      edit.accept(qs);
      return qs;
    }

    private static final PageSignature BASE = signature(built(base()));

    @Test
    void theSameGraphGivesTheSameSignature() {
      assertEquals(BASE, signature(built(base())));
    }

    @Test
    void aNewSaveTimeAloneLeavesItEqual() {
      assertEquals(
          BASE,
          signature(
              with(
                  "Gare",
                  quest("Gare").owner(MAX).writer(JUJU).in("Hub").after("Dig").modified(99))));
    }

    @Test
    void aContentChangeOnTheQuestChangesIt() {
      assertNotEquals(
          BASE,
          signature(
              with(
                  "Gare",
                  quest("Gare")
                      .owner(MAX)
                      .writer(JUJU)
                      .in("Hub")
                      .after("Dig")
                      .content("- [x] a"))));
    }

    @Test
    void anUnrelatedQuestChangingLeavesItEqual() {
      assertEquals(BASE, signature(with("Other", quest("Other").owner(LEO).content("two"))));
    }

    static Stream<Arguments> changes() {
      return Stream.of(
          Arguments.of(
              "viewer demoted",
              with("Gare", quest("Gare").owner(MAX).reader(JUJU).in("Hub").after("Dig"))),
          Arguments.of(
              "owner added",
              with(
                  "Gare",
                  quest("Gare").owner(MAX).writer(JUJU).coOwner(ANNA).in("Hub").after("Dig"))),
          Arguments.of(
              "contributor added",
              with(
                  "Gare",
                  quest("Gare").owner(MAX).writer(JUJU).reader(ANNA).in("Hub").after("Dig"))),
          Arguments.of(
              "visibility changed",
              with("Gare", quest("Gare").owner(MAX).writer(JUJU).closed().in("Hub").after("Dig"))),
          Arguments.of(
              "quest itself completed",
              with(
                  "Gare",
                  quest("Gare").owner(MAX).writer(JUJU).in("Hub").after("Dig").completed())),
          Arguments.of(
              "subquest completed",
              with("Roues", quest("Roues").owner(MAX).in("Gare").completed())),
          Arguments.of(
              "subquest renamed", edited(qs -> qs.replaceAll(q -> retitled(q, "Roues", "Wheels")))),
          Arguments.of(
              "subquest added",
              edited(qs -> qs.add(quest("Wagons").owner(MAX).in("Gare").build()))),
          Arguments.of(
              "subquest locked or unlocked",
              with("Roues", quest("Roues").owner(MAX).in("Gare").after("Dig").locked())),
          Arguments.of(
              "subquest deleted", edited(qs -> qs.removeIf(q -> q.title().equals("Roues")))),
          Arguments.of(
              "sibling added under the parent",
              edited(qs -> qs.add(quest("Wagons").owner(MAX).in("Hub").build()))),
          Arguments.of(
              "parent renamed", edited(qs -> qs.replaceAll(q -> retitled(q, "Hub", "Gare hub")))),
          Arguments.of("prerequisite completed", with("Dig", quest("Dig").owner(LEO).completed())),
          Arguments.of(
              "prerequisite renamed",
              edited(qs -> qs.replaceAll(q -> retitled(q, "Dig", "Dig deeper")))),
          Arguments.of(
              "prerequisite deleted", edited(qs -> qs.removeIf(q -> q.title().equals("Dig")))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changes")
    void eachChangeOnThePageChangesIt(String name, List<QuestData> changed) {
      assertNotEquals(BASE, signature(changed));
    }

    @Test
    void noPaneWithoutTrees() {
      QuestGraph g = QuestGraph.of(built(base()));
      assertEquals(
          List.of(), QuestPage.signature(g.get(id("Gare")), g, JUJU, Set.of(), false).pane());
    }
  }

  /** {@code q} with a new title when it is the quest titled {@code from}; ids stay. */
  private static QuestData retitled(QuestData q, String from, String to) {
    if (!q.title().equals(from)) return q;
    return copy(q, to, q.autoJoin());
  }

  private static QuestData withAutoJoin(QuestData q) {
    return copy(q, q.title(), true);
  }

  private static QuestData copy(QuestData q, String title, boolean autoJoin) {
    return new QuestData(
        q.id(),
        title,
        q.content(),
        q.ownerUuid(),
        q.ownerName(),
        q.visibility(),
        q.contributors(),
        q.lastModified(),
        q.coordinates(),
        q.isRegion(),
        q.coordinates2(),
        q.map(),
        q.tags(),
        q.polygon(),
        q.completedAt(),
        q.completedBy(),
        q.completedByName(),
        q.parentId(),
        q.prerequisites(),
        q.hiddenPrerequisites(),
        q.deletedPrerequisite(),
        q.hiddenParent(),
        autoJoin,
        q.locked(),
        q.privatized());
  }
}
