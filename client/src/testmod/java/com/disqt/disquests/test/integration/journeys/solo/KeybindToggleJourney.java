package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

@IntegrationTest
@DisplayName("Keybind Toggle Journey")
class KeybindToggleJourney {

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("N key closes Disquests screen when no text field focused")
  void nKeyCloses(ClientGameTestContext context) {
    given("MainScreen is open");
    openMainScreen(context);
    assertScreenIs(context, MainScreen.class);

    when("player presses N");
    context.getInput().pressKey(GLFW.GLFW_KEY_N);
    context.waitTicks(5);

    then("screen closes and returns to gameplay");
    context.waitFor(client -> client.screen == null, TIMEOUT);
  }
}
