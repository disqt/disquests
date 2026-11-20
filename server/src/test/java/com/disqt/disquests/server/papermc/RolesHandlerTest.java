package com.disqt.disquests.server.papermc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.PacketCodec.ContributorOpEntry;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.graph.Plans.PrivatizeInfo;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.ContributorOp;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.PersonOp;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Who may do what on a quest, with three roles, and how people join, leave and are managed. */
class RolesHandlerTest extends HandlerFixture {

  /** The role Juju holds on Leo's quest in the role table. */
  enum Holder {
    NO_ROLE(null),
    READ(Role.READ),
    WRITE(Role.WRITE),
    OWNER(Role.OWNER);

    final Role role;

    Holder(Role role) {
      this.role = role;
    }
  }

  /** One row of the spec's role table, tried by Juju on Leo's Open quest "Hub". */
  enum Action {
    EDIT_TEXT(Role.WRITE),
    COMPLETE(Role.OWNER),
    REOPEN(Role.OWNER),
    DELETE(Role.OWNER),
    CHANGE_VISIBILITY(Role.OWNER),
    MANAGE_PEOPLE(Role.OWNER),
    ADD_PREREQUISITE(Role.OWNER),
    ATTACH_OWN_QUEST(Role.WRITE),
    DETACH(Role.OWNER);

    final Role needs;

    Action(Role needs) {
      this.needs = needs;
    }
  }

  static Stream<Arguments> roleTable() {
    List<Arguments> cells = new ArrayList<>();
    for (Action action : Action.values()) {
      for (Holder holder : Holder.values()) {
        boolean allowed = holder.role != null && holder.role.atLeast(action.needs);
        cells.add(Arguments.of(action, holder, allowed));
      }
    }
    return cells.stream();
  }

  @ParameterizedTest(name = "{0} with {1}: allowed {2}")
  @MethodSource("roleTable")
  void theRoleTable(Action action, Holder holder, boolean allowed) {
    UUID hub = quest(LEO, "Hub", Visibility.OPEN);
    if (holder.role != null) dm.setRole(hub, JUJU, holder.role);
    UUID before = quest(LEO, "Before", Visibility.OPEN);
    UUID mine = quest(JUJU, "Mine", Visibility.OPEN);
    UUID inside = subquest(hub, MAX, "Inside", Visibility.OPEN);
    if (action == Action.REOPEN) dm.completeQuests(List.of(hub), LEO, 5000L);
    outbox.clear();

    switch (action) {
      case EDIT_TEXT -> saveText(JUJU, hub, "Changed");
      case COMPLETE -> receive(JUJU, PacketCodec.writeCompleteQuest(hub, false));
      case REOPEN -> receive(JUJU, PacketCodec.writeReopenQuest(hub));
      case DELETE -> receive(JUJU, PacketCodec.writeDeleteQuest(hub, false));
      case CHANGE_VISIBILITY -> save(JUJU, hub, "Hub", visibility(Visibility.CLOSED));
      case MANAGE_PEOPLE ->
          receive(
              JUJU,
              PacketCodec.writeUpdatePeople(
                  hub, List.of(new PersonOp(PersonOp.Kind.SET_ROLE, ANNA, Role.READ))));
      case ADD_PREREQUISITE -> save(JUJU, hub, "Hub", addPrerequisites(before));
      case ATTACH_OWN_QUEST -> save(JUJU, hub, "Hub", treeOps(attach(mine, hub)));
      case DETACH -> save(JUJU, hub, "Hub", treeOps(detach(inside)));
    }

    QuestData q = stored(hub);
    boolean done =
        switch (action) {
          case EDIT_TEXT -> q.title().equals("Changed");
          case COMPLETE -> q.isCompleted();
          case REOPEN -> !q.isCompleted();
          case DELETE -> q == null;
          case CHANGE_VISIBILITY -> q.visibility() == Visibility.CLOSED;
          case MANAGE_PEOPLE -> q.roleOf(ANNA) == Role.READ;
          case ADD_PREREQUISITE -> q.prerequisites().contains(before);
          case ATTACH_OWN_QUEST -> hub.equals(stored(mine).parentId());
          case DETACH -> stored(inside).parentId() == null;
        };
    assertEquals(allowed, done);
  }

  @Test
  void aV3ContributorWhoCanEditNoLongerCompletes() {
    UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
    dm.setRole(hub, JUJU, Role.WRITE);
    register(JUJU, ProtocolVersion.V3);
    outbox.clear();

    receive(JUJU, PacketCodec.writeCompleteQuest(hub));

    assertFalse(stored(hub).isCompleted());
    assertEquals(List.of(), outbox.packetsFor(LEO));
  }

  @Nested
  class LastOwner {

    @Test
    void cannotLeave() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      receive(LEO, PacketCodec.writeLeaveQuest(hub));
      assertEquals(Role.OWNER, roleOf(hub, LEO));
    }

    @Test
    void aCoOwnerMayLeaveAndTheOtherBecomesDisplayOwner() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      dm.setRole(hub, MAX, Role.OWNER);

      receive(LEO, PacketCodec.writeLeaveQuest(hub));

      assertNull(roleOf(hub, LEO));
      assertEquals(MAX, stored(hub).ownerUuid());
      assertEquals(List.of(hub), outbox.deletesFor(LEO));
    }

    @ParameterizedTest
    @EnumSource(
        value = PersonOp.Kind.class,
        names = {"REMOVE", "REMOVE_WITH_MY_SUBQUESTS", "SET_ROLE"})
    void cannotBeRemovedOrDemoted(PersonOp.Kind kind) {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      dm.setRole(hub, JUJU, Role.WRITE);
      Role role = kind == PersonOp.Kind.SET_ROLE ? Role.WRITE : null;

      receive(LEO, PacketCodec.writeUpdatePeople(hub, List.of(new PersonOp(kind, LEO, role))));

      assertEquals(Role.OWNER, roleOf(hub, LEO));
      assertEquals(
          List.of(new Notice(Notice.Kind.CHANGE_REFUSED, hub, "Hub", Refusal.LAST_OWNER)),
          notices(LEO));
    }

    @Test
    void aV3ClientCannotRemoveOrDemoteTheLastOwner() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      register(LEO, ProtocolVersion.V3);

      receive(
          LEO,
          PacketCodec.writeUpdateContributors(
              hub,
              List.of(
                  new ContributorOpEntry(ContributorOp.UPDATE, LEO, null, false),
                  new ContributorOpEntry(ContributorOp.REMOVE, LEO, null, false))));

      assertEquals(Role.OWNER, roleOf(hub, LEO));
    }

    @ParameterizedTest(name = "canEdit {0}")
    @ValueSource(booleans = {true, false})
    void aV3UpdateNeverLowersACoOwner(boolean canEdit) {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      dm.setRole(hub, MAX, Role.OWNER);
      register(LEO, ProtocolVersion.V3);

      receive(
          LEO,
          PacketCodec.writeUpdateContributors(
              hub,
              List.of(
                  new ContributorOpEntry(ContributorOp.UPDATE, MAX, null, canEdit),
                  new ContributorOpEntry(ContributorOp.ADD, MAX, null, canEdit))));

      assertEquals(Role.OWNER, roleOf(hub, MAX));
    }

    @Test
    void aV3RemoveTakesACoOwnerOff() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      dm.setRole(hub, MAX, Role.OWNER);
      register(LEO, ProtocolVersion.V3);

      receive(
          LEO,
          PacketCodec.writeUpdateContributors(
              hub, List.of(new ContributorOpEntry(ContributorOp.REMOVE, MAX, null, false))));

      assertNull(roleOf(hub, MAX));
      assertEquals(Role.OWNER, roleOf(hub, LEO));
    }
  }

  @Nested
  class UnknownPlayers {

    private final UUID stranger = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Test
    void aPlayerTheServerHasNeverSeenIsNotAddedAndTheOtherOpsApply() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);

      receive(
          LEO,
          PacketCodec.writeUpdatePeople(
              hub,
              List.of(
                  new PersonOp(PersonOp.Kind.SET_ROLE, stranger, Role.WRITE),
                  new PersonOp(PersonOp.Kind.SET_ROLE, ANNA, Role.READ))));

      assertNull(roleOf(hub, stranger));
      assertEquals(Role.READ, roleOf(hub, ANNA));
      assertQuestStillSyncs(hub);
    }

    @Test
    void aV3AddOfAPlayerTheServerHasNeverSeenIsIgnored() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      register(LEO, ProtocolVersion.V3);

      receive(
          LEO,
          PacketCodec.writeUpdateContributors(
              hub,
              List.of(
                  new ContributorOpEntry(ContributorOp.ADD, stranger, null, true),
                  new ContributorOpEntry(ContributorOp.ADD, ANNA, null, false))));

      assertNull(roleOf(hub, stranger));
      assertEquals(Role.READ, roleOf(hub, ANNA));
      assertQuestStillSyncs(hub);
    }

    private void assertQuestStillSyncs(UUID hub) {
      outbox.clear();
      register(LEO, ProtocolVersion.V4);
      List<FakeOutbox.MyQuests> syncs = outbox.myQuestsFor(LEO);
      assertEquals(1, syncs.size());
      assertEquals(List.of(viewOf(LEO, hub)), syncs.get(0).quests());
    }
  }

  @Nested
  class People {

    @Test
    void twoOwnersSwapInOnePacket() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      dm.setRole(hub, JUJU, Role.WRITE);

      receive(
          LEO,
          PacketCodec.writeUpdatePeople(
              hub,
              List.of(
                  new PersonOp(PersonOp.Kind.SET_ROLE, JUJU, Role.OWNER),
                  new PersonOp(PersonOp.Kind.SET_ROLE, LEO, Role.WRITE))));

      QuestData q = stored(hub);
      assertEquals(JUJU, q.ownerUuid());
      assertEquals(List.of(new ContributorData(LEO, "Leo", Role.WRITE)), q.contributors());
      assertEquals(List.of(viewOf(LEO, hub)), outbox.updatesFor(LEO));
      assertEquals(List.of(viewOf(JUJU, hub)), outbox.updatesFor(JUJU));
    }

    @Test
    void anAddedPlayerIsStoredWithTheirNameAndReceivesTheQuest() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);

      receive(
          LEO,
          PacketCodec.writeUpdatePeople(
              hub, List.of(new PersonOp(PersonOp.Kind.SET_ROLE, ANNA, Role.WRITE))));

      assertEquals(
          List.of(new ContributorData(ANNA, "Anna", Role.WRITE)), stored(hub).contributors());
      assertEquals(List.of(viewOf(ANNA, hub)), outbox.updatesFor(ANNA));
    }

    @Test
    void removingWithMySubquestsTakesThePlayerOffTheSendersOwnSubquestsOnly() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      UUID mine = subquest(hub, LEO, "Mine", Visibility.PRIVATE);
      UUID theirs = subquest(hub, MAX, "Theirs", Visibility.OPEN);
      for (UUID q : List.of(hub, mine, theirs)) dm.setRole(q, JUJU, Role.READ);

      receive(
          LEO,
          PacketCodec.writeUpdatePeople(
              hub, List.of(new PersonOp(PersonOp.Kind.REMOVE_WITH_MY_SUBQUESTS, JUJU, null))));

      assertNull(roleOf(hub, JUJU));
      assertNull(roleOf(mine, JUJU));
      assertEquals(Role.READ, roleOf(theirs, JUJU));
    }

    @Test
    void removingOneselfWithMySubquestsSkipsWhereOneIsTheLastOwner() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      dm.setRole(hub, MAX, Role.OWNER);
      UUID alone = subquest(hub, LEO, "Alone", Visibility.OPEN);
      UUID shared = subquest(hub, LEO, "Shared", Visibility.OPEN);
      dm.setRole(shared, MAX, Role.OWNER);

      receive(
          LEO,
          PacketCodec.writeUpdatePeople(
              hub, List.of(new PersonOp(PersonOp.Kind.REMOVE_WITH_MY_SUBQUESTS, LEO, null))));

      assertNull(roleOf(hub, LEO));
      assertNull(roleOf(shared, LEO));
      assertEquals(Role.OWNER, roleOf(alone, LEO));
    }

    @ParameterizedTest(name = "removing Max first: {0}")
    @ValueSource(booleans = {true, false})
    void twoRemovalsWithMySubquestsInOnePacketLeaveASubquestOneOwner(boolean maxFirst) {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      dm.setRole(hub, MAX, Role.OWNER);
      dm.setRole(hub, JUJU, Role.OWNER);
      UUID shared = subquest(hub, LEO, "Shared", Visibility.OPEN);
      dm.setRole(shared, MAX, Role.OWNER);
      PersonOp max = new PersonOp(PersonOp.Kind.REMOVE_WITH_MY_SUBQUESTS, MAX, null);
      PersonOp leo = new PersonOp(PersonOp.Kind.REMOVE_WITH_MY_SUBQUESTS, LEO, null);

      receive(
          LEO,
          PacketCodec.writeUpdatePeople(hub, maxFirst ? List.of(max, leo) : List.of(leo, max)));

      assertEquals(List.of(JUJU), ownerRows(hub));
      List<UUID> sharedOwners = ownerRows(shared);
      assertEquals(1, sharedOwners.size());
      assertEquals(sharedOwners.get(0), stored(shared).ownerUuid());
    }

    @Test
    void applyRoleDownGivesTheRoleOnTheSendersSubquestsWithoutLoweringAnyone() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      dm.setRole(hub, JUJU, Role.WRITE);
      UUID mine = subquest(hub, LEO, "Mine", Visibility.PRIVATE);
      UUID higher = subquest(hub, LEO, "Higher", Visibility.PRIVATE);
      dm.setRole(higher, JUJU, Role.OWNER);
      UUID theirs = subquest(hub, MAX, "Theirs", Visibility.OPEN);

      receive(LEO, PacketCodec.writeApplyRoleDown(hub, JUJU));

      assertEquals(Role.WRITE, roleOf(mine, JUJU));
      assertEquals(Role.OWNER, roleOf(higher, JUJU));
      assertNull(roleOf(theirs, JUJU));
      assertEquals(List.of(viewOf(JUJU, mine)), outbox.updatesFor(JUJU));
    }

    @Test
    void applyRoleDownNeedsOwnerOnTheQuest() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      dm.setRole(hub, MAX, Role.WRITE);
      dm.setRole(hub, JUJU, Role.READ);
      UUID maxs = subquest(hub, MAX, "Max's", Visibility.PRIVATE);

      receive(MAX, PacketCodec.writeApplyRoleDown(hub, JUJU));

      assertNull(roleOf(maxs, JUJU));
    }

    @Test
    void aV3OwnersContributorOpsMapCanEditToWriteOrRead() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      register(LEO, ProtocolVersion.V3);

      receive(
          LEO,
          PacketCodec.writeUpdateContributors(
              hub, List.of(new ContributorOpEntry(ContributorOp.ADD, null, "Anna", true))));
      assertEquals(Role.WRITE, roleOf(hub, ANNA));

      receive(
          LEO,
          PacketCodec.writeUpdateContributors(
              hub, List.of(new ContributorOpEntry(ContributorOp.UPDATE, ANNA, null, false))));
      assertEquals(Role.READ, roleOf(hub, ANNA));

      receive(
          LEO,
          PacketCodec.writeUpdateContributors(
              hub, List.of(new ContributorOpEntry(ContributorOp.REMOVE, ANNA, null, false))));
      assertNull(roleOf(hub, ANNA));
    }

    @Test
    void aV3WriterCannotManageContributors() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      dm.setRole(hub, JUJU, Role.WRITE);
      register(JUJU, ProtocolVersion.V3);

      receive(
          JUJU,
          PacketCodec.writeUpdateContributors(
              hub, List.of(new ContributorOpEntry(ContributorOp.ADD, ANNA, null, true))));

      assertNull(roleOf(hub, ANNA));
    }

    @Test
    void theOldVisibilityPacketNeedsOwner() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      dm.setRole(hub, JUJU, Role.WRITE);

      receive(JUJU, PacketCodec.writeUpdateVisibility(hub, Visibility.OPEN));
      assertEquals(Visibility.PRIVATE, stored(hub).visibility());

      receive(LEO, PacketCodec.writeUpdateVisibility(hub, Visibility.OPEN));
      assertEquals(Visibility.OPEN, stored(hub).visibility());
    }

    @ParameterizedTest(name = "through the save: {0}")
    @ValueSource(booleans = {true, false})
    void makingAPrivatizedQuestVisibleClearsItsPrivatizedState(boolean throughSave) {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      privatize(hub, new PrivatizeInfo(Visibility.OPEN, "Gone", MAX, Set.of(LEO)), 5000L);

      if (throughSave) save(LEO, hub, "Hub", visibility(Visibility.CLOSED));
      else receive(LEO, PacketCodec.writeUpdateVisibility(hub, Visibility.CLOSED));

      assertEquals(Visibility.CLOSED, stored(hub).visibility());
      assertNull(stored(hub).privatized());
    }
  }

  @Nested
  class JoiningAndRequests {

    @Test
    void joiningGivesRead() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);

      receive(ANNA, PacketCodec.writeJoinQuest(hub));

      assertEquals(Role.READ, roleOf(hub, ANNA));
      assertEquals(List.of(viewOf(ANNA, hub)), outbox.updatesFor(ANNA));
    }

    @ParameterizedTest
    @EnumSource(
        value = Visibility.class,
        names = {"OPEN", "CLOSED"})
    void joiningOrRequestingAHiddenSubquestByIdIsRefused(Visibility visibility) {
      UUID secret = quest(LEO, "Secret", Visibility.PRIVATE);
      UUID inside = subquest(secret, LEO, "Inside", visibility);

      receive(ANNA, PacketCodec.writeJoinQuest(inside));
      receive(ANNA, PacketCodec.writeRequestCollaboration(inside));

      assertNull(roleOf(inside, ANNA));
      assertEquals(0, dm.getPendingRequestCount(LEO));
      assertEquals(List.of(), outbox.packetsFor(ANNA));
      assertEquals(List.of(), outbox.packetsFor(LEO));
    }

    @ParameterizedTest
    @EnumSource(
        value = Visibility.class,
        names = {"OPEN", "CLOSED"})
    void joiningOrRequestingAQuestLockedForThePlayerIsRefused(Visibility visibility) {
      UUID first = quest(LEO, "First", Visibility.OPEN);
      UUID next = quest(LEO, "Next", visibility);
      dm.setPrerequisites(next, List.of(first));

      receive(ANNA, PacketCodec.writeJoinQuest(next));
      receive(ANNA, PacketCodec.writeRequestCollaboration(next));

      assertNull(roleOf(next, ANNA));
      assertEquals(0, dm.getPendingRequestCount(LEO));
      assertEquals(List.of(), outbox.packetsFor(LEO));
    }

    @Test
    void aRequestReachesEveryOwnerAndTheFirstAnswerSettlesIt() {
      UUID hub = quest(LEO, "Hub", Visibility.CLOSED);
      dm.setRole(hub, MAX, Role.OWNER);

      receive(ANNA, PacketCodec.writeRequestCollaboration(hub));

      List<PacketCodec.CollaborationRequestPayload> toLeo = outbox.collaborationRequestsFor(LEO);
      assertEquals(1, toLeo.size());
      assertEquals(
          List.of(
              new PacketCodec.CollaborationRequestPayload(
                  toLeo.get(0).requestId(), hub, "Hub", "Anna")),
          toLeo);
      assertEquals(toLeo, outbox.collaborationRequestsFor(MAX));
      UUID request = toLeo.get(0).requestId();
      outbox.clear();

      receive(MAX, PacketCodec.writeRespondCollaboration(request, true));
      receive(LEO, PacketCodec.writeRespondCollaboration(request, false));

      assertEquals(Role.READ, roleOf(hub, ANNA));
      assertEquals(
          List.of(new PacketCodec.CollaborationResponsePayload(hub, true, viewOf(ANNA, hub))),
          outbox.collaborationResponsesFor(ANNA));
      assertEquals(0, dm.getPendingRequestCount(LEO));
    }

    @Test
    void aReaderMayLeaveWhileTheQuestIsLockedForThem() {
      UUID first = quest(LEO, "First", Visibility.OPEN);
      UUID next = quest(LEO, "Next", Visibility.OPEN);
      dm.setPrerequisites(next, List.of(first));
      dm.setRole(next, JUJU, Role.READ);

      receive(JUJU, PacketCodec.writeLeaveQuest(next));

      assertNull(roleOf(next, JUJU));
    }

    @Test
    void aWriterMayLeave() {
      UUID hub = quest(LEO, "Hub", Visibility.PRIVATE);
      dm.setRole(hub, JUJU, Role.WRITE);
      dm.pinQuest(JUJU, hub);

      receive(JUJU, PacketCodec.writeLeaveQuest(hub));

      assertNull(roleOf(hub, JUJU));
      assertFalse(dm.isQuestPinned(JUJU, hub));
      assertEquals(List.of(hub), outbox.deletesFor(JUJU));
    }
  }
}
