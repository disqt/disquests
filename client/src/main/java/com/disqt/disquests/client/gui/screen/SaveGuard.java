package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.model.LinkChanges;
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

  /** The toast when owner-only changes were left out because the player is no longer an owner. */
  public static final String NO_LONGER_OWNER = "gui.disquests.toast.owner_parts_not_saved";

  /** The links block to send, and whether owner-only changes were left out of it. */
  public record Links(LinkChanges links, boolean ownerPartsDropped) {}

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

  /**
   * The editor's links block as the server will take it. A player who is no longer an owner of the
   * cached quest (lowered to Write while the editor was open) keeps only the tree moves:
   * visibility, prerequisite changes, the two drop flags and auto-join need Owner and the server
   * would drop them silently. A quest not cached yet (new) is the player's own: everything is sent.
   */
  public static Links linksToSend(@Nullable Quest cached, UUID me, LinkChanges draft) {
    if (cached == null || cached.isOwner(me)) return new Links(draft, false);
    LinkChanges treeOnly =
        draft.treeOps().isEmpty()
            ? LinkChanges.NONE
            : new LinkChanges(null, draft.treeOps(), null, null, false, false, null);
    return new Links(treeOnly, !treeOnly.equals(draft));
  }
}
