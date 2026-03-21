package com.disqt.disquests.test.integration.harness;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class TestContext {
    private static ClientGameTestContext context;
    private static String playerRole;

    private TestContext() {}

    public static void set(ClientGameTestContext ctx, String role) {
        context = ctx;
        playerRole = role;
    }

    public static ClientGameTestContext get() {
        if (context == null) throw new IllegalStateException("TestContext not initialized -- are you running inside the harness?");
        return context;
    }

    public static String getPlayerRole() {
        return playerRole;
    }

    public static boolean isPlayerA() {
        return "PlayerA".equals(playerRole);
    }

    public static boolean isPlayerB() {
        return "PlayerB".equals(playerRole);
    }
}
