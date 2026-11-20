package com.disqt.disquests.common.graph;

import static com.disqt.disquests.common.graph.GraphFixtures.ANNA;
import static com.disqt.disquests.common.graph.GraphFixtures.JUJU;
import static com.disqt.disquests.common.graph.GraphFixtures.LEO;
import static com.disqt.disquests.common.graph.GraphFixtures.MAX;
import static com.disqt.disquests.common.graph.GraphFixtures.STRANGER;
import static com.disqt.disquests.common.graph.GraphFixtures.graph;
import static com.disqt.disquests.common.graph.GraphFixtures.id;
import static com.disqt.disquests.common.graph.GraphFixtures.quest;
import static com.disqt.disquests.common.graph.GraphFixtures.replace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.graph.Plans.Counts;
import com.disqt.disquests.common.graph.Plans.DeletePlan;
import com.disqt.disquests.common.graph.Plans.PeopleResult;
import com.disqt.disquests.common.graph.Plans.PrivatizeInfo;
import com.disqt.disquests.common.graph.Plans.Unlock;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.PersonOp;
import com.disqt.disquests.common.model.PersonOp.Kind;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlansTest {

  /** Holds no role anywhere; used to check what everyone else sees. */
  private static final UUID NOBODY = new UUID(0, 0);

  private static final List<UUID> KNOWN = List.of(LEO, MAX, JUJU, ANNA);

  /** The graph after the server applied the plan. */
  static QuestGraph apply(QuestGraph g, DeletePlan plan) {
    List<QuestData> kept = new ArrayList<>();
    for (QuestData q : g.all()) {
      if (plan.deleted().contains(q.id())) continue;
      QuestData k = q;
      if (plan.reparent().containsKey(q.id())) {
        k = k.withParent(plan.reparent().get(q.id()).orElse(null));
      }
      if (plan.privatize().containsKey(q.id())) k = k.withVisibility(Visibility.PRIVATE);
      List<UUID> drop = plan.prerequisitesToRemove().getOrDefault(q.id(), List.of());
      if (!drop.isEmpty()) {
        List<UUID> prerequisites = new ArrayList<>(k.prerequisites());
        prerequisites.removeAll(drop);
        k = k.withPrerequisites(List.copyOf(prerequisites));
      }
      kept.add(k);
    }
    return QuestGraph.of(kept);
  }

  private static Set<UUID> ids(String... titles) {
    return Set.copyOf(Arrays.stream(titles).map(GraphFixtures::id).toList());
  }

  /**
   * LEO's Open hub (JUJU writes on it) holds MAX's Private {@code c}, which LEO cannot see; inside
   * {@code c} are LEO's unfinished {@code f} and MAX's {@code g}, which LEO reads.
   */
  private static QuestGraph hiddenBetween() {
    return graph(
        quest("hub").open().writer(JUJU),
        quest("c").owner(MAX).in("hub"),
        quest("f").open().in("c"),
        quest("g").owner(MAX).open().reader(LEO).in("c"));
  }

  private static List<UUID> players(String names) {
    if (names == null || names.isBlank()) return List.of();
    return Arrays.stream(names.split(";")).map(String::trim).map(GraphFixtures::player).toList();
  }

  @Nested
  class Delete {

    @Test
    void untickedMovesEveryChildUpOneLevel() {
      QuestGraph g =
          graph(
              quest("top").open(),
              quest("hub").open().in("top"),
              quest("kid1").open().in("hub"),
              quest("kid2").owner(MAX).closed().in("hub"),
              quest("grandkid").open().in("kid1"));

      assertEquals(
          new DeletePlan(
              ids("hub"),
              Map.of(id("kid1"), Optional.of(id("top")), id("kid2"), Optional.of(id("top"))),
              Map.of(),
              Map.of()),
          Plans.delete(g, LEO, id("hub"), false, KNOWN));
    }

    @Test
    void tickedDeletesOnlyMineAndRehomesToTheNearestSurvivor() {
      QuestGraph g =
          graph(
              quest("top").open(),
              quest("x").open().in("top"),
              quest("y").open().in("x"),
              quest("z").owner(MAX).open().in("y"),
              quest("m").owner(MAX).open().in("x"),
              quest("n").open().in("m"));

      assertEquals(
          new DeletePlan(
              ids("x", "y", "n"),
              Map.of(id("z"), Optional.of(id("top")), id("m"), Optional.of(id("top"))),
              Map.of(),
              Map.of()),
          Plans.delete(g, LEO, id("x"), true, KNOWN));
    }

    @Test
    void tickedKeepsMyQuestBeyondAQuestIDoNotSee() {
      assertEquals(
          new DeletePlan(ids("hub"), Map.of(id("c"), Optional.empty()), Map.of(), Map.of()),
          Plans.delete(hiddenBetween(), LEO, id("hub"), true, KNOWN));
    }

    @Test
    void openChildOfADeletedPrivateParentIsPrivatized() {
      QuestGraph g = graph(quest("secret").reader(JUJU), quest("inside").open().in("secret"));

      assertEquals(
          new DeletePlan(
              ids("secret"),
              Map.of(id("inside"), Optional.empty()),
              Map.of(id("inside"), new PrivatizeInfo(Visibility.OPEN, "secret", LEO, Set.of(LEO))),
              Map.of()),
          Plans.delete(g, LEO, id("secret"), false, KNOWN));
    }

    @Test
    void openChildOfADeletedOpenTopLevelParentIsNot() {
      QuestGraph g = graph(quest("hub").open(), quest("kid").open().in("hub"));

      assertEquals(
          new DeletePlan(ids("hub"), Map.of(id("kid"), Optional.empty()), Map.of(), Map.of()),
          Plans.delete(g, LEO, id("hub"), false, KNOWN));
    }

    @Test
    void privateChildIsNeverPrivatizedAgain() {
      QuestGraph g = graph(quest("secret"), quest("inside").in("secret"));

      assertEquals(Map.of(), Plans.delete(g, LEO, id("secret"), false, KNOWN).privatize());
    }

    @Test
    void childOfADeletedQuestLockedForAReaderIsPrivatized() {
      QuestGraph g =
          graph(
              quest("gate").open(),
              quest("chapter").open().reader(JUJU).after("gate"),
              quest("room").open().in("chapter"));

      assertEquals(
          Map.of(id("room"), new PrivatizeInfo(Visibility.OPEN, "chapter", LEO, Set.of(LEO))),
          Plans.delete(g, LEO, id("chapter"), false, KNOWN).privatize());
    }

    @Test
    void aPlayerWithNoRoleAnywhereCountsWithoutBeingKnown() {
      QuestGraph g =
          graph(
              quest("gate").open(),
              quest("chapter").open().after("gate"),
              quest("room").open().in("chapter"));

      assertEquals(
          Set.of(id("room")),
          Plans.delete(g, LEO, id("chapter"), false, List.of()).privatize().keySet());
    }

    @Test
    void ownersWhoSawItExcludesAnOwnerWithoutARoleOnTheDeletedQuest() {
      QuestGraph g =
          graph(quest("secret").reader(JUJU), quest("inside").closed().coOwner(MAX).in("secret"));

      assertEquals(
          Map.of(id("inside"), new PrivatizeInfo(Visibility.CLOSED, "secret", LEO, Set.of(LEO))),
          Plans.delete(g, LEO, id("secret"), false, KNOWN).privatize());
    }

    @Test
    void completedDeletedPrerequisiteIsRemovedAndAnUnfinishedOneStays() {
      QuestGraph g =
          graph(
              quest("pack").open(),
              quest("done").open().in("pack").completed(),
              quest("todo").open().in("pack"),
              quest("follow").owner(MAX).open().after("done", "todo"));

      DeletePlan plan = Plans.delete(g, LEO, id("pack"), true, KNOWN);

      assertEquals(
          new DeletePlan(
              ids("pack", "done", "todo"),
              Map.of(),
              Map.of(),
              Map.of(id("follow"), List.of(id("done")))),
          plan);
      assertTrue(apply(g, plan).locked(id("follow")), "follow-up stays locked");
    }

    @TestFactory
    Stream<DynamicTest> deletingNeverWidensAnAudienceNorUnlocks() {
      return IntStream.rangeClosed(1, 200)
          .mapToObj(seed -> DynamicTest.dynamicTest("seed " + seed, () -> checkDelete(seed)));
    }

    private static void checkDelete(int seed) {
      Random random = new Random(seed);
      QuestGraph g =
          decorate(GraphFixtures.randomForest(random, 12, seed % 2 == 0 ? 3 : 0), random);
      // Prefer a quest with subquests, so the delete has something to move.
      List<QuestData> parents =
          g.all().stream().filter(q -> !g.children(q.id()).isEmpty()).toList();
      List<QuestData> pool = parents.isEmpty() ? List.copyOf(g.all()) : parents;
      QuestData target = pool.get(random.nextInt(pool.size()));
      boolean alsoMine = random.nextBoolean();

      DeletePlan plan = Plans.delete(g, target.ownerUuid(), target.id(), alsoMine, KNOWN);
      QuestGraph after = apply(g, plan);

      assertTrue(plan.deleted().contains(target.id()), "target deleted");
      for (QuestData q : after.all()) {
        String where = "seed " + seed + " quest " + q.title();
        assertEquals(g.locked(q.id()), after.locked(q.id()), "lock changed: " + where);
        for (UUID p : List.of(LEO, MAX, JUJU, ANNA, STRANGER, NOBODY)) {
          if (after.canSee(p, q.id())) {
            assertTrue(g.canSee(p, q.id()), "widened for " + p + ": " + where);
          }
        }
      }
    }

    /** Completes about a quarter of the quests and gives ANNA a role on about a third. */
    private static QuestGraph decorate(QuestGraph g, Random random) {
      List<Role> roles = List.of(Role.READ, Role.WRITE, Role.OWNER);
      List<QuestData> quests = new ArrayList<>();
      for (QuestData q : g.all()) {
        QuestData d = q;
        if (random.nextInt(3) == 0) {
          List<ContributorData> people = new ArrayList<>(d.contributors());
          people.add(new ContributorData(ANNA, "p4", roles.get(random.nextInt(3))));
          d = d.withContributors(List.copyOf(people));
        }
        if (random.nextInt(4) == 0) d = d.withCompletion(7L, d.ownerUuid(), "someone");
        quests.add(d);
      }
      return QuestGraph.of(quests);
    }
  }

  @Nested
  class Complete {

    private static final QuestGraph G =
        graph(
            quest("goal").open(),
            quest("a").open().in("goal"),
            quest("b").open().in("goal").completed(),
            quest("c").owner(MAX).open().in("goal"),
            quest("d").open().in("a"),
            quest("e").open().in("c"));

    @Test
    void untickedCompletesOnlyTheQuest() {
      assertEquals(List.of(id("goal")), Plans.completeTargets(G, LEO, id("goal"), false));
    }

    @Test
    void tickedAlsoCompletesMyUnfinishedDescendants() {
      assertEquals(
          List.of(id("goal"), id("a"), id("d"), id("e")),
          Plans.completeTargets(G, LEO, id("goal"), true));
    }

    @Test
    void myQuestBeyondAQuestIDoNotSeeIsNotCompleted() {
      assertEquals(
          List.of(id("hub")), Plans.completeTargets(hiddenBetween(), LEO, id("hub"), true));
    }
  }

  @Nested
  class Unlocks {

    @ParameterizedTest(name = "{0}, auto-join {1} -> joined [{2}]")
    @CsvSource({"OPEN, true, juju", "OPEN, false, ''", "CLOSED, true, ''"})
    void autoJoinOnlyWhenOpenAndTicked(Visibility visibility, boolean ticked, String joined) {
      GraphFixtures.QuestBuilder next = quest("next").visibility(visibility).after("gate");
      if (ticked) next.autoJoin();
      QuestGraph before = graph(quest("gate").open().reader(JUJU), next);
      QuestGraph after = completed(before, "gate");

      assertEquals(
          List.of(
              new Unlock(id("next"), players(joined), List.of(LEO, MAX, JUJU), List.of(LEO, JUJU))),
          Plans.unlocks(before, after, List.of(LEO, MAX, JUJU)));
    }

    @ParameterizedTest(name = "MAX co-owns next: {0} -> joined [{1}]")
    @CsvSource({"false, juju", "true, juju;anna"})
    void playersOfAPrerequisiteNoOwnerCanSeeAreSkipped(boolean maxCoOwns, String joined) {
      GraphFixtures.QuestBuilder next = quest("next").open().autoJoin().after("gate", "max secret");
      if (maxCoOwns) next.coOwner(MAX);
      QuestGraph before =
          graph(
              quest("gate").open().reader(JUJU),
              quest("max secret").owner(MAX).reader(ANNA).completed(),
              next);
      QuestGraph after = completed(before, "gate");

      assertEquals(
          List.of(
              new Unlock(
                  id("next"), players(joined), List.of(LEO, JUJU, ANNA), List.of(LEO, JUJU, ANNA))),
          Plans.unlocks(before, after, List.of(LEO, JUJU, ANNA)));
    }

    @Test
    void playersWhoCannotSeeTheQuestAreNotJoinedNorNotified() {
      QuestGraph before =
          graph(
              quest("gate").open().reader(JUJU),
              quest("hub"),
              quest("next").open().autoJoin().in("hub").after("gate"));
      QuestGraph after = completed(before, "gate");

      assertEquals(
          List.of(new Unlock(id("next"), List.of(), List.of(LEO), List.of(LEO))),
          Plans.unlocks(before, after, List.of(LEO, JUJU)));
    }

    @Test
    void aQuestWhosePrerequisiteWasRemovedIsReported() {
      QuestGraph before =
          graph(quest("gate").owner(MAX).open(), quest("next").open().after("gate"));
      QuestGraph after = replace(before, id("next"), q -> q.withPrerequisites(List.of()));

      assertEquals(
          List.of(new Unlock(id("next"), List.of(), List.of(LEO, MAX), List.of())),
          Plans.unlocks(before, after, List.of(LEO, MAX)));
    }

    @Test
    void stillLockedOrCompletedQuestsAreNotReported() {
      QuestGraph before =
          graph(
              quest("gate").open(),
              quest("other gate").open(),
              quest("both").open().after("gate", "other gate"),
              quest("done after").open().after("gate"));
      QuestGraph after = completed(completed(before, "gate"), "done after");

      assertEquals(List.of(), Plans.unlocks(before, after, List.of(LEO)));
    }

    private static QuestGraph completed(QuestGraph g, String title) {
      return replace(g, id(title), q -> q.withCompletion(5L, q.ownerUuid(), "someone"));
    }
  }

  @Nested
  class People {

    /** LEO owns it, MAX writes, JUJU reads. */
    private static final QuestData Q = quest("q").owner(LEO).writer(MAX).reader(JUJU).build();

    private static ContributorData person(UUID player, Role role) {
      return new ContributorData(player, "p" + player.getLeastSignificantBits(), role);
    }

    private static PersonOp set(UUID player, Role role) {
      return new PersonOp(Kind.SET_ROLE, player, role);
    }

    private static PersonOp remove(UUID player) {
      return new PersonOp(Kind.REMOVE, player, null);
    }

    private static PeopleResult refused(QuestData q, Refusal refusal) {
      return new PeopleResult(q.contributors(), q.ownerUuid(), Optional.of(refusal));
    }

    @Test
    void demotingTheOnlyOwnerIsRefused() {
      assertEquals(
          refused(Q, Refusal.LAST_OWNER), Plans.people(Q, LEO, List.of(set(LEO, Role.WRITE))));
    }

    @Test
    void removingSelfAsLastOwnerIsRefused() {
      assertEquals(refused(Q, Refusal.LAST_OWNER), Plans.people(Q, LEO, List.of(remove(LEO))));
    }

    @Test
    void aNonOwnerIsRefused() {
      assertEquals(
          refused(Q, Refusal.NEED_OWNER), Plans.people(Q, MAX, List.of(set(JUJU, Role.WRITE))));
    }

    @Test
    void theWholeListIsRefusedWhenItsEndLeavesNoOwner() {
      assertEquals(
          refused(Q, Refusal.LAST_OWNER),
          Plans.people(Q, LEO, List.of(set(ANNA, Role.READ), remove(LEO))));
    }

    @Test
    void setRoleWithoutARoleIsRejected() {
      assertThrows(
          IllegalArgumentException.class,
          () -> Plans.people(Q, LEO, List.of(set(ANNA, Role.READ), set(JUJU, null))));
    }

    @Test
    void twoOwnersSwappingRolesInOneListIsAccepted() {
      assertEquals(
          new PeopleResult(
              List.of(person(LEO, Role.WRITE), person(JUJU, Role.READ)), MAX, Optional.empty()),
          Plans.people(Q, LEO, List.of(set(MAX, Role.OWNER), set(LEO, Role.WRITE))));
    }

    @Test
    void aNewPlayerIsAddedAtTheEndWithoutAName() {
      assertEquals(
          new PeopleResult(
              List.of(
                  person(MAX, Role.WRITE),
                  person(JUJU, Role.READ),
                  new ContributorData(ANNA, null, Role.READ)),
              LEO,
              Optional.empty()),
          Plans.people(Q, LEO, List.of(set(ANNA, Role.READ))));
    }

    @Test
    void removeWithMySubquestsRemovesFromThisQuestOnly() {
      assertEquals(
          new PeopleResult(List.of(person(MAX, Role.WRITE)), LEO, Optional.empty()),
          Plans.people(Q, LEO, List.of(new PersonOp(Kind.REMOVE_WITH_MY_SUBQUESTS, JUJU, null))));
    }

    @Test
    void theDisplayOwnerLeavingHandsDisplayToTheNextOwner() {
      QuestData q = quest("q").owner(LEO).reader(JUJU).coOwner(MAX).build();

      assertEquals(
          new PeopleResult(List.of(person(JUJU, Role.READ)), MAX, Optional.empty()),
          Plans.people(q, LEO, List.of(remove(LEO))));
    }
  }

  @Nested
  class RoleDown {

    private static final QuestGraph G =
        graph(
            quest("hub").writer(JUJU),
            quest("a").in("hub").reader(JUJU),
            quest("b").in("hub").coOwner(JUJU),
            quest("c").owner(MAX).open().in("hub"),
            quest("d").in("a").completed(),
            quest("e").in("c"));

    @Test
    void givesTheRoleWhereTheActorOwnsAndThePlayerHoldsLess() {
      assertEquals(
          Map.of(id("a"), Role.WRITE, id("d"), Role.WRITE, id("e"), Role.WRITE),
          Plans.roleDown(G, LEO, id("hub"), JUJU));
    }

    @Test
    void notGivenBeyondAQuestTheActorDoesNotSee() {
      assertEquals(Map.of(), Plans.roleDown(hiddenBetween(), LEO, id("hub"), JUJU));
    }

    @Test
    void nothingForAPlayerWithoutARoleHere() {
      assertEquals(Map.of(), Plans.roleDown(G, LEO, id("hub"), ANNA));
    }

    @Test
    void mySubquestsAreTheOwnedDescendantsAlongAVisiblePath() {
      assertEquals(
          List.of(id("a"), id("b"), id("d"), id("e")), Plans.mySubquests(G, LEO, id("hub")));
    }

    @Test
    void mySubquestsStopAtAQuestTheActorDoesNotSee() {
      assertEquals(List.of(), Plans.mySubquests(hiddenBetween(), LEO, id("hub")));
    }
  }

  @Nested
  class SubquestCounts {

    private static final QuestGraph G =
        graph(
            quest("hub").open(),
            quest("a").open().in("hub"),
            quest("b").owner(MAX).open().in("hub"),
            quest("c").owner(MAX).in("hub"),
            quest("d").open().in("hub").completed(),
            quest("e").owner(MAX).open().in("hub").completed(),
            quest("f").owner(MAX).open().in("c"));

    @ParameterizedTest(name = "unfinished only {0} -> mine {1}, others {2}")
    @CsvSource({"true, 1, 1", "false, 2, 2"})
    void countsOnlyWhatTheViewerSees(boolean unfinishedOnly, int mine, int others) {
      assertEquals(
          new Counts(mine, others), Plans.subquestCounts(G, LEO, id("hub"), unfinishedOnly));
    }

    @Test
    void questsBeyondAQuestTheViewerDoesNotSeeAreNotCounted() {
      assertEquals(new Counts(0, 0), Plans.subquestCounts(hiddenBetween(), LEO, id("hub"), false));
    }
  }
}
