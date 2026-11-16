package com.disqt.disquests.common.graph;

import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Whether a player may change a quest's parent or prerequisites. Each check only validates against
 * the graph as it is; applying the change is up to the caller.
 *
 * <p>Rights are checked first, so a player without them learns nothing about the shape around the
 * quests. A quest the player can neither see nor act on is refused as {@link Refusal#NOT_VISIBLE},
 * the same as a quest that does not exist.
 */
public final class LinkRules {

  /** At most this many prerequisites per quest. */
  public static final int MAX_PREREQUISITES = 8;

  private LinkRules() {}

  /**
   * Puts {@code child} inside {@code parent}, or moves it there when it already has a parent. Needs
   * Owner on the child and Write on the parent. {@code maxDepth} 0 means no limit (walks still stop
   * at {@link QuestGraph#MAX_WALK} levels).
   */
  public static Optional<Refusal> attach(
      QuestGraph g, UUID actor, UUID child, UUID parent, int maxDepth) {
    if (child.equals(parent)) return Optional.of(Refusal.SELF);
    Optional<Refusal> rights = firstOf(needOwner(g, actor, child), needWrite(g, actor, parent));
    if (rights.isPresent()) return rights;
    QuestData c = g.get(child);
    QuestData p = g.get(parent);
    if (parent.equals(c.parentId())) return Optional.of(Refusal.ALREADY_THERE);
    if (c.isCompleted()) return Optional.of(Refusal.COMPLETED);
    if (p.isCompleted()) return Optional.of(Refusal.TARGET_COMPLETED);
    QuestGraph after = withParents(g, Map.of(child, parent));
    return shape(after, List.of(child), child, maxDepth);
  }

  /**
   * Takes {@code child} out of its parent, to the top level. Needs Owner on the child, or Owner on
   * its parent with the child visible to the actor.
   */
  public static Optional<Refusal> detach(QuestGraph g, UUID actor, UUID child) {
    QuestData c = g.get(child);
    if (c == null) return Optional.of(Refusal.NOT_VISIBLE);
    if (!holds(c, actor, Role.OWNER)) {
      QuestData parent = g.get(c.parentId());
      if (parent == null || !holds(parent, actor, Role.OWNER)) {
        return needOwner(g, actor, child);
      }
      if (!g.canSee(actor, child)) return Optional.of(Refusal.NOT_VISIBLE);
    }
    if (c.parentId() == null) return Optional.of(Refusal.ALREADY_THERE);
    if (c.isCompleted()) return Optional.of(Refusal.COMPLETED);
    return Optional.empty();
  }

  /**
   * {@code descendant}, somewhere inside {@code container}, takes the container's place, and the
   * container goes directly inside it. Needs Owner on both and Write on the container's parent if
   * it has one. The resulting shape is checked like an attach.
   */
  public static Optional<Refusal> swap(
      QuestGraph g, UUID actor, UUID container, UUID descendant, int maxDepth) {
    if (container.equals(descendant)) return Optional.of(Refusal.SELF);
    Optional<Refusal> rights =
        firstOf(needOwner(g, actor, container), needOwner(g, actor, descendant));
    if (rights.isPresent()) return rights;
    QuestData c = g.get(container);
    QuestData d = g.get(descendant);
    QuestData place = g.get(c.parentId());
    if (place != null) {
      Optional<Refusal> onPlace = needWrite(g, actor, place.id());
      if (onPlace.isPresent()) return onPlace;
    }
    if (c.isCompleted() || d.isCompleted()) return Optional.of(Refusal.COMPLETED);
    if (place != null && place.isCompleted()) return Optional.of(Refusal.TARGET_COMPLETED);
    Map<UUID, UUID> parents = new HashMap<>();
    parents.put(descendant, c.parentId());
    parents.put(container, descendant);
    QuestGraph after = withParents(g, parents);
    return shape(after, List.of(descendant, container), descendant, maxDepth);
  }

  /**
   * Makes {@code quest} wait on {@code prerequisite}. Needs Owner on the quest and the prerequisite
   * visible to the actor; no role on the prerequisite is needed.
   */
  public static Optional<Refusal> prerequisite(
      QuestGraph g, UUID actor, UUID quest, UUID prerequisite) {
    if (quest.equals(prerequisite)) return Optional.of(Refusal.SELF);
    Optional<Refusal> rights = needOwner(g, actor, quest);
    if (rights.isPresent()) return rights;
    if (!g.canSee(actor, prerequisite)) {
      return Optional.of(Refusal.NOT_VISIBLE);
    }
    QuestData q = g.get(quest);
    if (q.isCompleted()) return Optional.of(Refusal.COMPLETED);
    if (q.prerequisites().contains(prerequisite)) return Optional.of(Refusal.ALREADY_THERE);
    if (q.prerequisites().size() >= MAX_PREREQUISITES) {
      return Optional.of(Refusal.TOO_MANY_PREREQUISITES);
    }
    if (ids(g.ancestors(quest)).contains(prerequisite)
        || ids(g.ancestors(prerequisite)).contains(quest)
        || waitsOn(g, prerequisite, quest)) {
      return Optional.of(Refusal.LOOP);
    }
    return Optional.empty();
  }

  // --- shape of the tree after a move ---

  /**
   * Checks the graph after a move: none of the {@code moved} quests is its own ancestor, the
   * subtree under {@code top} fits the depth limit, and no quest in it is directly linked by a
   * prerequisite to one of its ancestors.
   */
  private static Optional<Refusal> shape(
      QuestGraph after, List<UUID> moved, UUID top, int maxDepth) {
    for (UUID id : moved) {
      if (ids(after.ancestors(id)).contains(id)) return Optional.of(Refusal.LOOP);
    }
    int limit = maxDepth > 0 ? Math.min(maxDepth, QuestGraph.MAX_WALK) : QuestGraph.MAX_WALK;
    if (after.depth(top) + after.height(top) - 1 > limit) return Optional.of(Refusal.TOO_DEEP);
    List<QuestData> subtree = new ArrayList<>();
    subtree.add(after.get(top));
    subtree.addAll(after.descendants(top));
    for (QuestData q : subtree) {
      for (QuestData above : after.ancestors(q.id())) {
        if (q.prerequisites().contains(above.id()) || above.prerequisites().contains(q.id())) {
          return Optional.of(Refusal.LINKED_BY_PREREQUISITE);
        }
      }
    }
    return Optional.empty();
  }

  private static QuestGraph withParents(QuestGraph g, Map<UUID, UUID> parents) {
    List<QuestData> quests = new ArrayList<>();
    for (QuestData q : g.all()) {
      quests.add(parents.containsKey(q.id()) ? q.withParent(parents.get(q.id())) : q);
    }
    return QuestGraph.of(quests);
  }

  /** Whether {@code from} waits, directly or through other prerequisites, on {@code target}. */
  private static boolean waitsOn(QuestGraph g, UUID from, UUID target) {
    Set<UUID> seen = new HashSet<>();
    Deque<UUID> todo = new ArrayDeque<>();
    todo.push(from);
    while (!todo.isEmpty()) {
      QuestData q = g.get(todo.pop());
      if (q == null || !seen.add(q.id())) continue;
      for (UUID p : q.prerequisites()) {
        if (p.equals(target)) return true;
        todo.push(p);
      }
    }
    return false;
  }

  // --- rights ---

  /** Refuses unless the actor holds Owner on the quest. */
  private static Optional<Refusal> needOwner(QuestGraph g, UUID actor, UUID id) {
    QuestData q = g.get(id);
    if (q != null && holds(q, actor, Role.OWNER)) return Optional.empty();
    if (q == null || !g.canSee(actor, id)) return Optional.of(Refusal.NOT_VISIBLE);
    return Optional.of(Refusal.NEED_OWNER);
  }

  /** Refuses unless the actor holds Write on the quest something goes into. */
  private static Optional<Refusal> needWrite(QuestGraph g, UUID actor, UUID id) {
    QuestData q = g.get(id);
    if (q != null && holds(q, actor, Role.WRITE)) return Optional.empty();
    if (q == null || !g.canSee(actor, id)) return Optional.of(Refusal.NOT_VISIBLE);
    return Optional.of(Refusal.NEED_WRITE_ON_TARGET);
  }

  private static boolean holds(QuestData q, UUID actor, Role role) {
    Role held = q.roleOf(actor);
    return held != null && held.atLeast(role);
  }

  @SafeVarargs
  private static Optional<Refusal> firstOf(Optional<Refusal>... refusals) {
    for (Optional<Refusal> r : refusals) {
      if (r.isPresent()) return r;
    }
    return Optional.empty();
  }

  private static Set<UUID> ids(List<QuestData> quests) {
    Set<UUID> ids = new HashSet<>();
    for (QuestData q : quests) ids.add(q.id());
    return ids;
  }
}
