package com.disqt.disquests.client.data;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.Role;
import java.util.UUID;

public class Contributor {

  private final UUID uuid;
  private final String name;
  private final Role role;

  public Contributor(ContributorData data) {
    this.uuid = data.uuid();
    this.name = data.name();
    this.role = data.role();
  }

  public UUID getUuid() {
    return uuid;
  }

  public String getName() {
    return name;
  }

  public Role getRole() {
    return role;
  }

  /** WRITE or OWNER. */
  public boolean canEdit() {
    return role.atLeast(Role.WRITE);
  }
}
