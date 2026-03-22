package com.disqt.disquests.test.integration.harness;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;

/**
 * Synchronizes both clients at the start of each two-player journey class.
 * Both clients signal "ready" and wait for each other before tests begin.
 * This prevents race conditions where one client's setup deletes the other's signals.
 */
public class TwoPlayerSyncExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext ctx) throws Exception {
        String className = ctx.getRequiredTestClass().getSimpleName();
        String role = TestContext.getPlayerRole();
        ClientGameTestContext context = TestContext.get();

        // 1. Clean PhaseSync .done files from previous journeys (but not client-ready markers)
        try {
            var syncDir = PhaseSync.getSyncDir();
            if (Files.exists(syncDir)) {
                try (var stream = Files.list(syncDir)) {
                    stream.filter(p -> p.toString().endsWith(".done"))
                          .filter(p -> !p.getFileName().toString().startsWith("client-"))
                          .filter(p -> !p.getFileName().toString().contains("-ready"))
                          .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                }
            }
        } catch (Exception ignored) {}

        // 2. Clear local cache and close screens
        context.runOnClient(c -> {
            ClientCache.clear();
            if (c.currentScreen != null) c.setScreen(null);
        });
        context.waitTicks(5);

        // 3. Clear AbortOnFailureExtension state from previous journey
        AbortOnFailureExtension.clearFailures();

        // 4. Signal "I'm ready for this class"
        PhaseSync.signal(className + "-" + role + "-ready");

        // 5. Wait for the OTHER client to be ready
        String otherRole = role.equals("PlayerA") ? "PlayerB" : "PlayerA";
        PhaseSync.waitFor(className + "-" + otherRole + "-ready", context);

        // 6. Small settle time after both clients ready
        context.waitTicks(5);
    }
}
