package com.disqt.disquests.server.papermc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.graph.Plans.PrivatizeInfo;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Completing, reopening and deleting quests, and what that unlocks, relocks and privatizes. */
class CompleteDeleteHandlerTest extends HandlerFixture {

  @Nested
  class Completion {

    @ParameterizedTest(name = "also my subquests: {0}")
    @ValueSource(booleans = {false, true})
    void anotherPlayersSubquestStaysActive(boolean alsoMine) {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      UUID mine = subquest(hub, LEO, "Mine", Visibility.OPEN);
      UUID theirs = subquest(hub, MAX, "Theirs", Visibility.OPEN);

      receive(LEO, PacketCodec.writeCompleteQuest(hub, alsoMine));

      assertTrue(stored(hub).isCompleted());
      assertEquals(alsoMine, stored(mine).isCompleted());
      assertFalse(stored(theirs).isCompleted());
      assertEquals(LEO, stored(hub).completedBy());
    }

    @Test
    void completingACompletedQuestOrReopeningAnActiveOneDoesNothing() {
      UUID done = quest(LEO, "Done", Visibility.OPEN);
      UUID active = quest(LEO, "Active", Visibility.OPEN);
      dm.completeQuest(done, MAX, 5000L);

      receive(LEO, PacketCodec.writeCompleteQuest(done, false));
      receive(LEO, PacketCodec.writeReopenQuest(active));

      assertEquals(MAX, stored(done).completedBy());
      assertFalse(stored(active).isCompleted());
      assertEquals(List.of(), outbox.packetsFor(LEO));
    }

    @Test
    void anOwnerIsToldAgainHowManyRequestsWaitWhenCompletionDropsThem() {
      UUID hub = quest(LEO, "Hub", Visibility.CLOSED);
      dm.setRole(hub, MAX, Role.OWNER);
      dm.createCollaborationRequest(hub, ANNA);

      receive(LEO, PacketCodec.writeCompleteQuest(hub, false));

      assertEquals(0, dm.getPendingRequestCount(LEO));
      for (UUID owner : List.of(LEO, MAX)) {
        assertEquals(1, outbox.handshakesFor(owner).size(), "handshake for " + owner);
        assertEquals(0, outbox.handshakesFor(owner).get(0).pendingRequestCount());
      }
    }
  }

  @Nested
  class Unlocking {

    @ParameterizedTest(name = "{0} with auto-join {1}: Juju joins {2}")
    @CsvSource({"OPEN, true, true", "OPEN, false, false", "CLOSED, true, false"})
    void completingThePrerequisiteUnlocksTheFollowUp(
        Visibility visibility, boolean autoJoin, boolean joins) {
      UUID first = quest(LEO, "First", Visibility.OPEN);
      dm.setRole(first, JUJU, Role.READ);
      UUID next = quest(LEO, "Next", visibility);
      dm.setPrerequisites(next, List.of(first));
      dm.setAutoJoin(next, autoJoin);
      assertTrue(viewOf(JUJU, next).locked());

      receive(LEO, PacketCodec.writeCompleteQuest(first, false));

      assertEquals(joins ? Role.READ : null, roleOf(next, JUJU));
      QuestData full = viewOf(JUJU, next);
      assertFalse(full.locked());
      assertTrue(outbox.updatesFor(JUJU).contains(full));
      assertEquals(
          List.of(new Notice(Notice.Kind.QUEST_UNLOCKED_FOLLOWED, next, "Next", null)),
          notices(JUJU));
      assertEquals(
          List.of(new Notice(Notice.Kind.QUEST_UNLOCKED, next, "Next", null)), notices(ANNA));
    }

    @Test
    void anOfflineFollowerIsAutoJoinedToo() {
      UUID first = quest(LEO, "First", Visibility.OPEN);
      dm.setRole(first, JUJU, Role.READ);
      UUID next = quest(LEO, "Next", Visibility.OPEN);
      dm.setPrerequisites(next, List.of(first));
      dm.setAutoJoin(next, true);
      outbox.leave(JUJU);

      receive(LEO, PacketCodec.writeCompleteQuest(first, false));

      assertEquals(Role.READ, roleOf(next, JUJU));
    }

    @Test
    void reopeningThePrerequisiteRelocksTheFollowUpAndKeepsRolesAndPins() {
      UUID first = quest(LEO, "First", Visibility.OPEN);
      UUID next = quest(LEO, "Next", Visibility.OPEN);
      dm.setPrerequisites(next, List.of(first));
      dm.completeQuest(first, LEO, 5000L);
      dm.setRole(next, JUJU, Role.READ);
      dm.pinQuest(JUJU, next);
      assertFalse(viewOf(JUJU, next).locked());

      receive(LEO, PacketCodec.writeReopenQuest(first));

      QuestData locked = viewOf(JUJU, next);
      assertTrue(locked.locked());
      assertEquals(
          List.of(locked),
          outbox.updatesFor(JUJU).stream().filter(q -> q.id().equals(next)).toList());
      assertEquals(Role.READ, roleOf(next, JUJU));
      assertTrue(dm.isQuestPinned(JUJU, next));
      assertEquals(List.of(), notices(JUJU));
    }
  }

  @Nested
  class Deletion {

    @ParameterizedTest(name = "also my subquests: {0}")
    @ValueSource(booleans = {false, true})
    void keptSubquestsMoveUp(boolean alsoMine) {
      UUID top = quest(LEO, "Top", Visibility.OPEN);
      UUID hub = subquest(top, LEO, "Hub", Visibility.OPEN);
      UUID mine = subquest(hub, LEO, "Mine", Visibility.OPEN);
      UUID theirs = subquest(hub, MAX, "Theirs", Visibility.OPEN);

      receive(LEO, PacketCodec.writeDeleteQuest(hub, alsoMine));

      assertNull(stored(hub));
      if (alsoMine) assertNull(stored(mine));
      else assertEquals(top, stored(mine).parentId());
      assertEquals(top, stored(theirs).parentId());
      assertEquals(Visibility.OPEN, stored(theirs).visibility());
    }

    @Test
    void aDeleteThatWouldWidenAnAudiencePrivatizesAndTheOtherOwnerRestoresIt() {
      UUID secret = quest(LEO, "Secret", Visibility.PRIVATE);
      UUID inside = subquest(secret, LEO, "Inside", Visibility.OPEN);
      dm.setRole(inside, MAX, Role.OWNER);

      receive(LEO, PacketCodec.writeDeleteQuest(secret, false));

      QuestData q = stored(inside);
      assertNull(q.parentId());
      assertEquals(Visibility.PRIVATE, q.visibility());
      assertNotNull(q.privatized());
      assertEquals(Visibility.OPEN, q.privatized().previous());
      assertEquals(
          List.of(new Notice(Notice.Kind.SET_PRIVATE, inside, "Inside", null)), notices(MAX));
      assertEquals(List.of(), notices(LEO));
      assertEquals(List.of(), outbox.packetsFor(ANNA));
      outbox.clear();

      receive(MAX, PacketCodec.writeResolvePrivatized(inside, true));

      assertEquals(Visibility.OPEN, stored(inside).visibility());
      assertNull(stored(inside).privatized());
      assertEquals(List.of(viewOf(ANNA, inside)), outbox.updatesFor(ANNA));
    }

    @Test
    void keepingPrivateClearsTheBannerOnly() {
      UUID inside = quest(LEO, "Inside", Visibility.PRIVATE);
      dm.setPrivatized(inside, new PrivatizeInfo(Visibility.OPEN, "Secret", MAX, Set.of()), 5000L);

      receive(JUJU, PacketCodec.writeResolvePrivatized(inside, true));
      assertNotNull(stored(inside).privatized());

      receive(LEO, PacketCodec.writeResolvePrivatized(inside, false));
      assertEquals(Visibility.PRIVATE, stored(inside).visibility());
      assertNull(stored(inside).privatized());
    }

    @Test
    void deletingAnUnfinishedPrerequisiteLeavesTheFollowUpLocked() {
      UUID first = quest(MAX, "First", Visibility.OPEN);
      UUID next = quest(LEO, "Next", Visibility.OPEN);
      dm.setPrerequisites(next, List.of(first));
      dm.setRole(next, JUJU, Role.READ);

      receive(MAX, PacketCodec.writeDeleteQuest(first, false));

      assertEquals(List.of(first), stored(next).prerequisites());
      QuestData forLeo = viewOf(LEO, next);
      assertTrue(forLeo.deletedPrerequisite());
      assertEquals(List.of(forLeo), outbox.updatesFor(LEO));
      QuestData forJuju = viewOf(JUJU, next);
      assertTrue(forJuju.locked());
      assertFalse(forJuju.deletedPrerequisite());
      assertTrue(forJuju.hiddenPrerequisites());
      assertEquals(List.of(), notices(JUJU));
    }

    @Test
    void theIdOfADeletedUnfinishedPrerequisiteCannotBeTakenByANewQuest() {
      UUID first = quest(MAX, "First", Visibility.OPEN);
      UUID next = quest(LEO, "Next", Visibility.OPEN);
      dm.setPrerequisites(next, List.of(first));
      receive(MAX, PacketCodec.writeDeleteQuest(first, false));
      outbox.clear();

      save(JUJU, first, "First again", visibility(Visibility.OPEN));
      receive(JUJU, PacketCodec.writeCompleteQuest(first, false));

      assertNull(stored(first));
      assertTrue(stored(next).prerequisites().contains(first));
      assertTrue(viewOf(ANNA, next).locked());
      assertEquals(List.of(), outbox.packetsFor(JUJU));
      assertEquals(List.of(), outbox.packetsFor(ANNA));

      UUID fresh = UUID.randomUUID();
      saveText(JUJU, fresh, "Fresh");
      assertEquals(JUJU, stored(fresh).ownerUuid());
    }

    @Test
    void deletingACompletedPrerequisiteDoesNotRelock() {
      UUID first = quest(MAX, "First", Visibility.OPEN);
      UUID next = quest(LEO, "Next", Visibility.OPEN);
      dm.setPrerequisites(next, List.of(first));
      dm.completeQuest(first, MAX, 5000L);

      receive(MAX, PacketCodec.writeDeleteQuest(first, false));

      assertEquals(List.of(), stored(next).prerequisites());
      assertFalse(viewOf(ANNA, next).locked());
    }

    @Test
    void privatizedStateIsShownToOwnersOnly() {
      UUID secret = quest(LEO, "Secret", Visibility.PRIVATE);
      UUID inside = subquest(secret, LEO, "Inside", Visibility.OPEN);
      dm.setRole(inside, JUJU, Role.WRITE);

      receive(LEO, PacketCodec.writeDeleteQuest(secret, false));

      Privatized forLeo = viewOf(LEO, inside).privatized();
      assertEquals(Visibility.OPEN, forLeo.previous());
      assertEquals("Secret", forLeo.deletedTitle());
      assertNull(viewOf(JUJU, inside).privatized());
      assertEquals(List.of(), notices(JUJU));
    }
  }
}
