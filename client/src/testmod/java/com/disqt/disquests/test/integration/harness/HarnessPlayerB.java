package com.disqt.disquests.test.integration.harness;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class HarnessPlayerB implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        HarnessCommon.run(context, "b");
    }
}
