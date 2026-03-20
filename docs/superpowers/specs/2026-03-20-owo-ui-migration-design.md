# MainScreen owo-ui Migration

Migrate MainScreen from manual pixel-positioned vanilla widgets to owo-ui's declarative layout system. First screen to migrate -- establishes patterns for future screens.

## Scope

**Migrate:** MainScreen only.
**Create:** DisquestsBaseScreen (shared owo-ui base), QuestEntryComponent (custom owo-ui component for quest list entries).
**Keep vanilla:** QuestScreen, ContributorScreen, ConfirmScreen, ConfigScreen (migrate later using patterns from this work).

## Dependencies

Add owo-lib v0.13.0+1.21.11 to `client/build.gradle.kts`. Include owo-sentinel as jar-in-jar for graceful missing-dependency handling. Add `owo-lib` to `fabric.mod.json` dependencies.

## Screen Structure

XML-driven via `assets/disquests/owo_ui/main_screen.xml` for hot-reload iteration.

```
BaseUIModelScreen<FlowLayout> (Surface.VANILLA_TRANSLUCENT)
├── Title label ("Disquests", centered)
├── Tab row (horizontal flow, centered)
│   ├── Button "My Quests" (active/inactive styling)
│   └── Button "Quest Board" (with pending request badge)
├── Search bar (textBox, fill width)
├── Filter row (horizontal flow, Quest Board tab only, hidden for My Quests)
│   ├── Button "All"
│   ├── Button "Open"
│   └── Button "Closed"
├── Quest list (ScrollContainer > vertical FlowLayout)
│   └── QuestEntryComponent[] (custom, dynamically populated)
└── Toast overlay (label, timed visibility, absolute positioning)
```

## DisquestsBaseScreen

Shared base for all future owo-ui screens:

```java
public abstract class DisquestsBaseScreen extends BaseUIModelScreen<FlowLayout> {
    protected final Screen parent;

    protected DisquestsBaseScreen(Class<FlowLayout> rootType, DataSource source, Screen parent) {
        super(rootType, source);
        this.parent = parent;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
```

Keeps the parent-screen navigation pattern from the existing BaseScreen.

## QuestEntryComponent

Custom owo-ui component extending `BaseComponent`. Renders the same 3-row quest entry layout currently in `QuestListWidget.QuestEntry`:

- **Row 1:** Title (white) + visibility badge (colored) + pending count (amber) + owner name (gray)
- **Row 2:** Content preview (gray) + pin icon (right-aligned)
- **Row 3:** Timestamp (gray) + location/coords (gray, right-aligned)

Click handling: single click selects, double click opens QuestScreen. Pin icon click toggles pin.

Fixed height per entry (same as current `itemHeight`). Width fills parent.

## Tab Switching

Two owo-ui buttons in a horizontal flow. Active tab gets a distinct surface/color. Switching tabs:
1. Updates `ClientSession.activeTab`
2. Rebuilds the quest list content in the ScrollContainer
3. Shows/hides the filter row (Quest Board only)

## Search & Filtering

- `Components.textBox()` for search input with change listener
- Filtering rebuilds the quest list children (clear + re-add matching QuestEntryComponents)
- Filter buttons use active/inactive styling matching the tab pattern

## Toast Overlay

Absolute-positioned label at top-center. Shown via `Positioning.relative(50, 5)`. Faded out after 3 seconds using a tick counter in `tick()` that removes the component.

## Pending Badge on Tab

The "My Quests" tab button label includes the pending request count when > 0. Rendered as styled Text with amber color, same as current MainScreen badge logic but using owo-ui's label/text styling.

## Files Deleted

| File | Reason |
|------|--------|
| `MainScreen.java` | Rewritten as owo-ui screen |
| `TabButtonWidget.java` | Replaced by owo-ui buttons |

## Files Possibly Deleted (if no other screen uses them)

| File | Check |
|------|-------|
| `AbstractListWidget.java` | Used by QuestScreen? If yes, keep. |
| `QuestListWidget.java` | Only used by MainScreen. Delete. |

## Files Created

| File | Purpose |
|------|---------|
| `client/.../gui/screen/DisquestsBaseScreen.java` | Shared owo-ui base screen |
| `client/.../gui/screen/MainScreen.java` | Rewritten owo-ui MainScreen |
| `client/.../gui/component/QuestEntryComponent.java` | Custom quest list entry |
| `client/src/main/resources/assets/disquests/owo_ui/main_screen.xml` | XML UI model |

## Files Modified

| File | Change |
|------|--------|
| `client/build.gradle.kts` | Add owo-lib dependency |
| `gradle.properties` | Add `owo_version=0.13.0+1.21.11` |
| `client/src/main/resources/fabric.mod.json` | Add owo-lib dependency |
| `client/.../gui/helper/Colors.java` | Keep (still used for text colors in QuestEntryComponent) |
| `client/.../gui/helper/UIHelper.java` | Keep (still used by non-migrated screens) |
| `client/.../gui/helper/ScreenLayouts.java` | Keep (still used by non-migrated screens) |
| `client/src/testmod/.../QuestScreenTest.java` | Update MainScreen tests for new API |

## Migration Approach

1. Add owo-lib dependency, verify build
2. Create DisquestsBaseScreen
3. Create QuestEntryComponent with rendering logic ported from QuestListWidget.QuestEntry
4. Create XML model for MainScreen layout
5. Rewrite MainScreen extending DisquestsBaseScreen
6. Wire up all behavior (tabs, search, filter, pin, double-click, toast, badge)
7. Delete old files (TabButtonWidget, QuestListWidget)
8. Update E2E tests
9. Manual test in Prism instance
