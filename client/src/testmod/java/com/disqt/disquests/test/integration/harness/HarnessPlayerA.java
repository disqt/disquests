package com.disqt.disquests.test.integration.harness;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class HarnessPlayerA implements FabricClientGameTest {
  @Override
  public void runTest(ClientGameTestContext context) {
    HarnessCommon.run(context, "a");
  }
}
