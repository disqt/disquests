# Subquests Design

**Date:** 2026-11-09
**Status:** Approved
**Builds on:** 0.8.0 (world map, completion and Archived tab)

## Context

Big projects on the server are really several projects. "Westenheim hub" is a place, and the
work around it is "Améliorer les intérieurs de Westenheim", "Église satanique", "Wagons", each
with its own people, area and progress. Today these are unrelated quests: nothing on the board
or the map says they belong together, and the hub's page cannot show how its parts are going.

## Intent

Let a quest have **subquests**, forming a tree. Opening a quest shows its subquests at a glance
(who is on each, visibility, a snippet) with one-click navigation down and back up. The lists
nest subquests under their parent. The map stays readable: subquests only appear when you are
looking at their tree or when they are yours.

## Goals

- A quest can have any number of subquests; a subquest is a full quest (own owner,
  contributors, tags, area, content, visibility, completion).
- **Strict tree, one parent per quest, at most 3 levels in total** (main quest, subquest,
  sub-subquest). "Related, but not part of" stays a wiki-link (`[[Gare 2.0]]`).
- **Quest view** (layout A from the brainstorm):
  - Under the description, a "Subquests (n)" section. Each row: title (opens it), who works on
    it ("Nobody yet" when no contributors), visibility badge, first ~80 characters of the
    description as plain text. Rows with children have a ▾/▸ toggle and show their children
    indented. Only subquests the viewer may see are listed.
  - A subquest's view starts with a breadcrumb trail of its ancestors
    ("Westenheim hub › Améliorer les intérieurs › Taverne"), each part clickable. Ancestors the
    viewer cannot see are left out of the trail.
  - "+ Add subquest" (for players who may edit this quest, hidden at level 3) opens the
    new-quest editor with the parent preset.
- **Quest lists** (My Quests, Quest Board): subquests nest under their parent, indented,
  **collapsed by default**; the parent shows a ▸ toggle and a child count.
- **Map**: only main quests' zones by default. A subquest's zone is also drawn when:
  - the selected quest is in its tree (selecting the main quest or any quest of the tree shows
    the whole tree; selecting something outside the tree hides it again), or
  - the viewer is the subquest's owner or a direct or inherited contributor.
- **Restructure**: attach an existing quest under another, move a subquest to a different
  parent, or detach it to make it a main quest. Each move respects the depth limit, the
  visibility rule and the editor's rights on both ends.

## Rules

### Visibility

Order: Private < Closed < Open. A quest is never more visible than its parent.

- Choosing a visibility above the parent's is not offered (and refused by the server).
- **Lowering** a quest's visibility lowers every descendant that is now above it, cascading
  down. Raising a quest does not touch its descendants.
- Attaching or moving a quest under a less visible parent lowers it (and its descendants) to
  fit; the confirm dialog says so.

### Access

- **Inherited access**: owner and contributors of a quest have the same access (view, and edit
  if they can edit) to every descendant. Inheritance is live: removing Max from the hub removes
  what he inherited from it.
- **Effective rights (designer's call)**: a player's right on a quest is the most permissive
  of what they hold on it and on each ancestor (an explicit view-only entry on a subquest does
  not cap edit rights inherited from above). Today's owner-only powers (manage contributors,
  change visibility, delete, answer join requests) belong to the quest's owner **and to the
  owner of any ancestor**; edit rights, direct or inherited, never grant them.
- **Restructure rights (designer's call)**: attaching, moving or detaching a quest needs
  owner powers on that quest (own or inherited) and edit rights on the new parent. Losing
  access because of a move is accepted; the server sends `DELETE_QUEST_S2C` to everyone who
  can no longer see a quest after a move, a delete-with-promotion or an access change, and
  `UPDATE_QUEST` to everyone who newly can.
- **Not upward**: a contributor of a subquest only, e.g. Tom on a Private "Église satanique"
  under a Private hub, sees just his subquest. For him it behaves as a main quest: top of his
  list, no trail, on the map as one of his own.
- Board visibility of a subquest follows its own visibility, as for any quest (Open/Closed
  subquests of an Open hub are on the board, nested under it).

### Completion

- The Complete confirm gains a checkbox "Also complete its N unfinished subquests", **ticked by
  default**, shown only when there are unfinished descendants. Ticked: every unfinished
  descendant is completed with the same time and completer.
- Unticked: only this quest is archived; its unfinished subquests stay active and show as main
  quests in the lists (their trail still names the archived parent, clickable for those who
  can open it).
- Completing the last subquest does nothing to the parent.
- Reopening a quest reopens only that quest; subquests completed with it stay archived.
- Archived tab: flat list as today, with the trail line on archived subquests.
- "Unfinished subquests" means every non-archived descendant. Whoever may complete a quest
  already has (inherited) edit rights on all its descendants, so the ticked box never fails on
  permissions.
- An archived quest is locked as in 0.8.0: nothing can be added, attached or moved under it.
  Reopening a subquest whose parent is archived is allowed; it then shows as a main quest, like
  the unticked case.
- Visibility cascades include archived descendants (the cap holds for every quest).

### Deletion

- The delete confirm lists the subquests and has a checkbox "Also delete its N subquests",
  **unticked by default**.
- Ticked: the whole subtree is deleted. Delete rights on the quest imply them on every
  descendant (owner powers are inherited), so this cannot partly fail.
- Unticked: direct children move up to the deleted quest's parent, or become main quests. Their
  people change accordingly (they gain the grandparent's, lose the deleted quest's); no rights
  on the grandparent are needed, and the visibility cap still holds since children were already
  at or below the deleted quest.

## Non-goals

- Multiple parents / graph structure. Researched: GitHub sub-issues, Jira, Redmine, Linear all
  keep one parent and use separate links for "related".
- Progress roll-up (e.g. "2 of 4 done") on the parent. Can come later.
- Dependencies or ordering between subquests ("do A before B").
- Inheriting tags, area or content from the parent.

## Anti-goals

- An Open quest under a Private one (leaks the existence and title of private work).
- A map cluttered with every subquest zone of every project.
- Losing subquests by accident when deleting or completing a parent.
- Old clients (0.8.x and earlier) breaking: they must keep working, seeing subquests as plain
  quests.

## Constraints

- Protocol: new fields trailing-optional; `ProtocolVersion.CURRENT` becomes `V4`. The parent id
  is a nullable UUID appended after the completion block. Clients below V4 get quests without it
  (subquests then look like ordinary quests to them).
- Per-recipient parent data (designer's call): the server sends `parentId` only when the
  recipient may see the parent, and always sends a `depth` byte (1-3) so a client can hide
  "+ Add subquest" and filter the parent picker even when the ancestors are hidden. The trail is
  built client-side from cached ancestors.
- Old clients: a save from a client below V4 never clears `parent_id` (the upsert keeps it, as
  with completion); a visibility above the parent's cap is refused silently. Server-side
  permission checks, including inherited ones, apply whatever the client version.
- Database: `quests` gains `parent_id TEXT NULL` via the existing idempotent migration pattern;
  existing quests become main quests.
- The server is the authority for depth, cycles (impossible with one parent plus a depth check,
  but checked), visibility caps and inherited permissions; the client only hides options.
- Inherited access must apply everywhere permissions are checked today (save, contributors,
  visibility, pin, complete/reopen, delete) and in who receives quest updates.

## Left to the builder

- Packet shape for attach/move/detach (a dedicated C2S packet vs a field on SAVE_QUEST), and for
  the complete/delete checkboxes (flags on the existing packets are fine).
- Exact snippet length, indentation, and how the ▸ count looks.
- Where "attach to a parent" lives in the UI (my call: a "Parent quest" picker in the quest
  editor, listing quests the player can edit, filtered by depth and cycles).
- Search in the lists: my call is that a matching subquest shows with its ancestors expanded.
- E2E coverage: at least create/navigate a 3-level tree, visibility cascade, complete with and
  without the checkbox, delete with and without it, and a duo journey for inherited access.
