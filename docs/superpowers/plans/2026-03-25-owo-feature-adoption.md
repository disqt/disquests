# owo-lib Feature Adoption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace hand-rolled client infrastructure with owo-lib equivalents: config, templates, lang, overlay/collapsible/draggable containers, and HUD pin refactor.

**Architecture:** Six feature areas, all client-side. No Paper plugin changes. Single PR (individual commits, squash on merge). Tasks ordered by dependency: lang first (zero code risk), then config (build changes), then templates (XML refactor), then containers (UI changes), then HUD refactor (highest risk), then test updates. **Note:** Tasks 1-6 may leave E2E tests in a broken state until Task 7 updates them -- this is expected for the single-PR approach.

**Tech Stack:** owo-lib v0.13.0+1.21.11 (config annotations, XML templates, overlay/collapsible/draggable containers, JSON5/nested lang/rich translations), Fabric API, JUnit 5 E2E harness.

**Spec:** `docs/superpowers/specs/2026-03-25-owo-feature-adoption-design.md`

---

## Task 1: Lang Modernization

**Files:**
- Create: `client/src/main/resources/owo-json5` (empty marker file)
- Create: `client/src/main/resources/assets/disquests/lang/en_us.json5` (replaces en_us.json)
- Create: `client/src/main/resources/assets/disquests/lang/en_pt.json5` (replaces en_pt.json)
- Create: `client/src/main/resources/assets/disquests/lang/zh_cn.json5` (replaces zh_cn.json)
- Delete: `client/src/main/resources/assets/disquests/lang/en_us.json`
- Delete: `client/src/main/resources/assets/disquests/lang/en_pt.json`
- Delete: `client/src/main/resources/assets/disquests/lang/zh_cn.json`

No Java code references `buildnotes` translation keys (verified via grep -- all `Text.translatable` calls already use `disquests` keys). The `buildnotes` keys in the lang files are dead entries from the original fork.

- [ ] **Step 1: Create `owo-json5` marker file**

Create an empty file at `client/src/main/resources/owo-json5`. This enables JSON5 lang loading, nested lang, and rich translations globally.

- [ ] **Step 2: Convert `en_us.json` to `en_us.json5` with nested structure**

Create `en_us.json5` with nested lang syntax. Remove all `buildnotes` keys. Add missing `key.disquests.openconfig`. Add config translation keys for owo-config (needed by Task 2). Structure:

```json5
{
  // Keybinds
  "key.category.disquests.main": "Disquests",
  "key.disquests.{}": {
    "opengui": "Open Disquests",
    "togglepin": "Toggle HUD Pin",
    "openconfig": "Open Config",
  },

  // GUI
  "gui.disquests.{}": {
    "main_title": "Disquests",
    "add_button": "Add",
    "edit_button": "Edit",
    "delete_button": "Delete",
    "open_button": "Open",
    "save_button": "Save",
    "close_button": "Close",
    "cancel_button": "Cancel",
    "confirm_button": "Confirm",

    "edit.{}": {
      "coords": "Coords",
      "dimension": "Dimension",
      "biome": "Biome",
      "add_images": "Add Images",
      "add_field": "Add Field",
      "remove_field": "X",
      "scope_button": "Scope: %s",
      "scope.{}": {
        "global": "Global",
        "server": "Server (Shared)",
        "world": "World",
        "per_server": "Per-Server",
      },
    },

    "placeholder.{}": {
      "title": "Enter Title Here",
      "note_content": "Enter Text Here",
      "build_name": "Build Name",
      "coords": "X, Y, Z",
      "dimension": "minecraft:overworld",
      "description": "Build Description",
      "credits": "Designer Credits",
    },

    "gallery.{}": {
      "previous": "<",
      "next": ">",
      "delete": "X",
      "loading": "Loading image...",
      "error": "Error loading image",
    },

    "label.{}": {
      "coords": "Coords:",
      "dimension": "Dimension:",
      "description": "Description:",
      "credits": "Designer/Credits:",
    },

    "prompt.field_title": "Enter Field Title",

    "edit_note_title": "Editing Note",
    "edit_build_title": "Editing Build",
    "notes_tab": "Notes",
    "builds_tab": "Builds",
  },

  // owo-config translations
  "text.config.disquests-config.{}": {
    "title": "Disquests Config",
    "option.pinnedWidth": "Pinned Width",
    "option.theme": "Theme",
    "option.pinnedX": "Pin X Position",
    "option.pinnedY": "Pin Y Position",
    "category.hud": "HUD",
    "category.colors": "Colors",
  },
}
```

- [ ] **Step 3: Convert `en_pt.json` to `en_pt.json5`**

Same nested structure as en_us but with pirate/lore translations. Remove all `buildnotes` keys. Add the same new keys (`openconfig`, owo-config translations).

- [ ] **Step 4: Convert `zh_cn.json` to `zh_cn.json5`**

Same nested structure with Chinese translations. Remove all `buildnotes` keys. Add the same new keys.

- [ ] **Step 5: Delete old `.json` lang files**

Delete `en_us.json`, `en_pt.json`, `zh_cn.json` from `client/src/main/resources/assets/disquests/lang/`.

- [ ] **Step 5b: Add rich translations for title branding**

Add at least one rich translation entry to `en_us.json5`. The main title is a good candidate:

```json5
"gui.disquests.main_title": [
    "Dis",
    { "text": "quests", "color": "#FFB300" }
],
```

Apply the same treatment to `en_pt.json5` and `zh_cn.json5`. Rich translations are optional -- only add them where colored text adds value. Don't force it on every entry.

- [ ] **Step 6: Build to verify lang loading**

Run: `./gradlew :client:build`

Expected: BUILD SUCCESS. No compilation errors. The JSON5 files are processed at runtime by owo-lib, so compile-time verification is limited -- full verification happens in E2E tests (Task 7).

- [ ] **Step 7: Commit**

```
feat: modernize lang files to JSON5 with nested lang

- Convert .json -> .json5 with owo nested lang syntax
- Add owo-json5 marker file for JSON5 data loading
- Remove dead buildnotes keys (clean break from fork)
- Add missing key.disquests.openconfig translation
- Add owo-config translation keys
```

---

## Task 2: owo-config Migration

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfigModel.java`
- Modify: `client/build.gradle.kts` (add annotationProcessor)
- Modify: `client/src/main/java/com/disqt/disquests/client/DisquestsClient.java` (load config)
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/Colors.java` (hook-based reload)
- Modify: `client/src/main/java/com/disqt/disquests/client/KeyBinds.java` (open auto-generated screen)
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java` (config access)
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java` (config access)
- Modify: `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java` (config access)
- Modify: `client/src/main/resources/fabric.mod.json` (remove modmenu entrypoint)
- Delete: `client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfig.java`
- Delete: `client/src/main/java/com/disqt/disquests/client/gui/helper/ColorConfig.java`
- Delete: `client/src/main/java/com/disqt/disquests/client/gui/screen/ConfigScreen.java`
- Delete: `client/src/main/java/com/disqt/disquests/client/compat/ModMenuIntegration.java`
- Delete: `client/src/main/resources/assets/disquests/owo_ui/config_screen.xml`

**API verification spike:** Before implementing, verify these owo-config APIs against owo-lib v0.13.0:
1. Does `@Modmenu` accept a `modId` parameter, or is it inferred from `@Config(name=...)`?
2. Does owo-config support `Map<String, String>` fields? If not, store color overrides in a separate `colors.json` with simple Gson loading.
3. What is the exact `@Hook` generated method signature? (e.g., `subscribeToTheme(Consumer<Theme>)`)

Check the owo-lib source or Context7 docs to verify before writing code.

- [ ] **Step 1: Add annotationProcessor to build.gradle.kts**

In `client/build.gradle.kts`, after the existing `modImplementation("io.wispforest:owo-lib:$owo_version")` line (~line 49), add:

```kotlin
annotationProcessor("io.wispforest:owo-lib:$owo_version")
```

- [ ] **Step 2: Create DisquestsConfigModel.java**

Create `client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfigModel.java`:

```java
package com.disqt.disquests.client.gui.helper;

import io.wispforest.owo.config.annotation.*;
import java.util.HashMap;
import java.util.Map;

@Modmenu(modId = "disquests")
@Config(name = "disquests-config", wrapperName = "DisquestsConfigWrapper")
public class DisquestsConfigModel {

    @RangeConstraint(min = 100, max = 400)
    public int pinnedWidth = 200;

    @Hook
    public Theme theme = Theme.FROSTED;

    @SectionHeader("hud")
    public int pinnedX = -1;
    public int pinnedY = -1;

    @SectionHeader("colors")
    @ExcludeFromScreen
    public Map<String, String> colorOverrides = new HashMap<>();
}
```

**Note:** The wrapper is named `DisquestsConfigWrapper` to avoid conflict with the old `DisquestsConfig.java` during migration. After deleting the old class (Step 5), consider renaming if desired. Alternatively, delete the old class first and name the wrapper `DisquestsConfig`.

- [ ] **Step 3: Build to trigger annotation processor**

Run: `./gradlew :client:build`

This generates the wrapper class. Verify it compiles. If `Map<String, String>` causes an annotation processor error, remove the `colorOverrides` field and keep color loading in a simplified `ColorConfig` (Gson, no reflection).

If `@Modmenu(modId = "disquests")` fails, try `@Modmenu` without parameters or check the owo-lib source for the correct syntax.

- [ ] **Step 4: Update DisquestsClient.java entrypoint**

Replace the config loading block (lines 22-24):

```java
// OLD:
DisquestsConfig.load();
DisquestsConfig.getTheme().applyColors();
ColorConfig.loadColors();

// NEW:
public static final DisquestsConfigWrapper CONFIG = DisquestsConfigWrapper.createAndLoad();

// In onInitializeClient():
CONFIG.theme().applyColors();
// Apply color overrides from config
Colors.applyOverrides(CONFIG.colorOverrides());
// Subscribe to theme changes
CONFIG.subscribeToTheme(newTheme -> {
    newTheme.applyColors();
    Colors.applyOverrides(CONFIG.colorOverrides());
});
```

Also update the keybind handler (lines 41-45) -- remove direct `ConfigScreen` creation. This is handled in Step 7.

- [ ] **Step 5: Update Colors.java (before deleting old files)**

Replace the `reload()` method (line 59-62) with a static `applyOverrides` method:

```java
public static void applyOverrides(Map<String, String> overrides) {
    for (Map.Entry<String, String> entry : overrides.entrySet()) {
        try {
            Field field = Colors.class.getField(entry.getKey());
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                field.set(null, parseColor(entry.getValue()));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not apply color override '{}': {}", entry.getKey(), e.getMessage());
        }
    }
}
```

Move the `parseColor` method from `ColorConfig.java` into `Colors.java` (or a private helper). Remove the `reload()` method and the `ColorConfig` import.

- [ ] **Step 6: Delete old config files**

Delete these files:
- `client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfig.java`
- `client/src/main/java/com/disqt/disquests/client/gui/helper/ColorConfig.java`
- `client/src/main/java/com/disqt/disquests/client/gui/screen/ConfigScreen.java`
- `client/src/main/java/com/disqt/disquests/client/compat/ModMenuIntegration.java` (also delete the empty `compat/` directory)
- `client/src/main/resources/assets/disquests/owo_ui/config_screen.xml`

**Note:** `ConfigScreen.resetOriginalTheme()` is referenced in test code (`ConfigJourney.java` line 33). This will cause a compile error in testmod until Task 7 rewrites `ConfigJourney`. This is expected -- the testmod classpath is separate from the main build.

- [ ] **Step 7: Update all DisquestsConfig call sites**

Replace each call site:

| File | Old | New |
|------|-----|-----|
| `DisquestsBaseScreen.java:60` | `DisquestsConfig.getTheme()` | `DisquestsClient.CONFIG.theme()` |
| `DisquestsBaseScreen.java:64` | `DisquestsConfig.getTheme()` | `DisquestsClient.CONFIG.theme()` |
| `QuestEntryComponent.java:203` | `DisquestsConfig.getTheme()` | `DisquestsClient.CONFIG.theme()` |
| `HudPinRenderer.java:26` | `DisquestsConfig.getPinnedWidth()` | `DisquestsClient.CONFIG.pinnedWidth()` |

- [ ] **Step 8: Update KeyBinds.java**

Replace the config screen opening (in `DisquestsClient.java` lines 41-45):

```java
// OLD:
while (KeyBinds.openConfigKey.wasPressed()) {
    if (client.currentScreen == null) {
        client.setScreen(new ConfigScreen(null));
    }
}

// NEW:
while (KeyBinds.openConfigKey.wasPressed()) {
    if (client.currentScreen == null) {
        client.setScreen(DisquestsClient.CONFIG.createScreen(null));
    }
}
```

**Note:** Verify the owo-config wrapper's screen creation method name. It may be `createScreen(Screen parent)` or a different signature.

- [ ] **Step 9: Update fabric.mod.json**

Remove the `modmenu` entrypoint block (lines 25-27):

```json
// REMOVE:
"modmenu": [
    "com.disqt.disquests.client.compat.ModMenuIntegration"
]
```

The `"entrypoints"` block should only contain `"client"`.

- [ ] **Step 10: Build and verify**

Run: `./gradlew :client:build`

Expected: BUILD SUCCESS. All old config references resolved.

- [ ] **Step 11: Commit**

```
feat: migrate config to owo-config annotation system

- Add DisquestsConfigModel with @Config, @Hook, @RangeConstraint
- Delete hand-rolled DisquestsConfig, ColorConfig, ConfigScreen
- Delete ModMenuIntegration (owo-config @Modmenu replaces it)
- Delete config_screen.xml
- Update all config call sites to use generated wrapper
- Theme changes now trigger via @Hook subscription
- Color overrides stored in config Map, applied via Colors.applyOverrides
```

---

## Task 3: XML Templates

**Files:**
- Create: `client/src/main/resources/assets/disquests/owo_ui/templates.xml`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/TagPickerScreen.java`
- Modify: `client/src/main/resources/assets/disquests/owo_ui/main_screen.xml`
- Modify: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml`
- Modify: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_edit.xml`
- Modify: `client/src/main/resources/assets/disquests/owo_ui/contributor_screen.xml`
- Modify: `client/src/main/resources/assets/disquests/owo_ui/tag_picker_screen.xml`

**Critical constraint:** Component IDs must not change. All `childById` calls in Java and E2E tests depend on these IDs.

**Template expansion timing:** Use `model.expandTemplate()` in `build()`. Verify during implementation that the model is available at this point. If not, fall back to `init()` after `super.init()`.

- [ ] **Step 1: Create templates.xml**

Create `client/src/main/resources/assets/disquests/owo_ui/templates.xml`:

```xml
<owo-ui xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:noNamespaceSchemaLocation="https://raw.githubusercontent.com/wisp-forest/owo-lib/1.21/owo-ui.xsd">
    <components>
        <flow-layout direction="vertical"/>
    </components>
    <templates>
        <template name="dark-panel">
            <flow-layout direction="{{direction}}">
                <sizing>
                    <horizontal method="{{h-method}}">{{h-value}}</horizontal>
                    <vertical method="{{v-method}}">{{v-value}}</vertical>
                </sizing>
                <surface><panel dark="true"/></surface>
                <padding><all>{{padding}}</all></padding>
                <gap>{{gap}}</gap>
                <children/>
            </flow-layout>
        </template>

        <template name="button-row">
            <flow-layout direction="horizontal">
                <sizing>
                    <horizontal method="content"/>
                    <vertical method="content"/>
                </sizing>
                <gap>4</gap>
                <margins><top>4</top><bottom>4</bottom></margins>
                <children/>
            </flow-layout>
        </template>

        <template name="scrollable-list">
            <scroll direction="vertical" id="{{scroll-id}}">
                <flow-layout direction="vertical" id="{{list-id}}">
                    <sizing>
                        <horizontal method="fill">100</horizontal>
                        <vertical method="content"/>
                    </sizing>
                    <gap>2</gap>
                </flow-layout>
                <sizing>
                    <horizontal method="fill">{{width}}</horizontal>
                    <vertical method="fill">{{height}}</vertical>
                </sizing>
                <surface><panel dark="true"/></surface>
                <padding><all>4</all></padding>
            </scroll>
        </template>

        <template name="dialog-panel">
            <flow-layout direction="vertical">
                <sizing>
                    <horizontal method="content"/>
                    <vertical method="content"/>
                </sizing>
                <surface><panel dark="true"/></surface>
                <padding><all>12</all></padding>
                <horizontal-alignment>center</horizontal-alignment>
                <gap>8</gap>
                <children/>
            </flow-layout>
        </template>
    </templates>
</owo-ui>
```

- [ ] **Step 2: Refactor one screen XML as a prototype**

Start with `tag_picker_screen.xml` (simplest dialog-style screen). Replace the inline dark panel + button row patterns with template references from Java. Verify the screen still renders correctly by running the client.

The Java screen code uses `model.expandTemplate()`:

```java
FlowLayout dialogPanel = this.model.expandTemplate(
    FlowLayout.class,
    "dialog-panel@disquests:templates",
    Map.of()
);
```

Verify that `childById` still works for components placed inside expanded templates.

- [ ] **Step 3: Refactor main_screen.xml**

Apply templates to `main_screen.xml` (scrollable-list for quest list, button-row for action buttons, dark-panel for filter area). Update `MainScreen.java` with `expandTemplate()` calls. Verify component IDs preserved.

- [ ] **Step 4a: Refactor quest_screen_view.xml**

Apply templates (dark-panel for content area, button-row for action buttons). Update `QuestScreen.java` view mode. Verify component IDs.

- [ ] **Step 4b: Refactor quest_screen_edit.xml**

Apply templates (dark-panel for editor areas, button-row). Update `QuestScreen.java` edit mode. Verify component IDs.

- [ ] **Step 4c: Refactor contributor_screen.xml**

Apply templates (scrollable-list for contributor/request lists, button-row, dark-panel). Update `ContributorScreen.java`. Verify component IDs.

- [ ] **Step 5: Build and verify**

Run: `./gradlew :client:build`

Expected: BUILD SUCCESS. Run `./gradlew :client:runClient` to visually verify screens render correctly.

- [ ] **Step 6: Commit**

```
refactor: extract XML templates for shared UI patterns

- Create templates.xml with dark-panel, button-row, scrollable-list, dialog-panel
- Replace inline duplicated patterns with expandTemplate() calls
- Preserves all component IDs for E2E test compatibility
```

---

## Task 4: Overlay Container (replaces ConfirmScreen)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java`
- Delete: `client/src/main/java/com/disqt/disquests/client/gui/screen/ConfirmScreen.java`
- Delete: `client/src/main/resources/assets/disquests/owo_ui/confirm_screen.xml`

**API verification:** Before implementing, check the exact owo-lib v0.13.0 API for `Containers.overlay()`:
- Method signature and return type
- How dismissal works (click outside, escape key)
- Whether it floats above siblings in a FlowLayout or needs special handling

Check owo-lib source at `io.wispforest.owo.ui.container.OverlayContainer` or Context7 docs.

- [ ] **Step 1: Verify overlay API**

Read the owo-lib overlay API. Confirm:
1. `Containers.overlay(Component child)` exists and returns an overlay component
2. There is a method to configure click-outside-to-close behavior
3. It renders as a floating overlay (not participating in parent flow layout)

Document the exact API in a code comment for future reference.

- [ ] **Step 2: Add showConfirmOverlay to DisquestsBaseScreen**

Add to `DisquestsBaseScreen.java`:

```java
protected void showConfirmOverlay(Text message, Runnable onConfirm) {
    showConfirmOverlay(message, onConfirm, () -> {});
}

protected void showConfirmOverlay(Text message, Runnable onConfirm, Runnable onCancel) {
    // Build confirm panel
    var messageLabel = UIComponents.label(message);
    messageLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);

    var yesButton = UIComponents.button(Text.translatable("gui.disquests.confirm_button"), b -> {
        dismissOverlay();
        onConfirm.run();
    });
    yesButton.id("btn-confirm-yes");
    yesButton.sizing(Sizing.fixed(60), Sizing.fixed(20));

    var noButton = UIComponents.button(Text.translatable("gui.disquests.cancel_button"), b -> {
        dismissOverlay();
        onCancel.run();
    });
    noButton.id("btn-confirm-no");
    noButton.sizing(Sizing.fixed(60), Sizing.fixed(20));

    var buttonRow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
    buttonRow.gap(4);
    buttonRow.child(yesButton).child(noButton);

    var panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
    panel.surface(Surface.DARK_PANEL);
    panel.padding(Insets.of(12));
    panel.gap(8);
    panel.horizontalAlignment(HorizontalAlignment.CENTER);
    panel.child(messageLabel).child(buttonRow);

    var overlay = Containers.overlay(panel);
    overlay.id("confirm-overlay");
    this.uiAdapter.rootComponent.child(overlay);
}

protected void dismissOverlay() {
    var overlay = this.uiAdapter.rootComponent.childById(OverlayContainer.class, "confirm-overlay");
    if (overlay != null) {
        this.uiAdapter.rootComponent.removeChild(overlay);
    }
}
```

**Note:** Adjust API calls based on Step 1 verification. The `OverlayContainer` class name and method signatures may differ.

- [ ] **Step 3: Replace ConfirmScreen calls in QuestScreen**

In `QuestScreen.java`, replace three call sites. **Key simplification:** the old `ConfirmScreen` needed `onCancel` callbacks to navigate back to the parent screen. With overlays, cancel just dismisses the overlay -- the parent screen is still underneath. So most `onCancel` callbacks become empty or unnecessary (use the two-argument `showConfirmOverlay` form).

Line 529 (discard unsaved changes):
```java
// OLD: navigateToScreen(new ConfirmScreen(this, ..., discardRunnable, backToEditRunnable));
// NEW: showConfirmOverlay(Text.translatable("..."), () -> { /* discard + navigate to view */ });
// onCancel is just overlay dismissal -- no callback needed
```

Line 569 (delete quest):
```java
showConfirmOverlay(Text.literal("Delete quest?"), () -> { /* delete logic */ });
```

Line 582 (leave quest):
```java
showConfirmOverlay(Text.literal("Leave quest?"), () -> { /* leave logic */ });
```

- [ ] **Step 4: Replace ConfirmScreen call in ContributorScreen**

In `ContributorScreen.java` line 166 (remove contributor):
```java
showConfirmOverlay(Text.literal("Remove contributor?"), () -> { /* remove logic */ });
```

- [ ] **Step 5: Delete ConfirmScreen files**

Delete:
- `client/src/main/java/com/disqt/disquests/client/gui/screen/ConfirmScreen.java`
- `client/src/main/resources/assets/disquests/owo_ui/confirm_screen.xml`

- [ ] **Step 6: Build and verify**

Run: `./gradlew :client:build`

Expected: BUILD SUCCESS. No remaining references to `ConfirmScreen`.

- [ ] **Step 7: Commit**

```
feat: replace ConfirmScreen with overlay container

- Add showConfirmOverlay/dismissOverlay to DisquestsBaseScreen
- Replace all ConfirmScreen navigations with in-place overlay modals
- Delete ConfirmScreen.java and confirm_screen.xml
```

---

## Task 5: Collapsible Container

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java` (view mode)
- Modify: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml`

- [ ] **Step 1: Add collapsible contributor list in quest view XML**

In `quest_screen_view.xml`, wrap the contributors-row section in a collapsible container:

```xml
<collapsible expanded="false" id="contributors-collapse">
    <text>Contributors</text>
    <children>
        <flow-layout direction="horizontal" id="contributors-row">
            <!-- existing contributor label -->
        </flow-layout>
    </children>
</collapsible>
```

- [ ] **Step 2: Add collapsible tags section**

In `quest_screen_view.xml`, wrap the tag-display section in a collapsible container:

```xml
<collapsible expanded="false" id="tags-collapse">
    <text>Tags</text>
    <children>
        <flow-layout direction="horizontal" id="tag-display">
            <!-- tags populated programmatically -->
        </flow-layout>
    </children>
</collapsible>
```

- [ ] **Step 3: Update QuestScreen.java view mode**

Update the Java code that populates contributors (lines 225-235) and tags (lines 154-169) to:
1. Set the collapsible title text dynamically: `"Contributors (3)"`, `"Tags (2)"`
2. Auto-expand when there are few items (e.g., <=3 contributors)
3. Ensure `childById` lookups for `contributors-row` and `tag-display` still work through the collapsible wrapper

- [ ] **Step 4: Build and verify visually**

Run: `./gradlew :client:runClient`

Verify: collapsible sections render correctly, expand/collapse on click, correct counts in titles.

- [ ] **Step 5: Commit**

```
feat: add collapsible containers for contributors and tags

- Wrap contributors and tags in collapsible sections on quest view
- Auto-expand when few items, collapsed by default otherwise
- Dynamic title shows item count
```

---

## Task 6: HUD Pin Refactor (Draggable Container)

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/hud/HudOverlay.java`
- Create: `client/src/main/java/com/disqt/disquests/client/hud/HudPinComponent.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/hud/HudPinManager.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientSession.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/DisquestsClient.java`
- Modify: `client/src/main/resources/disquests.mixins.json`
- Delete: `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java`
- Delete: `client/src/main/java/com/disqt/disquests/client/mixin/InGameHudMixin.java`

**This is the highest-risk task.** Start with a spike to validate the approach.

- [ ] **Step 1: Spike -- validate owo-ui HUD overlay approach**

Research and test whether owo-ui can render a persistent component tree during gameplay (outside a `Screen` context).

Check:
1. Can `OwoUIAdapter` be created and rendered outside a `Screen`?
2. How does Fabric's `HudRenderCallback` interact with owo-ui?
3. Is there a pattern in the owo-lib source or community for HUD overlays?

**Go/no-go criteria for full overlay approach:**
- GO: `OwoUIAdapter` can render a component tree during `HudRenderCallback` or equivalent, AND mouse events can be routed for drag interaction without opening a `Screen`
- NO-GO: either condition fails -> use hybrid fallback

If owo-ui cannot render outside a `Screen`, use the **hybrid fallback**: keep `InGameHudMixin` for the render hook, but port the rendering logic to `HudPinComponent` (an owo-ui `BaseUIComponent` that renders via raw draw calls inside the mixin). Drag repositioning uses a keybind to open a transparent `Screen` with the draggable container.

Document the chosen approach before proceeding.

- [ ] **Step 2: Create HudPinComponent**

Create `client/src/main/java/com/disqt/disquests/client/hud/HudPinComponent.java`:

Port the rendering logic from `HudPinRenderer.java` into a `BaseUIComponent` subclass:
- `draw()`: renders pinned quest markdown with word-wrap, caching
- Reads pin width from `DisquestsClient.CONFIG.pinnedWidth()`
- Reads pin position from `DisquestsClient.CONFIG.pinnedX()` / `pinnedY()`
- Uses `HudPinManager` for pin list state

- [ ] **Step 3: Create HudOverlay (or hybrid mixin)**

**If full owo-ui overlay works (from spike):**

Create `HudOverlay.java` that manages a persistent owo-ui layer:
- Wraps `HudPinComponent` in `Containers.draggable()`
- Registers on world join, tears down on disconnect
- Persists drag position to config on release

**If hybrid fallback:**

Keep `InGameHudMixin.java`. Modify it to instantiate and render `HudPinComponent` directly:
```java
@Inject(method = "render", at = @At("TAIL"))
private void renderPinnedQuest(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
    HudPinComponent.renderHud(context);
}
```

For drag: add a keybind that opens a transparent `Screen` containing the draggable container. On close, save position to config.

- [ ] **Step 4: Wire up lifecycle in ClientSession**

In `ClientSession.java`:
- `joinServer()`: Initialize HUD overlay / component
- `leaveServer()`: Tear down HUD overlay / component

- [ ] **Step 5: Update disquests.mixins.json**

If full owo-ui overlay: remove `InGameHudMixin` from the mixin list.
If hybrid: keep it.

```json
{
  "client": [
    "InventoryBadgeMixin"
  ]
}
```

- [ ] **Step 6: Delete old files (if full overlay approach)**

Delete:
- `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java`
- `client/src/main/java/com/disqt/disquests/client/mixin/InGameHudMixin.java` (only if full overlay)

- [ ] **Step 7: Build and verify**

Run: `./gradlew :client:runClient`

Verify:
1. Pinned quests render on the HUD during gameplay
2. Pin visibility toggles via keybind
3. Pin survives screen opens/closes
4. (If draggable) drag repositioning works and persists

- [ ] **Step 8: Commit**

```
feat: refactor HUD pin to owo-ui component with drag support

- Port HudPinRenderer to HudPinComponent (BaseUIComponent)
- [Full overlay: create HudOverlay, delete InGameHudMixin]
- [Hybrid: keep mixin, render owo-ui component inside mixin]
- Wrap in draggable container for user repositioning
- Persist pin position via owo-config (pinnedX, pinnedY)
```

---

## Task 7: E2E Test Updates

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/harness/UIActions.java` (or equivalent helper)
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/ConfigJourney.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/DirtyDetectionJourney.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/QuestLifecycleJourney.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/duo/TwoPlayerJourneys.java`

- [ ] **Step 1: Add overlay-aware test helpers**

Add to `UIActions.java` (or wherever `waitForScreen` is defined):

```java
public static void waitForOverlay(ClientGameTestContext context, String overlayId) {
    context.waitFor(client -> {
        if (client.currentScreen instanceof DisquestsBaseScreen screen) {
            return screen.getRootComponent().childById(Component.class, overlayId) != null;
        }
        return false;
    }, TIMEOUT);
}

public static void assertOverlayVisible(ClientGameTestContext context, String overlayId) {
    boolean visible = context.computeOnClient(client -> {
        if (client.currentScreen instanceof DisquestsBaseScreen screen) {
            return screen.getRootComponent().childById(Component.class, overlayId) != null;
        }
        return false;
    });
    assertTrue(visible, "Expected overlay '" + overlayId + "' to be visible");
}

public static void assertNoOverlay(ClientGameTestContext context, String overlayId) {
    boolean absent = context.computeOnClient(client -> {
        if (client.currentScreen instanceof DisquestsBaseScreen screen) {
            return screen.getRootComponent().childById(Component.class, overlayId) == null;
        }
        return true;
    });
    assertTrue(absent, "Expected no overlay '" + overlayId + "'");
}
```

- [ ] **Step 2: Update DirtyDetectionJourney**

Replace all `ConfirmScreen` references:

```java
// OLD:
waitForScreen(context, ConfirmScreen.class);
assertScreenIs(context, ConfirmScreen.class);

// NEW:
waitForOverlay(context, "confirm-overlay");
assertOverlayVisible(context, "confirm-overlay");
```

For "no ConfirmScreen appears" assertions (line 154):
```java
// OLD:
context.waitFor(client -> !(client.currentScreen instanceof ConfirmScreen), TIMEOUT);

// NEW:
assertNoOverlay(context, "confirm-overlay");
```

For clicking Yes/No on the overlay, the button IDs inside the overlay need to match. Ensure `showConfirmOverlay` uses IDs like `"btn-confirm-yes"` and `"btn-confirm-no"`, then:
```java
click(context, "btn-confirm-yes");  // or "btn-confirm-no"
```

- [ ] **Step 3: Update QuestLifecycleJourney**

Replace `waitForScreen(context, ConfirmScreen.class)` (line 119) with:
```java
waitForOverlay(context, "confirm-overlay");
```

- [ ] **Step 4: Update TwoPlayerJourneys**

Replace `waitForScreen(context, ConfirmScreen.class)` (lines 260, 357) with:
```java
waitForOverlay(context, "confirm-overlay");
```

- [ ] **Step 5: Rewrite ConfigJourney**

The entire `ConfigJourney.java` needs rewriting since it tests the custom `ConfigScreen` which no longer exists. The owo-config auto-generated screen has different components.

Options:
1. **Minimal approach:** Test that the config screen opens (is an owo-config screen), change a value, save, reopen, verify persistence. Don't test exact button IDs since they're owo-generated.
2. **Skip config UI tests:** Since owo-config generates and tests its own screen, our E2E test can just verify the config values persist correctly without testing the UI.

Recommended: option 1. Write a simplified ConfigJourney that opens the auto-generated screen, interacts with it minimally (verify it opens, close it), and test config persistence via the API.

- [ ] **Step 6: Update PinAndHudJourney**

The HUD rendering mechanism changed (mixin -> owo-ui overlay or hybrid). Update `PinAndHudJourney.java` to:
1. Verify pinned quests still render on the HUD after the refactor
2. If full overlay: test that the owo-ui HUD layer is active
3. If hybrid: verify the mixin still renders `HudPinComponent`
4. Test pin position persistence (if draggable is implemented)

The exact test changes depend on the approach chosen in Task 6 Step 1. At minimum, verify the existing pin/unpin behavior still works.

- [ ] **Step 7: Add lang verification test**

Add a test (in any existing journey, or a new minimal one) that asserts a known translation key resolves correctly:

```java
@Test @Order(1) @PlayerA
@DisplayName("JSON5 lang loading works")
void langKeysResolve(ClientGameTestContext context) {
    String resolved = context.computeOnClient(c ->
        net.minecraft.text.Text.translatable("key.category.disquests.main").getString()
    );
    assertEquals("Disquests", resolved);
}
```

This catches JSON5 loading failures that `./gradlew :client:build` cannot detect.

- [ ] **Step 8: Run full E2E suite**

Run: `./gradlew :client:runIntegrationTest`

Expected: ALL TESTS PASS. If failures, debug and fix.

- [ ] **Step 9: Commit**

```
test: update E2E tests for owo-lib migration

- Add overlay-aware test helpers (waitForOverlay, assertOverlayVisible)
- Replace ConfirmScreen assertions with overlay assertions
- Rewrite ConfigJourney for owo-config auto-generated screen
- Verify all journeys pass after XML template and lang changes
```

---

## Task 8: Final Verification and Cleanup

- [ ] **Step 1: Run full build**

Run: `./gradlew build`

Expected: BUILD SUCCESS across all modules (common, client, server).

- [ ] **Step 2: Run all tests**

Run: `./gradlew :common:test` (unit tests)
Run: `./gradlew :client:runIntegrationTest` (E2E tests)

Expected: ALL PASS.

- [ ] **Step 3: Clean up stale worktrees**

Remove any leftover `.claude/worktrees/` directories from previous agent sessions that show up in globbing.

- [ ] **Step 4: Visual smoke test**

Run: `./gradlew :client:runClient`

Manually verify:
1. Main screen opens (N key), quest list renders
2. Config screen opens (F6), shows owo-config auto-generated UI
3. Create quest, edit, save -- confirm overlay works on cancel with changes
4. Delete quest -- confirm overlay works
5. Pinned quest HUD renders, can be dragged (if full overlay) or positioned (if hybrid)
6. Collapsible contributors/tags on quest view
7. No raw translation keys visible (JSON5 lang loading works)

- [ ] **Step 5: Final commit (if any fixups needed)**

```
chore: post-migration cleanup
```
