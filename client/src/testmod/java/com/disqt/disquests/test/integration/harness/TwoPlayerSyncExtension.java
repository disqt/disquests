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
        // Skip for @Nested inner classes -- only run once for the outer class.
        if (ctx.getRequiredTestClass().isMemberClass()) {
            return;
        }

        String className = ctx.getRequiredTestClass().getSimpleName();
        String role = TestContext.getPlayerRole();
        ClientGameTestContext context = TestContext.get();

        // 1. Nuke server state via RCON reset (wipes DB, re-sends handshakes)
        try {
            var rcon = new com.disqt.disquests.test.integration.harness.RconClient("localhost",
                Integer.parseInt(System.getProperty("disquests.test.rcon.port", "25575")));
            rcon.login(System.getProperty("disquests.test.rcon.password", "testpassword"));
            rcon.command("disquests reset");
            rcon.close();
        } catch (Exception ignored) {}

        // 2. Clear local cache and close screens
        context.runOnClient(c -> {
            ClientCache.clear();
            if (c.currentScreen != null) c.setScreen(null);
        });
        context.waitTicks(40); // wait for server re-handshake

        // 3. Clear failure state
        AbortOnFailureExtension.clearFailures();

        // 4. Barrier: both clients must be ready before tests start
        PhaseSync.signal(className + "-" + role + "-ready");
        String otherRole = role.equals("PlayerA") ? "PlayerB" : "PlayerA";
        PhaseSync.waitFor(className + "-" + otherRole + "-ready", context);
        context.waitTicks(5);
    }
}
