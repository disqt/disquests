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
public final class TreeFixtures {

  public static final UUID LEO = new UUID(0, 1);
  public static final UUID MAX = new UUID(0, 2);
  public static final UUID JUJU = new UUID(0, 3);
  public static final UUID ANNA = new UUID(0, 4);

  private TreeFixtures() {}

  /** The fixed id of the quest with this title. */
  public static UUID id(String title) {
    return UUID.nameUUIDFromBytes(("quest:" + title).getBytes(StandardCharsets.UTF_8));
  }

  public static List<UUID> ids(String... titles) {
    return Arrays.stream(titles).map(TreeFixtures::id).toList();
  }

  public static Q quest(String title) {
    return new Q(title);
  }

  public static QuestGraph graph(Q... quests) {
    return QuestGraph.of(Arrays.stream(quests).map(Q::build).toList());
  }

  /** Stands for a parent the player cannot see. */
  public static final UUID HIDDEN = new UUID(0, 0);

  /** Every quest's parent, by id: {@link #HIDDEN} for one the player cannot see. */
  public static Map<UUID, UUID> parents(QuestGraph g) {
    Map<UUID, UUID> parents = new HashMap<>();
    for (QuestData q : g.all()) {
      parents.put(q.id(), q.parentId() == null && q.hiddenParent() ? HIDDEN : q.parentId());
    }
    return parents;
  }

  /**
   * Applies the ops one by one as the server does: each checked with {@link LinkRules} against the
   * tree as it is then. Fails the test on the first refused op.
   */
  public static QuestGraph replay(QuestGraph g, UUID actor, int maxDepth, List<TreeOp> ops) {
    for (TreeOp op : ops) {
      Optional<Refusal> refused =
          switch (op.kind()) {
            case ATTACH -> LinkRules.attach(g, actor, op.quest(), op.target(), maxDepth);
            case DETACH -> detachRefusal(g, actor, op.quest());
            case SWAP -> LinkRules.swap(g, actor, op.quest(), op.target(), maxDepth);
          };
      if (refused.isPresent()) throw new AssertionError(op + " refused: " + refused.get());
      g = applied(g, op);
    }
    return g;
  }

  /**
   * The server detaches a quest from a parent the player cannot see; on the client graph that is a
   * quest with no parent and the hidden-parent flag, for which only Owner and completion matter.
   */
  private static Optional<Refusal> detachRefusal(QuestGraph g, UUID actor, UUID quest) {
    QuestData q = g.get(quest);
    if (q == null || q.parentId() != null || !q.hiddenParent()) {
      return LinkRules.detach(g, actor, quest);
    }
    if (q.roleOf(actor) != Role.OWNER) return Optional.of(Refusal.NEED_OWNER);
    if (q.isCompleted()) return Optional.of(Refusal.COMPLETED);
    return Optional.empty();
  }

  /** As the server applies it; a hidden place is kept as the hidden-parent flag. */
  private static QuestGraph applied(QuestGraph g, TreeOp op) {
    Map<UUID, UUID> newParents = new HashMap<>();
    switch (op.kind()) {
      case ATTACH -> newParents.put(op.quest(), op.target());
      case DETACH -> newParents.put(op.quest(), null);
      case SWAP -> {
        QuestData container = g.get(op.quest());
        QuestData place = g.get(container.parentId());
        newParents.put(
            op.target(), place != null ? place.id() : container.hiddenParent() ? HIDDEN : null);
        newParents.put(op.quest(), op.target());
      }
    }
    List<QuestData> quests = new ArrayList<>();
    for (QuestData q : g.all()) {
      if (!newParents.containsKey(q.id())) {
        quests.add(q);
        continue;
      }
      UUID parent = newParents.get(q.id());
      quests.add(
          HIDDEN.equals(parent)
              ? q.withParent(null).withHiddenParent(true)
              : q.withParent(parent).withHiddenParent(false));
    }
    return QuestGraph.of(quests);
  }

  /** A quest under construction: Open, owned by {@link #LEO}, active, unless told otherwise. */
  public static final class Q {
    private final String title;
    private UUID owner = LEO;
    private Visibility visibility = Visibility.OPEN;
    private final List<ContributorData> people = new ArrayList<>();
    private UUID parent;
    private final List<UUID> prerequisites = new ArrayList<>();
    private boolean hiddenPrerequisites;
    private boolean deletedPrerequisite;
    private boolean hiddenParent;
    private boolean autoJoin;
    private long completedAt;
    private long lastModified = 1L;
    private boolean locked;
    private boolean placed;
    private String content = "";
    private final List<String> tags = new ArrayList<>();

    private Q(String title) {
      this.title = title;
    }

    public Q owner(UUID player) {
      this.owner = player;
      return this;
    }

    public Q role(UUID player, Role role) {
      people.add(new ContributorData(player, "p" + player.getLeastSignificantBits(), role));
      return this;
    }

    public Q reader(UUID player) {
      return role(player, Role.READ);
    }

    public Q writer(UUID player) {
      return role(player, Role.WRITE);
    }

    public Q coOwner(UUID player) {
      return role(player, Role.OWNER);
    }

    public Q visibility(Visibility visibility) {
      this.visibility = visibility;
      return this;
    }

    public Q privateQuest() {
      return visibility(Visibility.PRIVATE);
    }

    public Q closed() {
      return visibility(Visibility.CLOSED);
    }

    public Q in(String parentTitle) {
      this.parent = id(parentTitle);
      return this;
    }

    public Q after(String... titles) {
      for (String t : titles) prerequisites.add(id(t));
      return this;
    }

    public Q hiddenPrerequisites() {
      this.hiddenPrerequisites = true;
      return this;
    }

    public Q deletedPrerequisite() {
      this.deletedPrerequisite = true;
      return this;
    }

    public Q hiddenParent() {
      this.hiddenParent = true;
      return this;
    }

    public Q autoJoin() {
      this.autoJoin = true;
      return this;
    }

    public Q completed(long at) {
      this.completedAt = at;
      return this;
    }

    public Q completed() {
      return completed(1_000L);
    }

    public Q modified(long at) {
      this.lastModified = at;
      return this;
    }

    /** A locked view: no content, no time of last change, the flag set. */
    public Q locked() {
      this.locked = true;
      this.lastModified = 0L;
      return this;
    }

    public Q placed() {
      this.placed = true;
      return this;
    }

    public Q content(String content) {
      this.content = content;
      return this;
    }

    public Q tags(String... tags) {
      this.tags.addAll(List.of(tags));
      return this;
    }

    public QuestData build() {
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
          autoJoin,
          locked,
          null);
    }
  }
}
