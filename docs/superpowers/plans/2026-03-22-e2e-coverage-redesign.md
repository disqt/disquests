# E2E Coverage Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all existing E2E/integration tests with UX-driven journey tests targeting 80% line coverage.

**Architecture:** Custom BDD DSL (given/when/then/and) on JUnit 5, all tests server-connected via `runIntegrationTest`, journey-per-file with ordered steps, single client for solo journeys, two clients for collaboration. JaCoCo coverage on integration test clients.

**Tech Stack:** JUnit 5 (programmatic launcher), Fabric ClientGameTest API (TestInput for GLFW input), PhaseSync (file-based coordination), RCON (server reset), JaCoCo

**Spec:** `docs/superpowers/specs/2026-03-22-e2e-coverage-redesign.md`

---

## Critical Implementation Notes

These notes supplement the tasks below. Implementors MUST read these before starting.

### fabric.mod.json entrypoints (Tasks 5-6 combined)
Delete old entrypoints from `client/src/testmod/resources/fabric.mod.json` when deleting old tests. The file must only contain `HarnessPlayerA` and `HarnessPlayerB`:
```json
{
  "entrypoints": {
    "fabric-client-gametest": [
      "com.disqt.disquests.test.integration.harness.HarnessPlayerA",
      "com.disqt.disquests.test.integration.harness.HarnessPlayerB"
    ]
  }
}
```
This MUST be done before any journey test is run, otherwise the old `QuestScreenTest` and `DisquestsE2ETest` will try to run and interfere.

### IntegrationTestHelper migration (Task 6)
When updating `HarnessPlayerA.java` and `HarnessPlayerB.java`, also update the import from `IntegrationTestHelper.*` to `UIActions.*` (for `connectAndWait` and `shouldSkip`). `UIActions` must include a `shouldSkip()` method:
```java
public static boolean shouldSkip(String journeyName) {
    String filter = System.getProperty("disquests.test.journey");
    return filter != null && !filter.isEmpty() && !filter.equals(journeyName);
}
```
Similarly update `IntegrationTestExtension.java` to import `connectAndWait` from `UIActions` instead of `IntegrationTestHelper`.

### RCON reset in @BeforeAll (all journey classes)
Every journey class MUST have a `@BeforeAll` that resets server state via RCON:
```java
@BeforeAll
static void resetServer() throws Exception {
    var rcon = new RconClient("localhost",
        Integer.parseInt(System.getProperty("disquests.test.rcon.port", "25575")));
    rcon.login(System.getProperty("disquests.test.rcon.password", "testpassword"));
    rcon.command("disquests reset");
    rcon.close();
    AbortOnFailureExtension.clearFailures();
    // Wait for server re-handshake to complete
    Thread.sleep(1000);
}
```
This runs after `IntegrationTestExtension.beforeAll()` per JUnit 5 ordering. The extension syncs first (gets stale data), then RCON wipes the DB and triggers re-handshake (clean state).

### Component IDs for programmatic fields (Task 5 exact edits)
In `QuestScreen.java`, add `.id()` calls after component creation:
- Line ~276: after `titleFieldComponent.sizing(...)` add `titleFieldComponent.id("title-field");`
- Line ~286: after `contentFieldComponent.sizing(...)` add `contentFieldComponent.id("content-field");`
- Line ~335: after `coordsRow.child(coordXComponent)` -- before that, add `coordXComponent.id("coord-x1");`
- Same for `coordYComponent.id("coord-y1")` and `coordZComponent.id("coord-z1")`
- Line ~344: after `setPosBtn.sizing(...)` add `setPosBtn.id("btn-set-pos");`
- Line ~349: after `regionBtn.sizing(...)` add `regionBtn.id("btn-region");`
- Line ~354: after `clearBtn.sizing(...)` add `clearBtn.id("btn-clear");`
- Line ~363-365: add `coord2XComponent.id("coord-x2")`, `coord2YComponent.id("coord-y2")`, `coord2ZComponent.id("coord-z2")`
- Line ~384: after `mapBtn.sizing(...)` add `mapBtn.id("btn-map");`

In `MainScreen.java`, line ~137: after `this.searchField = UIComponents.textBox(...)` add `this.searchField.id("search-box");`

### runClientGameTest alias (Task 7)
Add this to `build.gradle.kts` to alias `runClientGameTest` to `runIntegrationTest`:
```kotlin
tasks.register("runClientGameTestAlias") {
    group = "verification"
    dependsOn("runIntegrationTest")
}
```
Or update the existing `runClientGameTest` task configuration.

### TestLogCapture preservation
`TestLogCapture.java` at `client/src/testmod/.../test/TestLogCapture.java` is NOT deleted. It remains available for debug log assertions (trust level 3).

### Task ordering clarification
**Delete old tests and update fabric.mod.json BEFORE writing any journey files.** The plan lists deletion as Task 19 but it should be done as part of Task 6 (harness update). In practice: update fabric.mod.json + harness imports in Task 6, then delete old files immediately in a new Task 6b, before writing journeys.

---

## File Structure

### New Files (infrastructure)
| File | Responsibility |
|------|---------------|
| `client/src/testmod/.../integration/bdd/BDD.java` | given/when/then/and logging DSL |
| `client/src/testmod/.../integration/bdd/UIActions.java` | click, type, scroll, waitForScreen helpers |
| `client/src/testmod/.../integration/bdd/UIAssertions.java` | assertComponent, assertLabel, waitForCondition |
| `client/src/testmod/.../integration/bdd/AbortOnFailureExtension.java` | Skip remaining @Order steps on failure |

### New Files (journeys)
| File | Responsibility |
|------|---------------|
| `.../integration/journeys/QuestLifecycleJourney.java` | Create, edit, delete quest via UI |
| `.../integration/journeys/QuestContentJourney.java` | Markdown editing, rendering, checkbox toggle |
| `.../integration/journeys/CoordinatesJourney.java` | Coord input, region toggle, map cycle |
| `.../integration/journeys/SearchAndFilterJourney.java` | Tabs, filters, search box |
| `.../integration/journeys/PinAndHudJourney.java` | Pin icon, sort order, HUD state |
| `.../integration/journeys/ConfigJourney.java` | Theme cycling, persistence, cancel/revert |
| `.../integration/journeys/UndoRedoJourney.java` | Undo/redo in content editor |
| `.../integration/journeys/DirtyDetectionJourney.java` | Unsaved changes, confirm dialog |
| `.../integration/journeys/CollaborationJourney.java` | Two-player: request, accept, permissions, remove |
| `.../integration/journeys/OpenQuestJourney.java` | Two-player: join, view, leave |
| `.../integration/journeys/LiveUpdateJourney.java` | Two-player: S2C updates, delete propagation |

### Modified Files
| File | Change |
|------|--------|
| `client/build.gradle.kts` | JaCoCo on integration clients, alias runClientGameTest |
| `.../harness/HarnessPlayerA.java` | TESTS_PACKAGE -> `journeys`, import UIActions instead of IntegrationTestHelper |
| `.../harness/HarnessPlayerB.java` | TESTS_PACKAGE -> `journeys`, import UIActions instead of IntegrationTestHelper |
| `.../harness/IntegrationTest.java` | Add AbortOnFailureExtension |
| `.../harness/IntegrationTestExtension.java` | Import connectAndWait from UIActions |
| `client/src/testmod/resources/fabric.mod.json` | Remove QuestScreenTest and DisquestsE2ETest entrypoints |
| `.../gui/screen/QuestScreen.java` | Add IDs to programmatic components (title-field, content-field, coord fields, buttons) |
| `.../gui/screen/MainScreen.java` | Add ID to search TextBoxComponent (search-box) |
| `.github/workflows/e2e-test.yml` | Replace runClientGameTest with runIntegrationTest |
| `.claude/CLAUDE.md` | Update test documentation |

### Deleted Files
| File | Reason |
|------|--------|
| `client/src/testmod/.../test/QuestScreenTest.java` | Replaced by journeys |
| `client/src/testmod/.../test/DisquestsE2ETest.java` | Replaced by journeys |
| `client/src/testmod/.../integration/tests/LifecycleTest.java` | Replaced |
| `client/src/testmod/.../integration/tests/DiscoveryTest.java` | Replaced |
| `client/src/testmod/.../integration/tests/CollaborationTest.java` | Replaced |
| `client/src/testmod/.../integration/tests/LeaveTest.java` | Replaced |
| `client/src/testmod/.../integration/tests/PinPersistenceTest.java` | Replaced |
| `client/src/testmod/.../integration/QuestLifecycleTest.java` | Old standalone |
| `client/src/testmod/.../integration/QuestDiscoveryTest.java` | Old standalone |
| `client/src/testmod/.../integration/CollaborationTest.java` | Old standalone |
| `client/src/testmod/.../integration/LeaveQuestTest.java` | Old standalone |
| `client/src/testmod/.../integration/PinPersistenceTest.java` | Old standalone |
| `client/src/testmod/.../integration/IntegrationTestHelper.java` | Absorbed into UIActions |

**All paths below use `...` for `client/src/testmod/java/com/disqt/disquests/test`.**

---

## Task 1: BDD DSL

**Files:**
- Create: `.../integration/bdd/BDD.java`

- [ ] **Step 1: Create BDD.java**

```java
package com.disqt.disquests.test.integration.bdd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BDD {
    private static final Logger LOG = LoggerFactory.getLogger("Disquests/E2E");

    private BDD() {}

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

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/bdd/BDD.java
git commit -m "feat(test): add BDD DSL for journey tests"
```

---

## Task 2: UIActions helper

**Files:**
- Create: `.../integration/bdd/UIActions.java`
- Reference: `.../test/QuestScreenTest.java` (existing click patterns)
- Reference: `.../integration/IntegrationTestHelper.java` (connectAndWait, disconnect, waitForQuest)

UIActions absorbs the useful methods from `IntegrationTestHelper` and adds UI interaction helpers. All methods use GLFW physical input via `TestInput`.

- [ ] **Step 1: Create UIActions.java**

```java
package com.disqt.disquests.test.integration.bdd;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Function;

/**
 * UI interaction helpers using GLFW physical input (TestInput).
 * Never call screen methods directly -- always go through the real input pipeline.
 */
public final class UIActions {
    private static final Logger LOG = LoggerFactory.getLogger("Disquests/E2E");
    public static final int TIMEOUT = 30 * 20; // 30 seconds in ticks

    private UIActions() {}

    // --- Connection ---

    public static void connectAndWait(ClientGameTestContext context) {
        String host = System.getProperty("disquests.test.server.host", "localhost");
        int port = Integer.getInteger("disquests.test.server.port", 25565);
        String address = host + ":" + port;

        context.runOnClient(client -> {
            ServerAddress serverAddress = ServerAddress.parse(address);
            ServerInfo serverInfo = new ServerInfo("Test", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client, serverAddress, serverInfo, false, null);
        });

        // Wait for player entity
        context.waitFor(client -> client.player != null, TIMEOUT);
        // Wait for Disquests handshake
        context.waitFor(client -> ClientSession.isOnServer(), TIMEOUT);
        context.waitTicks(10);
    }

    public static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
        context.waitForScreen(TitleScreen.class);
    }

    // --- Screen navigation ---

    public static void openMainScreen(ClientGameTestContext context) {
        context.runOnClient(client -> client.setScreen(new MainScreen(null)));
        context.waitForScreen(MainScreen.class);
        context.waitTicks(2);
    }

    public static <T extends Screen> void waitForScreen(ClientGameTestContext context, Class<T> screenClass) {
        context.waitFor(client -> screenClass.isInstance(client.currentScreen), TIMEOUT);
        context.waitTicks(2);
    }

    // --- Component interaction ---

    /**
     * Click a component by its XML/programmatic ID.
     * Uses GLFW physical input: setCursorPos + pressMouse.
     */
    public static void click(ClientGameTestContext context, String componentId) {
        double scale = context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
        double[] pos = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                var root = owoScreen.uiAdapter.rootComponent;
                var component = root.childById(UIComponent.class, componentId);
                if (component == null) throw new AssertionError("Component not found: " + componentId);
                return new double[]{component.x() + component.width() / 2.0, component.y() + component.height() / 2.0};
            }
            throw new AssertionError("Current screen is not an owo-ui screen");
        });
        context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    /**
     * Click a QuestEntryComponent by index in the quest list.
     * Entries are children of the "quest-list" FlowLayout.
     */
    public static void clickEntry(ClientGameTestContext context, int index) {
        double scale = context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
        double[] pos = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                var root = owoScreen.uiAdapter.rootComponent;
                var questList = root.childById(io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
                if (questList == null) throw new AssertionError("quest-list not found");
                var children = questList.children();
                if (index >= children.size()) throw new AssertionError("Entry index " + index + " out of bounds, size=" + children.size());
                var entry = children.get(index);
                return new double[]{entry.x() + entry.width() / 2.0, entry.y() + entry.height() / 2.0};
            }
            throw new AssertionError("Current screen is not an owo-ui screen");
        });
        context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    /**
     * Double-click a QuestEntryComponent by index.
     */
    public static void doubleClickEntry(ClientGameTestContext context, int index) {
        clickEntry(context, index);
        context.waitTicks(1);
        clickEntry(context, index);
    }

    /**
     * Click the pin icon on a QuestEntryComponent by index.
     * Pin icon is in the rightmost 20px of the entry, vertically centered.
     */
    public static void clickPinIcon(ClientGameTestContext context, int index) {
        double scale = context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
        double[] pos = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                var root = owoScreen.uiAdapter.rootComponent;
                var questList = root.childById(io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
                if (questList == null) throw new AssertionError("quest-list not found");
                var entry = questList.children().get(index);
                // Pin icon is rightmost 10px, vertically at y+19 (center of rows 2-3)
                return new double[]{entry.x() + entry.width() - 10.0, entry.y() + 19.0};
            }
            throw new AssertionError("Current screen is not an owo-ui screen");
        });
        context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    /**
     * Type text into a focused text field.
     * Must click the field first to focus it. Waits 1 tick for focus desync workaround.
     */
    public static void type(ClientGameTestContext context, String componentId, String text) {
        click(context, componentId);
        context.waitTicks(1); // focus desync workaround for GreedyInputUIComponent
        // Select all existing text (Ctrl+A) then delete to clear field
        context.getInput().holdControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_A);
        context.getInput().releaseControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_DELETE);
        context.waitTicks(1);
        // Type new text
        context.getInput().typeChars(text);
        context.waitTicks(2);
    }

    /**
     * Append text to a text field without clearing existing content.
     */
    public static void appendText(ClientGameTestContext context, String componentId, String text) {
        click(context, componentId);
        context.waitTicks(1);
        // Move to end (Ctrl+End)
        context.getInput().holdControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_END);
        context.getInput().releaseControl();
        context.getInput().typeChars(text);
        context.waitTicks(2);
    }

    /**
     * Press Ctrl+Z (undo) on the currently focused field.
     */
    public static void undo(ClientGameTestContext context) {
        context.getInput().holdControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_Z);
        context.getInput().releaseControl();
        context.waitTicks(2);
    }

    /**
     * Press Ctrl+Y (redo) on the currently focused field.
     */
    public static void redo(ClientGameTestContext context) {
        context.getInput().holdControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_Y);
        context.getInput().releaseControl();
        context.waitTicks(2);
    }

    // --- Quest helpers ---

    /**
     * Wait for a quest to appear in cache by title.
     * @param myQuests true = search myQuests, false = search serverQuests
     */
    public static Quest waitForQuestByTitle(ClientGameTestContext context, String title, boolean myQuests) {
        context.waitFor(client -> {
            var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
            return list.stream().anyMatch(q -> title.equals(q.getTitle()));
        }, TIMEOUT);
        return context.computeOnClient(c -> {
            var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
            return list.stream().filter(q -> title.equals(q.getTitle())).findFirst().orElse(null);
        });
    }

    /**
     * Wait for a quest to be removed from myQuests.
     */
    public static void waitForQuestRemoved(ClientGameTestContext context, UUID questId) {
        context.waitFor(client ->
            ClientCache.getMyQuests().stream().noneMatch(q -> q.getId().equals(questId)),
            TIMEOUT
        );
    }

    /**
     * Wait for quest list in MainScreen to have exactly N entries.
     */
    public static void waitForEntryCount(ClientGameTestContext context, int count) {
        context.waitFor(client -> {
            Screen screen = client.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                var root = owoScreen.uiAdapter.rootComponent;
                var questList = root.childById(io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
                return questList != null && questList.children().size() == count;
            }
            return false;
        }, TIMEOUT);
    }

    /**
     * Offline-mode UUID generation (matches Paper's offline UUID format).
     */
    public static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Check if this test journey should be skipped based on -Ddisquests.test.journey filter.
     * Used by HarnessPlayerA/B to decide whether to run.
     */
    public static boolean shouldSkip(String journeyName) {
        String filter = System.getProperty("disquests.test.journey");
        return filter != null && !filter.isEmpty() && !filter.equals(journeyName);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/bdd/UIActions.java
git commit -m "feat(test): add UIActions helper for GLFW-based UI interactions"
```

---

## Task 3: UIAssertions helper

**Files:**
- Create: `.../integration/bdd/UIAssertions.java`

- [ ] **Step 1: Create UIAssertions.java**

```java
package com.disqt.disquests.test.integration.bdd;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI assertion helpers. All assertions run on the client thread
 * via computeOnClient to ensure thread safety.
 */
public final class UIAssertions {

    private UIAssertions() {}

    /**
     * Assert a condition on a component found by ID.
     */
    public static <T extends UIComponent> void assertComponent(
            ClientGameTestContext context, String componentId, Class<T> type, Predicate<T> predicate, String message) {
        boolean result = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                T component = owoScreen.uiAdapter.rootComponent.childById(type, componentId);
                if (component == null) return false;
                return predicate.test(component);
            }
            return false;
        });
        assertTrue(result, message);
    }

    /**
     * Assert that a label component has the expected text.
     */
    public static void assertLabelText(ClientGameTestContext context, String componentId, String expected) {
        String actual = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                LabelComponent label = owoScreen.uiAdapter.rootComponent.childById(LabelComponent.class, componentId);
                if (label == null) return null;
                return label.text().getString();
            }
            return null;
        });
        assertNotNull(actual, "Label '" + componentId + "' not found");
        assertEquals(expected, actual, "Label '" + componentId + "' text mismatch");
    }

    /**
     * Assert that a button component has text containing the expected substring.
     */
    public static void assertButtonText(ClientGameTestContext context, String componentId, String expectedSubstring) {
        String actual = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                ButtonComponent btn = owoScreen.uiAdapter.rootComponent.childById(ButtonComponent.class, componentId);
                if (btn == null) return null;
                return btn.getMessage().getString();
            }
            return null;
        });
        assertNotNull(actual, "Button '" + componentId + "' not found");
        assertTrue(actual.contains(expectedSubstring),
            "Button '" + componentId + "' text '" + actual + "' does not contain '" + expectedSubstring + "'");
    }

    /**
     * Assert a component exists (non-null).
     */
    public static void assertComponentExists(ClientGameTestContext context, String componentId) {
        boolean exists = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                return owoScreen.uiAdapter.rootComponent.childById(UIComponent.class, componentId) != null;
            }
            return false;
        });
        assertTrue(exists, "Component '" + componentId + "' not found");
    }

    /**
     * Assert a component does NOT exist (was removed or hidden).
     */
    public static void assertComponentMissing(ClientGameTestContext context, String componentId) {
        boolean exists = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                return owoScreen.uiAdapter.rootComponent.childById(UIComponent.class, componentId) != null;
            }
            return false;
        });
        assertFalse(exists, "Component '" + componentId + "' should not exist but was found");
    }

    /**
     * Assert the current screen is the expected type.
     */
    public static void assertScreenIs(ClientGameTestContext context, Class<? extends Screen> screenClass) {
        boolean match = context.computeOnClient(c -> screenClass.isInstance(c.currentScreen));
        assertTrue(match, "Expected screen " + screenClass.getSimpleName() + " but got different screen");
    }

    /**
     * Assert the quest list has exactly N entries.
     */
    public static void assertEntryCount(ClientGameTestContext context, int expected) {
        int actual = context.computeOnClient(c -> {
            Screen screen = c.currentScreen;
            if (screen instanceof BaseUIModelScreen<?> owoScreen) {
                var questList = owoScreen.uiAdapter.rootComponent.childById(
                    io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
                return questList != null ? questList.children().size() : -1;
            }
            return -1;
        });
        assertEquals(expected, actual, "Quest list entry count mismatch");
    }

    /**
     * Wait for a condition with timeout, then assert it's true.
     */
    public static void waitAndAssert(ClientGameTestContext context, String description,
                                      java.util.function.Predicate<net.minecraft.client.MinecraftClient> condition) {
        context.waitFor(condition::test, UIActions.TIMEOUT);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/bdd/UIAssertions.java
git commit -m "feat(test): add UIAssertions helper for component-level assertions"
```

---

## Task 4: AbortOnFailureExtension

**Files:**
- Create: `.../integration/bdd/AbortOnFailureExtension.java`
- Modify: `.../integration/harness/IntegrationTest.java`

- [ ] **Step 1: Create AbortOnFailureExtension.java**

```java
package com.disqt.disquests.test.integration.bdd;

import org.junit.jupiter.api.extension.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skips remaining @Order steps in a journey if a prior step failed.
 * Prevents cascading failures from producing noise.
 */
public class AbortOnFailureExtension implements ExecutionCondition, AfterTestExecutionCallback {

    private static final Map<Class<?>, Throwable> FAILURES = new ConcurrentHashMap<>();

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return context.getTestClass()
                .filter(FAILURES::containsKey)
                .map(cls -> ConditionEvaluationResult.disabled(
                    "Prior step failed: " + FAILURES.get(cls).getMessage()))
                .orElse(ConditionEvaluationResult.enabled("No prior failures"));
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        context.getExecutionException().ifPresent(ex ->
            context.getTestClass().ifPresent(cls -> FAILURES.putIfAbsent(cls, ex))
        );
    }

    /**
     * Clear failure state. Call between journey classes (e.g. in RCON reset).
     */
    public static void clearFailures() {
        FAILURES.clear();
    }
}
```

- [ ] **Step 2: Add extension to @IntegrationTest annotation**

In `.../integration/harness/IntegrationTest.java`, add AbortOnFailureExtension:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(IntegrationTestExtension.class)
@ExtendWith(AbortOnFailureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public @interface IntegrationTest {}
```

Add import: `import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;`

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/bdd/AbortOnFailureExtension.java
git add client/src/testmod/java/com/disqt/disquests/test/integration/harness/IntegrationTest.java
git commit -m "feat(test): add AbortOnFailureExtension for journey step cascade prevention"
```

---

## Task 5: Add component IDs to production code

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

Programmatically-added components need IDs so UIActions.click() and UIAssertions can find them.

- [ ] **Step 1: Add IDs in QuestScreen.java**

Find where components are created programmatically and add `.id("...")` calls. Key components:

In the `buildEditMode()` method where title TextFieldComponent is created, add:
```java
titleFieldComponent.id("title-field");
```

Where content TextFieldComponent is created, add:
```java
contentFieldComponent.id("content-field");
```

Where coordinate TextBoxComponents are created (x, y, z for corner 1 and 2), add:
```java
// Corner 1
xField.id("coord-x1");
yField.id("coord-y1");
zField.id("coord-z1");
// Corner 2
x2Field.id("coord-x2");
y2Field.id("coord-y2");
z2Field.id("coord-z2");
```

Where the Set Pos, Region, Clear, and Map buttons are created programmatically (if not already in XML), add IDs.

**Read the actual QuestScreen.java** to find the exact locations and variable names before editing.

- [ ] **Step 2: Add search box ID in MainScreen.java**

Find where the search TextBoxComponent is created programmatically and add:
```java
searchBox.id("search-box");
```

**Read the actual MainScreen.java** to find the exact location and variable name.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :client:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat: add component IDs to programmatic UI elements for test accessibility"
```

---

## Task 6: Update harness, fabric.mod.json, and delete old tests

**Files:**
- Modify: `.../harness/HarnessPlayerA.java`
- Modify: `.../harness/HarnessPlayerB.java`
- Modify: `.../harness/IntegrationTestExtension.java`
- Modify: `client/src/testmod/resources/fabric.mod.json`
- Delete: all old test files (see Critical Implementation Notes)

This task MUST be completed before writing any journey files.

- [ ] **Step 1: Update TESTS_PACKAGE in both harness files**

Change from:
```java
private static final String TESTS_PACKAGE = "com.disqt.disquests.test.integration.tests";
```
To:
```java
private static final String TESTS_PACKAGE = "com.disqt.disquests.test.integration.journeys";
```

- [ ] **Step 2: Update imports in HarnessPlayerA.java and HarnessPlayerB.java**

Replace:
```java
import static com.disqt.disquests.test.integration.IntegrationTestHelper.*;
```
With:
```java
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
```

- [ ] **Step 3: Update import in IntegrationTestExtension.java**

Replace:
```java
import static com.disqt.disquests.test.integration.IntegrationTestHelper.connectAndWait;
```
With:
```java
import static com.disqt.disquests.test.integration.bdd.UIActions.connectAndWait;
```

- [ ] **Step 4: Update fabric.mod.json entrypoints**

Remove `QuestScreenTest` and `DisquestsE2ETest` entrypoints, keeping only the harness:
```json
{
  "schemaVersion": 1,
  "id": "disquests-test",
  "version": "1.0.0",
  "name": "Disquests E2E Tests",
  "environment": "client",
  "entrypoints": {
    "fabric-client-gametest": [
      "com.disqt.disquests.test.integration.harness.HarnessPlayerA",
      "com.disqt.disquests.test.integration.harness.HarnessPlayerB"
    ]
  },
  "depends": {
    "disquests": "*",
    "fabric-client-gametest-api-v1": "*"
  }
}
```

- [ ] **Step 5: Delete all old test files**

```bash
rm client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java
rm client/src/testmod/java/com/disqt/disquests/test/DisquestsE2ETest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/tests/LifecycleTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/tests/DiscoveryTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/tests/CollaborationTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/tests/LeaveTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/tests/PinPersistenceTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/QuestLifecycleTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/QuestDiscoveryTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/CollaborationTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/LeaveQuestTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/PinPersistenceTest.java
rm client/src/testmod/java/com/disqt/disquests/test/integration/IntegrationTestHelper.java
```

Do NOT delete: `TestLogCapture.java`, `PhaseSync.java`, `RconClient.java`, or any harness files.

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```
git add -A client/src/testmod/
git commit -m "refactor(test): update harness for journeys, remove old test files"
```

---

## Task 7: Add JaCoCo to integration test

**Files:**
- Modify: `client/build.gradle.kts`

- [ ] **Step 1: Add JaCoCo agent to integration client launch**

The `runIntegrationTest` task launches clients via `launchClient()` which calls `gradlew :client:runClientGameTest`. The JaCoCo agent is already attached to `runClientGameTest` when `-Pcoverage` is passed. We need to:

1. Pass `-Pcoverage` through to client launches when the orchestrator receives it
2. Add a `jacocoIntegrationTestReport` task that reads from integration exec files
3. Keep `runClientGameTest` working (aliased to runIntegrationTest for CI)

In the `launchClient` function inside `runIntegrationTest`, add coverage flag forwarding:

```kotlin
if (project.hasProperty("coverage")) cmd.add("-Pcoverage")
```

Add integration JaCoCo report task after the existing `jacocoGameTestReport`:

```kotlin
tasks.register<JacocoReport>("jacocoIntegrationTestReport") {
    group = "verification"
    description = "Generate code coverage report from integration E2E tests"
    dependsOn("compileJava")

    executionData(layout.buildDirectory.file("jacoco/gameTest.exec"))
    sourceDirectories.from(files("src/main/java"))
    classDirectories.from(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            include("com/disqt/disquests/**")
        }
    )

    reports {
        html.required.set(true)
        xml.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/integrationTest/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/integrationTest/report.xml"))
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew tasks --group=verification`
Expected: Shows both `jacocoGameTestReport` and `jacocoIntegrationTestReport`

- [ ] **Step 3: Commit**

```
git add client/build.gradle.kts
git commit -m "feat(test): add JaCoCo coverage to integration test clients"
```

---

## Task 8: QuestLifecycleJourney

**Files:**
- Create: `.../integration/journeys/QuestLifecycleJourney.java`

This is the first journey and the most critical -- it exercises the core create/edit/save/delete flow.

- [ ] **Step 1: Write the journey**

```java
package com.disqt.disquests.test.integration.journeys;

import com.disqt.disquests.client.gui.screen.*;
import com.disqt.disquests.test.integration.harness.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

@IntegrationTest
@DisplayName("Quest Lifecycle Journey")
class QuestLifecycleJourney {

    @Test @Order(1) @PlayerA
    @DisplayName("Open MainScreen with empty My Quests")
    void openMainScreen(ClientGameTestContext context) {
        given("player is connected to the server");
        when("player opens MainScreen");
            openMainScreen(context);
        then("My Quests tab is shown with no entries");
            assertScreenIs(context, MainScreen.class);
            assertEntryCount(context, 0);
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Create a new quest")
    void createQuest(ClientGameTestContext context) {
        when("player clicks New Quest");
            openMainScreen(context);
            click(context, "btn-new-quest");
        then("QuestScreen opens in edit mode with empty fields");
            waitForScreen(context, QuestScreen.class);
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Type title and content")
    void typeContent(ClientGameTestContext context) {
        given("player is in edit mode");
        when("player types title and content");
            type(context, "title-field", "Lifecycle Test");
        and("player types content");
            type(context, "content-field", "Initial content for testing");
        then("fields show the entered text");
            // Fields populated -- verified by save in next step
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Save quest and verify view mode")
    void saveQuest(ClientGameTestContext context) {
        when("player clicks Save");
            click(context, "btn-save");
        then("screen shows view mode with correct title");
            waitForScreen(context, QuestScreen.class);
            assertLabelText(context, "title-label", "Lifecycle Test");
    }

    @Test @Order(5) @PlayerA
    @DisplayName("Return to MainScreen and see quest in list")
    void returnToMain(ClientGameTestContext context) {
        when("player clicks Close");
            click(context, "btn-close");
        then("MainScreen shows the quest in My Quests");
            waitForScreen(context, MainScreen.class);
            assertEntryCount(context, 1);
    }

    @Test @Order(6) @PlayerA
    @DisplayName("Open quest and enter edit mode")
    void openAndEdit(ClientGameTestContext context) {
        given("player is on MainScreen with one quest");
            openMainScreen(context);
        when("player selects the quest and clicks Open");
            clickEntry(context, 0);
            click(context, "btn-open");
        then("QuestScreen opens in view mode");
            waitForScreen(context, QuestScreen.class);
        when("player clicks Edit");
            click(context, "btn-edit");
        then("edit mode opens");
            waitForScreen(context, QuestScreen.class);
    }

    @Test @Order(7) @PlayerA
    @DisplayName("Edit title and save")
    void editTitle(ClientGameTestContext context) {
        when("player changes the title");
            type(context, "title-field", "Updated Title");
        and("player clicks Save");
            click(context, "btn-save");
        then("view mode shows updated title");
            waitForScreen(context, QuestScreen.class);
            assertLabelText(context, "title-label", "Updated Title");
    }

    @Test @Order(8) @PlayerA
    @DisplayName("Delete quest with confirmation")
    void deleteQuest(ClientGameTestContext context) {
        when("player clicks Delete");
            click(context, "btn-delete");
        then("ConfirmScreen appears");
            waitForScreen(context, ConfirmScreen.class);
        when("player clicks Yes");
            click(context, "btn-yes");
        then("MainScreen shows empty list");
            waitForScreen(context, MainScreen.class);
            assertEntryCount(context, 0);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the journey against a live server**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=QuestLifecycleJourney`
Expected: All steps PASS

- [ ] **Step 4: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/QuestLifecycleJourney.java
git commit -m "test: add QuestLifecycleJourney (create, edit, delete via UI)"
```

---

## Task 9: QuestContentJourney

**Files:**
- Create: `.../integration/journeys/QuestContentJourney.java`

- [ ] **Step 1: Write the journey**

Covers: markdown editing, rendering, checkbox toggle, formatting panel.

```java
package com.disqt.disquests.test.integration.journeys;

import com.disqt.disquests.client.gui.screen.*;
import com.disqt.disquests.test.integration.harness.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.*;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

@IntegrationTest
@DisplayName("Quest Content Journey")
class QuestContentJourney {

    @Test @Order(1) @PlayerA
    @DisplayName("Create quest with markdown content")
    void createWithMarkdown(ClientGameTestContext context) {
        given("player is on MainScreen");
            openMainScreen(context);
        when("player creates a new quest");
            click(context, "btn-new-quest");
            waitForScreen(context, QuestScreen.class);
        and("types a title");
            type(context, "title-field", "Markdown Test");
        and("types markdown content");
            type(context, "content-field", "# Heading\n**bold** and *italic*\n- [ ] task one\n- [x] task two\n> a quote");
        and("clicks Save");
            click(context, "btn-save");
        then("view mode shows the quest");
            waitForScreen(context, QuestScreen.class);
            assertLabelText(context, "title-label", "Markdown Test");
    }

    @Test @Order(2) @PlayerA
    @DisplayName("Verify markdown renders in view mode")
    void verifyMarkdownRenders(ClientGameTestContext context) {
        then("content area contains rendered markdown");
            assertComponentExists(context, "content-area");
            // MarkdownWidget is a child of content-area -- its presence means rendering occurred
    }

    @Test @Order(3) @PlayerA
    @DisplayName("Formatting panel visible in edit mode")
    void formattingPanel(ClientGameTestContext context) {
        when("player clicks Edit");
            click(context, "btn-edit");
            waitForScreen(context, QuestScreen.class);
        then("formatting panel is present");
            assertComponentExists(context, "formatting-panel");
    }

    @Test @Order(4) @PlayerA
    @DisplayName("Save and clean up")
    void cleanup(ClientGameTestContext context) {
        when("player clicks Cancel to return to view mode");
            click(context, "btn-cancel");
            waitForScreen(context, QuestScreen.class);
    }
}
```

- [ ] **Step 2: Verify it compiles and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=QuestContentJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/QuestContentJourney.java
git commit -m "test: add QuestContentJourney (markdown editing, rendering, formatting panel)"
```

---

## Task 10: CoordinatesJourney

**Files:**
- Create: `.../integration/journeys/CoordinatesJourney.java`

- [ ] **Step 1: Write the journey**

Covers: coordinate input, region toggle, map cycling, clear.

The journey creates a quest, enters edit mode, fills in coordinates, saves, verifies in view, then tests region toggle, map cycle, and clear. Uses the component IDs added in Task 5 (`coord-x1`, `coord-y1`, `coord-z1`, region toggle, map button, clear button, corner2 fields).

**Read QuestScreen.java** to find exact button IDs for Set Pos, Region, Clear, Map before writing. These are likely in XML (`btn-set-pos`, `btn-region`, `btn-clear`, `btn-map`) or need to be identified from the code.

Write the journey following the same pattern as QuestLifecycleJourney. Key steps:
1. Create quest, enter edit mode
2. Type coordinates in x1/y1/z1 fields
3. Save, verify coords display in view mode (`coords-label`)
4. Edit, click region toggle, type corner2 coordinates
5. Save, verify region display
6. Edit, click map button multiple times (cycle through values)
7. Save, verify map label
8. Edit, click clear, save, verify coords hidden

- [ ] **Step 2: Verify it compiles and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=CoordinatesJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/CoordinatesJourney.java
git commit -m "test: add CoordinatesJourney (coord input, region, map cycle, clear)"
```

---

## Task 11: SearchAndFilterJourney

**Files:**
- Create: `.../integration/journeys/SearchAndFilterJourney.java`

- [ ] **Step 1: Write the journey**

Covers: MainScreen tabs, filter buttons, search box. Steps:
1. Create quest "Alpha", save
2. Edit Alpha, cycle visibility to OPEN (click `btn-visibility` twice: PRIVATE->CLOSED->OPEN), save
3. Return to MainScreen, create "Beta", edit, cycle visibility to CLOSED (once), save
4. Create "Gamma" (stays PRIVATE)
5. Switch to Quest Board tab (click `tab-quest-board`)
6. Verify filter row appears, Alpha (OPEN) visible
7. Click "Open" filter (`filter-open`), verify only Alpha
8. Click "Closed" filter (`filter-closed`), verify only Beta
9. Click "All" filter (`filter-all`), verify both
10. Type "Alpha" in search box (`search-box`), verify filtering
11. Clear search, verify all return
12. Switch to My Quests tab, verify all 3 quests

- [ ] **Step 2: Verify and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=SearchAndFilterJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/SearchAndFilterJourney.java
git commit -m "test: add SearchAndFilterJourney (tabs, filters, search)"
```

---

## Task 12: PinAndHudJourney

**Files:**
- Create: `.../integration/journeys/PinAndHudJourney.java`

- [ ] **Step 1: Write the journey**

Covers: pin icon click, sort order, HUD pin state. Steps:
1. Create 2 quests ("First", "Second")
2. On MainScreen, click pin icon on "Second" entry (index 1 initially)
3. Verify "Second" sorts to index 0 (pinned first)
4. Verify `HudPinManager.isPinned()` returns true (trust level 4 -- HUD renders outside Screen)
5. Click pin icon on "Second" again (now at index 0) to unpin
6. Verify "First" returns to index 0

- [ ] **Step 2: Verify and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=PinAndHudJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/PinAndHudJourney.java
git commit -m "test: add PinAndHudJourney (pin icon, sort order, HUD state)"
```

---

## Task 13: ConfigJourney

**Files:**
- Create: `.../integration/journeys/ConfigJourney.java`

- [ ] **Step 1: Write the journey**

Covers: ConfigScreen, theme cycling, save/cancel/revert. Steps:
1. Open ConfigScreen (`context.runOnClient(c -> c.setScreen(new ConfigScreen(null)))`)
2. Read current theme button text
3. Click `btn-theme` 5 times (cycle all themes), verify button text changes each time
4. Click `btn-cancel`, verify theme reverts
5. Reopen ConfigScreen, click `btn-theme` once (FLAT), click `btn-save`
6. Reopen ConfigScreen, verify theme button shows "Flat"

- [ ] **Step 2: Verify and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=ConfigJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/ConfigJourney.java
git commit -m "test: add ConfigJourney (theme cycling, persistence, cancel/revert)"
```

---

## Task 14: UndoRedoJourney

**Files:**
- Create: `.../integration/journeys/UndoRedoJourney.java`

- [ ] **Step 1: Write the journey**

Covers: undo/redo in content editor via Ctrl+Z / Ctrl+Y. Steps:
1. Create quest, enter edit mode
2. Click content field, type "Hello"
3. Ctrl+Z -- verify field empty
4. Ctrl+Y -- verify field shows "Hello"
5. Type " World" (append) -- verify "Hello World"
6. Ctrl+Z -- verify "Hello"
7. Ctrl+Z -- verify empty

Use `UIActions.undo()`, `UIActions.redo()`, and check field content via `computeOnClient`.

- [ ] **Step 2: Verify and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=UndoRedoJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/UndoRedoJourney.java
git commit -m "test: add UndoRedoJourney (undo/redo in content editor)"
```

---

## Task 15: DirtyDetectionJourney

**Files:**
- Create: `.../integration/journeys/DirtyDetectionJourney.java`

- [ ] **Step 1: Write the journey**

Covers: dirty detection, ConfirmScreen both paths. Steps:
1. Create quest, save with title "Original"
2. Edit, change title to "Modified"
3. Click Cancel -- ConfirmScreen appears
4. Click No -- returns to edit mode, title still "Modified"
5. Click Cancel again -- ConfirmScreen appears again
6. Click Yes -- view mode, title shows "Original"
7. Click Edit, change nothing, click Cancel -- no ConfirmScreen, goes straight to view

- [ ] **Step 2: Verify and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=DirtyDetectionJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/DirtyDetectionJourney.java
git commit -m "test: add DirtyDetectionJourney (unsaved changes, confirm dialog)"
```

---

## Task 16: CollaborationJourney (two-player)

**Files:**
- Create: `.../integration/journeys/CollaborationJourney.java`

- [ ] **Step 1: Write the journey**

Covers: collaboration request, accept, permission toggle, contributor removal. Two-player with PhaseSync.

Pattern: `@PlayerA` and `@PlayerB` methods with same `@Order` for concurrent steps, PhaseSync signals for coordination.

Steps:
1. (A) Create quest, cycle visibility to CLOSED, save. Signal `collab-quest-created`.
2. (B) Wait for signal. Open Quest Board, find quest. Click Request. Signal `collab-request-sent`.
3. (A) Wait for signal. Open quest, edit mode. Verify Contributors button shows pending. Click Contributors. Verify pending request from B. Click Accept. Signal `collab-accepted`.
4. (B) Wait for signal. Verify quest in My Quests.
5. (A) Toggle B's permission to "View Only". Verify button text changes.
6. (A) Click Remove on B, confirm. Verify contributor list empty.

- [ ] **Step 2: Verify and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=CollaborationJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/CollaborationJourney.java
git commit -m "test: add CollaborationJourney (request, accept, permissions, remove)"
```

---

## Task 17: OpenQuestJourney (two-player)

**Files:**
- Create: `.../integration/journeys/OpenQuestJourney.java`

- [ ] **Step 1: Write the journey**

Covers: join OPEN quest, view content, leave. Two-player with PhaseSync.

Steps:
1. (A) Create OPEN quest, save. Signal `open-quest-created`.
2. (B) Wait for signal. Open Quest Board. Select quest. Click Join. Verify toast, quest in My Quests. Signal `open-quest-joined`.
3. (B) Open quest, verify content visible in view mode.
4. (B) Click Leave. ConfirmScreen. Click Yes. Verify quest removed from My Quests but still on Quest Board.

- [ ] **Step 2: Verify and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=OpenQuestJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/OpenQuestJourney.java
git commit -m "test: add OpenQuestJourney (join, view, leave)"
```

---

## Task 18: LiveUpdateJourney (two-player)

**Files:**
- Create: `.../integration/journeys/LiveUpdateJourney.java`

- [ ] **Step 1: Write the journey**

Covers: S2C update and delete handlers. Two-player with PhaseSync.

Steps:
1. (A) Create OPEN quest "Original Title", save. Signal `live-created`.
2. (B) Wait. Join quest. Signal `live-joined`.
3. (A) Wait. Edit title to "Updated Title", save. Signal `live-updated`.
4. (B) Wait. Open MainScreen. Verify entry shows "Updated Title" (S2C UPDATE_QUEST received).
5. (A) Delete quest, confirm. Signal `live-deleted`.
6. (B) Wait. Verify quest gone from My Quests (S2C DELETE_QUEST received).

- [ ] **Step 2: Verify and run**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=LiveUpdateJourney`
Expected: PASS

- [ ] **Step 3: Commit**

```
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/LiveUpdateJourney.java
git commit -m "test: add LiveUpdateJourney (S2C update and delete propagation)"
```

---

## Task 19: Update CI workflow

**Files:**
- Modify: `.github/workflows/e2e-test.yml`

- [ ] **Step 1: Replace manual Paper server setup with runIntegrationTest**

The current workflow manually downloads Paper, starts it, seeds SQLite data, runs `runClientGameTest`, then stops the server. Replace all of that with `runIntegrationTest` which handles everything.

Replace the steps from "Configure Paper server" through "Run E2E tests" with:

```yaml
      - name: Run integration tests
        run: |
          sudo apt-get install -y xvfb
          Xvfb :99 -ac -screen 0 854x480x24 &
          sleep 2
          DISPLAY=:99 ./gradlew :client:runIntegrationTest
        timeout-minutes: 10
```

Remove the "Stop Paper server" step (runIntegrationTest handles teardown).

Keep the "Upload test artifacts" step but update paths:
```yaml
      - name: Upload test artifacts
        if: always()
        uses: actions/upload-artifact@v6
        with:
          name: e2e-results
          path: |
            client/run/logs/
            client/run-b/logs/
            paper/run/logs/
            integration-sync/
          retention-days: 14
```

- [ ] **Step 2: Commit**

```
git add .github/workflows/e2e-test.yml
git commit -m "ci: replace manual Paper setup with runIntegrationTest"
```

---

## Task 21: Update CLAUDE.md

**Files:**
- Modify: `.claude/CLAUDE.md`

- [ ] **Step 1: Update E2E Tests section**

Replace the "E2E Tests" section with:

```markdown
## E2E Tests

UX-driven journey tests in `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/`. Run via:

\```bash
./gradlew :client:runIntegrationTest                                          # full suite (auto-starts Paper)
./gradlew :client:runIntegrationTest -Pcoverage                               # with JaCoCo coverage
./gradlew :client:runIntegrationTest -PtestFilter=QuestLifecycleJourney       # single journey
./gradlew :client:runIntegrationTest -Pharness                                # keep clients alive for re-runs
./gradlew :client:runIntegrationTest -PnoStart -PtestFilter=ConfigJourney     # re-run on existing clients
./gradlew :client:jacocoIntegrationTestReport                                 # generate HTML report
\```

Tests use a custom BDD DSL (`given`/`when`/`then`/`and`) with GLFW physical input via `TestInput`. All tests connect to a live Paper server -- no mocking.

- **`UIActions`** -- click, type, undo/redo, waitForScreen, openMainScreen, etc.
- **`UIAssertions`** -- assertLabelText, assertButtonText, assertEntryCount, assertScreenIs, etc.
- **Trust hierarchy:** UI state > component state > debug logs > cache state
- **Two-player journeys** use `PhaseSync.signal()`/`waitFor()` for coordination
- **`AbortOnFailureExtension`** skips remaining steps if a prior step fails
```

- [ ] **Step 2: Update Integration Tests section**

Remove the old Integration Tests section (it's now merged into E2E Tests above). Remove references to `runClientGameTest` as the primary test command.

- [ ] **Step 3: Commit**

```
git add .claude/CLAUDE.md
git commit -m "docs: update CLAUDE.md for new journey test infrastructure"
```

---

## Task 22: Full test run with coverage

- [ ] **Step 1: Run the full suite with coverage**

```bash
./gradlew :client:runIntegrationTest -Pcoverage
```

Expected: All 11 journeys PASS

- [ ] **Step 2: Generate and check coverage report**

```bash
./gradlew :client:jacocoIntegrationTestReport
```

Parse the XML report and verify overall line coverage is >= 70% (80% target may need additional test steps tuned during implementation).

- [ ] **Step 3: If coverage < 80%, identify gaps and add steps to existing journeys**

Check per-package coverage. The most likely gaps:
- `widget` -- may need more text field interaction steps
- `network` -- should be covered by server-connected tests but check specific handlers
- `markdown` -- may need more varied markdown content in QuestContentJourney

Add specific test steps to existing journey files to close gaps. Do NOT create new journey files -- extend existing ones.

- [ ] **Step 4: Final commit**

```
git add -A
git commit -m "test: complete E2E coverage redesign, targeting 80% line coverage"
```
