package com.disqt.disquests.common.graph;

import com.disqt.disquests.common.model.ContributorData;
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
    UUID place = g.get(container).parentId();
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

  /** A quest under construction. Private and owned by {@link #LEO} unless told otherwise. */
  static final class QuestBuilder {
    private final String title;
    private UUID owner = LEO;
    private Visibility visibility = Visibility.PRIVATE;
    private final List<ContributorData> people = new ArrayList<>();
    private UUID parent;
    private final List<UUID> prerequisites = new ArrayList<>();
    private long completedAt;

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

    QuestData build() {
      QuestData base =
          new QuestData(
              id(title),
              title,
              "",
              owner,
              "p" + owner.getLeastSignificantBits(),
              visibility,
              List.copyOf(people),
              1L,
              null,
              false,
              null,
              null,
              List.of(),
              List.of(),
              completedAt,
              completedAt == 0L ? null : owner,
              null);
      return base.withParent(parent).withPrerequisites(List.copyOf(prerequisites));
    }
  }
}
