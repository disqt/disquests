# Disquests Bugs & Features Spec

## Overview

A combined spec covering bug fixes and feature additions for the Disquests mod.

### Implementation Order

Items have dependencies that constrain ordering:

1. **B1** (UUID fix) must come first -- B5 and F1's permission checks depend on correct UUIDs
2. **B2, B3, B4** (independent bug fixes) can be done in any order
3. **B5** (cross-list cleanup) should come after B1, since B1 may resolve the root cause
4. **F1** (QuestScreen merge) should come next -- F3, F4, F5, F6 all target QuestScreen
5. **B1 + F2** both modify the HANDSHAKE packet -- coordinate them together (see Protocol Changes section)
6. **F2** (multi-pin) before F3 (pin button on MainScreen) since F3 uses multi-pin semantics
7. **F4, F5, F6, F7, F8, F9, F10** are independent of each other

### Protocol Changes

B1 and F2 both modify the HANDSHAKE packet. This is a **breaking protocol change** -- both client and server must be updated together. There is no protocol versioning; the mod is distributed as a matched client+server pair.

**New HANDSHAKE wire format** (appended fields at end):

| Field | Type | Notes |
|-------|------|-------|
| bluemapUrl | String (UTF) | Existing |
| pendingRequestCount | int | Existing |
| pinnedQuestCount | VarInt | NEW: number of pinned quests (replaces single nullable UUID) |
| pinnedQuestIds | UUID[] | NEW: `pinnedQuestCount` UUIDs |
| playerUuid | UUID | NEW: canonical player UUID from server |

The old single `pinnedQuestId` (nullable UUID) is replaced by a length-prefixed list. The `playerUuid` is appended last.

---

## Bug Fixes

### B1: Can't edit/delete quests after reconnect

**Problem:** After disconnecting and reconnecting, the Edit and Delete buttons are grayed out on quests the user owns. Quests created in the current session work fine.

**Root cause (suspected):** UUID mismatch between `MinecraftClient.getInstance().getSession().getUuidOrNull()` (client-side) and the UUID stored by the server via `player.getUniqueId()`. After reconnect, quests synced from the server carry the server-side UUID, which may differ from the client's session UUID (especially in offline-mode servers).

**Fix:** Investigate the UUID comparison in `ViewQuestScreen.init()` and `EditQuestScreen.init()`. The server should be the source of truth for the player's UUID. On HANDSHAKE, the server should send the player's canonical UUID so the client can use it for all ownership checks instead of relying on `getSession().getUuidOrNull()`.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/ClientSession.java` -- store canonical player UUID from handshake
- `client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java` -- use canonical UUID
- `client/src/main/java/com/disqt/disquests/client/gui/screen/EditQuestScreen.java` -- use canonical UUID
- `common/src/main/java/com/disqt/disquests/common/PacketCodec.java` -- add player UUID to handshake payload
- `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java` -- send player UUID in handshake

### B2: Content with task list items doesn't display in view mode

**Problem:** When quest content contains `- [ ]` task list items, the formatted view (MarkdownWidget in ViewQuestScreen) shows empty content. The raw text is visible in the list preview and in edit mode.

**Root cause (suspected):** The `MarkdownRenderer.render()` method or `MarkdownWidget` has a bug rendering task list items. Possibly `TaskListItemMarker` handling produces rendered lines that fail in the widget, or an exception is silently swallowed during rendering.

**Fix:** Debug `MarkdownRenderer.render()` with task list input. Fix the rendering path so `- [ ] text` and `- [x] text` produce correct `RenderedLine` output.

**Testing:** `MarkdownRenderer` uses MC `Text` types, so unit testing requires the MC runtime. This is a manual verification item -- test in-game by creating a quest with `- [ ] task` content and verifying it renders in view mode. Consider extracting pure-logic markdown functions to `common` for unit testing in a future refactor.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java`

### B3: Delete doesn't update quest list immediately

**Problem:** After deleting a quest from ViewQuestScreen, the quest still appears in My Quests until the user exits and re-opens the menu.

**Root cause:** `ViewQuestScreen.confirmDelete()` sends the delete packet and immediately closes back to MainScreen. MainScreen re-initializes and reads from `ClientCache`, but the server's `DELETE_QUEST_S2C` response hasn't arrived yet, so the cache still contains the deleted quest.

**Fix:** Optimistically remove the quest from `ClientCache` before closing ViewQuestScreen.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java` -- add `ClientCache.removeQuestById(quest.getId())` in `confirmDelete()` before `this.close()`

### B4: Checkboxes don't render in pinned HUD

**Problem:** `- [ ]` and `- [x]` task list items are stripped away in the pinned HUD overlay because `MarkdownRenderer.stripToPlainText()` doesn't handle `TaskListItemMarker` nodes.

**Fix:** Add a case for `TaskListItemMarker` in `collectPlainText()` that outputs `[ ] ` or `[x] `.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java` -- update `collectPlainText()`

### B5: Private quest appears in Quest Board (Server) view

**Problem:** The user's own quests sometimes appear in the Server tab. The server SQL correctly filters private quests and owned quests from `getServerQuests()`, but the client-side `handleUpdateQuest()` in `ClientPacketHandler` doesn't clean up across lists when a quest moves between them.

**Dependency:** This bug may be a symptom of B1's UUID mismatch -- if `handleUpdateQuest()` uses the wrong UUID for the ownership check, quests land in the wrong list. Fix B1 first, then verify if B5 is still reproducible.

**Fix:** In `ClientPacketHandler.handleUpdateQuest()`, when adding to `myQuests`, also remove from `serverQuests` (and vice versa). This provides defense-in-depth regardless of whether B1 is the root cause. Also update the UUID source to use `ClientSession.getPlayerUuid()` (from B1) instead of `getSession().getUuidOrNull()`.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java` -- add cross-list cleanup in `handleUpdateQuest()`

---

## Features

### F1: Merge View and Edit into a single QuestScreen

**Goal:** Replace `ViewQuestScreen` and `EditQuestScreen` with a single `QuestScreen` that toggles between formatted view and raw editing.

**Behavior:**

- **View mode (default):** Content rendered as formatted markdown via `MarkdownWidget`. Title displayed as read-only styled text. Buttons: Edit, Delete (owner only), Pin/Unpin, Close. Clicking the content area or the Edit button switches to edit mode.
- **Edit mode:** `MarkdownWidget` swaps to `MultiLineTextFieldWidget` for the content. Title becomes an editable text field. The formatting help side panel is available via "?" toggle. Optional fields (coords, map, visibility, contributors) appear below the editor. Buttons: Save, Cancel. Save persists changes and returns to view mode. Cancel discards and returns to view mode.
- **Permissions:** If the user doesn't have edit access, the Edit button is disabled and clicking the content area is a no-op (no cursor change, no mode switch). Delete is only available to the owner.
- **New quest flow:** When MainScreen's "New Quest" button is clicked, QuestScreen opens directly in edit mode with a blank quest. The screen detects this via a constructor flag or null quest ID.
- **Navigation:** MainScreen opens QuestScreen in view mode (existing quests) or edit mode (new quests). QuestScreen never opens another screen for viewing/editing (except ContributorScreen and ConfirmScreen as modals).

**Files:**
- Delete: `client/.../gui/screen/ViewQuestScreen.java`
- Delete: `client/.../gui/screen/EditQuestScreen.java`
- Create: `client/.../gui/screen/QuestScreen.java` -- merged screen with view/edit toggle
- Modify: `client/.../gui/screen/MainScreen.java` -- open QuestScreen instead of ViewQuestScreen

### F2: Multi-pin quests

**Goal:** Allow pinning unlimited quests to the HUD, stacked vertically.

**Changes:**

- **Client:** `ClientSession.pinnedQuestId` (single UUID) becomes `pinnedQuestIds` (List<UUID>). `HudPinManager` methods updated for add/remove/toggle/contains. `HudPinRenderer` iterates pinned quests and renders each as a separate box stacked vertically with a small gap.
- **Server:** `DataManager` pin storage changes from single row to multiple rows per player. The `PIN_QUEST` packet type semantics change: sending a quest ID toggles pin (add if not pinned, remove if pinned). Sending null unpins all (or this can be removed).
- **Protocol:** `HANDSHAKE` payload changes per the Protocol Changes section above. `PIN_QUEST` C2S semantics: toggle pin for given quest ID.
- **DB schema change:** The current `pinned_quests` table has `player_uuid TEXT PRIMARY KEY` (one row per player). New schema: `CREATE TABLE pinned_quests (player_uuid TEXT NOT NULL, quest_id TEXT NOT NULL, pinned_at INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (player_uuid, quest_id))`. Migration: on plugin startup, if the old single-column schema is detected, migrate existing rows to the new format and drop the old column. The `pinned_at` column preserves pin order.
- **HUD rendering:** Each pinned quest renders as its own box. Boxes stack top-to-bottom starting from top-left corner. Each box has the same format as current (title + truncated content).

**Files:**
- `client/.../ClientSession.java` -- List<UUID> for pinned IDs
- `client/.../hud/HudPinManager.java` -- multi-pin logic
- `client/.../hud/HudPinRenderer.java` -- stacked rendering
- `client/.../gui/screen/QuestScreen.java` -- pin button uses multi-pin
- `client/.../gui/screen/MainScreen.java` -- pin button in bottom row
- `common/.../PacketCodec.java` -- updated handshake + pin packet encoding
- `paper/.../ServerPacketHandler.java` -- toggle pin handling
- `paper/.../DataManager.java` -- multi-pin storage (multiple rows per player)

### F3: Pin button on MainScreen

**Goal:** Add a "Pin" / "Unpin" button to MainScreen's bottom button row so users can pin quests without opening them.

**Behavior:** Button appears on both My Quests and Quest Board tabs. Enabled when a quest is selected. Text toggles based on whether the selected quest is currently pinned. Clicking sends the pin toggle to the server and updates `ClientSession`.

**Files:**
- `client/.../gui/screen/MainScreen.java` -- add Pin button to bottom row, update `updateActionButtons()` to set text/active state

### F4: Editable coordinates

**Goal:** Allow manual entry of X/Y/Z coordinates in addition to "Set Pos" (capture from player position).

**Behavior:** In edit mode of QuestScreen, the coords row shows three small text fields (X, Y, Z) that are editable. "Set Pos" button fills them from player position. "Clear" button empties them. The text fields accept numeric input (integers or decimals). On save, values are parsed from the fields.

**Files:**
- `client/.../gui/screen/QuestScreen.java` -- coord text fields in edit mode, replacing the static label + "Set Pos" only approach

### F5: Formatting help side panel

**Goal:** A toggleable side panel in edit mode showing markdown syntax reference.

**Behavior:** A "?" button in the top-right of the content editor. Clicking it toggles a side panel (~120px wide) to the right of the content field. The panel shows syntax examples: bold, italic, strikethrough, headings, task lists, blockquotes, links. When the panel is open, the content field width shrinks accordingly. The panel is only available in edit mode.

**Files:**
- `client/.../gui/screen/QuestScreen.java` -- "?" button, side panel rendering, content field width adjustment

### F6: Tooltips

**Goal:** Add descriptive tooltips to UI elements throughout the mod.

**Targets:**
- Visibility badges (PRIVATE, CLOSED, OPEN) -- explain what each means
- Contributors button -- "Manage who can view/edit this quest"
- Pin button -- "Pin this quest to your HUD"
- Filter buttons (All/Open/Closed on Quest Board) -- explain what each filter shows
- Region toggle -- "Define a rectangular area with two corners"

**Implementation:** Use Minecraft's built-in `ButtonWidget.setTooltip(Tooltip.of(Text))` API (available in MC 1.21.11).

**Files:**
- `client/.../gui/screen/QuestScreen.java`
- `client/.../gui/screen/MainScreen.java`

### F7: Configurable pinned quest width

**Goal:** A config option for the width of pinned quest HUD boxes.

**Behavior:** Add a `pinnedWidth` option to a new `~/.fabric/config/disquests/config.json` file (separate from `colors.json`, since this is not a color). Default: 200 (current `MAX_WIDTH`). `HudPinRenderer` reads this value instead of the hardcoded constant. Create a `DisquestsConfig` loader similar to `ColorConfig` but for general settings.

**Files:**
- Create: `client/.../gui/helper/DisquestsConfig.java` -- config loader for `config.json`
- `client/.../hud/HudPinRenderer.java` -- read width from `DisquestsConfig`

### F8: Rainbow "Disquests" title on hover

**Goal:** The MainScreen title "Disquests" has a rainbow color animation when the mouse hovers over it.

**Behavior:** In `MainScreen.render()`, detect if mouse is over the title text area. If hovering, render each character with a color cycling through the HSB spectrum, animated over time using a tick counter. If not hovering, render normally.

**Files:**
- `client/.../gui/screen/MainScreen.java` -- custom title rendering in `render()`

### F9: Rename "Server" tab to "Quest Board"

**Goal:** Rename the Server tab to "Quest Board" for clarity.

**Behavior:** Tab text changes from "Server" to "Quest Board". Tab width may need to increase slightly to fit the text.

**Files:**
- `client/.../gui/screen/MainScreen.java` -- tab label and width

### F10: Date format change

**Goal:** Change date display format from `yyyy-MM-dd HH:mm` to `HH:mm dd MM yyyy`.

**Example:** `2026-03-16 20:14` becomes `20:14 16 03 2026`

**Pattern:** `DateTimeFormatter.ofPattern("HH:mm dd MM yyyy")`

**Files:**
- `client/.../gui/widget/list/QuestListWidget.java` -- update `DateTimeFormatter` pattern
- Any other files that format dates for display

---

## Out of Scope

- Per-block (Notion-style) inline editing -- deferred due to Minecraft GUI limitations
- BlueMap integration changes
- Server-side command system changes
- E2E test framework (noted in CLAUDE.md as needing rewrite, but separate effort)
