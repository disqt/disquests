package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.common.graph.LinkRules;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.TreeOp;
import com.disqt.disquests.common.model.TreeOp.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The tree changes of the edit screen, held until Save. Each move is checked with {@link LinkRules}
 * against the tree as the draft has it so far (a best-effort answer: the server checks again).
 *
 * <p>The draft keeps the player's accepted moves in order. {@link #ops()} sends them minus the ones
 * made redundant by later moves: an op is dropped only when the remaining ops, replayed one by one
 * on the base tree with {@link LinkRules}, are all still accepted and give the same tree. All of a
 * quest's ops are tried together first, so a quest moved away and back sends nothing; then single
 * ops, so a quest moved twice keeps only its last move.
 *
 * <p>A quest whose parent the player cannot see shows as top level, but on the server it still has
 * that parent. The draft tracks it as "in a hidden quest", so moving it away and back out is a real
 * change, and a swap with it puts the subquest into that hidden quest, as the server does. {@link
 * #detachFromHiddenParent} takes such a quest out of the parent it cannot see.
 *
 * <p>After each change the shortened list becomes the draft's history, so the cost of shortening
 * does not grow with every move.
 */
public final class TreeDraft {

  /** Stands for a parent the player cannot see when comparing parents. */
  private static final UUID HIDDEN = new UUID(0, 0);

  /**
   * One accepted change. {@code fromHidden}: a DETACH out of a parent the player cannot see,
   * checked by {@link #hiddenDetachRefusal} instead of {@link LinkRules#detach}.
   */
  private record Step(TreeOp op, UUID actor, int maxDepth, boolean fromHidden) {}

  private final QuestGraph base;
  private final List<Step> steps = new ArrayList<>();
  private QuestGraph effective;
  private List<TreeOp> ops;

  public TreeDraft(QuestGraph base) {
    this.base = base;
    this.effective = base;
    this.ops = List.of();
  }

  /**
   * Puts {@code quest} inside {@code target}; when {@code target} is inside {@code quest} the two
   * swap places. A refusal leaves the draft as it was.
   */
  public Optional<Refusal> move(UUID quest, UUID target, UUID me, int maxDepth) {
    boolean inside = effective.descendants(quest).stream().anyMatch(q -> q.id().equals(target));
    TreeOp op = new TreeOp(inside ? Kind.SWAP : Kind.ATTACH, quest, target);
    return record(new Step(op, me, maxDepth, false));
  }

  /** Takes {@code quest} out to the top level. A refusal leaves the draft as it was. */
  public Optional<Refusal> detach(UUID quest, UUID me) {
    return record(new Step(new TreeOp(Kind.DETACH, quest, null), me, 0, false));
  }

  /**
   * Takes {@code quest} out of a parent the player cannot see (the "This quest sits inside a quest
   * you can't see" Detach button). Needs Owner on the quest, which must not be completed; refused
   * as ALREADY_THERE when the quest is not inside a hidden quest, or no longer is in this draft.
   */
  public Optional<Refusal> detachFromHiddenParent(UUID quest, UUID me) {
    return record(new Step(new TreeOp(Kind.DETACH, quest, null), me, 0, true));
  }

  /** The base tree with the draft applied. */
  public QuestGraph effective() {
    return effective;
  }

  /** The quest's place differs from the base tree's. */
  public boolean isChanged(UUID quest) {
    return !Objects.equals(parentOf(base, quest), parentOf(effective, quest));
  }

  /**
   * The quests under {@code treeRoot} in the base tree that are no longer in its tree: under the
   * top of the tree that holds {@code treeRoot} now (which is another quest after a swap at the
   * top), only the topmost of them: one line per quest the player took out. Level by level, nearest
   * first.
   */
  public List<UUID> takenOut(UUID treeRoot) {
    List<QuestData> above = effective.ancestors(treeRoot);
    UUID top = above.isEmpty() ? treeRoot : above.get(above.size() - 1).id();
    Set<UUID> now = new HashSet<>();
    now.add(top);
    for (QuestData q : effective.descendants(top)) now.add(q.id());
    Set<UUID> out = new LinkedHashSet<>();
    for (QuestData q : base.descendants(treeRoot)) {
      if (!now.contains(q.id())) out.add(q.id());
    }
    // Only the topmost: a quest that went along inside another taken-out quest is not listed.
    List<UUID> topmost = new ArrayList<>();
    for (UUID id : out) {
      if (!out.contains(effective.get(id).parentId())) topmost.add(id);
    }
    return topmost;
  }

  /** What to send on Save, in order. */
  public List<TreeOp> ops() {
    return ops;
  }

  /** Nothing to send. */
  public boolean isEmpty() {
    return ops.isEmpty();
  }

  // --- recording ---

  private Optional<Refusal> record(Step step) {
    Optional<Refusal> refusal = check(effective, step);
    if (refusal.isPresent()) return refusal;
    steps.add(step);
    effective = apply(effective, step.op());
    List<Step> kept = minimise();
    steps.clear();
    steps.addAll(kept);
    ops = steps.stream().map(Step::op).toList();
    return Optional.empty();
  }

  private static Optional<Refusal> check(QuestGraph g, Step step) {
    if (step.fromHidden()) return hiddenDetachRefusal(g, step.actor(), step.op().quest());
    TreeOp op = step.op();
    return switch (op.kind()) {
      case ATTACH -> LinkRules.attach(g, step.actor(), op.quest(), op.target(), step.maxDepth());
      case DETACH -> LinkRules.detach(g, step.actor(), op.quest());
      case SWAP -> LinkRules.swap(g, step.actor(), op.quest(), op.target(), step.maxDepth());
    };
  }

  private static Optional<Refusal> hiddenDetachRefusal(QuestGraph g, UUID actor, UUID quest) {
    QuestData q = g.get(quest);
    if (q == null) return Optional.of(Refusal.NOT_VISIBLE);
    if (q.roleOf(actor) != Role.OWNER) return Optional.of(Refusal.NEED_OWNER);
    if (q.parentId() != null || !q.hiddenParent()) return Optional.of(Refusal.ALREADY_THERE);
    if (q.isCompleted()) return Optional.of(Refusal.COMPLETED);
    return Optional.empty();
  }

  /** The tree after one op; a quest given a parent is no longer in a hidden one. */
  private static QuestGraph apply(QuestGraph g, TreeOp op) {
    Map<UUID, QuestData> changed = new HashMap<>();
    switch (op.kind()) {
      case ATTACH ->
          changed.put(
              op.quest(), g.get(op.quest()).withParent(op.target()).withHiddenParent(false));
      case DETACH ->
          changed.put(op.quest(), g.get(op.quest()).withParent(null).withHiddenParent(false));
      case SWAP -> {
        QuestData container = g.get(op.quest());
        QuestData place = g.get(container.parentId());
        boolean hiddenPlace = place == null && container.hiddenParent();
        changed.put(
            op.target(),
            g.get(op.target())
                .withParent(place == null ? null : place.id())
                .withHiddenParent(hiddenPlace));
        changed.put(op.quest(), container.withParent(op.target()).withHiddenParent(false));
      }
    }
    List<QuestData> quests = new ArrayList<>();
    for (QuestData q : g.all()) quests.add(changed.getOrDefault(q.id(), q));
    return QuestGraph.of(quests);
  }

  // --- what to send ---

  /** The recorded steps without the ones later steps made redundant. */
  private List<Step> minimise() {
    Map<UUID, UUID> wanted = parents(effective);
    if (wanted.equals(parents(base))) return List.of();
    List<Step> current = List.copyOf(steps);
    boolean shorter = true;
    while (shorter) {
      shorter = false;
      for (UUID quest : touched(current)) {
        List<Step> without = new ArrayList<>();
        for (Step s : current) {
          if (!touches(s.op(), quest)) without.add(s);
        }
        if (replaysTo(without, wanted)) {
          current = without;
          shorter = true;
          break;
        }
      }
      for (int i = 0; !shorter && i < current.size(); i++) {
        List<Step> without = new ArrayList<>(current);
        without.remove(i);
        if (replaysTo(without, wanted)) {
          current = without;
          shorter = true;
        }
      }
    }
    return current;
  }

  private boolean replaysTo(List<Step> candidate, Map<UUID, UUID> wanted) {
    QuestGraph g = base;
    for (Step step : candidate) {
      if (check(g, step).isPresent()) return false;
      g = apply(g, step.op());
    }
    return parents(g).equals(wanted);
  }

  private static Set<UUID> touched(List<Step> steps) {
    Set<UUID> quests = new LinkedHashSet<>();
    for (Step s : steps) {
      quests.add(s.op().quest());
      if (s.op().kind() == Kind.SWAP) quests.add(s.op().target());
    }
    return quests;
  }

  private static boolean touches(TreeOp op, UUID quest) {
    return op.quest().equals(quest) || (op.kind() == Kind.SWAP && op.target().equals(quest));
  }

  private static Map<UUID, UUID> parents(QuestGraph g) {
    Map<UUID, UUID> parents = new HashMap<>();
    for (QuestData q : g.all()) parents.put(q.id(), parentOf(g, q.id()));
    return parents;
  }

  /** The quest's parent, {@link #HIDDEN} for one the player cannot see, null for none. */
  private static UUID parentOf(QuestGraph g, UUID quest) {
    QuestData q = g.get(quest);
    if (q == null) return null;
    if (q.parentId() != null) return q.parentId();
    return q.hiddenParent() ? HIDDEN : null;
  }
}
