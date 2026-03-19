package com.disqt.disquests.test;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

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
        } finally {
            ClientSession.leaveServer();
        }
    }

    /**
     * Formatting panel is open by default. Toggling it off should expand the
     * content field width (panel no longer taking space).
     */
    private void testHelpButtonToggle(ClientGameTestContext context) {
        Quest quest = createTestQuest();

        context.setScreen(() -> new QuestScreen(null, quest, true));
        context.waitForScreen(QuestScreen.class);
        context.waitTick();

        // Panel is open by default -- content field is narrower
        int widthWithPanel = context.computeOnClient(client -> {
            QuestScreen screen = (QuestScreen) client.currentScreen;
            return screen.getContentField().width;
        });

        // Toggle OFF -- content field should expand
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
}
