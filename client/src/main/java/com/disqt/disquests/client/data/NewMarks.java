package com.disqt.disquests.client.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quests the player has not looked at yet. A mark goes when its quest is opened, or once the board
 * has been opened three times since it was set: it shows on those three openings and is gone on the
 * fourth.
 */
public final class NewMarks {

  static final int BOARD_OPENINGS_BEFORE_FADE = 3;

  private final Map<UUID, Integer> boardOpeningsSeen = new ConcurrentHashMap<>();

  public void mark(UUID questId) {
    boardOpeningsSeen.putIfAbsent(questId, 0);
  }

  public boolean isNew(UUID questId) {
    return questId != null && boardOpeningsSeen.containsKey(questId);
  }

  public void opened(UUID questId) {
    if (questId != null) boardOpeningsSeen.remove(questId);
  }

  /**
   * Counts one board opening for every current mark and drops marks that have now seen more than
   * three, so a mark still shows on the third opening.
   */
  public void boardOpened() {
    boardOpeningsSeen.replaceAll((id, seen) -> seen + 1);
    boardOpeningsSeen.values().removeIf(seen -> seen > BOARD_OPENINGS_BEFORE_FADE);
  }

  public void clear() {
    boardOpeningsSeen.clear();
  }
}
