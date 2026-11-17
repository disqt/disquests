package com.disqt.disquests.client.data;

import com.disqt.disquests.common.model.QuestData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Decides which cache list a quest belongs in: "my quests" when the player holds any role. */
public final class QuestRouting {

  private QuestRouting() {}

  /** The quests split by whether the local player holds a role on them, in incoming order. */
  public record Split(List<Quest> mine, List<Quest> other) {}

  public static boolean isMine(Quest quest, UUID me) {
    return quest.roleOf(me) != null;
  }

  public static Split split(List<QuestData> incoming, UUID me) {
    List<Quest> mine = new ArrayList<>();
    List<Quest> other = new ArrayList<>();
    for (QuestData data : incoming) {
      Quest quest = Quest.fromNetwork(data);
      (isMine(quest, me) ? mine : other).add(quest);
    }
    return new Split(mine, other);
  }
}
