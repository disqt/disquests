package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Whether the editor may send its save, decided from the quest as the cache holds it now (the
 * editor's copy can be older). The server drops a save it refuses without an answer, so the client
 * must not show such a save as done.
 */
public final class SaveGuard {

  /** The toast when the player lost the right to edit while the editor was open. */
  public static final String NO_LONGER_EDITABLE = "gui.disquests.toast.no_longer_editable";

  public enum Outcome {
    /** Send the save. */
    SEND,
    /** Completed meanwhile: show it archived (the existing toast). */
    ARCHIVED,
    /** The player can no longer edit it (role lowered or removed, or locked for them). */
    NO_LONGER_EDITABLE,
    /** No longer in the cache (deleted, or no longer visible). */
    GONE
  }

  private SaveGuard() {}

  /**
   * @param cached the quest as the cache holds it now, null when absent
   * @param newQuest true for a quest the server has not stored yet (it is not cached)
   */
  public static Outcome check(@Nullable Quest cached, UUID me, boolean newQuest) {
    if (cached == null) return newQuest ? Outcome.SEND : Outcome.GONE;
    if (cached.isCompleted()) return Outcome.ARCHIVED;
    if (cached.isLocked() || !cached.canEdit(me)) return Outcome.NO_LONGER_EDITABLE;
    return Outcome.SEND;
  }
}
