package com.disqt.disquests.test.integration.bdd;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.test.integration.harness.RconClient;
import com.disqt.disquests.test.integration.harness.TestContext;
import io.wispforest.owo.ui.core.UIComponent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * UI interaction helpers using GLFW physical input (TestInput).
 * Never call screen methods directly -- always go through the real input pipeline.
 */
public final class UIActions {
    private static final Logger LOG = LoggerFactory.getLogger("Disquests/E2E");
    public static final int TIMEOUT = 30 * 20; // 30 seconds in ticks

    private UIActions() {}

    // --- Server reset ---

    /**
     * Sends RCON "disquests reset" to wipe the server DB, then clears client cache
     * and requests a fresh sync. Waits for the sync to complete.
     * Call this from @BeforeAll in journey classes instead of raw RCON + Thread.sleep.
     */
    /**
     * Full reset for single-player journeys: RCON reset + cache clear + sync.
     * Safe to call from @BeforeAll on any client -- guards against missing client.
     */
    public static void resetServerAndSync() {
        // Guard: skip entirely if client isn't running
        // (e.g. PlayerB executing @BeforeAll on a PlayerA-only journey class)
        ClientGameTestContext context;
        try {
            context = TestContext.get();
            // Test that runOnClient works
            context.computeOnClient(c -> c.player != null);
        } catch (Exception e) {
            LOG.info("resetServerAndSync skipped (no client): {}", e.getMessage());
            return;
        }

        // RCON reset
        try {
            var rcon = new RconClient("localhost",
                Integer.parseInt(System.getProperty("disquests.test.rcon.port", "25575")));
            rcon.login(System.getProperty("disquests.test.rcon.password", "testpassword"));
            rcon.command("disquests reset");
            rcon.close();
        } catch (Exception e) {
            LOG.warn("RCON reset failed: {}", e.getMessage());
        }

        // Clear client cache and close screens
        context.runOnClient(c -> {
            ClientCache.clear();
            if (c.currentScreen != null) c.setScreen(null);
        });

        // Wait for server re-handshake + sync
        context.waitTicks(40);
        context.runOnClient(c -> {
            com.disqt.disquests.client.network.PacketSender.requestSync();
        });
        context.waitTicks(20);
    }

    /**
     * Lightweight reset for two-player journeys: clear local cache only.
     * RCON reset is done once by the orchestrator at the start of the run.
     * PhaseSync signals are cleaned so previous journey signals don't leak.
     */
    public static void resetLocalState() {
        // Clean PhaseSync signals from previous journeys
        try {
            var syncDir = com.disqt.disquests.test.integration.PhaseSync.getSyncDir();
            if (java.nio.file.Files.exists(syncDir)) {
                try (var stream = java.nio.file.Files.list(syncDir)) {
                    stream.filter(p -> p.toString().endsWith(".done"))
                          .filter(p -> !p.getFileName().toString().startsWith("client-"))
                          .forEach(p -> { try { java.nio.file.Files.delete(p); } catch (Exception ignored) {} });
                }
            }
        } catch (Exception e) {
            LOG.warn("PhaseSync cleanup failed: {}", e.getMessage());
        }

        // Clear client cache
        try {
            ClientGameTestContext context = TestContext.get();
            context.runOnClient(c -> {
                ClientCache.clear();
                if (c.currentScreen != null) c.setScreen(null);
            });
            context.waitTicks(5);
        } catch (Exception e) {
            LOG.warn("Local state reset failed: {}", e.getMessage());
        }
    }

    // --- Connection ---

    public static void connectAndWait(ClientGameTestContext context) {
        String host = System.getProperty("disquests.test.server.host", "localhost");
        int port = Integer.getInteger("disquests.test.server.port", 25565);
        String address = host + ":" + port;

        context.runOnClient(client -> {
            ServerAddress serverAddress = ServerAddress.parse(address);
            ServerInfo serverInfo = new ServerInfo("Test", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client, serverAddress, serverInfo, false, null);
        });

        // Wait for player entity
        context.waitFor(client -> client.player != null, TIMEOUT);
        // Wait for Disquests handshake
        context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);
        context.waitTicks(10);
    }

    public static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
        context.waitForScreen(TitleScreen.class);
    }

    // --- Screen navigation ---

    public static void openMainScreen(ClientGameTestContext context) {
        context.runOnClient(client -> client.setScreen(new MainScreen(null)));
        context.waitForScreen(MainScreen.class);
        context.waitTicks(2);
    }

    public static <T extends Screen> void waitForScreen(ClientGameTestContext context, Class<T> screenClass) {
        context.waitFor(client -> screenClass.isInstance(client.currentScreen), TIMEOUT);
        context.waitTicks(2);
    }

    // --- Component interaction ---

    /**
     * Click a component by its XML/programmatic ID.
     * Uses GLFW physical input: setCursorPos + pressMouse.
     */
    public static void click(ClientGameTestContext context, String componentId) {
        double scale = context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
        double[] pos = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) throw new AssertionError("Screen root not initialized");
                var component = root.childById(UIComponent.class, componentId);
                if (component == null) throw new AssertionError("Component not found: " + componentId);
                return new double[]{component.x() + component.width() / 2.0, component.y() + component.height() / 2.0};
            }
            throw new AssertionError("Current screen is not a Disquests screen");
        });
        context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    /**
     * Click a QuestEntryComponent by index in the quest list.
     * Entries are children of the "quest-list" FlowLayout.
     */
    public static void clickEntry(ClientGameTestContext context, int index) {
        double scale = context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
        double[] pos = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) throw new AssertionError("Screen root not initialized");
                var questList = root.childById(io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
                if (questList == null) throw new AssertionError("quest-list not found");
                var children = questList.children();
                if (index >= children.size()) throw new AssertionError("Entry index " + index + " out of bounds, size=" + children.size());
                var entry = children.get(index);
                return new double[]{entry.x() + entry.width() / 2.0, entry.y() + entry.height() / 2.0};
            }
            throw new AssertionError("Current screen is not a Disquests screen");
        });
        context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    /**
     * Double-click a QuestEntryComponent by index.
     */
    public static void doubleClickEntry(ClientGameTestContext context, int index) {
        clickEntry(context, index);
        context.waitTicks(1);
        clickEntry(context, index);
    }

    /**
     * Click the pin icon on a QuestEntryComponent by index.
     * Pin icon is in the rightmost 20px of the entry, vertically centered.
     */
    public static void clickPinIcon(ClientGameTestContext context, int index) {
        double scale = context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
        double[] pos = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root == null) throw new AssertionError("Screen root not initialized");
                var questList = root.childById(io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
                if (questList == null) throw new AssertionError("quest-list not found");
                var entry = questList.children().get(index);
                // Pin icon is rightmost 10px, vertically at y+19 (center of rows 2-3)
                return new double[]{entry.x() + entry.width() - 10.0, entry.y() + 19.0};
            }
            throw new AssertionError("Current screen is not a Disquests screen");
        });
        context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    /**
     * Type text into a focused text field.
     * Must click the field first to focus it.
     * Clears existing text by directly manipulating the widget,
     * then types the new text via GLFW character input.
     * Handles both TextFieldComponent (our custom multi-line) and
     * TextBoxComponent (owo-ui single-line).
     */
    public static void type(ClientGameTestContext context, String componentId, String text) {
        click(context, componentId);
        context.waitTicks(1); // focus desync workaround for GreedyInputUIComponent
        // Clear existing text by directly manipulating the widget.
        // GLFW Ctrl+A routing can be unreliable through owo-ui layers,
        // so we clear via runOnClient for robustness.
        context.runOnClient(c -> {
            if (c.currentScreen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                if (root != null) {
                    // Try our custom TextFieldComponent first
                    var textField = root.childById(
                        com.disqt.disquests.client.gui.component.TextFieldComponent.class, componentId);
                    if (textField != null) {
                        textField.getDelegate().setText("");
                        textField.getDelegate().setFocused(true);
                        return;
                    }
                    // Fall back to owo-ui TextBoxComponent
                    var textBox = root.childById(
                        io.wispforest.owo.ui.component.TextBoxComponent.class, componentId);
                    if (textBox != null) {
                        textBox.text("");
                    }
                }
            }
        });
        context.waitTicks(1);
        // Re-click to ensure owo-ui focus is restored after setText (which clears focused)
        click(context, componentId);
        context.waitTicks(1);
        // Type new text
        context.getInput().typeChars(text);
        context.waitTicks(2);
    }

    /**
     * Append text to a text field without clearing existing content.
     */
    public static void appendText(ClientGameTestContext context, String componentId, String text) {
        click(context, componentId);
        context.waitTicks(1);
        // Move to end (Ctrl+End)
        context.getInput().holdControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_END);
        context.getInput().releaseControl();
        context.getInput().typeChars(text);
        context.waitTicks(2);
    }

    /**
     * Press Ctrl+Z (undo) on the currently focused field.
     */
    public static void undo(ClientGameTestContext context) {
        context.getInput().holdControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_Z);
        context.getInput().releaseControl();
        context.waitTicks(2);
    }

    /**
     * Press Ctrl+Z repeatedly to undo N actions.
     * Each typeChars character creates a separate undo action,
     * so use this to undo an entire typed string.
     */
    public static void undoN(ClientGameTestContext context, int count) {
        for (int i = 0; i < count; i++) {
            context.getInput().holdControl();
            context.getInput().pressKey(GLFW.GLFW_KEY_Z);
            context.getInput().releaseControl();
        }
        context.waitTicks(2);
    }

    /**
     * Press Ctrl+Y (redo) on the currently focused field.
     */
    public static void redo(ClientGameTestContext context) {
        context.getInput().holdControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_Y);
        context.getInput().releaseControl();
        context.waitTicks(2);
    }

    /**
     * Press Ctrl+Y repeatedly to redo N actions.
     */
    public static void redoN(ClientGameTestContext context, int count) {
        for (int i = 0; i < count; i++) {
            context.getInput().holdControl();
            context.getInput().pressKey(GLFW.GLFW_KEY_Y);
            context.getInput().releaseControl();
        }
        context.waitTicks(2);
    }

    // --- Quest helpers ---

    /**
     * Wait for a quest to appear in cache by title.
     * @param myQuests true = search myQuests, false = search serverQuests
     */
    public static Quest waitForQuestByTitle(ClientGameTestContext context, String title, boolean myQuests) {
        context.waitFor(client -> {
            var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
            return list.stream().anyMatch(q -> title.equals(q.getTitle()));
        }, TIMEOUT);
        return context.computeOnClient(c -> {
            var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
            return list.stream().filter(q -> title.equals(q.getTitle())).findFirst().orElse(null);
        });
    }

    /**
     * Wait for a quest to be removed from myQuests.
     */
    public static void waitForQuestRemoved(ClientGameTestContext context, UUID questId) {
        context.waitFor(client ->
            ClientCache.getMyQuests().stream().noneMatch(q -> q.getId().equals(questId)),
            TIMEOUT
        );
    }

    /**
     * Wait for quest list in MainScreen to have exactly N entries.
     */
    public static void waitForEntryCount(ClientGameTestContext context, int count) {
        context.waitFor(client -> {
            Screen screen = client.currentScreen;
            if (screen instanceof DisquestsBaseScreen dScreen) {
                var root = dScreen.getRootComponent();
                var questList = root != null ? root.childById(io.wispforest.owo.ui.container.FlowLayout.class, "quest-list") : null;
                return questList != null && questList.children().size() == count;
            }
            return false;
        }, TIMEOUT);
    }

    /**
     * Offline-mode UUID generation (matches Paper's offline UUID format).
     */
    public static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Check if this test journey should be skipped based on -Ddisquests.test.journey filter.
     * Used by HarnessPlayerA/B to decide whether to run.
     */
    public static boolean shouldSkip(String journeyName) {
        String filter = System.getProperty("disquests.test.journey");
        return filter != null && !filter.isEmpty() && !filter.equals(journeyName);
    }
}
