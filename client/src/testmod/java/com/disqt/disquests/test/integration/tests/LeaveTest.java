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
@DisplayName("Leave Quest (A creates OPEN, B joins and leaves)")
class LeaveTest {

    static final UUID QUEST = UUID.fromString("aaaaaaaa-0004-0000-0000-000000000001");

    @Test @Order(1) @PlayerA
    @DisplayName("A creates OPEN quest for B to join and leave")
    void createsOpenQuest(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Leave Test", "Try leaving", null, false, null, null));
        waitForQuestByTitle(context, "Leave Test", true);
        context.runOnClient(c -> PacketSender.updateVisibility(QUEST, Visibility.OPEN));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(QUEST);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);

        PhaseSync.signal("leave-a-created");
        PhaseSync.waitFor("leave-b-left", context);
    }

    @Test @Order(1) @PlayerB
    @DisplayName("B joins then leaves the quest")
    void joinsAndLeaves(ClientGameTestContext context) {
        PhaseSync.waitFor("leave-a-created", context);

        waitForQuestByTitle(context, "Leave Test", false);

        context.runOnClient(c -> PacketSender.joinQuest(QUEST));
        context.waitFor(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST)),
            TIMEOUT);

        context.runOnClient(c -> PacketSender.leaveQuest(QUEST));
        waitForQuestRemoved(context, QUEST);

        // Verify quest is no longer in myQuests (it remains in serverQuests since it's OPEN)
        var inMyQuests = context.computeOnClient(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST)));
        assertFalse((boolean) inMyQuests, "Quest should be removed from My Quests after leaving");

        PhaseSync.signal("leave-b-left");
    }
}
