package com.disqt.disquests.test.integration.tests;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.harness.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Collaboration (A creates CLOSED, B requests, A accepts)")
class CollaborationTest {

    static final UUID QUEST = UUID.fromString("aaaaaaaa-0003-0000-0000-000000000001");

    @Test @Order(1) @PlayerA
    @DisplayName("A creates CLOSED quest and accepts B's request")
    void createsAndAccepts(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Secret Base", "Top secret", null, false, null, null));
        waitForQuestByTitle(context, "Secret Base", true);
        context.runOnClient(c -> PacketSender.updateVisibility(QUEST, Visibility.CLOSED));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(QUEST);
            return q != null && q.getVisibility() == Visibility.CLOSED;
        }, TIMEOUT);

        PhaseSync.signal("collab-a-created");
        PhaseSync.waitFor("collab-b-requested", context);

        context.waitFor(c ->
            !ClientCache.getPendingRequestsForQuest(QUEST).isEmpty(), TIMEOUT);

        var requests = context.computeOnClient(c -> ClientCache.getPendingRequestsForQuest(QUEST));
        assertFalse(requests.isEmpty(), "Should have pending collaboration requests");

        UUID requestId = requests.get(0).id();
        context.runOnClient(c -> {
            PacketSender.respondCollaboration(requestId, true);
            ClientCache.removePendingRequest(QUEST, requestId);
        });

        context.waitFor(c ->
            ClientCache.getPendingRequestsForQuest(QUEST).isEmpty(), TIMEOUT);

        PhaseSync.signal("collab-a-accepted");
    }

    @Test @Order(1) @PlayerB
    @DisplayName("B requests collaboration and gets accepted")
    void requestsAndJoins(ClientGameTestContext context) {
        PhaseSync.waitFor("collab-a-created", context);

        var quest = waitForQuestByTitle(context, "Secret Base", false);
        assertNotNull(quest, "Secret Base should appear on Quest Board");

        context.runOnClient(c -> PacketSender.requestCollaboration(QUEST));
        context.waitTicks(20);

        PhaseSync.signal("collab-b-requested");
        PhaseSync.waitFor("collab-a-accepted", context);

        context.waitFor(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST)),
            TIMEOUT);

        var joined = context.computeOnClient(c ->
            ClientCache.getMyQuests().stream().filter(q -> q.getId().equals(QUEST)).findFirst().orElse(null));
        assertNotNull(joined, "Quest should appear in My Quests after collaboration accepted");
    }
}
