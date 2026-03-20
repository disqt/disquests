# v0.2.4 Player Feedback Fixes

Addresses 10 bug reports and feature requests from player testing feedback (organized into 7 sections, some bundling multiple items).

## Overview

One branch, one PR. TDD approach with unit tests (common/paper) and E2E client game tests. Version bump to 0.2.4 in `gradle.properties`.

## Items Covered

1. Checkbox toggle requires edit permission (was open to any viewer)
2. Checkbox toggle broadcasts to other players
3. ContributorScreen: fix permission toggle
4. ContributorScreen: fix remove contributor
5. ContributorScreen: disable invite UI
6. Leave quest (new feature)
7. Markdown: leading whitespace breaks formatting
8. Links clickable in MarkdownWidget
9. Tab inserts 4 spaces in edit field
10. Edit field word-wraps (no horizontal scroll)
11. BlueMap URL fix (coordinates, map mapping, perspective view)

(BlueMap config URL was fixed server-side during triage. Quest tags deferred to future version.)

## 1. Permission & Checkbox Fixes

### Problem
Anyone who can view a quest can toggle checkboxes. The `CheckboxToggleListener` lambda in `QuestScreen.initViewMode()` (line 228) only checks `hideContent` (blocks CLOSED quests for non-members), but does not check `canEdit`. A view-only contributor on an OPEN quest can toggle checkboxes -- the server silently rejects the SAVE_QUEST, but the client shows the toggled state locally.

Checkbox toggles may also not broadcast to other players correctly.

### Changes

**Client (QuestScreen.initViewMode(), line 228):**
- Add `if (!canEdit) return;` at the top of the `CheckboxToggleListener` lambda, before the `hideContent` check.

**Server (ServerPacketHandler):**
- Verify SAVE_QUEST broadcast logic sends updates to all relevant players after a checkbox-only content change. The `broadcastQuestUpdate()` method should handle this, but needs verification.

### Tests
- **Unit (paper):** SAVE_QUEST rejected from non-owner non-editor; accepted from owner; accepted from editor contributor; broadcast reaches contributors after save.
- **Unit (common):** PacketCodec round-trip for SAVE_QUEST with checkbox content.
- **E2E (client):** Checkbox not clickable on quests without edit permission; checkbox clickable on owned quests.

## 2. ContributorScreen Fixes

### Problem
Permission toggle (Can Edit / View Only) does nothing. Remove contributor confirm does nothing. Invite system not useful.

### Root Cause
Likely a stale quest reference. `ContributorScreen.clearAndInit()` reinitializes the screen from `quest.getContributors()`, but after sending UPDATE_CONTRIBUTORS, the server responds with UPDATE_QUEST which updates `ClientCache` -- not the quest object held by this screen. The screen re-renders with the old data.

### Changes

**Fix stale reference:**
- After sending UPDATE_CONTRIBUTORS (toggle or remove), re-fetch the quest from `ClientCache` before calling `clearAndInit()`, or register a listener for UPDATE_QUEST that refreshes the screen's quest reference.

**Disable invite:**
- Remove the invite text field and invite button from ContributorScreen UI. Keep protocol support server-side. Adding contributors is only via collaboration requests for now.

### Tests
- **Unit (paper):** UPDATE_CONTRIBUTORS with UPDATE op changes canEdit in DB; REMOVE op removes contributor and sends correct notifications.
- **E2E (client):** ContributorScreen renders without invite field; permission toggle button updates UI after server response; remove button removes contributor after confirm.

## 3. Leave Quest

### Problem
No mechanism for a contributor to remove themselves from a quest. Owners can only delete.

### Changes

**New packet type:** LEAVE_QUEST (C2S)
- Payload: questId (UUID)
- Byte ID: next available in PacketType enum.

**Server handler (ServerPacketHandler):**
- Validate: player is contributor, not owner.
- Remove player from contributors in DB.
- Remove player's pin for this quest (`DataManager.unpinQuest()`) to prevent orphaned pin records.
- Send DELETE_QUEST_S2C to leaving player.
- Broadcast updated quest to owner + remaining contributors.
- If OPEN/CLOSED: broadcast to all mod players (intentional -- these quests are visible on the server board, so all players should see the updated contributor list).

**Client UI (QuestScreen):**
- "Leave" button in view mode, visible only when player is a contributor (not owner).
- ConfirmScreen before sending.
- On success: navigate to MainScreen with toast "Left quest".

### Tests
- **Unit (common):** PacketCodec encode/decode LEAVE_QUEST.
- **Unit (paper):** Handler removes contributor; removes pin; rejects owner; rejects non-contributor; broadcasts correctly.
- **E2E (client):** Leave button visible for contributors; hidden for owners; hidden for non-members.

## 4. Markdown Leading Whitespace

### Problem
Leading whitespace before formatting markers (e.g. `"             **Hangar**"`) triggers commonmark's code block rule (4+ spaces), preventing formatting.

### Changes

**MarkdownRenderer (pre-processing approach):**
1. Split input by `\n`.
2. For each line, count leading spaces and trim them.
3. Record the space count per line.
4. Rejoin trimmed lines with `\n` and pass to commonmark parser.
5. After parsing, apply pixel indents to the corresponding `RenderedLine` objects (e.g. 2px per original leading space).

Note: This is a pre-processing approach. Commonmark may merge/reorder lines (e.g. list continuations), so indentation mapping is best-effort. For the primary use case (standalone lines with leading spaces), this works correctly.

### Tests
- **Unit:** `"             **Hangar**"` renders as bold with indent.
- **Unit:** `"  *italic*"` renders as italic.
- **Unit:** Normal markdown without leading spaces still works.
- **Unit:** Mixed lines with and without leading whitespace.

## 5. Clickable Links

### Problem
Links render as aqua+underlined text but MarkdownWidget doesn't handle click events for styled text. The `MarkdownRenderer` already attaches `ClickEvent.OpenUrl` to link text via `Style`, and `OrderedText` preserves style information -- but `MarkdownWidget` never inspects it.

### Changes

**MarkdownWidget:**
- During word-wrap/render phase, extract `ClickEvent` data from `Style` on each character run to build link hitboxes (position, width, URL).
- On mouse click: check link hitboxes, open URL via `Util.getOperatingSystem().open()`.
- On hover: show URL as tooltip, visual feedback.

### Tests
- **Unit:** `MarkdownRenderer.render("[text](https://example.com)")` produces styled text with ClickEvent.OpenUrl.
- **E2E (client):** Link in MarkdownWidget has hitbox; click triggers URL open.

## 6. Edit Field Fixes

### Problem
Tab key doesn't insert spaces. Content text field scrolls horizontally instead of wrapping.

### Changes

**Tab key (MultiLineTextFieldWidget):**
- Intercept Tab keypress, insert 4 spaces at cursor.
- If text is selected, replace selection with 4 spaces.
- Consume event to prevent focus change.

**Word wrap:**
- Ensure content edit field wraps text at field width with no horizontal scrolling. Check constructor parameters and word-wrap logic.

### Tests
- **E2E (client):** Tab inserts 4 spaces at cursor position.
- **E2E (client):** Tab with selection replaces selection with 4 spaces.
- **E2E (client):** Long text wraps at field boundary.

## 7. BlueMap URL Fix

### Problem
URL links to wrong coordinates (0:0:0), uses flat view, and quest map names don't match BlueMap map IDs.

### Changes

**Map name mapping via Paper config (`config.yml`):**
```yaml
bluemap-url: "https://disqt.com/minecraft/map"
bluemap-map-names:
  overworld: "world_new"
  nether: "world_new_nether"
  the_end: "world_new_the_end"
```

Config.java reads the map into a `Map<String, String>`. Sent to client in the HANDSHAKE packet as an additional field (list of key-value pairs after the existing fields). Client stores in `ClientSession` alongside `bluemapUrl`.

**BlueMapHelper URL format:**
```
{base}/#${bluemapMapId}:${x}:${y}:${z}:300:0:0:0:0:perspective
```
- Map name looked up from the mapping; if not found, use the quest map name as-is (fallback).
- Zoom: 300 (reasonable default for viewing a build).
- View: `perspective` (last parameter).
- Coordinates: actual quest coordinates (debug and fix why they are currently 0).

### Tests
- **Unit:** BlueMapHelper produces correct URL with mapped coordinates.
- **Unit:** Region quests use center coordinates.
- **Unit:** Unknown map name passes through as-is.
- **Unit (common):** PacketCodec round-trip for updated HANDSHAKE with map name mappings.

## File Impact

| File | Changes |
|------|---------|
| `gradle.properties` | Version bump to 0.2.4 |
| `common/PacketType.java` | Add LEAVE_QUEST |
| `common/PacketCodec.java` | Encode/decode LEAVE_QUEST, HANDSHAKE map mappings |
| `client/gui/screen/QuestScreen.java` | Permission check on checkbox, Leave button |
| `client/gui/screen/ContributorScreen.java` | Fix stale reference, fix toggle/remove, remove invite UI |
| `client/gui/widget/MarkdownWidget.java` | Link hitboxes, click handling, hover tooltip |
| `client/gui/widget/MultiLineTextFieldWidget.java` | Tab insertion, word-wrap fix |
| `client/markdown/MarkdownRenderer.java` | Pre-process leading whitespace |
| `client/BlueMapHelper.java` | Map name lookup, perspective view, zoom, coordinate fix |
| `client/ClientSession.java` | Store BlueMap map name mappings |
| `paper/ServerPacketHandler.java` | LEAVE_QUEST handler, verify SAVE_QUEST broadcast |
| `paper/DataManager.java` | Leave quest: remove contributor + unpin |
| `paper/Config.java` | BlueMap map name mappings |
| `paper/resources/config.yml` | Map name config entries |
