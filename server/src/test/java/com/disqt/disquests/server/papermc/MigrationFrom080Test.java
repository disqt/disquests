package com.disqt.disquests.server.papermc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The live server's 0.8.0 database must come through the roles and tree migration intact. */
class MigrationFrom080Test {

  @TempDir Path tempDir;

  private static final UUID OLIVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID EDITH = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID VIKTOR = UUID.fromString("00000000-0000-0000-0000-00000000000c");
  private static final UUID RHEA = UUID.fromString("00000000-0000-0000-0000-00000000000d");
  private static final UUID QUEST_A = UUID.fromString("10000000-0000-0000-0000-00000000000a");
  private static final UUID QUEST_B = UUID.fromString("10000000-0000-0000-0000-00000000000b");

  private String url() {
    return "jdbc:sqlite:" + tempDir.resolve("disquests.db");
  }

  private void loadFixture() throws Exception {
    String sql;
    try (var in = getClass().getResourceAsStream("/fixtures/disquests-0.8.0.sql")) {
      sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    StringBuilder statement = new StringBuilder();
    try (Connection c = DriverManager.getConnection(url());
        Statement st = c.createStatement()) {
      for (String line : sql.split("\n")) {
        if (line.startsWith("--") || line.isBlank()) continue;
        statement.append(line).append('\n');
        if (line.endsWith(";")) {
          st.executeUpdate(statement.toString());
          statement.setLength(0);
        }
      }
    }
  }

  private int count(String sql) throws Exception {
    try (Connection c = DriverManager.getConnection(url());
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      return rs.next() ? rs.getInt(1) : -1;
    }
  }

  private Set<String> columns(String table) throws Exception {
    Set<String> cols = new HashSet<>();
    try (Connection c = DriverManager.getConnection(url());
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (rs.next()) cols.add(rs.getString("name"));
    }
    return cols;
  }

  private DataManager migrate() {
    DataManager dm = new DataManager(tempDir);
    dm.initialize();
    return dm;
  }

  @Test
  void rolesPinsRequestsAndCompletionSurvive() throws Exception {
    loadFixture();
    DataManager dm = migrate();
    try {
      QuestData a = dm.getQuest(QUEST_A);
      assertEquals(OLIVE, a.ownerUuid());
      assertEquals("Olive", a.ownerName());
      assertEquals(
          List.of(
              new ContributorData(EDITH, "Edith", Role.WRITE),
              new ContributorData(VIKTOR, "Viktor", Role.READ)),
          a.contributors());
      assertEquals(Role.OWNER, a.roleOf(OLIVE));
      assertEquals(Visibility.OPEN, a.visibility());
      assertNull(a.parentId());
      assertEquals(List.of(), a.prerequisites());
      assertFalse(a.autoJoin());
      assertNull(a.privatized());
      assertEquals(3, a.polygon().size());
      assertEquals(List.of("building"), a.tags());

      QuestData b = dm.getQuest(QUEST_B);
      assertEquals(EDITH, b.ownerUuid());
      assertEquals(List.of(new ContributorData(OLIVE, "Olive", Role.WRITE)), b.contributors());
      assertEquals(8000L, b.completedAt());
      assertEquals(EDITH, b.completedBy());
      assertEquals("Edith", b.completedByName());

      assertEquals(List.of(QUEST_A), dm.getPinnedQuestIds(EDITH));
      assertEquals(1, dm.getPendingRequestCount(OLIVE));
      assertEquals(RHEA, dm.getPendingRequestsForOwner(OLIVE).get(0).requesterUuid());
      assertEquals(
          List.of(QUEST_A, QUEST_B),
          dm.getQuestsForPlayer(OLIVE).stream().map(QuestData::id).sorted().toList());
      assertEquals(
          List.of(QUEST_A), dm.getQuestsForPlayer(VIKTOR).stream().map(QuestData::id).toList());
    } finally {
      dm.close();
    }
  }

  @Test
  void ownerRowsHaveAddedAtZeroAndContributorsOne() throws Exception {
    loadFixture();
    migrate().close();
    assertEquals(
        2, count("SELECT COUNT(*) FROM quest_people WHERE role = 'OWNER' AND added_at = 0"));
    assertEquals(
        2, count("SELECT COUNT(*) FROM quest_people WHERE role = 'WRITE' AND added_at = 1"));
    assertEquals(
        1, count("SELECT COUNT(*) FROM quest_people WHERE role = 'READ' AND added_at = 1"));
  }

  @Test
  void legacyContributorsTableIsKeptUntouched() throws Exception {
    loadFixture();
    migrate().close();
    assertEquals(3, count("SELECT COUNT(*) FROM contributors"));
  }

  @Test
  void newColumnsAndTablesExist() throws Exception {
    loadFixture();
    migrate().close();
    assertTrue(
        columns("quests")
            .containsAll(
                List.of(
                    "parent_id",
                    "auto_join",
                    "priv_previous",
                    "priv_title",
                    "priv_by",
                    "priv_at",
                    "priv_saw")));
    assertEquals(Set.of("quest_id", "player_uuid", "role", "added_at"), columns("quest_people"));
    assertEquals(Set.of("from_id", "to_id", "kind"), columns("quest_links"));
  }

  @Test
  void secondInitializeChangesNothing() throws Exception {
    loadFixture();
    DataManager first = migrate();
    List<QuestData> before = first.getAllQuests();
    first.close();
    int peopleBefore = count("SELECT COUNT(*) FROM quest_people");

    DataManager second = migrate();
    try {
      assertEquals(before, second.getAllQuests());
    } finally {
      second.close();
    }
    assertEquals(peopleBefore, count("SELECT COUNT(*) FROM quest_people"));
    assertEquals(5, peopleBefore);
  }

  @Test
  void changesMadeAfterMigrationAreNotUndoneByARestart() throws Exception {
    loadFixture();
    DataManager first = migrate();
    first.setRole(QUEST_A, VIKTOR, Role.WRITE);
    first.removePerson(QUEST_A, EDITH);
    first.close();

    DataManager second = migrate();
    try {
      assertEquals(
          List.of(new ContributorData(VIKTOR, "Viktor", Role.WRITE)),
          second.getQuest(QUEST_A).contributors());
    } finally {
      second.close();
    }
  }

  @Test
  void failedMigrationRollsBackEverythingAndIsRetried() throws Exception {
    loadFixture();
    try (Connection c = DriverManager.getConnection(url());
        Statement st = c.createStatement()) {
      st.executeUpdate("DROP TABLE contributors");
      st.executeUpdate(
          "CREATE TABLE contributors (quest_id TEXT, player_uuid TEXT, can_edit INTEGER)");
      st.executeUpdate("INSERT INTO contributors VALUES ('" + QUEST_A + "', NULL, 1)");
    }

    DataManager failing = new DataManager(tempDir);
    assertThrows(RuntimeException.class, failing::initialize);
    failing.close();

    assertTrue(columns("quest_people").isEmpty(), "no half-built quest_people table");
    assertTrue(columns("quest_links").isEmpty(), "no half-built quest_links table");
    assertFalse(columns("quests").contains("parent_id"), "no half-added column");

    try (Connection c = DriverManager.getConnection(url());
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM contributors");
      st.executeUpdate("INSERT INTO contributors VALUES ('" + QUEST_A + "', '" + EDITH + "', 1)");
    }
    DataManager retry = migrate();
    try {
      assertEquals(
          List.of(new ContributorData(EDITH, "Edith", Role.WRITE)),
          retry.getQuest(QUEST_A).contributors());
    } finally {
      retry.close();
    }
  }

  @Test
  void freshDatabaseGetsTheNewSchema() {
    DataManager dm = migrate();
    try {
      assertEquals(List.of(), dm.getAllQuests());
    } finally {
      dm.close();
    }
  }
}
