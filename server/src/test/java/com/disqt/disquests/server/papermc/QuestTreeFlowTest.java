package com.disqt.disquests.server.papermc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.PersonOp;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.TreeOp;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * One quest line from first save to deletion, through one database and only packets: the steps
 * share their state, so each one checks what the ones before it left behind.
 */
class QuestTreeFlowTest extends HandlerFixture {

  private static final UUID HUB = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID MIDDLE = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID LOW = UUID.fromString("20000000-0000-0000-0000-000000000003");
  private static final UUID MAXS = UUID.fromString("20000000-0000-0000-0000-000000000004");
  private static final UUID NEXT = UUID.fromString("20000000-0000-0000-0000-000000000005");

  private static LinkChanges openAndAttach(TreeOp op) {
    return new LinkChanges(Visibility.OPEN, List.of(op), List.of(), List.of(), false, false, null);
  }

  private void setRole(UUID owner, UUID quest, UUID player, Role role) {
    receive(
        owner,
        PacketCodec.writeUpdatePeople(
            quest, List.of(new PersonOp(PersonOp.Kind.SET_ROLE, player, role))));
  }

  @Test
  void aQuestLineFromFirstSaveToDeletion() {
    // Leo builds Hub (Private) > Middle (Open) > Low (Open) through saves with tree moves.
    saveText(LEO, HUB, "Hub");
    save(LEO, MIDDLE, "Middle", openAndAttach(attach(MIDDLE, HUB)));
    save(LEO, LOW, "Low", openAndAttach(attach(LOW, MIDDLE)));
    // Max reads the hub and writes in Middle, where he puts his own quest; Juju reads Low.
    setRole(LEO, HUB, MAX, Role.READ);
    setRole(LEO, MIDDLE, MAX, Role.WRITE);
    setRole(LEO, LOW, JUJU, Role.READ);
    save(MAX, MAXS, "Max's", openAndAttach(attach(MAXS, MIDDLE)));

    assertNull(stored(HUB).parentId());
    assertEquals(Visibility.PRIVATE, stored(HUB).visibility());
    assertEquals(HUB, stored(MIDDLE).parentId());
    assertEquals(MIDDLE, stored(LOW).parentId());
    assertEquals(MIDDLE, stored(MAXS).parentId());
    assertEquals(MAX, stored(MAXS).ownerUuid());
    assertEquals(List.of(), notices(LEO));
    assertEquals(List.of(), notices(MAX));
    assertNull(viewOf(ANNA, MIDDLE), "the Private hub hides its tree from Anna");
    assertNull(viewOf(ANNA, MAXS));
    assertEquals(List.of(), outbox.packetsFor(ANNA));

    // Next is Open, waits on Low and auto-joins Low's people once it unlocks.
    save(LEO, NEXT, "Next", visibility(Visibility.OPEN));
    save(
        LEO,
        NEXT,
        "Next",
        new LinkChanges(null, List.of(), List.of(LOW), List.of(), false, false, true));
    assertEquals(List.of(LOW), stored(NEXT).prerequisites());
    assertTrue(stored(NEXT).autoJoin());
    assertTrue(viewOf(JUJU, NEXT).locked());
    assertTrue(viewOf(ANNA, NEXT).locked());
    outbox.clear();

    // Completing Low unlocks Next: Juju, on Low, is added with Read and told it is one he follows;
    // Anna, with no role on Low, is told it unlocked and joins nothing.
    receive(LEO, PacketCodec.writeCompleteQuest(LOW, false));

    assertTrue(stored(LOW).isCompleted());
    assertEquals(Role.READ, roleOf(NEXT, JUJU));
    assertNull(roleOf(NEXT, ANNA));
    QuestData unlocked = viewOf(JUJU, NEXT);
    assertFalse(unlocked.locked());
    assertTrue(outbox.updatesFor(JUJU).contains(unlocked));
    assertEquals(
        List.of(new Notice(Notice.Kind.QUEST_UNLOCKED_FOLLOWED, NEXT, "Next", null)),
        notices(JUJU));
    assertEquals(
        List.of(new Notice(Notice.Kind.QUEST_UNLOCKED, NEXT, "Next", null)), notices(ANNA));
    outbox.clear();

    // Reopening Low locks Next again for Juju, who keeps his Read role and is told nothing.
    receive(LEO, PacketCodec.writeReopenQuest(LOW));

    assertFalse(stored(LOW).isCompleted());
    QuestData relocked = viewOf(JUJU, NEXT);
    assertTrue(relocked.locked());
    assertEquals(
        List.of(relocked),
        outbox.updatesFor(JUJU).stream().filter(q -> q.id().equals(NEXT)).toList());
    assertEquals(Role.READ, roleOf(NEXT, JUJU));
    assertEquals(List.of(), notices(JUJU));
    outbox.clear();

    // Leo deletes the hub with his subquests. Max's quest is kept and moves to top level, where
    // everyone would see it, so it becomes Private and Max is told; Anna learns nothing.
    receive(LEO, PacketCodec.writeDeleteQuest(HUB, true));

    assertNull(stored(HUB));
    assertNull(stored(MIDDLE));
    assertNull(stored(LOW));
    QuestData kept = stored(MAXS);
    assertNull(kept.parentId());
    assertEquals(Visibility.PRIVATE, kept.visibility());
    assertNotNull(kept.privatized());
    assertEquals(Visibility.OPEN, kept.privatized().previous());
    assertEquals(Role.OWNER, kept.roleOf(MAX));
    assertEquals(List.of(new Notice(Notice.Kind.SET_PRIVATE, MAXS, "Max's", null)), notices(MAX));
    assertTrue(outbox.updatesFor(ANNA).stream().noneMatch(q -> q.id().equals(MAXS)));
    assertNull(viewOf(ANNA, MAXS));
    // Low was unfinished, so Next keeps waiting on its id and stays locked.
    assertEquals(List.of(LOW), stored(NEXT).prerequisites());
    assertTrue(viewOf(JUJU, NEXT).locked());
    outbox.clear();

    // Max sets his quest back to what it was: Anna now sees it.
    receive(MAX, PacketCodec.writeResolvePrivatized(MAXS, true));

    assertEquals(Visibility.OPEN, stored(MAXS).visibility());
    assertNull(stored(MAXS).privatized());
    assertEquals(List.of(viewOf(ANNA, MAXS)), outbox.updatesFor(ANNA));
  }
}
