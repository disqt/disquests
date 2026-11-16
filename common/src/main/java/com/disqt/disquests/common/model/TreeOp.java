package com.disqt.disquests.common.model;

import java.util.UUID;

/**
 * One move in the quest tree. ATTACH: {@code quest} goes inside {@code target}. DETACH: {@code
 * target} is null. SWAP: {@code target} sits inside {@code quest} and takes its place.
 */
public record TreeOp(Kind kind, UUID quest, UUID target) {

  public enum Kind {
    ATTACH,
    DETACH,
    SWAP
  }
}
