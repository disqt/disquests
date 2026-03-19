package com.disqt.disquests.client.migration;

import com.disqt.disquests.client.network.PacketSender;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

public class BuildNotesMigrator {

    private static final Logger LOGGER = LoggerFactory.getLogger("Disquests");

    public static void migrateIfNeeded(String serverAddress) {
        Path notesDir = FabricLoader.getInstance().getGameDir()
                .resolve("notes").resolve("remote").resolve(serverAddress);

        if (!Files.isDirectory(notesDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir, "*.txt")) {
            for (Path file : stream) {
                migrateFile(file);
            }
            // Delete directory if empty
            try (DirectoryStream<Path> check = Files.newDirectoryStream(notesDir)) {
                if (!check.iterator().hasNext()) {
                    Files.delete(notesDir);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to migrate BuildNotes from {}", serverAddress, e);
        }
    }

    private static final long MAX_FILE_SIZE = 64 * 1024; // 64KB

    private static void migrateFile(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_SIZE) {
            LOGGER.warn("Skipping oversized BuildNotes file: {} ({} bytes)", file.getFileName(), Files.size(file));
            return;
        }
        String content = Files.readString(file);
        if (content.isBlank()) {
            Files.delete(file);
            return;
        }

        String title = file.getFileName().toString();
        if (title.endsWith(".txt")) {
            title = title.substring(0, title.length() - 4);
        }

        PacketSender.saveQuest(UUID.randomUUID(), title, content, null, false, null, null);
        Files.delete(file);
    }
}
