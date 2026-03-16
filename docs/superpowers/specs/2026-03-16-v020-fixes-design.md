# v0.2.0 Fixes & Improvements Design Spec

**Date:** 2026-03-16
**Branch:** `fix/v020-feedback`

## Context

User testing of v0.2.0 revealed bugs and UI improvements. This spec covers all items.

## Items

### 1. Title + Tab Layout Fix

**Problem:** "Disquests" title, "My Quests" tab, and "Quest Board" tab all render at y=5-11, centered horizontally, causing overlap.

**Fix:** Increase `TOP_MARGIN` from 30 to 45. Render "Disquests" title centered at y=4 (above tabs). Tabs render at y=20 (below title). Sub-filter buttons (All/Open/Closed) remain positioned relative to `TOP_MARGIN`. Everything below shifts down automatically since lists, search, and buttons are all computed from `TOP_MARGIN`.

**Files:** `ScreenLayouts.java` (TOP_MARGIN), `MainScreen.java` (title render position, tab Y)

### 2. Pin on List Entries

**Problem:** Pin button is in the bottom bar, requiring select-then-pin. User expects pin toggle directly on each quest entry.

**Fix:** Add a pin toggle icon (gold `*` = pinned, gray `*` = unpinned) on the right side of each quest entry in `QuestListWidget.QuestEntry.render()`. Clicking the icon area toggles pin state via `HudPinManager.toggle()`. Remove `pinButton` from MainScreen's bottom button bar.

The click detection in `QuestEntry.mouseClicked()` checks if the click X is within the pin icon region (rightmost ~12px of the entry). If so, toggle pin and return true (consuming the click so it doesn't select the entry).

**Files:** `QuestListWidget.java` (render pin icon, handle click), `MainScreen.java` (remove pinButton)

### 3. Formatting Help Button Fix

**Problem:** The 14x14 `?` button sits at `contentX + contentWidth - 16`, inside the `editContentField` bounds. The text field captures clicks first.

**Fix:** Move the `?` button above the content panel, next to the title panel. Position it at `contentX + contentWidth - helpBtnSize` at the title row Y. This places it outside the content field entirely.

**Files:** `QuestScreen.java` (move ? button position in `initEditMode()`)

### 4. Strip Markdown in List Preview + HUD

**Problem:** List entry content preview shows raw markdown (`_pain_`, `[ ]`). HUD pinned quests show raw markdown in both titles and content.

**Fix:**
- List preview: run `firstLine` through `MarkdownRenderer.stripToPlainText()` in `QuestEntry` constructor
- HUD titles: run `quest.getTitle()` through `stripToPlainText()` before wrapping in `rebuildCache()`
- Ensure `stripToPlainText()` properly strips `_`, `*`, `**`, `~~`, `#` prefixes, and converts `- [ ]`/`- [x]` to unicode checkbox characters (☐/☑)

**Files:** `QuestListWidget.java`, `HudPinRenderer.java`, `MarkdownRenderer.java`

### 5. Clickable Checkboxes in View Mode

**Problem:** Task list checkboxes (`[ ]`/`[x]`) in view mode are display-only. User wants to click them to toggle state.

**Fix:**
- `MarkdownWidget`: during render, record the bounding box of each checkbox glyph along with its line index in the source content
- `MarkdownWidget.mouseClicked()`: check if click hit a checkbox, find the corresponding `- [ ]` or `- [x]` in the raw content, toggle it
- `QuestScreen`: when MarkdownWidget reports a checkbox toggle, update `quest.content`, re-render the MarkdownWidget, and auto-save via `PacketSender.saveQuest()`
- Add a callback interface `CheckboxToggleListener` on MarkdownWidget that QuestScreen implements

**Files:** `MarkdownWidget.java`, `QuestScreen.java`, `MarkdownRenderer.java` (track checkbox source positions)

### 6. Remove Pin from QuestScreen

**Problem:** Pin button in QuestScreen view mode is redundant now that pin is on list entries.

**Fix:** Remove the "Pin to HUD" button from `initViewMode()` button list. Remove `pinButton` field and `togglePin()` method.

**Files:** `QuestScreen.java`

### 7. Corner 2 Editable XYZ Fields

**Problem:** `buildCorner2Row()` shows static text for Corner 2 coordinates. Corner 1 has editable X/Y/Z text fields.

**Fix:** Add three `MultiLineTextFieldWidget` fields (coord2XField, coord2YField, coord2ZField) in `buildCorner2Row()`, matching Corner 1's pattern. Render with X:/Y:/Z: labels. Update `persistFieldValues()` to read Corner 2 fields and set `quest.setCoordinates2()`. Keep the "Set" button to fill from player position.

**Files:** `QuestScreen.java`

### 8. Test Quests from Another Player

**Problem:** Need quests from a different player on the Quest Board to test OPEN/CLOSED visibility.

**Fix:** Insert two quests directly via SQLite on the Minecraft server:
- One with `visibility=OPEN`, owner="TestPlayer", owner_uuid=random
- One with `visibility=CLOSED`, owner="TestPlayer", owner_uuid=random

Command: `ssh minecraft "sqlite3 ~/serverfiles/plugins/Disquests/disquests.db \"INSERT INTO ...\""` (two insert statements)

### 9. E2E Test Expansion

**Problem:** Current E2E test only covers connect -> handshake -> open MainScreen -> close. No coverage for the features being fixed.

**Fix:** Expand `DisquestsE2ETest.runTest()` with additional test steps:

1. **Tab switching:** Switch to Quest Board tab, verify it loads, switch back
2. **Quest creation:** Click New Quest -> fill title+content -> save -> verify appears in list
3. **Pin toggle:** Pin a quest from the list, verify HUD shows it, unpin
4. **Open quest:** Open a quest in view mode, verify content displays
5. **Formatting help:** Enter edit mode, click ?, verify panel appears
6. **Quest Board:** Switch to Quest Board, verify test quests from other player appear
7. **Checkbox toggle:** Open a quest with checkboxes, click one in view mode, verify it toggles

Tests that require UI interaction (button clicks, text input) use `context.runOnClient()` to execute on the render thread. Assertions use `context.computeOnClient()`.

Pre-seed test data: The E2E workflow already starts a Paper server. Add a step that inserts test quests via RCON before running the client tests.

**Files:** `DisquestsE2ETest.java`, `.github/workflows/e2e-test.yml` (RCON seeding step)
