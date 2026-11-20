package com.disqt.disquests.common.model;

/** A person's standing on a quest, weakest first. */
public enum Role {
  READ,
  WRITE,
  OWNER;

  public boolean atLeast(Role other) {
    return compareTo(other) >= 0;
  }
}
