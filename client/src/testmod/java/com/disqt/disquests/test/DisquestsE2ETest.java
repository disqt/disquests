package com.disqt.disquests.test;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.KeyBinds;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.EditQuestScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.ViewQuestScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * End-to-end tests for the Disquests mod.
 *
 * <p>Tests are sequential: each builds on state from previous tests.
 * Requires a Paper server running with the Disquests plugin, RCON enabled.
 */
public class DisquestsE2ETest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        test1_connectAndHandshake(context);
        test2_createQuest(context);
        test3_questPersistsAcrossReconnect(context);
        test4_hudPin(context);
        test5_deleteQuest(context);
    }

    /**
     * Test 1: Connect to server and verify handshake.
     * - Connect to the Paper server
     * - Wait for the Disquests handshake
     * - Assert ClientSession.isOnServer() == true
     * - Assert ClientCache.getMyQuests() is empty (fresh server)
     */
    private void test1_connectAndHandshake(ClientGameTestContext context) {
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);

        // Wait a moment for sync to complete
        context.waitTicks(20);

        boolean onServer = context.computeOnClient(client -> ClientSession.isOnServer());
        if (!onServer) {
            throw new AssertionError("Test 1 FAILED: Expected ClientSession.isOnServer() == true");
        }

        int questCount = context.computeOnClient(client -> ClientCache.getMyQuests().size());
        if (questCount != 0) {
            throw new AssertionError("Test 1 FAILED: Expected 0 quests on fresh server, got " + questCount);
        }

        context.takeScreenshot("01_connected_empty");
    }

    /**
     * Test 2: Create a quest through the UI.
     * - Open MainScreen via keybind (N)
     * - Click "New Quest"
     * - Fill in title and content on EditQuestScreen
     * - Click "Save"
     * - Verify ViewQuestScreen shows
     * - Go back to MainScreen
     * - Assert quest was added to cache
     */
    private void test2_createQuest(ClientGameTestContext context) {
        // Open main screen via keybind
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.takeScreenshot("02_main_screen_empty");

        // Click "New Quest" button
        context.clickScreenButton("New Quest");
        context.waitForScreen(EditQuestScreen.class);
        context.waitTicks(5);

        // Set title and content programmatically via widget setText —
        // avoids gametest focus/input unreliability with custom widgets.
        context.runOnClient(client -> {
            if (client.currentScreen instanceof EditQuestScreen editScreen) {
                editScreen.getTitleField().setText("E2E Test Quest");
                editScreen.getContentField().setText("This is a **test** quest");
            }
        });
        context.waitTicks(5);

        context.takeScreenshot("02_edit_quest_filled");

        // Click "Save"
        context.clickScreenButton("Save");
        context.waitTicks(20); // Wait for server round-trip

        // After save, EditQuestScreen opens ViewQuestScreen
        context.waitForScreen(ViewQuestScreen.class);
        context.takeScreenshot("02_view_quest_rendered");

        // Press Escape to go back to MainScreen (ViewQuestScreen.close() returns to parent)
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(10);

        // We should be back at MainScreen (the parent chain: EditQuestScreen parent = MainScreen)
        context.waitForScreen(MainScreen.class);

        // Verify quest was created
        int questCount = context.computeOnClient(client -> ClientCache.getMyQuests().size());
        if (questCount != 1) {
            throw new AssertionError("Test 2 FAILED: Expected 1 quest, got " + questCount);
        }

        String title = context.computeOnClient(client -> {
            List<Quest> quests = ClientCache.getMyQuests();
            return quests.isEmpty() ? null : quests.get(0).getTitle();
        });
        if (!"E2E Test Quest".equals(title)) {
            throw new AssertionError("Test 2 FAILED: Expected title 'E2E Test Quest', got '" + title + "'");
        }

        context.takeScreenshot("02_main_screen_with_quest");

        // Close main screen
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
    }

    /**
     * Test 3: Quest persists across reconnect.
     * - Disconnect from server
     * - Reconnect
     * - Wait for handshake + sync
     * - Assert quest is still in cache with correct title
     */
    private void test3_questPersistsAcrossReconnect(ClientGameTestContext context) {
        TestHelper.disconnect(context);
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);

        // Wait for sync to deliver quests
        context.waitTicks(40);

        int questCount = context.computeOnClient(client -> ClientCache.getMyQuests().size());
        if (questCount != 1) {
            throw new AssertionError("Test 3 FAILED: Expected 1 quest after reconnect, got " + questCount);
        }

        String title = context.computeOnClient(client -> {
            List<Quest> quests = ClientCache.getMyQuests();
            return quests.isEmpty() ? null : quests.get(0).getTitle();
        });
        if (!"E2E Test Quest".equals(title)) {
            throw new AssertionError("Test 3 FAILED: Expected title 'E2E Test Quest' after reconnect, got '" + title + "'");
        }
    }

    /**
     * Test 4: HUD pin toggle.
     * - Open MainScreen, select the quest, click Open
     * - On ViewQuestScreen, click "Pin to HUD"
     * - Close UI, take screenshot (HUD should show pinned quest)
     * - Assert pinnedQuestId is set
     * - Reopen quest, click "Unpin"
     * - Close UI
     * - Assert pinnedQuestId is null
     */
    private void test4_hudPin(ClientGameTestContext context) {
        // Open main screen
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.waitTicks(5);

        // Click "Open" to open the selected quest
        // First we need to select it - click on the quest list entry
        // Since there's only one quest, clicking Open should work if auto-selected,
        // but the Open button requires a selection. Let's try clicking Open directly.
        // If the button is inactive (no selection), we may need a different approach.
        // The quest list doesn't auto-select, so Open will be inactive.
        // Instead, we can try double-clicking, but the API doesn't support direct list clicks.
        // Let's use computeOnClient to programmatically open the quest.
        context.runOnClient(client -> {
            List<Quest> quests = ClientCache.getMyQuests();
            if (!quests.isEmpty()) {
                Quest quest = quests.get(0);
                client.setScreen(new ViewQuestScreen(client.currentScreen, quest));
            }
        });
        context.waitForScreen(ViewQuestScreen.class);
        context.waitTicks(5);

        // Click "Pin to HUD"
        context.clickScreenButton("Pin to HUD");
        context.waitTicks(10);

        // Close the screen (go back to game)
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
        // May go back to MainScreen first
        if (context.computeOnClient(client -> client.currentScreen != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);
        }
        context.waitForScreen(null);
        context.waitTicks(20);

        // Take screenshot showing HUD pin
        context.takeScreenshot("04_hud_pin_visible");

        // Verify pin state
        boolean isPinned = context.computeOnClient(client ->
                ClientSession.getPinnedQuestId() != null);
        if (!isPinned) {
            throw new AssertionError("Test 4 FAILED: Expected pinnedQuestId to be set after pinning");
        }

        // Reopen quest to unpin
        context.runOnClient(client -> {
            List<Quest> quests = ClientCache.getMyQuests();
            if (!quests.isEmpty()) {
                Quest quest = quests.get(0);
                client.setScreen(new ViewQuestScreen(null, quest));
            }
        });
        context.waitForScreen(ViewQuestScreen.class);
        context.waitTicks(5);

        // Button should now say "Unpin"
        context.clickScreenButton("Unpin");
        context.waitTicks(10);

        // Close screen
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
        context.waitTicks(10);

        context.takeScreenshot("04_hud_pin_removed");

        // Verify unpin state
        boolean isUnpinned = context.computeOnClient(client ->
                ClientSession.getPinnedQuestId() == null);
        if (!isUnpinned) {
            throw new AssertionError("Test 4 FAILED: Expected pinnedQuestId to be null after unpinning");
        }
    }

    /**
     * Test 5: Delete quest.
     * - Open the quest via ViewQuestScreen
     * - Click "Delete", confirm with "Yes"
     * - Wait for server round-trip
     * - Assert cache is empty
     */
    private void test5_deleteQuest(ClientGameTestContext context) {
        // Open the quest
        context.runOnClient(client -> {
            List<Quest> quests = ClientCache.getMyQuests();
            if (!quests.isEmpty()) {
                Quest quest = quests.get(0);
                client.setScreen(new ViewQuestScreen(null, quest));
            }
        });
        context.waitForScreen(ViewQuestScreen.class);
        context.waitTicks(5);

        // Click "Delete"
        context.clickScreenButton("Delete");
        context.waitTicks(5);

        // Confirm deletion - the ConfirmScreen has "Yes" and "No" buttons
        context.clickScreenButton("Yes");
        context.waitTicks(20); // Wait for server round-trip

        // The ViewQuestScreen auto-closes when the quest is removed from cache (tick check).
        // We should end up with no screen (null) since parent was null.
        context.waitForScreen(null);
        context.waitTicks(10);

        // Verify quest was deleted
        int questCount = context.computeOnClient(client -> ClientCache.getMyQuests().size());
        if (questCount != 0) {
            throw new AssertionError("Test 5 FAILED: Expected 0 quests after deletion, got " + questCount);
        }

        context.takeScreenshot("05_quest_deleted");

        // Final disconnect
        TestHelper.disconnect(context);
    }
}
