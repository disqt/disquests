# In-Progress: owo-ui MainScreen Migration

## Branch
`feat/owo-ui-migration` -- PR #14 (draft): https://github.com/disqt/disquests/pull/14

## What Was Done
- Added owo-lib 0.13.0+1.21.11 dependency (needs jitpack repo for kdl4j transitive dep)
- Created `DisquestsBaseScreen` extending `BaseUIModelScreen<FlowLayout>`
- Created `QuestEntryComponent` extending `BaseUIComponent` (custom quest list entry renderer)
- Created XML UI model at `assets/disquests/owo_ui/main_screen.xml`
- Rewrote `MainScreen` using owo-ui declarative layout
- Deleted `TabButtonWidget`, `QuestListWidget`, `AbstractListWidget`
- Updated E2E tests
- Ran /simplify -- cached per-frame allocations in QuestEntryComponent

## Known Bugs (Need Fixing)

### 1. Filter row visibility broken
- All/Open/Closed filter buttons show on My Quests tab (should be hidden)
- They disappear when switching to Quest Board and back
- Root cause: `selectTab()` removes/re-adds the filter row from the root layout, but the initial state doesn't hide it for My Quests tab. The remove/re-add approach for visibility is fragile.

### 2. Bottom buttons truncated / not visible
- Action buttons (New Quest, Join, Request, Open, Close) at the bottom are cut off
- Root cause: the scroll container has `fill(100)` vertical sizing which takes all remaining space, leaving no room for the search bar and action buttons below it. Need to change sizing so the scroll area doesn't push buttons off-screen.

### 3. Pin click detection may still be wrong
- Switched from absolute to relative coordinates but untested in-game
- Need E2E test to verify

## owo-ui API Findings (v0.13.0)
- Class names: `BaseUIComponent` (not BaseComponent), `OwoUIGraphics` (not OwoUIDrawContext), `UIComponents` (not Components), `UIContainers` (not Containers)
- `onMouseDown` signature: `(Click click, boolean doubled)` -- owo handles double-click timing
- XML `<scroll>` child must be FIRST element (before sizing/surface/padding properties)
- XML needs `<components>` wrapper inside `<owo-ui>`
- No visibility API -- must use remove/re-add or zero-sizing hacks
- `TextBoxComponent.onChanged()` returns `EventSource` with `.subscribe()`
- `ButtonComponent.active(boolean)` works as expected

## Next Steps
1. Write E2E test for pin clicking on QuestEntryComponent
2. Fix filter row visibility (initial state + tab switching)
3. Fix bottom buttons being truncated (scroll container sizing)
4. Manual test all interactions
5. Once stable, merge PR

## Files Changed (vs main)
- `gradle.properties` -- added owo_version
- `client/build.gradle.kts` -- owo-lib + jitpack + sentinel
- `client/src/main/resources/fabric.mod.json` -- owo-lib dependency
- `client/src/main/resources/assets/disquests/owo_ui/main_screen.xml` -- NEW
- `client/.../gui/screen/DisquestsBaseScreen.java` -- NEW
- `client/.../gui/component/QuestEntryComponent.java` -- NEW
- `client/.../gui/screen/MainScreen.java` -- REWRITTEN
- `client/.../gui/widget/TabButtonWidget.java` -- DELETED
- `client/.../gui/widget/list/QuestListWidget.java` -- DELETED
- `client/.../gui/widget/list/AbstractListWidget.java` -- DELETED
- `client/src/testmod/.../QuestScreenTest.java` -- MODIFIED

## Prism Instance
owo-lib.jar must be in the mods folder alongside the client mod:
`C:/Users/leole/AppData/Roaming/PrismLauncher/instances/1.21.11 v2.7/.minecraft/mods/owo-lib-0.13.0+1.21.11.jar`
