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
@DisplayName("Quest Discovery (A creates OPEN, B discovers and joins)")
class DiscoveryTest {

    static final UUID QUEST = UUID.fromString("aaaaaaaa-0002-0000-0000-000000000001");

    @Test @Order(1) @PlayerA
    @DisplayName("A creates OPEN quest")
    void createsOpenQuest(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Join Test", "Come join!", null, false, null, null));
        waitForQuestByTitle(context, "Join Test", true);
        context.runOnClient(c -> PacketSender.updateVisibility(QUEST, Visibility.OPEN));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(QUEST);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);

        PhaseSync.signal("discovery-a-created");
        PhaseSync.waitFor("discovery-b-joined", context);
    }

    @Test @Order(1) @PlayerB
    @DisplayName("B discovers and joins the quest")
    void discoversAndJoins(ClientGameTestContext context) {
        PhaseSync.waitFor("discovery-a-created", context);

        var quest = waitForQuestByTitle(context, "Join Test", false);
        assertNotNull(quest, "Join Test should appear on Quest Board");

        context.runOnClient(c -> PacketSender.joinQuest(QUEST));
        context.waitFor(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST)),
            TIMEOUT);

        var joined = context.computeOnClient(c ->
            ClientCache.getMyQuests().stream().filter(q -> q.getId().equals(QUEST)).findFirst().orElse(null));
        assertNotNull(joined, "Quest should appear in My Quests after joining");

        PhaseSync.signal("discovery-b-joined");
    }
}
