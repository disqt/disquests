package com.disqt.disquests.test.integration;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File-based coordination between two integration test clients.
 * Uses context.waitFor() to poll so the client thread keeps processing packets.
 */
public class PhaseSync {

    // Sync dir is at project root / integration-sync (shared between both client run dirs)
    private static final Path SYNC_DIR;
    static {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Path projectRoot = userDir.getParent().getParent(); // client/run -> client -> project
        SYNC_DIR = projectRoot.resolve("integration-sync");
    }

    public static void clean() throws IOException {
        if (Files.exists(SYNC_DIR)) {
            try (var files = Files.list(SYNC_DIR)) {
                files.forEach(f -> { try { Files.delete(f); } catch (IOException ignored) {} });
            }
        }
        Files.createDirectories(SYNC_DIR);
    }

    public static void signal(String phaseName) {
        try {
            Files.createDirectories(SYNC_DIR);
            Files.writeString(SYNC_DIR.resolve(phaseName + ".done"), "done");
        } catch (IOException e) {
            throw new RuntimeException("Failed to signal phase: " + phaseName, e);
        }
    }

    /**
     * Wait for another client to signal a phase, using context.waitFor()
     * so the client thread keeps processing packets while we poll.
     */
    public static void waitFor(String phaseName, ClientGameTestContext context) {
        Path marker = SYNC_DIR.resolve(phaseName + ".done");
        // Use longer timeout for phase sync (120s) since other client may be booting
        context.waitFor(client -> Files.exists(marker), 120 * 20);
    }
}
