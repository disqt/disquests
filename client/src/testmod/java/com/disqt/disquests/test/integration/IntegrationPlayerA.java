package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.hud.HudPinManager;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

/**
 * Integration test client for Player A (quest owner).
 * Runs all Player A phases across all journeys in a single session.
 * Coordinates with IntegrationPlayerB via PhaseSync.
 */
public class IntegrationPlayerA implements FabricClientGameTest {

    // Deterministic quest IDs shared with PlayerB
    static final UUID LIFECYCLE_QUEST = UUID.fromString("aaaaaaaa-0001-0000-0000-000000000001");
    static final UUID DISCOVERY_QUEST = UUID.fromString("aaaaaaaa-0002-0000-0000-000000000001");
    static final UUID COLLAB_QUEST = UUID.fromString("aaaaaaaa-0003-0000-0000-000000000001");
    static final UUID LEAVE_QUEST = UUID.fromString("aaaaaaaa-0004-0000-0000-000000000001");
    static final UUID PIN_QUEST = UUID.fromString("aaaaaaaa-0005-0000-0000-000000000001");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("IntegrationPlayerA")) return;

        connectAndWait(context);

        // --- Journey 1: Quest Lifecycle (A only) ---
        journeyLifecycle(context);

        // --- Journey 2: Discovery (A creates, B joins) ---
        journeyDiscovery_A(context);

        // --- Journey 3: Collaboration (A creates, B requests, A accepts) ---
        journeyCollaboration_A(context);

        // --- Journey 4: Leave (A creates, B joins+leaves) ---
        journeyLeave_A(context);

        // --- Journey 5: Pin Persistence (A only, reconnect) ---
        journeyPin_A(context);

        PhaseSync.signal("all-a-done");
        disconnect(context);
    }

    private void journeyLifecycle(ClientGameTestContext context) {
        // Create quest
        context.runOnClient(c -> PacketSender.saveQuest(LIFECYCLE_QUEST, "Lifecycle Test", "initial", null, false, null, null));
        waitForQuestByTitle(context, "Lifecycle Test", true);

        // Update visibility
        context.runOnClient(c -> PacketSender.updateVisibility(LIFECYCLE_QUEST, Visibility.OPEN));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(LIFECYCLE_QUEST);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);

        // Edit content
        context.runOnClient(c -> PacketSender.saveQuest(LIFECYCLE_QUEST, "Lifecycle Test", "updated", null, false, null, null));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(LIFECYCLE_QUEST);
            return q != null && "updated".equals(q.getContent());
        }, TIMEOUT);

        // Delete
        context.runOnClient(c -> PacketSender.deleteQuest(LIFECYCLE_QUEST));
        waitForQuestRemoved(context, LIFECYCLE_QUEST);

        PhaseSync.signal("lifecycle-done");
    }

    private void journeyDiscovery_A(ClientGameTestContext context) {
        // Create OPEN quest for B to discover
        context.runOnClient(c -> PacketSender.saveQuest(DISCOVERY_QUEST, "Join Test", "Come join!", null, false, null, null));
        waitForQuestByTitle(context, "Join Test", true);
        context.runOnClient(c -> PacketSender.updateVisibility(DISCOVERY_QUEST, Visibility.OPEN));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(DISCOVERY_QUEST);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);

        PhaseSync.signal("discovery-a-created");
        PhaseSync.waitFor("discovery-b-joined", context);
    }

    private void journeyCollaboration_A(ClientGameTestContext context) {
        // Create CLOSED quest
        context.runOnClient(c -> PacketSender.saveQuest(COLLAB_QUEST, "Secret Base", "Top secret", null, false, null, null));
        waitForQuestByTitle(context, "Secret Base", true);
        context.runOnClient(c -> PacketSender.updateVisibility(COLLAB_QUEST, Visibility.CLOSED));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(COLLAB_QUEST);
            return q != null && q.getVisibility() == Visibility.CLOSED;
        }, TIMEOUT);

        PhaseSync.signal("collab-a-created");
        PhaseSync.waitFor("collab-b-requested", context);

        // Wait for B's collaboration request to arrive via COLLABORATION_REQUEST S2C
        context.waitFor(c ->
            !ClientCache.getPendingRequestsForQuest(COLLAB_QUEST).isEmpty(), TIMEOUT);

        var requests = context.computeOnClient(c -> ClientCache.getPendingRequestsForQuest(COLLAB_QUEST));
        if (requests.isEmpty()) throw new AssertionError("No pending requests for Secret Base");

        UUID requestId = requests.get(0).id();
        context.runOnClient(c -> PacketSender.respondCollaboration(requestId, true));
        // Wait for request to be processed (removed from pending list)
        context.waitFor(c ->
            ClientCache.getPendingRequestsForQuest(COLLAB_QUEST).isEmpty(), TIMEOUT);

        PhaseSync.signal("collab-a-accepted");
    }

    private void journeyLeave_A(ClientGameTestContext context) {
        // Create OPEN quest for B to join then leave
        context.runOnClient(c -> PacketSender.saveQuest(LEAVE_QUEST, "Leave Test", "Try leaving", null, false, null, null));
        waitForQuestByTitle(context, "Leave Test", true);
        context.runOnClient(c -> PacketSender.updateVisibility(LEAVE_QUEST, Visibility.OPEN));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(LEAVE_QUEST);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);

        PhaseSync.signal("leave-a-created");
        PhaseSync.waitFor("leave-b-left", context);
    }

    private void journeyPin_A(ClientGameTestContext context) {
        // Create and pin
        context.runOnClient(c -> PacketSender.saveQuest(PIN_QUEST, "Pin Test", "Pin me", null, false, null, null));
        waitForQuestByTitle(context, "Pin Test", true);
        context.runOnClient(c -> HudPinManager.toggle(PIN_QUEST));
        context.waitFor(c -> ClientSession.isPinned(PIN_QUEST), TIMEOUT);

        // Disconnect and reconnect to verify pin persists
        disconnect(context);
        connectAndWait(context);
        context.waitFor(c -> ClientSession.isPinned(PIN_QUEST), TIMEOUT);

        PhaseSync.signal("pin-done");
    }
}
