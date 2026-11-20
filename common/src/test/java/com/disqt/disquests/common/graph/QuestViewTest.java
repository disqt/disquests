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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

class QuestViewTest {

  private static final UUID NOBODY = new UUID(0, 0);

  /** The view as titles: [parent, prerequisites, hiddenPrerequisites, deletedPrerequisite]. */
  private static List<Object> links(QuestGraph g, QuestData view) {
    return List.of(
        view.parentId() == null ? "-" : g.get(view.parentId()).title(),
        view.prerequisites().stream().map(p -> g.get(p).title()).toList(),
        view.hiddenPrerequisites(),
        view.deletedPrerequisite());
  }

  @Nested
  class Locked {

    /** "gate" waits on "first"; JUJU reads it, ANNA reads "first" only, MAX writes on it. */
    private final QuestGraph g =
        graph(
            quest("hub").open(),
            quest("first").open().reader(ANNA),
            quest("gate").open().rich().in("hub").after("first").reader(JUJU).writer(MAX),
            quest("inside gate").open().in("gate"));

    @Test
    void readerSeesTitleLockAndOwnEntryOnly() {
      QuestData stored = g.get(id("gate"));
      QuestData expected =
          new QuestData(
              stored.id(),
              "gate",
              "",
              NOBODY,
              "",
              Visibility.OPEN,
              List.of(new ContributorData(JUJU, "p3", Role.READ)),
              0L,
              null,
              false,
              null,
              null,
              List.of(),
              List.of(),
              0L,
              null,
              null,
              id("hub"),
              List.of(id("first")),
              false,
              false,
              false,
              false,
              true,
              null);
      assertEquals(expected, QuestView.forRecipient(g, JUJU, id("gate")));
    }

    @Test
    void playerWithoutRoleGetsNoPeopleAtAll() {
      QuestData view = QuestView.forRecipient(g, STRANGER, id("gate"));
      assertEquals(
          List.of(List.of(), NOBODY, "", true),
          List.of(view.contributors(), view.ownerUuid(), view.ownerName(), view.locked()));
    }

    @ParameterizedTest(name = "{0} gets the stored quest unchanged")
    @CsvSource({"leo", "max"})
    void ownerAndWriterAreNotAffectedByTheLock(String viewer) {
      assertEquals(g.get(id("gate")), QuestView.forRecipient(g, player(viewer), id("gate")));
    }

    @ParameterizedTest(name = "{0}: quest inside the locked quest visible: {1}")
    @CsvSource({"juju, false", "stranger, false", "leo, true", "max, true"})
    void questInsideALockedQuest(String viewer, boolean visible) {
      assertEquals(visible, QuestView.forRecipient(g, player(viewer), id("inside gate")) != null);
    }
  }

  @Nested
  class Prerequisites {

    /**
     * LEO owns the waiting quests and JUJU reads them; "max's secret" is Private to MAX, "gone" is
     * not in the graph (deleted). Every prerequisite is completed so nothing is locked.
     */
    private final QuestGraph g =
        graph(
            quest("a1").open().completed(),
            quest("a2").open().completed(),
            quest("max's secret").owner(MAX).completed(),
            quest("all visible").open().after("a1", "a2").reader(JUJU).writer(ANNA),
            quest("some hidden").open().after("a1", "max's secret").reader(JUJU).writer(ANNA),
            quest("none visible").open().after("max's secret").reader(JUJU).writer(ANNA),
            quest("one deleted").open().after("a1", "gone").reader(JUJU).writer(ANNA),
            quest("hidden and deleted")
                .open()
                .after("max's secret", "gone")
                .reader(JUJU)
                .writer(ANNA));

    @ParameterizedTest(name = "{0} looks at [{1}]: [{2}] hidden {3} deleted {4}")
    @CsvSource({
      "leo,      all visible,        a1;a2, false, false",
      "juju,     all visible,        a1;a2, false, false",
      "leo,      some hidden,        a1,    true,  false",
      "juju,     some hidden,        a1,    true,  false",
      "leo,      none visible,       '',    true,  false",
      "juju,     none visible,       '',    true,  false",
      "leo,      one deleted,        a1,    false, true",
      "juju,     one deleted,        a1,    true,  false",
      "anna,     one deleted,        a1,    true,  false",
      "stranger, one deleted,        a1,    true,  false",
      "leo,      hidden and deleted, '',    true,  true",
      "juju,     hidden and deleted, '',    true,  false",
    })
    void rows(String viewer, String quest, String visible, boolean hidden, boolean deleted) {
      List<String> titles = visible.isEmpty() ? List.of() : Arrays.asList(visible.split(";"));
      assertEquals(
          List.of("-", titles, hidden, deleted),
          links(g, QuestView.forRecipient(g, player(viewer), id(quest))));
    }

    @Test
    void lockedQuestStillListsWhatItWaitsOn() {
      QuestGraph locked =
          graph(
              quest("a1").open(),
              quest("hidden").owner(MAX),
              quest("gate").open().after("a1", "hidden", "gone"));
      assertEquals(
          List.of("-", List.of("a1"), true, false),
          links(locked, QuestView.forRecipient(locked, JUJU, id("gate"))));
    }
  }

  @Nested
  class Parent {

    private final QuestGraph g =
        graph(
            quest("max's hub").owner(MAX),
            quest("leo's in max's").open().in("max's hub").writer(JUJU).reader(ANNA),
            quest("open hub").open(),
            quest("in open hub").open().in("open hub"),
            quest("orphan").open().in("deleted hub"));

    /** Only an owner who cannot see the parent is told that there is one. */
    @ParameterizedTest(name = "{0} looks at [{1}]: parent {2}, hidden parent {3}")
    @CsvSource({
      "leo,  leo's in max's, -,         true",
      "juju, leo's in max's, -,         false",
      "anna, leo's in max's, -,         false",
      "max,  leo's in max's, max's hub, false",
      "juju, in open hub,    open hub,  false",
      "leo,  in open hub,    open hub,  false",
      "leo,  orphan,         -,         false",
    })
    void rows(String viewer, String quest, String parent, boolean hiddenParent) {
      QuestData view = QuestView.forRecipient(g, player(viewer), id(quest));
      assertEquals(
          List.of(parent, hiddenParent), List.of(links(g, view).get(0), view.hiddenParent()));
    }

    @Test
    void strangerGetsNothingOfAQuestInAnInvisibleParent() {
      assertNull(QuestView.forRecipient(g, STRANGER, id("leo's in max's")));
    }

    @Test
    void notVisibleMeansNoView() {
      assertNull(QuestView.forRecipient(g, JUJU, id("max's hub")));
    }

    @Test
    void unknownQuestMeansNoView() {
      assertNull(QuestView.forRecipient(g, LEO, id("nowhere")));
    }
  }

  @Nested
  class PrivatizedBanner {

    private final Privatized stored =
        new Privatized(Visibility.OPEN, "Intérieurs", "Leo", 42L, Set.of(LEO));

    /** LEO and ANNA own the kept quest, LEO could see the deleted one; MAX writes, JUJU reads. */
    private final QuestGraph g =
        graph(quest("kept").privatized(stored).coOwner(ANNA).writer(MAX).reader(JUJU));

    @ParameterizedTest(name = "{0} gets banner {1}")
    @CsvSource({
      "leo,  Intérieurs, Leo",
      "anna, ,           ",
    })
    void owners(String viewer, String deletedTitle, String deletedByName) {
      assertEquals(
          new Privatized(Visibility.OPEN, deletedTitle, deletedByName, 42L, Set.of()),
          QuestView.forRecipient(g, player(viewer), id("kept")).privatized());
    }

    @ParameterizedTest(name = "{0} gets no banner")
    @CsvSource({"max", "juju"})
    void othersGetNoBanner(String viewer) {
      assertNull(QuestView.forRecipient(g, player(viewer), id("kept")).privatized());
    }
  }

  /**
   * Random forests with random roles, visibilities, completions, deletions and privatized marks:
   * for every player and quest, the view exists exactly when the player can see the quest, names no
   * quest the player cannot see, and a locked view carries no content.
   */
  @TestFactory
  Stream<DynamicTest> viewsNeverLeak() {
    return IntStream.rangeClosed(1, 200)
        .mapToObj(seed -> DynamicTest.dynamicTest("seed " + seed, () -> checkViews(seed)));
  }

  private static void checkViews(int seed) {
    Random random = new Random(seed);
    QuestGraph g = decorate(GraphFixtures.randomForest(random, 12, seed % 2 == 0 ? 3 : 0), random);
    for (UUID player : List.of(LEO, MAX, JUJU, ANNA, STRANGER)) {
      for (QuestData q : g.all()) {
        QuestData view = QuestView.forRecipient(g, player, q.id());
        String where = "player " + player + " quest " + q.title();
        assertEquals(g.canSee(player, q.id()), view != null, "view exists: " + where);
        if (view == null) continue;
        if (view.parentId() != null) {
          assertTrue(g.canSee(player, view.parentId()), "parent leaks: " + where);
        }
        for (UUID p : view.prerequisites()) {
          assertTrue(g.canSee(player, p), "prerequisite leaks: " + where);
        }
        if (view.privatized() != null) {
          assertEquals(Role.OWNER, q.roleOf(player), "banner for a non-owner: " + where);
          assertEquals(Set.of(), view.privatized().sawDeleted(), "sawDeleted sent: " + where);
          if (view.privatized().deletedTitle() != null) {
            assertTrue(q.privatized().sawDeleted().contains(player), "deleted title: " + where);
          }
        }
        if (view.deletedPrerequisite()) {
          assertEquals(Role.OWNER, q.roleOf(player), "deleted flag for a non-owner: " + where);
        }
        assertEquals(g.lockedFor(player, q.id()), view.locked(), "lock: " + where);
        QuestData parent = g.get(q.parentId());
        assertEquals(
            q.roleOf(player) == Role.OWNER && parent != null && !g.canSee(player, parent.id()),
            view.hiddenParent(),
            "hidden parent: " + where);
        if (view.locked()) {
          assertEquals(lockedAllowlist(q, player, view), view, "locked view: " + where);
        }
      }
    }
  }

  /**
   * What a locked view may carry, built with the positional constructor so that a component added
   * to {@link QuestData} later fails to compile here until it is placed on this list: the id, title
   * and visibility of the stored quest, the link fields of the view (checked separately), the
   * recipient's own entry, and {@code locked}. Everything else is empty.
   */
  private static QuestData lockedAllowlist(QuestData stored, UUID player, QuestData view) {
    return new QuestData(
        stored.id(),
        stored.title(),
        "",
        NOBODY,
        "",
        stored.visibility(),
        stored.contributors().stream().filter(c -> c.uuid().equals(player)).toList(),
        0L,
        null,
        false,
        null,
        null,
        List.of(),
        List.of(),
        0L,
        null,
        null,
        view.parentId(),
        view.prerequisites(),
        view.hiddenPrerequisites(),
        view.deletedPrerequisite(),
        false,
        false,
        true,
        null);
  }

  /**
   * Fills every quest, gives ANNA random roles, completes some quests, privatizes some and deletes
   * a couple so that parents and prerequisites can point at missing quests.
   */
  private static QuestGraph decorate(QuestGraph g, Random random) {
    List<Role> roles = List.of(Role.READ, Role.WRITE, Role.OWNER);
    Map<UUID, QuestData> quests = new LinkedHashMap<>();
    for (QuestData q : g.all()) {
      QuestData rich =
          q.withContent("text of " + q.title())
              .withTags(List.of("tag of " + q.title()))
              .withPolygon(List.of(new com.disqt.disquests.common.model.PointData(1, 2)));
      if (random.nextInt(3) == 0) {
        List<ContributorData> people = new ArrayList<>(rich.contributors());
        people.add(new ContributorData(ANNA, "p4", roles.get(random.nextInt(3))));
        rich = rich.withContributors(List.copyOf(people));
      }
      if (random.nextInt(4) == 0) rich = rich.withCompletion(7L, rich.ownerUuid(), "someone");
      if (random.nextInt(4) == 0) {
        Set<UUID> saw = random.nextBoolean() ? Set.of(rich.ownerUuid()) : Set.of();
        rich = rich.withPrivatized(new Privatized(Visibility.OPEN, "gone", "Leo", 9L, saw));
      }
      quests.put(q.id(), rich);
    }
    List<UUID> ids = new ArrayList<>(quests.keySet());
    for (int i = 0; i < 2; i++) quests.remove(ids.get(random.nextInt(ids.size())));
    return QuestGraph.of(quests.values());
  }
}
