package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.client.gui.tree.QuestTreeRows.ListOptions;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.Row;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.Tab;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a tab lists, every quest expanded: the same rows as {@link QuestTreeRows#forTab} with
 * nothing collapsed. The map and the "NEW" marks read this, so they agree with the list whatever
 * the player has expanded.
 */
public final class ListedQuests {

  private ListedQuests() {}

  public static List<Row> fullyExpanded(
      QuestGraph graph, UUID me, Tab tab, String search, ListOptions options) {
    Set<UUID> everything = new HashSet<>();
    for (QuestData q : graph.all()) everything.add(q.id());
    return QuestTreeRows.forTab(graph, me, tab, search, everything, options);
  }
}
