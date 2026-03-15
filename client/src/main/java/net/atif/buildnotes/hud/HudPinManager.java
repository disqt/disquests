package net.atif.buildnotes.hud;

import net.atif.buildnotes.client.ClientCache;
import net.atif.buildnotes.data.Note;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class HudPinManager {
    private static UUID pinnedNoteId = null;
    private static Path configPath;

    public static void init(Path configDir) {
        configPath = configDir.resolve("buildnotes_pin.txt");
        load();
    }

    public static void pin(UUID noteId) {
        pinnedNoteId = noteId;
        save();
    }

    public static void unpin() {
        pinnedNoteId = null;
        save();
    }

    public static void toggle(UUID noteId) {
        if (noteId.equals(pinnedNoteId)) unpin();
        else pin(noteId);
    }

    public static boolean isPinned(UUID noteId) {
        return noteId != null && noteId.equals(pinnedNoteId);
    }

    public static UUID getPinnedNoteId() { return pinnedNoteId; }

    public static Note getPinnedNote() {
        if (pinnedNoteId == null) return null;
        return ClientCache.getNoteById(pinnedNoteId);
    }

    private static void load() {
        try {
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath).trim();
                if (!content.isEmpty()) pinnedNoteId = UUID.fromString(content);
            }
        } catch (Exception ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, pinnedNoteId != null ? pinnedNoteId.toString() : "");
        } catch (IOException ignored) {}
    }
}
