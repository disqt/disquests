package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

public class QuestLifecycleTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("QuestLifecycleTest")) return;

        connectAndWait(context);

        UUID questId = UUID.randomUUID();

        // Create quest (server always creates as PRIVATE)
        context.runOnClient(client ->
            PacketSender.saveQuest(questId, "Lifecycle Test", "initial content", null, false, null, null));
        var quest = waitForQuestByTitle(context, "Lifecycle Test", true);
        if (quest == null) throw new AssertionError("Quest not created");

        // Update visibility to OPEN
        context.runOnClient(client -> PacketSender.updateVisibility(questId, Visibility.OPEN));
        context.waitFor(client -> {
            var q = ClientCache.getQuestById(questId);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);

        // Edit content
        context.runOnClient(client ->
            PacketSender.saveQuest(questId, "Lifecycle Test", "updated content", null, false, null, null));
        context.waitFor(client -> {
            var q = ClientCache.getQuestById(questId);
            return q != null && "updated content".equals(q.getContent());
        }, TIMEOUT);

        // Delete
        context.runOnClient(client -> PacketSender.deleteQuest(questId));
        waitForQuestRemoved(context, questId);

        disconnect(context);
    }
}
