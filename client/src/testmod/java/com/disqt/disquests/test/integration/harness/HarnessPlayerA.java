package com.disqt.disquests.test.integration.harness;

import com.disqt.disquests.test.integration.PhaseSync;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.IOException;
import java.nio.file.*;

import static com.disqt.disquests.test.integration.bdd.UIActions.*;

public class HarnessPlayerA implements FabricClientGameTest {

    private static final String PLAYER_ROLE = "PlayerA";
    private static final String TESTS_PACKAGE = "com.disqt.disquests.test.integration.journeys";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("HarnessPlayerA")) return;

        TestContext.set(context, PLAYER_ROLE);
        connectAndWait(context);

        boolean harness = Boolean.getBoolean("disquests.test.harness");

        if (harness) {
            harnessLoop(context);
        } else {
            String result = runJUnitSuite(null);
            writeResult(result);
            System.exit(result.startsWith("PASS") ? 0 : 1);
        }
    }

    private void harnessLoop(ClientGameTestContext context) {
        Path syncDir = PhaseSync.getSyncDir();

        while (true) {
            try { Files.deleteIfExists(syncDir.resolve("results-a.txt")); } catch (IOException ignored) {}
            try { Files.deleteIfExists(syncDir.resolve("error-playera.done")); } catch (IOException ignored) {}

            PhaseSync.signal("client-a-ready");

            context.waitFor(client -> Files.exists(syncDir.resolve("run.signal")), Integer.MAX_VALUE);

            String filter = null;
            try {
                String content = Files.readString(syncDir.resolve("run.signal")).trim();
                if (!content.equals("*")) filter = content;
            } catch (IOException ignored) {}

            String result = runJUnitSuite(filter);
            writeResult(result);
        }
    }

    private String runJUnitSuite(String testClassFilter) {
        try {
            var requestBuilder = LauncherDiscoveryRequestBuilder.request();

            if (testClassFilter != null) {
                try {
                    Class<?> testClass = Class.forName(TESTS_PACKAGE + "." + testClassFilter);
                    requestBuilder.selectors(DiscoverySelectors.selectClass(testClass));
                } catch (ClassNotFoundException e) {
                    return "FAIL: Test class not found: " + testClassFilter;
                }
            } else {
                requestBuilder.selectors(DiscoverySelectors.selectPackage(TESTS_PACKAGE));
            }

            LauncherDiscoveryRequest request = requestBuilder.build();
            Launcher launcher = LauncherFactory.create();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            launcher.registerTestExecutionListeners(listener);
            launcher.execute(request);

            TestExecutionSummary summary = listener.getSummary();

            long totalRun = summary.getTestsSucceededCount() + summary.getTestsFailedCount()
                + summary.getTestsAbortedCount();
            if (totalRun == 0) {
                return "FAIL: No tests were found or executed (check test package/filter)";
            }

            if (summary.getTestsFailedCount() == 0 && summary.getTestsAbortedCount() == 0) {
                return "PASS: " + summary.getTestsSucceededCount() + " tests passed";
            } else {
                StringBuilder sb = new StringBuilder("FAIL: ");
                sb.append(summary.getTestsFailedCount()).append(" failed, ");
                sb.append(summary.getTestsSucceededCount()).append(" passed");
                for (TestExecutionSummary.Failure f : summary.getFailures()) {
                    sb.append("\n  - ").append(f.getTestIdentifier().getDisplayName());
                    sb.append(": ").append(f.getException().getMessage());
                }
                return sb.toString();
            }
        } catch (Exception e) {
            PhaseSync.signalError(PLAYER_ROLE, e.getMessage());
            return "FAIL: " + e.getMessage();
        }
    }

    private void writeResult(String result) {
        try {
            Path syncDir = PhaseSync.getSyncDir();
            Files.createDirectories(syncDir);
            Files.writeString(syncDir.resolve("results-a.txt"), result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write results", e);
        }
    }
}
