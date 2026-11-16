package com.disqt.disquests.common.graph;

import static com.disqt.disquests.common.graph.GraphFixtures.JUJU;
import static com.disqt.disquests.common.graph.GraphFixtures.LEO;
import static com.disqt.disquests.common.graph.GraphFixtures.MAX;
import static com.disqt.disquests.common.graph.GraphFixtures.attached;
import static com.disqt.disquests.common.graph.GraphFixtures.detached;
import static com.disqt.disquests.common.graph.GraphFixtures.expected;
import static com.disqt.disquests.common.graph.GraphFixtures.graph;
import static com.disqt.disquests.common.graph.GraphFixtures.id;
import static com.disqt.disquests.common.graph.GraphFixtures.player;
import static com.disqt.disquests.common.graph.GraphFixtures.quest;
import static com.disqt.disquests.common.graph.GraphFixtures.swapped;
import static com.disqt.disquests.common.graph.GraphFixtures.titles;
import static com.disqt.disquests.common.graph.GraphFixtures.withPrerequisite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

class LinkRulesTest {

  /**
   * LEO's tree root > mid > leaf (Open), with JUJU reading root and lone, and MAX reading root and
   * writing on mid. Around it: quests to move, completed quests, and quests linked to the tree by
   * prerequisites.
   */
  private static final QuestGraph G =
      graph(
          quest("root").open().reader(JUJU).reader(MAX),
          quest("mid").open().in("root").writer(MAX),
          quest("leaf").open().in("mid"),
          quest("lone").open().reader(JUJU),
          quest("pair").open(),
          quest("pair kid").open().in("pair"),
          quest("done").open().completed(),
          quest("done target").open().completed(),
          quest("waits on root").open().after("root"),
          quest("root follows me").open(),
          quest("kid waits on root").open(),
          quest("waiting kid").open().in("kid waits on root").after("root"),
          quest("max's").owner(MAX).open(),
          quest("max's secret").owner(MAX),
          quest("max's sub").owner(MAX).open().in("root"),
          quest("max's private sub").owner(MAX).in("root"),
          quest("first").open(),
          quest("second").open().after("first"),
          quest("c1").open(),
          quest("c2").open().after("c1"),
          quest("c3").open().after("c2"),
          quest("p1").open(),
          quest("p2").open(),
          quest("p3").open(),
          quest("p4").open(),
          quest("p5").open(),
          quest("p6").open(),
          quest("p7").open(),
          quest("p8").open(),
          quest("p9").open(),
          quest("eight").open().after("p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8"),
          quest("juju writes").open().writer(JUJU),
          quest("leo's secret"),
          quest("done kid").open().in("root").completed());

  /** "root follows me" is a prerequisite of a quest above the target. */
  private static final QuestGraph G_FOLLOWED =
      GraphFixtures.replace(
          G, id("root"), q -> q.withPrerequisites(List.of(id("root follows me"))));

  @Nested
  class Attach {

    @ParameterizedTest(name = "{0}: {1} puts [{2}] in [{3}], limit {4} -> {5}")
    @CsvSource({
      "to itself,                         leo,  lone,              lone,        3, SELF",
      "into its own descendant,           leo,  root,              leaf,        0, LOOP",
      "into its own child,                leo,  mid,               leaf,        0, LOOP",
      "level 4 with limit 3,              leo,  lone,              leaf,        3, TOO_DEEP",
      "level 4 with no limit,             leo,  lone,              leaf,        0, NONE",
      "level 3 with limit 3,              leo,  lone,              mid,         3, NONE",
      "subtree of height 2 to level 3,    leo,  pair,              mid,         3, TOO_DEEP",
      "subtree of height 2 to level 2,    leo,  pair,              root,        3, NONE",
      "completed child,                   leo,  done,              root,        3, COMPLETED",
      "completed target,                  leo,  lone,              done target, 3, TARGET_COMPLETED",
      "child waits on target's ancestor,  leo,  waits on root,     mid,         0, LINKED_BY_PREREQUISITE",
      "child waits on the target,         leo,  waits on root,     root,        0, LINKED_BY_PREREQUISITE",
      "subquest waits on target,          leo,  kid waits on root, root,        0, LINKED_BY_PREREQUISITE",
      "actor reads the child,             juju, lone,              juju writes, 3, NEED_OWNER",
      "actor reads the target,            max,  max's,             root,        3, NEED_WRITE_ON_TARGET",
      "actor writes on the target,        max,  max's,             mid,         3, NONE",
      "actor has no role on the target,   max,  max's,             lone,        3, NEED_WRITE_ON_TARGET",
      "actor cannot see the target,       max,  max's,             leo's secret, 3, NOT_VISIBLE",
      "already there,                     leo,  mid,               root,        3, ALREADY_THERE",
      "move to another parent,            leo,  leaf,              root,        3, NONE",
      "unknown target,                    leo,  lone,              nowhere,     3, NOT_VISIBLE",
      "unknown child,                     leo,  nowhere,           lone,        3, NOT_VISIBLE",
    })
    void rows(String why, String actor, String child, String parent, int limit, String refusal) {
      assertEquals(
          expected(refusal), LinkRules.attach(G, player(actor), id(child), id(parent), limit));
    }

    @Test
    void targetAncestorWaitsOnTheChild() {
      assertEquals(
          Optional.of(Refusal.LINKED_BY_PREREQUISITE),
          LinkRules.attach(G_FOLLOWED, LEO, id("root follows me"), id("leaf"), 0));
    }

    @Test
    void noLimitStillStopsAtTheWalkGuard() {
      List<GraphFixtures.QuestBuilder> chain = new ArrayList<>();
      chain.add(quest("level 1"));
      for (int i = 2; i <= QuestGraph.MAX_WALK; i++) {
        chain.add(quest("level " + i).in("level " + (i - 1)));
      }
      chain.add(quest("extra"));
      QuestGraph g = graph(chain.toArray(GraphFixtures.QuestBuilder[]::new));
      UUID deepest = id("level " + QuestGraph.MAX_WALK);
      UUID nextToDeepest = id("level " + (QuestGraph.MAX_WALK - 1));

      assertEquals(
          List.of(Optional.of(Refusal.TOO_DEEP), Optional.empty()),
          List.of(
              LinkRules.attach(g, LEO, id("extra"), deepest, 0),
              LinkRules.attach(g, LEO, id("extra"), nextToDeepest, 0)));
    }
  }

  @Nested
  class Prerequisite {

    @ParameterizedTest(name = "{0}: {1} makes [{2}] wait on [{3}] -> {4}")
    @CsvSource({
      "on itself,                     leo,  lone,          lone,           SELF",
      "on its ancestor,               leo,  leaf,          root,           LOOP",
      "on its parent,                 leo,  leaf,          mid,            LOOP",
      "on its descendant,             leo,  root,          leaf,           LOOP",
      "closing A after B after A,     leo,  first,         second,         LOOP",
      "closing a longer chain,        leo,  c1,            c3,             LOOP",
      "extending a chain,             leo,  c1,            lone,           NONE",
      "ninth prerequisite,            leo,  eight,         p9,             TOO_MANY_PREREQUISITES",
      "invisible prerequisite,        leo,  lone,          max's secret,   NOT_VISIBLE",
      "unknown prerequisite,          leo,  lone,          nowhere,        NOT_VISIBLE",
      "visible with no role on it,        leo,  lone,          max's,          NONE",
      "completed prerequisite,        leo,  lone,          done,           NONE",
      "completed quest,               leo,  done,          lone,           COMPLETED",
      "already a prerequisite,        leo,  second,        first,          ALREADY_THERE",
      "actor reads the quest,         juju, lone,          first,          NEED_OWNER",
      "actor writes on the quest,     juju, juju writes,   first,          NEED_OWNER",
      "unknown quest,                 leo,  nowhere,       first,          NOT_VISIBLE",
      "in another branch of its tree, leo,  leaf,          max's sub,      NONE",
      "cousin in another tree,        leo,  leaf,          pair kid,       NONE",
    })
    void rows(String why, String actor, String quest, String prerequisite, String refusal) {
      assertEquals(
          expected(refusal), LinkRules.prerequisite(G, player(actor), id(quest), id(prerequisite)));
    }
  }

  @Nested
  class Detach {

    @ParameterizedTest(name = "{0}: {1} takes [{2}] out -> {3}")
    @CsvSource({
      "by the child's owner,                         max,  max's sub,         NONE",
      "by the parent's owner with the child visible,         leo,  max's sub,         NONE",
      "by the parent's owner with a Private child unseen,  leo,  max's private sub, NOT_VISIBLE",
      "by a reader of the parent,                    juju, max's sub,         NEED_OWNER",
      "by a writer on the parent,                    max,  leaf,              NEED_OWNER",
      "top-level quest,                              leo,  lone,              ALREADY_THERE",
      "completed child,                              leo,  done kid,          COMPLETED",
      "unknown quest,                                leo,  nowhere,           NOT_VISIBLE",
    })
    void rows(String why, String actor, String child, String refusal) {
      assertEquals(expected(refusal), LinkRules.detach(G, player(actor), id(child)));
    }

    @Test
    void ownerOfChildCanDetachFromParentTheyCannotSee() {
      QuestGraph g = graph(quest("hidden").owner(MAX), quest("mine").owner(LEO).in("hidden"));
      assertEquals(Optional.empty(), LinkRules.detach(g, LEO, id("mine")));
    }
  }

  @Nested
  class Swap {

    /** top > container > between > inner > deep, and container > other > other kid. */
    private final QuestGraph tree =
        graph(
            quest("top").writer(JUJU),
            quest("container").in("top").coOwner(JUJU),
            quest("between").open().in("container"),
            quest("inner").in("between").coOwner(JUJU),
            quest("deep").open().in("inner"),
            quest("other").in("container"),
            quest("other kid").in("other"));

    @Test
    void descendantTakesTheContainersPlace() {
      assertEquals(Optional.empty(), LinkRules.swap(tree, LEO, id("container"), id("inner"), 0));

      QuestGraph after = swapped(tree, id("container"), id("inner"));
      assertEquals(
          List.of(
              List.of("top"),
              List.of("inner", "top"),
              List.of("container", "inner", "top"),
              List.of("inner", "top"),
              List.of("other", "container", "inner", "top")),
          Stream.of("inner", "container", "between", "deep", "other kid")
              .map(q -> titles(after.ancestors(id(q))))
              .toList());
    }

    @ParameterizedTest(name = "{0}: {1} swaps [{2}] with [{3}], limit {4} -> {5}")
    @CsvSource({
      "owner of both,                      leo,  container, inner,     0, NONE",
      "co-owner of both and writer on parent, juju, container, inner,     0, NONE",
      "with itself,                        leo,  container, container, 0, SELF",
      "not owner of the descendant,        juju, container, deep,      0, NEED_OWNER",
      "not owner of the container,         juju, between,   inner,     0, NEED_OWNER",
      "result too deep,                    leo,  container, inner,     4, TOO_DEEP",
      "result just fits,                   leo,  container, inner,     5, NONE",
      "descendant is really an ancestor,   leo,  inner,     container, 0, LOOP",
      "unknown descendant,                 leo,  container, nowhere,   0, NOT_VISIBLE",
    })
    void rows(
        String why, String actor, String container, String descendant, int limit, String refusal) {
      assertEquals(
          expected(refusal),
          LinkRules.swap(tree, player(actor), id(container), id(descendant), limit));
    }

    @Test
    void needsWriteOnTheContainersParent() {
      QuestGraph g =
          GraphFixtures.replace(
              tree, id("top"), q -> q.withContributors(List.of()).withVisibility(Visibility.OPEN));
      assertEquals(
          Optional.of(Refusal.NEED_WRITE_ON_TARGET),
          LinkRules.swap(g, JUJU, id("container"), id("inner"), 0));
    }

    @Test
    void containersParentCompleted() {
      QuestGraph g = GraphFixtures.replace(tree, id("top"), q -> q.withCompletion(5L, LEO, "leo"));
      assertEquals(
          Optional.of(Refusal.TARGET_COMPLETED),
          LinkRules.swap(g, LEO, id("container"), id("inner"), 0));
    }

    @Test
    void completedDescendant() {
      QuestGraph g =
          GraphFixtures.replace(tree, id("inner"), q -> q.withCompletion(5L, LEO, "leo"));
      assertEquals(
          Optional.of(Refusal.COMPLETED), LinkRules.swap(g, LEO, id("container"), id("inner"), 0));
    }

    @Test
    void siblingWaitingOnTheDescendantWouldEndUpBelowIt() {
      QuestGraph g = withPrerequisite(tree, id("other"), id("inner"));
      assertEquals(
          Optional.of(Refusal.LINKED_BY_PREREQUISITE),
          LinkRules.swap(g, LEO, id("container"), id("inner"), 0));
    }
  }

  /**
   * Random forests built only from accepted changes never hold a loop, a quest deeper than the
   * limit, or a prerequisite between a quest and one of its ancestors.
   */
  @TestFactory
  Stream<DynamicTest> acceptedChangesKeepTheGraphSound() {
    return IntStream.rangeClosed(1, 200)
        .mapToObj(seed -> DynamicTest.dynamicTest("seed " + seed, () -> randomForest(seed)));
  }

  private static void randomForest(int seed) {
    Random random = new Random(seed);
    int size = 12;
    int limit = seed % 2 == 0 ? 3 : 0;
    List<UUID> actors = List.of(LEO, MAX, JUJU);
    List<Visibility> visibilities = List.of(Visibility.PRIVATE, Visibility.CLOSED, Visibility.OPEN);
    List<Role> roles = List.of(Role.READ, Role.WRITE, Role.OWNER);
    List<GraphFixtures.QuestBuilder> builders = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      GraphFixtures.QuestBuilder b =
          quest("q" + i)
              .owner(actors.get(random.nextInt(2)))
              .visibility(visibilities.get(random.nextInt(3)));
      if (random.nextBoolean()) b.role(JUJU, roles.get(random.nextInt(3)));
      builders.add(b);
    }
    QuestGraph g = graph(builders.toArray(GraphFixtures.QuestBuilder[]::new));
    List<UUID> ids = g.all().stream().map(QuestData::id).toList();

    for (int step = 0; step < 150; step++) {
      UUID actor = actors.get(random.nextInt(actors.size()));
      UUID a = ids.get(random.nextInt(size));
      UUID b = ids.get(random.nextInt(size));
      switch (random.nextInt(4)) {
        case 0 -> {
          if (LinkRules.attach(g, actor, a, b, limit).isEmpty()) g = attached(g, a, b);
        }
        case 1 -> {
          if (LinkRules.prerequisite(g, actor, a, b).isEmpty()) g = withPrerequisite(g, a, b);
        }
        case 2 -> {
          if (LinkRules.detach(g, actor, a).isEmpty()) g = detached(g, a);
        }
        default -> {
          if (LinkRules.swap(g, actor, a, b, limit).isEmpty()) g = swapped(g, a, b);
        }
      }
    }

    for (UUID q : ids) {
      List<UUID> ancestors = g.ancestors(q).stream().map(QuestData::id).toList();
      List<UUID> descendants = g.descendants(q).stream().map(QuestData::id).toList();
      assertTrue(ancestors.size() < size && !ancestors.contains(q), "loop above " + q);
      assertTrue(descendants.size() < size && !descendants.contains(q), "loop below " + q);
      assertEquals(descendants.size(), new HashSet<>(descendants).size(), "shared subtree");
      if (limit > 0) assertTrue(g.depth(q) <= limit, "too deep: " + q);
      for (UUID above : ancestors) {
        assertTrue(
            !g.get(q).prerequisites().contains(above) && !g.get(above).prerequisites().contains(q),
            "prerequisite between a quest and its ancestor");
      }
      assertTrue(!waitsOn(g, q, q), "prerequisite loop through " + q);
      assertTrue(g.get(q).prerequisites().size() <= 8, "too many prerequisites");
    }
  }

  /** Whether {@code from} waits, directly or not, on {@code target}. */
  private static boolean waitsOn(QuestGraph g, UUID from, UUID target) {
    Set<UUID> seen = new HashSet<>();
    List<UUID> todo = new ArrayList<>(g.get(from).prerequisites());
    while (!todo.isEmpty()) {
      UUID next = todo.remove(todo.size() - 1);
      if (next.equals(target)) return true;
      QuestData q = g.get(next);
      if (q != null && seen.add(next)) todo.addAll(q.prerequisites());
    }
    return false;
  }
}
