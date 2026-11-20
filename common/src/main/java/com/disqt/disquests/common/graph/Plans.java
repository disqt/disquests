package com.disqt.disquests.common.graph;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.PersonOp;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a delete, a completion, an unlock or a people change must do, worked out from the graph
 * alone. Nothing here changes the graph or checks the actor's rights to start the action; the
 * caller checks those and then applies exactly what is returned.
 */
public final class Plans {

  /** Stands for every player who holds no role on any quest. */
  private static final UUID NOBODY = new UUID(0x6e6f626f6479L, 0x6e6f626f6479L);

  private Plans() {}

  /**
   * What deleting a quest does. {@code reparent} maps each kept quest that moves to its new parent,
   * empty for top level. {@code privatize} lists the moved quests set to Private so their audience
   * does not widen. {@code prerequisitesToRemove} lists, per surviving quest, the deleted
   * prerequisites that were completed; unfinished ones stay as dangling ids and keep it locked.
   */
  public record DeletePlan(
      Set<UUID> deleted,
      Map<UUID, Optional<UUID>> reparent,
      Map<UUID, PrivatizeInfo> privatize,
      Map<UUID, List<UUID>> prerequisitesToRemove) {}

  /**
   * Why a kept quest was set to Private: the setting it had, the quest it sat in and who deleted
   * that, and which of its owners could see that quest.
   */
  public record PrivatizeInfo(
      Visibility previous, String deletedTitle, UUID deletedBy, Set<UUID> ownersWhoSawIt) {}

  /**
   * A quest that just unlocked: who is added to it with Read, who is told, and who also gets it
   * marked as new. ({@code toNotify} because a record component cannot be named {@code notify}.)
   */
  public record Unlock(
      UUID quest, List<UUID> autoJoin, List<UUID> toNotify, List<UUID> followers) {}

  /** Subquests the viewer owns, and the other ones they can see. */
  public record Counts(int mine, int others) {}

  /**
   * A quest's people after a list of changes, as {@link QuestData#withPeople} takes them: {@code
   * people} is everyone but {@code displayOwner}. On a refusal, the quest's people as they were.
   */
  public record PeopleResult(
      List<ContributorData> people, UUID displayOwner, Optional<Refusal> refusal) {}

  // --- delete ---

  /**
   * Deletes the quest and, when {@code alsoMine}, every descendant the actor owns along a path they
   * can see; one beyond a quest hidden from them is kept. Kept quests whose parent goes move to the
   * nearest surviving ancestor, or to top level. A moved quest that any of {@code knownPlayers}, or
   * a player with no role anywhere, would see after the move but not before is set to Private.
   */
  public static DeletePlan delete(
      QuestGraph g, UUID actor, UUID quest, boolean alsoMine, Collection<UUID> knownPlayers) {
    Set<UUID> deleted = new LinkedHashSet<>();
    deleted.add(quest);
    if (alsoMine) {
      for (QuestData d : visibleDescendants(g, actor, quest)) {
        if (d.roleOf(actor) == Role.OWNER) deleted.add(d.id());
      }
    }

    Map<UUID, Optional<UUID>> reparent = new LinkedHashMap<>();
    for (QuestData q : g.all()) {
      if (deleted.contains(q.id()) || !deleted.contains(q.parentId())) continue;
      reparent.put(q.id(), nearestSurvivor(g, q.id(), deleted));
    }

    Map<UUID, List<UUID>> prerequisitesToRemove = new LinkedHashMap<>();
    for (QuestData q : g.all()) {
      if (deleted.contains(q.id())) continue;
      List<UUID> gone = new ArrayList<>();
      for (UUID p : q.prerequisites()) {
        QuestData prerequisite = g.get(p);
        if (deleted.contains(p) && prerequisite != null && prerequisite.isCompleted()) gone.add(p);
      }
      if (!gone.isEmpty()) prerequisitesToRemove.put(q.id(), List.copyOf(gone));
    }

    Set<UUID> players = new LinkedHashSet<>(knownPlayers);
    players.add(NOBODY);
    QuestGraph after = afterDelete(g, deleted, reparent, prerequisitesToRemove);
    Map<UUID, PrivatizeInfo> privatize = new LinkedHashMap<>();
    // Ancestors first, so a quest that moves under another moved quest sees it already settled.
    List<UUID> moved = new ArrayList<>(reparent.keySet());
    moved.sort(Comparator.comparingInt(after::depth));
    for (UUID id : moved) {
      QuestData q = g.get(id);
      if (q.visibility() == Visibility.PRIVATE) continue;
      boolean widens = false;
      for (UUID p : players) {
        if (after.canSee(p, id) && !g.canSee(p, id)) {
          widens = true;
          break;
        }
      }
      if (!widens) continue;
      QuestData deletedParent = g.get(q.parentId());
      Set<UUID> sawIt = new LinkedHashSet<>();
      for (UUID owner : q.owners()) {
        if (g.canSee(owner, deletedParent.id())) sawIt.add(owner);
      }
      privatize.put(
          id, new PrivatizeInfo(q.visibility(), deletedParent.title(), actor, Set.copyOf(sawIt)));
      after = withVisibility(after, id, Visibility.PRIVATE);
    }

    return new DeletePlan(
        Set.copyOf(deleted),
        Map.copyOf(reparent),
        Map.copyOf(privatize),
        Map.copyOf(prerequisitesToRemove));
  }

  /** The nearest ancestor of the quest that is not deleted, empty for top level. */
  private static Optional<UUID> nearestSurvivor(QuestGraph g, UUID id, Set<UUID> deleted) {
    for (QuestData a : g.ancestors(id)) {
      if (!deleted.contains(a.id())) return Optional.of(a.id());
    }
    return Optional.empty();
  }

  private static QuestGraph afterDelete(
      QuestGraph g,
      Set<UUID> deleted,
      Map<UUID, Optional<UUID>> reparent,
      Map<UUID, List<UUID>> prerequisitesToRemove) {
    List<QuestData> kept = new ArrayList<>();
    for (QuestData q : g.all()) {
      if (deleted.contains(q.id())) continue;
      QuestData k = q;
      Optional<UUID> parent = reparent.get(q.id());
      if (parent != null) k = k.withParent(parent.orElse(null));
      List<UUID> gone = prerequisitesToRemove.get(q.id());
      if (gone != null) {
        List<UUID> prerequisites = new ArrayList<>(k.prerequisites());
        prerequisites.removeAll(gone);
        k = k.withPrerequisites(List.copyOf(prerequisites));
      }
      kept.add(k);
    }
    return QuestGraph.of(kept);
  }

  private static QuestGraph withVisibility(QuestGraph g, UUID id, Visibility visibility) {
    List<QuestData> quests = new ArrayList<>();
    for (QuestData q : g.all()) {
      quests.add(q.id().equals(id) ? q.withVisibility(visibility) : q);
    }
    return QuestGraph.of(quests);
  }

  // --- complete ---

  /**
   * The quest, then, when {@code alsoMine}, every unfinished descendant the actor owns along a path
   * they can see.
   */
  public static List<UUID> completeTargets(QuestGraph g, UUID actor, UUID quest, boolean alsoMine) {
    List<UUID> targets = new ArrayList<>();
    targets.add(quest);
    if (alsoMine) {
      for (QuestData d : visibleDescendants(g, actor, quest)) {
        if (d.roleOf(actor) == Role.OWNER && !d.isCompleted()) targets.add(d.id());
      }
    }
    return targets;
  }

  // --- unlock ---

  /**
   * The quests locked in {@code before} and neither locked nor completed in {@code after}. For
   * each, auto-join (Open quests with auto-join ticked only) adds the players holding a role on a
   * prerequisite that one of the quest's owners can see, if they can see the quest and hold no role
   * on it. {@code toNotify} is the {@code players} who can see the quest once they are added, and
   * {@code followers} those of them who hold a role on one of its prerequisites.
   */
  public static List<Unlock> unlocks(
      QuestGraph before, QuestGraph after, Collection<UUID> players) {
    List<Unlock> unlocks = new ArrayList<>();
    for (QuestData q : after.all()) {
      if (before.get(q.id()) == null) continue;
      if (!before.locked(q.id()) || after.locked(q.id()) || q.isCompleted()) continue;

      List<UUID> autoJoin = autoJoinCandidates(after, q);
      QuestGraph joined = autoJoin.isEmpty() ? after : withReaders(after, q, autoJoin);
      List<UUID> toNotify = new ArrayList<>();
      List<UUID> followers = new ArrayList<>();
      for (UUID p : new LinkedHashSet<>(players)) {
        if (!joined.canSee(p, q.id())) continue;
        toNotify.add(p);
        if (holdsRoleOnAPrerequisite(after, q, p)) followers.add(p);
      }
      unlocks.add(
          new Unlock(q.id(), List.copyOf(autoJoin), List.copyOf(toNotify), List.copyOf(followers)));
    }
    return unlocks;
  }

  private static List<UUID> autoJoinCandidates(QuestGraph g, QuestData q) {
    if (!q.autoJoin() || q.visibility() != Visibility.OPEN) return List.of();
    Set<UUID> candidates = new LinkedHashSet<>();
    for (UUID id : q.prerequisites()) {
      QuestData prerequisite = g.get(id);
      if (prerequisite == null || !seenByAnOwner(g, q, id)) continue;
      for (UUID p : holders(prerequisite)) {
        if (q.roleOf(p) == null && g.canSee(p, q.id())) candidates.add(p);
      }
    }
    return List.copyOf(candidates);
  }

  private static boolean seenByAnOwner(QuestGraph g, QuestData q, UUID id) {
    for (UUID owner : q.owners()) {
      if (g.canSee(owner, id)) return true;
    }
    return false;
  }

  private static boolean holdsRoleOnAPrerequisite(QuestGraph g, QuestData q, UUID player) {
    for (UUID id : q.prerequisites()) {
      QuestData prerequisite = g.get(id);
      if (prerequisite != null && prerequisite.roleOf(player) != null) return true;
    }
    return false;
  }

  /** The display owner, then every contributor, in list order. */
  private static List<UUID> holders(QuestData q) {
    List<UUID> holders = new ArrayList<>();
    holders.add(q.ownerUuid());
    for (ContributorData c : q.contributors()) holders.add(c.uuid());
    return holders;
  }

  private static QuestGraph withReaders(QuestGraph g, QuestData q, List<UUID> readers) {
    List<ContributorData> people = new ArrayList<>(q.contributors());
    for (UUID r : readers) people.add(new ContributorData(r, null, Role.READ));
    QuestData joined = q.withContributors(List.copyOf(people));
    List<QuestData> quests = new ArrayList<>();
    for (QuestData other : g.all()) quests.add(other.id().equals(q.id()) ? joined : other);
    return QuestGraph.of(quests);
  }

  // --- subquest counts ---

  /**
   * The quest's descendants the viewer can see along a visible path, only the unfinished ones when
   * {@code unfinishedOnly}: those the viewer owns, and the rest.
   */
  public static Counts subquestCounts(
      QuestGraph g, UUID viewer, UUID quest, boolean unfinishedOnly) {
    int mine = 0;
    int others = 0;
    for (QuestData d : visibleDescendants(g, viewer, quest)) {
      if (unfinishedOnly && d.isCompleted()) continue;
      if (d.roleOf(viewer) == Role.OWNER) mine++;
      else others++;
    }
    return new Counts(mine, others);
  }

  // --- people ---

  /**
   * Applies the changes in order, all or none. SET_ROLE on a player without a role adds them at the
   * end, with no name (the caller fills it in); REMOVE and REMOVE_WITH_MY_SUBQUESTS both take the
   * player off this quest, and the subquests are the caller's job. Refused with {@link
   * Refusal#NEED_OWNER} when the actor is not an owner, and {@link Refusal#LAST_OWNER} when no
   * owner would remain. The display owner stays if still an owner, otherwise the first remaining
   * owner takes that place.
   */
  public static PeopleResult people(QuestData quest, UUID actor, List<PersonOp> ops) {
    for (PersonOp op : ops) {
      if (op.kind() == PersonOp.Kind.SET_ROLE && op.role() == null) {
        throw new IllegalArgumentException("SET_ROLE without a role for " + op.player());
      }
    }
    if (quest.roleOf(actor) != Role.OWNER) return refused(quest, Refusal.NEED_OWNER);

    List<ContributorData> people = new ArrayList<>();
    people.add(new ContributorData(quest.ownerUuid(), quest.ownerName(), Role.OWNER));
    for (ContributorData c : quest.contributors()) {
      if (!c.uuid().equals(quest.ownerUuid())) people.add(c);
    }
    for (PersonOp op : ops) {
      int at = indexOf(people, op.player());
      switch (op.kind()) {
        case SET_ROLE -> {
          if (at < 0) people.add(new ContributorData(op.player(), null, op.role()));
          else people.set(at, new ContributorData(op.player(), people.get(at).name(), op.role()));
        }
        case REMOVE, REMOVE_WITH_MY_SUBQUESTS -> {
          if (at >= 0) people.remove(at);
        }
      }
    }

    UUID displayOwner = null;
    for (ContributorData c : people) {
      if (c.role() != Role.OWNER) continue;
      if (c.uuid().equals(quest.ownerUuid())) {
        displayOwner = c.uuid();
        break;
      }
      if (displayOwner == null) displayOwner = c.uuid();
    }
    if (displayOwner == null) return refused(quest, Refusal.LAST_OWNER);

    List<ContributorData> rest = new ArrayList<>();
    for (ContributorData c : people) {
      if (!c.uuid().equals(displayOwner)) rest.add(c);
    }
    return new PeopleResult(List.copyOf(rest), displayOwner, Optional.empty());
  }

  private static int indexOf(List<ContributorData> people, UUID player) {
    for (int i = 0; i < people.size(); i++) {
      if (people.get(i).uuid().equals(player)) return i;
    }
    return -1;
  }

  private static PeopleResult refused(QuestData quest, Refusal refusal) {
    return new PeopleResult(quest.contributors(), quest.ownerUuid(), Optional.of(refusal));
  }

  // --- sharing a role down the tree ---

  /**
   * The role the player holds on the quest, for each descendant the actor owns along a path they
   * can see (completed ones included) where the player holds a lower role or none. Empty when the
   * player holds no role on the quest.
   */
  public static Map<UUID, Role> roleDown(QuestGraph g, UUID actor, UUID quest, UUID player) {
    Role role = g.get(quest).roleOf(player);
    if (role == null) return Map.of();
    Map<UUID, Role> changes = new LinkedHashMap<>();
    for (QuestData d : visibleDescendants(g, actor, quest)) {
      if (d.roleOf(actor) != Role.OWNER) continue;
      Role current = d.roleOf(player);
      if (current == null || !current.atLeast(role)) changes.put(d.id(), role);
    }
    return changes;
  }

  /**
   * The actor's subquests of the quest, as every dialog means them: the descendants the actor owns
   * along a path they can see, completed ones included, nearest first.
   */
  public static List<UUID> mySubquests(QuestGraph g, UUID actor, UUID quest) {
    List<UUID> mine = new ArrayList<>();
    for (QuestData d : visibleDescendants(g, actor, quest)) {
      if (d.roleOf(actor) == Role.OWNER) mine.add(d.id());
    }
    return mine;
  }

  /**
   * The quest's descendants as the player knows them: those where every quest on the path down from
   * {@code quest}, the descendant included, is visible to the player. Level by level, nearest
   * first, at most {@link QuestGraph#MAX_WALK} levels.
   */
  private static List<QuestData> visibleDescendants(QuestGraph g, UUID player, UUID quest) {
    List<QuestData> found = new ArrayList<>();
    Set<UUID> seen = new LinkedHashSet<>();
    List<QuestData> level = g.children(quest);
    for (int depth = 0; !level.isEmpty() && depth < QuestGraph.MAX_WALK; depth++) {
      List<QuestData> next = new ArrayList<>();
      for (QuestData q : level) {
        if (!seen.add(q.id()) || !g.canSee(player, q.id())) continue;
        found.add(q);
        next.addAll(g.children(q.id()));
      }
      level = next;
    }
    return found;
  }
}
