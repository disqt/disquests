package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

public class LeaveQuestTest implements FabricClientGameTest {

    private static final UUID QUEST_ID = UUID.fromString("aaaaaaaa-0004-0000-0000-000000000001");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("LeaveQuestTest")) return;
        String phase = System.getProperty("disquests.test.phase", "1");

        connectAndWait(context);

        switch (phase) {
            case "1" -> phaseA_createOpenQuest(context);
            case "2" -> phaseB_joinQuest(context);
            case "3" -> phaseB_leaveQuest(context);
        }

        disconnect(context);
    }

    private void phaseA_createOpenQuest(ClientGameTestContext context) {
        context.runOnClient(client ->
            PacketSender.saveQuest(QUEST_ID, "Leave Test", "Try leaving", null, false, null, null));
        waitForQuestByTitle(context, "Leave Test", true);
        context.runOnClient(client -> PacketSender.updateVisibility(QUEST_ID, Visibility.OPEN));
        context.waitFor(client -> {
            var q = ClientCache.getQuestById(QUEST_ID);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);
    }

    private void phaseB_joinQuest(ClientGameTestContext context) {
        waitForQuestByTitle(context, "Leave Test", false);
        context.runOnClient(client -> PacketSender.joinQuest(QUEST_ID));
        context.waitFor(client ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST_ID)),
            TIMEOUT);
    }

    private void phaseB_leaveQuest(ClientGameTestContext context) {
        context.waitFor(client ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST_ID)),
            TIMEOUT);
        context.runOnClient(client -> PacketSender.leaveQuest(QUEST_ID));
        waitForQuestRemoved(context, QUEST_ID);
    }
}
