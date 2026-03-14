package com.disqt.buildnotes.paper;

import com.disqt.buildnotes.common.model.BuildData;
import com.disqt.buildnotes.common.model.CustomFieldData;
import com.disqt.buildnotes.common.model.NoteData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataManager {

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();
    private static final Type CUSTOM_FIELD_LIST_TYPE = new TypeToken<List<CustomFieldData>>() {}.getType();

    private final Path dataDir;
    private Connection connection;

    public DataManager(Path dataDir) {
        this.dataDir = dataDir;
    }

    public void initialize() {
        try {
            Files.createDirectories(dataDir);
            String url = "jdbc:sqlite:" + dataDir.resolve("buildnotes.db");
            connection = DriverManager.getConnection(url);
            createTables();
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS notes (
                        id TEXT PRIMARY KEY,
                        title TEXT,
                        content TEXT DEFAULT '',
                        owner_uuid TEXT,
                        created_at INTEGER,
                        updated_at INTEGER
                    )""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS builds (
                        id TEXT PRIMARY KEY,
                        name TEXT,
                        coordinates TEXT DEFAULT '',
                        dimension TEXT DEFAULT '',
                        description TEXT DEFAULT '',
                        credits TEXT DEFAULT '',
                        custom_fields_json TEXT DEFAULT '[]',
                        image_filenames_json TEXT DEFAULT '[]',
                        owner_uuid TEXT,
                        created_at INTEGER,
                        updated_at INTEGER
                    )""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS permissions (
                        entry_id TEXT,
                        entry_type TEXT CHECK(entry_type IN ('note', 'build')),
                        player_uuid TEXT,
                        level TEXT,
                        PRIMARY KEY (entry_id, entry_type, player_uuid)
                    )""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS images (
                        id TEXT PRIMARY KEY,
                        entry_id TEXT,
                        entry_type TEXT CHECK(entry_type IN ('note', 'build')),
                        filename TEXT,
                        data BLOB,
                        UNIQUE(entry_id, filename)
                    )""");
        }
    }

    public List<NoteData> getAllNotes() {
        List<NoteData> notes = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT id, title, content, owner_uuid, updated_at FROM notes ORDER BY updated_at DESC")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                notes.add(new NoteData(
                        UUID.fromString(rs.getString("id")),
                        rs.getLong("updated_at"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("title"),
                        rs.getString("content")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get notes", e);
        }
        return notes;
    }

    public void saveNote(NoteData note) {
        long now = System.currentTimeMillis();
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO notes (id, title, content, owner_uuid, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    content = excluded.content,
                    owner_uuid = excluded.owner_uuid,
                    updated_at = excluded.updated_at
                """)) {
            stmt.setString(1, note.id().toString());
            stmt.setString(2, note.title());
            stmt.setString(3, note.content());
            stmt.setString(4, note.ownerUuid().toString());
            stmt.setLong(5, now);
            stmt.setLong(6, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save note", e);
        }
    }

    public boolean deleteNote(UUID id) {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM notes WHERE id = ?")) {
            stmt.setString(1, id.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete note", e);
        }
    }

    public List<BuildData> getAllBuilds() {
        List<BuildData> builds = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT id, name, coordinates, dimension, description, credits, custom_fields_json, image_filenames_json, owner_uuid, updated_at FROM builds ORDER BY updated_at DESC")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                List<String> imageFileNames = GSON.fromJson(rs.getString("image_filenames_json"), STRING_LIST_TYPE);
                List<CustomFieldData> customFields = GSON.fromJson(rs.getString("custom_fields_json"), CUSTOM_FIELD_LIST_TYPE);
                builds.add(new BuildData(
                        UUID.fromString(rs.getString("id")),
                        rs.getLong("updated_at"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("name"),
                        rs.getString("coordinates"),
                        rs.getString("dimension"),
                        rs.getString("description"),
                        rs.getString("credits"),
                        imageFileNames,
                        customFields
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get builds", e);
        }
        return builds;
    }

    public void saveBuild(BuildData build) {
        long now = System.currentTimeMillis();
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO builds (id, name, coordinates, dimension, description, credits, custom_fields_json, image_filenames_json, owner_uuid, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    coordinates = excluded.coordinates,
                    dimension = excluded.dimension,
                    description = excluded.description,
                    credits = excluded.credits,
                    custom_fields_json = excluded.custom_fields_json,
                    image_filenames_json = excluded.image_filenames_json,
                    owner_uuid = excluded.owner_uuid,
                    updated_at = excluded.updated_at
                """)) {
            stmt.setString(1, build.id().toString());
            stmt.setString(2, build.name());
            stmt.setString(3, build.coordinates());
            stmt.setString(4, build.dimension());
            stmt.setString(5, build.description());
            stmt.setString(6, build.credits());
            stmt.setString(7, GSON.toJson(build.customFields()));
            stmt.setString(8, GSON.toJson(build.imageFileNames()));
            stmt.setString(9, build.ownerUuid().toString());
            stmt.setLong(10, now);
            stmt.setLong(11, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save build", e);
        }
    }

    public boolean deleteBuild(UUID id) {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM builds WHERE id = ?")) {
            stmt.setString(1, id.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete build", e);
        }
    }

    public void saveImage(UUID entryId, String entryType, String filename, byte[] data) {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO images (id, entry_id, entry_type, filename, data)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(entry_id, filename) DO UPDATE SET data = excluded.data
                """)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, entryId.toString());
            stmt.setString(3, entryType);
            stmt.setString(4, filename);
            stmt.setBytes(5, data);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save image", e);
        }
    }

    public byte[] getImage(UUID entryId, String filename) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT data FROM images WHERE entry_id = ? AND filename = ?")) {
            stmt.setString(1, entryId.toString());
            stmt.setString(2, filename);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBytes("data");
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get image", e);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // ignore on shutdown
            }
        }
    }
}
