# Integration Test Harness Status (2026-03-21)

## Status: ALL PASSING (JUnit 5 Harness)

12 tests total: Player A runs 9, Player B runs 3. All pass in one-shot mode.

## Architecture

The integration test framework uses JUnit 5 running inside the Minecraft client JVM:

- **Harness entry points**: `HarnessPlayerA`/`HarnessPlayerB` (FabricClientGameTest wrappers)
- **JUnit extension**: `IntegrationTestExtension` handles context injection, player filtering, cache cleanup
- **PhaseSync**: File-based coordination between clients with cross-client error propagation
- **Test classes**: 5 classes in `tests/` package, each self-contained with `@IntegrationTest` annotation

## Test Classes

| Class | Tests (A) | Tests (B) | Description |
|-------|-----------|-----------|-------------|
| LifecycleTest | 4 | 0 | Create, edit visibility, edit content, delete |
| DiscoveryTest | 1 | 1 | A creates OPEN, B discovers and joins |
| CollaborationTest | 1 | 1 | A creates CLOSED, B requests, A accepts |
| LeaveTest | 1 | 1 | A creates OPEN, B joins then leaves |
| PinPersistenceTest | 2 | 0 | Pin quest, disconnect/reconnect, verify |

## Gradle Workflows

```bash
# CI / one-shot (runs everything, tears down)
./gradlew :client:runIntegrationTest

# Start debugging session (clients stay alive)
./gradlew :client:runIntegrationTest -Pharness

# Re-run tests against existing clients
./gradlew :client:runIntegrationTest -PnoStart

# Run single test class
./gradlew :client:runIntegrationTest -PnoStart -PtestFilter=CollaborationTest
```

## File Map

| File | Purpose |
|------|---------|
| `client/src/testmod/.../harness/HarnessPlayerA.java` | FabricClientGameTest entry, harness loop |
| `client/src/testmod/.../harness/HarnessPlayerB.java` | FabricClientGameTest entry, harness loop |
| `client/src/testmod/.../harness/TestContext.java` | Static holder for ClientGameTestContext |
| `client/src/testmod/.../harness/IntegrationTestExtension.java` | JUnit 5 extension |
| `client/src/testmod/.../harness/IntegrationTest.java` | Composed annotation |
| `client/src/testmod/.../harness/PlayerA.java` | @Tag annotation |
| `client/src/testmod/.../harness/PlayerB.java` | @Tag annotation |
| `client/src/testmod/.../harness/RconClient.java` | Source RCON client |
| `client/src/testmod/.../tests/*.java` | 5 JUnit test classes |
| `client/src/testmod/.../PhaseSync.java` | File-based coordination |
| `client/src/testmod/.../IntegrationTestHelper.java` | Shared utilities |
| `client/build.gradle.kts` | Gradle task + Loom run configs |
| `paper/.../Commands.java` | `/disquests reset` command |

## Server-Side Reset

`/disquests reset` (console/RCON only, debug mode only):
- Deletes all rows from all 5 tables
- Resends handshakes to all connected mod players
- Enabled via `debug: true` in config.yml or `-Ddisquests.debug=true` system property
