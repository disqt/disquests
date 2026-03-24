# E2E Test Code Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce duplication and improve readability across the E2E test infrastructure without changing behavior.

**Architecture:** Extract shared patterns into helpers (`seconds()`, `findComponent()`, `waitForQuestCondition()`, `HarnessCommon`). Replace inline boilerplate with helper calls. Name magic numbers.

**Tech Stack:** Java 21, JUnit 5, Fabric Client GameTest API, owo-ui

**Spec:** `docs/superpowers/specs/2026-03-23-e2e-test-cleanup-design.md`

---

### Task 1: Add `seconds()` helper and replace all tick math

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/bdd/UIActions.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/PhaseSync.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/HarnessPlayerA.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/HarnessPlayerB.java`

- [ ] **Step 1: Add `seconds()` to UIActions**

In `UIActions.java`, add after the class declaration:

```java
/** Convert seconds to game ticks (20 ticks/second). */
public static int seconds(int s) { return s * 20; }
```

Replace the constants:

```java
public static final int CONNECT_TIMEOUT = seconds(30);
public static final int TIMEOUT = seconds(10);
```

- [ ] **Step 2: Replace `15 * 20` in PhaseSync.java:73**

```java
// Before:
}, 15 * 20);
// After:
}, UIActions.seconds(15));
```

Add import: `import static com.disqt.disquests.test.integration.bdd.UIActions.seconds;`

Then use: `}, seconds(15));`

- [ ] **Step 3: Replace `15 * 20` in HarnessPlayerA.java:41 and HarnessPlayerB.java:41**

Both files, same change:

```java
// Before:
context.waitFor(client -> Files.exists(...), 15 * 20);
// After:
context.waitFor(client -> Files.exists(...), seconds(15));
```

Add static import of `seconds` to both files.

- [ ] **Step 4: Run tests**

Run: `./gradlew :client:runDuoTests`
Expected: PASS (4+4)

- [ ] **Step 5: Commit**

```
git add client/src/testmod/
git commit -m "refactor(test): add seconds() helper, replace tick math"
```

---

### Task 2: Consolidate HarnessPlayerA/B into HarnessCommon

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/HarnessCommon.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/HarnessPlayerA.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/HarnessPlayerB.java`

- [ ] **Step 1: Create HarnessCommon.java**

Extract all shared logic from HarnessPlayerA. Parameterize by role string.

```java
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

/**
 * Shared test harness logic for both PlayerA and PlayerB.
 * Each player's FabricClientGameTest entry point delegates here.
 */
public final class HarnessCommon {

    private static final String TESTS_PACKAGE = System.getProperty(
        "disquests.test.package", "com.disqt.disquests.test.integration.journeys");

    private HarnessCommon() {}

    /**
     * Main entry point. Called by HarnessPlayerA/B with role "a" or "b".
     */
    public static void run(ClientGameTestContext context, String role) {
        String playerRole = role.equals("a") ? "PlayerA" : "PlayerB";
        String harnessName = "HarnessPlayer" + playerRole.substring(6); // "A" or "B"

        if (shouldSkip(harnessName)) return;

        TestContext.set(context, playerRole);
        connectAndWait(context);

        boolean harness = Boolean.getBoolean("disquests.test.harness");

        if (harness) {
            harnessLoop(context, role);
        } else {
            String result = runJUnitSuite(playerRole, null);
            writeResult(role, result);

            String otherRole = role.equals("a") ? "b" : "a";
            PhaseSync.signal("player-" + role + "-done");
            try {
                context.waitFor(client ->
                    Files.exists(PhaseSync.getSyncDir().resolve("player-" + otherRole + "-done.done")),
                    seconds(15));
            } catch (Exception ignored) {}
            System.exit(result.startsWith("PASS") ? 0 : 1);
        }
    }

    private static void harnessLoop(ClientGameTestContext context, String role) {
        String playerRole = role.equals("a") ? "PlayerA" : "PlayerB";
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

            String result = runJUnitSuite(playerRole, filter);
            writeResult(role, result);
        }
    }

    static String runJUnitSuite(String playerRole, String testClassFilter) {
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

    private static void writeResult(String role, String result) {
        try {
            Path syncDir = PhaseSync.getSyncDir();
            Files.createDirectories(syncDir);
            Files.writeString(syncDir.resolve("results-" + role + ".txt"), result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write results", e);
        }
    }
}
```

- [ ] **Step 2: Slim down HarnessPlayerA.java**

Replace entire file contents:

```java
package com.disqt.disquests.test.integration.harness;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class HarnessPlayerA implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        HarnessCommon.run(context, "a");
    }
}
```

- [ ] **Step 3: Slim down HarnessPlayerB.java**

Replace entire file contents:

```java
package com.disqt.disquests.test.integration.harness;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class HarnessPlayerB implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        HarnessCommon.run(context, "b");
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :client:runDuoTests`
Expected: PASS (4+4)

- [ ] **Step 5: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/harness/
git commit -m "refactor(test): consolidate harness into HarnessCommon"
```

---

### Task 3: Extract `findComponent()` helper and refactor UIActions + UIAssertions

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/bdd/UIActions.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/bdd/UIAssertions.java`

- [ ] **Step 1: Add `findComponent` and `findComponentOrNull` to UIActions**

Add to UIActions after the `seconds()` method:

```java
/**
 * Find a component by ID on the current Disquests screen.
 * Runs on the client thread. Throws AssertionError if screen or component not found.
 */
public static <T extends UIComponent> T findComponent(ClientGameTestContext context, Class<T> type, String id) {
    return context.computeOnClient(c -> {
        if (c.currentScreen instanceof DisquestsBaseScreen dScreen) {
            var root = dScreen.getRootComponent();
            if (root == null) throw new AssertionError("Screen root not initialized");
            T component = root.childById(type, id);
            if (component == null) throw new AssertionError("Component not found: " + id);
            return component;
        }
        throw new AssertionError("Current screen is not a Disquests screen");
    });
}

/**
 * Find a component by ID, returning null if not found.
 * Runs on the client thread. Returns null if screen is wrong type or component missing.
 */
@javax.annotation.Nullable
public static <T extends UIComponent> T findComponentOrNull(ClientGameTestContext context, Class<T> type, String id) {
    return context.computeOnClient(c -> {
        if (c.currentScreen instanceof DisquestsBaseScreen dScreen) {
            var root = dScreen.getRootComponent();
            if (root == null) return null;
            return root.childById(type, id);
        }
        return null;
    });
}

/**
 * Get the center coordinates of a component (screen-space, pre-scale).
 * Runs on client thread.
 */
public static double[] componentCenter(ClientGameTestContext context, UIComponent component) {
    return context.computeOnClient(c ->
        new double[]{component.x() + component.width() / 2.0, component.y() + component.height() / 2.0});
}

/**
 * Get the window scale factor.
 */
public static double scaleFactor(ClientGameTestContext context) {
    return context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
}
```

- [ ] **Step 2: Refactor `click()` to use helpers**

```java
public static void click(ClientGameTestContext context, String componentId) {
    var component = findComponent(context, UIComponent.class, componentId);
    double[] pos = componentCenter(context, component);
    double scale = scaleFactor(context);
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
}
```

- [ ] **Step 3: Refactor `clickEntry()` and `clickEntryByTitle()` to use helpers**

`clickEntry`:
```java
public static void clickEntry(ClientGameTestContext context, int index) {
    var questList = findComponent(context, io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
    double[] pos = context.computeOnClient(c -> {
        var children = questList.children();
        if (index >= children.size()) throw new AssertionError("Entry index " + index + " out of bounds, size=" + children.size());
        var entry = children.get(index);
        return new double[]{entry.x() + entry.width() / 2.0, entry.y() + entry.height() / 2.0};
    });
    double scale = scaleFactor(context);
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
}
```

`clickEntryByTitle`:
```java
public static void clickEntryByTitle(ClientGameTestContext context, String title) {
    var questList = findComponent(context, io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
    double[] pos = context.computeOnClient(c -> {
        for (var child : questList.children()) {
            if (child instanceof com.disqt.disquests.client.gui.component.QuestEntryComponent entry) {
                if (title.equals(entry.getQuest().getTitle())) {
                    return new double[]{entry.x() + entry.width() / 2.0, entry.y() + entry.height() / 2.0};
                }
            }
        }
        throw new AssertionError("No entry with title '" + title + "' found in quest list");
    });
    double scale = scaleFactor(context);
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
}
```

- [ ] **Step 4: Refactor `clickPinIcon()` using named constants**

Add constants:
```java
private static final double PIN_ICON_CLICK_X = 10.0;  // offset from right edge
private static final double PIN_ICON_CLICK_Y = 19.0;  // offset from entry top
```

```java
public static void clickPinIcon(ClientGameTestContext context, int index) {
    var questList = findComponent(context, io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
    double[] pos = context.computeOnClient(c -> {
        var entry = questList.children().get(index);
        return new double[]{entry.x() + entry.width() - PIN_ICON_CLICK_X, entry.y() + PIN_ICON_CLICK_Y};
    });
    double scale = scaleFactor(context);
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
}
```

- [ ] **Step 5: Refactor UIAssertions to use `findComponent`/`findComponentOrNull`**

Every method in UIAssertions currently does the `computeOnClient -> instanceof DisquestsBaseScreen -> getRootComponent -> childById` chain. Replace with calls to `UIActions.findComponent` or `UIActions.findComponentOrNull`.

`assertLabelText`:
```java
public static void assertLabelText(ClientGameTestContext context, String componentId, String expected) {
    LabelComponent label = UIActions.findComponent(context, LabelComponent.class, componentId);
    String actual = context.computeOnClient(c -> label.text().getString());
    assertEquals(expected, actual, "Label '" + componentId + "' text mismatch");
}
```

`assertButtonText`:
```java
public static void assertButtonText(ClientGameTestContext context, String componentId, String expectedSubstring) {
    ButtonComponent btn = UIActions.findComponent(context, ButtonComponent.class, componentId);
    String actual = context.computeOnClient(c -> btn.getMessage().getString());
    assertTrue(actual.contains(expectedSubstring),
        "Button '" + componentId + "' text '" + actual + "' does not contain '" + expectedSubstring + "'");
}
```

`assertComponentExists`:
```java
public static void assertComponentExists(ClientGameTestContext context, String componentId) {
    UIComponent comp = UIActions.findComponentOrNull(context, UIComponent.class, componentId);
    assertTrue(comp != null, "Component '" + componentId + "' not found");
}
```

`assertComponentMissing`:
```java
public static void assertComponentMissing(ClientGameTestContext context, String componentId) {
    UIComponent comp = UIActions.findComponentOrNull(context, UIComponent.class, componentId);
    assertFalse(comp != null, "Component '" + componentId + "' should not exist but was found");
}
```

`assertEntryCount`:
```java
public static void assertEntryCount(ClientGameTestContext context, int expected) {
    var questList = UIActions.findComponentOrNull(context, io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
    int actual = questList != null ? context.computeOnClient(c -> questList.children().size()) : -1;
    assertEquals(expected, actual, "Quest list entry count mismatch");
}
```

`assertComponent` (generic) can stay as-is since it already takes a predicate, but can use `findComponentOrNull` internally.

- [ ] **Step 6: Run full test suite**

Run: `./gradlew :client:runIntegrationTest`
Expected: All 48 tests pass (40 solo + 8 duo)

- [ ] **Step 7: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/bdd/
git commit -m "refactor(test): extract findComponent helper, simplify UIActions and UIAssertions"
```

---

### Task 4: Add `waitForQuestCondition()` and clean up TwoPlayerJourneys

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/bdd/UIActions.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/duo/TwoPlayerJourneys.java`

- [ ] **Step 1: Add `waitForQuestCondition` to UIActions**

```java
/**
 * Wait for a quest matching title to satisfy a condition in the cache.
 * @param myQuests true = search myQuests, false = search serverQuests
 */
public static void waitForQuestCondition(
        ClientGameTestContext context, String title, boolean myQuests, java.util.function.Predicate<Quest> condition) {
    context.waitFor(client -> {
        var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
        return list.stream()
            .filter(q -> title.equals(q.getTitle()))
            .findFirst()
            .map(condition::test)
            .orElse(false);
    }, TIMEOUT);
}
```

- [ ] **Step 2: Replace inline stream chains in TwoPlayerJourneys**

At line ~105 (pending count wait):
```java
// Before:
context.waitFor(client -> ClientCache.getPendingCount(
    ClientCache.getMyQuests().stream()
        .filter(q -> "Collab Quest".equals(q.getTitle()))
        .findFirst().map(q -> q.getId()).orElse(null)) > 0,
    TIMEOUT);
// After:
waitForQuestCondition(context, "Collab Quest", true,
    q -> ClientCache.getPendingCount(q.getId()) > 0);
```

At line ~170 (contributor added):
```java
// Before: inline stream chain
// After:
waitForQuestCondition(context, "Collab Quest", true,
    q -> !q.getContributors().isEmpty());
```

At line ~276 (contributor removed):
```java
// Before: inline stream chain
// After:
waitForQuestCondition(context, "Collab Quest", true,
    q -> q.getContributors().isEmpty());
```

- [ ] **Step 3: Run duo tests**

Run: `./gradlew :client:runDuoTests`
Expected: PASS (4+4)

- [ ] **Step 4: Commit**

```
git add client/src/testmod/
git commit -m "refactor(test): extract waitForQuestCondition, simplify TwoPlayerJourneys"
```

---

### Task 5: Final verification

- [ ] **Step 1: Run full integration test suite**

Run: `./gradlew :client:runIntegrationTest`
Expected: All 48 tests pass (40 solo + 8 duo)

- [ ] **Step 2: Verify no behavioral changes**

Check that test execution time is similar to before (no new delays or timeouts).

- [ ] **Step 3: Push and PR**
