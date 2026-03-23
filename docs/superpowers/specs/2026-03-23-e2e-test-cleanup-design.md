# E2E Test Code Cleanup

**Date:** 2026-03-23
**Status:** Approved

## Goal

Improve readability, reduce duplication, and apply SOLID principles to the E2E test infrastructure (~2200 lines across 21 files). No behavior changes -- pure structural refactoring.

## Changes

### 1. Timeout readability: `seconds()` helper

Add `seconds(int)` to `UIActions`:

```java
public static int seconds(int s) { return s * 20; }
```

Replace all `N * 20` tick calculations across the codebase:
- `UIActions.CONNECT_TIMEOUT = seconds(30)`
- `UIActions.TIMEOUT = seconds(10)`
- `PhaseSync.waitFor()` uses `seconds(15)`
- `HarnessPlayerA/B` exit barriers use `seconds(15)`

### 2. Harness consolidation: `HarnessCommon`

New `HarnessCommon` class parameterized by role string (`"a"` / `"b"`).

Contains all shared logic:
- `run(context, role)` -- entry point (connect, harness vs one-shot, exit barrier)
- `runJUnitSuite(role, testsPackage, filter)` -- JUnit launcher + result capture with stack traces
- `harnessLoop(context, role)` -- signal-based re-run loop
- `writeResult(role, result)` -- file output

`HarnessPlayerA` and `HarnessPlayerB` become one-liner delegates:

```java
public class HarnessPlayerA implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        HarnessCommon.run(context, "a");
    }
}
```

### 3. Root component lookup helper

Extract the repeated `DisquestsBaseScreen -> root -> childById` pattern (~20 occurrences) into UIActions:

```java
/** Find a component by ID on the current Disquests screen. Throws if not found. */
public static <T extends UIComponent> T findComponent(ClientGameTestContext context, Class<T> type, String id)

/** Find a component, returning null if not found (for existence checks). */
@Nullable
public static <T extends UIComponent> T findComponentOrNull(ClientGameTestContext context, Class<T> type, String id)
```

Simplifies `click()`, `clickEntry()`, `clickEntryByTitle()`, all assertion methods, and inline journey code.

### 4. Quest cache lookup helpers

Extract repeated stream chains into UIActions:

```java
/** Wait for a quest matching title to satisfy a condition in the cache. */
public static void waitForQuestCondition(
    ClientGameTestContext context, String title, boolean myQuests, Predicate<Quest> condition)
```

Replaces the 3 inline `stream().filter(title).findFirst().map(predicate)` blocks in TwoPlayerJourneys.

### 5. Magic number cleanup

Name the following constants:

| Current | Named constant | Location |
|---------|---------------|----------|
| `10.0` (pin icon width) | `PIN_ICON_CLICK_OFFSET_X` | UIActions |
| `19.0` (pin icon Y) | `PIN_ICON_CLICK_OFFSET_Y` | UIActions |
| `40` (RCON wait ticks) | `seconds(2)` | UIActions.resetServerAndSync |
| `10` (post-spawn ticks) | `seconds(1)` with `waitTicks(seconds(1))` or keep as `waitTicks(10)` with comment | UIActions.connectAndWait |

## Out of scope

- **waitTicks audit**: Removing or reducing baked-in `waitTicks()` from helpers. Separate effort with before/after timing.
- **Journey test refactoring**: No changes to test logic or flow, only to how helpers are called.
- **New test coverage**: No new tests added.

## Verification

Run full `runIntegrationTest` suite before and after. All 48 tests (40 solo + 8 duo) must pass.
