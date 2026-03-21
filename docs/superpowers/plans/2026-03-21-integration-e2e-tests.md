# Integration E2E Test Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an integration test framework that verifies full Disquests user journeys with real packet round-trips between a Fabric client and Paper server.

**Architecture:** One Gradle task (`runIntegrationTest`) orchestrates: clean DB, start Paper server, run N sequential client phases (each a separate JVM with a specific player identity and phase number), query DB between phases, stop server. Five journeys cover quest lifecycle, discovery, collaboration, leave, and pin persistence.

**Tech Stack:** Java 21, Fabric 1.21.11, FabricClientGameTest, Gradle Kotlin DSL, SQLite JDBC, Paper dev server

**Spec:** `docs/superpowers/specs/2026-03-21-integration-e2e-tests-design.md`

---

## Task 1: Enable WAL Mode in DataManager

**Files:**
- Modify: `paper/src/main/java/com/disqt/disquests/paper/DataManager.java`

The Gradle task needs to read the DB while the server is running. SQLite's default journal mode blocks concurrent readers during writes. WAL mode allows concurrent reads.

- [ ] **Step 1:** Add `PRAGMA journal_mode=WAL` after existing `PRAGMA foreign_keys = ON` in `DataManager.initialize()`:

```java
stmt.executeUpdate("PRAGMA foreign_keys = ON");
stmt.executeUpdate("PRAGMA journal_mode = WAL");
```

- [ ] **Step 2:** Verify `enforce-secure-profile=false` in `paper/run/server.properties` (offline mode clients need this):

```bash
grep "enforce-secure-profile" paper/run/server.properties
```

If `true`, change to `false`.

- [ ] **Step 3:** Build: `./gradlew :paper:build`
- [ ] **Step 4:** Commit

---

## Task 2: IntegrationTestHelper

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/IntegrationTestHelper.java`

Shared utilities for all integration test journeys.

- [ ] **Step 1:** Create the helper class:

```java
package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class IntegrationTestHelper {

    public static final int TIMEOUT = 30 * 20; // 30 seconds in ticks

    public static boolean shouldSkip(String journeyName) {
        String selected = System.getProperty("disquests.test.journey");
        return selected != null && !selected.equals(journeyName);
    }

    public static void connectAndWait(ClientGameTestContext context) {
        String host = System.getProperty("disquests.test.server.host", "localhost");
        int port = Integer.parseInt(System.getProperty("disquests.test.server.port", "25565"));
        String address = host + ":" + port;

        context.runOnClient(client -> {
            ServerAddress serverAddress = ServerAddress.parse(address);
            ServerInfo serverInfo = new ServerInfo("Integration Test", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client, serverAddress, serverInfo, false, null);
        });

        context.waitFor(client -> client.player != null, TIMEOUT);
        context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);
        context.waitTicks(10); // allow sync packets to arrive
    }

    public static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
        context.waitForScreen(TitleScreen.class);
    }

    public static Quest waitForQuestByTitle(ClientGameTestContext context, String title, boolean myQuests) {
        context.waitFor(client -> {
            var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
            return list.stream().anyMatch(q -> title.equals(q.getTitle()));
        }, TIMEOUT);

        return context.computeOnClient(client -> {
            var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
            return list.stream().filter(q -> title.equals(q.getTitle())).findFirst().orElse(null);
        });
    }

    public static void waitForQuestRemoved(ClientGameTestContext context, UUID questId) {
        context.waitFor(client ->
            ClientCache.getMyQuests().stream().noneMatch(q -> q.getId().equals(questId)),
            TIMEOUT);
    }

    public static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 2:** Build: `./gradlew :client:build`
- [ ] **Step 3:** Commit

---

## Task 3: Quest Lifecycle Journey (Journey 1)

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/QuestLifecycleTest.java`

Single-player journey: create quest, update visibility, edit content, delete.

- [ ] **Step 1:** Create the test class:

```java
package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

public class QuestLifecycleTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("QuestLifecycleTest")) return;

        connectAndWait(context);

        UUID questId = UUID.randomUUID();

        // Create quest (server always creates as PRIVATE)
        context.runOnClient(client ->
            PacketSender.saveQuest(questId, "Lifecycle Test", "initial content", null, false, null, null));
        var quest = waitForQuestByTitle(context, "Lifecycle Test", true);
        if (quest == null) throw new AssertionError("Quest not created");

        // Update visibility to OPEN
        context.runOnClient(client -> PacketSender.updateVisibility(questId, Visibility.OPEN));
        context.waitFor(client -> {
            var q = ClientCache.getQuestById(questId);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);

        // Edit content
        context.runOnClient(client ->
            PacketSender.saveQuest(questId, "Lifecycle Test", "updated content", null, false, null, null));
        context.waitFor(client -> {
            var q = ClientCache.getQuestById(questId);
            return q != null && "updated content".equals(q.getContent());
        }, TIMEOUT);

        // Delete
        context.runOnClient(client -> PacketSender.deleteQuest(questId));
        waitForQuestRemoved(context, questId);

        disconnect(context);
    }
}
```

- [ ] **Step 2:** Build: `./gradlew :client:build`
- [ ] **Step 3:** Commit

---

## Task 4: Quest Discovery and Join Journey (Journey 2)

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/QuestDiscoveryTest.java`

Two-phase journey: Player A creates OPEN quest, Player B discovers and joins.

- [ ] **Step 1:** Create the test class:

```java
package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

public class QuestDiscoveryTest implements FabricClientGameTest {

    // Quest ID must be deterministic so Phase 2 can find it
    private static final UUID QUEST_ID = UUID.fromString("aaaaaaaa-0002-0000-0000-000000000001");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("QuestDiscoveryTest")) return;
        String phase = System.getProperty("disquests.test.phase", "1");

        connectAndWait(context);

        switch (phase) {
            case "1" -> phaseA_createOpenQuest(context);
            case "2" -> phaseB_discoverAndJoin(context);
        }

        disconnect(context);
    }

    private void phaseA_createOpenQuest(ClientGameTestContext context) {
        context.runOnClient(client ->
            PacketSender.saveQuest(QUEST_ID, "Join Test", "Come join!", null, false, null, null));
        waitForQuestByTitle(context, "Join Test", true);

        context.runOnClient(client -> PacketSender.updateVisibility(QUEST_ID, Visibility.OPEN));
        context.waitFor(client -> {
            var q = ClientCache.getQuestById(QUEST_ID);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);
    }

    private void phaseB_discoverAndJoin(ClientGameTestContext context) {
        // Wait for quest to appear on Quest Board
        var quest = waitForQuestByTitle(context, "Join Test", false);
        if (quest == null) throw new AssertionError("Join Test not found on Quest Board");

        // Join the quest
        context.runOnClient(client -> PacketSender.joinQuest(QUEST_ID));

        // Should appear in my quests after server processes JOIN_QUEST
        context.waitFor(client ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST_ID)),
            TIMEOUT);
    }
}
```

- [ ] **Step 2:** Build: `./gradlew :client:build`
- [ ] **Step 3:** Commit

---

## Task 5: Collaboration Request Journey (Journey 3)

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/CollaborationTest.java`

Three-phase journey: A creates CLOSED quest, B requests access, A accepts.

- [ ] **Step 1:** Create the test class:

```java
package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

public class CollaborationTest implements FabricClientGameTest {

    private static final UUID QUEST_ID = UUID.fromString("aaaaaaaa-0003-0000-0000-000000000001");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("CollaborationTest")) return;
        String phase = System.getProperty("disquests.test.phase", "1");

        connectAndWait(context);

        switch (phase) {
            case "1" -> phaseA_createClosedQuest(context);
            case "2" -> phaseB_requestAccess(context);
            case "3" -> phaseA_acceptRequest(context);
        }

        disconnect(context);
    }

    private void phaseA_createClosedQuest(ClientGameTestContext context) {
        context.runOnClient(client ->
            PacketSender.saveQuest(QUEST_ID, "Secret Base", "Top secret", null, false, null, null));
        waitForQuestByTitle(context, "Secret Base", true);

        context.runOnClient(client -> PacketSender.updateVisibility(QUEST_ID, Visibility.CLOSED));
        context.waitFor(client -> {
            var q = ClientCache.getQuestById(QUEST_ID);
            return q != null && q.getVisibility() == Visibility.CLOSED;
        }, TIMEOUT);
    }

    private void phaseB_requestAccess(ClientGameTestContext context) {
        var quest = waitForQuestByTitle(context, "Secret Base", false);
        if (quest == null) throw new AssertionError("Secret Base not found on Quest Board");

        context.runOnClient(client -> PacketSender.requestCollaboration(QUEST_ID));
        context.waitTicks(20); // Allow server to process
    }

    private void phaseA_acceptRequest(ClientGameTestContext context) {
        // Wait for pending request to sync
        context.waitFor(client -> ClientCache.getPendingCount(QUEST_ID) > 0, TIMEOUT);

        // Get request ID
        var requests = context.computeOnClient(client ->
            ClientCache.getPendingRequestsForQuest(QUEST_ID));
        if (requests.isEmpty()) throw new AssertionError("No pending requests found");

        UUID requestId = requests.get(0).id();

        // Accept
        context.runOnClient(client -> PacketSender.respondCollaboration(requestId, true));
        context.waitFor(client -> ClientCache.getPendingCount(QUEST_ID) == 0, TIMEOUT);
    }
}
```

- [ ] **Step 2:** Build: `./gradlew :client:build`
- [ ] **Step 3:** Commit

---

## Task 6: Leave Quest and Pin Persistence Journeys (Journeys 4 and 5)

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/LeaveQuestTest.java`
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/PinPersistenceTest.java`

- [ ] **Step 1:** Create `LeaveQuestTest.java` (3 phases: A creates OPEN, B joins, B leaves):

```java
package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.UUID;

import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;

public class LeaveQuestTest implements FabricClientGameTest {

    private static final UUID QUEST_ID = UUID.fromString("aaaaaaaa-0004-0000-0000-000000000001");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (shouldSkip("LeaveQuestTest")) return;
        String phase = System.getProperty("disquests.test.phase", "1");

        connectAndWait(context);

        switch (phase) {
            case "1" -> phaseA_createOpenQuest(context);
            case "2" -> phaseB_joinQuest(context);
            case "3" -> phaseB_leaveQuest(context);
        }

        disconnect(context);
    }

    private void phaseA_createOpenQuest(ClientGameTestContext context) {
        context.runOnClient(client ->
            PacketSender.saveQuest(QUEST_ID, "Leave Test", "Try leaving", null, false, null, null));
        waitForQuestByTitle(context, "Leave Test", true);
        context.runOnClient(client -> PacketSender.updateVisibility(QUEST_ID, Visibility.OPEN));
        context.waitFor(client -> {
            var q = ClientCache.getQuestById(QUEST_ID);
            return q != null && q.getVisibility() == Visibility.OPEN;
        }, TIMEOUT);
    }

    private void phaseB_joinQuest(ClientGameTestContext context) {
        waitForQuestByTitle(context, "Leave Test", false);
        context.runOnClient(client -> PacketSender.joinQuest(QUEST_ID));
        context.waitFor(client ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST_ID)),
            TIMEOUT);
    }

    private void phaseB_leaveQuest(ClientGameTestContext context) {
        context.waitFor(client ->
            ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(QUEST_ID)),
            TIMEOUT);
        context.runOnClient(client -> PacketSender.leaveQuest(QUEST_ID));
        waitForQuestRemoved(context, QUEST_ID);
    }
}
```

- [ ] **Step 2:** Create `PinPersistenceTest.java` (2 phases, same player):

```java
package com.disqt.disquests.test.integration;

import com.disqt.disquests.client.ClientCache;
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

        context.runOnClient(client -> PacketSender.pinQuest(QUEST_ID, true));
        context.waitTicks(10);

        boolean pinned = ClientSession.isPinned(QUEST_ID);
        if (!pinned) throw new AssertionError("Quest should be pinned after PIN_QUEST");
    }

    private void phaseA_verifyPinRestored(ClientGameTestContext context) {
        // After reconnect, handshake includes pinnedIds
        context.waitFor(client -> ClientSession.isPinned(QUEST_ID), TIMEOUT);
    }
}
```

- [ ] **Step 3:** Build: `./gradlew :client:build`
- [ ] **Step 4:** Commit

---

## Task 7: Register Integration Tests as Entrypoints

**Files:**
- Modify: `client/src/testmod/resources/fabric.mod.json`

- [ ] **Step 1:** Add all 5 integration test classes to the entrypoints:

```json
{
  "schemaVersion": 1,
  "id": "disquests-test",
  "version": "1.0.0",
  "name": "Disquests E2E Tests",
  "environment": "client",
  "entrypoints": {
    "fabric-client-gametest": [
      "com.disqt.disquests.test.QuestScreenTest",
      "com.disqt.disquests.test.DisquestsE2ETest",
      "com.disqt.disquests.test.integration.QuestLifecycleTest",
      "com.disqt.disquests.test.integration.QuestDiscoveryTest",
      "com.disqt.disquests.test.integration.CollaborationTest",
      "com.disqt.disquests.test.integration.LeaveQuestTest",
      "com.disqt.disquests.test.integration.PinPersistenceTest"
    ]
  },
  "depends": {
    "disquests": "*",
    "fabric-client-gametest-api-v1": "*"
  }
}
```

- [ ] **Step 2:** Build: `./gradlew :client:build`
- [ ] **Step 3:** Commit

---

## Task 8: Gradle Orchestration Task

**Files:**
- Modify: `client/build.gradle.kts`

This is the core orchestration: start server, run phases with different player identities, stop server.

- [ ] **Step 1:** Add dynamic Loom run configs for integration phases and the orchestration task:

```kotlin
// After the existing clientGameTest run config, add:

// Integration test run configs are created dynamically by the orchestration task
// They use --username to set player identity and system properties for phase/journey selection

data class JourneyPhase(val journey: String, val phase: Int, val player: String)

tasks.register("runIntegrationTest") {
    group = "verification"
    description = "Run integration E2E tests against a Paper dev server"

    dependsOn(":paper:build", ":client:build")

    doLast {
        // Step 1: Delete old DB
        val db = file("../paper/run/plugins/Disquests/disquests.db")
        if (db.exists()) {
            db.delete()
            logger.lifecycle("Deleted old test database")
        }

        // Step 2: Start Paper server
        val serverProcess = ProcessBuilder(
            "java", "-Xmx1G",
            "-jar", file("../paper/run/paper.jar").absolutePath,
            "--nogui"
        ).directory(file("../paper/run"))
            .redirectErrorStream(true)
            .start()

        val serverLog = file("../paper/run/logs/latest.log")

        try {
            // Wait for server ready
            logger.lifecycle("Waiting for Paper server to start...")
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 60000) {
                if (serverLog.exists() && serverLog.readText().contains("Done (")) {
                    logger.lifecycle("Paper server ready!")
                    break
                }
                Thread.sleep(1000)
            }

            // Step 3: Define journeys
            val journeys = listOf(
                // Journey 1: Quest Lifecycle (single player, single phase)
                listOf(JourneyPhase("QuestLifecycleTest", 1, "IntTestPlayerA")),
                // Journey 2: Discovery & Join
                listOf(
                    JourneyPhase("QuestDiscoveryTest", 1, "IntTestPlayerA"),
                    JourneyPhase("QuestDiscoveryTest", 2, "IntTestPlayerB"),
                ),
                // Journey 3: Collaboration
                listOf(
                    JourneyPhase("CollaborationTest", 1, "IntTestPlayerA"),
                    JourneyPhase("CollaborationTest", 2, "IntTestPlayerB"),
                    JourneyPhase("CollaborationTest", 3, "IntTestPlayerA"),
                ),
                // Journey 4: Leave Quest
                listOf(
                    JourneyPhase("LeaveQuestTest", 1, "IntTestPlayerA"),
                    JourneyPhase("LeaveQuestTest", 2, "IntTestPlayerB"),
                    JourneyPhase("LeaveQuestTest", 3, "IntTestPlayerB"),
                ),
                // Journey 5: Pin Persistence
                listOf(
                    JourneyPhase("PinPersistenceTest", 1, "IntTestPlayerA"),
                    JourneyPhase("PinPersistenceTest", 2, "IntTestPlayerA"),
                ),
            )

            // Step 4: Run each journey
            for (journey in journeys) {
                val journeyName = journey.first().journey
                logger.lifecycle("=== Journey: $journeyName ===")

                var journeyFailed = false
                for (jp in journey) {
                    if (journeyFailed) {
                        logger.lifecycle("  Skipping phase ${jp.phase} (previous phase failed)")
                        continue
                    }

                    logger.lifecycle("  Phase ${jp.phase} as ${jp.player}...")

                    val result = exec {
                        commandLine(
                            "cmd", "/c", "gradlew.bat", ":client:runClientGameTest",
                            "--no-daemon",
                            "-Ddisquests.test.journey=${jp.journey}",
                            "-Ddisquests.test.phase=${jp.phase}",
                            "-PtestUsername=${jp.player}"
                        )
                        isIgnoreExitValue = true
                    }

                    if (result.exitValue != 0) {
                        logger.error("  FAILED: ${jp.journey} phase ${jp.phase} as ${jp.player}")
                        journeyFailed = true
                    } else {
                        logger.lifecycle("  PASSED")
                    }
                }
            }

        } finally {
            // Step 5: Stop server
            serverProcess.destroyForcibly()
            logger.lifecycle("Paper server stopped")
        }
    }
}
```

- [ ] **Step 2:** Add the `testUsername` property handling to pass `--username` to the client game test. Modify the existing `clientGameTest` run config:

```kotlin
create("clientGameTest") {
    client()
    configName = "Client Game Test"
    source(sourceSets.getByName("testmod"))
    vmArg("-Dfabric.client.gametest")
    vmArg("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
    vmArg("-Ddisquests.test.server.host=localhost")
    vmArg("-Ddisquests.test.server.port=25565")
    vmArg("-Ddisquests.test.rcon.port=25575")
    vmArg("-Ddisquests.test.rcon.password=testpassword")
    // Pass journey/phase system properties if set
    val journey = providers.systemProperty("disquests.test.journey")
    val phase = providers.systemProperty("disquests.test.phase")
    if (journey.isPresent) vmArg("-Ddisquests.test.journey=${journey.get()}")
    if (phase.isPresent) vmArg("-Ddisquests.test.phase=${phase.get()}")
    // Pass custom username for integration test player identity
    val username = providers.gradleProperty("testUsername")
    if (username.isPresent) programArgs("--username", username.get())
}
```

- [ ] **Step 3:** Ensure `enforce-secure-profile=false` in `paper/run/server.properties`
- [ ] **Step 4:** Build: `./gradlew :client:build`
- [ ] **Step 5:** Commit

---

## Task 9: Guard Existing Tests During Integration Runs

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/DisquestsE2ETest.java`

When `disquests.test.journey` is set, existing tests should skip so only the selected integration journey runs.

- [ ] **Step 1:** Add skip guard at top of `QuestScreenTest.runTest()`:

```java
@Override
public void runTest(ClientGameTestContext context) {
    // Skip during integration test runs (journey property selects specific test class)
    if (System.getProperty("disquests.test.journey") != null) return;

    ClientSession.joinServer(null, 0, new ArrayList<>(), TEST_PLAYER_UUID);
    // ... rest unchanged
}
```

- [ ] **Step 2:** Add same guard to `DisquestsE2ETest.runTest()`:

```java
@Override
public void runTest(ClientGameTestContext context) {
    if (System.getProperty("disquests.test.journey") != null) return;
    // ... rest unchanged
}
```

- [ ] **Step 3:** Verify existing tests still pass: `./gradlew :client:runClientGameTest`
- [ ] **Step 4:** Commit

---

## Task 10: End-to-End Verification

- [ ] **Step 1:** Start Paper server: `./gradlew :paper:runServer`
- [ ] **Step 2:** Run Journey 1 standalone to verify the framework works:

```bash
./gradlew :client:runClientGameTest -Ddisquests.test.journey=QuestLifecycleTest -Ddisquests.test.phase=1 -PtestUsername=IntTestPlayerA
```

- [ ] **Step 3:** Run existing tests still pass without journey property: `./gradlew :client:runClientGameTest`
- [ ] **Step 4:** Run full integration suite: `./gradlew :client:runIntegrationTest`
- [ ] **Step 5:** Commit final state
- [ ] **Step 6:** Update `docs/superpowers/plans/2026-03-21-integration-e2e-tests.md` to mark all tasks complete

---

## Summary

| Task | Description | Depends On |
|------|-------------|-----------|
| 1 | WAL mode + server config | -- |
| 2 | IntegrationTestHelper | -- |
| 3 | QuestLifecycleTest (Journey 1) | 2 |
| 4 | QuestDiscoveryTest (Journey 2) | 2 |
| 5 | CollaborationTest (Journey 3) | 2 |
| 6 | LeaveQuestTest + PinPersistenceTest (Journeys 4, 5) | 2 |
| 7 | Register entrypoints | 3, 4, 5, 6 |
| 8 | Gradle orchestration task | 1, 7 |
| 9 | Guard existing tests | 7 |
| 10 | End-to-end verification | All |
