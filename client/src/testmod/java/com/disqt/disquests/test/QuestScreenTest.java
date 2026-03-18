package com.disqt.disquests.test;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Client-side screen tests. No server connection needed --
 * opens screens directly with test quest data.
 */
public class QuestScreenTest implements FabricClientGameTest {

    private static final UUID TEST_PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Quest createTestQuest() {
        Quest q = new Quest();
        q.setId(UUID.randomUUID());
        q.setTitle("Test Quest");
        q.setContent("Some test content here");
        q.setVisibility(Visibility.OPEN);
        q.setOwnerUuid(TEST_PLAYER_UUID);
        q.setOwnerName("TestPlayer");
        q.setLastModified(System.currentTimeMillis() / 1000);
        q.setContributors(new ArrayList<>());
        return q;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        // Fake being on a server so screens work
        ClientSession.joinServer(null, 0, new ArrayList<>(), TEST_PLAYER_UUID);

        try {
            testHelpButtonToggle(context);
            testOwnerInfoRendersInViewMode(context);
        } finally {
            ClientSession.leaveServer();
        }
    }

    /**
     * Issue 1: Click the "?" button in edit mode and verify the formatting
     * panel appears (content field width shrinks when panel is shown).
     */
    private void testHelpButtonToggle(ClientGameTestContext context) {
        Quest quest = createTestQuest();

        context.setScreen(() -> new QuestScreen(null, quest, true));
        context.waitForScreen(QuestScreen.class);
        context.waitTick();

        // Get content field width BEFORE clicking help button
        int widthBefore = context.computeOnClient(client -> {
            QuestScreen screen = (QuestScreen) client.currentScreen;
            return screen.getContentField().width;
        });

        // Read the actual help button position from the screen
        double[] btnCenter = context.computeOnClient(client -> {
            QuestScreen screen = (QuestScreen) client.currentScreen;
            DarkButtonWidget btn = screen.getHelpButton();
            double x = btn.getX() + btn.getWidth() / 2.0;
            double y = btn.getY() + btn.getHeight() / 2.0;
            return new double[]{x, y};
        });

        // Click the help button
        TestInput input = context.getInput();
        input.setCursorPos(btnCenter[0], btnCenter[1]);
        input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);

        // Content field should be narrower now (formatting panel visible)
        int widthAfter = context.computeOnClient(client -> {
            QuestScreen screen = (QuestScreen) client.currentScreen;
            return screen.getContentField().width;
        });

        if (widthAfter >= widthBefore) {
            throw new AssertionError(
                    "Help button toggle failed: content width should shrink when formatting panel opens. " +
                    "Before=" + widthBefore + " After=" + widthAfter);
        }

        context.setScreen(() -> null);
        context.waitTick();
    }

    /**
     * Issue 2: Verify QuestScreen view mode renders without error
     * and captures a screenshot for visual verification of owner text alignment.
     */
    private void testOwnerInfoRendersInViewMode(ClientGameTestContext context) {
        Quest quest = createTestQuest();

        context.setScreen(() -> new QuestScreen(null, quest));
        context.waitForScreen(QuestScreen.class);
        context.waitTick();

        boolean isViewMode = context.computeOnClient(client ->
                client.currentScreen instanceof QuestScreen);
        if (!isViewMode) {
            throw new AssertionError("QuestScreen should be in view mode");
        }

        context.takeScreenshot("issue2-owner-alignment");

        context.setScreen(() -> null);
        context.waitTick();
    }
}
