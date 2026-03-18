# v0.2.2 UI Fixes & Improvements Design

**Date**: 2026-03-18
**Branch**: `fix/v022-ui-feedback`
**Release**: v0.2.2 (single branch, all fixes together)

## Overview

Seven issues identified from v0.2.1 playtesting. All are UI/UX bugs or improvements in the Fabric client mod. One branch, one release.

---

## Issue 1: "?" Formatting Help Button Broken in Edit Mode

**Symptom**: Clicking the "?" button in edit mode does nothing.

**Root Cause**: The `editTitleField` (registered via `addSelectableChild`) fully overlaps the "?" button (registered via `addDrawableChild`). Minecraft's `Screen.mouseClicked()` iterates selectable children first; the title field returns `true` for any click within its bounds, consuming the event before the button sees it.

**Fix**: Override `mouseClicked()` in `QuestScreen` to check the help button's bounds first when in edit mode. If the click is within the button area, toggle `showFormattingHelp` and call `clearAndInit()`, returning `true` before `super.mouseClicked()` runs.

**Files**: `QuestScreen.java`

**Test**: E2E or client test -- open quest in edit mode, call `mouseClicked` at the button's computed coordinates, assert `showFormattingHelp` toggles to `true`.

---

## Issue 2: "by Owner [VISIBILITY]" Text Misaligned in View Mode

**Symptom**: The "by Termiduck [OPEN]" text appears cramped and misaligned in the top-right of the view screen.

**Root Cause**: Text is rendered at `Y = TOP_MARGIN - 2` (43px), only 2px above the title panel at Y=45. The `-2` is a hardcoded magic number.

**Fix**: Align the owner info text vertically with the "View Quest" screen title. Use a proper layout constant instead of the magic `-2` offset. Ensure right-alignment uses consistent padding from the content edge.

**Files**: `QuestScreen.java`, possibly `ScreenLayouts.java`

**Test**: Client test -- open quest in view mode, assert owner info Y position matches screen title baseline. Assert right edge aligns with `contentX + contentWidth`.

---

## Issue 3: Pin Button Too Small and Unclear

**Symptom**: The pin indicator is a tiny gray/gold asterisk `*` (~5-6px wide) with no tooltip. Users don't realize it's clickable or what it does.

**Current State**: Rendered as `drawText("*")` on row 2 of each quest entry. 14x12px hit area.

**Fix**:
1. Create two 10x10 PNG sprites:
   - `assets/disquests/textures/gui/sprites/icon/pin.png` (gray, unpinned)
   - `assets/disquests/textures/gui/sprites/icon/pin_active.png` (gold, pinned)
2. Replace `drawText("*")` with `drawGuiTexture(RenderPipelines.GUI_TEXTURED, pinIcon, ...)` in `QuestListWidget.renderWidget()`
3. Increase hit area to 20x14px
4. Add tooltip on hover: "Pin to HUD" / "Unpin from HUD"

**Placement**: Row 2, right side (same position, just better icon).

**Files**: `QuestListWidget.java`, new PNG assets

**Tests**:
- Click pin icon area on an entry, assert `HudPinManager.isPinned(questId)` toggles
- Assert correct sprite identifier used based on pin state
- Assert tooltip text present on hover

---

## Issue 5a: No Feedback After Requesting to Join a Closed Quest

**Symptom**: Clicking "Request" on a closed quest sends the packet but gives zero visual feedback.

**Fix**:
1. After sending `REQUEST_COLLABORATION` packet, change button text from "Request" to "Requested" and set `button.active = false`
2. Track `requestedQuestIds` in `ClientSession` (`Set<UUID>`) so the state persists across screen rebuilds within the session
3. Show actionbar toast: `"Request sent to <ownerName>"` via `MinecraftClient.getInstance().inGameHud.setOverlayMessage()`

**Files**: `MainScreen.java`, `ClientSession.java`

**Tests**:
- Select a CLOSED quest, invoke the request action, assert button text == "Requested" and `button.active == false`
- Assert `ClientSession.requestedQuestIds` contains the quest ID
- Assert actionbar overlay message contains owner name

---

## Issue 5b: No Feedback After Joining an Open Quest

**Symptom**: Clicking "Join" on an open quest sends the packet but the quest stays in the board until the user leaves and re-enters. No visual confirmation.

**Root Cause**: `ClientPacketHandler.handleCollaborationResponse()` moves the quest between caches but doesn't trigger a screen refresh or any user-facing notification.

**Fix**:
1. In `ClientPacketHandler.handleCollaborationResponse()`, after cache update, show actionbar toast: `"Joined \"<quest title>\" -- see My Quests"`
2. Trigger `MainScreen.refreshServerQuestList()` so the quest board list updates immediately (quest disappears from the board)
3. Stay on the Quest Board tab (no auto-switch)

**Files**: `ClientPacketHandler.java`, `MainScreen.java`

**Tests** (full E2E with server):
- Select an OPEN quest, click "Join", wait for server response
- Assert quest removed from `ClientCache.getServerQuests()`
- Assert quest present in `ClientCache.getMyQuests()`
- Assert actionbar overlay message contains quest title

---

## Issue 7: Mod Menu Integration

**Symptom**: Disquests doesn't appear in Mod Menu's mod list with a config button. Users can't find the mod or its settings.

**Current State**: Zero Mod Menu integration -- no dependency, no entrypoint, no config screen.

**Fix**:
1. Add `modmenu` dependency to `client/build.gradle.kts` (modCompileOnly so it's optional at runtime)
2. Add `modmenu` version to `gradle.properties`
3. Create `ModMenuIntegration` class implementing `ModMenuApi`:
   - `getModConfigScreenFactory()` returns `parent -> new MainScreen(parent)`
4. Add `"modmenu"` entrypoint to `fabric.mod.json`

Opening the config screen from Mod Menu opens the Disquests main screen directly. No dedicated settings screen needed.

**Files**: `client/build.gradle.kts`, `gradle.properties`, `fabric.mod.json`, new `ModMenuIntegration.java`

**Test**: Unit test -- assert `ModMenuIntegration.getModConfigScreenFactory()` returns a non-null factory that produces a `MainScreen` instance.

---

## Test Strategy

**Principle**: Every feature that touches user experience gets test coverage.

| Issue | Test Type | Server Required |
|-------|-----------|-----------------|
| 1 - Help button | Client test (screen interaction) | No |
| 2 - Owner text alignment | Client test (layout assertion) | No |
| 3 - Pin button | Client test (click + state) | No |
| 5a - Request feedback | Client test (button state + toast) | No |
| 5b - Join feedback | E2E (packet round-trip) | Yes |
| 7 - Mod Menu | Unit test | No |

Client-side tests will use Fabric's `ClientGameTestContext` to instantiate screens with mock quest data and interact with them programmatically. Pending research on additional testing libraries that may simplify input simulation and assertions.

---

## Out of Scope

- Issue 6 (edit permissions): Already works correctly. Default is read-only, owner can toggle "Can Edit" per contributor in ContributorScreen. No change needed.
- Dedicated settings screen for Mod Menu (MainScreen is sufficient for now)
- Custom keybind configuration UI
