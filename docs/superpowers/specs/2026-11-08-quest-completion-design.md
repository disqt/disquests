# Quest Completion and Archive Design

**Date:** 2026-11-08
**Status:** Designed, ready for planning
**Branch:** `feat/quest-completion` (off `main`)

## Context

A quest today is Private, Closed or Open and lives until its owner deletes it. There is no way
to say "we did it": finished builds either stay on the board forever or get deleted, and the
record of who worked on them goes with them.

## Intent

Let the people working on a quest mark it **complete**. A completed quest leaves the active
lists and the map and is kept, with its full record, in an **Archived** tab for everyone who
took part. A completed quest can be reopened. Later (out of scope here) completing will be able
to hand out a physical certificate item carrying the quest's record; this design keeps the data
ready for that.

## Goals

- A **Complete** button on a quest's view screen. Confirming it marks the quest completed and
  records when and by whom.
- A third tab on the home, **Archived**, after "My Quests" and "Quest Board". It lists every
  completed quest the player owned or contributed to, newest completion first. Same entry
  component, search and tag filter as the other tabs.
- Completed quests disappear from "My Quests", from the "Quest Board" and from the map, for
  everyone.
- Opening an archived quest shows it read-only with a "Completed on <date> by <player>" line and
  a **Reopen** button.
- **Reopen** asks for confirmation: "Reopen this quest? It goes back to the active lists, and
  its completion time will be replaced if it is completed again." Confirming clears the
  completion and the quest returns to the lists it was in before, with its previous visibility.
- Everything about the quest is kept while archived: title, content, tags, area, owner,
  contributors and their permissions. HUD pins are dropped (see Client UI).

## Non-goals

- The certificate item, the "generate a certificate" checkbox, and any in-game display of it
  (banner, frame). Next project.
- A completion history across several complete/reopen cycles. Only the current completion is
  stored; certificates (later) keep their own copy, so they are unaffected by a reopen.
- Rewards, stats, notifications to offline players, Discord posts.
- Archiving quests that were never completed (e.g. abandoned). Delete stays the way to remove
  those.

## Anti-goals

- An accidental click that archives a quest with no way back. Both directions confirm, and
  Reopen exists.
- Old clients (0.7.x and earlier) showing completed quests as if they were still active, or
  crashing on the new state.
- Completed quests cluttering the board or the map.

## Constraints

- Protocol: new fields are trailing-optional; never insert mid-packet; `ProtocolVersion.CURRENT`
  becomes `V3`. `Visibility` is sent as an enum ordinal and old readers reject unknown ordinals,
  so completion is **not** a new `Visibility` value: it is two separate fields.
- Old clients: the server sends completed quests only to clients at V3 or later. Older clients
  simply do not see them (they disappear from their lists, which matches the intent).
- Database: existing installs must migrate on plugin start without data loss (add columns).

## Design

### Data

`QuestData` gains three trailing components: `long completedAt` (epoch millis, `0` = not
completed) `UUID completedBy` (null when not completed) and `String completedByName` (null when unknown). A quest is *archived* exactly when
`completedAt != 0`. The client `Quest` mirrors both, with `isCompleted()`.

SQLite: `quests` gains `completed_at INTEGER NOT NULL DEFAULT 0` and `completed_by TEXT`, added by
an idempotent migration (`ALTER TABLE ... ADD COLUMN` when `PRAGMA table_info` lacks them).

### Protocol (V3)

- Quest encoding (`writeQuest`/`readQuest` with version ≥ V3): after the polygon block,
  a fixed 8-byte `long completedAt`, then a nullable UUID `completedBy` and a nullable string
  `completedByName` (existing nullable helpers). Readers below V3 neither write nor read them.
- New C2S packets: `COMPLETE_QUEST` (0x0B, questId) and `REOPEN_QUEST` (0x0C, questId).
- Server responses reuse `UPDATE_QUEST` (per recipient, per version) and `DELETE_QUEST_S2C`:
  - On complete: owner and contributors at V3+ get `UPDATE_QUEST` with the completion set;
    every other mod player who could see it (Open/Closed board) gets `DELETE_QUEST_S2C`; owner
    and contributors below V3 get `DELETE_QUEST_S2C` too.
  - On reopen: the reverse, using the existing broadcast rules for the quest's visibility.
- Sync: `getServerQuests` excludes completed quests. `getQuestsForPlayer` includes them for V3+
  clients only.

### Permissions

Complete and Reopen are allowed to the owner and to contributors with edit rights (the same
rule as editing). Others see neither button. The server enforces this; the client only hides
the buttons.

### Client UI

- Home: `ClientSession.Tab` gains `ARCHIVED`; a third tab button `tab-archived` ("Archived" /
  "Archivées") in both the map layout and the legacy layout. The Archived list: completed quests
  from the player's own list, sorted by `completedAt` descending, search and `#tag` filter
  applied; no Open/Closed filter row; no "New Quest" or "Join" action. On the map layout the map
  keeps showing only active quests whichever tab is selected.
- `MainScreen.visibleQuests()` for My Quests and Quest Board skips completed quests (defensive,
  the server already filters).
- Quest view screen:
  - Active quest, player may edit: a **Complete** button (`btn-complete`) in the button row,
    with a confirm overlay "Mark this quest as completed? It moves to the Archived tab for
    everyone who worked on it."
  - Completed quest: read-only (no Edit, no Leave, no Join/Request), a line `completed-label`
    "Completed on <date> by <player>", and, for players who may edit, a **Reopen** button
    (`btn-reopen`) with the confirm text from Goals.
- HUD pins: a completed quest is unpinned for everyone (server removes the pin rows on
  complete; client drops it from the HUD).
- Lang keys in `en_us.json5` and `fr_fr.json5` (French informal "tu").

### Tests

- Codec: V3 round-trip of `completedAt`/`completedBy`; V2 encoding omits them; new packets
  round-trip.
- DataManager: migration adds the columns idempotently; complete/reopen persist; server-quest
  query excludes completed; player query includes them.
- ServerPacketHandler: complete/reopen permission checks; per-recipient broadcast (V3 owner gets
  UPDATE, V2 contributor and board viewers get DELETE); pins removed on complete.
- E2E journey `QuestCompletionJourney`: create, complete (confirm), quest gone from My Quests
  and in Archived with the completed line, reopen (confirm), back in My Quests; a duo step where
  a contributor sees it move to their Archived tab.

### Archived quests and the rest of the system (decided during review, designer's calls)

- **Server-side lock**: while completed, the server refuses `SAVE_QUEST`, `UPDATE_CONTRIBUTORS`,
  `UPDATE_VISIBILITY`, `PIN_QUEST`, `JOIN_QUEST`, `REQUEST_COLLABORATION` and `LEAVE_QUEST` for
  that quest (silently, like other unauthorized packets). Only `REOPEN_QUEST` and the owner's
  `DELETE_QUEST` are accepted. Saves never touch `completed_at`/`completed_by` (the upsert keeps
  the stored values; only complete/reopen write them).
- **Pending collaboration requests** for the quest are deleted on complete (the owner's inbox
  count is resent like after any request change).
- **Wiki-links** keep resolving: `WikiLinkResolver` looks quests up in the database whatever
  their state, so `[[Gare 2.0]]` still becomes a link. A participant opens the archived quest;
  for anyone who doesn't have it in their cache the link does nothing, as for any quest they
  cannot see today.
- **Who is notified**: the existing visibility rules decide who saw the quest (Private: owner
  and contributors; Open/Closed: all mod players). Encoding is per recipient and per version,
  as everywhere else. Offline participants get the completed quest in their next sync; offline
  non-participants simply stop receiving it.
- **Permissions use the current state**: a contributor whose edit rights were removed, or who
  left, cannot reopen; leaving is refused while archived anyway.
- **Completer's name**: the quest carries `completedByName` (resolved by the server from
  `player_names`, sent after `completedBy`), so the "Completed on <date> by <player>" line works
  for players the client has never seen. Unknown name → "Completed on <date>".

## Left to the builder

- Exact button placement and the date format (locale-aware short date is fine).
- Whether the Archived tab shows a count badge.
- Whether archived quests can still be deleted by the owner (my call: yes, same confirm as
  today).
