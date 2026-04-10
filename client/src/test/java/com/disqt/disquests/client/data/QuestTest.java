package com.disqt.disquests.client.data;

import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
