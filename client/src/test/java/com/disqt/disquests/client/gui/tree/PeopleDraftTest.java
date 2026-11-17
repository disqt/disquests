package com.disqt.disquests.client.gui.tree;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.ANNA;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.JUJU;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.tree.PeopleDraft.Person;
import com.disqt.disquests.common.model.PersonOp;
import com.disqt.disquests.common.model.PersonOp.Kind;
import com.disqt.disquests.common.model.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PeopleDraftTest {

  private static final UUID NEWCOMER = new UUID(0, 5);

  /** Leo is the display owner; Juju reads, Max owns, Anna writes. */
  private static PeopleDraft draft() {
    return new PeopleDraft(
        Quest.fromNetwork(quest("hub").reader(JUJU).coOwner(MAX).writer(ANNA).build()));
  }

  private static Person person(UUID uuid, Role role, boolean removed, boolean dirty) {
    return new Person(uuid, "p" + uuid.getLeastSignificantBits(), role, removed, dirty);
  }

  private static PersonOp setRole(UUID uuid, Role role) {
    return new PersonOp(Kind.SET_ROLE, uuid, role);
  }

  @Nested
  class Order {

    @Test
    void ownersFirstThenByRoleKeepingTheIncomingOrder() {
      assertEquals(
          List.of(
              person(LEO, Role.OWNER, false, false),
              person(MAX, Role.OWNER, false, false),
              person(ANNA, Role.WRITE, false, false),
              person(JUJU, Role.READ, false, false)),
          draft().rows());
    }

    @Test
    void aRoleChangeDoesNotMoveTheRow() {
      PeopleDraft d = draft();
      d.setRole(JUJU, Role.OWNER);
      d.setRole(MAX, Role.READ);
      assertEquals(
          List.of(
              person(LEO, Role.OWNER, false, false),
              person(MAX, Role.READ, false, true),
              person(ANNA, Role.WRITE, false, false),
              person(JUJU, Role.OWNER, false, true)),
          d.rows());
    }

    @Test
    void anAddedPersonGoesLast() {
      PeopleDraft d = draft();
      d.add(NEWCOMER, "p5", Role.OWNER);
      assertEquals(person(NEWCOMER, Role.OWNER, false, true), d.rows().get(4));
    }
  }

  @Nested
  class Ops {

    @Test
    void nothingChangedSendsNothing() {
      PeopleDraft d = draft();
      d.setRole(JUJU, Role.WRITE);
      d.setRole(JUJU, Role.READ);
      d.remove(ANNA, false);
      d.undoRemove(ANNA);
      assertEquals(List.of(), d.ops());
      assertFalse(d.isDirty(JUJU));
      assertFalse(d.isDirty(ANNA));
    }

    @Test
    void anAddedThenRemovedPersonIsDropped() {
      PeopleDraft d = draft();
      d.add(NEWCOMER, "p5", Role.READ);
      d.remove(NEWCOMER, false);
      assertEquals(List.of(), d.ops());
      assertEquals(4, d.rows().size());
      assertFalse(d.isDirty(NEWCOMER));
    }

    @Test
    void removalsSayWhetherTheyReachMySubquests() {
      PeopleDraft d = draft();
      d.remove(ANNA, true);
      d.remove(JUJU, false);
      assertEquals(
          List.of(
              new PersonOp(Kind.REMOVE_WITH_MY_SUBQUESTS, ANNA, null),
              new PersonOp(Kind.REMOVE, JUJU, null)),
          d.ops());
      assertEquals(person(ANNA, Role.WRITE, true, true), d.rows().get(2));
    }

    @Test
    void readdingARemovedPersonIsARoleChange() {
      PeopleDraft d = draft();
      d.remove(JUJU, false);
      d.add(JUJU, "p3", Role.WRITE);
      assertEquals(List.of(setRole(JUJU, Role.WRITE)), d.ops());
      assertEquals(person(JUJU, Role.WRITE, false, true), d.rows().get(3));
    }

    @Test
    void promotionsToOwnerFirstAndMyOwnChangeLast() {
      PeopleDraft d = draft();
      d.setRole(LEO, Role.WRITE);
      d.remove(ANNA, false);
      d.add(NEWCOMER, "p5", Role.OWNER);
      d.setRole(JUJU, Role.OWNER);
      assertEquals(
          List.of(
              setRole(JUJU, Role.OWNER),
              setRole(NEWCOMER, Role.OWNER),
              new PersonOp(Kind.REMOVE, ANNA, null),
              setRole(LEO, Role.WRITE)),
          d.ops(LEO));
    }
  }

  @Nested
  class Merge {

    /** The quest as the server saves it meanwhile: Leo owns, plus the given people. */
    private static Quest saved(TreeFixtures.Q q) {
      return Quest.fromNetwork(q.build());
    }

    @Test
    void someoneNewOnTheServerComesLastAsASavedRow() {
      PeopleDraft d = draft();
      d.merge(saved(quest("hub").reader(JUJU).coOwner(MAX).writer(ANNA).reader(NEWCOMER)));
      assertEquals(person(NEWCOMER, Role.READ, false, false), d.rows().get(4));
      assertEquals(List.of(), d.ops());
    }

    @Test
    void aSavedRowTheServerDroppedGoes() {
      PeopleDraft d = draft();
      d.merge(saved(quest("hub").coOwner(MAX).writer(ANNA)));
      assertEquals(
          List.of(
              person(LEO, Role.OWNER, false, false),
              person(MAX, Role.OWNER, false, false),
              person(ANNA, Role.WRITE, false, false)),
          d.rows());
    }

    @Test
    void aRowRemovedInTheDraftAndOnTheServerGoes() {
      PeopleDraft d = draft();
      d.remove(JUJU, true);
      d.merge(saved(quest("hub").coOwner(MAX).writer(ANNA)));
      assertEquals(3, d.rows().size());
      assertEquals(List.of(), d.ops());
    }

    @Test
    void aRoleChangedInTheDraftStaysWhenTheServerDroppedThePerson() {
      PeopleDraft d = draft();
      d.setRole(JUJU, Role.WRITE);
      d.merge(saved(quest("hub").coOwner(MAX).writer(ANNA)));
      assertEquals(person(JUJU, Role.WRITE, false, true), d.rows().get(3));
      assertTrue(d.isNew(JUJU));
      assertEquals(List.of(setRole(JUJU, Role.WRITE)), d.ops());
    }

    @Test
    void anUnchangedRowFollowsTheServerRoleInPlace() {
      PeopleDraft d = draft();
      d.merge(saved(quest("hub").writer(JUJU).coOwner(MAX).writer(ANNA)));
      assertEquals(person(JUJU, Role.WRITE, false, false), d.rows().get(3));
      assertEquals(List.of(), d.ops());
    }

    @Test
    void aDraftRoleTheServerNowHasIsNoLongerUnsaved() {
      PeopleDraft d = draft();
      d.setRole(JUJU, Role.WRITE);
      d.merge(saved(quest("hub").writer(JUJU).coOwner(MAX).writer(ANNA)));
      assertFalse(d.isDirty(JUJU));
      assertEquals(List.of(), d.ops());
    }

    @Test
    void aDraftRoleTheServerDoesNotHaveStays() {
      PeopleDraft d = draft();
      d.setRole(JUJU, Role.OWNER);
      d.merge(saved(quest("hub").writer(JUJU).coOwner(MAX).writer(ANNA)));
      assertEquals(person(JUJU, Role.OWNER, false, true), d.rows().get(3));
      assertEquals(List.of(setRole(JUJU, Role.OWNER)), d.ops());
    }

    @Test
    void aRemovalStaysWhileTheServerStillHasThePerson() {
      PeopleDraft d = draft();
      d.remove(ANNA, true);
      d.merge(saved(quest("hub").reader(JUJU).coOwner(MAX).writer(ANNA).reader(NEWCOMER)));
      assertEquals(person(ANNA, Role.WRITE, true, true), d.rows().get(2));
      assertEquals(List.of(new PersonOp(Kind.REMOVE_WITH_MY_SUBQUESTS, ANNA, null)), d.ops());
    }

    @Test
    void aDraftAdditionTheServerAcceptedMeanwhileKeepsItsPlaceAndRole() {
      PeopleDraft d = draft();
      d.add(NEWCOMER, "p5", Role.WRITE);
      d.merge(saved(quest("hub").reader(JUJU).coOwner(MAX).writer(ANNA).reader(NEWCOMER)));
      assertEquals(person(NEWCOMER, Role.WRITE, false, true), d.rows().get(4));
      assertFalse(d.isNew(NEWCOMER));
      assertEquals(List.of(setRole(NEWCOMER, Role.WRITE)), d.ops());
    }

    @Test
    void aDraftAdditionWithTheRoleTheServerGaveIsSaved() {
      PeopleDraft d = draft();
      d.add(NEWCOMER, "p5", Role.READ);
      d.merge(saved(quest("hub").reader(JUJU).coOwner(MAX).writer(ANNA).reader(NEWCOMER)));
      assertEquals(person(NEWCOMER, Role.READ, false, false), d.rows().get(4));
      assertEquals(5, d.rows().size());
    }
  }

  @Nested
  class IsNew {

    @Test
    void onlyAPersonAddedInTheDraft() {
      PeopleDraft d = draft();
      d.add(NEWCOMER, "p5", Role.READ);
      d.remove(JUJU, false);
      d.add(JUJU, "p3", Role.WRITE);
      assertTrue(d.isNew(NEWCOMER));
      assertFalse(d.isNew(JUJU));
      assertFalse(d.isNew(LEO));
    }
  }

  @Nested
  class Owners {

    @Test
    void keepsAnOwnerWhileOneIsLeft() {
      PeopleDraft d = draft();
      d.remove(MAX, false);
      assertTrue(d.keepsAnOwner());
      d.setRole(LEO, Role.READ);
      assertFalse(d.keepsAnOwner());
      d.add(NEWCOMER, "p5", Role.OWNER);
      assertTrue(d.keepsAnOwner());
    }

    @Test
    void givingUpMyOwnOwnerByDemotionOrRemoval() {
      PeopleDraft d = draft();
      assertFalse(d.givesUpOwnOwner(LEO));
      d.setRole(LEO, Role.WRITE);
      assertTrue(d.givesUpOwnOwner(LEO));
      d.setRole(LEO, Role.OWNER);
      d.remove(LEO, false);
      assertTrue(d.givesUpOwnOwner(LEO));
      d.undoRemove(LEO);
      assertFalse(d.givesUpOwnOwner(LEO));
    }

    @Test
    void someoneWhoWasNotAnOwnerGivesNothingUp() {
      PeopleDraft d = draft();
      d.setRole(JUJU, Role.OWNER);
      d.remove(JUJU, false);
      assertFalse(d.givesUpOwnOwner(JUJU));
    }
  }
}
