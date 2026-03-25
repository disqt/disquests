# Tags & Wiki-Links Design

Two new features for Disquests: quest tags/labels for organization and filtering, and `[[wiki-links]]` for cross-referencing quests in markdown content.

## Feature 1: Tags/Labels

### Overview

Quests can have multiple tags. A hybrid model combines predefined tags (dimensions, activity types) with custom tags (server-specific locations, project names). Tags appear in the quest list and are filterable via search.

### Data Model

Add `List<String> tags` to `QuestData`. Tags are lowercase strings, max 32 chars each, max 8 per quest.

**Predefined tags** defined in server config (`config.yml`):
```yaml
predefined-tags:
  - overworld
  - nether
  - the_end
  - building
  - redstone
  - farm
```

Custom tags are any string not in the predefined list. No structural difference -- both are stored as plain strings.

**QuestData record change:** Adding `List<String> tags` changes the canonical constructor. All call sites that construct `QuestData` must be updated:

- Server: `DataManager.mapQuestRow()`, `DataManager.withContributors()`, `DataManager.withContributorsAll()`, `ServerPacketHandler.handleSaveQuest()`
- Common: `PacketCodec.readQuest()`, `PacketCodec.writeSaveQuest()` (gains `List<String> tags` parameter)
- Client: `Quest.fromNetwork(QuestData)` (must extract and store tags), `PacketSender.saveQuest()` (gains `List<String> tags` parameter), `QuestScreen` save call sites
- Tests: any test code constructing `QuestData`
- Cleanup: `DataManager.resetDatabase()` must also clear the new `quest_tags` table

### SQLite Schema

```sql
quest_tags (
  quest_id TEXT NOT NULL,
  tag TEXT NOT NULL,
  PRIMARY KEY (quest_id, tag),
  FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
)
```

**Persistence in DataManager.saveQuest():** Within the same transaction as the quest upsert, delete all existing tags for the quest (`DELETE FROM quest_tags WHERE quest_id = ?`) then re-insert the new tag list. This is simpler and safer than diffing.

**Loading:** Follow the existing contributor pattern. `mapQuestRow()` returns `QuestData` with empty tags list. A new `withTags()` method (or batch variant `withTagsBatch()`) enriches quests with tags from `quest_tags` via a single `SELECT quest_id, tag FROM quest_tags WHERE quest_id IN (...)` query. Called from the same places that call `withContributors()`/`withContributorsAll()`.

### Tag Validation

- Allowed characters: `[a-z0-9_-]` (lowercase alphanumeric, underscore, hyphen)
- Server normalizes to lowercase on save
- Duplicate tags in a single quest silently deduplicated
- Empty strings rejected
- Exceeding max 8 tags: server truncates to first 8, no error

### Packet Changes

**No backward compatibility.** Disquests is a mod+plugin pair deployed together -- both client and server always update at the same time. Tags are written/read unconditionally (no `remaining() > 0` guards in `readQuest()`, which would be incorrect anyway since `readQuest()` is called in a loop inside `readQuestList()`).

**S2C (writeQuest/readQuest):** Append after existing fields:
```
VarInt tagCount
String[] tags
```

Always written, always read. Old client + new server (or vice versa) is an unsupported configuration.

**C2S (writeSaveQuest/readSaveQuest):** Add `List<String> tags` to `SaveQuestPayload` record and `writeSaveQuest()` method signature. Append tag serialization after existing fields.

**Predefined tag list:** Sent to clients via HANDSHAKE packet (append after existing fields; HANDSHAKE is a single packet so `remaining() > 0` guard is safe here for graceful handling of old servers during transition). Client stores in `ClientSession` and uses for the tag picker UI and fixed color assignments.

No new packet types needed.

### Permissions

Owner + contributors with `canEdit=true` can add/remove tags. Server validates on `SAVE_QUEST`.

### Client UI

**QuestEntryComponent:**
- Row 3 (currently last-modified + location) replaced with tags
- Last-modified and location/coordinates removed from list entries, kept in QuestScreen
- Tags render as small colored pills, left-aligned
- Predefined tags get fixed colors (nether=amber, building=green, etc.)
- Custom tags get deterministic color from hash of tag name
- No tags: render "no tags" in muted italic text

**QuestScreen (edit mode):**
- New tag section below title field
- Current tags shown as removable pills (click X to remove)
- "Add tag" button opens picker: predefined tags listed first, then text field for custom input
- View-only users see tags but cannot edit

**MainScreen (search):**
- `#tag` syntax in existing search box filters by tag
- Combined search: `#nether highway` = tagged "nether" AND title/content contains "highway"
- Search parser splits `#word` tokens as tag filters, remaining text as title/content search

### Tag Colors

Predefined tags have fixed color assignments:
- `overworld`: gold/yellow
- `nether`: amber/orange
- `the_end`: purple
- `building`: green
- `redstone`: blue
- `farm`: teal

Custom tags derive color from a deterministic hash of the tag string, selecting from a palette of 8-10 distinguishable background/foreground color pairs.

---

## Feature 2: Wiki-Links

### Overview

Players can reference other quests in markdown content using `[[Quest Name]]` syntax. Links render as clickable references that open the linked quest. Access control prevents leaking quest titles to unauthorized players.

### Storage & Resolution

**Server stores raw content** with `[[Quest Name]]` as typed by the author.

**On sync** (SYNC_MY_QUESTS, SYNC_SERVER_QUESTS, UPDATE_QUEST), the server scans content for `[[...]]` patterns and resolves per-recipient:

| Match result | Recipient access | Server sends |
|---|---|---|
| Quest found | Has access | `[[uuid\|Quest Title]]` |
| Quest found | No access | `[[uuid\|Private Quest]]` |
| Quest not found | N/A | `[[\|Quest Title]]` (empty UUID = broken) |

Title lookup is case-insensitive. When multiple quests share the same title, the server uses the quest UUID as a deterministic tiebreaker (lexicographic order).

**Performance:** Resolution batches all `[[...]]` matches in a quest's content into a single SQL query (`SELECT id, title FROM quests WHERE LOWER(title) IN (...)`), then checks access per-match in memory. Max 16 wiki-links per quest content enforced server-side; excess links pass through unresolved.

**On save** (SAVE_QUEST from client), the server reverse-resolves:
- `[[uuid|Display Name]]` where sender has access: stored as `[[Current Title]]` (canonical form, picks up renames)
- `[[uuid|Private Quest]]` where sender lacks access: stored as-is (sender cannot learn the real title through editing)
- `[[uuid|...]]` where UUID no longer exists: stored as `[[Original Text]]` (preserves the text the user typed, link becomes broken)

**Title character safety:** Quest titles containing `]]` are not linkable (the parser matches the first `]]` it finds). This is an acceptable edge case -- titles with `]]` are pathological.

### Regex Patterns

**Server-side content scanning** (raw `[[Quest Name]]` in stored content):
```
\[\[([^\]]+)\]\]
```
Captures the quest name between `[[` and the first `]]`.

**Server-side resolved link scanning** (after sync resolution, `[[uuid|title]]`):
```
\[\[([^|\]]*)\|([^\]]*)\]\]
```
Group 1 = UUID (may be empty for broken links), Group 2 = display title.

**Client-side pre-process** (before commonmark parsing, replaces `[[uuid|title]]` with `<dqlink>`):
Same pattern as server-side resolved link scanning. Replacement: `<dqlink uuid="$1" title="$2"/>`

### Markdown Rendering

**Pre-process approach:** Before passing content to commonmark-java, regex-replace `[[uuid|title]]` patterns with `<dqlink uuid="..." title="..."/>`. The commonmark parser passes these through as `HtmlInline` nodes (raw HTML strings, not parsed DOM). A new handler in `MarkdownRenderer.appendInline()` must detect `HtmlInline` nodes, parse the uuid and title from the literal string (e.g., via regex on `HtmlInline.getLiteral()`), and produce the styled text.

**Rendering styles:**
- Valid link (UUID resolves in client cache): amber text (`#e8a86d`), dashed underline. Click opens the linked quest.
- Broken/private link (UUID missing or not in cache): red text (`#e86d6d`), dashed underline, strikethrough. Click shows toast: "This quest is private or no longer exists."

Both broken and inaccessible links render identically -- no way to distinguish "doesn't exist" from "can't access."

### Autocomplete

In the multiline text editor (edit mode):

1. On keystroke, detect if cursor follows `[[` with no closing `]]`
2. Extract partial text after `[[` as search query
3. Query `ClientCache` (myQuests + serverQuests) filtered by case-insensitive title prefix
4. Show dropdown overlay (max 5 results) below cursor
5. Arrow keys navigate, Enter/click selects, Escape dismisses
6. On select: insert full quest title + closing `]]`

Autocomplete only shows quests from client cache (already filtered by access). For disambiguation when titles collide, show owner name next to title in the dropdown.

**Implementation note:** The dropdown renders as a custom overlay drawn in `TextFieldComponent.draw()` (or a sibling component positioned absolutely). Cursor pixel position can be approximated from the text content and line/column index using `TextRenderer.getWidth()`. Arrow key interception happens in `onKeyPress()` before forwarding to the delegate when the dropdown is visible.

### Edge Cases

- **Quest renamed:** Links use UUID on the client side, so existing links keep working. On next save, server re-resolves UUID to new title for storage.
- **Quest deleted:** UUID no longer resolves. Renders as broken link.
- **Circular links:** Not an issue -- links are rendered text, not evaluated recursively.
- **`[[` in non-link context:** Only matches `[[...]]` with content between brackets. Lone `[[` or `[[ ]]` (whitespace only) ignored.
- **Nested brackets:** `[[quest with [brackets]]]` -- parser matches outermost pair. Edge case, document as unsupported.

---

## Scope & Sequencing

Tags and wiki-links are independent features that touch overlapping code (QuestData, PacketCodec, DataManager, QuestScreen). Recommended build order:

1. **Tags first** -- simpler data model change, exercises the full stack (model, codec, DB, UI) without complex resolution logic
2. **Wiki-links second** -- builds on the updated codec/model infrastructure, adds resolution complexity

---

## Out of Scope

These were considered during brainstorming but deferred:

- **Task assignment** (assigning sub-tasks to specific contributors) -- concept still fuzzy, revisit later
- **Quest templates** (pre-filled quest structures) -- concept still fuzzy, revisit later
- **Physical quest item** (craftable journal book) -- parked, potentially complex
- **Quest status lifecycle** (todo/in-progress/done) -- doesn't fit the notes-style UX
- **BlueMap server-side markers** -- conflicts with existing banner-marker convention
- **Discord webhook notifications** -- declined
