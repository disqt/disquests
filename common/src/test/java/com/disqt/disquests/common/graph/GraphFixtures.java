package com.disqt.disquests.common.graph;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Builds quest graphs for tests: named players with fixed UUIDs, and quests named by their title
 * whose id is derived from that title, so a test can refer to every quest by name.
 *
 * <pre>
 * graph(
 *     quest("hub").owner(LEO).reader(JUJU),
 *     quest("Wagons").closed().in("hub").after("Gare"));
 * </pre>
 */
final class GraphFixtures {

  static final UUID LEO = new UUID(0, 1);
  static final UUID MAX = new UUID(0, 2);
  static final UUID JUJU = new UUID(0, 3);
  static final UUID ANNA = new UUID(0, 4);
  static final UUID STRANGER = new UUID(0, 99);

  /** Lower-case player names as used in {@code @CsvSource} rows. */
  static final Map<String, UUID> PLAYERS =
      Map.of("leo", LEO, "max", MAX, "juju", JUJU, "anna", ANNA, "stranger", STRANGER);

  private GraphFixtures() {}

  static UUID player(String name) {
    UUID uuid = PLAYERS.get(name);
    if (uuid == null) throw new IllegalArgumentException("unknown player " + name);
    return uuid;
  }

  /** The fixed id of the quest with this title. */
  static UUID id(String title) {
    return UUID.nameUUIDFromBytes(("quest:" + title).getBytes(StandardCharsets.UTF_8));
  }

  static QuestBuilder quest(String title) {
    return new QuestBuilder(title);
  }

  static QuestGraph graph(QuestBuilder... quests) {
    return QuestGraph.of(Arrays.stream(quests).map(QuestBuilder::build).toList());
  }

  /** Titles of the given quests, in order. */
  static List<String> titles(Collection<QuestData> quests) {
    return quests.stream().map(QuestData::title).toList();
  }

  /** Titles of every quest the player can see. */
  static Set<String> visibleTo(QuestGraph g, UUID player) {
    return g.all().stream()
        .filter(q -> g.canSee(player, q.id()))
        .map(QuestData::title)
        .collect(Collectors.toSet());
  }

  /** Splits a {@code ;}-separated list of titles; blank means none. */
  static Set<String> titleSet(String titles) {
    if (titles == null || titles.isBlank()) return Set.of();
    return Arrays.stream(titles.split(";")).map(String::trim).collect(Collectors.toSet());
  }

  /** {@code NONE} means accepted, anything else names the expected refusal. */
  static Optional<Refusal> expected(String refusal) {
    return "NONE".equals(refusal) ? Optional.empty() : Optional.of(Refusal.valueOf(refusal));
  }

  // --- applying accepted changes, as the server would store them ---

  static QuestGraph replace(QuestGraph g, UUID quest, UnaryOperator<QuestData> change) {
    Map<UUID, QuestData> quests = new LinkedHashMap<>();
    for (QuestData q : g.all()) quests.put(q.id(), q);
    quests.put(quest, change.apply(quests.get(quest)));
    return QuestGraph.of(quests.values());
  }

  static QuestGraph attached(QuestGraph g, UUID child, UUID parent) {
    return replace(g, child, q -> q.withParent(parent));
  }

  static QuestGraph detached(QuestGraph g, UUID child) {
    return replace(g, child, q -> q.withParent(null));
  }

  static QuestGraph swapped(QuestGraph g, UUID container, UUID descendant) {
    QuestData placeQuest = g.get(g.get(container).parentId());
    UUID place = placeQuest == null ? null : placeQuest.id();
    QuestGraph moved = replace(g, descendant, q -> q.withParent(place));
    return replace(moved, container, q -> q.withParent(descendant));
  }

  static QuestGraph withPrerequisite(QuestGraph g, UUID quest, UUID prerequisite) {
    return replace(
        g,
        quest,
        q -> {
          List<UUID> prerequisites = new ArrayList<>(q.prerequisites());
          prerequisites.add(prerequisite);
          return q.withPrerequisites(List.copyOf(prerequisites));
        });
  }

  /**
   * A random forest of {@code size} quests named {@code q0..}, owned by LEO or MAX, with random
   * visibilities and a random role for JUJU on about half of them, then shaped by 150 random
   * attach, prerequisite, detach and swap attempts, each applied only when {@link LinkRules}
   * accepts it.
   */
  static QuestGraph randomForest(Random random, int size, int limit) {
    List<UUID> actors = List.of(LEO, MAX, JUJU);
    List<Visibility> visibilities = List.of(Visibility.PRIVATE, Visibility.CLOSED, Visibility.OPEN);
    List<Role> roles = List.of(Role.READ, Role.WRITE, Role.OWNER);
    List<QuestBuilder> builders = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      QuestBuilder b =
          quest("q" + i)
              .owner(actors.get(random.nextInt(2)))
              .visibility(visibilities.get(random.nextInt(3)));
      if (random.nextBoolean()) b.role(JUJU, roles.get(random.nextInt(3)));
      builders.add(b);
    }
    QuestGraph g = graph(builders.toArray(QuestBuilder[]::new));
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
    return g;
  }

  /** A quest under construction. Private and owned by {@link #LEO} unless told otherwise. */
  static final class QuestBuilder {
    private final String title;
    private UUID owner = LEO;
    private Visibility visibility = Visibility.PRIVATE;
    private final List<ContributorData> people = new ArrayList<>();
    private UUID parent;
    private final List<UUID> prerequisites = new ArrayList<>();
    private long completedAt;
    private boolean autoJoin;
    private boolean rich;
    private Privatized privatized;
    private String content;
    private long lastModified = 1L;

    private QuestBuilder(String title) {
      this.title = title;
    }

    QuestBuilder owner(UUID player) {
      this.owner = player;
      return this;
    }

    QuestBuilder role(UUID player, Role role) {
      people.add(new ContributorData(player, "p" + player.getLeastSignificantBits(), role));
      return this;
    }

    QuestBuilder reader(UUID player) {
      return role(player, Role.READ);
    }

    QuestBuilder writer(UUID player) {
      return role(player, Role.WRITE);
    }

    QuestBuilder coOwner(UUID player) {
      return role(player, Role.OWNER);
    }

    QuestBuilder open() {
      this.visibility = Visibility.OPEN;
      return this;
    }

    QuestBuilder closed() {
      this.visibility = Visibility.CLOSED;
      return this;
    }

    QuestBuilder visibility(Visibility visibility) {
      this.visibility = visibility;
      return this;
    }

    /** Puts the quest inside the quest with this title. */
    QuestBuilder in(String parentTitle) {
      this.parent = id(parentTitle);
      return this;
    }

    /** The quest waits on the quests with these titles. */
    QuestBuilder after(String... prerequisiteTitles) {
      for (String t : prerequisiteTitles) prerequisites.add(id(t));
      return this;
    }

    QuestBuilder completed() {
      this.completedAt = 1_000L;
      return this;
    }

    /** Ticks auto-join: the prerequisites' players are added when the quest unlocks. */
    QuestBuilder autoJoin() {
      this.autoJoin = true;
      return this;
    }

    /**
     * Fills everything a locked view must withhold: description, tags, a pin and second corner, a
     * polygon, a map and auto-join.
     */
    QuestBuilder rich() {
      this.rich = true;
      return this;
    }

    /** Replaces the description, rich or not. */
    QuestBuilder content(String content) {
      this.content = content;
      return this;
    }

    QuestBuilder lastModified(long lastModified) {
      this.lastModified = lastModified;
      return this;
    }

    QuestBuilder privatized(Privatized privatized) {
      this.privatized = privatized;
      return this;
    }

    QuestData build() {
      return new QuestData(
          id(title),
          title,
          content != null ? content : rich ? "the text of " + title : "",
          owner,
          "p" + owner.getLeastSignificantBits(),
          visibility,
          List.copyOf(people),
          lastModified,
          rich ? new CoordinatesData(1, 64, 2) : null,
          rich,
          rich ? new CoordinatesData(10, 64, 20) : null,
          rich ? "world" : null,
          rich ? List.of("tag of " + title) : List.of(),
          rich
              ? List.of(new PointData(1, 2), new PointData(10, 2), new PointData(10, 20))
              : List.of(),
          completedAt,
          completedAt == 0L ? null : owner,
          completedAt == 0L ? null : "p" + owner.getLeastSignificantBits(),
          parent,
          List.copyOf(prerequisites),
          false,
          false,
          false,
          rich || autoJoin,
          false,
          privatized);
    }
  }
}
