# Subquests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quests form a strict tree (one parent, max 3 levels) with inherited access, capped
visibility, nested lists, a subquest section and trail on the quest screen, and tree-aware map,
complete and delete.

**Architecture:** One nullable `parent_id` column. The server owns every tree rule through a
Minecraft-free `QuestAccess` (effective role, visibility, depth) and a single
"snapshot audience, mutate, send diffs" delivery path that replaces the per-handler broadcast
code. Each quest sent at V4 carries per-recipient tree data (parent id if visible, depth,
inherited role, visibility cap) so the client never recomputes permissions from hidden
ancestors. The client keeps tree logic in a Minecraft-free `QuestTree` and only renders.

**Tech Stack:** Java 25, Fabric (owo-ui 0.13), Paper 26.x, SQLite, JUnit 5 + Mockito, the E2E
journey harness.

**Spec:** `docs/superpowers/specs/2026-11-09-subquests-design.md`

## Global Constraints

- Strict tree: one parent per quest, **at most 3 levels** (root depth 1). No graph.
- Visibility order `PRIVATE < CLOSED < OPEN`; a quest is never more visible than its parent.
  Lowering cascades to every descendant above the new value, **including archived ones**.
- Effective role on a quest = max over the quest and its ancestors of: owner → `OWNER`,
  contributor with edit → `EDIT`, contributor → `VIEW`. Owner-only powers (contributors,
  visibility, delete, answer join requests, restructure) need `OWNER`; editing, complete,
  reopen, add subquest need `EDIT`.
- Protocol: `ProtocolVersion.V4 = 4`, `CURRENT = V4`. All new fields trailing; never insert
  mid-packet. Pre-V4 clients see subquests as plain quests and never lose `parent_id` by saving.
- Completed (archived) quests stay locked as in 0.8.0; nothing is added, attached or moved under
  an archived quest. Reopening a subquest under an archived parent is allowed.
- UI copy in `en_us.json5` and `fr_fr.json5` (French informal "tu", full accents). Strings:
  - `gui.disquests.section.subquests`: "Subquests (%s)" / "Sous-quêtes (%s)"
  - `gui.disquests.btn.add_subquest`: "+ Add subquest" / "+ Ajouter une sous-quête"
  - `gui.disquests.subquests.nobody`: "Nobody yet" / "Personne pour l'instant"
  - `gui.disquests.confirm.complete_subquests`: "Also complete its %s unfinished subquests" / "Terminer aussi ses %s sous-quêtes en cours"
  - `gui.disquests.confirm.delete_subquests`: "Also delete its %s subquests" / "Supprimer aussi ses %s sous-quêtes"
  - `gui.disquests.confirm.delete_promote`: "Its subquests will move up a level." / "Ses sous-quêtes remonteront d'un niveau."
  - `gui.disquests.edit.parent`: "Parent: %s" / "Parent : %s"
  - `gui.disquests.edit.parent_none`: "None (main quest)" / "Aucun (quête principale)"
  - `gui.disquests.confirm.parent_lowers_visibility`: "This lowers its visibility to %s to match the new parent." / "Sa visibilité passera à %s pour suivre le nouveau parent."
- Commit subjects lowercase after the type (commitlint). Run `./gradlew spotlessApply` before each
  commit.

## Review Focus

1. **A pre-V4 client saving a subquest** (no parent field in SAVE_QUEST) must keep `parent_id`
   and must not raise visibility above the cap → Task 2 test `saveQuest_keepsParentId`, Task 5 test
   `oldClientVisibilityAboveCapRefused`.
2. **Access lost by a move or a contributor removal on an ancestor**: the player must get
   `DELETE_QUEST_S2C` for every descendant they no longer see, not only the quest touched →
   Task 4 test `removingHubContributorDeletesInheritedSubquestsForThem`.
3. **Cycles and depth through moves**: attaching a quest under its own descendant, or a 2-level
   subtree under a level-2 quest, must be refused → Task 5 tests `setParentRefusesCycle`,
   `setParentRefusesDepthOverflow`.
4. **Tom's case (subquest member, ancestors hidden)**: no parent id leaks, depth still sent,
   quest shows as a root in his list and on his map → Task 4 test
   `hiddenParentNotSentButDepthIs`, Task 7 test `rootsIncludeQuestsWhoseParentIsNotCached`.
5. **Search inside collapsed trees**: a matching subquest must be visible with its ancestors
   expanded, not hidden in a collapsed parent → Task 7 test `flattenExpandsAncestorsOfMatches`.

---

### Task 1: Protocol V4 (common)

**Files:**
- Modify: `common/.../common/model/QuestData.java`, `common/.../common/ProtocolVersion.java`,
  `common/.../common/PacketType.java`, `common/.../common/PacketCodec.java`, `.claude/CLAUDE.md`
- Test: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`

**Interfaces:**
- Produces:
  - `QuestData` gains trailing components `UUID parentId, int depth, int inheritedRole,
    Visibility maxVisibility` (defaults `null, 1, 0, OPEN` in every existing shorter
    constructor). Withers: `withParentId(UUID)`, `withTreeView(UUID parentId, int depth, int
    inheritedRole, Visibility maxVisibility)`.
  - Role constants in `common.model.QuestRole`: `NONE = 0, VIEW = 1, EDIT = 2, OWNER = 3`
    (final class with int constants, `static int max(int a, int b)`).
  - `PacketType.SET_PARENT((byte) 0x0D)`.
  - `PacketCodec`: V4 quest block after the completion block: nullable UUID `parentId`, byte
    `depth`, byte `inheritedRole`, varint `maxVisibility.ordinal()`.
  - `SaveQuestPayload` gains trailing `UUID parentId` (nullable; `remaining() > 0` guard);
    `writeSaveQuest(..., UUID parentId)` overload.
  - `record CompleteQuestPayload(UUID questId, boolean includeSubquests)`;
    `writeCompleteQuest(UUID, boolean)`; `readCompleteQuest` returns it (`false` when absent).
  - `record DeleteQuestPayload(UUID questId, boolean deleteSubtree)`; `writeDeleteQuest(UUID,
    boolean)`; `readDeleteQuest` returns it (`false` when absent).
  - `record SetParentPayload(UUID questId, UUID parentId)`; `writeSetParent`, `readSetParent`
    (parentId nullable = detach).

- [ ] **Step 1: Write failing tests** in `PacketCodecTest`:
  - `v4QuestRoundTripsTreeFields`: quest with parentId=U, depth=2, inheritedRole=EDIT,
    maxVisibility=CLOSED written/read at V4 → all four equal.
  - `v3EncodingOmitsTreeFields`: same quest at V3 → read back parentId null, depth 1, role 0,
    maxVisibility OPEN, and byte length equals the V3 length of the same quest without tree fields.
  - `saveQuestParentIdTrailing`: payload with parentId round-trips; a payload written by the old
    overload reads parentId null.
  - `completeQuestFlagDefaultsFalse`, `deleteQuestFlagDefaultsFalse`: old writers → flag false;
    new writers with `true` → true.
  - `setParentRoundTripsNullAndValue`.
- [ ] **Step 2:** `./gradlew :common:test` → the new tests fail to compile/fail.
- [ ] **Step 3:** Implement the interfaces above. Keep the old `readCompleteQuest`/`readDeleteQuest`
  callers compiling by updating them in server in the same commit (server handlers use
  `.questId()`, behaviour unchanged until Task 6).
- [ ] **Step 4:** `./gradlew build` → PASS.
- [ ] **Step 5:** In `.claude/CLAUDE.md`, add `SET_PARENT (0x0D)` to the C2S list, V4 to the
  version list, and one line: "**V4 tree block** — after completion: nullable UUID `parentId` (only
  when the recipient may see the parent), byte `depth` (1-3), byte `inheritedRole` (QuestRole),
  varint `maxVisibility`. SAVE_QUEST trailing nullable `parentId` (used on create only);
  COMPLETE_QUEST / DELETE_QUEST trailing booleans `includeSubquests` / `deleteSubtree`."
- [ ] **Step 6:** Commit `feat(common): protocol v4 tree fields and set_parent packet`.

### Task 2: Database tree storage (server)

**Files:**
- Modify: `server/.../papermc/DataManager.java`
- Test: `server/src/test/java/com/disqt/disquests/server/papermc/DataManagerTest.java`

**Interfaces:**
- Consumes: `QuestData.parentId`, `withParentId` (Task 1).
- Produces (all `synchronized`):
  - Column `parent_id TEXT NULL` + index `idx_quests_parent` via `migrateParent()` (same
    PRAGMA pattern as `migrateCompletion`), called from `initialize`.
  - `mapQuestRow` fills `parentId`. `saveQuest` writes `parent_id` **only on insert** (not in the
    `ON CONFLICT DO UPDATE SET` list).
  - `void setParent(UUID questId, UUID parentId)` (null detaches).
  - `List<QuestData> getAllQuests()` (with contributors, tags, polygon, names).
  - `List<UUID> getChildIds(UUID questId)`.
  - `void updateVisibilities(Map<UUID, Visibility> changes)` in one transaction.
  - `void completeQuests(List<UUID> questIds, UUID completedBy, long completedAt)` (one
    transaction; same pin/request cleanup as `completeQuest`, which delegates to it).
  - `void deleteQuestPromotingChildren(UUID questId)`: in one transaction, set children's
    `parent_id` to the deleted quest's `parent_id`, then delete.
  - `void deleteQuests(List<UUID> questIds)` in one transaction.

- [ ] **Step 1: Failing tests:** `migrationAddsParentColumnIdempotently` (initialize twice on an
  old-schema DB), `saveQuest_keepsParentId` (insert with parent, re-save the same quest with
  `parentId` null → stored parent unchanged), `setParentAndDetach`, `getChildIds`,
  `updateVisibilitiesAppliesAll`, `completeQuestsSetsSameTimeAndDropsPins`,
  `deletePromotingChildrenReparentsToGrandparent` (A>B>C, delete B → C.parent == A; delete root A
  of A>B → B.parent null), `deleteQuestsRemovesAll`.
- [ ] **Step 2:** `./gradlew :server:test --tests '*DataManagerTest'` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command → PASS.
- [ ] **Step 5:** Commit `feat(server): store quest parent and tree operations`.

### Task 3: QuestAccess rules (server, pure)

**Files:**
- Create: `server/.../papermc/QuestAccess.java`
- Test: `server/src/test/java/com/disqt/disquests/server/papermc/QuestAccessTest.java`

**Interfaces:**
- Consumes: `QuestData`, `QuestRole` (Task 1).
- Produces: `final class QuestAccess` built from `Function<UUID, QuestData> lookup` (no Bukkit,
  no SQL), with:
  - `int directRole(UUID player, QuestData q)`
  - `int role(UUID player, QuestData q)` (max over q and ancestors)
  - `int inheritedRole(UUID player, QuestData q)` (max over ancestors only)
  - `boolean canSee(UUID player, QuestData q)`: `role >= VIEW`, or `!q.isCompleted() &&
    q.visibility() != PRIVATE`
  - `int depth(QuestData q)` (root = 1)
  - `int subtreeHeight(QuestData q, Function<UUID, List<QuestData>> children)` (leaf = 1)
  - `List<QuestData> ancestors(QuestData q)` (parent first)
  - `Visibility maxVisibility(QuestData q)` (parent's visibility, `OPEN` for roots)
  - `boolean isAncestorOrSelf(UUID candidate, QuestData q)` (cycle check)
  - `QuestData shapeFor(UUID recipient, QuestData q)`: returns `q.withTreeView(parentVisible ?
    parentId : null, depth, inheritedRole, maxVisibility)`

- [ ] **Step 1: Failing tests** over an in-memory map Hub(owner Leo, Max edit, Juju view,
  PRIVATE) > Interiors(owner Max) > Taverne(owner Juju, Tom view):
  `ownerOfAncestorGetsOwnerRole` (Leo on Taverne = OWNER), `editInheritedOverridesViewOnly`
  (Juju is view on Hub, owner of Taverne; Max is EDIT on Taverne), `noUpwardAccess` (Tom on Hub =
  NONE, canSee false), `depthAndHeight` (Taverne depth 3, Hub height 3),
  `hiddenParentNotSentButDepthIs` (shapeFor(Tom, Taverne): parentId null, depth 3),
  `completedVisibleOnlyToMembers`, `maxVisibilityIsParents`, `cycleDetection`.
- [ ] **Step 2:** `./gradlew :server:test --tests '*QuestAccessTest'` → FAIL.
- [ ] **Step 3:** Implement (ancestor walk bounded at 3 hops plus a visited set, so a corrupt DB
  cannot loop).
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat(server): quest access rules for trees`.

### Task 4: Audience-diff delivery and sync (server)

**Files:**
- Modify: `server/.../papermc/ServerPacketHandler.java`
- Test: `server/src/test/java/com/disqt/disquests/server/papermc/ServerPacketHandlerTest.java`

**Interfaces:**
- Consumes: `QuestAccess` (Task 3), `DataManager.getAllQuests` (Task 2).
- Produces (private, in `ServerPacketHandler`):
  - `QuestAccess access()` (new instance per call over `dataManager::getQuest`).
  - `Map<UUID, Set<UUID>> snapshotAudience(Collection<UUID> questIds)`: quest id → online mod
    players who `canSee` it now.
  - `void deliver(Collection<UUID> questIds, Map<UUID, Set<UUID>> before)`: for each quest
    (deleted ones included) and each online mod player: can see now → `sendQuestTo` (shaped with
    `shapeFor`, keeps the 0.8.0 V3 completed rule); else if in `before` → `DELETE_QUEST_S2C`.
  - `Collection<UUID> withDescendants(UUID questId)`.
  - `handleRequestSync`: my quests = all quests with `role >= VIEW` (completed ones only for V3+),
    board = `canSee` but `role == NONE`; both lists shaped per recipient.
- Every existing handler that broadcasts (`handleSaveQuest`, `handleJoinQuest`,
  `handleRespondCollaboration`, `handleUpdateContributors`, `handleUpdateVisibility`,
  `handleLeaveQuest`, `handleCompleteQuest`, `handleReopenQuest`, `handleDeleteQuest`) now does
  `before = snapshotAudience(withDescendants(id))` → mutate → `deliver(...)`. Remove
  `broadcastQuestUpdate`, `broadcastToContributors`, `notifyCompletionChange`.
  `handleSaveQuest` keeps sending only to members (snapshot filtered to `role >= VIEW`) to
  preserve the 0.8.0 save-scoping rule.

- [ ] **Step 1: Failing tests** (Mockito, existing patterns): `syncIncludesInheritedSubquests`
  (Max gets Interiors and Taverne in my quests), `syncTomSeesOnlyTaverneAsRoot` (parent null in
  the packet), `removingHubContributorDeletesInheritedSubquestsForThem` (UPDATE_CONTRIBUTORS
  removes Max → Max gets DELETE for Hub, Interiors, Taverne), and the existing
  `ServerPacketHandlerTest` cases still pass unchanged.
- [ ] **Step 2:** `./gradlew :server:test` → new tests FAIL.
- [ ] **Step 3:** Implement and remove the replaced helpers.
- [ ] **Step 4:** `./gradlew :server:test` → all PASS.
- [ ] **Step 5:** Commit `refactor(server): deliver quest changes by audience diff`.

### Task 5: Tree-aware permissions and restructuring (server)

**Files:**
- Modify: `server/.../papermc/ServerPacketHandler.java`
- Test: `ServerPacketHandlerTest.java`

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces:
  - Permission checks via `access().role(...)`: edit-level actions (`SAVE_QUEST` on existing,
    `COMPLETE`, `REOPEN`) need `EDIT`; owner-level (`UPDATE_CONTRIBUTORS`, `UPDATE_VISIBILITY`,
    `DELETE_QUEST`, `RESPOND_COLLABORATION`, `SET_PARENT`) need `OWNER`. `JOIN`/`REQUEST` refused
    when `role >= VIEW`. `LEAVE` stays direct contributors only. `PIN` allowed when `canSee`.
  - Create with parent (`SAVE_QUEST` new quest, `parentId != null`): parent exists, not
    completed, `role(parent) >= EDIT`, `depth(parent) < 3`; otherwise the quest is created as a
    root (logged warning). New quests stay `PRIVATE`.
  - `handleSetParent(Player, SetParentPayload)`: quest not completed; new parent (if any) exists,
    not completed, `role >= EDIT` on it, not `isAncestorOrSelf(quest.id, newParent)`,
    `depth(newParent) + subtreeHeight(quest) <= 3`; then `setParent`, lower the subtree to
    `newParent.visibility()` where above it (`updateVisibilities`), deliver for the subtree.
  - `UPDATE_VISIBILITY`: refused when above `maxVisibility`; lowering collects every descendant
    above the new value (archived included) into one `updateVisibilities` call.

- [ ] **Step 1: Failing tests:** `hubOwnerCanDeleteSubquestOwnedByOther`,
  `hubEditorCannotManageSubquestContributors`, `createWithParentNeedsEditOnParent`,
  `createUnderDepth3BecomesRoot`, `setParentRefusesCycle`, `setParentRefusesDepthOverflow`,
  `setParentLowersSubtreeVisibility`, `setParentUnderArchivedRefused`,
  `visibilityAboveParentRefused`, `oldClientVisibilityAboveCapRefused` (V3 player),
  `loweringCascadesIncludingArchived`.
- [ ] **Step 2:** `./gradlew :server:test` → FAIL.
- [ ] **Step 3:** Implement; register `SET_PARENT` in `onPluginMessageReceived`.
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat(server): tree permissions, create under parent, move and visibility cap`.

### Task 6: Complete and delete with subtrees (server)

**Files:**
- Modify: `server/.../papermc/ServerPacketHandler.java`
- Test: `ServerPacketHandlerTest.java`

**Interfaces:**
- Consumes: `CompleteQuestPayload`, `DeleteQuestPayload` (Task 1), `completeQuests`,
  `deleteQuests`, `deleteQuestPromotingChildren` (Task 2).
- Produces:
  - Complete with `includeSubquests`: the quest plus every non-archived descendant, one
    `completeQuests` call (same time and completer); `false` completes only the quest.
  - Reopen: only the quest, also when its parent is archived.
  - Delete with `deleteSubtree`: `deleteQuests(quest + all descendants)`; `false`:
    `deleteQuestPromotingChildren`. Deliver covers the quest, its descendants, and (promotion) the
    moved children's new audiences.
  - Pending-request inbox refresh (0.8.0 handshake resend) covers every owner whose quests lost
    requests.

- [ ] **Step 1: Failing tests:** `completeWithSubquestsArchivesUnfinishedDescendants` (an
  already-archived descendant keeps its own completedAt), `completeWithoutFlagLeavesChildren`,
  `reopenUnderArchivedParentAllowed`, `deleteSubtreeRemovesAll`,
  `deleteWithoutFlagPromotesChildren` (children get UPDATE with new parent/depth),
  `v3ClientDeleteDefaultsToPromote`.
- [ ] **Step 2:** `./gradlew :server:test` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `./gradlew build` → PASS.
- [ ] **Step 5:** Commit `feat(server): complete and delete quest subtrees`.

### Task 7: Client tree model (client, Minecraft-free)

**Files:**
- Modify: `client/.../client/data/Quest.java`, `client/.../client/network/ClientPacketHandler.java`,
  `client/.../client/network/PacketSender.java`, `client/.../client/ClientSession.java`
- Create: `client/.../client/data/QuestTree.java`
- Test: `client/src/test/java/com/disqt/disquests/client/data/QuestTreeTest.java`, `QuestTest.java`

**Interfaces:**
- Consumes: V4 fields (Task 1).
- Produces:
  - `Quest`: fields + getters `parentId`, `depth`, `inheritedRole`, `maxVisibility`;
    `canEdit(me)` = direct edit or `inheritedRole >= EDIT`; `hasOwnerPowers(me)` = `isOwner` or
    `inheritedRole == OWNER`; `isMember(me)` = direct or `inheritedRole >= VIEW`.
  - `ClientPacketHandler.handleUpdateQuest`: `isMine` uses `isMember`.
  - `ClientSession.supportsSubquests()` = server version `>= V4`; `Set<UUID> expandedTreeIds()`
    (session-scoped, empty at start = collapsed by default).
  - `PacketSender`: `setParent(UUID, UUID)`, `completeQuest(UUID, boolean)`,
    `deleteQuest(UUID, boolean)`, `saveQuest(..., UUID parentId)` for creation.
  - `QuestTree` (pure, static, works on `List<Quest>`):
    - `List<Quest> children(UUID id, Collection<Quest> pool)` (title order)
    - `List<Quest> roots(List<Quest> list)` (no parent, or parent not in `list`)
    - `List<Quest> ancestors(Quest q, Function<UUID, Quest> lookup)` (root first, stops at the
      first unknown)
    - `List<Quest> descendants(Quest q, Collection<Quest> pool)`
    - `record Row(Quest quest, int indent, int childCount, boolean expanded)`
    - `List<Row> flatten(List<Quest> list, Set<UUID> expanded, Set<UUID> matches)`: roots in the
      given order, children under expanded parents; when `matches` is non-null, keep only matches
      and their ancestors, with those ancestors expanded.
    - `Set<UUID> treeOf(UUID id, Function<UUID, Quest> lookup, Collection<Quest> pool)` (root's id
      and all descendants)
    - `List<Quest> forMap(List<Quest> active, UUID selectedId, UUID me, Function<UUID, Quest>
      lookup)`: roots, plus quests in the selected quest's tree, plus quests where
      `isMember(me)`.
    - `String snippet(String markdown, int max)` (strip markdown/wiki-link syntax, first line,
      ellipsis; max 80).

- [ ] **Step 1: Failing tests** in `QuestTreeTest`: `rootsIncludeQuestsWhoseParentIsNotCached`,
  `flattenCollapsedShowsRootsOnly`, `flattenExpandedShowsIndentedChildren`,
  `flattenExpandsAncestorsOfMatches`, `forMapHidesSubquestsUntilTreeSelected`,
  `forMapShowsMemberSubquests`, `ancestorsStopAtUnknown`, `snippetStripsMarkdownAndTruncates`; in
  `QuestTest`: `inheritedEditGrantsCanEdit`, `inheritedOwnerGrantsOwnerPowers`.
- [ ] **Step 2:** `./gradlew :client:test` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `./gradlew :client:test` → PASS.
- [ ] **Step 5:** Commit `feat(client): quest tree model and v4 sync`.

### Task 8: Nested lists and tree-aware map (client UI)

**Files:**
- Modify: `client/.../gui/screen/MainScreen.java`, `client/.../gui/component/QuestEntryComponent.java`
- Test: E2E in Task 10

**Interfaces:**
- Consumes: `QuestTree.flatten`, `QuestTree.forMap`, `ClientSession.expandedTreeIds()` (Task 7).
- Produces:
  - `refreshListContents` builds entries from `QuestTree.flatten(visibleQuests(),
    expandedTreeIds(), searchMatchesOrNull)` on My Quests and Quest Board; Archived stays flat.
  - `QuestEntryComponent(Quest, int indent, int childCount, boolean expanded)`: left margin
    `indent * 10` px, a toggle (id `tree-toggle-<questId>`) drawn as ▸/▾ with the child count when
    `childCount > 0`; clicking the toggle flips the id in `expandedTreeIds()` and refreshes the
    list without selecting the entry.
  - `polygonLayer.setQuests(QuestTree.forMap(activeQuests, selectedId, me, ClientCache::getQuestById))`,
    recomputed on selection change and on `clearSelection`.
  - The map-mode "unplaced" ordering applies to roots only (children follow their parent).
  - Archived tab stays flat; an archived quest with known ancestors shows them as a muted prefix
    line "Hub › Interiors ›" (label id `entry-trail-<questId>`).

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** `./gradlew :client:build :client:compileTestmodJava` → PASS.
- [ ] **Step 3:** Commit `feat(client): nested quest lists and tree-aware map`.

### Task 9: Quest screen tree UI (client UI)

**Files:**
- Modify: `client/.../gui/screen/QuestScreen.java`, `client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml`,
  `.../owo_ui/quest_screen_edit.xml`, lang `en_us.json5`, `fr_fr.json5`
- Test: E2E in Task 10

**Interfaces:**
- Consumes: Task 7.
- Produces (component ids are part of the contract for the E2E):
  - View mode:
    - `trail` row above the title when ancestors are known: each ancestor a clickable label
      `trail-<questId>` opening that quest, separated by " › ".
    - `subquests-section` (only when `supportsSubquests()` and at least one visible child or the
      add button shows): header `gui.disquests.section.subquests`; rows `subquest-row-<id>` with
      title link `subquest-title-<id>`, members line (owner and contributors, or
      `gui.disquests.subquests.nobody`), visibility badge, `QuestTree.snippet`; ▾/▸ toggle
      `subquest-toggle-<id>` for rows with children (collapsed by default, local to the screen).
    - `btn-add-subquest` when `canEdit && depth < 3 && !completed && supportsSubquests()`: opens a
      new QuestScreen in edit mode whose save sends `parentId`.
    - Complete confirm: checkbox `complete-subquests-checkbox`, ticked, label
      `gui.disquests.confirm.complete_subquests` with the count of non-archived descendants in
      cache; hidden when zero. Sends `completeQuest(id, checked)`.
    - Delete confirm: lists child titles and has `delete-subquests-checkbox`, unticked, label
      `gui.disquests.confirm.delete_subquests`; when unticked shows
      `gui.disquests.confirm.delete_promote`. Sends `deleteQuest(id, checked)`.
  - Edit mode (owner powers, existing quest, `supportsSubquests()`):
    - `btn-parent` showing `gui.disquests.edit.parent`; opens a picker listing "None" plus cached
      quests where `canEdit`, not archived, not the quest or its descendants, and
      `depth(candidate) + subtreeHeight <= 3`. Choosing one sends `setParent`; if the candidate's
      visibility is below the quest's, a confirm with
      `gui.disquests.confirm.parent_lowers_visibility` comes first.
    - `cycleVisibility` skips values above `quest.getMaxVisibility()`.

- [ ] **Step 1:** Implement; add lang keys (Global Constraints) to both files.
- [ ] **Step 2:** `./gradlew :client:build :client:compileTestmodJava` → PASS.
- [ ] **Step 3:** Commit `feat(client): subquests section, trail and tree-aware complete and delete`.

### Task 10: E2E journeys

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/SubquestJourney.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/duo/TwoPlayerJourneys.java`

**Interfaces:**
- Consumes: component ids from Tasks 8-9, `PacketSender` (Task 7).

- [ ] **Step 1:** `SubquestJourney` steps:
  1. Create "Hub"; `btn-add-subquest` → create "Interiors"; on Interiors add "Taverne".
  2. On Taverne: `trail-<hubId>` and `trail-<interiorsId>` exist; `btn-add-subquest` absent
     (depth 3). Clicking `trail-<hubId>` opens Hub.
  3. Hub's `subquests-section` lists Interiors; `subquest-toggle-<interiorsId>` reveals Taverne.
  4. Main screen: only Hub listed; `tree-toggle-<hubId>` shows Interiors indented; searching
     "Taverne" shows Hub › Interiors › Taverne expanded.
  5. Hub OPEN, Interiors OPEN; set Hub PRIVATE → Interiors and Taverne PRIVATE in cache.
  6. Complete Hub with the box ticked → all three in Archived; reopen Hub → only Hub active.
  7. Delete Hub with the box unticked → Interiors becomes a root (parentId null, depth 1) and
     Taverne depth 2.
- [ ] **Step 2:** Duo block in `TwoPlayerJourneys`: A creates Hub, adds B as edit contributor,
  creates subquest S under Hub; B sees S in My Quests and can edit it (`btn-edit` active); A removes
  B from Hub → B's cache loses Hub and S.
- [ ] **Step 3:** Push the branch and let CI run `e2e-test.yml` (local E2E crashes while Prism is
  open; do not launch it locally without asking).
- [ ] **Step 4:** Commit `test(e2e): subquest journeys`.
