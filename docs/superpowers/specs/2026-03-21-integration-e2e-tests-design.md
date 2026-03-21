# Integration E2E Test Framework Design

## Goal

Test full user journeys with real packet round-trips between a Fabric client and Paper server, verifying that the Disquests protocol, server logic, and client UI work together correctly.

## Constraints

- **MC client identity is immutable per JVM** -- `MinecraftClient.session` is `private final`. Two players requires two JVM launches.
- **Paper dev server** via `./gradlew :paper:runServer`. SQLite DB at `paper/run/plugins/Disquests/disquests.db`.
- **FabricClientGameTest** provides `waitFor(predicate, timeoutTicks)`, `computeOnClient()`, `getInput()`, `setScreen()`.
- **Offline mode** -- server has `online-mode=false`, `enforce-secure-profile=false`. Player UUID = `UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes())`.

## Architecture

### Single Gradle Task

`./gradlew :client:runIntegrationTest` orchestrates everything:

1. Deletes `paper/run/plugins/Disquests/disquests.db` (clean state)
2. Starts Paper dev server (background), waits for ready (polls log for "Done")
3. Runs journey phases sequentially -- each phase is a separate `runClientGameTest` JVM with:
   - Loom `programArgs("--username", "PlayerA")` for player identity
   - `-Ddisquests.test.phase=N` for phase selection
   - `-Ddisquests.test.journey=CollaborationTest` for journey selection
4. Between phases: optionally queries SQLite DB via JDBC for server-side assertions
5. Stops Paper server
6. Reports pass/fail

### Player Identity

Each phase creates a dynamic Loom run configuration with `programArgs("--username", playerName)`. Loom passes `--username` to MC's main class, which sets the Session username. In offline mode, the server derives UUID deterministically:

```java
UUID playerUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
```

### Phase Execution

Each journey has a single test class. The `disquests.test.journey` property selects which class runs (others return immediately). The `disquests.test.phase` property selects which phase within the journey:

```java
public class CollaborationTest implements FabricClientGameTest {
    private static final int TIMEOUT = 30 * 20; // 30 seconds

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!"CollaborationTest".equals(System.getProperty("disquests.test.journey"))) return;
        String phase = System.getProperty("disquests.test.phase");
        switch (phase) {
            case "1" -> ownerCreatesClosedQuest(context);
            case "2" -> discovererRequestsAccess(context);
            case "3" -> ownerAcceptsRequest(context);
        }
    }
}
```

Existing `QuestScreenTest` checks the journey property and returns early during integration runs.

### Connection Flow Per Phase

1. Connect to `localhost:25565` via `ConnectScreen.connect()`
2. `waitFor(client -> client.player != null, TIMEOUT)` -- player spawned
3. `waitFor(client -> ClientSession.isOnServer(), TIMEOUT)` -- Disquests handshake complete (triggers `requestSync()`)
4. `waitTicks(10)` -- allow sync packets to arrive
5. Execute phase-specific steps
6. Disconnect (client exits when test completes)

Default timeout: 30 seconds (30 * 20 ticks). Fails with descriptive error on timeout.

### DB Assertions

Between phases, the Gradle task queries `paper/run/plugins/Disquests/disquests.db` via JDBC. The Paper plugin must enable WAL mode (`PRAGMA journal_mode=WAL`) for concurrent reads while the server holds a write connection.

### Error Handling

- If any phase fails, subsequent phases in that journey are skipped
- Other journeys still run (fail-fast per journey, not globally)
- Paper server is always stopped in a `finally` block
- Failure reports include: journey name, phase number, player, assertion message
- DB file preserved on failure for debugging

## Journeys

### Journey 1: Quest Lifecycle (Single Player)

**Phases:** 1 (Player A only)

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Connect, wait for handshake | `ClientSession.isOnServer()` |
| 2 | `PacketSender.saveQuest(id, "Lifecycle Test", "content", ...)` | `waitFor` quest in myQuests |
| 3 | `PacketSender.updateVisibility(id, OPEN)` | `waitFor` visibility=OPEN in cache |
| 4 | `PacketSender.saveQuest(id, ..., "updated content", ...)` | `waitFor` content updated |
| 5 | `PacketSender.deleteQuest(id)` | `waitFor` quest removed from cache |

**DB check after:** Quest not in `quests` table.

Note: New quests are always created as PRIVATE. Must call `updateVisibility` separately to change.

### Journey 2: Quest Discovery and Join

**Phases:** 2

| Phase | Player | Action | Assertion |
|-------|--------|--------|-----------|
| 1 | A | `saveQuest` + `updateVisibility(OPEN)` "Join Test" | `waitFor` in myQuests, visibility=OPEN |
| 2 | B | Connect, wait for sync | `waitFor` "Join Test" in serverQuests |
| 2 | B | `PacketSender.joinQuest(questId)` | `waitFor` quest in myQuests |

**DB check after phase 2:** B's UUID in `contributors` table.

### Journey 3: Collaboration Request Flow

**Phases:** 3

| Phase | Player | Action | Assertion |
|-------|--------|--------|-----------|
| 1 | A | `saveQuest` + `updateVisibility(CLOSED)` "Secret Base" | `waitFor` in myQuests |
| 2 | B | Connect, see on Quest Board | `waitFor` in serverQuests |
| 2 | B | `PacketSender.requestCollaboration(questId)` | No client assertion |
| -- | DB | Assert collaboration_requests has B's UUID | Row exists |
| 3 | A | Connect, wait for sync | `waitFor` `getPendingCount(questId) > 0` |
| 3 | A | Read request ID from `getPendingRequestsForQuest()` | Request exists |
| 3 | A | `PacketSender.respondCollaboration(requestId, true)` | `waitFor` pending count = 0 |

**DB check after phase 3:** B in `contributors` table. No pending requests.

### Journey 4: Leave Quest

**Phases:** 3

| Phase | Player | Action | Assertion |
|-------|--------|--------|-----------|
| 1 | A | `saveQuest` + `updateVisibility(OPEN)` "Leave Test" | `waitFor` in myQuests |
| 2 | B | Connect, `joinQuest` | `waitFor` in myQuests |
| 3 | B | Connect, `PacketSender.leaveQuest(questId)` | `waitFor` quest removed from myQuests |

**DB check after phase 3:** B not in `contributors`. Quest still exists.

### Journey 5: Pin Persistence

**Phases:** 2 (same player, two sessions)

| Phase | Player | Action | Assertion |
|-------|--------|--------|-----------|
| 1 | A | `saveQuest` "Pin Test", `pinQuest(questId, true)` | `isPinned(questId)` |
| 2 | A | Connect (new JVM, same username) | `waitFor` handshake, `isPinned(questId)` still true |

## Shared Test Utilities

```java
public class IntegrationTestHelper {
    static final int TIMEOUT = 30 * 20;
    static void connectAndWait(ClientGameTestContext context);
    static Quest waitForQuestByTitle(ClientGameTestContext context, String title, boolean myQuests);
    static UUID offlineUuid(String playerName);
    static boolean shouldSkip(String journeyName);
}
```

## File Structure

```
client/src/testmod/java/com/disqt/disquests/test/
  QuestScreenTest.java              -- existing UI tests (unchanged)
  integration/
    IntegrationTestHelper.java
    QuestLifecycleTest.java         -- Journey 1
    QuestDiscoveryTest.java         -- Journey 2
    CollaborationTest.java          -- Journey 3
    LeaveQuestTest.java             -- Journey 4
    PinPersistenceTest.java         -- Journey 5
```

## Prerequisites

- `server.properties`: `online-mode=false`, `enforce-secure-profile=false`
- `DataManager.java`: add `PRAGMA journal_mode=WAL` for concurrent DB reads
- Paper dev server plugin jar includes latest Disquests changes

## Cleanup

Before each full integration run, delete `paper/run/plugins/Disquests/disquests.db`. Plugin recreates empty tables on startup.

## Non-Goals

- UI rendering fidelity (covered by QuestScreenTest)
- More than 2 concurrent players
- Network failure/timeout scenarios
- Load testing
