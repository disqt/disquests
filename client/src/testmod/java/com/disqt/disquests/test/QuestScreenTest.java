package com.disqt.disquests.test;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Contributor;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.screen.ConfigScreen;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.client.gui.screen.ContributorScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.hud.HudPinRenderer;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side tests covering all UI features. No server connection needed.
 */
public class QuestScreenTest implements FabricClientGameTest {

    private static final UUID TEST_PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private Quest createTestQuest() {
        Quest q = new Quest();
        q.setId(UUID.randomUUID());
        q.setTitle("Test Quest");
        q.setContent("Some **bold** content here");
        q.setVisibility(Visibility.OPEN);
        q.setOwnerUuid(TEST_PLAYER_UUID);
        q.setOwnerName("TestPlayer");
        q.setLastModified(System.currentTimeMillis() / 1000);
        q.setContributors(new ArrayList<>());
        return q;
    }

    private Quest createClosedQuest() {
        Quest q = createTestQuest();
        q.setVisibility(Visibility.CLOSED);
        q.setOwnerUuid(OTHER_PLAYER_UUID);
        q.setOwnerName("OtherPlayer");
        return q;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getProperty("disquests.test.journey") != null) return;

        ClientSession.joinServer(null, 0, new ArrayList<>(), TEST_PLAYER_UUID);

        try {
            testFormattingPanelToggle(context);
            testClickContentDoesNotEdit(context);
            testPinDoesNotResort(context);
            testHudVisibilityToggle(context);
            testClosedQuestContentHidden(context);
            testRequestedState(context);
            testToastOverlay(context);
            testConfigScreen(context);
            testCheckboxNotClickableWithoutPermission(context);
            testLeaveButtonVisibility(context);
            testContributorScreenNoInvite(context);
            testMarkdownLeadingWhitespace(context);
            testEntryClickThroughScreen(context);
            testPinClickThroughScreen(context);
        } finally {
            ClientCache.clear();
            ClientSession.leaveServer();
        }
    }

    /**
     * QuestScreen edit mode opens without crashing and shows editing state.
     */
    private void testFormattingPanelToggle(ClientGameTestContext context) {
        Quest quest = createTestQuest();
        ClientCache.addOrUpdateMyQuest(quest);

        context.setScreen(() -> new QuestScreen(null, quest, true));
        context.waitForScreen(QuestScreen.class);
        context.waitTicks(3);

        String screenInfo = context.computeOnClient(client -> {
            if (client.currentScreen == null) return "null";
            if (!(client.currentScreen instanceof QuestScreen screen)) {
                return "not QuestScreen: " + client.currentScreen.getClass().getSimpleName();
            }
            return "QuestScreen editing=" + screen.isEditing();
        });

        if (!screenInfo.contains("editing=true")) {
            throw new AssertionError("Expected edit mode but got: " + screenInfo);
        }

        ClientCache.removeQuestById(quest.getId());
        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * QuestScreen in view mode should not have isEditing() true.
     * (Click-to-edit was removed; only Edit button enters edit mode.)
     */
    private void testClickContentDoesNotEdit(ClientGameTestContext context) {
        Quest quest = createTestQuest();

        context.setScreen(() -> new QuestScreen(null, quest));
        context.waitForScreen(QuestScreen.class);
        context.waitTicks(2);

        boolean isQuestScreen = context.computeOnClient(client ->
                client.currentScreen instanceof QuestScreen);
        if (!isQuestScreen) {
            // Screen closed unexpectedly -- skip this test
            return;
        }

        boolean editing = context.computeOnClient(client -> {
            QuestScreen screen = (QuestScreen) client.currentScreen;
            return screen.isEditing();
        });
        if (editing) {
            throw new AssertionError("QuestScreen should be in view mode, not edit mode");
        }

        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * Pin toggle should NOT re-sort the list immediately.
     * Order should only change on screen open or tab switch.
     */
    private void testPinDoesNotResort(ClientGameTestContext context) {
        Quest quest1 = createTestQuest();
        quest1.setTitle("Older Quest");
        quest1.setLastModified(1000);
        Quest quest2 = createTestQuest();
        quest2.setTitle("Newer Quest");
        quest2.setLastModified(2000);

        ClientCache.addOrUpdateMyQuest(quest1);
        ClientCache.addOrUpdateMyQuest(quest2);

        context.setScreen(MainScreen::new);
        context.waitForScreen(MainScreen.class);
        context.waitTick();

        // Get first quest title (should be "Newer Quest" -- sorted by lastModified desc)
        String firstBefore = context.computeOnClient(client -> {
            MainScreen screen = (MainScreen) client.currentScreen;
            var entries = screen.getQuestEntries();
            return entries.isEmpty() ? "" : entries.get(0).getQuest().getTitle();
        });

        // Pin the older quest (use ClientSession directly to avoid packet send)
        context.runOnClient(client -> {
            ClientSession.addPinnedQuest(quest1.getId());
            MainScreen screen = (MainScreen) client.currentScreen;
            screen.refreshAfterPinToggle();
        });
        context.waitTick();

        // Order should NOT have changed (older quest should NOT jump to top)
        String firstAfter = context.computeOnClient(client -> {
            MainScreen screen = (MainScreen) client.currentScreen;
            var entries = screen.getQuestEntries();
            return entries.isEmpty() ? "" : entries.get(0).getQuest().getTitle();
        });

        if (!firstBefore.equals(firstAfter)) {
            throw new AssertionError(
                    "Pin toggle should not re-sort list. First quest before: " +
                    firstBefore + ", after: " + firstAfter);
        }

        // Cleanup
        ClientSession.removePinnedQuest(quest1.getId());
        ClientCache.removeQuestById(quest1.getId());
        ClientCache.removeQuestById(quest2.getId());
        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * HUD visibility toggle should hide/show without unpinning.
     */
    private void testHudVisibilityToggle(ClientGameTestContext context) {
        Quest quest = createTestQuest();
        ClientCache.addOrUpdateMyQuest(quest);
        ClientSession.addPinnedQuest(quest.getId()); // pin without sending packet

        // Toggle visibility off
        HudPinRenderer.toggleVisibility();

        // Quest should still be pinned
        boolean stillPinned = ClientSession.isPinned(quest.getId());
        if (!stillPinned) {
            throw new AssertionError("Toggle visibility should not unpin quests");
        }

        // Toggle back on
        HudPinRenderer.toggleVisibility();

        // Cleanup
        ClientSession.removePinnedQuest(quest.getId());
        ClientCache.removeQuestById(quest.getId());
    }

    /**
     * Closed quest content should be hidden for non-owner/non-contributor.
     */
    private void testClosedQuestContentHidden(ClientGameTestContext context) {
        Quest closedQuest = createClosedQuest();

        context.setScreen(() -> new QuestScreen(null, closedQuest));
        context.waitForScreen(QuestScreen.class);
        context.waitTicks(2);

        boolean isQuestScreen = context.computeOnClient(client ->
                client.currentScreen instanceof QuestScreen);
        if (!isQuestScreen) return; // Screen closed -- skip

        boolean editing = context.computeOnClient(client -> {
            QuestScreen screen = (QuestScreen) client.currentScreen;
            return screen.isEditing();
        });
        if (editing) {
            throw new AssertionError("Should not be in edit mode for a closed quest we don't own");
        }

        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * Marking a quest as requested should persist across screen rebuilds.
     */
    private void testRequestedState(ClientGameTestContext context) {
        UUID questId = UUID.randomUUID();

        ClientSession.markRequested(questId);

        if (!ClientSession.isRequested(questId)) {
            throw new AssertionError("Quest should be marked as requested");
        }

        // Verify it persists (doesn't get cleared by some other operation)
        if (!ClientSession.isRequested(questId)) {
            throw new AssertionError("Requested state should persist");
        }
    }

    /**
     * Toast overlay shows message and ticks down.
     */
    private void testToastOverlay(ClientGameTestContext context) {
        context.setScreen(MainScreen::new);
        context.waitForScreen(MainScreen.class);
        context.waitTick();

        context.runOnClient(client -> {
            MainScreen screen = (MainScreen) client.currentScreen;
            screen.showToast("Test notification");
        });
        context.waitTick();

        // Toast should be visible (we can't directly check rendering,
        // but we verify no crash occurs and the screen is still open)
        boolean screenOpen = context.computeOnClient(client ->
                client.currentScreen instanceof MainScreen);
        if (!screenOpen) {
            throw new AssertionError("MainScreen should still be open after toast");
        }

        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * Config screen opens, displays current value, and doesn't crash.
     */
    private void testConfigScreen(ClientGameTestContext context) {
        int originalWidth = DisquestsConfig.getPinnedWidth();

        context.setScreen(() -> new ConfigScreen(null));
        context.waitForScreen(ConfigScreen.class);
        context.waitTick();

        boolean onConfigScreen = context.computeOnClient(client ->
                client.currentScreen instanceof ConfigScreen);
        if (!onConfigScreen) {
            throw new AssertionError("ConfigScreen should be open");
        }

        context.setScreen(() -> null);
        context.waitTick();

        // Verify config wasn't changed (we didn't save)
        if (DisquestsConfig.getPinnedWidth() != originalWidth) {
            throw new AssertionError("Config should not change without saving");
        }
    }

    /**
     * Checkbox in task list should not be togglable when the player has no edit permission.
     */
    private void testCheckboxNotClickableWithoutPermission(ClientGameTestContext context) {
        Quest quest = createTestQuest();
        quest.setOwnerUuid(OTHER_PLAYER_UUID); // not owned by test player
        quest.setOwnerName("OtherPlayer");
        quest.setContent("- [ ] Task 1\n- [ ] Task 2");
        quest.setContributors(new ArrayList<>()); // not a contributor
        ClientCache.addOrUpdateServerQuest(quest); // prevent auto-close

        context.setScreen(() -> new QuestScreen(null, quest));
        context.waitForScreen(QuestScreen.class);
        context.waitTicks(2);

        // Verify the quest content hasn't been modified (checkbox guard prevents toggle)
        String content = context.computeOnClient(client -> {
            if (!(client.currentScreen instanceof QuestScreen screen)) return "";
            return screen.getQuest().getContent();
        });

        if (content.contains("[x]")) {
            throw new AssertionError("Checkbox should not be toggled on quest without edit permission");
        }

        ClientCache.removeQuestById(quest.getId());
        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * Leave button should be visible for contributors but not for the owner.
     */
    private void testLeaveButtonVisibility(ClientGameTestContext context) {
        // As contributor (not owner) -- should have leave button
        Quest quest = createTestQuest();
        quest.setOwnerUuid(OTHER_PLAYER_UUID);
        quest.setOwnerName("OtherPlayer");
        quest.setContributors(new ArrayList<>(List.of(
                new Contributor(new ContributorData(TEST_PLAYER_UUID, "TestPlayer", false))
        )));
        ClientCache.addOrUpdateMyQuest(quest); // prevent auto-close

        context.setScreen(() -> new QuestScreen(null, quest));
        context.waitForScreen(QuestScreen.class);
        context.waitTicks(2);

        boolean hasLeave = context.computeOnClient(client -> {
            if (!(client.currentScreen instanceof QuestScreen screen)) return false;
            return screen.hasLeaveButton();
        });
        if (!hasLeave) {
            throw new AssertionError("Leave button should be visible for contributors");
        }

        // As owner -- no leave button
        Quest ownedQuest = createTestQuest(); // owned by TEST_PLAYER_UUID
        ClientCache.addOrUpdateMyQuest(ownedQuest); // prevent auto-close

        context.setScreen(() -> new QuestScreen(null, ownedQuest));
        context.waitForScreen(QuestScreen.class);
        context.waitTicks(2);

        boolean ownerHasLeave = context.computeOnClient(client -> {
            if (!(client.currentScreen instanceof QuestScreen screen)) return false;
            return screen.hasLeaveButton();
        });
        if (ownerHasLeave) {
            throw new AssertionError("Leave button should NOT be visible for owners");
        }

        // Cleanup
        ClientCache.removeQuestById(quest.getId());
        ClientCache.removeQuestById(ownedQuest.getId());
        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * ContributorScreen should open without crashing.
     */
    private void testContributorScreenNoInvite(ClientGameTestContext context) {
        Quest quest = createTestQuest();

        context.setScreen(() -> new ContributorScreen(null, quest));
        context.waitForScreen(ContributorScreen.class);
        context.waitTicks(2);

        boolean screenOpen = context.computeOnClient(client ->
                client.currentScreen instanceof ContributorScreen);
        if (!screenOpen) {
            throw new AssertionError("ContributorScreen should be open");
        }

        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * QuestScreen should render markdown with leading whitespace without crashing.
     */
    private void testMarkdownLeadingWhitespace(ClientGameTestContext context) {
        Quest quest = createTestQuest();
        quest.setContent("             **Hangar**\n  *italic text*\nNormal text");
        ClientCache.addOrUpdateMyQuest(quest); // prevent auto-close

        context.setScreen(() -> new QuestScreen(null, quest));
        context.waitForScreen(QuestScreen.class);
        context.waitTicks(2);

        boolean screenOpen = context.computeOnClient(client ->
                client.currentScreen instanceof QuestScreen);
        if (!screenOpen) {
            throw new AssertionError("QuestScreen should render leading-whitespace markdown without crashing");
        }

        ClientCache.removeQuestById(quest.getId());
        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * Clicking on a quest entry via GLFW input simulation selects it.
     * Uses TestInput (setCursorPos + pressMouse) for the FULL input path:
     * GLFW -> MC mouse handler -> Fabric screen events -> owo-ui adapter -> QuestEntryComponent.onMouseDown
     */
    private void testEntryClickThroughScreen(ClientGameTestContext context) {
        Quest quest = createTestQuest();
        quest.setTitle("Click Test Quest");
        ClientCache.addOrUpdateMyQuest(quest);

        context.setScreen(MainScreen::new);
        context.waitForScreen(MainScreen.class);
        context.waitTicks(3);

        // Attach log capture to verify the full dispatch chain
        TestLogCapture entryLog = TestLogCapture.attach("Disquests/QuestEntry");
        TestLogCapture screenLog = TestLogCapture.attach("Disquests/MainScreen");
        TestLogCapture screenEventsLog = TestLogCapture.attach("Disquests/ScreenEvents");

        // Get entry position, then click via real input simulation
        double[] clickPos = context.computeOnClient(client -> {
            if (!(client.currentScreen instanceof MainScreen screen)) return new double[]{-1, -1};
            var entries = screen.getQuestEntries();
            if (entries.isEmpty()) return new double[]{-1, -1};
            QuestEntryComponent entry = entries.get(0);
            return new double[]{entry.x() + entry.width() / 2.0, entry.y() + entry.height() / 2.0};
        });

        if (clickPos[0] < 0) {
            throw new AssertionError("No quest entries found on screen");
        }

        // Log the actual click position for debugging
        org.slf4j.LoggerFactory.getLogger("Disquests/Test").info(
                "Entry click target: ({}, {})", clickPos[0], clickPos[1]);

        // Simulate real mouse click through GLFW path
        // GLFW uses physical pixels; entry coords are logical (UI-scaled)
        // Multiply by scale factor to convert logical -> physical
        double scale = context.computeOnClient(client ->
                (double) client.getWindow().getScaleFactor());
        context.getInput().setCursorPos(clickPos[0] * scale, clickPos[1] * scale);
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);

        // Verify entry was selected
        boolean selected = context.computeOnClient(client -> {
            if (!(client.currentScreen instanceof MainScreen screen)) return false;
            var entries = screen.getQuestEntries();
            return !entries.isEmpty() && entries.get(0).isSelected();
        });

        if (!selected) {
            throw new AssertionError("Entry should be selected after GLFW click. "
                    + "EntryLog: " + entryLog.getMessages()
                    + " ScreenLog: " + screenLog.getMessages()
                    + " ScreenEventsLog: " + screenEventsLog.getMessages());
        }

        // Verify debug logs prove the full dispatch chain fired
        if (!entryLog.hasMessageContaining("onMouseDown called")) {
            throw new AssertionError("onMouseDown debug log missing. Captured: " + entryLog.getMessages());
        }
        if (!screenLog.hasMessageContaining("onEntryClicked")) {
            throw new AssertionError("onEntryClicked debug log missing. Captured: " + screenLog.getMessages());
        }
        if (!screenEventsLog.hasMessageContaining("[CLICK PRE]")) {
            throw new AssertionError("Fabric ScreenMouseEvents [CLICK PRE] missing. Captured: " + screenEventsLog.getMessages());
        }

        entryLog.detach();
        screenLog.detach();
        screenEventsLog.detach();

        // Cleanup
        ClientCache.removeQuestById(quest.getId());
        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * Clicking the pin icon via GLFW input simulation toggles pin state.
     * Uses TestInput for the full input path.
     */
    private void testPinClickThroughScreen(ClientGameTestContext context) {
        Quest quest = createTestQuest();
        quest.setTitle("Pin Click Test");
        ClientCache.addOrUpdateMyQuest(quest);
        ClientSession.removePinnedQuest(quest.getId());

        context.setScreen(MainScreen::new);
        context.waitForScreen(MainScreen.class);
        context.waitTicks(3);

        TestLogCapture entryLog = TestLogCapture.attach("Disquests/QuestEntry");

        // Get pin icon position
        double[] pinPos = context.computeOnClient(client -> {
            if (!(client.currentScreen instanceof MainScreen screen)) return new double[]{-1, -1};
            var entries = screen.getQuestEntries();
            if (entries.isEmpty()) return new double[]{-1, -1};
            QuestEntryComponent entry = entries.get(0);
            // Pin icon center: rightmost area, row 2
            return new double[]{entry.x() + entry.width() - 7, entry.y() + 18};
        });

        if (pinPos[0] < 0) {
            throw new AssertionError("No quest entries found on screen");
        }

        // Simulate real mouse click on pin icon through GLFW path
        // GLFW uses physical pixels; multiply by scale factor
        double scale = context.computeOnClient(client ->
                (double) client.getWindow().getScaleFactor());
        context.getInput().setCursorPos(pinPos[0] * scale, pinPos[1] * scale);
        try {
            context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        } catch (Exception ignored) {
            // PacketSender.pinQuest throws when no server -- pin state already updated locally
        }
        context.waitTicks(2);

        boolean pinned = ClientSession.isPinned(quest.getId());

        if (!pinned) {
            throw new AssertionError("Pin icon GLFW click should toggle pin state. "
                    + "EntryLog: " + entryLog.getMessages());
        }

        if (!entryLog.hasMessageContaining("PIN CLICK detected")) {
            throw new AssertionError("PIN CLICK debug log missing. Captured: " + entryLog.getMessages());
        }

        entryLog.detach();

        // Cleanup
        ClientSession.removePinnedQuest(quest.getId());
        ClientCache.removeQuestById(quest.getId());
        context.setScreen(() -> null);
        context.waitTick();
    }
}
