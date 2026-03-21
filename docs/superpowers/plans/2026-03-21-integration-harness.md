# Integration Test Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple integration test client lifecycle from test execution, enabling iterative debugging with persistent clients, per-test re-runs, and a proper JUnit 5 test framework inside the Minecraft client JVM.

**Architecture:** FabricClientGameTest entry points become thin harness wrappers that keep clients alive and poll for test triggers. Test logic moves into JUnit 5 test classes, executed programmatically inside the client JVM via JUnit's `Launcher` API. A custom JUnit extension handles `ClientGameTestContext` injection, player-based filtering, and state cleanup. The Gradle task orchestrates server/client lifecycle with smart detection and RCON-based DB reset.

**Tech Stack:** JUnit 5 (jupiter + platform-launcher), Fabric Client GameTest API, Source RCON protocol, Gradle Kotlin DSL, PaperMC plugin API, SQLite.

**Spec:** `docs/superpowers/specs/2026-03-21-integration-harness-design.md`

---

### Task 1: Server-Side Debug Mode + Reset Command

Add `/disquests reset` command gated behind a debug flag. This is the foundation for repeatable test runs.

**Files:**
- Modify: `paper/src/main/java/com/disqt/disquests/paper/Config.java`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/DataManager.java`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/Commands.java`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/DisquestsPlugin.java`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java`
- Modify: `paper/src/main/resources/plugin.yml`
- Modify: `paper/src/test/java/com/disqt/disquests/paper/DataManagerTest.java`

- [ ] **Step 1: Write failing test for `DataManager.resetDatabase()`**

Add to `DataManagerTest.java`:

```java
@Test
void resetDatabase_clearsAllTables() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(questId, OWNER, "Test Quest", Visibility.OPEN));
    dm.pinQuest(OWNER, questId);
    dm.addContributor(questId, PLAYER2, false);
    dm.createCollaborationRequest(questId, PLAYER2);

    dm.resetDatabase();

    assertTrue(dm.getQuestsForPlayer(OWNER).isEmpty());
    assertTrue(dm.getPinnedQuestIds(OWNER).isEmpty());
    assertFalse(dm.isContributor(questId, PLAYER2));
    assertTrue(dm.getPendingRequestsForOwner(OWNER).isEmpty());
    assertNull(dm.getPlayerName(OWNER));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :paper:test --tests "DataManagerTest.resetDatabase_clearsAllTables" -x :client:build`
Expected: FAIL with `resetDatabase()` not found

- [ ] **Step 3: Implement `DataManager.resetDatabase()`**

Add to `DataManager.java` after the `close()` method:

```java
public synchronized void resetDatabase() {
    try (var stmt = connection.createStatement()) {
        // Order matters: foreign keys enforce dependencies
        stmt.executeUpdate("DELETE FROM pinned_quests");
        stmt.executeUpdate("DELETE FROM collaboration_requests");
        stmt.executeUpdate("DELETE FROM contributors");
        stmt.executeUpdate("DELETE FROM quests");
        stmt.executeUpdate("DELETE FROM player_names");
    } catch (SQLException e) {
        throw new RuntimeException("Failed to reset database", e);
    }
}
```

Note: DELETE instead of DROP+CREATE preserves schema/migrations. Foreign keys with ON DELETE CASCADE mean we only strictly need to delete from `quests` and `player_names`, but explicit deletion of all tables is clearer and avoids relying on cascade ordering.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :paper:test --tests "DataManagerTest.resetDatabase_clearsAllTables" -x :client:build`
Expected: PASS

- [ ] **Step 5: Add debug flag to Config**

In `Config.java`, add a `debug` field:

```java
private boolean debug;

// In reload():
this.debug = plugin.getConfig().getBoolean("debug", false);
// System property override
if (Boolean.getBoolean("disquests.debug")) {
    this.debug = true;
}

public boolean isDebug() { return debug; }
```

- [ ] **Step 6: Add `resendHandshakes()` to ServerPacketHandler**

Add a public method to `ServerPacketHandler.java` that resends handshakes to all connected mod players:

```java
public void resendHandshakes() {
    for (Player p : getModPlayers()) {
        sendHandshake(p);
    }
}
```

- [ ] **Step 7: Add reset subcommand to Commands**

The current `Commands` constructor takes `DisquestsPlugin plugin` only. Add `DataManager` and `ServerPacketHandler` fields. Replace the entire `Commands.java`:

```java
package com.disqt.disquests.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands implements CommandExecutor {
    private final DisquestsPlugin plugin;
    private final DataManager dataManager;
    private final ServerPacketHandler packetHandler;

    public Commands(DisquestsPlugin plugin, DataManager dataManager, ServerPacketHandler packetHandler) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.packetHandler = packetHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /disquests <reload|reset>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.getDisquestsConfig().reload(plugin);
            sender.sendMessage("Disquests config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (!plugin.getDisquestsConfig().isDebug()) {
                sender.sendMessage("Reset is only available in debug mode.");
                return true;
            }
            if (sender instanceof Player) {
                sender.sendMessage("Reset can only be run from console/RCON.");
                return true;
            }
            dataManager.resetDatabase();
            packetHandler.resendHandshakes();
            sender.sendMessage("Disquests database reset. Handshakes resent.");
            return true;
        }

        sender.sendMessage("Usage: /disquests <reload|reset>");
        return true;
    }
}
```

Update `DisquestsPlugin.onEnable()` to pass the new dependencies:
```java
getCommand("disquests").setExecutor(new Commands(this, dataManager, packetHandler));
```

- [ ] **Step 8: Update plugin.yml with reset command description**

The `/disquests` command is already registered. Add `reset` to the usage description. No new top-level command needed since `reset` is a subcommand of `/disquests`.

- [ ] **Step 9: Run all paper tests + manual RCON validation**

Run: `./gradlew :paper:test -x :client:build`
Expected: All tests pass (including the new `resetDatabase_clearsAllTables`)

- [ ] **Step 10: Commit**

```bash
git add paper/src/ paper/src/test/
git commit -m "feat: add /disquests reset command for integration test DB cleanup"
```

---

### Task 2: JUnit 5 Dependencies

Add JUnit 5 to the testmod classpath so the programmatic launcher API is available inside the Minecraft client JVM.

**Files:**
- Modify: `client/build.gradle.kts`

- [ ] **Step 1: Add JUnit dependencies to testmod source set**

Add to the `dependencies` block in `client/build.gradle.kts`:

```kotlin
"testmodImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
"testmodImplementation"("org.junit.platform:junit-platform-launcher:1.11.4")
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/build.gradle.kts
git commit -m "build: add JUnit 5 dependencies for integration test harness"
```

---

### Task 3: Harness Infrastructure -- Annotations and TestContext

Create the JUnit 5 annotation types and the static context holder that bridges FabricClientGameTest with JUnit.

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/TestContext.java`
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/PlayerA.java`
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/PlayerB.java`
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/IntegrationTest.java`

- [ ] **Step 1: Create `TestContext.java`**

Static holder for `ClientGameTestContext` and player identity. The harness sets these before launching JUnit.

```java
package com.disqt.disquests.test.integration.harness;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class TestContext {
    private static ClientGameTestContext context;
    private static String playerRole; // "PlayerA" or "PlayerB"

    private TestContext() {}

    public static void set(ClientGameTestContext ctx, String role) {
        context = ctx;
        playerRole = role;
    }

    public static ClientGameTestContext get() {
        if (context == null) throw new IllegalStateException("TestContext not initialized -- are you running inside the harness?");
        return context;
    }

    public static String getPlayerRole() {
        return playerRole;
    }

    public static boolean isPlayerA() {
        return "PlayerA".equals(playerRole);
    }

    public static boolean isPlayerB() {
        return "PlayerB".equals(playerRole);
    }
}
```

- [ ] **Step 2: Create `@PlayerA` and `@PlayerB` tag annotations**

```java
package com.disqt.disquests.test.integration.harness;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Tag("PlayerA")
public @interface PlayerA {}
```

```java
package com.disqt.disquests.test.integration.harness;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Tag("PlayerB")
public @interface PlayerB {}
```

- [ ] **Step 3: Create `@IntegrationTest` composed annotation**

```java
package com.disqt.disquests.test.integration.harness;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(IntegrationTestExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public @interface IntegrationTest {}
```

Note: `IntegrationTestExtension.class` doesn't exist yet -- it will be created in the next step.

- [ ] **Step 4: Commit** (deferred -- commit all harness infrastructure together after Task 5)

---

### Task 4: RconClient

Lightweight Source RCON client for sending `/disquests reset` from within the client JVM. The Source RCON protocol is simple: TCP connection, login packet, command packet, response.

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/RconClient.java`

- [ ] **Step 1: Implement RconClient**

```java
package com.disqt.disquests.test.integration.harness;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal Source RCON client. Protocol: length-prefixed packets with
 * [requestId(4) + type(4) + body(null-terminated) + pad(1 byte)].
 */
public final class RconClient implements AutoCloseable {
    private static final int TYPE_LOGIN = 3;
    private static final int TYPE_COMMAND = 2;
    private static final int TYPE_AUTH_RESPONSE = 2;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private int nextRequestId = 1;

    public RconClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(5000);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public void login(String password) throws IOException {
        int id = sendPacket(TYPE_LOGIN, password);
        RconResponse resp = readPacket();
        if (resp.requestId == -1) {
            throw new IOException("RCON authentication failed");
        }
    }

    public String command(String cmd) throws IOException {
        sendPacket(TYPE_COMMAND, cmd);
        RconResponse resp = readPacket();
        return resp.body;
    }

    private int sendPacket(int type, String body) throws IOException {
        int id = nextRequestId++;
        byte[] bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = 4 + 4 + bodyBytes.length + 1 + 1; // id + type + body + null + pad
        ByteBuffer buf = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(length);
        buf.putInt(id);
        buf.putInt(type);
        buf.put(bodyBytes);
        buf.put((byte) 0); // null terminator
        buf.put((byte) 0); // pad
        out.write(buf.array());
        out.flush();
        return id;
    }

    private RconResponse readPacket() throws IOException {
        byte[] lenBytes = new byte[4];
        in.readFully(lenBytes);
        int length = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] payload = new byte[length];
        in.readFully(payload);
        ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int requestId = buf.getInt();
        int type = buf.getInt();
        byte[] bodyBytes = new byte[length - 4 - 4 - 2]; // minus id, type, 2 null bytes
        buf.get(bodyBytes);
        return new RconResponse(requestId, type, new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private record RconResponse(int requestId, int type, String body) {}
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`
Expected: May fail due to `IntegrationTestExtension` reference in `@IntegrationTest` -- that's expected. The RconClient itself should compile.

- [ ] **Step 3: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/harness/RconClient.java
git commit -m "feat: add lightweight Source RCON client for integration test DB reset"
```

---

### Task 5: IntegrationTestExtension

The core JUnit 5 extension that makes test classes transparent to the harness. Handles parameter injection, player filtering, client-side cache cleanup, and connection management.

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/IntegrationTestExtension.java`

- [ ] **Step 1: Implement the extension**

```java
package com.disqt.disquests.test.integration.harness;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Method;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.connectAndWait;

/**
 * JUnit 5 extension for Disquests integration tests.
 *
 * Responsibilities:
 * - Injects ClientGameTestContext into test method parameters
 * - Filters test methods by @PlayerA/@PlayerB based on current client identity
 * - Clears ClientCache once per test class (BeforeAll), NOT per method
 * - Ensures client is connected to the server before each method
 *
 * Cache is cleared per-class, not per-method, because ordered @Test methods
 * within a class share state (e.g., LifecycleTest creates a quest in @Order(1)
 * and modifies it in @Order(2)). Server DB is reset externally via RCON before
 * the entire test run, not inside the extension.
 */
public class IntegrationTestExtension implements
        ExecutionCondition,
        BeforeAllCallback,
        BeforeEachCallback,
        ParameterResolver {

    // --- ExecutionCondition: skip methods tagged for the other player ---

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext ctx) {
        if (ctx.getTestMethod().isEmpty()) {
            return ConditionEvaluationResult.enabled("Class-level: always enabled");
        }

        Method method = ctx.getTestMethod().get();
        boolean hasPlayerA = method.isAnnotationPresent(PlayerA.class);
        boolean hasPlayerB = method.isAnnotationPresent(PlayerB.class);

        // Methods without a player tag run on both clients
        if (!hasPlayerA && !hasPlayerB) {
            return ConditionEvaluationResult.enabled("No player tag: runs on both");
        }

        boolean isA = TestContext.isPlayerA();
        if (hasPlayerA && isA) return ConditionEvaluationResult.enabled("PlayerA method on PlayerA client");
        if (hasPlayerB && !isA) return ConditionEvaluationResult.enabled("PlayerB method on PlayerB client");

        return ConditionEvaluationResult.disabled("Skipped: method is @" +
            (hasPlayerA ? "PlayerA" : "PlayerB") + " but this client is " + TestContext.getPlayerRole());
    }

    // --- BeforeAllCallback: clear cache and sync once per test class ---

    @Override
    public void beforeAll(ExtensionContext ctx) {
        ClientGameTestContext context = TestContext.get();

        // Ensure connected
        if (!ClientSession.isOnServer()) {
            connectAndWait(context);
        }

        // Clear client-side cache and request fresh sync
        context.runOnClient(c -> {
            ClientCache.clear();
            com.disqt.disquests.client.network.PacketSender.requestSync();
        });
        context.waitTicks(20);
    }

    // --- BeforeEachCallback: ensure connection (lightweight) ---

    @Override
    public void beforeEach(ExtensionContext ctx) {
        // Only check connection -- don't clear cache between ordered methods
        if (!ClientSession.isOnServer()) {
            connectAndWait(TestContext.get());
        }
    }

    // --- ParameterResolver: inject ClientGameTestContext ---

    @Override
    public boolean supportsParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        return paramCtx.getParameter().getType() == ClientGameTestContext.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        return TestContext.get();
    }
}
```

Note: `BeforeAllCallback` requires the test class to have `@TestInstance(Lifecycle.PER_CLASS)` or use only static extension state. Since our extension uses `TestContext` (which is static), this works without `@TestInstance`.

- [ ] **Step 2: Verify full testmod compilation**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL (all harness classes + @IntegrationTest reference now resolve)

- [ ] **Step 3: Commit all harness infrastructure (Tasks 3-5)**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/harness/
git commit -m "feat: add JUnit 5 integration test harness (annotations, extension, context holder)"
```

---

### Task 6: PhaseSync Error Propagation

Update PhaseSync to detect when the other client has failed, enabling fast-fail instead of waiting for the full 120-second timeout.

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/PhaseSync.java`

- [ ] **Step 1: Add error signaling and fast-fail detection**

Add these methods to `PhaseSync.java`:

```java
/**
 * Signal that this client has encountered an error.
 * The other client's waitFor() will detect this and fail fast.
 */
public static void signalError(String playerRole, String message) {
    try {
        Files.createDirectories(SYNC_DIR);
        Files.writeString(SYNC_DIR.resolve("error-" + playerRole.toLowerCase() + ".done"), message);
    } catch (IOException e) {
        // Best-effort; the other client will time out if this fails
    }
}

/**
 * Check if the other client has signaled an error.
 */
private static String checkOtherClientError() {
    String otherPlayer = TestContext.isPlayerA() ? "playerb" : "playera";
    Path errorFile = SYNC_DIR.resolve("error-" + otherPlayer + ".done");
    if (Files.exists(errorFile)) {
        try {
            return Files.readString(errorFile).trim();
        } catch (IOException e) {
            return "Unknown error";
        }
    }
    return null;
}
```

Update the `waitFor` method to check for error files on each poll:

```java
public static void waitFor(String phaseName, ClientGameTestContext context) {
    Path marker = SYNC_DIR.resolve(phaseName + ".done");
    context.waitFor(client -> {
        // Fast-fail if the other client errored
        String error = checkOtherClientError();
        if (error != null) {
            throw new AssertionError("Other client failed while waiting for '" + phaseName + "': " + error);
        }
        return Files.exists(marker);
    }, 120 * 20);
}
```

Note: This requires adding `import com.disqt.disquests.test.integration.harness.TestContext;` to PhaseSync. For backward compatibility with the old `IntegrationPlayerA/B` (which don't set TestContext), guard the `checkOtherClientError()` call:

```java
private static String checkOtherClientError() {
    try {
        String role = TestContext.getPlayerRole();
        if (role == null) return null; // Not in harness mode
        String otherPlayer = TestContext.isPlayerA() ? "playerb" : "playera";
        // ...
    } catch (IllegalStateException e) {
        return null; // TestContext not initialized
    }
}
```

- [ ] **Step 2: Expose `getSyncDir()` public accessor**

Add to `PhaseSync.java`:

```java
public static Path getSyncDir() {
    return SYNC_DIR;
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/PhaseSync.java
git commit -m "feat: add cross-client error propagation and getSyncDir to PhaseSync"
```

---

### Task 7: Harness Entry Points

Create `HarnessPlayerA` and `HarnessPlayerB` -- the FabricClientGameTest implementations that either run once (CI) or loop (harness mode). Update `fabric.mod.json` entrypoints.

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/HarnessPlayerA.java`
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/HarnessPlayerB.java`
- Modify: `client/src/testmod/resources/fabric.mod.json`

- [ ] **Step 1: Create shared harness runner logic**

Both players share the same loop logic. Create a private static method in each harness class (or a shared utility). Since the logic is identical except for player identity, I'll show the full `HarnessPlayerA` and note that `HarnessPlayerB` is identical with "PlayerB" substituted.

```java
package com.disqt.disquests.test.integration.harness;

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

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

public class HarnessPlayerA implements FabricClientGameTest {

    private static final String PLAYER_ROLE = "PlayerA";
    private static final String TESTS_PACKAGE = "com.disqt.disquests.test.integration.tests";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("HarnessPlayerA")) return;

        TestContext.set(context, PLAYER_ROLE);
        connectAndWait(context);

        boolean harness = Boolean.getBoolean("disquests.test.harness");

        if (harness) {
            harnessLoop(context);
        } else {
            // One-shot mode: run all tests once, then exit
            String result = runJUnitSuite(null);
            writeResult(result);
            System.exit(result.startsWith("PASS") ? 0 : 1);
        }
    }

    private void harnessLoop(ClientGameTestContext context) {
        Path syncDir = PhaseSync.getSyncDir();

        while (true) {
            // Clean stale result file
            try { Files.deleteIfExists(syncDir.resolve("results-a.txt")); } catch (IOException ignored) {}
            try { Files.deleteIfExists(syncDir.resolve("error-playera.done")); } catch (IOException ignored) {}

            // Signal ready
            PhaseSync.signal("client-a-ready");

            // Wait for run signal
            context.waitFor(client -> Files.exists(syncDir.resolve("run.signal")), Integer.MAX_VALUE);

            // Read test filter from run.signal
            String filter = null;
            try {
                String content = Files.readString(syncDir.resolve("run.signal")).trim();
                if (!content.equals("*")) filter = content;
            } catch (IOException ignored) {}

            // Run tests
            String result = runJUnitSuite(filter);
            writeResult(result);
        }
    }

    private String runJUnitSuite(String testClassFilter) {
        try {
            var requestBuilder = LauncherDiscoveryRequestBuilder.request();

            if (testClassFilter != null) {
                // Run specific test class
                try {
                    Class<?> testClass = Class.forName(TESTS_PACKAGE + "." + testClassFilter);
                    requestBuilder.selectors(DiscoverySelectors.selectClass(testClass));
                } catch (ClassNotFoundException e) {
                    return "FAIL: Test class not found: " + testClassFilter;
                }
            } else {
                // Run all tests in package
                requestBuilder.selectors(DiscoverySelectors.selectPackage(TESTS_PACKAGE));
            }

            // Do NOT use TagFilter here -- it would exclude untagged methods that should
            // run on both clients. The IntegrationTestExtension's ExecutionCondition handles
            // player-based filtering, allowing untagged methods to run on both clients.

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
```

- [ ] **Step 2: Create `HarnessPlayerB.java`**

Identical to HarnessPlayerA with these substitutions:
- Class name: `HarnessPlayerB`
- `PLAYER_ROLE = "PlayerB"`
- `shouldSkip("HarnessPlayerB")`
- Result file: `results-b.txt`
- Error file: `error-playerb.done`
- Ready signal: `client-b-ready`

- [ ] **Step 3: Update `fabric.mod.json` entrypoints**

Replace the old integration test entries with the new harness entries:

```json
{
  "entrypoints": {
    "fabric-client-gametest": [
      "com.disqt.disquests.test.QuestScreenTest",
      "com.disqt.disquests.test.DisquestsE2ETest",
      "com.disqt.disquests.test.integration.harness.HarnessPlayerA",
      "com.disqt.disquests.test.integration.harness.HarnessPlayerB"
    ]
  }
}
```

Keep the old `IntegrationPlayerA`/`IntegrationPlayerB` files for now (they just won't be registered as entrypoints). Delete them later once the new framework is validated.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/harness/Harness*.java
git add client/src/testmod/resources/fabric.mod.json
git commit -m "feat: add harness entry points with JUnit programmatic launcher"
```

---

### Task 8: LifecycleTest (First Test Class -- Validates Framework)

This is the first test class and validates the entire harness infrastructure works end-to-end. It's Player A only (no coordination), making it the simplest test to debug.

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/tests/LifecycleTest.java`

- [ ] **Step 1: Write LifecycleTest**

```java
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
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run end-to-end validation**

Run: `./gradlew :client:runIntegrationTest`

This is the first end-to-end test of the new framework. The existing `runIntegrationTest` task won't work yet (it still references old journey names). For now, test manually:

1. Start Paper server: `./gradlew :paper:runServer` (in one terminal)
2. Send reset: connect to RCON port 25575 with password `testpassword`, send `disquests reset`
3. Run client A: `./gradlew :client:runClientGameTest -PtestJourney=HarnessPlayerA`

Expected: Client starts, connects, runs LifecycleTest, prints results, exits.

If this works, the framework is validated. If not, debug before proceeding.

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/tests/LifecycleTest.java
git commit -m "feat: add LifecycleTest - first JUnit 5 integration test class"
```

---

### Task 9: DiscoveryTest

Two-player test: Player A creates an OPEN quest, Player B discovers and joins it.

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/tests/DiscoveryTest.java`

- [ ] **Step 1: Write DiscoveryTest**

```java
package com.disqt.disquests.test.integration.tests;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.harness.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Quest Discovery (A creates OPEN, B discovers and joins)")
class DiscoveryTest {

    static final UUID QUEST = UUID.fromString("aaaaaaaa-0002-0000-0000-000000000001");

    @Test @Order(1) @PlayerA
    @DisplayName("A creates OPEN quest")
    void createsOpenQuest(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Join Test", "Come join!", null, false, null, null));
        waitForQuestByTitle(context, "Join Test", true);
        context.runOnClient(c -> PacketSender.updateVisibility(QUEST, Visibility.OPEN));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(QUEST);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);

        PhaseSync.signal("discovery-a-created");
        PhaseSync.waitFor("discovery-b-joined", context);
    }

    @Test @Order(1) @PlayerB
    @DisplayName("B discovers and joins the quest")
    void discoversAndJoins(ClientGameTestContext context) {
        PhaseSync.waitFor("discovery-a-created", context);

        var quest = waitForQuestByTitle(context, "Join Test", false);
        assertNotNull(quest, "Join Test should appear on Quest Board");

        context.runOnClient(c -> PacketSender.joinQuest(QUEST));
        context.waitFor(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST)),
            TIMEOUT);

        var joined = context.computeOnClient(c ->
            ClientCache.getMyQuests().stream().filter(q -> q.getId().equals(QUEST)).findFirst().orElse(null));
        assertNotNull(joined, "Quest should appear in My Quests after joining");

        PhaseSync.signal("discovery-b-joined");
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/tests/DiscoveryTest.java
git commit -m "feat: add DiscoveryTest - two-player quest discovery integration test"
```

---

### Task 10: CollaborationTest

Two-player test: A creates CLOSED quest, B requests collaboration, A accepts.

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/tests/CollaborationTest.java`

- [ ] **Step 1: Write CollaborationTest**

```java
package com.disqt.disquests.test.integration.tests;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.harness.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Collaboration (A creates CLOSED, B requests, A accepts)")
class CollaborationTest {

    static final UUID QUEST = UUID.fromString("aaaaaaaa-0003-0000-0000-000000000001");

    @Test @Order(1) @PlayerA
    @DisplayName("A creates CLOSED quest and accepts B's request")
    void createsAndAccepts(ClientGameTestContext context) {
        // Create and set to CLOSED
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Secret Base", "Top secret", null, false, null, null));
        waitForQuestByTitle(context, "Secret Base", true);
        context.runOnClient(c -> PacketSender.updateVisibility(QUEST, Visibility.CLOSED));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(QUEST);
            return q != null && q.getVisibility() == Visibility.CLOSED;
        }, TIMEOUT);

        PhaseSync.signal("collab-a-created");
        PhaseSync.waitFor("collab-b-requested", context);

        // Wait for B's collaboration request to arrive
        context.waitFor(c ->
            !ClientCache.getPendingRequestsForQuest(QUEST).isEmpty(), TIMEOUT);

        var requests = context.computeOnClient(c -> ClientCache.getPendingRequestsForQuest(QUEST));
        assertFalse(requests.isEmpty(), "Should have pending collaboration requests");

        // Accept the request (with optimistic cache removal)
        UUID requestId = requests.get(0).id();
        context.runOnClient(c -> {
            PacketSender.respondCollaboration(requestId, true);
            ClientCache.removePendingRequest(QUEST, requestId);
        });

        context.waitFor(c ->
            ClientCache.getPendingRequestsForQuest(QUEST).isEmpty(), TIMEOUT);

        PhaseSync.signal("collab-a-accepted");
    }

    @Test @Order(1) @PlayerB
    @DisplayName("B requests collaboration and gets accepted")
    void requestsAndJoins(ClientGameTestContext context) {
        PhaseSync.waitFor("collab-a-created", context);

        var quest = waitForQuestByTitle(context, "Secret Base", false);
        assertNotNull(quest, "Secret Base should appear on Quest Board");

        context.runOnClient(c -> PacketSender.requestCollaboration(QUEST));
        context.waitTicks(20);

        PhaseSync.signal("collab-b-requested");
        PhaseSync.waitFor("collab-a-accepted", context);

        // After acceptance, quest should appear in My Quests
        context.waitFor(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST)),
            TIMEOUT);

        var joined = context.computeOnClient(c ->
            ClientCache.getMyQuests().stream().filter(q -> q.getId().equals(QUEST)).findFirst().orElse(null));
        assertNotNull(joined, "Quest should appear in My Quests after collaboration accepted");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/tests/CollaborationTest.java
git commit -m "feat: add CollaborationTest - two-player collaboration flow integration test"
```

---

### Task 11: LeaveTest

Two-player test: A creates OPEN quest, B joins then leaves.

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/tests/LeaveTest.java`

- [ ] **Step 1: Write LeaveTest**

```java
package com.disqt.disquests.test.integration.tests;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import com.disqt.disquests.test.integration.PhaseSync;
import com.disqt.disquests.test.integration.harness.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@DisplayName("Leave Quest (A creates OPEN, B joins and leaves)")
class LeaveTest {

    static final UUID QUEST = UUID.fromString("aaaaaaaa-0004-0000-0000-000000000001");

    @Test @Order(1) @PlayerA
    @DisplayName("A creates OPEN quest for B to join and leave")
    void createsOpenQuest(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Leave Test", "Try leaving", null, false, null, null));
        waitForQuestByTitle(context, "Leave Test", true);
        context.runOnClient(c -> PacketSender.updateVisibility(QUEST, Visibility.OPEN));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(QUEST);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);

        PhaseSync.signal("leave-a-created");
        PhaseSync.waitFor("leave-b-left", context);
    }

    @Test @Order(1) @PlayerB
    @DisplayName("B joins then leaves the quest")
    void joinsAndLeaves(ClientGameTestContext context) {
        PhaseSync.waitFor("leave-a-created", context);

        waitForQuestByTitle(context, "Leave Test", false);

        // Join
        context.runOnClient(c -> PacketSender.joinQuest(QUEST));
        context.waitFor(c ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST)),
            TIMEOUT);

        // Leave
        context.runOnClient(c -> PacketSender.leaveQuest(QUEST));
        waitForQuestRemoved(context, QUEST);

        var quest = context.computeOnClient(c -> ClientCache.getQuestById(QUEST));
        assertNull(quest, "Quest should be removed from My Quests after leaving");

        PhaseSync.signal("leave-b-left");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/tests/LeaveTest.java
git commit -m "feat: add LeaveTest - two-player leave quest integration test"
```

---

### Task 12: PinPersistenceTest

Player A only: pins a quest, disconnects, reconnects, verifies pin persists.

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/tests/PinPersistenceTest.java`

- [ ] **Step 1: Write PinPersistenceTest**

```java
package com.disqt.disquests.test.integration.tests;

import com.disqt.disquests.client.ClientCache;
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
        assertTrue(context.computeOnClient(c -> ClientSession.isPinned(QUEST)));

        // Wait for server to process the fire-and-forget pin packet
        context.waitTicks(40);
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Pin persists after disconnect and reconnect")
    void pinPersistsAfterReconnect(ClientGameTestContext context) {
        // Disconnect
        disconnect(context);

        // Reconnect
        connectAndWait(context);

        // Verify pin restored from server handshake
        context.waitFor(c -> ClientSession.isPinned(QUEST), TIMEOUT);
        assertTrue(context.computeOnClient(c -> ClientSession.isPinned(QUEST)),
            "Pin should persist after reconnection");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/tests/PinPersistenceTest.java
git commit -m "feat: add PinPersistenceTest - pin persistence across reconnects"
```

---

### Task 13: Gradle Task Rewrite

Update `runIntegrationTest` to support harness mode, smart client detection, RCON reset, and test filtering. Update Loom run configs with harness property wiring.

**Files:**
- Modify: `client/build.gradle.kts`

- [ ] **Step 1: Add harness property to Loom run configs**

In both `clientGameTest` and `clientGameTestB` run config blocks, add:

```kotlin
val harness = providers.gradleProperty("harness")
if (harness.isPresent) {
    vmArg("-Ddisquests.test.harness=true")
}
```

- [ ] **Step 2: Rewrite `runIntegrationTest` task**

Replace the entire `runIntegrationTest` task registration with the new implementation. Key changes:

```kotlin
tasks.register("runIntegrationTest") {
    group = "verification"
    description = "Run integration E2E tests with optional harness mode for persistent clients"
    dependsOn(":paper:jar", ":client:build")

    doLast {
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val gradlew = File(rootProject.projectDir, if (isWin) "gradlew.bat" else "gradlew").absolutePath
        val noStart = project.hasProperty("noStart")
        val harness = project.hasProperty("harness")
        val testFilter = project.findProperty("test")?.toString()

        val syncDir = File(rootProject.projectDir, "integration-sync")
        val serverDir = file("../paper/run")

        // --- Step 1: Clean sync directory ---
        if (syncDir.exists()) syncDir.listFiles()?.forEach { it.delete() }
        syncDir.mkdirs()
        logger.lifecycle("Cleaned sync directory")

        // --- Step 2: Server ---
        var serverProcess: Process? = null
        var startedServer = false

        if (!noStart) {
            // Check if server is already running by probing the port
            val serverRunning = try {
                java.net.Socket("localhost", 25565).use { true }
            } catch (_: Exception) { false }

            if (!serverRunning) {
                // Clean DB
                val db = file("../paper/run/plugins/Disquests/disquests.db")
                val walFile = file("../paper/run/plugins/Disquests/disquests.db-wal")
                val shmFile = file("../paper/run/plugins/Disquests/disquests.db-shm")
                listOf(db, walFile, shmFile).forEach { if (it.exists()) it.delete() }

                // Deploy plugin jar
                val pluginJar = file("../paper/build/libs/paper.jar")
                val pluginDest = File(serverDir, "plugins/Disquests.jar")
                pluginJar.copyTo(pluginDest, overwrite = true)
                logger.lifecycle("Deployed plugin jar")

                val paperJar = File(serverDir, "paper.jar")
                serverProcess = ProcessBuilder(
                    "java", "-Xmx1G", "-Ddisquests.debug=true",
                    "-jar", paperJar.absolutePath, "--nogui"
                ).directory(serverDir).redirectErrorStream(true).start()
                startedServer = true

                // Drain server stdout
                val serverOutput = StringBuilder()
                val serverReader = Thread {
                    serverProcess!!.inputStream.bufferedReader().lines().forEach { serverOutput.appendLine(it) }
                }
                serverReader.isDaemon = true
                serverReader.start()

                // Wait for server to start
                logger.lifecycle("Waiting for Paper server...")
                val logFile = File(serverDir, "logs/latest.log")
                val deadline = System.currentTimeMillis() + 60000
                while (System.currentTimeMillis() < deadline) {
                    if (logFile.exists() && logFile.readText().contains("Done (")) break
                    Thread.sleep(1000)
                }
                if (!logFile.exists() || !logFile.readText().contains("Done (")) {
                    throw RuntimeException("Paper server failed to start within 60s")
                }
                logger.lifecycle("Paper server ready")
            } else {
                logger.lifecycle("Server already running on port 25565")
            }
        }

        try {
            // --- Step 3: Clients ---
            val processes = mutableListOf<Process>()
            var startedClients = false

            if (!noStart) {
                val clientAReady = File(syncDir, "client-a-ready.done").exists()
                val clientBReady = File(syncDir, "client-b-ready.done").exists()

                if (!clientAReady || !clientBReady) {
                    file("run-b").mkdirs()

                    fun launchClient(journey: String, username: String, taskName: String): Process {
                        val cmd = mutableListOf<String>()
                        if (isWin) cmd.addAll(listOf("cmd", "/c"))
                        cmd.add(gradlew)
                        cmd.addAll(listOf(taskName, "--no-daemon",
                            "-PtestJourney=$journey", "-PtestUsername=$username"))
                        if (harness) cmd.add("-Pharness")
                        logger.lifecycle("  Starting $journey as $username")
                        return ProcessBuilder(cmd)
                            .directory(rootProject.projectDir)
                            .redirectErrorStream(true)
                            .start()
                    }

                    val procA = launchClient("HarnessPlayerA", "IntTestPlayerA", ":client:runClientGameTest")
                    val procB = launchClient("HarnessPlayerB", "IntTestPlayerB", ":client:runClientGameTestB")
                    processes.addAll(listOf(procA, procB))
                    startedClients = true

                    // Drain output
                    val outputA = StringBuilder()
                    val outputB = StringBuilder()
                    Thread { procA.inputStream.bufferedReader().lines().forEach { outputA.appendLine(it) } }.apply { isDaemon = true; start() }
                    Thread { procB.inputStream.bufferedReader().lines().forEach { outputB.appendLine(it) } }.apply { isDaemon = true; start() }

                    // Wait for clients to be ready
                    logger.lifecycle("Waiting for clients to be ready...")
                    val readyDeadline = System.currentTimeMillis() + 120000
                    while (System.currentTimeMillis() < readyDeadline) {
                        if (File(syncDir, "client-a-ready.done").exists() &&
                            File(syncDir, "client-b-ready.done").exists()) break
                        Thread.sleep(1000)
                    }
                    if (!File(syncDir, "client-a-ready.done").exists() ||
                        !File(syncDir, "client-b-ready.done").exists()) {

                        // In one-shot mode, clients may have already finished
                        if (!harness) {
                            // Check if result files exist (one-shot already ran)
                            if (File(syncDir, "results-a.txt").exists() &&
                                File(syncDir, "results-b.txt").exists()) {
                                // Results already available, skip to reporting
                            } else {
                                throw RuntimeException("Clients failed to start within 120s")
                            }
                        } else {
                            throw RuntimeException("Clients failed to signal ready within 120s")
                        }
                    }
                } else {
                    logger.lifecycle("Clients already running (ready markers found)")
                }
            } else {
                // -PnoStart: verify clients are ready
                val readyDeadline = System.currentTimeMillis() + 10000
                while (System.currentTimeMillis() < readyDeadline) {
                    if (File(syncDir, "client-a-ready.done").exists() &&
                        File(syncDir, "client-b-ready.done").exists()) break
                    Thread.sleep(500)
                }
                if (!File(syncDir, "client-a-ready.done").exists() ||
                    !File(syncDir, "client-b-ready.done").exists()) {
                    throw RuntimeException("Clients not running. Start them first or remove -PnoStart.")
                }
            }

            // --- Step 4: RCON reset (only in harness mode / re-runs) ---
            if (noStart || harness) {
                // Clean sync dir (except ready markers)
                syncDir.listFiles()?.filter { !it.name.startsWith("client-") }?.forEach { it.delete() }

                sendRconCommand("localhost", 25575, "testpassword", "disquests reset")
                logger.lifecycle("Sent RCON reset command")
                Thread.sleep(1000) // Wait for handshakes to propagate
            }

            // --- Step 5: Write run signal ---
            val signalContent = testFilter ?: "*"
            File(syncDir, "run.signal").writeText(signalContent)
            logger.lifecycle("Triggered test run: $signalContent")

            // --- Step 6: Wait for results ---
            val resultA = File(syncDir, "results-a.txt")
            val resultB = File(syncDir, "results-b.txt")
            val resultsDeadline = System.currentTimeMillis() + 180000 // 3 min timeout
            while (System.currentTimeMillis() < resultsDeadline) {
                if (resultA.exists() && resultB.exists()) break
                // Check for process crashes in one-shot mode
                if (!harness && startedClients && processes.all { !it.isAlive }) break
                Thread.sleep(500)
            }

            // --- Step 7: Report results ---
            logger.lifecycle("\n=== Integration Test Results ===")
            val aResult = if (resultA.exists()) resultA.readText().trim() else "NO RESULT (timeout or crash)"
            val bResult = if (resultB.exists()) resultB.readText().trim() else "NO RESULT (timeout or crash)"
            logger.lifecycle("  Player A: $aResult")
            logger.lifecycle("  Player B: $bResult")

            val passed = aResult.startsWith("PASS") && bResult.startsWith("PASS")
            if (!passed) {
                throw RuntimeException("Integration tests failed")
            }
            logger.lifecycle("  All tests PASSED")

        } finally {
            // --- Step 8: Teardown ---
            if (!harness && !noStart) {
                processes.forEach { it.destroyForcibly() }
                if (serverProcess != null) {
                    try {
                        serverProcess.outputStream.write("stop\n".toByteArray())
                        serverProcess.outputStream.flush()
                        Thread.sleep(5000)
                    } catch (_: Exception) {}
                    if (serverProcess.isAlive) serverProcess.destroyForcibly()
                }
                logger.lifecycle("Teardown complete")
            } else {
                logger.lifecycle("Harness mode: server and clients left running")
            }
        }
    }
}
```

- [ ] **Step 3: Add inline RCON helper function**

Add this helper function inside `build.gradle.kts` (before the task registration, or as a local function inside `doLast`). Since Gradle Kotlin DSL shadows `java`, use `Class.forName` for any `java.*` types if needed, or use Kotlin stdlib:

```kotlin
fun sendRconCommand(host: String, port: Int, password: String, command: String): String {
    val socket = java.net.Socket(host, port)
    socket.soTimeout = 5000
    val out = socket.getOutputStream()
    val inp = socket.getInputStream()

    fun writePacket(id: Int, type: Int, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val length = 4 + 4 + bodyBytes.size + 1 + 1
        val buf = java.nio.ByteBuffer.allocate(4 + length).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putInt(length)
        buf.putInt(id)
        buf.putInt(type)
        buf.put(bodyBytes)
        buf.put(0.toByte())
        buf.put(0.toByte())
        out.write(buf.array())
        out.flush()
    }

    val dins = java.io.DataInputStream(inp)

    fun readPacket(): Triple<Int, Int, String> {
        val lenBuf = ByteArray(4)
        dins.readFully(lenBuf)
        val length = java.nio.ByteBuffer.wrap(lenBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        val payload = ByteArray(length)
        dins.readFully(payload)
        val buf = java.nio.ByteBuffer.wrap(payload).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val reqId = buf.int
        val type = buf.int
        val body = String(payload, 8, length - 10, Charsets.UTF_8)
        return Triple(reqId, type, body)
    }

    // Login
    writePacket(1, 3, password)
    val loginResp = readPacket()
    if (loginResp.first == -1) {
        socket.close()
        throw RuntimeException("RCON authentication failed")
    }

    // Command
    writePacket(2, 2, command)
    val cmdResp = readPacket()
    socket.close()
    return cmdResp.third
}
```

Note: This uses `java.net.Socket`, `java.nio.ByteBuffer`, and `java.nio.ByteOrder` directly. In Gradle Kotlin DSL, `java.net.*` and `java.nio.*` resolve fine -- only `java.lang.management.*` is shadowed (the `java` accessor shadows the `java` package only at the top level for Gradle-DSL-specific types). If `java.net.Socket` doesn't resolve, use `Class.forName("java.net.Socket")` pattern from CLAUDE.md.

- [ ] **Step 4: Verify the build script compiles**

Run: `./gradlew :client:tasks`
Expected: BUILD SUCCESSFUL (task listed under "verification")

- [ ] **Step 5: Commit**

```bash
git add client/build.gradle.kts
git commit -m "feat: rewrite runIntegrationTest with harness mode, RCON reset, and smart detection"
```

---

### Task 14: End-to-End Validation

Run the full test suite through the new framework in both one-shot and harness modes.

**Files:** (no changes -- validation only)

- [ ] **Step 1: One-shot mode (CI-equivalent)**

Run: `./gradlew :client:runIntegrationTest`

Expected output:
```
Paper server ready
  Starting HarnessPlayerA as IntTestPlayerA
  Starting HarnessPlayerB as IntTestPlayerB
Triggered test run: *

=== Integration Test Results ===
  Player A: PASS: 7 tests passed
  Player B: PASS: 4 tests passed
  All tests PASSED
Teardown complete
BUILD SUCCESSFUL
```

If this fails, check:
1. Server logs: `paper/run/logs/latest.log`
2. Client A logs: `client/run/logs/latest.log`
3. Client B logs: `client/run-b/logs/latest.log`
4. Result files: `integration-sync/results-{a|b}.txt`
5. Error files: `integration-sync/error-*.done`

- [ ] **Step 2: Harness mode startup**

Run: `./gradlew :client:runIntegrationTest -Pharness`

Expected: Tests pass, then task exits but server + clients stay alive. Verify:
- `integration-sync/client-a-ready.done` exists
- `integration-sync/client-b-ready.done` exists

- [ ] **Step 3: Re-run with `-PnoStart`**

Run: `./gradlew :client:runIntegrationTest -PnoStart`

Expected: Connects to existing clients, resets DB via RCON, runs tests again, reports results.

- [ ] **Step 4: Run single test class**

Run: `./gradlew :client:runIntegrationTest -PnoStart -Ptest=CollaborationTest`

Expected: Only CollaborationTest runs, other test classes skipped.

- [ ] **Step 5: Commit any fixes needed**

```bash
git add -A
git commit -m "fix: integration test harness validation fixes"
```

---

### Task 15: Cleanup

Remove old integration test files and update documentation.

**Files:**
- Delete: `client/src/testmod/java/com/disqt/disquests/test/integration/IntegrationPlayerA.java`
- Delete: `client/src/testmod/java/com/disqt/disquests/test/integration/IntegrationPlayerB.java`
- Modify: `docs/in-progress-integration-debug.md`

- [ ] **Step 1: Delete old integration test classes**

Remove `IntegrationPlayerA.java` and `IntegrationPlayerB.java`. They're no longer registered as entrypoints and are superseded by the harness + JUnit test classes.

- [ ] **Step 2: Update debug doc**

Update `docs/in-progress-integration-debug.md` to reflect the new framework: harness architecture, new file locations, new Gradle workflows.

- [ ] **Step 3: Verify everything still compiles and tests pass**

Run: `./gradlew :client:runIntegrationTest`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: remove old integration test classes, update docs for harness framework"
```
