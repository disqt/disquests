package com.disqt.disquests.test.integration.harness;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Method;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.connectAndWait;

public class IntegrationTestExtension implements
        ExecutionCondition,
        BeforeAllCallback,
        BeforeEachCallback,
        ParameterResolver {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext ctx) {
        if (ctx.getTestMethod().isEmpty()) {
            return ConditionEvaluationResult.enabled("Class-level: always enabled");
        }

        Method method = ctx.getTestMethod().get();
        boolean hasPlayerA = method.isAnnotationPresent(PlayerA.class);
        boolean hasPlayerB = method.isAnnotationPresent(PlayerB.class);

        if (!hasPlayerA && !hasPlayerB) {
            return ConditionEvaluationResult.enabled("No player tag: runs on both");
        }

        boolean isA = TestContext.isPlayerA();
        if (hasPlayerA && isA) return ConditionEvaluationResult.enabled("PlayerA method on PlayerA client");
        if (hasPlayerB && !isA) return ConditionEvaluationResult.enabled("PlayerB method on PlayerB client");

        return ConditionEvaluationResult.disabled("Skipped: method is @" +
            (hasPlayerA ? "PlayerA" : "PlayerB") + " but this client is " + TestContext.getPlayerRole());
    }

    @Override
    public void beforeAll(ExtensionContext ctx) {
        ClientGameTestContext context = TestContext.get();

        if (!ClientSession.isOnServer()) {
            connectAndWait(context);
        }

        context.runOnClient(c -> {
            ClientCache.clear();
            com.disqt.disquests.client.network.PacketSender.requestSync();
        });
        context.waitTicks(20);
    }

    @Override
    public void beforeEach(ExtensionContext ctx) {
        if (!ClientSession.isOnServer()) {
            connectAndWait(TestContext.get());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        return paramCtx.getParameter().getType() == ClientGameTestContext.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        return TestContext.get();
    }
}
