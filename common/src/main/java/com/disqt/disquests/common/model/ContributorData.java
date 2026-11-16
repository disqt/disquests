package com.disqt.disquests.common.model;

import java.util.UUID;

public record ContributorData(UUID uuid, String name, Role role) {

  /** Pre-roles signature: true is WRITE, false is READ. */
  public ContributorData(UUID uuid, String name, boolean canEdit) {
    this(uuid, name, canEdit ? Role.WRITE : Role.READ);
  }

  public boolean canEdit() {
    return role.atLeast(Role.WRITE);
  }
}
