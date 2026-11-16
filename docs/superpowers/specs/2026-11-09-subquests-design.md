# Quest Trees, Quest Lines and Roles Design

**Date:** 2026-11-09, rewritten 2026-11-15, canvas dropped 2026-11-16
**Status:** Draft, awaiting approval. Replaces the subquests design approved on 2026-11-09
(inherited access, visibility caps and the "Subquests section" layout are gone).
**Builds on:** 0.8.0 (world map, completion and Archived tab)

Anything marked "Claude's call" was decided by Claude to keep the design complete and is the
first thing to challenge. Everything else was asked for or agreed by the owner.

## Context

Big projects on the server are really several projects. "Westenheim hub" is a place, and the
work around it is "Améliorer les intérieurs", "Église satanique", "Wagons", each with its own
people, area and progress. Today these are unrelated quests.

Separately, the owner wants to game-master story quests: "Les lumières dans la crypte", then
"Le journal du prêtre", which opens once the first is completed.

## Intent

A quest is a node with two independent relations to other quests:

- **Contains** (a tree): `parent` (none or one) and `children`. "Ferme à fer is a subquest of
  Nouveau main hub." It never makes anything wait: anyone allowed can work on the iron farm
  whenever they like.
- **Then** (a graph without loops): `prev`, the quests that must be completed first, and
  `next`. "Chapter 2 unlocks after chapter 1." This locks a quest: its content is withheld,
  but the quest itself stays visible, with a lock.

Only `parent` and `prev` are stored as relations. `children`, `next` and "locked" are worked
out from them, and "who can see this" from visibility, roles and those.

**Whether a quest is visible is decided by visibility and roles.** Prerequisites decide
whether it is locked. The one place the two meet: what sits inside a locked quest is not
shown until that quest unlocks.

This split is the recurring model in the systems surveyed (FTB Quests, Better Questing, HQM,
WoW; planning tools such as MS Project, Jira and Linear, which show the tree as a list and
dependencies separately).

## Model

```
Quest {
  title, description, location, tags, visibility, completion   // as today
  people: [(player, role)]        // role = READ | WRITE | OWNER, at least one OWNER
  parent: Quest?                  // contains
  prev:   [Quest]                 // then (at most 8)
  autoJoin: bool                  // add the prerequisites' players when it unlocks
}

locked(q)          = q is not completed and some prerequisite of q is not completed
                     (a prerequisite that was deleted counts as not completed)
lockedFor(player,q)= locked(q) and player has neither Write nor Owner on q
canSee(player,q)   = player holds a role on q
                     or (q is not Private
                         and (q has no parent
                              or (canSee(player, q.parent) and not lockedFor(player, q.parent))))
```

A player who can see a quest that is `lockedFor` them gets its title and lock only.

## Roles

Three roles per quest replace today's view-only contributor, contributor who can edit, and
single owner.

| | Read | Write | Owner |
|---|---|---|---|
| See the quest, pin it, leave it (while it is locked: Read sees title and lock only, can leave, cannot pin) | yes | yes | yes |
| Edit title, description, location, tags | | yes | yes |
| Put a quest they own under it, as a subquest | | yes | yes |
| Complete and reopen | | | yes |
| Delete | | | yes |
| Change visibility | | | yes |
| Manage people and their roles, answer join requests | | | yes |
| Change its links (parent, prerequisites, auto-join) | | | yes |

- **Several owners.** The creator starts as the only owner. Owners can give any role,
  including Owner. The last owner cannot be removed, demoted or leave.
- Joining an Open quest, or being accepted on a Closed one, gives Read, as today. A join
  request goes to all owners; the first answer settles it (Claude's call).
- **Rights are per quest. Nothing is inherited.** A role on a quest gives nothing on its
  subquests, and nothing on the quests it leads to.
- **Completing is owner-only**, and only from the quest's own details screen. This changes
  0.8.0, where contributors who could edit could complete; they keep Write and lose that.
- Migration of existing quests: owner to Owner, contributors who can edit to Write, view-only
  contributors to Read.
- **The people screen is a draft with Save and Cancel**, like the quest editor: role
  changes, additions and removals are marked "not saved" and nothing is applied until Save.
  Saving a change that takes away the player's own Owner role asks first: "You are giving up
  Owner on this quest. You will not be able to change this back yourself." Answering a join
  request and "apply to subquests" still act at once ("apply to subquests" is unavailable on
  a row with unsaved changes).
  People are listed by role, owners first, sorted once when the screen opens: changing a role
  does not move the row, and a person added in the draft goes to the bottom.

### Sharing a role down the tree

- In the people screen of a quest that has subquests, each person's row has an **"apply to
  subquests"** button. It asks "Give Juju Write on your N subquests of Westenheim hub?" and,
  on yes, gives that one person the role they hold here on every descendant where the acting
  player is an owner, completed ones included.
- It is one-time and per person. Other people are not touched; descendants the acting player
  does not own are not touched and not mentioned; nothing links the copies afterwards.
- It never lowers anyone: a person who already holds a higher role on a subquest keeps it
  (Claude's call).
- A quest linked in as a subquest later is not affected: it keeps its own people (Claude's
  call).
- Removing a person from a quest that has subquests asks: "From this quest only" or "From this
  quest and my N subquests" (Claude's call).
- "My subquests" in every dialog means the descendants where the player is an owner.

### Who can change links

- **Quests are always created on their own**, in the quest editor, as today, and start
  Private as today. There is no "add subquest" button anywhere: a quest becomes a subquest
  only by being picked in the tree on the edit screen of the quest it goes under (see Screens).
- Making a quest B a subquest of A, or moving it there: Owner on B and Write on A.
- Detaching B from its parent A: Owner on B, **or Owner on A** for a subquest they can see
  (Claude's call: an owner can always remove what hangs directly under their quest; a Private
  subquest they cannot see stays, and costs them nothing).
- An owner of B who cannot see B's parent is told so on B's edit screen ("This quest sits
  inside a quest you can't see") with a Detach button, so B can always be freed (Claude's
  call). Other players with a role on B see it as a top-level quest with no parent.
- Making B unlock after A, removing that, or changing B's auto-join: Owner on B. The player
  must be able to see A; no role on A is needed, since only B changes.
- A completed quest's links are frozen: it cannot be moved, detached or have its
  prerequisites changed until it is reopened (Claude's call).

## Visibility

- Every quest keeps its own setting (Private, Closed, Open). Nothing above it ever changes it,
  and any setting can be chosen whatever the parent's.
- The setting is read relative to the people who can see the parent (for a quest without a
  parent: everyone on the server, so top-level quests behave exactly as today):
  - **Open**: they see it and can join it.
  - **Closed**: they see it and can request to join.
  - **Private**: only the players who hold a role on it. Nobody else, not even an owner of the
    quest it sits in.
- Example, the hub being Private and you a member of it:

  | Quest | Setting | For you |
  |---|---|---|
  | Westenheim hub | Private | visible, you are a member |
  | Intérieurs | Open | visible, you can join |
  | Wagons | Closed | visible, you can request to join |
  | Église satanique | Private | not visible unless you are added to it |

  Someone who is not on the hub sees none of the four.
- A Private quest that Max links under Leo's hub is invisible to Leo: not listed, not
  counted. It is Max's own quest that points at the hub.
- Changing a quest's setting changes who sees what is inside it without touching the settings
  inside; setting it back restores everything.
- The badge shows the quest's own setting, with no extra wording.
- A player who holds a role on a quest always sees it. If they cannot see its parent, it shows
  for them as a top-level quest, with no parent.
- Joining and requesting are refused for a quest the player cannot see.
- **Depth**: the number of levels is the server setting `max-quest-depth` in `config.yml`:
  default 3, **0 means no limit**.

## Quest lines (locking)

- A quest is locked while it is not completed and at least one of its prerequisites is not
  completed. All prerequisites are required. A completed quest is never locked, and an owner
  can complete a quest that is still locked (Claude's call).
- **Players with Write or Owner on a quest are never affected by its lock.**
- Everyone else who can see a locked quest, including players with Read, sees its **title, a
  lock, and what it is waiting on**. No description, tags, location or people; not on the
  map; no joining or requesting until it unlocks. The server does not send those fields.
- **What it is waiting on** never names or counts what the player cannot see:
  - all prerequisites visible to the player: they are listed, each a link;
  - some visible: those are listed, followed by "… and something else you can't see yet";
  - none visible: "This quest isn't available yet. Something else has to be finished first."
- There is no "hidden while locked" setting. **A secret chapter is a Private quest**, and its
  owner reveals it by making it Open or Closed when the time comes.
- A quest inside a locked quest is not shown at all to players who see the outer one as
  locked, unless they hold a role on the inner quest. It appears when the outer quest unlocks.
- **Unlocking**: when the last prerequisite is completed, players who can see the quest get a
  toast "New quest: TITLE". For players who held a role on one of its prerequisites, it is
  also marked as new at the top of their quest board. The mark goes away when they open the
  quest, or after they have opened the quest board three times, whichever comes first
  (Claude's call on the number). It is kept on the client only and not across sessions.
- **Auto-join**: a tickbox on the quest that waits, next to its prerequisites, off by
  default, usable only while the quest is Open. When the quest unlocks, every player who
  holds a role on one of its prerequisites and can see the quest is added to it with Read.
  Players already on it are left as they are. A prerequisite that none of the quest's owners
  can see is skipped, so its members are not revealed (Claude's call).
- **Relocking** (Claude's call): reopening a completed prerequisite locks its follow-ups
  again, except those already completed. Roles, pins and pending join requests are kept.
- **Refused links**: a quest waiting on itself, on one of its ancestors or descendants, or any
  link that closes a loop. Attaching or moving a quest so that it would end up above or below
  one of its own prerequisites or follow-ups is refused. The message names a quest only when
  the player can see it; otherwise it says the move or link is not possible (Claude's call).
- Prerequisites the owner can no longer see are never dropped by a save. They show as one
  line, "something you don't have access to", with one remove button that drops all of them
  at once (no count), so a quest can always be freed (Claude's call). Its owner can
  therefore notice the quest locking and unlocking as an unseen prerequisite is completed or
  reopened; this is accepted.
- **Deleting a quest never unlocks someone else's quest** (Claude's call). The quests that
  waited on it keep the link, now pointing at a deleted quest, and stay locked. Their owners
  see it in the Prerequisites field as "a deleted quest" with a remove button, and the quest
  carries a warning mark in their lists until they deal with it. Links from the deleted quest
  to its own prerequisites are simply removed.
- In a game-mastered line: players have Read, a helper who writes chapter text has Write, and
  only owners of a chapter can complete it and so unlock the next.

## Screens

The main screen stays the map with the quest list. Nothing here replaces it.

### Reading a quest

- Two panes with a **draggable divider**, like the map's side panel; the width is remembered.
- **Left, "where this sits"**: the quest's visible ancestors, the quest itself highlighted,
  the other quests under the same parent (Claude's call), and its own subquests, nested and
  collapsible. Every line opens that quest. A tick shows a completed quest and a lock a locked
  one; **ticks are never clickable**. The pane is absent for a quest with no parent and no
  subquests.
- Completed subquests stay listed, ticked, for every player who would see them if they were
  active, and open read-only like any archived quest (Claude's call). They are not added to
  that player's Archived tab unless the player holds a role on them.
- **Right**: the quest as today. Players with Write or Owner on a quest that has
  prerequisites also see a "Prerequisites" line listing them.
- A locked quest opens to the title, the lock and what it is waiting on (see Quest lines).

### Editing a quest

Two panes with the same draggable divider as the reading view. There is no canvas.

```
SUBQUESTS                      | Title          [ Westenheim hub            ]
Westenheim hub                 | Description    [ ...                       ]
    Intérieurs              x  | Location, tags   as today
    Wagons                  x  | Visibility     [Private] [Closed] [Open]
        Roues                  | Prerequisites  [ type to add...            ]
    [ add a subquest...     ]  |                [ ] When this unlocks, add the
                               |                    players from those quests
```

- **Left, the tree** (Write and Owner): the same indented list as the reading view, in the
  same place, now editable. It shows the whole visible tree the quest belongs to: the quests
  above it, the quests next to it, the quest itself highlighted, and everything under them.
  Nothing that the reading view shows disappears when Edit is pressed.
  - **Drag a quest onto another** to put it inside that quest, at any level. Dragging it onto
    the "take out of the tree" area at the bottom detaches it.
  - Each quest that sits inside another has a remove button when the player may detach it
    (Owner on it or on the quest it sits in). Removing detaches; it never deletes. Completed
    quests cannot be dragged or removed (their links are frozen).
  - Directly under the edited quest's own line, before its subquests, a field
    **"add a subquest..."** brings in a quest
    from outside the tree. It never creates a quest. Picking one that sits inside another
    quest moves it; the dropdown says "inside another quest" next to it.
  - **Dropping a quest onto one of its own subquests swaps them**: with Gare 2.0 inside
    Eglise Satanique, dragging Eglise Satanique onto Gare 2.0 makes Gare 2.0 the container.
    Gare 2.0 takes the other quest's place, and that quest goes inside it with its other
    subquests. Needs Owner on both.
  - **No confirmation pop-ups in the tree.** Nothing is applied until Save, every change is
    marked "not saved", and Cancel undoes all of it.
  - Every move is checked with the usual rights (Owner on the moved quest, Write on the quest
    it goes into) and the loop and depth rules; a refused move says why.
  - **Nothing in the tree is applied until Save.** Every drag, removal and addition is part
    of the same draft as the form: Save applies all of it, Cancel drops all of it. Quests
    whose place has changed are marked "not saved", and a quest taken out of the tree stays
    listed below it as "taken out, not saved" until Save.
  - **Concurrent edits: the last save wins, there are no locks.** The draft is sent as the
    list of changes the player made ("put A inside B", "take C out"), never as a copy of the
    whole tree, so saving does not undo what someone else changed in parts the player did
    not touch. If two players moved the same quest, the later save decides where it is.
  - On Save the server checks each change against the tree as it is then; one that is no
    longer possible (it would now make a loop, exceed the depth, or the rights changed) is
    refused and reported, and the rest still goes through. The player lands on the reading
    view, which shows the tree as it really is.
  - The form's text fields keep today's behaviour: the last save of a quest wins.
  - **A save never carries where the quest sits.** A player who only edited a title or a
    description sends an empty list of tree changes, so their save cannot put the tree back
    to what it was when they opened the editor, whatever happened to it meanwhile. The same
    goes for prerequisites and auto-join: they are sent as "add this, remove that" and only
    when the player changed them.
  - Lines here do not navigate, so the draft is not lost by a stray click (Claude's call).
  - A new quest has the same pane before its first save: its own line and the
    "add a subquest..." field.
- **Right, the form**: title, description, location, tags, as today, then for owners the
  visibility setting, **Prerequisites** and the auto-join tickbox.
- **Prerequisites** (Owner): a type-to-search field; picked quests become removable chips.
  This is the label everywhere in the UI for the quests that must be completed first.
- **How both search fields suggest quests**:
  - Clicking the empty field lists only the quests that can be added right now. For a
    subquest: quests the player owns that can go here. For a prerequisite: quests the player
    can see that would not make a refused link.
  - Typing searches every quest the player can see. A quest that cannot be added is listed
    greyed, with the reason in brackets after its title, for example "Les wagons de la gare
    (you need Owner on it, you have Read)", "(would make a loop)", "(too deep, limit 3
    levels)", "(it is completed)". The list is never cut short.
- **"When this unlocks, add the players from those quests"** (Owner): the auto-join tickbox,
  shown when Prerequisites is not empty, greyed unless the quest is Open.
- Prerequisites and auto-join are part of the same draft: applied by Save, dropped by Cancel.
- **Leaving a containing quest from the subquest's side** (Claude's call): an owner of a quest
  who has no right to edit the quest it sits in still needs a way out. They use the remove
  button on their own quest in the tree. When they cannot see the containing
  quest there is no tree to show it in, so the form has one read-only line with a Detach button:
  "This quest sits inside a quest you can't see" (as under "Who can change links").

### Lists and map

- My Quests and Quest Board: subquests nest under their parent, indented, **collapsed by
  default**; the parent shows a toggle and a count. A search match shows with its ancestors
  expanded. Locked quests are greyed with a lock.
- Map: only top-level quests' zones by default. A subquest's zone is also drawn when the
  selected quest is in its tree, or when the viewer holds a role on it. A quest a player sees
  as locked is not drawn.

## Completion

- From the quest's details screen only, by an owner.
- The confirm has a checkbox **"Also complete my N unfinished subquests"**, unticked by
  default, shown when the player owns at least one unfinished descendant; N counts only those.
  When the player can see other unfinished descendants, a line under it says "2 others belong
  to other players and stay active". It never names their owners.
- Subquests left active under an archived quest are not archived and do not go to the
  Archived tab. They show as top-level quests in the lists and on the board, keep their link
  to the archived parent, and nest back under it if it is reopened. The same holds for a
  prerequisite link to an archived quest.
- Completing the last subquest does nothing to the parent. Reopening reopens only that quest.
- Completed top-level quests leave the board as in 0.8.0. The Archived tab lists the completed
  quests the player holds a role on.
- An archived quest is locked for editing as in 0.8.0: no quest can be linked under it, and
  its own links are frozen.
- Descendants the player cannot see are never counted, named or touched.

## Deletion

- By an owner. The confirm has a checkbox **"Also delete my N subquests"**, unticked by
  default, where N counts the descendants the player owns, and a line "2 others belong to
  other players and are kept" when relevant.
- Ticked: every descendant the player owns is deleted. Kept quests, and all direct children
  when unticked, move up to the nearest surviving ancestor or become top-level quests.
- **A kept quest never becomes visible to more people because of the deletion.** If moving up
  would widen its audience, because the deleted quest was Private or was locked for some
  players, it is set to Private.
  This is the one case where a quest's setting is changed by someone else's action, so its
  owners must find out:
  - The quest remembers the visibility it had and why it changed (which quest was deleted, by
    whom, when).
  - Its page shows its owners a banner: "Set to Private on 15 Nov. The quest it was in,
    'Intérieurs', was deleted by Leo. It was Open before." with two buttons, "Set back to
    Open" and "Keep Private". An owner who could not see the deleted quest gets the same
    banner without its title or the name: "The quest it was in was deleted."
  - It carries a warning mark in its owners' quest lists.
  - Owners who are online when it happens also get a notification saying the same.
  - The banner and the mark stay until an owner presses one of the buttons or changes the
    visibility; then the remembered state is cleared.
  - Players who hold a role on the quest keep it and still see it. Players who could only see
    it through the deleted quest stop seeing it until an owner reopens it.
- Other tools show why this matters: Confluence moves child pages up when a parent is deleted
  and drops the restrictions they inherited, a long-standing reported bug (CONFCLOUD-26297).

## Non-goals

- Several parents for one quest. "Related, but not part of" stays a wiki-link.
- Creating quests from another quest's page ("add subquest").
- A node canvas for arranging quests. Six prototypes were tried; once prerequisites became a
  text field it only did what an indented list does, at a much higher cost in screens and
  things to learn (dropped on 2026-11-16, kept as a follow-up).
- Choosing a quest's parent from the subquest's own page.
- A quest that hides itself until it unlocks, then appears on its own (dropped on 2026-11-15
  in favour of Private quests revealed by hand).
- Progress bars or percentages beyond ticks and counts.
- Inheriting tags, area or content from a parent.

## Planned follow-ups (must not be blocked)

- **Choices**: an "exclusive with" link (completing one closes the other).
- **"Any of" / "at least N of"** prerequisites.
- **Auto-detected objectives**: the server completes a quest itself when its objectives are
  met (kill the ender dragon, kill 50 zombies, earn an advancement), through the same path as
  a manual completion.
- **Per-player quests** (idea from 2026-11-16, not designed): a quest each player completes
  for themselves, such as a default "Introduction to Disquests". One quest, with a
  completion recorded per player instead of once for everyone. To keep this possible,
  "is this prerequisite completed" must always be asked for a given player, never globally,
  even though in this version the answer is the same for everyone.
- **Reveal on completion**: an option when completing a quest to make its Private follow-ups
  visible in the same step.
- **A proper notification system.** Today's handling of join requests is awkward for the
  owner who has to accept them, and several things in this design (join requests reaching
  several owners, a quest set to Private by a deletion, "new quest" marks) are each handled
  on their own. A later design should give players one place for things waiting on them,
  including while they were offline. Out of scope here by the owner's decision (2026-11-15).
- **A visual view of a quest tree or story** (canvas or story board), if the indented list
  turns out not to be enough. The model needs nothing more for it: it would be another way
  to show and edit `parent` and `prev`.

## Anti-goals

- A subquest reaching someone who cannot see its parent, other than its own members.
- A Private quest, or the content of a locked one, leaking through a side door: a description,
  a place on the map, the text behind a wiki-link, a tag suggestion, a parent or prerequisite id, a
  count or a name in a dialog or banner, an error message, a pin, a delete notice, a people list.
- One quest's change silently rewriting other people's quests or roles (the deletion rule
  above is the single, announced exception).
- A deletion handing out someone else's content: exposing a kept subquest, or unlocking a
  quest that waited on the deleted one.
- A map cluttered with every subquest zone of every project.
- Losing subquests by accident when deleting or completing a parent.
- Clients on 0.8.x and earlier breaking: they must keep working, seeing subquests as plain
  quests.

## Constraints

- Protocol: `ProtocolVersion.CURRENT` becomes `V4`; new fields are trailing-optional. Per
  recipient a quest carries: the role of each person, `parentId` only when the recipient can
  see the parent, its depth counted from the highest ancestor the recipient can see, whether
  it is locked for the recipient, `autoJoin`, the prerequisite ids the recipient can see and a
  flag saying whether there are others (never a count). A quest sent as locked carries no
  description, tags, location or people.
- Clients below V4: get subquests as plain quests; never receive a quest that is locked for
  them (they get it when it unlocks, as with completed quests below V3); see a single owner
  (Claude's call: the creator while they are an owner, otherwise the owner added earliest) and
  the others as contributors, Owner and Write both shown as "can edit". A save from such a
  client never clears the parent, the links or `autoJoin`. The server refuses what a role
  does not allow, whatever the client.
- Depth: `max-quest-depth` (default 3, 0 = no limit, negative values treated as the default)
  is sent in the HANDSHAKE as a trailing varint (3 assumed when absent); `/disquests reload`
  resends it. Lowering it never changes existing trees; only new links and moves are checked.
  The limit counts the real depth, hidden ancestors and descendants included. A refusal says
  only that the quest cannot go there; that a player can learn by trying that some hidden
  structure exists is accepted, since no title or content is revealed. Lists stop indenting
  after a fixed number of levels. Every walk up or down the tree is guarded against loops.
- Database: `quests` gains `parent_id`, `auto_join` and the remembered state for a quest set
  to Private by a deletion (previous visibility, deleted quest's title, who, when; sent only
  to the quest's owners); a person's role and the time they were
  added are stored per quest, migrated from the owner column and the `can_edit` flag; links
  are rows `(from_id, to_id, kind)` with `kind` = `UNLOCKS_AFTER` for now, so later link kinds
  need no migration. All through the existing idempotent migration pattern.
- **One function decides everything a recipient learns about a quest**, and every path goes
  through it: sync, quest updates, saves, deletes, the board, pins, wiki-link resolution,
  parent and prerequisite ids. When a change alters who can see a quest, players who lose it
  get `DELETE_QUEST_S2C` and players who gain it get `UPDATE_QUEST`. This replaces today's
  per-handler rules, which do not fit the new model:
  - delete notices for Open and Closed quests go to every mod player;
  - saves reach only the owner and contributors, so other viewers keep stale titles;
  - the handshake sends every pinned id, including quests the player can no longer see.
- Wiki-links: a quest the recipient cannot see, or sees as locked, resolves to the "Private
  Quest" placeholder exactly as an inaccessible quest does today, id included (Claude's
  call). The id is kept because saving relies on it to preserve a link the editor cannot see,
  and it gives nothing away: it is a random identifier, every action on it is checked by the
  server, and the existing limit already lets someone tell that a title exists by linking to
  it.
- Tag suggestions: `SYNC_TAGS` sends each player only the tags of quests they can see fully
  (today it sends every quest's tags to everyone).
- The server is the authority for depth, loops, roles and who can see a quest; the client
  only hides options.

## Left to the builder

- Packet shapes for roles, links, moves and the complete/delete checkboxes.
- How the type-to-search chip field is built in owo-ui (one component for "add a subquest"
  and "Prerequisites"; the tag picker is the nearest existing code).
- Exact indentation, toggle and count styling in the lists; the divider's limits; how "new"
  is shown on the quest board.
- Where the "apply to subquests" button and the three-role selector sit in the people screen.
- E2E coverage, at least: three quests created in the editor and arranged into a three-level
  tree from the edit screen, a move with its confirmation, and a detach from each side;
  the visibility example
  above seen by a non-member, a member and the hub's owner; roles (a Write player cannot
  complete or link, the last owner cannot leave); sharing a role down; complete and delete
  with and without the checkbox when some subquests belong to another player, including the
  deletion that would widen an audience; a duo quest line with an Open locked chapter and a
  Private one revealed by hand, the three wordings of "what it is waiting on", auto-join, and
  a relock.

## Prototypes

Clickable and static prototypes from the design sessions are kept (not committed) under
`.superpowers/brainstorm/`: `view-and-edit-layout.html` (reading and the locked page are
current; its editing pane with a canvas is not), `board-prototype*.html` and `canvas-v3` to `canvas-v6.html` (the dropped
canvas, for the follow-up),
`people-links-locked.html` (people screen), `dialogs-complete-delete-share.html` (the three
dialogs).
