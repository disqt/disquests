package com.disqt.disquests.test.integration.tests;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.hud.HudPinManager;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.test.integration.harness.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Pin Persistence (pin survives disconnect/reconnect)")
class PinPersistenceTest {

    static final UUID QUEST = UUID.fromString("aaaaaaaa-0005-0000-0000-000000000001");

    @Test @Order(1) @PlayerA
    @DisplayName("Create and pin a quest")
    void createAndPin(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Pin Test", "Pin me", null, false, null, null));
        waitForQuestByTitle(context, "Pin Test", true);

        context.runOnClient(c -> HudPinManager.toggle(QUEST));
        context.waitFor(c -> ClientSession.isPinned(QUEST), TIMEOUT);
        assertTrue((boolean) context.computeOnClient(c -> ClientSession.isPinned(QUEST)));

        context.waitTicks(40);
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Pin persists after disconnect and reconnect")
    void pinPersistsAfterReconnect(ClientGameTestContext context) {
        disconnect(context);
        connectAndWait(context);

        context.waitFor(c -> ClientSession.isPinned(QUEST), TIMEOUT);
        assertTrue((boolean) context.computeOnClient(c -> ClientSession.isPinned(QUEST)),
            "Pin should persist after reconnection");
    }
}
