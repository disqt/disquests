package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;
import static com.disqt.disquests.test.integration.IntegrationPlayerA.*;

/**
 * Integration test client for Player B (discoverer/collaborator).
 * Runs all Player B phases across all journeys in a single session.
 * Coordinates with IntegrationPlayerA via PhaseSync.
 */
public class IntegrationPlayerB implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("IntegrationPlayerB")) return;

        // Wait for A to finish lifecycle before connecting (no B involvement in lifecycle)
        PhaseSync.waitFor("lifecycle-done", context);

        connectAndWait(context);

        // --- Journey 2: Discovery (B discovers and joins) ---
        journeyDiscovery_B(context);

        // --- Journey 3: Collaboration (B requests access) ---
        journeyCollaboration_B(context);

        // --- Journey 4: Leave (B joins then leaves) ---
        journeyLeave_B(context);

        // Wait for A to finish pin test
        PhaseSync.waitFor("pin-done", context);

        PhaseSync.signal("all-b-done");
        disconnect(context);
    }

    private void journeyDiscovery_B(ClientGameTestContext context) {
        PhaseSync.waitFor("discovery-a-created", context);

        // Find quest on Quest Board
        var quest = waitForQuestByTitle(context, "Join Test", false);
        if (quest == null) throw new AssertionError("Join Test not found on Quest Board");

        // Join
        context.runOnClient(c -> PacketSender.joinQuest(DISCOVERY_QUEST));
        context.waitFor(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(DISCOVERY_QUEST)),
            TIMEOUT);

        PhaseSync.signal("discovery-b-joined");
    }

    private void journeyCollaboration_B(ClientGameTestContext context) {
        PhaseSync.waitFor("collab-a-created", context);

        // Find closed quest on Quest Board
        var quest = waitForQuestByTitle(context, "Secret Base", false);
        if (quest == null) throw new AssertionError("Secret Base not found on Quest Board");

        // Request collaboration
        context.runOnClient(c -> PacketSender.requestCollaboration(COLLAB_QUEST));
        context.waitTicks(20); // allow server processing

        PhaseSync.signal("collab-b-requested");
        PhaseSync.waitFor("collab-a-accepted", context);

        // After A accepts, quest should appear in our myQuests via COLLABORATION_RESPONSE
        context.waitFor(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(COLLAB_QUEST)),
            TIMEOUT);
    }

    private void journeyLeave_B(ClientGameTestContext context) {
        PhaseSync.waitFor("leave-a-created", context);

        // Find and join
        waitForQuestByTitle(context, "Leave Test", false);
        context.runOnClient(c -> PacketSender.joinQuest(LEAVE_QUEST));
        context.waitFor(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(LEAVE_QUEST)),
            TIMEOUT);

        // Leave
        context.runOnClient(c -> PacketSender.leaveQuest(LEAVE_QUEST));
        waitForQuestRemoved(context, LEAVE_QUEST);

        PhaseSync.signal("leave-b-left");
    }
}
