package com.disqt.disquests.test.integration.tests;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import com.disqt.disquests.test.integration.harness.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Quest Lifecycle (create, edit, visibility, delete)")
class LifecycleTest {

    static final UUID QUEST = UUID.fromString("aaaaaaaa-0001-0000-0000-000000000001");

    @Test @Order(1) @PlayerA
    @DisplayName("Create a new quest")
    void createQuest(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Lifecycle Test", "initial", null, false, null, null));
        var quest = waitForQuestByTitle(context, "Lifecycle Test", true);
        assertNotNull(quest, "Quest should appear in My Quests after creation");
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Update visibility to OPEN")
    void updateVisibility(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.updateVisibility(QUEST, Visibility.OPEN));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(QUEST);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);
        var quest = context.computeOnClient(c -> ClientCache.getQuestById(QUEST));
        assertEquals(Visibility.OPEN, quest.getVisibility());
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Edit quest content")
    void editContent(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Lifecycle Test", "updated", null, false, null, null));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(QUEST);
            return q != null && "updated".equals(q.getContent());
        }, TIMEOUT);
        var quest = context.computeOnClient(c -> ClientCache.getQuestById(QUEST));
        assertEquals("updated", quest.getContent());
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Delete the quest")
    void deleteQuest(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.deleteQuest(QUEST));
        waitForQuestRemoved(context, QUEST);
        var quest = context.computeOnClient(c -> ClientCache.getQuestById(QUEST));
        assertNull(quest, "Quest should be removed after deletion");
    }
}
