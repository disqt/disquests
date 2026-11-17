package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.common.graph.LinkRules;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the quest editor holds besides the text fields, until Save: the tree changes, the
 * visibility, the chosen prerequisites, the two "drop" flags and auto-join. {@link #toLinkChanges}
 * turns it into the save's links block, sending only what changed.
 *
 * <p>A quest not saved yet is not in the client graph; it is added to the draft's base graph as
 * given (owned by the player, no parent), so tree moves and suggestions can name it.
 */
public final class QuestEditDraft {

  /** The "This quest sits inside a quest you can't see" line of the form. */
  public enum HiddenParent {
    /** No line: the parent is visible, or there is none. */
    NONE,
    /** The line with its Detach button. */
    INSIDE,
    /** The line marked "not saved", without the button. */
    DETACHED
  }

  private final UUID questId;
  private final TreeDraft tree;
  private final UUID treeRoot;
  private final boolean hiddenParentAtStart;

  private final Visibility originalVisibility;
  private Visibility visibility;

  private final List<UUID> originalPrerequisites;
  private final List<UUID> prerequisites;
  private final boolean hiddenPrerequisites;
  private final boolean deletedPrerequisite;
  private boolean dropHidden;
  private boolean dropDeleted;

  private final boolean originalAutoJoin;
  private boolean autoJoin;

  /**
   * @param graph what the client knows
   * @param quest the edited quest as the player sees it; added to the base graph when absent
   */
  public QuestEditDraft(QuestGraph graph, QuestData quest) {
    this.questId = quest.id();
    QuestGraph base = graph;
    if (graph.get(questId) == null) {
      List<QuestData> quests = new ArrayList<>(graph.all());
      quests.add(quest);
      base = QuestGraph.of(quests);
    }
    this.tree = new TreeDraft(base);
    List<QuestData> above = base.ancestors(questId);
    this.treeRoot = above.isEmpty() ? questId : above.get(above.size() - 1).id();
    this.hiddenParentAtStart = quest.hiddenParent();

    this.originalVisibility = quest.visibility();
    this.visibility = quest.visibility();
    this.originalPrerequisites = List.copyOf(quest.prerequisites());
    this.prerequisites = new ArrayList<>(quest.prerequisites());
    this.hiddenPrerequisites = quest.hiddenPrerequisites();
    this.deletedPrerequisite = quest.deletedPrerequisite();
    this.originalAutoJoin = quest.autoJoin();
    this.autoJoin = quest.autoJoin();
  }

  public UUID questId() {
    return questId;
  }

  /** The tree changes. */
  public TreeDraft tree() {
    return tree;
  }

  // --- visibility ---

  public Visibility visibility() {
    return visibility;
  }

  public void visibility(Visibility visibility) {
    this.visibility = visibility;
  }

  // --- prerequisites ---

  /** The chosen prerequisites the player can see, in the order they were chosen. */
  public List<UUID> prerequisites() {
    return List.copyOf(prerequisites);
  }

  public void addPrerequisite(UUID quest) {
    if (!prerequisites.contains(quest)) prerequisites.add(quest);
  }

  public void removePrerequisite(UUID quest) {
    prerequisites.remove(quest);
  }

  /** The "something you don't have access to" chip shows. */
  public boolean showsHiddenPrerequisites() {
    return hiddenPrerequisites && !dropHidden;
  }

  /** The "a deleted quest" chip shows. */
  public boolean showsDeletedPrerequisite() {
    return deletedPrerequisite && !dropDeleted;
  }

  /** Removes the "something you don't have access to" chip. */
  public void dropHidden() {
    if (hiddenPrerequisites) dropHidden = true;
  }

  /** Removes the "a deleted quest" chip. */
  public void dropDeleted() {
    if (deletedPrerequisite) dropDeleted = true;
  }

  /** At least one chip of any kind: the auto-join checkbox shows. */
  public boolean hasPrerequisites() {
    return !prerequisites.isEmpty() || showsHiddenPrerequisites() || showsDeletedPrerequisite();
  }

  // --- auto-join ---

  public boolean autoJoin() {
    return autoJoin;
  }

  public void autoJoin(boolean autoJoin) {
    this.autoJoin = autoJoin;
  }

  // --- the pane ---

  /** The edit pane's rows: the whole tree the quest is in now, from its top. */
  public List<QuestTreeRows.Row> rows(UUID me) {
    return QuestTreeRows.wholeTree(tree.effective(), me, questId);
  }

  /**
   * The quest sits inside another in the draft, is not completed and the player may detach it: its
   * row gets the remove button.
   */
  public boolean canRemove(UUID quest, UUID me) {
    QuestData q = tree.effective().get(quest);
    if (q == null || q.parentId() == null || q.isCompleted()) return false;
    return LinkRules.detach(tree.effective(), me, quest).isEmpty();
  }

  /**
   * The quests taken out of the tree the edited quest started in, topmost only, listed below the
   * pane; a quest the pane still shows (the edited quest moved to another tree, with its subquests)
   * is left out.
   */
  public List<UUID> takenOut(UUID me) {
    Set<UUID> shown = new HashSet<>();
    for (QuestTreeRows.Row row : rows(me)) shown.add(row.id());
    List<UUID> out = new ArrayList<>();
    for (UUID id : tree.takenOut(treeRoot)) {
      if (!shown.contains(id)) out.add(id);
    }
    return out;
  }

  /** The edited quest sits inside a visible quest in the draft. */
  public boolean hasParent() {
    QuestData q = tree.effective().get(questId);
    return q != null && q.parentId() != null;
  }

  public HiddenParent hiddenParent() {
    if (!hiddenParentAtStart) return HiddenParent.NONE;
    QuestData q = tree.effective().get(questId);
    return q != null && q.parentId() == null && q.hiddenParent()
        ? HiddenParent.INSIDE
        : HiddenParent.DETACHED;
  }

  // --- saving ---

  /** Something here differs from the quest as it was opened. */
  public boolean isDirty() {
    return !toLinkChanges().equals(LinkChanges.NONE);
  }

  /** The save's links block: only what changed; {@link LinkChanges#NONE} when nothing did. */
  public LinkChanges toLinkChanges() {
    List<UUID> added = new ArrayList<>(prerequisites);
    added.removeAll(originalPrerequisites);
    List<UUID> removed = new ArrayList<>(originalPrerequisites);
    removed.removeAll(prerequisites);
    LinkChanges links =
        new LinkChanges(
            visibility != originalVisibility ? visibility : null,
            tree.ops(),
            List.copyOf(added),
            List.copyOf(removed),
            dropHidden,
            dropDeleted,
            autoJoin != originalAutoJoin ? autoJoin : null);
    return links.equals(LinkChanges.NONE) ? LinkChanges.NONE : links;
  }
}
