package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.graph.Plans.DeletePlan;
import com.disqt.disquests.common.graph.Plans.PrivatizeInfo;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.CollaborationRequestData;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class DataManager {

  private static final String POLYGONS_DDL =
      """
          CREATE TABLE IF NOT EXISTS quest_polygons (
              quest_id TEXT NOT NULL,
              idx INTEGER NOT NULL,
              x REAL NOT NULL,
              z REAL NOT NULL,
              PRIMARY KEY (quest_id, idx),
              FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
          )""";

  private static final String PEOPLE_DDL =
      """
          CREATE TABLE IF NOT EXISTS quest_people (
              quest_id TEXT NOT NULL,
              player_uuid TEXT NOT NULL,
              role TEXT NOT NULL,
              added_at INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY (quest_id, player_uuid),
              FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
          )""";

  /** to_id has no foreign key on purpose: a deleted prerequisite must stay as a dangling id. */
  private static final String LINKS_DDL =
      """
          CREATE TABLE IF NOT EXISTS quest_links (
              from_id TEXT NOT NULL,
              to_id TEXT NOT NULL,
              kind TEXT NOT NULL,
              PRIMARY KEY (from_id, to_id, kind),
              FOREIGN KEY (from_id) REFERENCES quests(id) ON DELETE CASCADE
          )""";

  private static final String PREREQUISITE = "PREREQUISITE";

  private static final String QUEST_SELECT =
      """
          SELECT q.*, pn.name as owner_name, cn.name as completed_by_name, dn.name as priv_by_name
          FROM quests q
          LEFT JOIN player_names pn ON q.owner_uuid = pn.uuid
          LEFT JOIN player_names cn ON q.completed_by = cn.uuid
          LEFT JOIN player_names dn ON q.priv_by = dn.uuid
          """;

  private Connection connection;
  private final Path dataDir;

  public DataManager(Path dataDir) {
    this.dataDir = dataDir;
  }

  public synchronized void initialize() {
    initialize("overworld");
  }

  public synchronized void initialize(String defaultMap) {
    try {
      Files.createDirectories(dataDir);
      String url = "jdbc:sqlite:" + dataDir.resolve("disquests.db");
      connection = DriverManager.getConnection(url);
      try (Statement stmt = connection.createStatement()) {
        stmt.executeUpdate("PRAGMA foreign_keys = ON");
        stmt.executeUpdate("PRAGMA journal_mode = WAL");
      }
      // The quest_polygons table is the "migrated" marker, so it is only ever created inside the
      // migration transaction: a failed migration leaves no table and is retried on next start.
      boolean polygonsTableExisted = tableExists("quest_polygons");
      createTables();
      migratePinnedQuests();
      migrateCompletion();
      if (!polygonsTableExisted) {
        migrateLocations(defaultMap);
      }
      migrateRolesAndTree();
    } catch (SQLException | IOException e) {
      throw new RuntimeException("Failed to initialize database", e);
    }
  }

  private void createTables() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      stmt.executeUpdate(
          """
                    CREATE TABLE IF NOT EXISTS quests (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL DEFAULT '',
                        owner_uuid TEXT NOT NULL,
                        visibility TEXT NOT NULL DEFAULT 'PRIVATE',
                        coord_x REAL,
                        coord_y REAL,
                        coord_z REAL,
                        is_region INTEGER NOT NULL DEFAULT 0,
                        coord2_x REAL,
                        coord2_y REAL,
                        coord2_z REAL,
                        map TEXT,
                        last_modified INTEGER NOT NULL,
                        completed_at INTEGER NOT NULL DEFAULT 0,
                        completed_by TEXT
                    )""");

      stmt.executeUpdate(
          """
                    CREATE TABLE IF NOT EXISTS contributors (
                        quest_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        can_edit INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (quest_id, player_uuid),
                        FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
                    )""");

      stmt.executeUpdate(
          """
                    CREATE TABLE IF NOT EXISTS collaboration_requests (
                        id TEXT PRIMARY KEY,
                        quest_id TEXT NOT NULL,
                        requester_uuid TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        UNIQUE(quest_id, requester_uuid),
                        FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
                    )""");

      stmt.executeUpdate(
          """
                    CREATE TABLE IF NOT EXISTS pinned_quests (
                        player_uuid TEXT NOT NULL,
                        quest_id TEXT NOT NULL,
                        pinned_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (player_uuid, quest_id),
                        FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
                    )""");

      stmt.executeUpdate(
          """
                    CREATE TABLE IF NOT EXISTS player_names (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        last_seen INTEGER NOT NULL
                    )""");

      stmt.executeUpdate(
          """
                    CREATE TABLE IF NOT EXISTS quest_tags (
                        quest_id TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        PRIMARY KEY (quest_id, tag),
                        FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
                    )""");
    }
  }

  // -------------------------------------------------------------------------
  // Quests
  // -------------------------------------------------------------------------

  public synchronized void saveQuest(QuestData rawQuest) {
    QuestData quest = QuestLocation.normalize(rawQuest);
    boolean previousAutoCommit = true;
    try {
      previousAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to save quest", e);
    }
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                INSERT INTO quests (id, title, content, owner_uuid, visibility,
                    coord_x, coord_y, coord_z, is_region,
                    coord2_x, coord2_y, coord2_z, map, last_modified)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    content = excluded.content,
                    owner_uuid = excluded.owner_uuid,
                    visibility = excluded.visibility,
                    coord_x = excluded.coord_x,
                    coord_y = excluded.coord_y,
                    coord_z = excluded.coord_z,
                    is_region = excluded.is_region,
                    coord2_x = excluded.coord2_x,
                    coord2_y = excluded.coord2_y,
                    coord2_z = excluded.coord2_z,
                    map = excluded.map,
                    last_modified = excluded.last_modified
                """)) {
      stmt.setString(1, quest.id().toString());
      stmt.setString(2, quest.title());
      stmt.setString(3, quest.content());
      stmt.setString(4, quest.ownerUuid().toString());
      stmt.setString(5, quest.visibility().name());
      if (quest.coordinates() != null) {
        stmt.setDouble(6, quest.coordinates().x());
        stmt.setDouble(7, quest.coordinates().y());
        stmt.setDouble(8, quest.coordinates().z());
      } else {
        stmt.setNull(6, Types.REAL);
        stmt.setNull(7, Types.REAL);
        stmt.setNull(8, Types.REAL);
      }
      stmt.setInt(9, quest.isRegion() ? 1 : 0);
      if (quest.coordinates2() != null) {
        stmt.setDouble(10, quest.coordinates2().x());
        stmt.setDouble(11, quest.coordinates2().y());
        stmt.setDouble(12, quest.coordinates2().z());
      } else {
        stmt.setNull(10, Types.REAL);
        stmt.setNull(11, Types.REAL);
        stmt.setNull(12, Types.REAL);
      }
      stmt.setString(13, quest.map());
      stmt.setLong(14, quest.lastModified());
      stmt.executeUpdate();

      try (PreparedStatement delTags =
          connection.prepareStatement("DELETE FROM quest_tags WHERE quest_id = ?")) {
        delTags.setString(1, quest.id().toString());
        delTags.executeUpdate();
      }
      if (!quest.tags().isEmpty()) {
        try (PreparedStatement insTags =
            connection.prepareStatement(
                "INSERT OR IGNORE INTO quest_tags (quest_id, tag) VALUES (?, ?)")) {
          for (String tag : quest.tags()) {
            insTags.setString(1, quest.id().toString());
            insTags.setString(2, tag);
            insTags.addBatch();
          }
          insTags.executeBatch();
        }
      }

      try (PreparedStatement delPoly =
          connection.prepareStatement("DELETE FROM quest_polygons WHERE quest_id = ?")) {
        delPoly.setString(1, quest.id().toString());
        delPoly.executeUpdate();
      }
      if (!quest.polygon().isEmpty()) {
        try (PreparedStatement insPoly =
            connection.prepareStatement(
                "INSERT INTO quest_polygons (quest_id, idx, x, z) VALUES (?, ?, ?, ?)")) {
          int idx = 0;
          for (PointData p : quest.polygon()) {
            insPoly.setString(1, quest.id().toString());
            insPoly.setInt(2, idx++);
            insPoly.setDouble(3, p.x());
            insPoly.setDouble(4, p.z());
            insPoly.addBatch();
          }
          insPoly.executeBatch();
        }
      }
      // The display owner always holds an OWNER row; a brand-new quest's owner comes first.
      update(
          """
              INSERT INTO quest_people (quest_id, player_uuid, role, added_at) VALUES (?, ?, 'OWNER', 0)
              ON CONFLICT(quest_id, player_uuid) DO UPDATE SET role = 'OWNER'
              """,
          quest.id().toString(),
          quest.ownerUuid().toString());
      connection.commit();
    } catch (SQLException e) {
      try {
        connection.rollback();
      } catch (SQLException rollbackError) {
        e.addSuppressed(rollbackError);
      }
      throw new RuntimeException("Failed to save quest", e);
    } finally {
      try {
        connection.setAutoCommit(previousAutoCommit);
      } catch (SQLException ignored) {
        // connection is unusable anyway; the original error (if any) is already propagating
      }
    }
  }

  public synchronized boolean deleteQuest(UUID id) {
    try {
      return deleteQuestRaw(id);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete quest", e);
    }
  }

  /**
   * Deletes the quest; its people, links from it, pins, requests, tags and polygon cascade. Links
   * pointing at it stay, and quests that had it as their parent become top level.
   */
  private boolean deleteQuestRaw(UUID id) throws SQLException {
    update("UPDATE quests SET parent_id = NULL WHERE parent_id = ?", id.toString());
    return update("DELETE FROM quests WHERE id = ?", id.toString()) > 0;
  }

  public synchronized QuestData getQuest(UUID id) {
    try (PreparedStatement stmt = connection.prepareStatement(QUEST_SELECT + "WHERE q.id = ?")) {
      stmt.setString(1, id.toString());
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        return hydrate(List.of(mapQuestRow(rs))).get(0);
      }
      return null;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get quest", e);
    }
  }

  /** Every quest as stored: all people with roles, parent, prerequisites, nothing redacted. */
  public synchronized List<QuestData> getAllQuests() {
    List<QuestData> quests = new ArrayList<>();
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(QUEST_SELECT)) {
      while (rs.next()) {
        quests.add(mapQuestRow(rs));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get all quests", e);
    }
    return hydrate(quests);
  }

  public synchronized QuestGraph loadGraph() {
    return QuestGraph.of(getAllQuests());
  }

  public synchronized List<QuestData> getQuestsForPlayer(UUID playerUuid) {
    List<QuestData> quests = new ArrayList<>();
    try (PreparedStatement stmt =
        connection.prepareStatement(
            QUEST_SELECT
                + """
                WHERE q.owner_uuid = ?
                   OR q.id IN (SELECT quest_id FROM quest_people WHERE player_uuid = ?)
                """)) {
      stmt.setString(1, playerUuid.toString());
      stmt.setString(2, playerUuid.toString());
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        quests.add(mapQuestRow(rs));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get quests for player", e);
    }
    return hydrate(quests);
  }

  public synchronized List<QuestData> getServerQuests(UUID excludePlayerUuid) {
    List<QuestData> quests = new ArrayList<>();
    try (PreparedStatement stmt =
        connection.prepareStatement(
            QUEST_SELECT
                + """
                WHERE q.visibility IN ('OPEN', 'CLOSED')
                  AND q.completed_at = 0
                  AND q.owner_uuid != ?
                  AND q.id NOT IN (SELECT quest_id FROM quest_people WHERE player_uuid = ?)
                """)) {
      stmt.setString(1, excludePlayerUuid.toString());
      stmt.setString(2, excludePlayerUuid.toString());
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        quests.add(mapQuestRow(rs));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get server quests", e);
    }
    return hydrate(quests);
  }

  public synchronized Map<String, QuestData> findQuestsByTitlesIgnoreCase(List<String> titles) {
    if (titles.isEmpty()) return Map.of();
    Map<String, QuestData> result = new HashMap<>();
    String placeholders = titles.stream().map(t -> "?").collect(Collectors.joining(","));
    try (PreparedStatement stmt =
        connection.prepareStatement(
            QUEST_SELECT + "WHERE LOWER(q.title) IN (" + placeholders + ")")) {
      for (int i = 0; i < titles.size(); i++) {
        stmt.setString(i + 1, titles.get(i).toLowerCase());
      }
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        QuestData quest = mapQuestRow(rs);
        String lowerTitle = quest.title().toLowerCase();
        if (!result.containsKey(lowerTitle)
            || quest.id().toString().compareTo(result.get(lowerTitle).id().toString()) < 0) {
          result.put(lowerTitle, quest);
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find quests by titles", e);
    }
    return result;
  }

  public synchronized void updateVisibility(UUID questId, Visibility visibility) {
    try (PreparedStatement stmt =
        connection.prepareStatement("UPDATE quests SET visibility = ? WHERE id = ?")) {
      stmt.setString(1, visibility.name());
      stmt.setString(2, questId.toString());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update visibility", e);
    }
  }

  // -------------------------------------------------------------------------
  // Contributors
  // -------------------------------------------------------------------------

  public synchronized void addContributor(UUID questId, UUID playerUuid, boolean canEdit) {
    try {
      // Never demotes an owner: a legacy caller only knows WRITE and READ.
      update(
          """
              INSERT INTO quest_people (quest_id, player_uuid, role, added_at)
              VALUES (?, ?, ?, ?)
              ON CONFLICT(quest_id, player_uuid) DO UPDATE SET role = excluded.role
              WHERE quest_people.role <> 'OWNER'
              """,
          questId.toString(),
          playerUuid.toString(),
          (canEdit ? Role.WRITE : Role.READ).name(),
          nextAddedAt(questId));
    } catch (SQLException e) {
      throw new RuntimeException("Failed to add contributor", e);
    }
  }

  public synchronized void leaveQuest(UUID questId, UUID playerUuid) {
    removeContributor(questId, playerUuid);
    unpinQuest(playerUuid, questId);
  }

  public synchronized void removeContributor(UUID questId, UUID playerUuid) {
    try {
      // The display owner is never removed this way.
      update(
          """
              DELETE FROM quest_people WHERE quest_id = ? AND player_uuid = ?
                AND player_uuid NOT IN (SELECT owner_uuid FROM quests WHERE id = ?)
              """,
          questId.toString(),
          playerUuid.toString(),
          questId.toString());
    } catch (SQLException e) {
      throw new RuntimeException("Failed to remove contributor", e);
    }
  }

  public synchronized void updateContributor(UUID questId, UUID playerUuid, boolean canEdit) {
    try {
      update(
          "UPDATE quest_people SET role = ? WHERE quest_id = ? AND player_uuid = ? AND role <> 'OWNER'",
          (canEdit ? Role.WRITE : Role.READ).name(),
          questId.toString(),
          playerUuid.toString());
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update contributor", e);
    }
  }

  /** Everyone with a role on the quest except its display owner, oldest first. */
  public synchronized List<ContributorData> getContributors(UUID questId) {
    try (PreparedStatement stmt =
        connection.prepareStatement("SELECT owner_uuid FROM quests WHERE id = ?")) {
      stmt.setString(1, questId.toString());
      ResultSet rs = stmt.executeQuery();
      if (!rs.next()) return new ArrayList<>();
      UUID displayOwner = UUID.fromString(rs.getString("owner_uuid"));
      return getPeopleBatch(Map.of(questId, displayOwner)).get(questId);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get contributors", e);
    }
  }

  public synchronized boolean isContributor(UUID questId, UUID playerUuid) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                SELECT 1 FROM quest_people qp JOIN quests q ON q.id = qp.quest_id
                WHERE qp.quest_id = ? AND qp.player_uuid = ? AND qp.player_uuid <> q.owner_uuid
                """)) {
      stmt.setString(1, questId.toString());
      stmt.setString(2, playerUuid.toString());
      ResultSet rs = stmt.executeQuery();
      return rs.next();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to check contributor", e);
    }
  }

  // -------------------------------------------------------------------------
  // Collaboration Requests
  // -------------------------------------------------------------------------

  public synchronized UUID createCollaborationRequest(UUID questId, UUID requesterUuid) {
    UUID requestId = UUID.randomUUID();
    long now = System.currentTimeMillis() / 1000L;
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                INSERT INTO collaboration_requests (id, quest_id, requester_uuid, timestamp)
                VALUES (?, ?, ?, ?)
                """)) {
      stmt.setString(1, requestId.toString());
      stmt.setString(2, questId.toString());
      stmt.setString(3, requesterUuid.toString());
      stmt.setLong(4, now);
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to create collaboration request", e);
    }
    return requestId;
  }

  public synchronized void deleteCollaborationRequest(UUID requestId) {
    try (PreparedStatement stmt =
        connection.prepareStatement("DELETE FROM collaboration_requests WHERE id = ?")) {
      stmt.setString(1, requestId.toString());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete collaboration request", e);
    }
  }

  public synchronized List<CollaborationRequestData> getPendingRequestsForOwner(UUID ownerUuid) {
    List<CollaborationRequestData> requests = new ArrayList<>();
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                SELECT cr.id, cr.quest_id, cr.requester_uuid, cr.timestamp,
                       q.title as quest_title, pn.name as requester_name
                FROM collaboration_requests cr
                JOIN quests q ON cr.quest_id = q.id
                LEFT JOIN player_names pn ON cr.requester_uuid = pn.uuid
                WHERE EXISTS (SELECT 1 FROM quest_people qp
                              WHERE qp.quest_id = q.id AND qp.player_uuid = ? AND qp.role = 'OWNER')
                """)) {
      stmt.setString(1, ownerUuid.toString());
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        requests.add(
            new CollaborationRequestData(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("quest_id")),
                rs.getString("quest_title"),
                UUID.fromString(rs.getString("requester_uuid")),
                rs.getString("requester_name"),
                rs.getLong("timestamp")));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get pending requests", e);
    }
    return requests;
  }

  public synchronized Map<UUID, Integer> getPendingCountByQuest(UUID ownerUuid) {
    Map<UUID, Integer> counts = new HashMap<>();
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                SELECT cr.quest_id, COUNT(*) as cnt
                FROM collaboration_requests cr
                JOIN quests q ON cr.quest_id = q.id
                WHERE EXISTS (SELECT 1 FROM quest_people qp
                              WHERE qp.quest_id = q.id AND qp.player_uuid = ? AND qp.role = 'OWNER')
                GROUP BY cr.quest_id
                """)) {
      stmt.setString(1, ownerUuid.toString());
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        counts.put(UUID.fromString(rs.getString("quest_id")), rs.getInt("cnt"));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to count pending requests by quest", e);
    }
    return counts;
  }

  public synchronized int getPendingRequestCount(UUID ownerUuid) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                SELECT COUNT(*) FROM collaboration_requests cr
                JOIN quests q ON cr.quest_id = q.id
                WHERE EXISTS (SELECT 1 FROM quest_people qp
                              WHERE qp.quest_id = q.id AND qp.player_uuid = ? AND qp.role = 'OWNER')
                """)) {
      stmt.setString(1, ownerUuid.toString());
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) return rs.getInt(1);
      return 0;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to count pending requests", e);
    }
  }

  public synchronized CollaborationRequestData getCollaborationRequest(UUID requestId) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                SELECT cr.id, cr.quest_id, cr.requester_uuid, cr.timestamp,
                       q.title as quest_title, pn.name as requester_name
                FROM collaboration_requests cr
                JOIN quests q ON cr.quest_id = q.id
                LEFT JOIN player_names pn ON cr.requester_uuid = pn.uuid
                WHERE cr.id = ?
                """)) {
      stmt.setString(1, requestId.toString());
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        return new CollaborationRequestData(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("quest_id")),
            rs.getString("quest_title"),
            UUID.fromString(rs.getString("requester_uuid")),
            rs.getString("requester_name"),
            rs.getLong("timestamp"));
      }
      return null;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get collaboration request", e);
    }
  }

  // -------------------------------------------------------------------------
  // Pins
  // -------------------------------------------------------------------------

  public synchronized void pinQuest(UUID playerUuid, UUID questId) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                INSERT OR IGNORE INTO pinned_quests (player_uuid, quest_id, pinned_at) VALUES (?, ?, ?)
                """)) {
      stmt.setString(1, playerUuid.toString());
      stmt.setString(2, questId.toString());
      stmt.setLong(3, System.currentTimeMillis());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to pin quest", e);
    }
  }

  public synchronized void unpinQuest(UUID playerUuid, UUID questId) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "DELETE FROM pinned_quests WHERE player_uuid = ? AND quest_id = ?")) {
      stmt.setString(1, playerUuid.toString());
      stmt.setString(2, questId.toString());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to unpin quest", e);
    }
  }

  public synchronized boolean isQuestPinned(UUID playerUuid, UUID questId) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "SELECT 1 FROM pinned_quests WHERE player_uuid = ? AND quest_id = ?")) {
      stmt.setString(1, playerUuid.toString());
      stmt.setString(2, questId.toString());
      ResultSet rs = stmt.executeQuery();
      return rs.next();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to check pinned quest", e);
    }
  }

  public synchronized List<UUID> getPinnedQuestIds(UUID playerUuid) {
    List<UUID> ids = new ArrayList<>();
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "SELECT quest_id FROM pinned_quests WHERE player_uuid = ? ORDER BY pinned_at ASC")) {
      stmt.setString(1, playerUuid.toString());
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        ids.add(UUID.fromString(rs.getString("quest_id")));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get pinned quests", e);
    }
    return ids;
  }

  // -------------------------------------------------------------------------
  // Player Names
  // -------------------------------------------------------------------------

  public synchronized void upsertPlayerName(UUID uuid, String name) {
    long now = System.currentTimeMillis() / 1000L;
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                INSERT INTO player_names (uuid, name, last_seen) VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen
                """)) {
      stmt.setString(1, uuid.toString());
      stmt.setString(2, name);
      stmt.setLong(3, now);
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to upsert player name", e);
    }
  }

  public synchronized String getPlayerName(UUID uuid) {
    try (PreparedStatement stmt =
        connection.prepareStatement("SELECT name FROM player_names WHERE uuid = ?")) {
      stmt.setString(1, uuid.toString());
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) return rs.getString("name");
      return null;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get player name", e);
    }
  }

  public synchronized UUID getPlayerUuidByName(String name) {
    try (PreparedStatement stmt =
        connection.prepareStatement("SELECT uuid FROM player_names WHERE name = ?")) {
      stmt.setString(1, name);
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) return UUID.fromString(rs.getString("uuid"));
      return null;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get player uuid by name", e);
    }
  }

  public synchronized void close() {
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException e) {
        // ignore on shutdown
      }
    }
  }

  public synchronized void resetDatabase() {
    try (var stmt = connection.createStatement()) {
      stmt.executeUpdate("DELETE FROM pinned_quests");
      stmt.executeUpdate("DELETE FROM collaboration_requests");
      stmt.executeUpdate("DELETE FROM contributors");
      stmt.executeUpdate("DELETE FROM quest_people");
      stmt.executeUpdate("DELETE FROM quest_links");
      stmt.executeUpdate("DELETE FROM quest_tags");
      stmt.executeUpdate("DELETE FROM quest_polygons");
      stmt.executeUpdate("DELETE FROM quests");
      stmt.executeUpdate("DELETE FROM player_names");
    } catch (SQLException e) {
      throw new RuntimeException("Failed to reset database", e);
    }
  }

  // -------------------------------------------------------------------------
  // Migrations
  // -------------------------------------------------------------------------

  private void migratePinnedQuests() throws SQLException {
    // Check if the pinned_quests table has the old schema (player_uuid as PRIMARY KEY only)
    // by looking for the pinned_at column
    boolean hasPinnedAt = false;
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("PRAGMA table_info(pinned_quests)")) {
      while (rs.next()) {
        if ("pinned_at".equals(rs.getString("name"))) {
          hasPinnedAt = true;
          break;
        }
      }
    }
    if (!hasPinnedAt) {
      try (Statement stmt = connection.createStatement()) {
        stmt.executeUpdate("ALTER TABLE pinned_quests RENAME TO pinned_quests_old");
        stmt.executeUpdate(
            """
                        CREATE TABLE pinned_quests (
                            player_uuid TEXT NOT NULL,
                            quest_id TEXT NOT NULL,
                            pinned_at INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY (player_uuid, quest_id),
                            FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
                        )""");
        stmt.executeUpdate(
            """
                        INSERT INTO pinned_quests (player_uuid, quest_id, pinned_at)
                        SELECT player_uuid, quest_id, 0 FROM pinned_quests_old
                        """);
        stmt.executeUpdate("DROP TABLE pinned_quests_old");
      }
    }
  }

  /** Adds the completion columns to databases created before quest completion existed. */
  private void migrateCompletion() throws SQLException {
    boolean hasCompletedAt = false;
    boolean hasCompletedBy = false;
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("PRAGMA table_info(quests)")) {
      while (rs.next()) {
        String name = rs.getString("name");
        if ("completed_at".equals(name)) hasCompletedAt = true;
        if ("completed_by".equals(name)) hasCompletedBy = true;
      }
    }
    if (hasCompletedAt && hasCompletedBy) return;
    connection.setAutoCommit(false);
    try (Statement stmt = connection.createStatement()) {
      if (!hasCompletedAt) {
        stmt.executeUpdate("ALTER TABLE quests ADD COLUMN completed_at INTEGER NOT NULL DEFAULT 0");
      }
      if (!hasCompletedBy) {
        stmt.executeUpdate("ALTER TABLE quests ADD COLUMN completed_by TEXT");
      }
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  /**
   * Marks a quest completed and drops its pins and pending collaboration requests in one
   * transaction.
   */
  public synchronized void completeQuest(UUID questId, UUID completedBy, long completedAt) {
    inTransaction("complete quest", () -> completeQuestRaw(questId, completedBy, completedAt));
  }

  /** Completes each quest like {@link #completeQuest}, all in one transaction. */
  public synchronized void completeQuests(Collection<UUID> questIds, UUID completedBy, long at) {
    inTransaction(
        "complete quests",
        () -> {
          for (UUID id : questIds) completeQuestRaw(id, completedBy, at);
        });
  }

  private void completeQuestRaw(UUID questId, UUID completedBy, long completedAt)
      throws SQLException {
    update(
        "UPDATE quests SET completed_at = ?, completed_by = ? WHERE id = ?",
        completedAt,
        completedBy.toString(),
        questId.toString());
    update("DELETE FROM pinned_quests WHERE quest_id = ?", questId.toString());
    update("DELETE FROM collaboration_requests WHERE quest_id = ?", questId.toString());
  }

  public synchronized void reopenQuest(UUID questId) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "UPDATE quests SET completed_at = 0, completed_by = NULL WHERE id = ?")) {
      stmt.setString(1, questId.toString());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to reopen quest", e);
    }
  }

  // -------------------------------------------------------------------------
  // Tree, roles and privatization
  // -------------------------------------------------------------------------

  public synchronized void setParent(UUID quest, UUID parentOrNull) {
    try {
      update(
          "UPDATE quests SET parent_id = ? WHERE id = ?",
          parentOrNull == null ? null : parentOrNull.toString(),
          quest.toString());
    } catch (SQLException e) {
      throw new RuntimeException("Failed to set parent", e);
    }
  }

  public synchronized void setPrerequisites(UUID quest, List<UUID> prerequisites) {
    inTransaction(
        "set prerequisites",
        () -> {
          update(
              "DELETE FROM quest_links WHERE from_id = ? AND kind = ?",
              quest.toString(),
              PREREQUISITE);
          for (UUID prerequisite : prerequisites) {
            update(
                "INSERT OR IGNORE INTO quest_links (from_id, to_id, kind) VALUES (?, ?, ?)",
                quest.toString(),
                prerequisite.toString(),
                PREREQUISITE);
          }
        });
  }

  public synchronized void setAutoJoin(UUID quest, boolean autoJoin) {
    try {
      update("UPDATE quests SET auto_join = ? WHERE id = ?", autoJoin ? 1 : 0, quest.toString());
    } catch (SQLException e) {
      throw new RuntimeException("Failed to set auto-join", e);
    }
  }

  /**
   * Replaces everyone on the quest: the display owner (stored as OWNER) and {@code people}, who
   * keep the added-at of any role they already held. Does not check that an owner remains.
   */
  public synchronized void setPeople(UUID quest, List<ContributorData> people, UUID displayOwner) {
    inTransaction(
        "set people",
        () -> {
          Map<UUID, Long> addedAt = new HashMap<>();
          try (PreparedStatement stmt =
              connection.prepareStatement(
                  "SELECT player_uuid, added_at FROM quest_people WHERE quest_id = ?")) {
            stmt.setString(1, quest.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
              addedAt.put(UUID.fromString(rs.getString("player_uuid")), rs.getLong("added_at"));
            }
          }
          long next = nextAddedAt(quest);
          update("DELETE FROM quest_people WHERE quest_id = ?", quest.toString());
          insertPerson(quest, displayOwner, Role.OWNER, addedAt.getOrDefault(displayOwner, next++));
          for (ContributorData person : people) {
            if (person.uuid().equals(displayOwner)) continue;
            insertPerson(
                quest, person.uuid(), person.role(), addedAt.getOrDefault(person.uuid(), next++));
          }
          update(
              "UPDATE quests SET owner_uuid = ? WHERE id = ?",
              displayOwner.toString(),
              quest.toString());
        });
  }

  public synchronized void setRole(UUID quest, UUID player, Role role) {
    inTransaction(
        "set role",
        () -> {
          int changed =
              update(
                  "UPDATE quest_people SET role = ? WHERE quest_id = ? AND player_uuid = ?",
                  role.name(),
                  quest.toString(),
                  player.toString());
          if (changed == 0) insertPerson(quest, player, role, nextAddedAt(quest));
          moveDisplayOwnerIfNeeded(quest);
        });
  }

  public synchronized void removePerson(UUID quest, UUID player) {
    inTransaction(
        "remove person",
        () -> {
          update(
              "DELETE FROM quest_people WHERE quest_id = ? AND player_uuid = ?",
              quest.toString(),
              player.toString());
          moveDisplayOwnerIfNeeded(quest);
        });
  }

  /** When the display owner no longer holds OWNER, the earliest remaining owner takes the place. */
  private void moveDisplayOwnerIfNeeded(UUID quest) throws SQLException {
    update(
        """
        UPDATE quests SET owner_uuid = (
            SELECT player_uuid FROM quest_people
            WHERE quest_id = quests.id AND role = 'OWNER'
            ORDER BY added_at, player_uuid LIMIT 1)
        WHERE id = ?
          AND NOT EXISTS (SELECT 1 FROM quest_people
                          WHERE quest_id = quests.id AND player_uuid = quests.owner_uuid
                            AND role = 'OWNER')
          AND EXISTS (SELECT 1 FROM quest_people WHERE quest_id = quests.id AND role = 'OWNER')
        """,
        quest.toString());
  }

  private void insertPerson(UUID quest, UUID player, Role role, long addedAt) throws SQLException {
    update(
        "INSERT OR REPLACE INTO quest_people (quest_id, player_uuid, role, added_at)"
            + " VALUES (?, ?, ?, ?)",
        quest.toString(),
        player.toString(),
        role.name(),
        addedAt);
  }

  /** An added-at later than everyone already on the quest, so insertion order is kept. */
  private long nextAddedAt(UUID quest) throws SQLException {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "SELECT COALESCE(MAX(added_at), -1) FROM quest_people WHERE quest_id = ?")) {
      stmt.setString(1, quest.toString());
      ResultSet rs = stmt.executeQuery();
      long max = rs.next() ? rs.getLong(1) : -1;
      return Math.max(System.currentTimeMillis(), max + 1);
    }
  }

  /** Stores why the quest was made private and sets it Private. */
  public synchronized void setPrivatized(UUID quest, PrivatizeInfo info, long at) {
    try {
      setPrivatizedRaw(quest, info, at);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to set privatized", e);
    }
  }

  private void setPrivatizedRaw(UUID quest, PrivatizeInfo info, long at) throws SQLException {
    String saw =
        info.ownersWhoSawIt().stream()
            .map(UUID::toString)
            .sorted()
            .collect(Collectors.joining(","));
    update(
        """
        UPDATE quests SET visibility = 'PRIVATE', priv_previous = ?, priv_title = ?,
            priv_by = ?, priv_at = ?, priv_saw = ?
        WHERE id = ?
        """,
        info.previous().name(),
        info.deletedTitle(),
        info.deletedBy() == null ? null : info.deletedBy().toString(),
        at,
        saw,
        quest.toString());
  }

  public synchronized Set<UUID> privatizedSeenBy(UUID quest) {
    try (PreparedStatement stmt =
        connection.prepareStatement("SELECT priv_saw FROM quests WHERE id = ?")) {
      stmt.setString(1, quest.toString());
      ResultSet rs = stmt.executeQuery();
      return rs.next() ? parseUuidSet(rs.getString("priv_saw")) : Set.of();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to read who saw the deleted quest", e);
    }
  }

  /** Forgets the privatization; the visibility stays as it is. */
  public synchronized void clearPrivatized(UUID quest) {
    try {
      update(
          """
          UPDATE quests SET priv_previous = NULL, priv_title = NULL, priv_by = NULL,
              priv_at = NULL, priv_saw = NULL
          WHERE id = ?
          """,
          quest.toString());
    } catch (SQLException e) {
      throw new RuntimeException("Failed to clear privatized", e);
    }
  }

  /**
   * Applies a delete plan in one transaction: moves the kept quests, privatizes, drops the listed
   * prerequisite links, then deletes. Links to a deleted quest that are not listed stay dangling.
   */
  public synchronized void applyDeletePlan(DeletePlan plan, long at) {
    inTransaction(
        "apply delete plan",
        () -> {
          for (Map.Entry<UUID, Optional<UUID>> move : plan.reparent().entrySet()) {
            update(
                "UPDATE quests SET parent_id = ? WHERE id = ?",
                move.getValue().map(UUID::toString).orElse(null),
                move.getKey().toString());
          }
          for (Map.Entry<UUID, PrivatizeInfo> entry : plan.privatize().entrySet()) {
            setPrivatizedRaw(entry.getKey(), entry.getValue(), at);
          }
          for (Map.Entry<UUID, List<UUID>> entry : plan.prerequisitesToRemove().entrySet()) {
            for (UUID prerequisite : entry.getValue()) {
              update(
                  "DELETE FROM quest_links WHERE from_id = ? AND to_id = ? AND kind = ?",
                  entry.getKey().toString(),
                  prerequisite.toString(),
                  PREREQUISITE);
            }
          }
          for (UUID id : plan.deleted()) {
            deleteQuestRaw(id);
          }
        });
  }

  // -------------------------------------------------------------------------
  // SQL helpers
  // -------------------------------------------------------------------------

  @FunctionalInterface
  private interface SqlWork {
    void run() throws SQLException;
  }

  /** Runs the work in one transaction: all of it commits, or none of it does. */
  private void inTransaction(String what, SqlWork work) {
    try {
      boolean previous = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        work.run();
        connection.commit();
      } catch (SQLException | RuntimeException e) {
        try {
          connection.rollback();
        } catch (SQLException rollbackError) {
          e.addSuppressed(rollbackError);
        }
        throw e;
      } finally {
        connection.setAutoCommit(previous);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to " + what, e);
    }
  }

  /** Runs one statement; parameters are strings, longs, ints or null. */
  private int update(String sql, Object... params) throws SQLException {
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        Object p = params[i];
        if (p == null) stmt.setNull(i + 1, Types.NULL);
        else if (p instanceof Long l) stmt.setLong(i + 1, l);
        else if (p instanceof Integer n) stmt.setInt(i + 1, n);
        else stmt.setString(i + 1, p.toString());
      }
      return stmt.executeUpdate();
    }
  }

  /**
   * Adds roles, the quest tree and prerequisites. One transaction: either all of it applies or
   * none, and the existence of quest_people marks it done, so a failure is retried on next start.
   */
  private void migrateRolesAndTree() throws SQLException {
    boolean done = tableExists("quest_people");
    Set<String> columns = new java.util.HashSet<>();
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("PRAGMA table_info(quests)")) {
      while (rs.next()) columns.add(rs.getString("name"));
    }
    boolean previousAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      try (Statement stmt = connection.createStatement()) {
        for (String[] column :
            new String[][] {
              {"parent_id", "TEXT"},
              {"auto_join", "INTEGER NOT NULL DEFAULT 0"},
              {"priv_previous", "TEXT"},
              {"priv_title", "TEXT"},
              {"priv_by", "TEXT"},
              {"priv_at", "INTEGER"},
              {"priv_saw", "TEXT"}
            }) {
          if (!columns.contains(column[0])) {
            stmt.executeUpdate("ALTER TABLE quests ADD COLUMN " + column[0] + " " + column[1]);
          }
        }
        stmt.executeUpdate(PEOPLE_DDL);
        stmt.executeUpdate(LINKS_DDL);
        if (!done) {
          // Plain INSERTs, not OR IGNORE: that would also swallow a bad row and hide a failure.
          // Owners first so an owner who is also in contributors stays OWNER.
          stmt.executeUpdate(
              "INSERT INTO quest_people (quest_id, player_uuid, role, added_at)"
                  + " SELECT id, owner_uuid, 'OWNER', 0 FROM quests");
          stmt.executeUpdate(
              "INSERT INTO quest_people (quest_id, player_uuid, role, added_at)"
                  + " SELECT c.quest_id, c.player_uuid,"
                  + " CASE WHEN c.can_edit = 1 THEN 'WRITE' ELSE 'READ' END, 1"
                  + " FROM contributors c WHERE c.quest_id IN (SELECT id FROM quests)"
                  + " AND NOT EXISTS (SELECT 1 FROM quest_people p"
                  + " WHERE p.quest_id = c.quest_id AND p.player_uuid = c.player_uuid)");
        }
      }
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(previousAutoCommit);
    }
  }

  private boolean tableExists(String table) throws SQLException {
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
      return rs.next();
    }
  }

  /** One-off migration to the polygon location model; runs when quest_polygons is first created. */
  private void migrateLocations(String defaultMap) throws SQLException {
    boolean previousAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      try (Statement stmt = connection.createStatement()) {
        stmt.executeUpdate(POLYGONS_DDL);
        stmt.executeUpdate(
            "UPDATE quests SET coord2_x = NULL, coord2_y = NULL, coord2_z = NULL, is_region = 0"
                + " WHERE coord_x IS NULL AND coord2_x IS NOT NULL");
      }

      try (Statement stmt = connection.createStatement();
          ResultSet rs =
              stmt.executeQuery(
                  "SELECT id, coord_x, coord_y, coord_z, coord2_x, coord2_y, coord2_z FROM quests"
                      + " WHERE is_region = 1 AND coord_x IS NOT NULL AND coord_z IS NOT NULL"
                      + " AND coord2_x IS NOT NULL AND coord2_z IS NOT NULL");
          PreparedStatement ins =
              connection.prepareStatement(
                  "INSERT INTO quest_polygons (quest_id, idx, x, z) VALUES (?, ?, ?, ?)")) {
        while (rs.next()) {
          List<PointData> polygon =
              QuestLocation.rectangleToPolygon(
                  new CoordinatesData(
                      rs.getDouble("coord_x"), rs.getDouble("coord_y"), rs.getDouble("coord_z")),
                  new CoordinatesData(
                      rs.getDouble("coord2_x"),
                      rs.getDouble("coord2_y"),
                      rs.getDouble("coord2_z")));
          for (int i = 0; i < polygon.size(); i++) {
            ins.setString(1, rs.getString("id"));
            ins.setInt(2, i);
            ins.setDouble(3, polygon.get(i).x());
            ins.setDouble(4, polygon.get(i).z());
            ins.addBatch();
          }
        }
        ins.executeBatch();
      }

      try (PreparedStatement stmt =
          connection.prepareStatement(
              "UPDATE quests SET map = ? WHERE map IS NULL AND coord_x IS NOT NULL")) {
        stmt.setString(1, defaultMap);
        stmt.executeUpdate();
      }

      try (Statement stmt = connection.createStatement()) {
        stmt.executeUpdate("UPDATE quests SET map = 'the_nether' WHERE map = 'nether'");
        stmt.executeUpdate("UPDATE quests SET map = 'the_end' WHERE map = 'end'");
      }
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(previousAutoCommit);
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private QuestData mapQuestRow(ResultSet rs) throws SQLException {
    String coordX = rs.getString("coord_x");
    CoordinatesData coords =
        coordX != null
            ? new CoordinatesData(
                rs.getDouble("coord_x"), rs.getDouble("coord_y"), rs.getDouble("coord_z"))
            : null;

    String coord2X = rs.getString("coord2_x");
    CoordinatesData coords2 =
        coord2X != null
            ? new CoordinatesData(
                rs.getDouble("coord2_x"), rs.getDouble("coord2_y"), rs.getDouble("coord2_z"))
            : null;

    String completedBy = rs.getString("completed_by");
    String parent = rs.getString("parent_id");
    String privPrevious = rs.getString("priv_previous");
    Privatized privatized =
        privPrevious == null
            ? null
            : new Privatized(
                Visibility.valueOf(privPrevious),
                rs.getString("priv_title"),
                rs.getString("priv_by_name"),
                rs.getLong("priv_at"),
                parseUuidSet(rs.getString("priv_saw")));
    return new QuestData(
        UUID.fromString(rs.getString("id")),
        rs.getString("title"),
        rs.getString("content"),
        UUID.fromString(rs.getString("owner_uuid")),
        rs.getString("owner_name"),
        Visibility.valueOf(rs.getString("visibility")),
        new ArrayList<>(),
        rs.getLong("last_modified"),
        coords,
        rs.getInt("is_region") != 0,
        coords2,
        rs.getString("map"),
        List.of(),
        List.of(),
        rs.getLong("completed_at"),
        completedBy == null ? null : UUID.fromString(completedBy),
        rs.getString("completed_by_name"),
        parent == null ? null : UUID.fromString(parent),
        List.of(),
        false,
        false,
        false,
        rs.getInt("auto_join") != 0,
        false,
        privatized);
  }

  private static Set<UUID> parseUuidSet(String csv) {
    Set<UUID> out = new LinkedHashSet<>();
    if (csv == null || csv.isBlank()) return out;
    for (String part : csv.split(",")) {
      if (!part.isBlank()) out.add(UUID.fromString(part.trim()));
    }
    return out;
  }

  /** Loads people, links, tags and polygons for the quests, in one query each. */
  private List<QuestData> hydrate(List<QuestData> quests) {
    return withPolygonsAll(withTagsAll(withPeopleAll(withLinksAll(quests))));
  }

  /**
   * Everyone with a role on each quest except its display owner (the map's value), ordered by when
   * they were added, then by uuid.
   */
  private Map<UUID, List<ContributorData>> getPeopleBatch(Map<UUID, UUID> displayOwners) {
    if (displayOwners.isEmpty()) return Map.of();
    Map<UUID, List<ContributorData>> result = new HashMap<>();
    List<UUID> questIds = new ArrayList<>(displayOwners.keySet());
    for (UUID id : questIds) {
      result.put(id, new ArrayList<>());
    }
    String placeholders = String.join(",", questIds.stream().map(id -> "?").toList());
    String sql =
        "SELECT qp.quest_id, qp.player_uuid, qp.role, pn.name FROM quest_people qp "
            + "LEFT JOIN player_names pn ON qp.player_uuid = pn.uuid "
            + "WHERE qp.quest_id IN ("
            + placeholders
            + ") ORDER BY qp.added_at, qp.player_uuid";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      for (int i = 0; i < questIds.size(); i++) {
        stmt.setString(i + 1, questIds.get(i).toString());
      }
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        UUID questId = UUID.fromString(rs.getString("quest_id"));
        UUID player = UUID.fromString(rs.getString("player_uuid"));
        if (player.equals(displayOwners.get(questId))) continue;
        result
            .computeIfAbsent(questId, k -> new ArrayList<>())
            .add(
                new ContributorData(
                    player, rs.getString("name"), Role.valueOf(rs.getString("role"))));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to batch-load people", e);
    }
    return result;
  }

  private Map<UUID, List<UUID>> getPrerequisitesBatch(List<UUID> questIds) {
    if (questIds.isEmpty()) return Map.of();
    Map<UUID, List<UUID>> result = new HashMap<>();
    String placeholders = String.join(",", questIds.stream().map(id -> "?").toList());
    String sql =
        "SELECT from_id, to_id FROM quest_links WHERE kind = '"
            + PREREQUISITE
            + "' AND from_id IN ("
            + placeholders
            + ") ORDER BY rowid";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      for (int i = 0; i < questIds.size(); i++) {
        stmt.setString(i + 1, questIds.get(i).toString());
      }
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        result
            .computeIfAbsent(UUID.fromString(rs.getString("from_id")), k -> new ArrayList<>())
            .add(UUID.fromString(rs.getString("to_id")));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to batch-load prerequisites", e);
    }
    return result;
  }

  private List<QuestData> withLinksAll(List<QuestData> quests) {
    Map<UUID, List<UUID>> links =
        getPrerequisitesBatch(quests.stream().map(QuestData::id).toList());
    List<QuestData> result = new ArrayList<>(quests.size());
    for (QuestData quest : quests) {
      result.add(quest.withPrerequisites(links.getOrDefault(quest.id(), List.of())));
    }
    return result;
  }

  private List<QuestData> withPeopleAll(List<QuestData> quests) {
    Map<UUID, UUID> displayOwners = new HashMap<>();
    for (QuestData quest : quests) displayOwners.put(quest.id(), quest.ownerUuid());
    Map<UUID, List<ContributorData>> people = getPeopleBatch(displayOwners);
    List<QuestData> result = new ArrayList<>(quests.size());
    for (QuestData quest : quests) {
      result.add(quest.withContributors(people.getOrDefault(quest.id(), List.of())));
    }
    return result;
  }

  private Map<UUID, List<String>> getTagsBatch(List<UUID> questIds) {
    if (questIds.isEmpty()) return Map.of();
    Map<UUID, List<String>> result = new HashMap<>();
    for (UUID id : questIds) {
      result.put(id, new ArrayList<>());
    }
    String placeholders = String.join(",", questIds.stream().map(id -> "?").toList());
    String sql = "SELECT quest_id, tag FROM quest_tags WHERE quest_id IN (" + placeholders + ")";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      for (int i = 0; i < questIds.size(); i++) {
        stmt.setString(i + 1, questIds.get(i).toString());
      }
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        UUID questId = UUID.fromString(rs.getString("quest_id"));
        result.computeIfAbsent(questId, k -> new ArrayList<>()).add(rs.getString("tag"));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to batch-load tags", e);
    }
    return result;
  }

  private List<QuestData> withTagsAll(List<QuestData> quests) {
    List<UUID> questIds = quests.stream().map(QuestData::id).toList();
    Map<UUID, List<String>> tagMap = getTagsBatch(questIds);
    List<QuestData> result = new ArrayList<>(quests.size());
    for (QuestData quest : quests) {
      result.add(quest.withTags(tagMap.getOrDefault(quest.id(), List.of())));
    }
    return result;
  }

  private Map<UUID, List<PointData>> getPolygonsBatch(List<UUID> questIds) {
    if (questIds.isEmpty()) return Map.of();
    Map<UUID, List<PointData>> result = new HashMap<>();
    String placeholders = String.join(",", questIds.stream().map(id -> "?").toList());
    String sql =
        "SELECT quest_id, x, z FROM quest_polygons WHERE quest_id IN ("
            + placeholders
            + ") ORDER BY quest_id, idx";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      for (int i = 0; i < questIds.size(); i++) {
        stmt.setString(i + 1, questIds.get(i).toString());
      }
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        UUID questId = UUID.fromString(rs.getString("quest_id"));
        result
            .computeIfAbsent(questId, k -> new ArrayList<>())
            .add(new PointData(rs.getDouble("x"), rs.getDouble("z")));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to batch-load polygons", e);
    }
    return result;
  }

  private List<QuestData> withPolygonsAll(List<QuestData> quests) {
    List<UUID> questIds = quests.stream().map(QuestData::id).toList();
    Map<UUID, List<PointData>> polygonMap = getPolygonsBatch(questIds);
    List<QuestData> result = new ArrayList<>(quests.size());
    for (QuestData quest : quests) {
      result.add(quest.withPolygon(polygonMap.getOrDefault(quest.id(), List.of())));
    }
    return result;
  }

  synchronized int countPolygonRows() {
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM quest_polygons")) {
      return rs.next() ? rs.getInt(1) : 0;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to count polygon rows", e);
    }
  }

  /** Returns all distinct tags from the DB merged with config seed tags, alphabetically sorted. */
  public synchronized List<String> getAllDistinctTags(List<String> seedTags) {
    java.util.TreeSet<String> tags = new java.util.TreeSet<>();
    for (String seed : seedTags) {
      tags.add(seed.toLowerCase());
    }
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT DISTINCT tag FROM quest_tags")) {
      while (rs.next()) {
        tags.add(rs.getString("tag"));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get distinct tags", e);
    }
    return new ArrayList<>(tags);
  }
}
