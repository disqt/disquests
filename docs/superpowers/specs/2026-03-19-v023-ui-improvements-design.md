# v0.2.3 UI Improvements Design

**Date**: 2026-03-19
**Branch**: `fix/v023-ui-improvements`
**Release**: v0.2.3

## Overview

11 issues from v0.2.2 playtesting. Mix of UX fixes, new features, and bug fixes.

---

## Issue 1: Deferred Pin Re-sort

**Symptom**: Pinning/unpinning a quest makes it immediately jump position in the list.

**Fix**: `refreshAfterPinToggle()` should only repaint the list (pin icon updates automatically since `QuestEntry.render()` reads `isPinned` live). Do NOT call `refreshListContents()`. Full sort (pinned first, then by lastModified) happens only on screen open (`init()`) and tab switch.

**Files**: `MainScreen.java`

---

## Issue 2: In-Screen Toast Notifications

**Symptom**: `setOverlayMessage()` renders behind the Disquests screen -- invisible to the user.

**Fix**: Create a `ToastOverlay` utility that renders a text card at the bottom of the screen, above the buttons. Behavior:
- Semi-transparent dark card with white text, centered horizontally
- Fades out over the last ~1 second of a 3-second (60 tick) display
- Drawn in the screen's `render()` method so it's always on top

For toasts triggered outside the screen (e.g. `ClientPacketHandler` join approval), store a pending toast message in `ClientSession` that `MainScreen.render()` picks up on next frame.

Replace both `setOverlayMessage` calls (MainScreen.requestAccess and ClientPacketHandler.handleCollaborationResponse) with this system.

**Files**: New `ToastOverlay.java` in `gui/widget/`, `MainScreen.java`, `ClientPacketHandler.java`, `ClientSession.java`

---

## Issue 3: Remove Click-to-Edit

**Symptom**: Clicking the content area in view mode enters edit mode unexpectedly.

**Fix**: Delete the click-to-edit block in `QuestScreen.mouseClicked()` (`if (!editing && canEdit)` content area check). Remove `contentAreaX/Y/Width/Height` fields. The Edit button is the only way to enter edit mode.

**Files**: `QuestScreen.java`

---

## Issue 4: Formatting Help Rendered Preview

**Symptom**: The "?" panel shows raw syntax like `**text**` instead of rendered formatting.

**Fix**: Replace plain text rendering with a two-column layout:
- Left column: raw syntax in muted color (what you type)
- Right column: rendered result using `Formatting` enum (BOLD, ITALIC, STRIKETHROUGH)

For headings, quotes, checkboxes, links -- show a visual approximation (colored text, indent, checkbox glyph).

Panel width increases from 120px to ~160px to fit both columns.

**Default state**: `showFormattingHelp` defaults to `true` (open by default). The "?" button hides it if user wants more editor space.

**Files**: `QuestScreen.java`

---

## Issue 5: Dedicated Config Screen for Mod Menu

**Symptom**: ModMenu "Configure" button opens the quest board instead of settings.

**Fix**: Create `ConfigScreen extends BaseScreen` with:
- "Disquests Settings" title
- Pinned width slider (100-400, shows current value)
- Save / Cancel buttons
- Reads from / writes to `DisquestsConfig`

Update `ModMenuIntegration` to return `ConfigScreen::new`.

**Files**: New `ConfigScreen.java` in `gui/screen/`, `ModMenuIntegration.java`

---

## Issue 6: "Requested" Status in Quest List

**Symptom**: No visual indicator in the quest list that you've already requested access to a closed quest.

**Fix**: In `QuestListWidget.QuestEntry.render()`, on row 3 right-aligned (where location/coords go), if `ClientSession.isRequested(quest.getId())` is true, draw "Requested" in muted yellow.

If both location and "Requested" are present, show both: `X:142 Z:-891  Requested`.

**Files**: `QuestListWidget.java`

---

## Issue 7: Hide Closed Quest Content

**Symptom**: Closed quest content is fully visible before you've joined, defeating the purpose of closed visibility.

**In quest list** (`QuestListWidget`): For CLOSED quests on the Quest Board tab, replace row 2 content preview with "*Request access to view*" in italic muted text.

**In view mode** (`QuestScreen`): If quest is CLOSED and viewer is not owner or contributor, hide content panel. Show "Request access to view this quest" placeholder. Title + owner + Request Access button remain visible.

Check: `quest.getVisibility() == CLOSED && !isOwner && !isContributor`

**Files**: `QuestListWidget.java`, `QuestScreen.java`

---

## Issue 8: Contributors Visible in View Mode

**Symptom**: Contributors are only visible in the ContributorScreen (edit mode only). No way to see who's collaborating on a quest from view mode.

**Fix**: Add a contributors line in view mode, below the metadata bar and above the buttons:

```
[Title]
[Content]
[Metadata - coords/map]
[Contributors - "Termiduck, Alex, Steve"]
[Buttons]
```

Only shown if quest has contributors. Comma-separated names in muted text.

**Files**: `QuestScreen.java` (initViewMode + renderViewMode)

---

## Issue 9: BuildNotes Migration

**Symptom**: Players who used the old BuildNotes plugin have local `.txt` notes at `notes/remote/<server-address>/` that aren't accessible in Disquests.

**Trigger**: On server join (after handshake), check if `notes/remote/<server-address>/` has `.txt` files.

**Flow**:
1. List `.txt` files in the directory
2. For each: create a Quest with filename (sans `.txt`) as title, file content as body, PRIVATE visibility
3. Send each as `SAVE_QUEST` to server
4. Delete the `.txt` files after send
5. Delete directory if empty
6. Silent -- no notification

**Server address resolution**: Match the folder name against the connected server address. The old plugin uses the server hostname as folder name (e.g. `disqt.com`).

**Files**: New `BuildNotesMigrator.java` in `client/`, called from `ClientPacketHandler.handleHandshake()`

---

## Issue 10: Pinned HUD Shows Stale Quest Data

**Symptom**: After editing a pinned quest and saving, the pinned HUD overlay still shows old content until you unpin and re-pin.

**Fix**: After a successful save (when server confirms via `UPDATE_QUEST`), if the quest is pinned, refresh the HUD pin display. The `HudPinManager` or the HUD renderer needs to read fresh data from `ClientCache` on each render rather than caching quest content.

**Files**: `HudPinManager.java` or the HUD render class

---

## Issue 11: Pinned HUD Doesn't Render Markdown

**Symptom**: Pinned quest overlay shows raw markdown (`**bold**`, `*italic*`) instead of rendered formatting.

**Fix**: The pinned HUD renderer should parse markdown content through `MarkdownRenderer` (same as the view mode content panel) and render formatted text instead of raw strings.

**Files**: HUD render class (wherever pinned quests are drawn on the game HUD)

---

## Test Strategy

| Issue | Test Type | Notes |
|-------|-----------|-------|
| 1 - Pin re-sort | E2E | Pin a quest, verify list order unchanged until tab switch |
| 2 - Toast | E2E | Trigger request, verify toast renders in screen |
| 3 - Click-to-edit | E2E | Click content area, verify still in view mode |
| 4 - Formatting preview | Visual | Screenshot comparison |
| 5 - Config screen | E2E | Open config, change slider, save, verify config file |
| 6 - Requested status | E2E | Request access, verify "Requested" text in list entry |
| 7 - Hidden content | E2E | View closed quest, verify content hidden |
| 8 - Contributors | E2E | View quest with contributors, verify names shown |
| 9 - Migration | Unit test | Mock file system, verify quests created |
| 10 - Stale HUD | Manual | Edit pinned quest, verify HUD updates |
| 11 - Markdown HUD | Manual | Pin quest with formatting, verify rendered |

---

## Out of Scope

- Color config UI in the config screen (file-based colors.json is fine for now)
- Contributor management from view mode (still requires edit mode)
- Toast animation/slide-in (simple fade is sufficient)
