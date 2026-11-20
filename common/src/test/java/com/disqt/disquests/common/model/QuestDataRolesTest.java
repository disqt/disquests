package com.disqt.disquests.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuestDataRolesTest {

  private static final UUID OWNER = new UUID(0, 1);
  private static final UUID SECOND_OWNER = new UUID(0, 2);
  private static final UUID WRITER = new UUID(0, 3);
  private static final UUID READER = new UUID(0, 4);
  private static final UUID STRANGER = new UUID(0, 5);

  private static QuestData quest() {
    return new QuestData(
        new UUID(1, 1),
        "Quest",
        "",
        OWNER,
        "owner",
        Visibility.OPEN,
        List.of(
            new ContributorData(SECOND_OWNER, "second", Role.OWNER),
            new ContributorData(WRITER, "writer", Role.WRITE),
            new ContributorData(READER, "reader", Role.READ)),
        1_000L,
        null,
        false,
        null,
        null,
        List.of());
  }

  private static UUID player(String who) {
    return switch (who) {
      case "owner" -> OWNER;
      case "second" -> SECOND_OWNER;
      case "writer" -> WRITER;
      case "reader" -> READER;
      default -> STRANGER;
    };
  }

  @ParameterizedTest
  @CsvSource({"owner,OWNER", "second,OWNER", "writer,WRITE", "reader,READ"})
  void roleOfReturnsTheHeldRole(String who, Role expected) {
    assertEquals(expected, quest().roleOf(player(who)));
  }

  @Test
  void roleOfStrangerAndNullIsNull() {
    assertNull(quest().roleOf(STRANGER));
    assertNull(quest().roleOf(null));
  }

  @Test
  void ownersListsDisplayOwnerThenFurtherOwners() {
    assertEquals(List.of(OWNER, SECOND_OWNER), quest().owners());
  }

  @ParameterizedTest
  @CsvSource({"READ,READ,true", "READ,WRITE,false", "WRITE,READ,true", "WRITE,OWNER,false"})
  void atLeastComparesRoles(Role have, Role need, boolean expected) {
    assertEquals(expected, have.atLeast(need));
  }

  @Test
  void legacyContributorConstructorMapsCanEdit() {
    assertEquals(Role.WRITE, new ContributorData(WRITER, "w", true).role());
    assertEquals(Role.READ, new ContributorData(READER, "r", false).role());
  }

  @Test
  void privatizedDefaultsSawDeletedToEmpty() {
    assertEquals(Set.of(), new Privatized(Visibility.OPEN, "t", "n", 5L).sawDeleted());
  }
}
