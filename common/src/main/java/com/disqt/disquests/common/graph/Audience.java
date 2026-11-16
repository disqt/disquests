package com.disqt.disquests.common.graph;

import com.disqt.disquests.common.model.QuestData;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** What a change to the quests means for one player: which views they must receive or drop. */
public final class Audience {

  private Audience() {}

  /** A quest whose view changed for the player; {@code view} null means they no longer see it. */
  public record Change(UUID questId, QuestData view) {}

  /**
   * Every quest whose {@link QuestView#forRecipient view} for {@code player} differs between the
   * two graphs, in the order of {@code before} then the quests new in {@code after}. A quest the
   * player sees in neither graph is not listed.
   */
  public static List<Change> changes(QuestGraph before, QuestGraph after, UUID player) {
    Set<UUID> ids = new LinkedHashSet<>();
    for (QuestData q : before.all()) ids.add(q.id());
    for (QuestData q : after.all()) ids.add(q.id());
    List<Change> changes = new ArrayList<>();
    for (UUID id : ids) {
      QuestData was = QuestView.forRecipient(before, player, id);
      QuestData now = QuestView.forRecipient(after, player, id);
      if (!Objects.equals(was, now)) changes.add(new Change(id, now));
    }
    return changes;
  }
}
