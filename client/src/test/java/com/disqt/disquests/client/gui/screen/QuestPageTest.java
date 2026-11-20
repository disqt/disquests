package com.disqt.disquests.client.gui.screen;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.JUJU;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.data.NoticeText.Message;
import com.disqt.disquests.client.gui.screen.QuestPage.Actions;
import com.disqt.disquests.client.gui.screen.QuestPage.Dialog;
import com.disqt.disquests.client.gui.tree.TreeFixtures.Q;
import com.disqt.disquests.common.graph.Plans.Counts;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QuestPageTest {

  private static final Actions NOTHING =
      new Actions(false, false, false, false, false, false, false, false, false, false);

  private static Message msg(String key, Object... args) {
    return new Message(key, List.of(args));
  }

  /** A quest owned by MAX, with JUJU in the given role (none when null). */
  private static Q questWith(Role juju) {
    Q q = quest("Gare").owner(MAX);
    if (juju != null) q.role(JUJU, juju);
    return q;
  }

  @Nested
  class ActionsByRole {

    static Stream<Arguments> activeQuest() {
      return Stream.of(
          Arguments.of(
              null,
              new Actions(true, false, false, false, false, false, false, false, false, false)),
          Arguments.of(
              Role.READ,
              new Actions(false, false, true, false, false, false, false, false, false, false)),
          Arguments.of(
              Role.WRITE,
              new Actions(false, true, true, false, false, false, false, false, false, false)),
          Arguments.of(
              Role.OWNER,
              new Actions(false, true, true, true, false, true, true, false, false, false)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("activeQuest")
    void activeQuest(Role role, Actions expected) {
      assertEquals(expected, QuestPage.actions(questWith(role).build(), JUJU, true, true));
    }

    static Stream<Arguments> completedQuest() {
      return Stream.of(
          Arguments.of(null, NOTHING),
          Arguments.of(Role.READ, NOTHING),
          Arguments.of(Role.WRITE, NOTHING),
          Arguments.of(
              Role.OWNER,
              new Actions(false, false, false, false, true, true, false, false, false, false)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("completedQuest")
    void completedQuest(Role role, Actions expected) {
      assertEquals(
          expected, QuestPage.actions(questWith(role).completed().build(), JUJU, true, true));
    }

    @Test
    void theLastOwnerCannotLeave() {
      Actions a = QuestPage.actions(quest("Gare").owner(LEO).build(), LEO, true, true);
      assertEquals(
          new Actions(false, true, false, true, false, true, true, false, false, false), a);
    }

    @Test
    void anOwnerWithACoOwnerCanLeave() {
      QuestData q = quest("Gare").owner(LEO).coOwner(MAX).build();
      assertEquals(true, QuestPage.actions(q, LEO, true, true).leave());
      assertEquals(true, QuestPage.actions(q, MAX, true, true).leave());
    }

    @Test
    void anOlderServerStillLetsWriteCompleteAndReopen() {
      assertEquals(
          true, QuestPage.actions(questWith(Role.WRITE).build(), JUJU, true, false).complete());
      assertEquals(
          true,
          QuestPage.actions(questWith(Role.WRITE).completed().build(), JUJU, true, false).reopen());
    }

    @Test
    void anOlderServerOffersNoPeopleScreenOnTheReadingView() {
      // 0.8.0 reached the contributors from the editor only
      assertEquals(
          false,
          QuestPage.actions(quest("Gare").owner(LEO).build(), LEO, true, false).managePeople());
    }

    @Test
    void noCompletionBeforeV3() {
      Actions a = QuestPage.actions(quest("Gare").owner(LEO).build(), LEO, false, false);
      assertEquals(false, a.complete());
      assertEquals(
          false,
          QuestPage.actions(quest("Gare").owner(LEO).completed().build(), LEO, false, false)
              .reopen());
    }
  }

  @Nested
  class Locked {

    @Test
    void aReaderMayOnlyLeave() {
      QuestData q = questWith(Role.READ).after("Dig").locked().build();
      assertEquals(
          new Actions(false, false, true, false, false, false, false, false, false, false),
          QuestPage.actions(q, JUJU, true, true));
    }

    @Test
    void aStrangerGetsNoButtonsNotEvenJoin() {
      QuestData q = questWith(null).after("Dig").locked().build();
      assertEquals(NOTHING, QuestPage.actions(q, JUJU, true, true));
    }
  }

  @Nested
  class PrerequisitesSection {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("write with a prerequisite", questWith(Role.WRITE).after("Dig"), true),
          Arguments.of("owner, hidden only", questWith(Role.OWNER).hiddenPrerequisites(), true),
          Arguments.of("owner, deleted only", questWith(Role.OWNER).deletedPrerequisite(), true),
          Arguments.of("owner, none", questWith(Role.OWNER), false),
          Arguments.of("read with a prerequisite", questWith(Role.READ).after("Dig"), false),
          Arguments.of("stranger with a prerequisite", questWith(null).after("Dig"), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void shownToWriteAndOwnerWhenThereAreLinks(String name, Q q, boolean shown) {
      assertEquals(shown, QuestPage.actions(q.build(), JUJU, true, true).prerequisites());
    }
  }

  @Nested
  class Banners {

    @Test
    void ownersSeeTheDeletedPrerequisiteBanner() {
      assertEquals(
          true,
          QuestPage.actions(questWith(Role.OWNER).deletedPrerequisite().build(), JUJU, true, true)
              .deletedPrerequisiteBanner());
      assertEquals(
          false,
          QuestPage.actions(questWith(Role.WRITE).deletedPrerequisite().build(), JUJU, true, true)
              .deletedPrerequisiteBanner());
    }

    @Test
    void titledWhenTheOwnerSawTheDeletedQuest() {
      Privatized p = new Privatized(Visibility.OPEN, "Intérieurs", "Leo", 5L);
      assertEquals(
          msg(
              "gui.disquests.banner.privatized_titled",
              "15 Nov",
              "Intérieurs",
              "Leo",
              msg("gui.disquests.visibility.open")),
          QuestPage.privatizedBanner(p, "15 Nov"));
    }

    @Test
    void untitledWhenTheOwnerCouldNotSeeIt() {
      Privatized p = new Privatized(Visibility.CLOSED, null, null, 5L);
      assertEquals(
          msg("gui.disquests.banner.privatized", "15 Nov", msg("gui.disquests.visibility.closed")),
          QuestPage.privatizedBanner(p, "15 Nov"));
    }

    @Test
    void untitledWhenTheNameIsMissing() {
      Privatized p = new Privatized(Visibility.OPEN, "Intérieurs", null, 5L);
      assertEquals(
          msg("gui.disquests.banner.privatized", "15 Nov", msg("gui.disquests.visibility.open")),
          QuestPage.privatizedBanner(p, "15 Nov"));
    }

    @Test
    void restoreButtonNamesThePreviousSetting() {
      assertEquals(
          msg("gui.disquests.btn.restore_visibility", msg("gui.disquests.visibility.open")),
          QuestPage.restoreVisibility(Visibility.OPEN));
    }

    @Test
    void deletedPrerequisiteBannerText() {
      assertEquals(
          msg("gui.disquests.banner.deleted_prerequisite"), QuestPage.deletedPrerequisiteBanner());
    }

    @Test
    void privatizedBannerOnlyForOwners() {
      Privatized p = new Privatized(Visibility.OPEN, null, null, 5L);
      for (Role role : new Role[] {null, Role.READ, Role.WRITE, Role.OWNER}) {
        QuestData q = withPrivatized(questWith(role).privateQuest().build(), p);
        assertEquals(
            role == Role.OWNER,
            QuestPage.actions(q, JUJU, true, true).privatizedBanner(),
            String.valueOf(role));
      }
    }
  }

  @Nested
  class Dialogs {

    static Stream<Arguments> complete() {
      return Stream.of(
          Arguments.of(new Counts(0, 0), new Dialog(null, null)),
          Arguments.of(
              new Counts(1, 0),
              new Dialog(msg("gui.disquests.confirm.also_complete_one", 1), null)),
          Arguments.of(
              new Counts(3, 0),
              new Dialog(msg("gui.disquests.confirm.also_complete_many", 3), null)),
          Arguments.of(
              new Counts(0, 1),
              new Dialog(null, msg("gui.disquests.confirm.others_stay_active_one", 1))),
          Arguments.of(
              new Counts(2, 2),
              new Dialog(
                  msg("gui.disquests.confirm.also_complete_many", 2),
                  msg("gui.disquests.confirm.others_stay_active_many", 2))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("complete")
    void completeDialog(Counts counts, Dialog expected) {
      assertEquals(expected, QuestPage.completeDialog(counts));
    }

    static Stream<Arguments> delete() {
      return Stream.of(
          Arguments.of(new Counts(0, 0), new Dialog(null, null)),
          Arguments.of(
              new Counts(1, 1),
              new Dialog(
                  msg("gui.disquests.confirm.also_delete_one", 1),
                  msg("gui.disquests.confirm.others_kept_one", 1))),
          Arguments.of(
              new Counts(4, 0), new Dialog(msg("gui.disquests.confirm.also_delete_many", 4), null)),
          Arguments.of(
              new Counts(0, 5),
              new Dialog(null, msg("gui.disquests.confirm.others_kept_many", 5))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("delete")
    void deleteDialog(Counts counts, Dialog expected) {
      assertEquals(expected, QuestPage.deleteDialog(counts));
    }
  }

  @Nested
  class People {

    @Test
    void ownersFirstThenWriteThenRead() {
      QuestData q =
          quest("Gare").owner(LEO).reader(MAX).writer(JUJU).coOwner(new UUID(0, 9)).build();
      assertEquals(
          List.of(
              new ContributorData(LEO, "p1", Role.OWNER),
              new ContributorData(new UUID(0, 9), "p9", Role.OWNER),
              new ContributorData(JUJU, "p3", Role.WRITE),
              new ContributorData(MAX, "p2", Role.READ)),
          QuestPage.people(q));
    }

    @Test
    void theDisplayOwnerIsListedOnceWhenAlsoAContributor() {
      QuestData q = quest("Gare").owner(LEO).coOwner(LEO).build();
      assertEquals(List.of(new ContributorData(LEO, "p1", Role.OWNER)), QuestPage.people(q));
    }

    @ParameterizedTest
    @MethodSource("roles")
    void roleLabel(Role role, String key) {
      assertEquals(msg(key), QuestPage.role(role));
    }

    static Stream<Arguments> roles() {
      return Stream.of(
          Arguments.of(Role.OWNER, "gui.disquests.role.owner"),
          Arguments.of(Role.WRITE, "gui.disquests.role.write"),
          Arguments.of(Role.READ, "gui.disquests.role.read"),
          Arguments.of(null, "gui.disquests.role.none"));
    }
  }

  private static QuestData withPrivatized(QuestData q, Privatized p) {
    return new QuestData(
        q.id(),
        q.title(),
        q.content(),
        q.ownerUuid(),
        q.ownerName(),
        q.visibility(),
        q.contributors(),
        q.lastModified(),
        q.coordinates(),
        q.isRegion(),
        q.coordinates2(),
        q.map(),
        q.tags(),
        q.polygon(),
        q.completedAt(),
        q.completedBy(),
        q.completedByName(),
        q.parentId(),
        q.prerequisites(),
        q.hiddenPrerequisites(),
        q.deletedPrerequisite(),
        q.hiddenParent(),
        q.autoJoin(),
        q.locked(),
        p);
  }
}
