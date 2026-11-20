package com.disqt.disquests.client.gui.tree;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.gui.tree.QuestTreeRows.Row;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlacedSectionsTest {

  private static Row top(String title) {
    return new Row(id(title), 0, 0, false, false, true);
  }

  private static Row parent(String title, int children) {
    return new Row(id(title), 0, children, true, false, true);
  }

  private static Row child(String title) {
    return new Row(id(title), 1, 0, false, false, true);
  }

  private static Set<UUID> placed(String... titles) {
    return Set.copyOf(Arrays.stream(titles).map(TreeFixtures::id).toList());
  }

  @Test
  void mixedListSplitsAtTheFirstPlacedQuest() {
    List<Row> rows = List.of(top("Farm"), top("Mine"), top("Hangar"), top("Church"));
    assertEquals(
        OptionalInt.of(2),
        PlacedSections.firstPlacedRow(rows, placed("Hangar", "Church")::contains));
  }

  @Test
  void allPlacedHasNoSections() {
    List<Row> rows = List.of(top("Hangar"), top("Church"));
    assertEquals(
        OptionalInt.empty(),
        PlacedSections.firstPlacedRow(rows, placed("Hangar", "Church")::contains));
  }

  @Test
  void allUnplacedHasNoSections() {
    List<Row> rows = List.of(top("Farm"), top("Mine"));
    assertEquals(OptionalInt.empty(), PlacedSections.firstPlacedRow(rows, placed()::contains));
  }

  @Test
  void placedSubquestOfAnUnplacedParentStaysInTheUnplacedSection() {
    List<Row> rows =
        List.of(parent("Farm", 2), child("Barn"), child("Silo"), top("Hangar"), top("Church"));
    assertEquals(
        OptionalInt.of(3),
        PlacedSections.firstPlacedRow(rows, placed("Barn", "Hangar", "Church")::contains));
  }

  @Test
  void unplacedParentWithOnlyPlacedChildrenHasNoSections() {
    List<Row> rows = List.of(parent("Farm", 1), child("Barn"));
    assertEquals(
        OptionalInt.empty(), PlacedSections.firstPlacedRow(rows, placed("Barn")::contains));
  }

  @Test
  void emptyListHasNoSections() {
    assertEquals(OptionalInt.empty(), PlacedSections.firstPlacedRow(List.of(), placed()::contains));
  }
}
