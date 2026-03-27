# owo-lib Feature Adoption

Adopt owo-lib features beyond owo-ui to replace hand-rolled infrastructure in the client module. One PR covering all changes.

## Background

Disquests already depends on owo-lib for its UI framework (owo-ui). The library offers config management, lang file extensions, UI containers, and XML templates that we don't use. Our client module has custom implementations for config loading, color parsing, confirmation dialogs, and HUD rendering that owo-lib handles natively.

This design replaces those custom implementations with owo-lib equivalents and adopts new capabilities (collapsible sections, draggable HUD, nested lang).

## Scope

Six feature areas, all client-side (no Paper plugin changes):

1. **owo-config** -- replace `DisquestsConfig`, `ColorConfig`, `ConfigScreen`
2. **XML Templates** -- extract duplicated patterns across 7 screen XML files
3. **Lang modernization** -- JSON5, nested lang, rich translations, buildnotes->disquests rename
4. **Overlay Container** -- replace `ConfirmScreen` with in-place modal
5. **Collapsible Container** -- expandable contributor lists and tags
6. **Draggable Container** -- user-repositionable HUD pin via owo-ui overlay

## 1. owo-config

### Current state

- `DisquestsConfig.java` (72 lines): Gson load/save, manual clamping for `pinnedWidth` (100-400), `Theme` enum storage
- `ColorConfig.java` (118 lines): Reflection-based color field injection into `Colors.java`, hex/rgb/rgba parsing
- `ConfigScreen.java` + `config_screen.xml`: Hand-built owo-ui screen with theme dropdown and width slider

### Target state

A single annotated config model:

```java
@Config(name = "disquests-config", wrapperName = "DisquestsConfig")
public class DisquestsConfigModel {

    @RangeConstraint(min = 100, max = 400)
    public int pinnedWidth = 200;

    @Hook
    public Theme theme = Theme.FROSTED;

    @SectionHeader("hud")
    public int pinnedX = -1;  // -1 = default top-right
    public int pinnedY = -1;

    @SectionHeader("colors")
    @ExcludeFromScreen
    public Map<String, String> colorOverrides = new HashMap<>();
}
```

**Note:** The `@Modmenu` annotation syntax and `Map<String, String>` support need verification against the owo-lib v0.13.0 API during implementation. If `Map` is not a supported config field type, fall back to storing color overrides in a separate JSON file with a simple Gson loader (keeping `Colors.java` reload logic intact).

### Theme change hook

The `@Hook` annotation on `theme` generates a `subscribeToTheme()` method. On config load and on every theme change:

1. Call `theme.applyColors()` to set `Colors.*` fields to the theme's palette
2. Iterate `colorOverrides` map and apply each entry to the matching `Colors` field
3. If a screen is open, reopen it to reflect the new colors

```java
CONFIG.subscribeToTheme(newTheme -> {
    newTheme.applyColors();
    applyColorOverrides(CONFIG.colorOverrides());
});
```

### Changes

- Add `annotationProcessor` dependency on owo-lib in `client/build.gradle.kts`
- Create `DisquestsConfigModel.java` with annotations
- Load via `DisquestsConfig.createAndLoad()` in `DisquestsClient` entrypoint
- Add translation keys: `text.config.disquests-config.option.pinnedWidth`, etc.
- Delete `DisquestsConfig.java`, `ColorConfig.java`, `ConfigScreen.java`, `config_screen.xml`
- Delete `ModMenuIntegration.java` and remove the `modmenu` entrypoint from `fabric.mod.json` (owo-config's `@Modmenu` annotation registers the config screen automatically)
- Update `Colors.java`: replace `reload()` method to use the hook-based approach above instead of calling `ColorConfig.loadColors()`
- Update all call sites: `DisquestsConfig.getTheme()` -> `DisquestsClient.CONFIG.theme()`, etc.
- F6 keybind opens the auto-generated config screen instead of the custom one

### Migration path for existing users

owo-config writes to `disquests-config.json5`. Users with existing files (`config/disquests/config.json` and `config/disquests/colors.json`) keep them but they go unused. The new config starts with defaults. Users who customized colors must re-enter them in the new `colorOverrides` map. Acceptable since the user base is small and only two settings + optional color overrides exist.

## 2. XML Templates

### Current duplication

Six patterns repeat across the screen XML files (5 surviving after `config_screen.xml` and `confirm_screen.xml` are deleted):

| Pattern | Occurrences | Lines saved per use |
|---------|-------------|---------------------|
| Dark panel (surface + padding wrapper) | 8+ | ~10 |
| Button row (horizontal flow with gap/margins) | 7 | ~8 |
| Scrollable list (scroll + inner flow) | 3 | ~15 |
| Dialog panel (centered content panel) | 3 | ~10 |

### Target state

Create `client/src/main/resources/assets/disquests/owo_ui/templates.xml` with four named templates:

- `dark-panel` -- parameterized by direction, sizing, padding, gap
- `button-row` -- no parameters (always horizontal, gap 4, margins 4)
- `scrollable-list` -- parameterized by scroll-id, list-id, width%, height%
- `dialog-panel` -- no parameters (always centered, padding 12, gap 8)

Each screen XML references templates via `model.expandTemplate()` in Java. Screen XMLs shrink; repeated structure moves to one file.

**Template expansion timing:** Call `model.expandTemplate()` in `build()` (not `init()`). In `BaseUIModelScreen`, the model and root component are available during `build()`. The owo-ui reference doc warns about `init()` vs `build()` timing -- verify during implementation that template components are accessible in `build()` and fall back to `init()` if needed.

### What stays in XML

Screen-specific layouts, component IDs, and unique structures remain in each screen's XML. Templates handle only the structural boilerplate. **Component IDs must be preserved** -- the template refactor must not change any IDs that Java code or E2E tests reference via `childById`.

## 3. Lang Modernization

### Current state

- `en_us.json`, `en_pt.json`, `zh_cn.json` with flat key structure
- 57 keys, many still prefixed `buildnotes` (legacy from fork)
- No comments, no styled text

### Target state

- Rename to `.json5` (comments, trailing commas)
- Add `owo-json5` marker file to `client/src/main/resources/` root
- Rename all `buildnotes` -> `disquests` in keys and Java references
- Restructure with nested lang:

```json5
{
  // Keybinds
  "key.category.disquests.main": "Disquests",
  "key.disquests.{}": {
    "opengui": "Open Disquests",
    "togglepin": "Toggle HUD Pin"
  },
  // GUI strings
  "gui.disquests.{}": {
    "main_title": "Disquests",
    "add_button": "Add",
    "edit.{}": {
      "coords": "Coords",
      "dimension": "Dimension",
      // ...
    },
    "placeholder.{}": {
      "title": "Enter Title Here",
      // ...
    }
  }
}
```

- Rich translations for select entries where colored text is useful (e.g. title branding)
- Update all `Text.translatable("gui.buildnotes.*")` and `"key.buildnotes.*"` calls in Java to `"gui.disquests.*"` / `"key.disquests.*"`
- Add missing translation key `key.disquests.openconfig` (referenced by `KeyBinds.java` but absent from current lang files)
- Remove old `buildnotes` keys entirely (no aliases -- clean break)

### Trade-off: JSON5 creates hard owo-lib dependency for lang loading

With `.json5` lang files and the `owo-json5` marker, owo-lib must be present for translations to resolve. If owo-lib is missing (before owo-sentinel downloads it), raw key strings appear in the UI. With plain `.json`, translations load without owo-lib. This is acceptable since owo-lib is already a required dependency and owo-sentinel handles the missing-lib case.

### Scope

All three lang files (en_us, en_pt, zh_cn) get the same structural treatment.

## 4. Overlay Container (replaces ConfirmScreen)

### Current state

`ConfirmScreen.java` + `confirm_screen.xml`: Full screen navigation. Leaving the current screen, rendering a centered panel with message + confirm/cancel buttons, then navigating back.

### Target state

A helper method on `DisquestsBaseScreen`:

```java
protected void showConfirmOverlay(Text message, Runnable onConfirm) {
    var panel = // build confirm panel with message + buttons
    var overlay = Containers.overlay(panel);
    overlay.id("confirm-overlay");
    this.uiAdapter.rootComponent.child(overlay);
}
```

Confirm dialogs appear as modals on top of the current screen. Click outside or press cancel to dismiss. No screen navigation.

**API verification needed:** The exact `Containers.overlay()` API (method signatures, dismissal behavior) must be verified against owo-lib v0.13.0 during implementation. The overlay may need `.closeOnClick(true)` or a different method name. Also verify that adding an overlay as a child of a `FlowLayout` produces the expected floating/modal behavior rather than participating in the flow.

### Changes

- Add `showConfirmOverlay()` to `DisquestsBaseScreen`
- Replace all `client.setScreen(new ConfirmScreen(...))` calls with `showConfirmOverlay()`
- Delete `ConfirmScreen.java` and `confirm_screen.xml`
- Update E2E tests: replace `waitForScreen(context, ConfirmScreen.class)` / `assertScreenIs(context, ConfirmScreen.class)` with overlay-aware helpers like `waitForComponent(context, "confirm-overlay")`. The screen class no longer changes during confirmation, so screen-class-based assertions must be replaced with component-presence-based assertions.

## 5. Collapsible Container

### Where to use

- `quest_screen_view.xml`: Contributor list (collapsed by default when >3 contributors)
- `quest_screen_view.xml`: Tags section (collapsed by default when tags present)

### Implementation

```xml
<collapsible expanded="false" id="contributors-collapse">
    <text>Contributors ({{count}})</text>
    <children>
        <!-- contributor entries added programmatically -->
    </children>
</collapsible>
```

Java code sets the title text dynamically with the contributor count and populates children.

### Scope

View screen only. Edit screen keeps contributors and tags always visible since users interact with them directly.

## 6. Draggable HUD Pin (owo-ui overlay)

### Current state

- `InGameHudMixin.java`: `@Inject` into `InGameHud.render()`, calls `HudPinRenderer`
- `HudPinRenderer.java`: Raw `DrawContext` calls for markdown rendering, word-wrap, content caching
- `HudPinManager.java`: Pin list state management
- Position hardcoded to top-right corner

### Target state

Replace mixin-based rendering with a persistent owo-ui HUD layer:

1. **`HudOverlay`**: A lightweight owo-ui screen layer that renders during gameplay, registered on world join, torn down on disconnect
2. **`HudPinComponent`**: A `BaseUIComponent` subclass that renders the pinned quest markdown (ports `HudPinRenderer` logic into owo-ui's component model)
3. **Wrap in `Containers.draggable()`**: Users grab and reposition the pin display
4. **Persist position** via `pinnedX`/`pinnedY` in owo-config (saved on drag end)

### Lifecycle

- Register on `ClientPlayConnectionEvents.JOIN` (same event `ClientSession` uses)
- Tear down on `ClientPlayConnectionEvents.DISCONNECT`
- Survives screen opens/closes (owo-ui HUD layers persist across screen transitions)
- Toggle visibility via existing keybind

### Changes

- Create `HudOverlay.java` and `HudPinComponent.java`
- Port rendering logic from `HudPinRenderer` into `HudPinComponent`
- Delete `InGameHudMixin.java`
- Remove mixin registration from `disquests.client.mixins.json`
- `HudPinManager` remains (manages pin state), but `HudPinRenderer` is replaced by `HudPinComponent`
- Add `pinnedX`, `pinnedY` fields to `DisquestsConfigModel`

### Risk and open questions

Highest risk item in this design. The mixin approach is battle-tested. The owo-ui HUD overlay approach needs validation:

- **Does owo-ui support rendering outside a `Screen` context?** `OwoUIAdapter` is designed for `Screen` subclasses. Rendering an owo-ui component tree during `InGameHud.render()` may not be supported. If `OwoUIAdapter` cannot be instantiated without a `Screen`, this approach fails.
- **How does drag interaction work without a `Screen`?** Dragging requires mouse event routing. During gameplay, mouse events go to the game (camera look). The draggable container may require opening a temporary transparent `Screen` overlay for drag interaction, then closing it when done.
- **Lifecycle stability:** The HUD layer must survive screen opens/closes, respawns, and dimension changes without duplicating or disappearing.

**Fallback plan (hybrid):** Keep `InGameHudMixin` for the render hook. Inside the mixin's inject, create and render an owo-ui component tree (`HudPinComponent`) using `OwoUIAdapter` or raw component rendering. This preserves the mixin's reliable lifecycle while gaining owo-ui's component model for the pin content. Drag repositioning would use a keybind that opens a transparent `Screen` with the draggable container, then closes on release. Position saved to owo-config.

**Implementation approach:** Start with the full owo-ui overlay. If it doesn't work after a spike, fall back to the hybrid. The fallback still delivers draggable HUD and owo-ui rendering -- just with the mixin kept for lifecycle.

## Files Deleted

| File | Replaced by |
|------|-------------|
| `DisquestsConfig.java` | `DisquestsConfigModel.java` + generated wrapper |
| `ColorConfig.java` | `@Hook` on theme field + simple map overlay |
| `ConfigScreen.java` | owo-config auto-generated screen |
| `config_screen.xml` | owo-config auto-generated screen |
| `ModMenuIntegration.java` | owo-config `@Modmenu` annotation |
| `ConfirmScreen.java` | `DisquestsBaseScreen.showConfirmOverlay()` |
| `confirm_screen.xml` | Overlay container (no XML needed) |
| `HudPinRenderer.java` | `HudPinComponent.java` |
| `InGameHudMixin.java` | `HudOverlay.java` (or kept if hybrid fallback) |

## Files Created

| File | Purpose |
|------|---------|
| `DisquestsConfigModel.java` | Annotated owo-config model |
| `templates.xml` | Shared XML templates |
| `HudOverlay.java` | Persistent owo-ui HUD layer |
| `HudPinComponent.java` | Draggable pin component |
| `owo-json5` | Marker file enabling JSON5 lang loading |

## Files Modified

| File | Change |
|------|--------|
| `client/build.gradle.kts` | Add `annotationProcessor` for owo-lib |
| `fabric.mod.json` | Remove `modmenu` entrypoint (owo-config handles registration) |
| `DisquestsClient.java` | Load owo-config, register HUD overlay |
| `DisquestsBaseScreen.java` | Add `showConfirmOverlay()` helper |
| `Colors.java` | Replace `reload()` to use hook-based theme + overlay approach |
| `ClientSession.java` | Register/teardown HUD overlay on join/disconnect |
| `HudPinManager.java` | Update to work with new `HudPinComponent` |
| `en_us.json` -> `en_us.json5` | Rename, restructure, add config translation keys |
| `en_pt.json` -> `en_pt.json5` | Same |
| `zh_cn.json` -> `zh_cn.json5` | Same |
| All screen Java files | Update config access, replace ConfirmScreen calls |
| 5 surviving screen XML files | Replace duplicated patterns with template references |
| `disquests.client.mixins.json` | Remove `InGameHudMixin` (keep `InventoryBadgeMixin`) |
| `KeyBinds.java` | F6 opens auto-generated config screen |
| `QuestScreen.java` (view) | Add collapsible contributors/tags |
| `quest_screen_view.xml` | Add collapsible containers |

## Testing

All existing E2E tests must pass after migration. Specific test updates:

- **New test primitives needed**: `waitForComponent(context, componentId)` and `assertComponentExists(context, componentId)` to detect overlay presence, since `waitForScreen` / `assertScreenIs` no longer applies to confirm dialogs
- **ConfigJourney**: Rewrite to interact with owo-config's auto-generated screen instead of custom ConfigScreen
- **Quest delete/leave tests** (`DirtyDetectionJourney`, `QuestLifecycleJourney`, `TwoPlayerJourneys`): Replace `waitForScreen(context, ConfirmScreen.class)` / `assertScreenIs(context, ConfirmScreen.class)` with overlay-aware assertions
- **HUD pin tests**: Verify pin renders via new owo-ui overlay, test drag repositioning
- **Lang verification**: At least one test should assert that a known translation key resolves to its expected text (catches JSON5 loading failures)
- **All journeys**: Verify no regressions from XML template refactor and lang key renames

## Out of Scope

- owo networking (Paper server can't use it)
- owo Endec (PacketCodec lives in common module shared with Paper)
- owo registration (no items/blocks to register)
- Lavender guidebook
- owo debug mode / RenderDoc integration
