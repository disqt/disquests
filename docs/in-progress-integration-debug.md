# Integration Test Debug Status (2026-03-21)

## What Works
- **Quest Lifecycle** (Journey 1): Player A creates, edits, deletes -- PASS
- **Quest Discovery** (Journey 2): A creates OPEN, B discovers + joins -- PASS
- **Collaboration phases 1-2**: A creates CLOSED, B requests access -- PASS
- **Two clients run in parallel** with separate run dirs (`client/run/`, `client/run-b/`)
- **PhaseSync coordination** via `context.waitFor()` (file-based, non-blocking)
- **Sequential execution** of all collaboration phases works perfectly

## What Fails
- **Collaboration phase 3**: Player A waits for `getPendingRequestsForQuest(COLLAB_QUEST)` to be non-empty, times out after 30s
- **Leave Quest phase 3**: Player B waits for quest removal after `leaveQuest()`, times out
- **Pin Persistence phase 1**: Pin assertion (fixed to use `HudPinManager.toggle` but untested in parallel)

## Root Cause Investigation

### Collaboration failure analysis
1. Server's `handleRequestCollaboration` is NEVER called (no debug logs appear)
2. Both clients connect successfully (server logs show both players joining)
3. Sync file `collab-b-requested.done` exists, proving B's code executed past `requestCollaboration()`
4. The packet from B either never reaches the server, or the server silently drops it

### Hypotheses to test (in order)
1. **Visibility not yet CLOSED when B requests**: A signals `collab-a-created` after `waitFor(visibility==CLOSED)` succeeds on the CLIENT, but the server's DB update may lag. B then sends `requestCollaboration` immediately, and the server's `handleRequestCollaboration` returns on line 169 (`visibility != CLOSED`). Fix: add `context.waitTicks(20)` after A's visibility update before signaling.

2. **Quest ID mismatch**: `COLLAB_QUEST` is a hardcoded UUID (`aaaaaaaa-0003-...`). If the server generates a different ID (it shouldn't -- `saveQuest` uses the client-provided ID), the request targets a nonexistent quest. Fix: verify DB has the expected UUID.

3. **isModPlayer check fails for owner**: `Bukkit.getPlayer(ownerUuid)` finds A, but `isModPlayer(A)` returns false because A's plugin channel isn't registered. Unlikely since A already received handshake packets. Fix: add logging to all early returns in `handleRequestCollaboration`.

### Recommended next step
Add logging to EVERY early return in `handleRequestCollaboration`:
```java
if (quest == null) { plugin.getLogger().info("[DEBUG] quest null"); return; }
if (quest.visibility() != Visibility.CLOSED) { plugin.getLogger().info("[DEBUG] not closed: " + quest.visibility()); return; }
if (quest.ownerUuid().equals(player.getUniqueId())) { plugin.getLogger().info("[DEBUG] owner requesting own quest"); return; }
if (dataManager.isContributor(questId, player.getUniqueId())) { plugin.getLogger().info("[DEBUG] already contributor"); return; }
```

Then run the integration suite again and check server logs.

## File Map

| File | Purpose |
|------|---------|
| `client/src/testmod/.../integration/IntegrationPlayerA.java` | All Player A phases |
| `client/src/testmod/.../integration/IntegrationPlayerB.java` | All Player B phases |
| `client/src/testmod/.../integration/PhaseSync.java` | File-based coordination |
| `client/src/testmod/.../integration/IntegrationTestHelper.java` | Shared utilities |
| `client/build.gradle.kts` | `runIntegrationTest` task + `clientGameTestB` run config |
| `paper/.../ServerPacketHandler.java` | Has debug logging in `handleRequestCollaboration` |
| `docs/superpowers/specs/2026-03-21-integration-e2e-tests-design.md` | Design spec |
| `docs/superpowers/plans/2026-03-21-integration-e2e-tests.md` | Implementation plan |

## Bug Fixes Applied (pre-integration)
All committed and deployed:
- TextFieldComponent: force delegate focus sync + charTyped routing via GreedyInputUIComponent
- QuestEntryComponent: pin blocked for non-owner/non-contributor
- QuestScreen: optimistic cache removal on leave, "Corner 2" label
- ContributorScreen: removeChild instead of zero-sizing
- Colors: opaque gradient
- MainScreen: removeChild for hidden buttons
- DisquestsE2ETest: opt-in only (requires server)
