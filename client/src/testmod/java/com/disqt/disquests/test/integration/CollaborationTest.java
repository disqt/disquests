package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

public class CollaborationTest implements FabricClientGameTest {

    private static final UUID QUEST_ID = UUID.fromString("aaaaaaaa-0003-0000-0000-000000000001");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("CollaborationTest")) return;
        String phase = System.getProperty("disquests.test.phase", "1");

        connectAndWait(context);

        switch (phase) {
            case "1" -> phaseA_createClosedQuest(context);
            case "2" -> phaseB_requestAccess(context);
            case "3" -> phaseA_acceptRequest(context);
        }

        disconnect(context);
    }

    private void phaseA_createClosedQuest(ClientGameTestContext context) {
        context.runOnClient(client ->
            PacketSender.saveQuest(QUEST_ID, "Secret Base", "Top secret", null, false, null, null));
        waitForQuestByTitle(context, "Secret Base", true);

        context.runOnClient(client -> PacketSender.updateVisibility(QUEST_ID, Visibility.CLOSED));
        context.waitFor(client -> {
            var q = ClientCache.getQuestById(QUEST_ID);
            return q != null && q.getVisibility() == Visibility.CLOSED;
        }, TIMEOUT);
    }

    private void phaseB_requestAccess(ClientGameTestContext context) {
        var quest = waitForQuestByTitle(context, "Secret Base", false);
        if (quest == null) throw new AssertionError("Secret Base not found on Quest Board");

        context.runOnClient(client -> PacketSender.requestCollaboration(QUEST_ID));
        context.waitTicks(20);
    }

    private void phaseA_acceptRequest(ClientGameTestContext context) {
        context.waitFor(client -> ClientCache.getPendingCount(QUEST_ID) > 0, TIMEOUT);

        var requests = context.computeOnClient(client ->
            ClientCache.getPendingRequestsForQuest(QUEST_ID));
        if (requests.isEmpty()) throw new AssertionError("No pending requests found");

        UUID requestId = requests.get(0).id();

        context.runOnClient(client -> PacketSender.respondCollaboration(requestId, true));
        context.waitFor(client -> ClientCache.getPendingCount(QUEST_ID) == 0, TIMEOUT);
    }
}
