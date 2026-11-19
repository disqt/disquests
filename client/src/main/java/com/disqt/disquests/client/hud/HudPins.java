package com.disqt.disquests.client.hud;

import com.disqt.disquests.client.data.Quest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Which pinned quests the HUD draws, without Minecraft. */
public final class HudPins {

  private HudPins() {}

  /**
   * The pinned quests to draw, in pin order: those in the cache that are not completed. A quest
   * held as a locked view is skipped, not unpinned: its pin shows again when it unlocks.
   */
  public static List<Quest> shown(Iterable<UUID> pinnedIds, Function<UUID, Quest> lookup) {
    List<Quest> result = new ArrayList<>();
    for (UUID id : pinnedIds) {
      Quest quest = lookup.apply(id);
      if (quest != null && !quest.isCompleted() && !quest.isLocked()) {
        result.add(quest);
      }
    }
    return result;
  }
}
