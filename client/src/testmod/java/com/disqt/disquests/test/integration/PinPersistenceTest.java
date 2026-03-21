package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.network.PacketSender;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

public class PinPersistenceTest implements FabricClientGameTest {

    private static final UUID QUEST_ID = UUID.fromString("aaaaaaaa-0005-0000-0000-000000000001");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("PinPersistenceTest")) return;
        String phase = System.getProperty("disquests.test.phase", "1");

        connectAndWait(context);

        switch (phase) {
            case "1" -> phaseA_createAndPin(context);
            case "2" -> phaseA_verifyPinRestored(context);
        }

        disconnect(context);
    }

    private void phaseA_createAndPin(ClientGameTestContext context) {
        context.runOnClient(client ->
            PacketSender.saveQuest(QUEST_ID, "Pin Test", "Pin me", null, false, null, null));
        waitForQuestByTitle(context, "Pin Test", true);

        // Use HudPinManager.toggle which sets local state AND sends packet
        context.runOnClient(client ->
            com.disqt.disquests.client.hud.HudPinManager.toggle(QUEST_ID));
        context.waitTicks(10);

        context.waitFor(client -> ClientSession.isPinned(QUEST_ID), TIMEOUT);
    }

    private void phaseA_verifyPinRestored(ClientGameTestContext context) {
        context.waitFor(client -> ClientSession.isPinned(QUEST_ID), TIMEOUT);
    }
}
