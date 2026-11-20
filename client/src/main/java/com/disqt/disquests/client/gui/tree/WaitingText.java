package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a locked quest is waiting on, as the three spec sentences need it: the prerequisites the
 * player can see, each a link; whether there is something else they cannot see; and whether they
 * can see none at all ("This quest isn't available yet. Something else has to be finished first.").
 */
public final class WaitingText {

  /**
   * {@code visible}: the prerequisites listed in the view that are in the graph, in the view's
   * order. {@code somethingElse}: the list is incomplete (hidden or deleted prerequisites, or a
   * listed one the client does not have). {@code nothingVisible}: {@code visible} is empty.
   */
  public record Waiting(List<UUID> visible, boolean somethingElse, boolean nothingVisible) {}

  private WaitingText() {}

  public static Waiting of(Quest quest, QuestGraph graph) {
    QuestData view = quest.toData();
    List<UUID> visible = new ArrayList<>();
    for (UUID p : view.prerequisites()) {
      if (graph.get(p) != null) visible.add(p);
    }
    boolean somethingElse =
        view.hiddenPrerequisites()
            || view.deletedPrerequisite()
            || visible.size() < view.prerequisites().size();
    return new Waiting(List.copyOf(visible), somethingElse, visible.isEmpty());
  }
}
