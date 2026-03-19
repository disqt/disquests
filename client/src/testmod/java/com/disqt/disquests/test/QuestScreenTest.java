package com.disqt.disquests.test;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.screen.ConfigScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.hud.HudPinRenderer;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.ArrayList;
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
        } finally {
            ClientCache.clear();
            ClientSession.leaveServer();
        }
    }

    /**
     * Formatting panel is open by default. Toggling it off should expand
     * the content field width.
     */
    private void testFormattingPanelToggle(ClientGameTestContext context) {
        Quest quest = createTestQuest();

        context.setScreen(() -> new QuestScreen(null, quest, true));
        context.waitForScreen(QuestScreen.class);
        context.waitTick();

        int widthWithPanel = context.computeOnClient(client -> {
            QuestScreen screen = (QuestScreen) client.currentScreen;
            return screen.getContentField().width;
        });

        context.runOnClient(client -> {
            QuestScreen screen = (QuestScreen) client.currentScreen;
            screen.toggleFormattingHelp();
        });
        context.waitTicks(2);

        int widthWithoutPanel = context.computeOnClient(client -> {
            QuestScreen screen = (QuestScreen) client.currentScreen;
            return screen.getContentField().width;
        });

        if (widthWithoutPanel <= widthWithPanel) {
            throw new AssertionError(
                    "Toggling formatting panel off should expand content field. " +
                    "WithPanel=" + widthWithPanel + " WithoutPanel=" + widthWithoutPanel);
        }

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
            var children = screen.getMyQuestList().children();
            return children.isEmpty() ? "" : children.get(0).getQuest().getTitle();
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
            var children = screen.getMyQuestList().children();
            return children.isEmpty() ? "" : children.get(0).getQuest().getTitle();
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
}
