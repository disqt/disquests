# E2E Test Coverage Redesign

**Date:** 2026-03-22
**Goal:** Reach 80% line coverage via UX-driven E2E tests with real Paper server
**Current:** 42.9% line coverage (1231/2868 lines)

## Decision Summary

- **Replace** all existing tests (20 QuestScreenTest + 15 integration tests) with UX journey tests
- **All tests server-connected** via `runIntegrationTest` (Paper server auto-started)
- **Custom Given/When/Then/And DSL** -- 4 logging methods, zero dependencies
- **Journey-per-file** with `@Order`ed steps building on state
- **Single client** for solo journeys, **two clients** for collaboration
- **Trust hierarchy:** UI state > component state > debug logs > cache/network state
- **JaCoCo coverage** added to `runIntegrationTest`

## BDD DSL

Four static methods in a `BDD` utility class, imported statically into all tests:

```java
public final class BDD {
    private static final Logger LOG = LoggerFactory.getLogger("Disquests/E2E");

    public static void given(String description) {
        LOG.info("  GIVEN {}", description);
    }
    public static void when(String description) {
        LOG.info("  WHEN {}", description);
    }
    public static void then(String description) {
        LOG.info("  THEN {}", description);
    }
    public static void and(String description) {
        LOG.info("    AND {}", description);
    }
}
```

Test output reads:

```
=== Quest Lifecycle Journey ===
  GIVEN player is connected and on My Quests tab
  WHEN player clicks New Quest
    AND player types title "Build a castle"
    AND player clicks Save
  THEN quest appears in My Quests with title "Build a castle"
```

## Infrastructure Changes

### JaCoCo on runIntegrationTest

Add JaCoCo agent to integration test client JVM args (same pattern as `runClientGameTest -Pcoverage`). Write to `client/build/jacoco/integrationTest.exec`. Add `jacocoIntegrationTestReport` Gradle task.

### RCON reset between journeys

Each journey test class calls `rcon.send("disquests reset")` in `@BeforeAll` to wipe server state. Within a journey, ordered steps build on previous state.

### Remove runClientGameTest

Delete `QuestScreenTest.java` and `DisquestsE2ETest.java`. Remove or alias the `runClientGameTest` Gradle task. All E2E coverage comes from `runIntegrationTest`.

### Replace existing integration tests

Rewrite the 5 current test classes (LifecycleTest, DiscoveryTest, CollaborationTest, LeaveTest, PinPersistenceTest) as UX-driven journeys that interact through the UI.

## Trust Hierarchy

Tests verify results through the highest-trust signal available:

1. **UI state after interaction** (highest) -- clicked Save, screen changed to view mode, title label shows edited value. If the user would see it work, the test sees it work.
2. **UI component state** -- button became disabled, field contains text, entry appeared in list, screen auto-closed.
3. **Debug logs** -- via `TestLogCapture`. Useful for diagnosing failures, secondary to UI.
4. **Cache/network state** (lowest) -- only when UI can't observe the result (e.g., verifying a packet was sent).

Example step assertion:

```java
when("player clicks Save");
    click(ctx, "save-button");
    waitForScreen(ctx, QuestScreen.class);

then("quest title shows the edited value");
    assertComponent(ctx, "title-label",
        label -> label.getText().equals("Build a castle"));
and("edit button is visible");
    assertComponent(ctx, "edit-button", Component::isPresent);
```

## Interaction Helpers

All UI interactions use GLFW physical input (TestInput), never direct method calls:

- `click(ctx, componentId)` -- finds component by XML id, computes center, uses `setCursorPos` + `pressMouse`
- `type(ctx, componentId, text)` -- clicks field to focus, then types via key events
- `waitForScreen(ctx, screenClass)` -- polls until screen matches, timeout fails
- `assertComponent(ctx, componentId, predicate)` -- finds component, runs assertion
- `waitForCondition(predicate)` -- polls with timeout for async state (server round-trips)

## Journey Map

### Single-Player Journeys (PlayerA only)

#### 1. QuestLifecycleJourney

Create, edit, and delete a quest through the UI.

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Open MainScreen, My Quests tab | MainScreen displayed, empty list |
| 2 | Click "New Quest" | QuestScreen opens in edit mode, empty fields |
| 3 | Type title "Lifecycle Test" | Title field shows text |
| 4 | Type content "Initial content" | Content field shows text |
| 5 | Click Save | View mode, title label shows "Lifecycle Test" |
| 6 | Click Close, return to MainScreen | Entry visible in My Quests |
| 7 | Select entry, click Open | QuestScreen view mode |
| 8 | Click Edit | Edit mode, fields populated |
| 9 | Change title to "Updated Title" | Title field updated |
| 10 | Click Save | View mode, title shows "Updated Title" |
| 11 | Click Delete | ConfirmScreen appears |
| 12 | Click Yes | MainScreen, quest removed from list |

**Covers:** screen (edit/view modes, ConfirmScreen), widget (text fields), network (save/delete packets)

#### 2. QuestContentJourney

Markdown editing and rendering.

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Create quest, enter edit mode | Edit mode open |
| 2 | Type markdown content (headings, bold, task list, link) | Content field shows raw markdown |
| 3 | Save | View mode |
| 4 | Verify markdown renders (heading scaled, bold styled) | MarkdownWidget renders styled content |
| 5 | Click task list checkbox | Checkbox toggles, content updated |
| 6 | Verify formatting panel visible in edit mode | Panel component present and non-zero size |

**Covers:** markdown (MarkdownRenderer, RenderedLine), widget (MarkdownWidget checkbox), screen (formatting panel)

#### 3. CoordinatesJourney

Coordinate input, region toggle, map cycling.

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Create quest, enter edit mode | Edit mode open |
| 2 | Enter X/Y/Z coordinates in fields | Fields show values |
| 3 | Save, verify coords display in view mode | Coordinates label shows values |
| 4 | Edit, toggle Region on | Corner 2 row appears |
| 5 | Enter corner 2 coordinates | Fields show values |
| 6 | Save, verify region display | Region label shows both corners |
| 7 | Edit, cycle Map button through all values | Button text changes: any -> overworld -> nether -> end |
| 8 | Save, verify map display | Map label shows selected value |
| 9 | Edit, click Clear | All coordinate fields empty |
| 10 | Save, verify no coords in view | Coordinates section hidden |

**Covers:** screen (coord fields, region toggle, map cycle, clear), view mode metadata

#### 4. SearchAndFilterJourney

MainScreen tabs, filters, and search.

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Create 3 quests with distinct titles, set visibility: PRIVATE, CLOSED, OPEN | Quests in My Quests |
| 2 | Switch to Quest Board tab | Filter row appears, OPEN quest visible |
| 3 | Click "Open" filter | Only OPEN quest shown |
| 4 | Click "Closed" filter | Only CLOSED quest shown |
| 5 | Click "All" filter | Both OPEN and CLOSED shown |
| 6 | Type search term matching one quest | Only matching quest shown |
| 7 | Clear search | All quests return |
| 8 | Switch back to My Quests tab | Filter row hidden, all 3 quests shown |

**Covers:** screen (MainScreen tabs, filter buttons, search box), data (filtering logic)

#### 5. PinAndHudJourney

Pin icon, sort order, HUD rendering, config.

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Create 2 quests | Both in My Quests |
| 2 | Click pin icon on second quest | Pin icon changes to active state |
| 3 | Verify pinned quest sorts first | First entry in list is pinned quest |
| 4 | Verify HUD renders pinned quest | HudPinRenderer draws pin (log or component check) |
| 5 | Unpin quest | Pin icon returns to inactive |
| 6 | Verify sort order restored | Original order |

**Covers:** hud (HudPinManager, HudPinRenderer), screen (pin icon, sort), gui/component (QuestEntryComponent pin area)

#### 6. ConfigJourney

Theme switching and persistence.

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Open ConfigScreen | ConfigScreen displayed |
| 2 | Cycle through all 5 themes | Each theme name shown on button, colors change |
| 3 | Click Cancel | Theme reverts to original |
| 4 | Reopen, select FLAT theme, click Save | ConfigScreen closes |
| 5 | Reopen ConfigScreen | Theme button shows "Flat" (persisted) |

**Covers:** screen (ConfigScreen), gui/helper (Theme, Colors, DisquestsConfig)

#### 7. UndoRedoJourney

Undo/redo in content editor.

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Create quest, enter edit mode | Edit mode |
| 2 | Type "Hello" in content field | Field shows "Hello" |
| 3 | Ctrl+Z | Field reverts to empty |
| 4 | Ctrl+Y | Field shows "Hello" again |
| 5 | Type " World" (now "Hello World") | Field shows "Hello World" |
| 6 | Ctrl+Z | Field shows "Hello" |
| 7 | Ctrl+Z | Field empty |

**Covers:** widget (MultiLineTextFieldWidget, UndoManager)

#### 8. DirtyDetectionJourney

Unsaved changes detection and confirm dialog.

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Create quest, save with title "Original" | Saved |
| 2 | Edit, change title to "Modified" | Title field changed |
| 3 | Click Cancel | ConfirmScreen appears with discard message |
| 4 | Click No (don't discard) | Returns to edit mode, title still "Modified" |
| 5 | Click Cancel again | ConfirmScreen appears again |
| 6 | Click Yes (discard) | View mode, title shows "Original" |
| 7 | Edit, change nothing, click Cancel | No dialog, directly returns to view mode |

**Covers:** screen (dirty detection, ConfirmScreen both paths)

### Two-Player Journeys (PlayerA + PlayerB)

#### 9. CollaborationJourney

Full collaboration request flow through the UI.

| Step | Player | Action | Assertion |
|------|--------|--------|-----------|
| 1 | A | Create CLOSED quest, save | Quest in A's My Quests |
| 2 | B | Open Quest Board, find A's quest | Quest visible on board |
| 3 | B | Select quest, click Request | Toast shows "Request sent", button changes to "Requested" |
| 4 | A | Open quest, enter edit mode | Contributors button shows "(1 pending)" |
| 5 | A | Click Contributors | ContributorScreen shows pending request from B |
| 6 | A | Click Accept | Request removed, B appears in contributor list |
| 7 | B | Quest appears in My Quests | Entry visible in My Quests tab |
| 8 | A | Toggle B's permission to "View Only" | Button text changes |
| 9 | A | Click Remove on B, confirm | B removed from contributor list |

**Covers:** screen (ContributorScreen, accept/deny, permission toggle, remove), network (collaboration packets)

#### 10. OpenQuestJourney

Join and leave an OPEN quest through the UI.

| Step | Player | Action | Assertion |
|------|--------|--------|-----------|
| 1 | A | Create OPEN quest, save | Quest in A's My Quests |
| 2 | B | Open Quest Board, find A's quest | Quest visible, Join button enabled |
| 3 | B | Click Join | Toast shows "Joined", quest appears in B's My Quests |
| 4 | B | Open quest, verify content visible | Content rendered in view mode |
| 5 | B | Click Leave | ConfirmScreen appears |
| 6 | B | Click Yes | Quest removed from B's My Quests, still on Quest Board |

**Covers:** screen (join/leave buttons, ConfirmScreen), network (join/leave packets)

#### 11. LiveUpdateJourney

Server pushes updates to other connected clients.

| Step | Player | Action | Assertion |
|------|--------|--------|-----------|
| 1 | A | Create OPEN quest "Original Title" | Saved |
| 2 | B | Join quest | Quest in B's My Quests |
| 3 | A | Edit title to "Updated Title", save | A sees "Updated Title" |
| 4 | B | Open MainScreen | Entry shows "Updated Title" (S2C update received) |
| 5 | A | Delete quest, confirm | Quest removed from A |
| 6 | B | Quest disappears from My Quests | Entry removed (S2C delete received) |

**Covers:** network (S2C update/delete handlers), screen (live updates)

## Coverage Projection

| Package | Current | Target | How |
|---------|---------|--------|-----|
| gui/screen | 45.5% | 85%+ | All journeys exercise screens |
| gui/widget | 33.2% | 75%+ | Content, undo/redo, coord field journeys |
| gui/component | 61.0% | 85%+ | Entry clicks, pin icon, all journeys |
| gui/helper | 70.1% | 85%+ | Config + theme journeys |
| markdown | 57.4% | 80%+ | Content journey with markdown |
| network | 3.9% | 70%+ | All server-connected, collaboration + live update |
| hud | 9.7% | 60%+ | Pin journey |
| data | 72.6% | 85%+ | All journeys create/modify quests |
| debug | 85.0% | 85%+ | Already high |
| **Overall** | **42.9%** | **~80%** | |

## Files Changed

| Action | Path |
|--------|------|
| Create | `client/src/testmod/.../integration/bdd/BDD.java` |
| Create | `client/src/testmod/.../integration/bdd/UIActions.java` (click, type, wait helpers) |
| Create | `client/src/testmod/.../integration/bdd/UIAssertions.java` (assertComponent, waitForCondition) |
| Create | `client/src/testmod/.../integration/journeys/QuestLifecycleJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/QuestContentJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/CoordinatesJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/SearchAndFilterJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/PinAndHudJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/ConfigJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/UndoRedoJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/DirtyDetectionJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/CollaborationJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/OpenQuestJourney.java` |
| Create | `client/src/testmod/.../integration/journeys/LiveUpdateJourney.java` |
| Delete | `client/src/testmod/.../test/QuestScreenTest.java` |
| Delete | `client/src/testmod/.../test/DisquestsE2ETest.java` |
| Delete | `client/src/testmod/.../integration/tests/LifecycleTest.java` |
| Delete | `client/src/testmod/.../integration/tests/DiscoveryTest.java` |
| Delete | `client/src/testmod/.../integration/tests/CollaborationTest.java` |
| Delete | `client/src/testmod/.../integration/tests/LeaveTest.java` |
| Delete | `client/src/testmod/.../integration/tests/PinPersistenceTest.java` |
| Modify | `client/build.gradle.kts` (JaCoCo on integration test, remove runClientGameTest or alias) |
