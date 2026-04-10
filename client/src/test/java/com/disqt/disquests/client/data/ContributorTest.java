package com.disqt.disquests.client.data;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.common.model.ContributorData;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContributorTest {

  @Test
  void wrapsContributorData() {
    UUID uuid = UUID.randomUUID();
    ContributorData data = new ContributorData(uuid, "Alice", true);
    Contributor c = new Contributor(data);

    assertEquals(uuid, c.getUuid());
    assertEquals("Alice", c.getName());
    assertTrue(c.canEdit());
  }

  @Test
  void viewOnlyContributor() {
    ContributorData data = new ContributorData(UUID.randomUUID(), "Bob", false);
    Contributor c = new Contributor(data);
    assertFalse(c.canEdit());
  }
}
