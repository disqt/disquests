# Disquests -- Design Spec

> **Rebrand of BuildNotes.** Unified quest model, server-only storage, per-quest permissions, markdown content, BlueMap integration.

## Overview

Disquests is a Minecraft Fabric client mod + PaperMC server plugin for creating, sharing, and collaborating on in-game notes ("quests"). It replaces the BuildNotes fork's Notes/Builds split, local file storage, and global permission model with a single quest type, server-side SQLite storage, and per-quest ownership and permissions.

### What Changes From BuildNotes

| BuildNotes (current) | Disquests (new) |
|---|---|
| Two content types: Notes and Builds | One type: Quest |
| Three scopes: World, Global, Server | Server-only (all quests in SQLite) |
| Local JSON file storage for World/Global | No local storage |
| Global permission model (VIEW_ONLY / CAN_EDIT) | Per-quest ownership + contributors |
| Scope cycling button | Visibility setting (Private / Closed / Open) |
| Plain text content | Markdown content (commonmark-java) |
| No collaboration model | Owner, contributors, join/request flows |
| No notifications | Inventory badge with pending request count |
| Single "Insert Coords" text paste | Structured coordinates field (point or region) |
| No map integration | BlueMap clickable links |

### What's Removed

- Notes vs Builds distinction (Builds tab, Build data model, Build screens)
- Local JSON file storage (World scope, Global scope, per-server directories)
- `DataManager` file I/O (loadFromFile, writeToFile, path helpers)
- Scope enum and cycling logic
- Global `/buildnotes allow`, `disallow`, `allow_all` commands
- `PermissionManager` with allowAll + editors set
- Image attachments and chunked upload (cut for now)
- Custom fields on Builds (cut for now)
- Credits field (cut for now)

### What's Kept

- Fabric client mod + PaperMC server plugin architecture
- `common/` shared module for packet codec and data models
- Plugin message channel for networking (renamed to `disquests:main`)
- HUD pin system (one pinned quest, rendered via InGameHud mixin)
- Keybind system (N to open, unbound toggle pin)
- Dark UI theme and widget system
- SQLite storage on Paper side

### Migration

No automated migration from BuildNotes. Small user base (~2 players) -- quests are re-created manually. Old BuildNotes JSON files and SQLite data are left in place but ignored.

---

## Data Model

### Quest

```
Quest {
    id:             UUID
    title:          String
    content:        String              // markdown
    ownerUuid:      UUID                // player who created it
    ownerName:      String              // display name (denormalized, updated on join)
    visibility:     PRIVATE | CLOSED | OPEN
    contributors:   List<Contributor>
    lastModified:   long                // epoch seconds

    // Optional fields
    coordinates:    Coordinates | null
    isRegion:       boolean
    coordinates2:   Coordinates | null  // second corner if isRegion
    map:            String | null       // world name, null = "Any"
}

Coordinates {
    x: double
    y: double
    z: double
}

Contributor {
    uuid:       UUID
    name:       String                  // display name (denormalized)
    canEdit:    boolean
}
```

Region coordinates are the most complex optional field. If they prove unnecessary in practice, they can be cut to just single-point coordinates in a future simplification pass.

### Collaboration Request

```
CollaborationRequest {
    id:             UUID
    questId:        UUID
    requesterUuid:  UUID
    requesterName:  String              // display name
    timestamp:      long
}
```

Stored server-side. Cleared when owner approves or denies.

---

## Player Name Resolution

The server maintains a `player_names` table updated every time a player joins:

```sql
CREATE TABLE player_names (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    last_seen INTEGER NOT NULL
);
```

On `PlayerJoinEvent`, the server upserts the player's UUID and current name. Quest data sent to clients includes denormalized owner/contributor names (looked up from this table). If a player renames, names update on their next join.

The contributor management screen and invite flow resolve names from this table. Inviting a player who has never joined the server fails with "Unknown player."

---

## Visibility & Permissions

### Visibility Levels

| Level | Who can see | How to join | Appears in |
|---|---|---|---|
| **Private** | Owner + contributors | Owner invitation only | My Quests (owner/contributors only) |
| **Closed** | Everyone | Request access, owner approves | Server Quests + My Quests (for owner/contributors) |
| **Open** | Everyone | Join freely | Server Quests + My Quests (for owner/contributors) |

### Edit Permissions

Editing is always restricted to owner + contributors with `canEdit = true`. Visibility controls who can **see** the quest, not who can edit it. The owner can set each contributor's edit permission individually.

**Owner can always:**
- Edit the quest
- Change visibility
- Add/remove contributors
- Set contributor edit permissions
- Delete the quest
- Approve/deny collaboration requests

**Contributors with `canEdit = true` can:**
- Edit the quest content, title, optional fields
- Cannot change visibility, contributors, or delete

**Contributors with `canEdit = false` can:**
- View the quest (read-only)

**Non-contributors viewing Open/Closed quests can:**
- View the quest (read-only)
- Join (Open) or Request Access (Closed)

### Default Visibility

New quests default to **Private**.

### Visibility Transitions

When visibility changes:
- **Private -> Open/Closed**: quest becomes visible to all players. Server broadcasts UPDATE_QUEST to all online players.
- **Open/Closed -> Private**: quest disappears for non-contributors. Server broadcasts DELETE_QUEST_S2C to all non-contributor online players. If a non-contributor has the View Quest screen open, the client closes it.
- **Open <-> Closed**: no visibility change for viewers, only affects how new players join (freely vs request).

When a contributor is removed:
- If the quest is Private, it disappears for that player. Client closes Edit/View screens if open.
- If the quest is Open/Closed, it moves from My Quests to Server Quests for that player.

---

## Screens

### Main Screen

Two tabs: **My Quests** (default) and **Server Quests**.

The active tab, search term, and sub-filter persist in the client session (in-memory only -- resets on game restart). Reopening the mod mid-session returns to the same view.

#### My Quests Tab

Shows quests where the player is the owner or a contributor. Sorted by last modified, with the pinned quest always at the top.

Each list entry shows:
- Title
- First line of content (preview)
- Last modified date
- Visibility badge (Private / Closed / Open) with color coding
- "by [player]" for quests owned by someone else
- Map + coordinates summary (if set)

Notification badge on the My Quests tab header shows the count of pending collaboration requests for quests the player owns.

Buttons: **New Quest**, **Open**, **Close**

#### Server Quests Tab

Shows Open and Closed quests from other players (excluding quests the player already owns or contributes to -- those are in My Quests).

Sub-filters: **All** / **Open** / **Closed** (persisted in-memory).

Each list entry shows the same info as My Quests, plus the owner's name.

Buttons: **Join** (for Open quests), **Request Access** (for Closed quests), **Open** (read-only view), **Close**

### View Quest Screen

Read-only view with markdown-rendered content.

Shows:
- Title with owner name and visibility badge
- Rendered markdown content (bold, italic, strikethrough, lists, checkboxes, links, headers)
- Coordinates and map (if set), with clickable BlueMap link
- BlueMap link for regions uses the center point (average of two corners)

Buttons: **Edit** (if owner or contributor with canEdit), **Delete** (owner only), **Pin to HUD** / **Unpin**, **Close**

### Edit Quest Screen

Editable form for quest content.

Fields:
- **Title** text field
- **Content** multiline text field (markdown input, plain text editing)

Optional fields panel:
- **Coordinates**: display current coords, "Set Pos" button captures player position. "Region" checkbox expands to show Corner 1 / Corner 2 with separate "Set" buttons.
- **Map**: shows current world name, "Auto" button fills from current world, "Clear" button resets to "Any"

Settings (owner only):
- **Visibility** button: cycles Private -> Closed -> Open
- **Contributors** button: opens contributor management

Buttons: **Save**, **Close**

Close without saving prompts "Discard unsaved changes?" if edits were made.

### Contributor Management Screen

Accessed from Edit Quest screen (owner only).

Shows list of current contributors with:
- Player name
- Edit permission toggle (can edit / view only)
- Remove button

Shows pending collaboration requests (for Closed quests) with:
- Player name
- Approve / Deny buttons

Invite: text field for player name. Server resolves name to UUID via `player_names` table. Fails with "Unknown player" if the name isn't found (player must have joined the server at least once).

### Inventory Badge

A small icon rendered on the inventory screen (via mixin into `HandledScreen`) in the top-right corner area. Serves as:
1. **Entry point** -- click to open Disquests main screen
2. **Notification indicator** -- shows a red badge with count when the player has pending collaboration requests on quests they own

Badge count updates when COLLABORATION_REQUEST packets arrive.

---

## HUD Pin

- One quest can be pinned at a time
- Pinned quest displays as an overlay on the in-game HUD (bottom-left)
- Shows quest title and first few lines of content (plain text, not rendered markdown)
- Pin state stored server-side in the `pinned_quests` table (follows player across machines)
- Pinned quest appears at the top of the My Quests list
- Toggle via "Pin to HUD" / "Unpin" button on View Quest screen
- Optional unbound keybind to toggle pin
- When a pinned quest is deleted (DELETE_QUEST_S2C), the client clears pin state and removes the HUD overlay

---

## Markdown Rendering

### Library

**commonmark-java** (~235 KB, zero dependencies). Shaded into the mod JAR via Gradle shadow/include with package relocation to `com.disqt.shaded.commonmark` to avoid classpath conflicts with other mods.

Extensions: `gfm-strikethrough`, `task-list-items` (for `- [ ]` / `- [x]` checkboxes).

### Supported Syntax

| Markdown | Rendering |
|---|---|
| `**bold**` | Minecraft bold formatting |
| `*italic*` | Minecraft italic formatting |
| `~~strikethrough~~` | Minecraft strikethrough formatting |
| `# Heading` (h1-h3) | Bold text. If matrix scaling proves clean, h1 gets 1.5x scale, h2 gets 1.25x. Otherwise, headings are just bold with a blank line above. |
| `- bullet` | Indented with bullet character |
| `1. numbered` | Indented with number prefix |
| `- [ ]` / `- [x]` | Checkbox characters (unchecked / checked with strikethrough) |
| `[text](url)` | Colored + underlined, click event opens URL in browser |
| `> blockquote` | Indented with gray `|` prefix character |

### Implementation

1. Parse markdown string to AST: `Parser.builder().extensions(exts).build().parse(content)`
2. Walk AST with custom `AbstractVisitor` that builds a list of rendered lines (each a `MutableText` with styles)
3. Word-wrap using `TextRenderer.wrapLines()`
4. Render in a scrollable widget with variable line heights for headers (if scaling is used)

### Edit vs View

- **Edit screen**: plain text editing (user types markdown syntax)
- **View screen**: rendered markdown display

---

## BlueMap Integration

### Configuration

Paper plugin config (`plugins/Disquests/config.yml`):

```yaml
bluemap-url: "https://disqt.com/minecraft/map"
```

If empty or absent, BlueMap links are not shown.

### Handshake

The server sends the BlueMap URL (or empty string) in the handshake packet. The client stores it in the session and uses it to construct links.

### Link Construction

For a single point at `(x, y, z)` on map `world_new`:
```
{bluemap-url}/#world_new:{x}:{y}:{z}:50:0:0:0:0:flat
```

For a region with corners `(x1, y1, z1)` and `(x2, y2, z2)`:
- Link targets the center: `((x1+x2)/2, (y1+y2)/2, (z1+z2)/2)`

Link is displayed as clickable text on the View Quest screen. Click event opens the URL in the system browser.

---

## Networking Protocol

Channel: `disquests:main`. First byte = PacketType ID.

Server only sends packets to players who have registered the `disquests:main` channel (checked via `player.getListeningPluginChannels().contains("disquests:main")`). Players without the mod are unaffected.

### Connection Flow

1. Player joins the server
2. Server waits ~40 ticks for Fabric channel registration
3. Server sends **HANDSHAKE** (BlueMap URL, pending request count, pinned quest UUID)
4. Client sends **REQUEST_SYNC**
5. Server responds with **SYNC_MY_QUESTS** + **SYNC_SERVER_QUESTS**
6. Client is now fully synced; subsequent changes arrive as individual UPDATE/DELETE packets

### Packet Types & Payloads

**C2S (Client to Server):**

| Packet | Fields |
|---|---|
| REQUEST_SYNC | *(empty)* |
| SAVE_QUEST | questId: UUID, title: String, content: String, coordX/Y/Z: double?, isRegion: bool, coord2X/Y/Z: double?, map: String? |
| DELETE_QUEST | questId: UUID |
| JOIN_QUEST | questId: UUID |
| REQUEST_COLLABORATION | questId: UUID |
| RESPOND_COLLABORATION | requestId: UUID, approved: boolean |
| UPDATE_CONTRIBUTORS | questId: UUID, ops: List<{action: ADD\|REMOVE\|UPDATE, playerUuid: UUID, canEdit: boolean?}> |
| UPDATE_VISIBILITY | questId: UUID, visibility: PRIVATE\|CLOSED\|OPEN |

**S2C (Server to Client):**

| Packet | Fields |
|---|---|
| HANDSHAKE | bluemapUrl: String, pendingRequestCount: int, pinnedQuestId: UUID? |
| SYNC_MY_QUESTS | quests: List<Quest> (full data including contributor list) |
| SYNC_SERVER_QUESTS | quests: List<Quest> (full data, no contributor details for non-members) |
| UPDATE_QUEST | quest: Quest (full data) |
| DELETE_QUEST_S2C | questId: UUID |
| COLLABORATION_REQUEST | requestId: UUID, questId: UUID, questTitle: String, requesterName: String |
| COLLABORATION_RESPONSE | questId: UUID, approved: boolean, quest: Quest? (included if approved) |

### Broadcast Rules

| Event | Who receives |
|---|---|
| Quest created (Private) | Owner only (no broadcast needed) |
| Quest created (Open/Closed) | All online players with mod |
| Quest edited | Owner + all online contributors + all online players with mod (if Open/Closed) |
| Quest deleted | Same as edited |
| Visibility Private -> Open/Closed | All online players with mod (quest appears) |
| Visibility Open/Closed -> Private | All non-contributor online players with mod (quest disappears) |
| Contributor added/removed | The affected player |
| Collaboration request | Quest owner |
| Collaboration response | The requester |

### Scalability Note

Full sync sends all visible quests in one or more packets. For a small private server (~5-20 players, ~100s of quests), this fits within the 1 MB plugin channel message limit. If quests grow large (very long markdown content), the sync may need to be chunked across multiple packets. This is a future concern -- not implemented in v1.

---

## Server-Side Storage (SQLite)

### Tables

```sql
CREATE TABLE quests (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    owner_uuid TEXT NOT NULL,
    visibility TEXT NOT NULL DEFAULT 'PRIVATE',
    coord_x REAL,
    coord_y REAL,
    coord_z REAL,
    is_region INTEGER NOT NULL DEFAULT 0,
    coord2_x REAL,
    coord2_y REAL,
    coord2_z REAL,
    map TEXT,
    last_modified INTEGER NOT NULL
);

CREATE TABLE contributors (
    quest_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    can_edit INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (quest_id, player_uuid),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);

CREATE TABLE collaboration_requests (
    id TEXT PRIMARY KEY,
    quest_id TEXT NOT NULL,
    requester_uuid TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    UNIQUE(quest_id, requester_uuid),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);

CREATE TABLE pinned_quests (
    player_uuid TEXT PRIMARY KEY,
    quest_id TEXT NOT NULL,
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);

CREATE TABLE player_names (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    last_seen INTEGER NOT NULL
);
```

---

## Server Commands

Minimal for now (YAGNI). Admin commands can be added later as needed.

```
/disquests reload    -- Reload plugin config
```

---

## Plugin Config

`plugins/Disquests/config.yml`:

```yaml
# BlueMap web map URL. Leave empty to disable BlueMap links.
bluemap-url: "https://disqt.com/minecraft/map"
```

---

## Client Config

No client-side config files. Keybinds are handled by Fabric's `KeyBinding` system (persisted in vanilla `options.txt`). Session state (active tab, search term, filter) is in-memory only -- resets on game restart. Pin state is server-side.

---

## Keybinds

| Action | Default | Configurable |
|---|---|---|
| Open Disquests | N | Yes |
| Toggle HUD pin | Unbound | Yes |

---

## Known Limitations

- **Orphaned quests**: If a quest owner leaves the server permanently, their quests remain in SQLite. Open/Closed quests are visible but no one can manage contributors or respond to requests. Admin cleanup can be added later via `/disquests admin delete <id>`.
- **No offline/singleplayer support**: The mod requires the Paper plugin. Without it, the mod does nothing.
- **No real-time collaborative editing**: If two contributors edit the same quest simultaneously, last-write-wins. No conflict resolution.

---

## Rebrand Checklist

- Mod ID: `disquests`
- Plugin name: `Disquests`
- Channel: `disquests:main`
- Package namespace (paper + common): `com.disqt.disquests.*`
- Package namespace (client): `com.disqt.disquests.*` (no longer following upstream)
- Keybind category: `disquests`
- Server command: `/disquests`
- Config directory: `config/disquests/`
- Plugin data directory: `plugins/Disquests/`
- GitHub repo: rename or new repo
