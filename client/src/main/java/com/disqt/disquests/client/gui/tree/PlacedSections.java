package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.client.gui.tree.QuestTreeRows.Row;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Where the map layout's list splits into its "Unplaced" and "On the map" sections. The list puts
 * unplaced top-level quests first; a subquest row belongs to its top-level quest's section whatever
 * its own location, so only top-level rows decide.
 */
public final class PlacedSections {

  private PlacedSections() {}

  /**
   * The index of the first placed top-level row, when an unplaced top-level row comes before it.
   * Empty when the list holds only one kind (or nothing): then there is nothing to separate and
   * neither header is shown.
   *
   * @param rows the list's rows, in list order
   * @param placed whether a top-level quest has a location
   */
  public static OptionalInt firstPlacedRow(List<Row> rows, Predicate<UUID> placed) {
    boolean unplacedSeen = false;
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      if (row.depth() != 0) continue;
      if (!placed.test(row.id())) {
        unplacedSeen = true;
      } else if (unplacedSeen) {
        return OptionalInt.of(i);
      } else {
        return OptionalInt.empty();
      }
    }
    return OptionalInt.empty();
  }
}
