package com.disqt.disquests.test.integration.harness;

import com.disqt.disquests.test.integration.PhaseSync;
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

public class HarnessCommon {

    private static final String TESTS_PACKAGE = System.getProperty(
        "disquests.test.package", "com.disqt.disquests.test.integration.journeys");

    /**
     * Entry point for both harness players.
     *
     * @param context the game test context
     * @param role    "a" or "b"
     */
    public static void run(ClientGameTestContext context, String role) {
        String playerRole = "Player" + role.toUpperCase();
        String harnessName = "HarnessPlayer" + role.toUpperCase();
        String otherRole = role.equals("a") ? "b" : "a";

        if (shouldSkip(harnessName)) return;

        TestContext.set(context, playerRole);
        connectAndWait(context);

        boolean harness = Boolean.getBoolean("disquests.test.harness");

        if (harness) {
            harnessLoop(context, role);
        } else {
            String result = runJUnitSuite(null, playerRole);
            writeResult(result, role);
            // Wait for the other player to finish before exiting (prevents disconnecting mid-test)
            PhaseSync.signal("player-" + role + "-done");
            try {
                context.waitFor(client -> Files.exists(PhaseSync.getSyncDir().resolve("player-" + otherRole + "-done.done")), seconds(15));
            } catch (Exception ignored) {}
            System.exit(result.startsWith("PASS") ? 0 : 1);
        }
    }

    private static void harnessLoop(ClientGameTestContext context, String role) {
        Path syncDir = PhaseSync.getSyncDir();

        while (true) {
            try { Files.deleteIfExists(syncDir.resolve("results-" + role + ".txt")); } catch (IOException ignored) {}
            try { Files.deleteIfExists(syncDir.resolve("error-player" + role + ".done")); } catch (IOException ignored) {}

            PhaseSync.signal("client-" + role + "-ready");

            context.waitFor(client -> Files.exists(syncDir.resolve("run.signal")), Integer.MAX_VALUE);

            String filter = null;
            try {
                String content = Files.readString(syncDir.resolve("run.signal")).trim();
                if (!content.equals("*")) filter = content;
            } catch (IOException ignored) {}

            String playerRole = "Player" + role.toUpperCase();
            String result = runJUnitSuite(filter, playerRole);
            writeResult(result, role);
        }
    }

    private static String runJUnitSuite(String testClassFilter, String playerRole) {
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
                    java.io.StringWriter sw = new java.io.StringWriter();
                    f.getException().printStackTrace(new java.io.PrintWriter(sw));
                    sb.append("\n").append(sw);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            PhaseSync.signalError(playerRole, e.getMessage());
            return "FAIL: " + e.getMessage();
        }
    }

    private static void writeResult(String result, String role) {
        try {
            Path syncDir = PhaseSync.getSyncDir();
            Files.createDirectories(syncDir);
            Files.writeString(syncDir.resolve("results-" + role + ".txt"), result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write results", e);
        }
    }
}
