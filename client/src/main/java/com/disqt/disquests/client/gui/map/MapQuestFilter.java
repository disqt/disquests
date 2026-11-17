package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.gui.tree.QuestTreeRows;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.ListOptions;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.Row;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.Tab;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which quests' zones the main screen's map draws. Minecraft-free, so it can be unit-tested.
 *
 * <p>The base is what the tab lists, every level expanded: on My Quests and the Quest Board the
 * list with its search and Open/Closed filter, as the map has always followed the list; on Archived
 * the active My Quests, unsearched. Of those, the map draws the top-level quests (no parent in the
 * graph, or a completed one: subquests left active under an archived quest count as top level),
 * every quest in the selected quest's tree and every quest the player holds a role on. Never a
 * locked view (it has no location anyway) and never a completed quest.
 */
public final class MapQuestFilter {

  private MapQuestFilter() {}

  /** As {@link #visible(QuestGraph, UUID, UUID, Tab, String, ListOptions)} with no search. */
  public static List<UUID> visible(QuestGraph graph, UUID me, UUID selected, Tab tab) {
    return visible(graph, me, selected, tab, "", ListOptions.DEFAULT);
  }

  /** The ids to draw, in list order. {@code selected} may be null. */
  public static List<UUID> visible(
      QuestGraph graph, UUID me, UUID selected, Tab tab, String search, ListOptions options) {
    boolean archived = tab == Tab.ARCHIVED;
    Set<UUID> everything = new HashSet<>();
    for (QuestData q : graph.all()) everything.add(q.id());
    List<Row> rows =
        QuestTreeRows.forTab(
            graph,
            me,
            archived ? Tab.MY_QUESTS : tab,
            archived ? "" : search,
            everything,
            archived ? ListOptions.DEFAULT : options);

    Set<UUID> selectedTree = treeOf(graph, selected);
    List<UUID> drawn = new ArrayList<>();
    for (Row row : rows) {
      QuestData q = graph.get(row.id());
      if (q == null || q.locked() || q.isCompleted()) continue;
      if (isTopLevel(graph, q) || selectedTree.contains(q.id()) || q.roleOf(me) != null) {
        drawn.add(q.id());
      }
    }
    return drawn;
  }

  private static boolean isTopLevel(QuestGraph graph, QuestData q) {
    QuestData parent = graph.get(q.parentId());
    return parent == null || parent.isCompleted();
  }

  /** The selected quest's top visible ancestor and everything under it; empty without one. */
  private static Set<UUID> treeOf(QuestGraph graph, UUID selected) {
    QuestData q = graph.get(selected);
    if (q == null) return Set.of();
    List<QuestData> ancestors = graph.ancestors(q.id());
    QuestData top = ancestors.isEmpty() ? q : ancestors.get(ancestors.size() - 1);
    Set<UUID> tree = new HashSet<>();
    tree.add(top.id());
    for (QuestData d : graph.descendants(top.id())) tree.add(d.id());
    return tree;
  }
}
