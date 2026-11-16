package com.disqt.disquests.common.model;

import com.disqt.disquests.common.graph.Refusal;
import java.util.UUID;

/** A one-off message from the server. {@code title} and {@code reason} may be null. */
public record Notice(Kind kind, UUID questId, String title, Refusal reason) {

  public enum Kind {
    QUEST_UNLOCKED,
    QUEST_UNLOCKED_FOLLOWED,
    SET_PRIVATE,
    CHANGE_REFUSED
  }
}
