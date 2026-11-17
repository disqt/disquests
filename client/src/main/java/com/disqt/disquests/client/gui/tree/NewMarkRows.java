package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.client.gui.tree.QuestTreeRows.Row;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Which rows of a list show the "NEW" mark: a new quest's own row and the rows of every listed
 * quest above it, so a new quest nested under a collapsed parent is still seen at the top of the
 * list.
 */
public final class NewMarkRows {

  private NewMarkRows() {}

  /**
   * @param listed the tab's rows, fully expanded ({@link ListedQuests#fullyExpanded}), so the mark
   *     does not depend on what is collapsed
   * @param isNew whether a quest carries a mark of its own
   * @return the ids whose row shows the mark: each listed new quest and its listed ancestors
   */
  public static Set<UUID> showingNew(QuestGraph graph, List<Row> listed, Predicate<UUID> isNew) {
    Set<UUID> listedIds = new HashSet<>();
    for (Row row : listed) listedIds.add(row.id());
    Set<UUID> marked = new HashSet<>();
    for (Row row : listed) {
      if (!isNew.test(row.id())) continue;
      UUID current = row.id();
      for (int step = 0; current != null && step <= QuestGraph.MAX_WALK; step++) {
        if (!listedIds.contains(current) || !marked.add(current)) break;
        QuestData q = graph.get(current);
        current = q != null ? q.parentId() : null;
      }
    }
    return marked;
  }
}
