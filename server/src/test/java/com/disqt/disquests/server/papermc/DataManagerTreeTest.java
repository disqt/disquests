package com.disqt.disquests.server.papermc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.graph.Plans.DeletePlan;
import com.disqt.disquests.common.graph.Plans.PrivatizeInfo;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DataManagerTreeTest {

  @TempDir Path tempDir;

  private DataManager dm;

  private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID DAVE = UUID.fromString("00000000-0000-0000-0000-000000000004");

  @BeforeEach
  void setUp() {
    dm = new DataManager(tempDir);
    dm.initialize();
    dm.upsertPlayerName(ALICE, "Alice");
    dm.upsertPlayerName(BOB, "Bob");
    dm.upsertPlayerName(CAROL, "Carol");
    dm.upsertPlayerName(DAVE, "Dave");
  }

  @AfterEach
  void tearDown() {
    dm.close();
  }

  private UUID quest(UUID owner, String title, Visibility visibility) {
    UUID id = UUID.randomUUID();
    dm.saveQuest(
        new QuestData(
            id,
            title,
            "",
            owner,
            null,
            visibility,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of()));
    return id;
  }

  private UUID quest(UUID owner, String title) {
    return quest(owner, title, Visibility.OPEN);
  }

  private int rows(String sql) {
    try (java.sql.Connection c =
            java.sql.DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("disquests.db"));
        java.sql.Statement st = c.createStatement();
        java.sql.ResultSet rs = st.executeQuery(sql)) {
      return rs.next() ? rs.getInt(1) : -1;
    } catch (java.sql.SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private QuestData fromAll(UUID id) {
    return dm.getAllQuests().stream().filter(q -> q.id().equals(id)).findFirst().orElseThrow();
  }

  @Nested
  class Tree {
    @Test
    void parentAndPrerequisitesRoundTrip() {
      UUID root = quest(ALICE, "root");
      UUID dep1 = quest(ALICE, "dep1");
      UUID dep2 = quest(ALICE, "dep2");
      UUID child = quest(ALICE, "child");

      dm.setParent(child, root);
      dm.setPrerequisites(child, List.of(dep2, dep1));
      dm.setAutoJoin(child, true);

      QuestData stored = dm.getQuest(child);
      assertEquals(root, stored.parentId());
      assertEquals(List.of(dep2, dep1), stored.prerequisites());
      assertTrue(stored.autoJoin());
      assertFalse(stored.locked());
      assertFalse(stored.hiddenParent());
      assertFalse(stored.hiddenPrerequisites());
      assertFalse(stored.deletedPrerequisite());
      assertEquals(stored, fromAll(child));
    }

    @Test
    void setParentNullMakesTopLevelAndSetPrerequisitesReplaces() {
      UUID root = quest(ALICE, "root");
      UUID dep = quest(ALICE, "dep");
      UUID child = quest(ALICE, "child");
      dm.setParent(child, root);
      dm.setPrerequisites(child, List.of(dep));

      dm.setParent(child, null);
      dm.setPrerequisites(child, List.of());

      QuestData stored = dm.getQuest(child);
      assertNull(stored.parentId());
      assertEquals(List.of(), stored.prerequisites());
    }

    @Test
    void saveQuestKeepsParentPrerequisitesAndPeople() {
      UUID root = quest(ALICE, "root");
      UUID dep = quest(ALICE, "dep");
      UUID child = quest(ALICE, "child");
      dm.setParent(child, root);
      dm.setPrerequisites(child, List.of(dep));
      dm.setRole(child, BOB, Role.WRITE);

      dm.saveQuest(dm.getQuest(child).withContent("edited"));

      QuestData stored = dm.getQuest(child);
      assertEquals("edited", stored.content());
      assertEquals(root, stored.parentId());
      assertEquals(List.of(dep), stored.prerequisites());
      assertEquals(Role.WRITE, stored.roleOf(BOB));
    }

    @Test
    void deletingAPrerequisiteLeavesTheDanglingId() {
      UUID dep = quest(ALICE, "dep");
      UUID child = quest(ALICE, "child");
      dm.setPrerequisites(child, List.of(dep));

      assertTrue(dm.deleteQuest(dep));

      assertEquals(List.of(dep), dm.getQuest(child).prerequisites());
    }

    @Test
    void deletingAQuestRemovesItsPeopleAndOutgoingLinks() {
      UUID dep = quest(ALICE, "dep");
      UUID child = quest(ALICE, "child");
      dm.setPrerequisites(child, List.of(dep));
      dm.setRole(child, BOB, Role.READ);

      dm.deleteQuest(child);

      assertNull(dm.getQuest(child));
      assertEquals(List.of(), dm.getQuestsForPlayer(BOB));
      assertEquals(List.of(), dm.getContributors(child));
      assertEquals(0, rows("SELECT COUNT(*) FROM quest_links WHERE from_id = '" + child + "'"));
      assertEquals(0, rows("SELECT COUNT(*) FROM quest_people WHERE quest_id = '" + child + "'"));
    }

    @Test
    void loadGraphBuildsTheGraphFromStoredTruth() {
      UUID root = quest(ALICE, "root");
      UUID child = quest(ALICE, "child");
      dm.setParent(child, root);

      QuestGraph graph = dm.loadGraph();

      assertEquals(List.of(child), graph.children(root).stream().map(QuestData::id).toList());
    }
  }

  @Nested
  class People {
    @Test
    void everyPersonComesBackWithRoleOrderedByAddedAt() {
      UUID id = quest(ALICE, "q");
      dm.setRole(id, CAROL, Role.WRITE);
      dm.setRole(id, BOB, Role.OWNER);
      dm.setRole(id, DAVE, Role.READ);

      QuestData stored = dm.getQuest(id);

      assertEquals(ALICE, stored.ownerUuid());
      assertEquals("Alice", stored.ownerName());
      assertEquals(
          List.of(
              new ContributorData(CAROL, "Carol", Role.WRITE),
              new ContributorData(BOB, "Bob", Role.OWNER),
              new ContributorData(DAVE, "Dave", Role.READ)),
          stored.contributors());
      assertEquals(List.of(ALICE, BOB), stored.owners());
    }

    @Test
    void unknownNamesAreNull() {
      UUID stranger = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
      UUID id = quest(ALICE, "q");
      dm.setRole(id, stranger, Role.READ);

      assertEquals(
          List.of(new ContributorData(stranger, null, Role.READ)), dm.getQuest(id).contributors());
    }

    @Test
    void displayOwnerMovesToTheEarliestRemainingOwnerWhenDemoted() {
      UUID id = quest(ALICE, "q");
      dm.setRole(id, BOB, Role.OWNER);
      dm.setRole(id, CAROL, Role.OWNER);

      dm.setRole(id, ALICE, Role.WRITE);

      QuestData stored = dm.getQuest(id);
      assertEquals(BOB, stored.ownerUuid());
      assertEquals("Bob", stored.ownerName());
      assertEquals(Role.WRITE, stored.roleOf(ALICE));
      assertEquals(Role.OWNER, stored.roleOf(CAROL));
    }

    @Test
    void displayOwnerMovesToTheEarliestRemainingOwnerWhenRemoved() {
      UUID id = quest(ALICE, "q");
      dm.setRole(id, BOB, Role.OWNER);
      dm.setRole(id, CAROL, Role.OWNER);

      dm.removePerson(id, ALICE);

      QuestData stored = dm.getQuest(id);
      assertEquals(BOB, stored.ownerUuid());
      assertNull(stored.roleOf(ALICE));
    }

    @Test
    void displayOwnerStaysWhenAnotherOwnerIsRemoved() {
      UUID id = quest(ALICE, "q");
      dm.setRole(id, BOB, Role.OWNER);

      dm.removePerson(id, BOB);

      assertEquals(ALICE, dm.getQuest(id).ownerUuid());
      assertEquals(List.of(), dm.getQuest(id).contributors());
    }

    @Test
    void setPeopleReplacesEveryoneAndSetsTheDisplayOwner() {
      UUID id = quest(ALICE, "q");
      dm.setRole(id, BOB, Role.WRITE);

      dm.setPeople(
          id,
          List.of(
              new ContributorData(ALICE, "Alice", Role.OWNER),
              new ContributorData(DAVE, "Dave", Role.READ)),
          CAROL);

      QuestData stored = dm.getQuest(id);
      assertEquals(CAROL, stored.ownerUuid());
      assertEquals(Role.OWNER, stored.roleOf(ALICE));
      assertEquals(Role.READ, stored.roleOf(DAVE));
      assertNull(stored.roleOf(BOB));
      assertEquals(List.of(CAROL, ALICE), stored.owners());
    }

    @Test
    void setPeopleRefusesADisplayOwnerWhoIsNotAnOwner() {
      UUID id = quest(ALICE, "q");

      assertThrows(
          IllegalArgumentException.class,
          () -> dm.setPeople(id, List.of(new ContributorData(BOB, "Bob", Role.WRITE)), BOB));

      assertEquals(ALICE, dm.getQuest(id).ownerUuid());
    }

    @Test
    void setPeopleKeepsTheDisplayOwnerOutOfTheContributors() {
      UUID id = quest(ALICE, "q");

      dm.setPeople(id, List.of(new ContributorData(BOB, "Bob", Role.WRITE)), ALICE);

      assertEquals(
          List.of(new ContributorData(BOB, "Bob", Role.WRITE)), dm.getQuest(id).contributors());
    }

    @Test
    void legacyContributorMethodsStillWorkOnRoles() {
      UUID id = quest(ALICE, "q");

      dm.addContributor(id, BOB, true);
      dm.addContributor(id, CAROL, false);
      assertEquals(Role.WRITE, dm.getQuest(id).roleOf(BOB));
      assertEquals(Role.READ, dm.getQuest(id).roleOf(CAROL));
      assertTrue(dm.isContributor(id, BOB));
      assertFalse(dm.isContributor(id, ALICE));

      dm.updateContributor(id, CAROL, true);
      assertEquals(Role.WRITE, dm.getQuest(id).roleOf(CAROL));

      dm.leaveQuest(id, BOB);
      assertFalse(dm.isContributor(id, BOB));
      assertEquals(List.of(id), dm.getQuestsForPlayer(CAROL).stream().map(QuestData::id).toList());
      assertEquals(List.of(), dm.getQuestsForPlayer(BOB));
      assertEquals(List.of(id), dm.getServerQuests(BOB).stream().map(QuestData::id).toList());
    }

    @Test
    void legacyContributorMethodsNeverDemoteAnOwner() {
      UUID id = quest(ALICE, "q");
      dm.setRole(id, BOB, Role.OWNER);

      dm.addContributor(id, BOB, false);
      dm.updateContributor(id, BOB, false);
      dm.addContributor(id, ALICE, false);
      dm.removeContributor(id, ALICE);

      assertEquals(Role.OWNER, dm.getQuest(id).roleOf(BOB));
      assertEquals(Role.OWNER, dm.getQuest(id).roleOf(ALICE));
    }

    @Test
    void pendingCountsReachEveryOwner() {
      UUID id = quest(ALICE, "q", Visibility.CLOSED);
      dm.setRole(id, BOB, Role.OWNER);
      dm.createCollaborationRequest(id, DAVE);

      assertEquals(1, dm.getPendingRequestCount(ALICE));
      assertEquals(1, dm.getPendingRequestCount(BOB));
      assertEquals(Map.of(id, 1), dm.getPendingCountByQuest(BOB));
      assertEquals(1, dm.getPendingRequestsForOwner(BOB).size());

      dm.setRole(id, CAROL, Role.WRITE);
      assertEquals(0, dm.getPendingRequestCount(CAROL));
      assertEquals(Map.of(), dm.getPendingCountByQuest(CAROL));
    }
  }

  @Nested
  class Privatization {
    @Test
    void setPrivatizedStoresEverythingAndMakesTheQuestPrivate() {
      UUID id = quest(ALICE, "q", Visibility.OPEN);

      dm.setPrivatized(
          id, new PrivatizeInfo(Visibility.OPEN, "Old parent", BOB, Set.of(ALICE, CAROL)), 9000L);

      QuestData stored = dm.getQuest(id);
      assertEquals(Visibility.PRIVATE, stored.visibility());
      assertEquals(
          new Privatized(Visibility.OPEN, "Old parent", "Bob", 9000L, Set.of(ALICE, CAROL)),
          stored.privatized());
      assertEquals(Set.of(ALICE, CAROL), dm.privatizedSeenBy(id));
    }

    @Test
    void clearPrivatizedLeavesVisibilityAlone() {
      UUID id = quest(ALICE, "q", Visibility.CLOSED);
      dm.setPrivatized(id, new PrivatizeInfo(Visibility.CLOSED, "P", BOB, Set.of()), 1L);

      dm.clearPrivatized(id);

      QuestData stored = dm.getQuest(id);
      assertNull(stored.privatized());
      assertEquals(Visibility.PRIVATE, stored.visibility());
      assertEquals(Set.of(), dm.privatizedSeenBy(id));
    }

    @Test
    void privatizedWithNoOneWhoSawItRoundTrips() {
      UUID id = quest(ALICE, "q", Visibility.OPEN);
      dm.setPrivatized(id, new PrivatizeInfo(Visibility.OPEN, "P", BOB, Set.of()), 5L);

      assertEquals(
          new Privatized(Visibility.OPEN, "P", "Bob", 5L, Set.of()), dm.getQuest(id).privatized());
    }
  }

  @Nested
  class Plans {
    @Test
    void applyDeletePlanDeletesRehomesPrivatizesAndRemovesCompletedPrerequisites() {
      UUID grand = quest(ALICE, "grand");
      UUID doomed = quest(ALICE, "doomed");
      UUID kid = quest(ALICE, "kid", Visibility.OPEN);
      UUID doneDep = quest(ALICE, "done dep");
      UUID openDep = quest(ALICE, "open dep");
      UUID dependent = quest(ALICE, "dependent");
      UUID toTop = quest(ALICE, "to top");
      dm.setParent(doomed, grand);
      dm.setParent(kid, doomed);
      dm.setParent(toTop, doomed);
      dm.setPrerequisites(dependent, List.of(doneDep, openDep));
      dm.setPrerequisites(doomed, List.of(openDep));
      dm.setRole(kid, BOB, Role.READ);

      DeletePlan plan =
          new DeletePlan(
              Set.of(doomed, doneDep, openDep),
              Map.of(kid, Optional.of(grand), toTop, Optional.empty()),
              Map.of(kid, new PrivatizeInfo(Visibility.OPEN, "doomed", BOB, Set.of(ALICE))),
              Map.of(dependent, List.of(doneDep)));

      dm.applyDeletePlan(plan, 4000L);

      assertNull(dm.getQuest(doomed));
      assertNull(dm.getQuest(doneDep));
      assertNull(dm.getQuest(openDep));
      QuestData kidStored = dm.getQuest(kid);
      assertEquals(grand, kidStored.parentId());
      assertNull(dm.getQuest(toTop).parentId());
      assertEquals(Visibility.PRIVATE, kidStored.visibility());
      assertEquals(
          new Privatized(Visibility.OPEN, "doomed", "Bob", 4000L, Set.of(ALICE)),
          kidStored.privatized());
      // The completed prerequisite is taken out; the unfinished one dangles.
      assertEquals(List.of(openDep), dm.getQuest(dependent).prerequisites());
      assertEquals(Role.READ, kidStored.roleOf(BOB));
    }

    @Test
    void applyDeletePlanIsAtomic() {
      UUID a = quest(ALICE, "a");
      UUID child = quest(ALICE, "child", Visibility.OPEN);
      dm.setParent(child, a);

      // A null previous visibility cannot be stored, so the privatize step fails after the
      // reparent step already ran.
      DeletePlan broken =
          new DeletePlan(
              Set.of(a),
              Map.of(child, Optional.empty()),
              Map.of(child, new PrivatizeInfo(null, "a", ALICE, Set.of())),
              Map.of());

      assertThrows(RuntimeException.class, () -> dm.applyDeletePlan(broken, 1L));

      assertEquals(a, dm.getQuest(child).parentId());
      assertEquals(Visibility.OPEN, dm.getQuest(child).visibility());
      assertEquals("a", dm.getQuest(a).title());
    }

    @Test
    void applyDeletePlanKeepsLinksNotListedForRemoval() {
      UUID dep = quest(ALICE, "dep");
      UUID dependent = quest(ALICE, "dependent");
      dm.setPrerequisites(dependent, List.of(dep));

      dm.applyDeletePlan(new DeletePlan(Set.of(dep), Map.of(), Map.of(), Map.of()), 1L);

      assertEquals(List.of(dep), dm.getQuest(dependent).prerequisites());
    }

    @Test
    void completeQuestsCompletesEachAndDropsPinsAndRequests() {
      UUID q1 = quest(ALICE, "q1", Visibility.CLOSED);
      UUID q2 = quest(ALICE, "q2", Visibility.CLOSED);
      UUID q3 = quest(ALICE, "q3", Visibility.CLOSED);
      dm.pinQuest(BOB, q1);
      dm.pinQuest(BOB, q2);
      dm.pinQuest(BOB, q3);
      dm.createCollaborationRequest(q1, DAVE);
      dm.createCollaborationRequest(q2, DAVE);

      dm.completeQuests(List.of(q1, q2), ALICE, 7777L);

      assertEquals(7777L, dm.getQuest(q1).completedAt());
      assertEquals(ALICE, dm.getQuest(q2).completedBy());
      assertEquals("Alice", dm.getQuest(q2).completedByName());
      assertEquals(0L, dm.getQuest(q3).completedAt());
      assertEquals(List.of(q3), dm.getPinnedQuestIds(BOB));
      assertEquals(0, dm.getPendingRequestCount(ALICE));
    }
  }

  @Nested
  class MaxQuestDepth {
    @ParameterizedTest
    @CsvSource({"3,3", "0,0", "-1,3", "7,7"})
    void parsesTheConfiguredValue(int configured, int expected) throws Exception {
      YamlConfiguration cfg = new YamlConfiguration();
      cfg.loadFromString("max-quest-depth: " + configured);
      assertEquals(expected, Config.readMaxQuestDepth(cfg));
    }

    @Test
    void defaultsToThreeWhenAbsent() {
      assertEquals(3, Config.readMaxQuestDepth(new YamlConfiguration()));
    }
  }
}
