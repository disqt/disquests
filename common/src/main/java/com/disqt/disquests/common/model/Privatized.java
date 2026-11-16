package com.disqt.disquests.common.model;

import java.util.Set;
import java.util.UUID;

/**
 * Marks a quest forced to private because a quest it depended on was deleted. {@code sawDeleted} is
 * server-side truth only: it is never written to the wire and reads back as an empty set.
 */
public record Privatized(
    Visibility previous, String deletedTitle, String deletedByName, long at, Set<UUID> sawDeleted) {

  public Privatized {
    sawDeleted = sawDeleted == null ? Set.of() : Set.copyOf(sawDeleted);
  }

  public Privatized(Visibility previous, String deletedTitle, String deletedByName, long at) {
    this(previous, deletedTitle, deletedByName, at, Set.of());
  }
}
