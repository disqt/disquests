package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.model.CollaborationRequestData;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class DataManager {

  private Connection connection;
  private final Path dataDir;

  public DataManager(Path dataDir) {
    this.dataDir = dataDir;
  }

  public synchronized void initialize() {
    try {
      Files.createDirectories(dataDir);
      String url = "jdbc:sqlite:" + dataDir.resolve("disquests.db");
      connection = DriverManager.getConnection(url);
      try (Statement stmt = connection.createStatement()) {
        stmt.executeUpdate("PRAGMA foreign_keys = ON");
        stmt.executeUpdate("PRAGMA journal_mode = WAL");
      }
      createTables();
      migratePinnedQuests();
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
                        last_modified INTEGER NOT NULL
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

  public synchronized void saveQuest(QuestData quest) {
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
    } catch (SQLException e) {
      throw new RuntimeException("Failed to save quest", e);
    }
  }

  public synchronized boolean deleteQuest(UUID id) {
    try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM quests WHERE id = ?")) {
      stmt.setString(1, id.toString());
      return stmt.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete quest", e);
    }
  }

  public synchronized QuestData getQuest(UUID id) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "SELECT q.*, pn.name as owner_name FROM quests q LEFT JOIN player_names pn ON q.owner_uuid = pn.uuid WHERE q.id = ?")) {
      stmt.setString(1, id.toString());
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        QuestData quest = mapQuestRow(rs);
        return withTags(withContributors(quest));
      }
      return null;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get quest", e);
    }
  }

  public synchronized List<QuestData> getQuestsForPlayer(UUID playerUuid) {
    List<QuestData> quests = new ArrayList<>();
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                SELECT q.*, pn.name as owner_name FROM quests q
                LEFT JOIN player_names pn ON q.owner_uuid = pn.uuid
                WHERE q.owner_uuid = ?
                   OR q.id IN (SELECT quest_id FROM contributors WHERE player_uuid = ?)
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
    return withTagsAll(withContributorsAll(quests));
  }

  public synchronized List<QuestData> getServerQuests(UUID excludePlayerUuid) {
    List<QuestData> quests = new ArrayList<>();
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                SELECT q.*, pn.name as owner_name FROM quests q
                LEFT JOIN player_names pn ON q.owner_uuid = pn.uuid
                WHERE q.visibility IN ('OPEN', 'CLOSED')
                  AND q.owner_uuid != ?
                  AND q.id NOT IN (SELECT quest_id FROM contributors WHERE player_uuid = ?)
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
    return withTagsAll(withContributorsAll(quests));
  }

  public synchronized Map<String, QuestData> findQuestsByTitlesIgnoreCase(List<String> titles) {
    if (titles.isEmpty()) return Map.of();
    Map<String, QuestData> result = new HashMap<>();
    String placeholders = titles.stream().map(t -> "?").collect(Collectors.joining(","));
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "SELECT q.*, pn.name as owner_name FROM quests q "
                + "LEFT JOIN player_names pn ON q.owner_uuid = pn.uuid "
                + "WHERE LOWER(q.title) IN ("
                + placeholders
                + ")")) {
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
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                INSERT INTO contributors (quest_id, player_uuid, can_edit) VALUES (?, ?, ?)
                ON CONFLICT(quest_id, player_uuid) DO UPDATE SET can_edit = excluded.can_edit
                """)) {
      stmt.setString(1, questId.toString());
      stmt.setString(2, playerUuid.toString());
      stmt.setInt(3, canEdit ? 1 : 0);
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to add contributor", e);
    }
  }

  public synchronized void leaveQuest(UUID questId, UUID playerUuid) {
    removeContributor(questId, playerUuid);
    unpinQuest(playerUuid, questId);
  }

  public synchronized void removeContributor(UUID questId, UUID playerUuid) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "DELETE FROM contributors WHERE quest_id = ? AND player_uuid = ?")) {
      stmt.setString(1, questId.toString());
      stmt.setString(2, playerUuid.toString());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to remove contributor", e);
    }
  }

  public synchronized void updateContributor(UUID questId, UUID playerUuid, boolean canEdit) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "UPDATE contributors SET can_edit = ? WHERE quest_id = ? AND player_uuid = ?")) {
      stmt.setInt(1, canEdit ? 1 : 0);
      stmt.setString(2, questId.toString());
      stmt.setString(3, playerUuid.toString());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update contributor", e);
    }
  }

  public synchronized List<ContributorData> getContributors(UUID questId) {
    List<ContributorData> contributors = new ArrayList<>();
    try (PreparedStatement stmt =
        connection.prepareStatement(
            """
                SELECT c.player_uuid, c.can_edit, pn.name FROM contributors c
                LEFT JOIN player_names pn ON c.player_uuid = pn.uuid
                WHERE c.quest_id = ?
                """)) {
      stmt.setString(1, questId.toString());
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        contributors.add(
            new ContributorData(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("name"),
                rs.getInt("can_edit") != 0));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get contributors", e);
    }
    return contributors;
  }

  public synchronized boolean isContributor(UUID questId, UUID playerUuid) {
    try (PreparedStatement stmt =
        connection.prepareStatement(
            "SELECT 1 FROM contributors WHERE quest_id = ? AND player_uuid = ?")) {
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
                WHERE q.owner_uuid = ?
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
                WHERE q.owner_uuid = ?
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
                WHERE q.owner_uuid = ?
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
      stmt.executeUpdate("DELETE FROM quest_tags");
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
        List.of());
  }

  private QuestData withContributors(QuestData quest) {
    List<ContributorData> contributors = getContributors(quest.id());
    return new QuestData(
        quest.id(),
        quest.title(),
        quest.content(),
        quest.ownerUuid(),
        quest.ownerName(),
        quest.visibility(),
        contributors,
        quest.lastModified(),
        quest.coordinates(),
        quest.isRegion(),
        quest.coordinates2(),
        quest.map(),
        quest.tags());
  }

  private Map<UUID, List<ContributorData>> getContributorsBatch(List<UUID> questIds) {
    if (questIds.isEmpty()) return Map.of();
    Map<UUID, List<ContributorData>> result = new HashMap<>();
    for (UUID id : questIds) {
      result.put(id, new ArrayList<>());
    }
    String placeholders = String.join(",", questIds.stream().map(id -> "?").toList());
    String sql =
        "SELECT c.quest_id, c.player_uuid, c.can_edit, pn.name FROM contributors c "
            + "LEFT JOIN player_names pn ON c.player_uuid = pn.uuid "
            + "WHERE c.quest_id IN ("
            + placeholders
            + ")";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      for (int i = 0; i < questIds.size(); i++) {
        stmt.setString(i + 1, questIds.get(i).toString());
      }
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        UUID questId = UUID.fromString(rs.getString("quest_id"));
        result
            .computeIfAbsent(questId, k -> new ArrayList<>())
            .add(
                new ContributorData(
                    UUID.fromString(rs.getString("player_uuid")),
                    rs.getString("name"),
                    rs.getInt("can_edit") != 0));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to batch-load contributors", e);
    }
    return result;
  }

  private List<QuestData> withContributorsAll(List<QuestData> quests) {
    List<UUID> questIds = quests.stream().map(QuestData::id).toList();
    Map<UUID, List<ContributorData>> contributorMap = getContributorsBatch(questIds);
    List<QuestData> result = new ArrayList<>(quests.size());
    for (QuestData quest : quests) {
      List<ContributorData> contributors = contributorMap.getOrDefault(quest.id(), List.of());
      result.add(
          new QuestData(
              quest.id(),
              quest.title(),
              quest.content(),
              quest.ownerUuid(),
              quest.ownerName(),
              quest.visibility(),
              contributors,
              quest.lastModified(),
              quest.coordinates(),
              quest.isRegion(),
              quest.coordinates2(),
              quest.map(),
              quest.tags()));
    }
    return result;
  }

  private List<String> getTagsForQuest(UUID questId) {
    List<String> tags = new ArrayList<>();
    try (PreparedStatement stmt =
        connection.prepareStatement("SELECT tag FROM quest_tags WHERE quest_id = ?")) {
      stmt.setString(1, questId.toString());
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        tags.add(rs.getString("tag"));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get tags for quest", e);
    }
    return tags;
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

  private QuestData withTags(QuestData quest) {
    List<String> tags = getTagsForQuest(quest.id());
    return new QuestData(
        quest.id(),
        quest.title(),
        quest.content(),
        quest.ownerUuid(),
        quest.ownerName(),
        quest.visibility(),
        quest.contributors(),
        quest.lastModified(),
        quest.coordinates(),
        quest.isRegion(),
        quest.coordinates2(),
        quest.map(),
        tags);
  }

  private List<QuestData> withTagsAll(List<QuestData> quests) {
    List<UUID> questIds = quests.stream().map(QuestData::id).toList();
    Map<UUID, List<String>> tagMap = getTagsBatch(questIds);
    List<QuestData> result = new ArrayList<>(quests.size());
    for (QuestData quest : quests) {
      List<String> tags = tagMap.getOrDefault(quest.id(), List.of());
      result.add(
          new QuestData(
              quest.id(),
              quest.title(),
              quest.content(),
              quest.ownerUuid(),
              quest.ownerName(),
              quest.visibility(),
              quest.contributors(),
              quest.lastModified(),
              quest.coordinates(),
              quest.isRegion(),
              quest.coordinates2(),
              quest.map(),
              tags));
    }
    return result;
  }
}
