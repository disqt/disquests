package com.disqt.disquests.client.data;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.common.graph.QuestView;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QuestTest {

  private static final UUID OWNER = UUID.randomUUID();
  private static final UUID EDITOR = UUID.randomUUID();
  private static final UUID VIEWER = UUID.randomUUID();
  private static final UUID STRANGER = UUID.randomUUID();

  private Quest makeQuest(Visibility visibility) {
    return Quest.fromNetwork(
        new QuestData(
            UUID.randomUUID(),
            "Test Quest",
            "Some content",
            OWNER,
            "OwnerName",
            visibility,
            List.of(
                new ContributorData(EDITOR, "Editor", true),
                new ContributorData(VIEWER, "Viewer", false)),
            System.currentTimeMillis(),
            new CoordinatesData(100, 64, -200),
            false,
            null,
            "overworld",
            List.of("building", "redstone")));
  }

  @Test
  void fromNetwork_copiesAllFields() {
    Quest q = makeQuest(Visibility.OPEN);
    assertEquals("Test Quest", q.getTitle());
    assertEquals("Some content", q.getContent());
    assertEquals(OWNER, q.getOwnerUuid());
    assertEquals("OwnerName", q.getOwnerName());
    assertEquals(Visibility.OPEN, q.getVisibility());
    assertEquals(2, q.getContributors().size());
    assertEquals(100, q.getCoordinates().x());
    assertFalse(q.isRegion());
    assertNull(q.getCoordinates2());
    assertEquals("overworld", q.getMap());
    assertEquals(List.of("building", "redstone"), q.getTags());
  }

  @Test
  void isOwner_trueForOwner() {
    Quest q = makeQuest(Visibility.PRIVATE);
    assertTrue(q.isOwner(OWNER));
  }

  @Test
  void isOwner_falseForOthers() {
    Quest q = makeQuest(Visibility.PRIVATE);
    assertFalse(q.isOwner(EDITOR));
    assertFalse(q.isOwner(STRANGER));
    assertFalse(q.isOwner(null));
  }

  @Test
  void isContributor_matchesBothEditorAndViewer() {
    Quest q = makeQuest(Visibility.OPEN);
    assertTrue(q.isContributor(EDITOR));
    assertTrue(q.isContributor(VIEWER));
    assertFalse(q.isContributor(STRANGER));
  }

  @Test
  void canEdit_ownerAlwaysCan() {
    Quest q = makeQuest(Visibility.PRIVATE);
    assertTrue(q.canEdit(OWNER));
  }

  @Test
  void canEdit_editorCan_viewerCannot() {
    Quest q = makeQuest(Visibility.OPEN);
    assertTrue(q.canEdit(EDITOR));
    assertFalse(q.canEdit(VIEWER));
  }

  @Test
  void canEdit_strangerCannot() {
    Quest q = makeQuest(Visibility.OPEN);
    assertFalse(q.canEdit(STRANGER));
  }

  @Test
  void isContentHidden_closedQuest_hiddenFromStranger() {
    Quest q = makeQuest(Visibility.CLOSED);
    assertTrue(q.isContentHidden(STRANGER));
  }

  @Test
  void isContentHidden_closedQuest_visibleToOwner() {
    Quest q = makeQuest(Visibility.CLOSED);
    assertFalse(q.isContentHidden(OWNER));
  }

  @Test
  void isContentHidden_closedQuest_visibleToContributor() {
    Quest q = makeQuest(Visibility.CLOSED);
    assertFalse(q.isContentHidden(EDITOR));
    assertFalse(q.isContentHidden(VIEWER));
  }

  @Test
  void isContentHidden_openQuest_neverHidden() {
    Quest q = makeQuest(Visibility.OPEN);
    assertFalse(q.isContentHidden(STRANGER));
  }

  @Test
  void isContentHidden_privateQuest_neverHidden() {
    // PRIVATE visibility doesn't trigger isContentHidden (it controls listing, not content)
    Quest q = makeQuest(Visibility.PRIVATE);
    assertFalse(q.isContentHidden(STRANGER));
  }

  @Test
  void getTags_nullSafe() {
    Quest q =
        Quest.fromNetwork(
            new QuestData(
                UUID.randomUUID(),
                "t",
                "c",
                OWNER,
                "o",
                Visibility.OPEN,
                List.of(),
                0,
                null,
                false,
                null,
                null,
                List.of()));
    assertNotNull(q.getTags());
    assertTrue(q.getTags().isEmpty());
  }

  private Quest questWith(CoordinatesData coords, List<PointData> polygon) {
    return Quest.fromNetwork(
        new QuestData(
            UUID.randomUUID(),
            "T",
            "C",
            OWNER,
            "OwnerName",
            Visibility.OPEN,
            List.of(),
            0L,
            coords,
            false,
            null,
            "overworld",
            List.of(),
            polygon));
  }

  @Test
  void fromNetwork_copiesPolygon() {
    List<PointData> poly =
        new java.util.ArrayList<>(List.of(new PointData(1, 2), new PointData(3, 4)));
    Quest q = questWith(null, poly);
    assertEquals(poly, q.getPolygon());
    poly.clear();
    assertEquals(2, q.getPolygon().size());
    assertTrue(makeQuest(Visibility.OPEN).getPolygon().isEmpty());
    q.setPolygon(null);
    assertNotNull(q.getPolygon());
  }

  @Test
  void hasLocation_trueForPinOrPolygon_falseOtherwise() {
    assertFalse(questWith(null, List.of()).hasLocation());
    assertTrue(questWith(new CoordinatesData(1, 2, 3), List.of()).hasLocation());
    assertTrue(questWith(null, List.of(new PointData(1, 2))).hasLocation());
  }

  @Test
  void isPolygon_requiresTwoVertices() {
    assertFalse(questWith(null, List.of()).isPolygon());
    assertFalse(questWith(null, List.of(new PointData(1, 2))).isPolygon());
    assertTrue(questWith(null, List.of(new PointData(1, 2), new PointData(3, 4))).isPolygon());
  }

  @Test
  void fromNetwork_copiesCompletion() {
    UUID completer = UUID.randomUUID();
    QuestData base =
        new QuestData(
            UUID.randomUUID(),
            "T",
            "C",
            OWNER,
            "OwnerName",
            Visibility.OPEN,
            List.of(),
            0L,
            null,
            false,
            null,
            "overworld",
            List.of(),
            List.of());
    Quest completed = Quest.fromNetwork(base.withCompletion(1234L, completer, "Alice"));
    assertTrue(completed.isCompleted());
    assertEquals(1234L, completed.getCompletedAt());
    assertEquals(completer, completed.getCompletedBy());
    assertEquals("Alice", completed.getCompletedByName());

    Quest active = Quest.fromNetwork(base);
    assertFalse(active.isCompleted());
    assertEquals(0L, active.getCompletedAt());
    assertNull(active.getCompletedBy());
    assertNull(active.getCompletedByName());
  }

  @Nested
  class Trees {

    private final UUID co = UUID.randomUUID();
    private final UUID writer = UUID.randomUUID();
    private final UUID reader = UUID.randomUUID();
    private final UUID parent = UUID.randomUUID();
    private final UUID pre = UUID.randomUUID();

    private QuestData full() {
      return new QuestData(
          UUID.randomUUID(),
          "Title",
          "Body",
          OWNER,
          "OwnerName",
          Visibility.CLOSED,
          List.of(
              new ContributorData(co, "Co", Role.OWNER),
              new ContributorData(writer, "Writer", Role.WRITE),
              new ContributorData(reader, "Reader", Role.READ)),
          77L,
          new CoordinatesData(1, 2, 3),
          false,
          null,
          "overworld",
          List.of("a"),
          List.of(new PointData(1, 2)),
          5L,
          co,
          "Co",
          parent,
          List.of(pre),
          true,
          true,
          true,
          true,
          true,
          new Privatized(Visibility.OPEN, "Gone", "Bob", 9L));
    }

    @Test
    void fromNetworkCopiesTheLinkFields() {
      Quest q = Quest.fromNetwork(full());
      assertEquals(parent, q.getParentId());
      assertEquals(List.of(pre), q.getPrerequisites());
      assertTrue(q.hasHiddenPrerequisites());
      assertTrue(q.hasDeletedPrerequisite());
      assertTrue(q.isAutoJoin());
      assertTrue(q.isLocked());
      assertEquals(new Privatized(Visibility.OPEN, "Gone", "Bob", 9L), q.getPrivatized());
    }

    @Test
    void toDataRoundTripsWhatTheServerSent() {
      QuestData data = full();
      assertEquals(data, Quest.fromNetwork(data).toData());
    }

    @Test
    void toDataIsNotAffectedByLaterEditsToTheQuestLists() {
      Quest q = Quest.fromNetwork(full());
      QuestData before = q.toData();
      q.getPrerequisites().add(UUID.randomUUID());
      q.getContributors().clear();
      q.setPolygon(new java.util.ArrayList<>(List.of(new PointData(9, 9))));
      q.setTags(new java.util.ArrayList<>(List.of("zzz")));
      assertEquals(full().prerequisites(), before.prerequisites());
      assertEquals(3, before.contributors().size());
      assertEquals(List.of(new PointData(1, 2)), before.polygon());
      assertEquals(List.of("a"), before.tags());
    }

    @Test
    void toDataRoundTripsALockedView() {
      QuestData locked =
          new QuestData(
                  UUID.randomUUID(),
                  "Title",
                  "",
                  QuestView.NO_OWNER,
                  "",
                  Visibility.OPEN,
                  List.of(new ContributorData(reader, "Reader", Role.READ)),
                  0L,
                  null,
                  false,
                  null,
                  null,
                  List.of())
              .withLinksView(List.of(pre), false, false, true);
      assertEquals(locked, Quest.fromNetwork(locked).toData());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectations")
    void roleAccessors(
        String who, Function<Trees, UUID> player, Role role, boolean owner, boolean edit) {
      Quest q = Quest.fromNetwork(full());
      UUID id = player.apply(this);
      assertEquals(role, q.roleOf(id));
      assertEquals(owner, q.isOwner(id));
      assertEquals(edit, q.canEdit(id));
    }

    static Stream<Arguments> expectations() {
      return Stream.of(
          Arguments.of("display owner", (Function<Trees, UUID>) t -> OWNER, Role.OWNER, true, true),
          Arguments.of("co-owner", (Function<Trees, UUID>) t -> t.co, Role.OWNER, true, true),
          Arguments.of("writer", (Function<Trees, UUID>) t -> t.writer, Role.WRITE, false, true),
          Arguments.of("reader", (Function<Trees, UUID>) t -> t.reader, Role.READ, false, false),
          Arguments.of("stranger", (Function<Trees, UUID>) t -> STRANGER, null, false, false));
    }

    @Test
    void roleOfNullPlayerIsNull() {
      assertNull(Quest.fromNetwork(full()).roleOf(null));
    }

    @Test
    void contributorKeepsItsRole() {
      Quest q = Quest.fromNetwork(full());
      assertEquals(Role.OWNER, q.getContributors().get(0).getRole());
      assertTrue(q.getContributors().get(0).canEdit());
      assertFalse(q.getContributors().get(2).canEdit());
    }
  }

  @Nested
  class PointOrPolygonBeforeSave {
    @Test
    void twoCornersBecomeAPinOnTheFirstAtTheStoredHeight() {
      Quest q = makeQuest(Visibility.PRIVATE);
      q.setPolygon(
          new java.util.ArrayList<>(List.of(new PointData(10, 20), new PointData(30, 40))));
      q.setCoordinates(new CoordinatesData(10, 70, 20));
      q.setCoordinates2(new CoordinatesData(30, 70, 40));
      q.setRegion(true);
      q.applyPointOrPolygon();
      assertEquals(new CoordinatesData(10, 70, 20), q.getCoordinates());
      assertNull(q.getCoordinates2());
      assertFalse(q.isRegion());
      assertTrue(q.getPolygon().isEmpty());
    }

    @Test
    void threeCornersAndPinsAreLeftAlone() {
      Quest q = makeQuest(Visibility.PRIVATE);
      List<PointData> three =
          List.of(new PointData(0, 0), new PointData(5, 0), new PointData(5, 5));
      q.setPolygon(new java.util.ArrayList<>(three));
      q.setCoordinates(new CoordinatesData(0, 64, 0));
      q.setCoordinates2(new CoordinatesData(5, 64, 5));
      q.setRegion(true);
      q.applyPointOrPolygon();
      assertEquals(three, q.getPolygon());
      assertTrue(q.isRegion());

      Quest pin = makeQuest(Visibility.PRIVATE);
      pin.setPolygon(List.of());
      pin.setCoordinates(new CoordinatesData(1, 2, 3));
      pin.applyPointOrPolygon();
      assertEquals(new CoordinatesData(1, 2, 3), pin.getCoordinates());
    }
  }
}
