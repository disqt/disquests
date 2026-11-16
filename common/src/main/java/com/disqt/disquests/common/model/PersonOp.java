package com.disqt.disquests.common.model;

import java.util.UUID;

/** One change to a quest's people. {@code role} is only used by SET_ROLE. */
public record PersonOp(Kind kind, UUID player, Role role) {

  public enum Kind {
    SET_ROLE,
    REMOVE,
    REMOVE_WITH_MY_SUBQUESTS
  }
}
