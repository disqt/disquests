package com.disqt.disquests.client.data;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.common.graph.QuestView;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestRoutingTest {

  private static final UUID ME = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  private static QuestData data(UUID owner, List<ContributorData> people, long completedAt) {
    return new QuestData(
        UUID.randomUUID(),
        "T",
        "C",
        owner,
        "O",
        Visibility.OPEN,
        people,
        0L,
        null,
        false,
        null,
        null,
        List.of(),
        List.of(),
        completedAt,
        null,
        null);
  }

  @Test
  void splitsByWhetherTheLocalPlayerHoldsAnyRole() {
    QuestData owned = data(ME, List.of(), 0L);
    QuestData contributed = data(OTHER, List.of(new ContributorData(ME, "me", Role.READ)), 0L);
    QuestData stranger = data(OTHER, List.of(), 0L);
    QuestData completedStranger = data(OTHER, List.of(), 99L);
    QuestData completedMine = data(ME, List.of(), 99L);
    QuestData lockedRead =
        data(QuestView.NO_OWNER, List.of(new ContributorData(ME, "me", Role.READ)), 0L)
            .withLinksView(List.of(), false, false, true);

    QuestRouting.Split split =
        QuestRouting.split(
            List.of(owned, contributed, stranger, completedStranger, completedMine, lockedRead),
            ME);

    assertEquals(
        List.of(owned.id(), contributed.id(), completedMine.id(), lockedRead.id()),
        split.mine().stream().map(Quest::getId).toList());
    assertEquals(
        List.of(stranger.id(), completedStranger.id()),
        split.other().stream().map(Quest::getId).toList());
    assertTrue(split.mine().get(3).isLocked());
  }
}
