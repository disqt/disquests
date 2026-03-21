# Integration Test Harness Design

## Problem

The current `runIntegrationTest` task is monolithic: it starts the Paper server, launches both Minecraft clients, runs all 5 test journeys, and tears everything down. This makes UI debugging painful because every code change requires a full restart cycle (~40 seconds). Hotswapping or iterative testing is impossible.

## Goal

Decouple client lifecycle from test execution so that:
1. Clients can stay running across multiple test runs
2. Individual tests can be re-run without restarting anything
3. The developer can hotswap UI code or restart a single client, then re-run tests
4. CI still works with a single command (no behavioral change for one-shot runs)

## Architecture

### Client Harness

`FabricClientGameTest.runTest(context)` remains the entry point. A system property `disquests.test.harness=true` switches behavior:

**Normal mode** (no flag): Connects, runs JUnit suite once, then calls `System.exit(0)` to terminate cleanly. FabricClientGameTest would normally crash the game with `FormattedException` when `runTest()` returns -- `System.exit(0)` prevents this by terminating the JVM before the framework's error path runs. Used by CI.

**Harness mode**: Connects to server, stores the `ClientGameTestContext` in a static holder (`TestContext.set(context)`), then loops:
1. Delete stale result files (`results-{a|b}.xml`) from previous run
2. Write `client-{a|b}-ready.done` marker to `integration-sync/`
3. Poll for `run.signal` file via `context.waitFor()` (non-blocking, keeps render thread alive)
4. Launch JUnit programmatically against the test classes for this player
5. Write results to `integration-sync/results-{a|b}.txt` (simple `PASS` / `FAIL: message` format)
6. Loop back to step 1

The harness loop never exits on its own. To stop clients, the Gradle task force-kills the processes (same as today's teardown). In harness mode the task simply skips this step, leaving clients alive.

Both players use the same harness logic. `HarnessPlayerA` and `HarnessPlayerB` are thin wrappers that set the player identity and delegate to shared harness code.

### JUnit 5 Extension

A custom `@IntegrationTest` composed annotation applies an extension that handles all cross-cutting concerns transparently. Test methods contain only test logic.

**Extension responsibilities:**
- **`ParameterResolver`**: injects `ClientGameTestContext` from `TestContext.get()` into test method parameters
- **`BeforeEachCallback`**: clears `ClientCache` client-side, ensures client is connected to the server
- **`ExecutionCondition`**: skips `@PlayerB` methods when running as Player A, and vice versa

Note: DB reset and PhaseSync cleanup happen in the Gradle task (before writing `run.signal`), not in the JUnit extension. This avoids a race condition where both clients' `@BeforeAll` callbacks could reset the DB at different times, wiping each other's state.

**Test method ordering**: Test classes use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` with `@Order` annotations. The `ExecutionCondition` filters by player first, so `@Order` values are scoped per-player, not global. Two methods with `@Order(1)` on different players don't conflict -- each client only sees its own methods. PhaseSync signals define the cross-client ordering, while `@Order` ensures methods on the same client execute in the right sequence if there are multiple per player.

**Cross-client failure propagation**: When a test method fails, the harness writes an `error-{a|b}.done` file to `integration-sync/`. `PhaseSync.waitFor()` checks for the other client's error file on each poll iteration. If found, it throws immediately with `"Other client failed: <message>"` instead of waiting for the full 120-second timeout. This gives fast, clear failure messages.

**Test class example:**

```java
@IntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollaborationTest {

    static final UUID QUEST = UUID.fromString("aaaaaaaa-0003-0000-0000-000000000001");

    @Test @Order(1) @PlayerA
    void createsClosedQuest(ClientGameTestContext context) {
        context.runOnClient(c -> PacketSender.saveQuest(QUEST, "Secret Base", "Top secret", null, false, null, null));
        waitForQuestByTitle(context, "Secret Base", true);
        context.runOnClient(c -> PacketSender.updateVisibility(QUEST, Visibility.CLOSED));
        context.waitFor(c -> {
            var q = ClientCache.getQuestById(QUEST);
            return q != null && q.getVisibility() == Visibility.CLOSED;
        }, TIMEOUT);
        PhaseSync.signal("collab-a-created");
        PhaseSync.waitFor("collab-b-requested", context);

        context.waitFor(c -> !ClientCache.getPendingRequestsForQuest(QUEST).isEmpty(), TIMEOUT);
        var requests = context.computeOnClient(c -> ClientCache.getPendingRequestsForQuest(QUEST));
        UUID requestId = requests.get(0).id();
        context.runOnClient(c -> {
            PacketSender.respondCollaboration(requestId, true);
            ClientCache.removePendingRequest(QUEST, requestId);
        });
        PhaseSync.signal("collab-a-accepted");
    }

    @Test @Order(1) @PlayerB
    void requestsAndJoins(ClientGameTestContext context) {
        PhaseSync.waitFor("collab-a-created", context);
        waitForQuestByTitle(context, "Secret Base", false);
        context.runOnClient(c -> PacketSender.requestCollaboration(QUEST));
        context.waitTicks(20);
        PhaseSync.signal("collab-b-requested");
        PhaseSync.waitFor("collab-a-accepted", context);

        context.waitFor(c -> ClientCache.getMyQuests().stream()
            .anyMatch(q -> q.getId().equals(QUEST)), TIMEOUT);
    }
}
```

### Test File Layout

```
client/src/testmod/java/com/disqt/disquests/test/integration/
  harness/
    HarnessPlayerA.java          -- FabricClientGameTest entry, harness loop
    HarnessPlayerB.java          -- FabricClientGameTest entry, harness loop
    TestContext.java              -- static holder for ClientGameTestContext + player identity
    IntegrationTestExtension.java -- JUnit 5 extension (injection, filtering, cleanup)
    IntegrationTest.java         -- composed @ExtendWith + @TestMethodOrder annotation
    PlayerA.java                 -- @Tag annotation
    PlayerB.java                 -- @Tag annotation
    RconClient.java              -- lightweight RCON client for DB reset
  tests/
    LifecycleTest.java           -- Player A only
    DiscoveryTest.java           -- Player A + B
    CollaborationTest.java       -- Player A + B
    LeaveTest.java               -- Player A + B
    PinPersistenceTest.java      -- Player A only
  PhaseSync.java                 -- file-based coordination (with error-file fast-fail)
  IntegrationTestHelper.java     -- shared utilities (waitForQuestByTitle, etc.)
```

Package: `com.disqt.disquests.test.integration.harness` and `com.disqt.disquests.test.integration.tests`. PhaseSync and IntegrationTestHelper stay at `com.disqt.disquests.test.integration`.

### Server-Side: Debug Reset Command

Add `/disquests reset` to the Paper plugin:
- Drops and recreates all tables (quests, contributors, pinned_quests, collaboration_requests, player_names)
- Sends fresh HANDSHAKE packets to all connected mod players afterward (clears their server-side state and triggers re-sync)
- Only available when `debug: true` in `plugins/Disquests/config.yml` (defaults to `false`)
- Can also be enabled via system property `-Ddisquests.debug=true` as an override
- The `runServer` dev environment and integration test server set this flag
- Console/RCON only -- refuses execution from in-game players

### Gradle Task

Single `runIntegrationTest` task with smart detection and optional flags:

**Properties:**
- `-PnoStart` -- skip launching clients/server, assume already running
- `-Pharness` -- start clients in harness mode (stay alive after tests)
- `-Ptest=CollaborationTest` -- run only a specific test class (default: all)

**Harness property wiring** in Loom run configs:
```kotlin
val harness = providers.gradleProperty("harness")
if (harness.isPresent) vmArg("-Ddisquests.test.harness=true")
```

**Flow:**
1. Clean PhaseSync files (delete all `*.done` files and `run.signal` from `integration-sync/`)
2. Check if server is running (probe port 25565). Start if needed (unless `-PnoStart`).
3. Check for `client-a-ready.done` / `client-b-ready.done` markers. Start clients if needed (unless `-PnoStart`). If `-PnoStart` is set and markers are missing after 10 seconds, fail with `"Clients not running. Start them first or remove -PnoStart."`.
4. Send RCON `/disquests reset` to clean server DB + trigger re-sync. The Gradle task implements RCON inline (Source RCON is a simple TCP protocol: login packet + command packet). This is separate from the testmod's `RconClient.java` since the Gradle JVM cannot access testmod classes.
5. Write `run.signal` file. Format: single line, either `*` (all tests) or a simple class name like `CollaborationTest`. The harness reads this and builds a `ClassNameFilter` for the JUnit `LauncherDiscoveryRequest`.
6. Poll for `results-a.txt` and `results-b.txt` (timeout: 120 seconds)
7. Read result files, print pass/fail summary
8. If not harness mode and clients were started by this task: tear down (force-kill). If harness mode or `-PnoStart`: leave everything running.

**`-PnoStart` implies no teardown** regardless of `-Pharness`. If you didn't start the clients, you don't kill them. Passing both `-PnoStart -Pharness` is valid but redundant -- `-PnoStart` alone is sufficient for re-runs.

**Workflows:**

```bash
# CI / one-shot (identical to current behavior)
./gradlew runIntegrationTest

# Start a debugging session (clients stay alive)
./gradlew runIntegrationTest -Pharness

# Re-run tests after hotswap or code change
./gradlew runIntegrationTest -PnoStart

# Run a single test class
./gradlew runIntegrationTest -PnoStart -Ptest=CollaborationTest
```

### State Cleanup

All state cleanup happens in the Gradle task, before writing `run.signal`:
1. **PhaseSync files**: Gradle task deletes all `*.done` files from `integration-sync/`
2. **Server DB**: Gradle task sends RCON `/disquests reset` (drops+recreates tables, sends fresh handshakes)
3. **Client cache**: JUnit extension's `BeforeEachCallback` calls `ClientCache.clear()` on each client

This avoids race conditions between the two clients' JUnit lifecycles.

### Existing Tests

`QuestScreenTest` and `DisquestsE2ETest` remain unchanged. They already skip when `disquests.test.journey` is set. The new harness entrypoints (`HarnessPlayerA`/`HarnessPlayerB`) replace `IntegrationPlayerA`/`IntegrationPlayerB` in the testmod `fabric.mod.json` entrypoints. The old integration classes can be deleted once the new framework is validated.

### Dependencies

Add to `client/build.gradle.kts`:

```kotlin
dependencies {
    "testmodImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
    "testmodImplementation"("org.junit.platform:junit-platform-launcher:1.11.4")
}
```

These run inside the Minecraft client JVM alongside the Fabric game test framework. No `include` needed since they're test-only (not shipped in the mod JAR).
