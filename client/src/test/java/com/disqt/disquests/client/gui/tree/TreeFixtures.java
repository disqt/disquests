package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.common.graph.LinkRules;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.TreeOp;
import com.disqt.disquests.common.model.Visibility;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds client-side quest graphs for tests, the way the server sends them to one player: quests
 * named by their title, whose id is derived from that title, and players with fixed UUIDs.
 */
final class TreeFixtures {

  static final UUID LEO = new UUID(0, 1);
  static final UUID MAX = new UUID(0, 2);
  static final UUID JUJU = new UUID(0, 3);
  static final UUID ANNA = new UUID(0, 4);

  private TreeFixtures() {}

  /** The fixed id of the quest with this title. */
  static UUID id(String title) {
    return UUID.nameUUIDFromBytes(("quest:" + title).getBytes(StandardCharsets.UTF_8));
  }

  static List<UUID> ids(String... titles) {
    return Arrays.stream(titles).map(TreeFixtures::id).toList();
  }

  static Q quest(String title) {
    return new Q(title);
  }

  static QuestGraph graph(Q... quests) {
    return QuestGraph.of(Arrays.stream(quests).map(Q::build).toList());
  }

  /** Every quest's parent, by id. */
  static Map<UUID, UUID> parents(QuestGraph g) {
    Map<UUID, UUID> parents = new HashMap<>();
    for (QuestData q : g.all()) parents.put(q.id(), q.parentId());
    return parents;
  }

  /**
   * Applies the ops one by one as the server does: each checked with {@link LinkRules} against the
   * tree as it is then. Fails the test on the first refused op.
   */
  static QuestGraph replay(QuestGraph g, UUID actor, int maxDepth, List<TreeOp> ops) {
    for (TreeOp op : ops) {
      Optional<Refusal> refused =
          switch (op.kind()) {
            case ATTACH -> LinkRules.attach(g, actor, op.quest(), op.target(), maxDepth);
            case DETACH -> LinkRules.detach(g, actor, op.quest());
            case SWAP -> LinkRules.swap(g, actor, op.quest(), op.target(), maxDepth);
          };
      if (refused.isPresent()) throw new AssertionError(op + " refused: " + refused.get());
      g = applied(g, op);
    }
    return g;
  }

  private static QuestGraph applied(QuestGraph g, TreeOp op) {
    Map<UUID, UUID> newParents = new HashMap<>();
    switch (op.kind()) {
      case ATTACH -> newParents.put(op.quest(), op.target());
      case DETACH -> newParents.put(op.quest(), null);
      case SWAP -> {
        QuestData place = g.get(g.get(op.quest()).parentId());
        newParents.put(op.target(), place == null ? null : place.id());
        newParents.put(op.quest(), op.target());
      }
    }
    List<QuestData> quests = new ArrayList<>();
    for (QuestData q : g.all()) {
      quests.add(newParents.containsKey(q.id()) ? q.withParent(newParents.get(q.id())) : q);
    }
    return QuestGraph.of(quests);
  }

  /** A quest under construction: Open, owned by {@link #LEO}, active, unless told otherwise. */
  static final class Q {
    private final String title;
    private UUID owner = LEO;
    private Visibility visibility = Visibility.OPEN;
    private final List<ContributorData> people = new ArrayList<>();
    private UUID parent;
    private final List<UUID> prerequisites = new ArrayList<>();
    private boolean hiddenPrerequisites;
    private boolean deletedPrerequisite;
    private boolean hiddenParent;
    private long completedAt;
    private long lastModified = 1L;
    private boolean locked;
    private boolean placed;
    private String content = "";
    private final List<String> tags = new ArrayList<>();

    private Q(String title) {
      this.title = title;
    }

    Q owner(UUID player) {
      this.owner = player;
      return this;
    }

    Q role(UUID player, Role role) {
      people.add(new ContributorData(player, "p" + player.getLeastSignificantBits(), role));
      return this;
    }

    Q reader(UUID player) {
      return role(player, Role.READ);
    }

    Q writer(UUID player) {
      return role(player, Role.WRITE);
    }

    Q coOwner(UUID player) {
      return role(player, Role.OWNER);
    }

    Q visibility(Visibility visibility) {
      this.visibility = visibility;
      return this;
    }

    Q privateQuest() {
      return visibility(Visibility.PRIVATE);
    }

    Q closed() {
      return visibility(Visibility.CLOSED);
    }

    Q in(String parentTitle) {
      this.parent = id(parentTitle);
      return this;
    }

    Q after(String... titles) {
      for (String t : titles) prerequisites.add(id(t));
      return this;
    }

    Q hiddenPrerequisites() {
      this.hiddenPrerequisites = true;
      return this;
    }

    Q deletedPrerequisite() {
      this.deletedPrerequisite = true;
      return this;
    }

    Q hiddenParent() {
      this.hiddenParent = true;
      return this;
    }

    Q completed(long at) {
      this.completedAt = at;
      return this;
    }

    Q completed() {
      return completed(1_000L);
    }

    Q modified(long at) {
      this.lastModified = at;
      return this;
    }

    /** A locked view: no content, no time of last change, the flag set. */
    Q locked() {
      this.locked = true;
      this.lastModified = 0L;
      return this;
    }

    Q placed() {
      this.placed = true;
      return this;
    }

    Q content(String content) {
      this.content = content;
      return this;
    }

    Q tags(String... tags) {
      this.tags.addAll(List.of(tags));
      return this;
    }

    QuestData build() {
      return new QuestData(
          id(title),
          title,
          content,
          owner,
          "p" + owner.getLeastSignificantBits(),
          visibility,
          List.copyOf(people),
          lastModified,
          placed ? new CoordinatesData(1, 64, 2) : null,
          false,
          null,
          null,
          List.copyOf(tags),
          List.of(),
          completedAt,
          completedAt == 0L ? null : owner,
          completedAt == 0L ? null : "p" + owner.getLeastSignificantBits(),
          parent,
          List.copyOf(prerequisites),
          hiddenPrerequisites,
          deletedPrerequisite,
          hiddenParent,
          false,
          locked,
          null);
    }
  }
}
