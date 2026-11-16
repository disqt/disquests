package com.disqt.disquests.common.graph;

import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every quest with its parent and prerequisite links, as the server stores them, and the rules
 * worked out from them: who can see a quest and whether it is locked.
 *
 * <p>A parent id that is not in the graph counts as no parent; a prerequisite id that is not in the
 * graph is a deleted prerequisite and counts as not completed. Every walk up or down the tree stops
 * after {@link #MAX_WALK} steps, so corrupt data (a parent loop) cannot hang it.
 */
public final class QuestGraph {

  /** The most steps any walk up or down the tree takes. */
  public static final int MAX_WALK = 64;

  private final Map<UUID, QuestData> byId;
  private final Map<UUID, List<QuestData>> byParent;

  private QuestGraph(Map<UUID, QuestData> byId, Map<UUID, List<QuestData>> byParent) {
    this.byId = byId;
    this.byParent = byParent;
  }

  public static QuestGraph of(Collection<QuestData> quests) {
    Map<UUID, QuestData> byId = new LinkedHashMap<>();
    for (QuestData q : quests) byId.put(q.id(), q);
    Map<UUID, List<QuestData>> byParent = new HashMap<>();
    for (QuestData q : byId.values()) {
      if (q.parentId() != null)
        byParent.computeIfAbsent(q.parentId(), k -> new ArrayList<>()).add(q);
    }
    return new QuestGraph(Collections.unmodifiableMap(byId), byParent);
  }

  /** The quest, or null when it is not in the graph. */
  public QuestData get(UUID id) {
    return id == null ? null : byId.get(id);
  }

  /** Every quest, in the order given to {@link #of}. */
  public Collection<QuestData> all() {
    return byId.values();
  }

  /** The quests directly inside this one, in the order given to {@link #of}. */
  public List<QuestData> children(UUID id) {
    List<QuestData> children = byParent.get(id);
    return children == null ? List.of() : List.copyOf(children);
  }

  /** The quest's parent, its parent's parent and so on, nearest first. */
  public List<QuestData> ancestors(UUID id) {
    List<QuestData> ancestors = new ArrayList<>();
    QuestData current = get(id);
    while (current != null && ancestors.size() < MAX_WALK) {
      QuestData parent = get(current.parentId());
      if (parent == null) break;
      ancestors.add(parent);
      current = parent;
    }
    return ancestors;
  }

  /** Every quest inside this one at any level, level by level. */
  public List<QuestData> descendants(UUID id) {
    List<QuestData> descendants = new ArrayList<>();
    for (List<QuestData> level : levelsBelow(id)) descendants.addAll(level);
    return descendants;
  }

  /** 1 for a quest with no parent. */
  public int depth(UUID id) {
    return 1 + ancestors(id).size();
  }

  /** 1 for a quest with no subquests. */
  public int height(UUID id) {
    return 1 + levelsBelow(id).size();
  }

  /** Not completed and waiting on a prerequisite that is deleted or not completed. */
  public boolean locked(UUID id) {
    QuestData q = get(id);
    if (q == null || q.isCompleted()) return false;
    for (UUID prerequisite : q.prerequisites()) {
      QuestData p = get(prerequisite);
      if (p == null || !p.isCompleted()) return true;
    }
    return false;
  }

  /** Locked, and the player holds neither Write nor Owner on the quest. */
  public boolean lockedFor(UUID player, UUID id) {
    if (!locked(id)) return false;
    Role role = get(id).roleOf(player);
    return role == null || !role.atLeast(Role.WRITE);
  }

  /**
   * The player holds a role on the quest, or it is not Private and either has no parent or sits in
   * a parent the player can see and that is not locked for them.
   */
  public boolean canSee(UUID player, UUID id) {
    QuestData q = get(id);
    for (int step = 0; q != null && step <= MAX_WALK; step++) {
      if (q.roleOf(player) != null) return true;
      if (q.visibility() == Visibility.PRIVATE) return false;
      QuestData parent = get(q.parentId());
      if (parent == null) return true;
      if (lockedFor(player, parent.id())) return false;
      q = parent;
    }
    return false;
  }

  /** The quests below this one grouped by level, nearest first, at most {@link #MAX_WALK}. */
  private List<List<QuestData>> levelsBelow(UUID id) {
    List<List<QuestData>> levels = new ArrayList<>();
    Set<UUID> seen = new HashSet<>();
    List<QuestData> level = children(id);
    while (!level.isEmpty() && levels.size() < MAX_WALK) {
      List<QuestData> fresh = new ArrayList<>();
      for (QuestData q : level) {
        if (seen.add(q.id())) fresh.add(q);
      }
      if (fresh.isEmpty()) break;
      levels.add(fresh);
      List<QuestData> next = new ArrayList<>();
      for (QuestData q : fresh) next.addAll(children(q.id()));
      level = next;
    }
    return levels;
  }
}
