# Quest Trees, Quest Lines and Roles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quests can contain quests, wait on other quests, and carry three roles per player, with one server function deciding what each player learns about each quest.

**Architecture:** All rules (who sees what, locking, loops, depth, what a delete or completion does) live in a pure package in `common`, with no Bukkit and no Minecraft, used by both the server and the client. The server loads the whole quest graph before and after every change and sends each online player exactly the difference in their own view; the per-handler broadcast rules are removed. The client keeps its edits as drafts (a list of changes) and reuses the same rules to grey out what cannot be done.

**Tech Stack:** Java 25, Gradle 9.9, JUnit 6.1 (parameterized, nested and dynamic tests), Mockito 5.24, sqlite-jdbc, Paper API 26.3, Fabric + owo-ui 0.13.1, the existing E2E harness.

**Spec:** `docs/superpowers/specs/2026-11-09-subquests-design.md` (binding; read it before any task). This plan replaces `docs/superpowers/plans/2026-11-09-subquests.md`, which is deleted.

**Delivery:** two pull requests. PR A = Tasks 1 to 8 (common + server; today's 0.8.0 client keeps working against it). PR B = Tasks 9 to 16 (client + E2E), then release 0.9.0. Branch from `main`, never push code to `main`.

## Global Constraints

- Package namespace `com.disqt.disquests.*`; new rules package is `com.disqt.disquests.common.graph`.
- `ProtocolVersion.V4 = 4`, `CURRENT = V4`. Every new packet field is trailing-optional (`buf.remaining() > 0`); never insert mid-packet.
- Clients below V4 must keep working: they get subquests as plain quests, never receive a quest that is locked for them, and a save from them never clears parent, prerequisites or auto-join.
- `max-quest-depth` in `config.yml`: default 3, 0 = no limit, negative = default. At most 8 prerequisites per quest.
- The server is the authority; the client only hides or greys options.
- Nothing a player cannot see may leak: no title, count, id, tag, pin or name. See "Anti-goals" in the spec.
- UI wording is exactly the spec's: "Prerequisites", "add a subquest...", "not saved", "taken out, not saved", the three "waiting on" sentences, the dialog texts. French strings in `fr_fr.json5` (informal "tu", all accents).
- Commit subjects lowercase after the type (`feat: ...`). Run `./gradlew spotlessApply` before each commit.
- Client unit tests cannot touch `net.minecraft`; logic that needs unit tests stays in Minecraft-free classes.
- Tests: table-driven with `@ParameterizedTest`, grouped with `@Nested`, generated with `@TestFactory`; assert whole records with `assertEquals`; server tests decode the bytes they capture instead of `atLeastOnce()`. No MockBukkit, no jqwik.
- Local verification: `./gradlew build` for unit tests; E2E with `./gradlew :client:runIntegrationTest` (ask the owner first if they may be playing).

## Review Focus

Cases the spec implies that are most likely to bite, each pinned by a test in the task named:

1. A player with no role opens a Closed quest whose parent is Private: they must not see it, and must not get it through pins, tags, wiki-links or a delete notice (Task 3, Task 6).
2. A deletion that would widen an audience (kept subquest under a deleted Private or locked parent) sets the kept quest to Private and tells its owners (Task 4, Task 7).
3. Two saves interleave: a text-only save must not move the tree back; a tree change that became invalid is refused alone (Task 7).
4. A 0.8.0 client re-saves a quest that has a parent and prerequisites: nothing is cleared, and a locked quest never reaches it (Task 1, Task 6).
5. The last owner demotes or removes themself, or two owners demote each other in one save: the quest keeps an owner (Task 7).

## Deviations from the spec, to confirm with the owner

- The per-recipient `depth` number is not sent: the client derives depth from `parentId`, and the server is the only judge of the limit. (Spec lists it under Constraints; "packet shapes" are left to the builder.)
- Deleting a prerequisite that was already completed removes the link instead of leaving a dangling one, so finished follow-ups do not lock again. Only an unfinished deleted prerequisite stays as "a deleted quest". (Found with the mockup; add one line to the spec in Task 16.)

---

### Task 1: Roles, tree fields and protocol V4 (common)

**Files:**
- Create: `common/src/main/java/com/disqt/disquests/common/model/Role.java`, `Privatized.java`, `TreeOp.java`, `LinkChanges.java`, `PersonOp.java`, `Notice.java`
- Modify: `common/.../model/ContributorData.java`, `QuestData.java`, `common/.../ProtocolVersion.java`, `PacketType.java`, `PacketCodec.java`
- Test: `common/src/test/java/com/disqt/disquests/common/PacketCodecV4Test.java`, `common/.../model/QuestDataRolesTest.java`

**Interfaces (produces):**
- `enum Role { READ, WRITE, OWNER; boolean atLeast(Role other) }`
- `record ContributorData(UUID uuid, String name, Role role)` plus `ContributorData(UUID, String, boolean canEdit)` (true = WRITE, false = READ) and `boolean canEdit()` = `role.atLeast(WRITE)`.
- `QuestData` gains trailing components, in this order: `UUID parentId, List<UUID> prerequisites, boolean hiddenPrerequisites, boolean deletedPrerequisite, boolean autoJoin, boolean locked, Privatized privatized`. Older constructors stay and default them (`null, List.of(), false, false, false, false, null`). `ownerUuid`/`ownerName` stay as the display owner; every other person, further owners included, is in `contributors`.
- `QuestData` methods: `Role roleOf(UUID player)` (null when none), `List<UUID> owners()`, `withParent`, `withPrerequisites`, `withPeople(UUID displayOwner, String name, List<ContributorData>)`, `withVisibility`, `withLinksView(...)`.
- `record Privatized(Visibility previous, String deletedTitle, String deletedByName, long at)` (title and name nullable).
- `record TreeOp(Kind kind, UUID quest, UUID target)` with `enum Kind { ATTACH, DETACH, SWAP }`. ATTACH: `quest` goes inside `target`. DETACH: `target` null. SWAP: `target` sits inside `quest` and takes its place.
- `record LinkChanges(Visibility visibility, List<TreeOp> treeOps, List<UUID> addPrerequisites, List<UUID> removePrerequisites, boolean dropHidden, boolean dropDeleted, Boolean autoJoin)`; `LinkChanges.NONE`; nulls mean "unchanged".
- `record PersonOp(Kind kind, UUID player, Role role)` with `enum Kind { SET_ROLE, REMOVE, REMOVE_WITH_MY_SUBQUESTS }`.
- `record Notice(Kind kind, UUID questId, String title, Refusal reason)` with `enum Kind { QUEST_UNLOCKED, QUEST_UNLOCKED_FOLLOWED, SET_PRIVATE, CHANGE_REFUSED }` (`Refusal` comes from Task 2; declare the enum here in `common.graph` so this task compiles).
- Packet types: `UPDATE_PEOPLE(0x0D)`, `APPLY_ROLE_DOWN(0x0E)`, `RESOLVE_PRIVATIZED(0x0F)`, `NOTICE(0x19)`.
- Codec: `writeQuest`/`readQuest` V4 block after the completion block: one role byte per contributor (same order), nullable `parentId`, varint count (max 8) + prerequisite ids, the three booleans `hiddenPrerequisites`, `deletedPrerequisite`, `autoJoin`, boolean `locked`, boolean "has privatized" + (visibility byte, nullable title, nullable name, long). Below V4 the block is not written and contributors carry `canEdit` only.
- `writeSaveQuest(..., LinkChanges)` / `SaveQuestPayload.links()`: trailing block after the polygon; absent block reads as `LinkChanges.NONE`.
- `writeCompleteQuest(UUID, boolean alsoMine)`, `writeDeleteQuest(UUID, boolean alsoMine)` with a trailing-optional boolean (absent = false); readers return `record QuestActionPayload(UUID questId, boolean alsoMySubquests)`.
- `writeUpdatePeople(UUID, List<PersonOp>)`, `writeApplyRoleDown(UUID quest, UUID player)`, `writeResolvePrivatized(UUID quest, boolean restore)`, `writeNotice(Notice)` and their readers.
- `writeHandshake(..., int maxQuestDepth)` as a trailing varint after `playerMarkerEnabled`; `HandshakePayload.maxQuestDepth()` is 3 when absent.

- [ ] **Step 1: Write failing tests.** `PacketCodecV4Test`: a fixed-data quest (fixed UUIDs and timestamps, no `currentTimeMillis`) with parent, 2 prerequisites, three roles and a `Privatized` round-trips at V4 with `assertEquals` on the record; the same quest written at V3 reads back with `parentId == null`, no prerequisites and `canEdit` true for WRITE and OWNER contributors; `@ParameterizedTest @ValueSource(ints = {0,1,2,3,4})` list-packet round trip per version; SAVE_QUEST without the block reads `LinkChanges.NONE`; with the block round-trips every field including `autoJoin` null/true/false; COMPLETE and DELETE without the trailing boolean read `false`; 9 prerequisites and 65 tree ops are rejected; truncated V4 block throws; handshake without the depth reads 3. `QuestDataRolesTest`: `roleOf` for display owner, second owner, Write, Read, stranger; `owners()` lists both owners.
- [ ] **Step 2: Run** `./gradlew :common:test --tests "*PacketCodecV4Test" --tests "*QuestDataRolesTest"`; expected: compilation failure.
- [ ] **Step 3: Implement** the model records, packet types and codec blocks above. Keep every existing overload compiling.
- [ ] **Step 4: Run** `./gradlew :common:test`; expected: PASS, existing `PacketCodecTest` untouched and green.
- [ ] **Step 5: Commit** `feat: roles, tree fields and protocol v4 in the codec`.

### Task 2: Quest graph and link rules (common, pure)

**Files:**
- Create: `common/src/main/java/com/disqt/disquests/common/graph/QuestGraph.java`, `Refusal.java`, `LinkRules.java`
- Test: `common/src/test/java/com/disqt/disquests/common/graph/QuestGraphTest.java`, `LinkRulesTest.java`, `GraphFixtures.java` (test helper: `quest("hub").owner(LEO).open().in("x").after("y")` builder with fixed UUIDs)

**Interfaces (produces):**
- `QuestGraph.of(Collection<QuestData>)`; `QuestData get(UUID)`; `Collection<QuestData> all()`; `List<QuestData> children(UUID)`; `List<QuestData> ancestors(UUID)` nearest first; `List<QuestData> descendants(UUID)`; `int depth(UUID)` (1 = no parent); `int height(UUID)` (1 = leaf); `boolean locked(UUID quest)`; `boolean lockedFor(UUID player, UUID quest)`; `boolean canSee(UUID player, UUID quest)`. Every walk stops after 64 steps (corrupt data must not hang).
- Rules, exactly the spec's: `locked` = not completed and some prerequisite is missing or not completed; `lockedFor` = locked and role below WRITE; `canSee` = has a role, or not Private and (no parent, or parent visible and not `lockedFor`). A missing parent id counts as "no parent".
- `enum Refusal { SELF, LOOP, TOO_DEEP, COMPLETED, TARGET_COMPLETED, LINKED_BY_PREREQUISITE, NEED_OWNER, NEED_WRITE_ON_TARGET, NOT_VISIBLE, TOO_MANY_PREREQUISITES, ALREADY_THERE, LAST_OWNER }`
- `LinkRules.attach(QuestGraph, UUID actor, UUID child, UUID parent, int maxDepth)`, `detach(g, actor, child)`, `swap(g, actor, container, descendant, maxDepth)`, `prerequisite(g, actor, quest, prerequisite)`, each returning `Optional<Refusal>`. `maxDepth` 0 = no limit. Rights: attach needs OWNER on child and WRITE on parent; detach needs OWNER on child, or OWNER on its parent and the child visible to the actor; swap needs OWNER on both and WRITE on the container's parent if any; prerequisite needs OWNER on the quest and the prerequisite visible.

- [ ] **Step 1: Write failing tests.** `QuestGraphTest` with `@Nested` groups `Visibility`, `Locking`, `Shape`. `Visibility` is a `@ParameterizedTest @CsvSource` over the spec's hub example (hub Private; Intérieurs Open, Wagons Closed, Église Private) for viewers non-member, member, hub owner, Église member: expected visible set per row; plus "role holder sees quest under a parent they cannot see", "child of a quest locked for the viewer is not visible, unless they hold a role on it". `Locking`: deleted prerequisite locks; completed quest never locked; WRITE and OWNER never `lockedFor`. `LinkRulesTest` `@ParameterizedTest` rows: attach to self, into own descendant (LOOP), level 4 with limit 3 (TOO_DEEP), same with limit 0 (allowed), completed child, completed target, child waits on an ancestor of the target (LINKED_BY_PREREQUISITE), actor Read on child (NEED_OWNER), actor Read on target (NEED_WRITE_ON_TARGET); prerequisite on self, on ancestor, on descendant, closing a chain A after B after A (LOOP), ninth prerequisite, invisible prerequisite; detach by parent's owner of a Private subquest they cannot see (NOT_VISIBLE); swap moves the descendant to the container's place. One `@TestFactory` over seeds 1..200: build a random forest by applying random accepted `attach`/`prerequisite` calls and assert no walk ever exceeds the node count (no loop was accepted) and depth never exceeds the limit.
- [ ] **Step 2: Run** `./gradlew :common:test --tests "*graph*"`; expected: FAIL (classes missing).
- [ ] **Step 3: Implement** `QuestGraph` (index by id and by parent once in `of`) and `LinkRules`.
- [ ] **Step 4: Run** `./gradlew :common:test`; expected: PASS.
- [ ] **Step 5: Commit** `feat: quest graph with visibility, locking and link rules`.

### Task 3: What a recipient learns (common, pure)

**Files:**
- Create: `common/.../graph/QuestView.java`, `Audience.java`
- Test: `common/src/test/.../graph/QuestViewTest.java`, `AudienceTest.java`

**Interfaces (produces):**
- `QuestView.forRecipient(QuestGraph g, UUID recipient, UUID quest)` returns the `QuestData` the recipient may have, or `null` when they cannot see it. Rules: `parentId` only when the parent is visible to the recipient; `prerequisites` only the visible ones, `hiddenPrerequisites` true when others exist and still exist, `deletedPrerequisite` true when a prerequisite id no longer exists (owners of the quest only, false for everyone else); `privatized` only for owners, with `deletedTitle` and `deletedByName` null for an owner who could not see the deleted quest (the stored record keeps who could: see Task 5); `locked` = `lockedFor(recipient)`. A locked view carries the id, title, visibility and `locked`, the recipient's own contributor entry if they hold a role, and nothing else: content `""`, no tags, no location, no other people, display owner `new UUID(0,0)` with name `""`, no parent content beyond `parentId`, prerequisites as above.
- `Audience.changes(QuestGraph before, QuestGraph after, UUID player)` returns `List<Change>` where `record Change(UUID questId, QuestData view)` and `view == null` means "the player lost it"; only quests whose view differs are listed.

- [ ] **Step 1: Write failing tests.** `QuestViewTest`: `assertAll` on a locked view (every content field empty, own role kept); the three prerequisite cases (all visible, some hidden, none visible) produce the right ids and flag; parent id withheld when the parent is invisible; `privatized` absent for Write and Read; `deletedPrerequisite` false for non-owners. `@TestFactory` over seeds 1..200 on random forests with random roles and visibilities, for every player and quest: (a) a view is non-null exactly when `canSee`; (b) a view never contains an id (parent or prerequisite) of a quest the player cannot see; (c) a locked view has empty content, tags, polygon. `AudienceTest`: making a hub Private yields a loss for the non-member for the hub and its Open subquests, no entry for members; completing a prerequisite yields a changed view (locked false, content present) for a Read player.
- [ ] **Step 2: Run** the two test classes; expected: FAIL.
- [ ] **Step 3: Implement** `QuestView` and `Audience`.
- [ ] **Step 4: Run** `./gradlew :common:test`; expected: PASS.
- [ ] **Step 5: Commit** `feat: per-recipient quest view and audience changes`.

### Task 4: Plans for delete, complete, unlock and people (common, pure)

**Files:**
- Create: `common/.../graph/Plans.java`
- Test: `common/src/test/.../graph/PlansTest.java`

**Interfaces (produces):**
- `Plans.delete(QuestGraph g, UUID actor, UUID quest, boolean alsoMine, Collection<UUID> knownPlayers)` returns `record DeletePlan(Set<UUID> deleted, Map<UUID, Optional<UUID>> reparent, Map<UUID, PrivatizeInfo> privatize, Map<UUID, List<UUID>> prerequisitesToRemove)`; `record PrivatizeInfo(Visibility previous, String deletedTitle, UUID deletedBy, Set<UUID> ownersWhoSawIt)`. Deleted = the quest plus, when `alsoMine`, every descendant the actor owns. Kept children move to the nearest surviving ancestor or to top level. A kept quest is privatized when any player in `knownPlayers`, or a player with no role anywhere, would see it after the move but not before. `prerequisitesToRemove`: for each surviving quest, the deleted prerequisites that were completed (unfinished ones stay as dangling ids).
- `Plans.completeTargets(g, actor, quest, alsoMine)` returns the ids to complete (the quest, plus the actor's unfinished descendants when `alsoMine`).
- `Plans.unlocks(QuestGraph before, QuestGraph after, Collection<UUID> players)` returns `List<Unlock>`; `record Unlock(UUID quest, List<UUID> autoJoin, List<UUID> notify, List<UUID> followers)`: quests locked before and not after (and not completed); `autoJoin` = players holding a role on a prerequisite that at least one owner of the quest can see, who can see the quest and hold no role on it, only when `autoJoin` is set and the quest is Open; `notify` = players who can see it after; `followers` = notified players who hold a role on one of its prerequisites.
- `Plans.subquestCounts(g, viewer, quest, unfinishedOnly)` returns `record Counts(int mine, int others)` over descendants visible to the viewer.
- `Plans.people(QuestData quest, UUID actor, List<PersonOp> ops)` returns `Either`-style `record PeopleResult(List<ContributorData> people, UUID displayOwner, Optional<Refusal> refusal)`: applies the whole list or refuses it all with `LAST_OWNER` when no owner would remain, `NEED_OWNER` when the actor is not an owner.
- `Plans.roleDown(g, actor, quest, player)` returns `Map<UUID, Role>`: for each descendant the actor owns where the player's role is lower than the one they hold on `quest`.

- [ ] **Step 1: Write failing tests** (`@Nested` `Delete`, `Complete`, `Unlock`, `People`). Delete: unticked moves every child up one level; ticked deletes only the actor's descendants and re-homes another player's quest two levels down to the nearest survivor; Open child of a deleted Private parent is privatized with `previous = OPEN`; Open child of a deleted Open top-level parent is not; child of a deleted quest that was locked for a Read player is privatized; `ownersWhoSawIt` excludes an owner without a role on the deleted quest; a completed deleted prerequisite is removed, an unfinished one stays. `@TestFactory` seeds 1..200: after applying any delete plan to a random forest, no player sees a kept quest they did not see before. Unlock: auto-join only when Open and ticked; players of a prerequisite no owner can see are skipped; a quest that unlocks because its prerequisite was removed is also reported. People: demoting the only owner is refused; two owners swapping roles in one list is accepted; removing self as last owner refused; `REMOVE_WITH_MY_SUBQUESTS` is reported per quest by the caller (this function handles one quest).
- [ ] **Step 2: Run** `./gradlew :common:test --tests "*PlansTest"`; expected: FAIL.
- [ ] **Step 3: Implement** `Plans`.
- [ ] **Step 4: Run** `./gradlew :common:test`; expected: PASS.
- [ ] **Step 5: Commit** `feat: delete, complete, unlock and people plans`.

### Task 5: Storage and migration (server)

**Files:**
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/DataManager.java`, `Config.java`, `server/src/main/resources/config.yml`
- Test: `server/src/test/java/com/disqt/disquests/server/papermc/DataManagerTreeTest.java`, `MigrationFrom080Test.java`, fixture `server/src/test/resources/fixtures/disquests-0.8.0.sql`

**Interfaces (produces):**
- Schema, through the existing idempotent pattern (`PRAGMA table_info` checks, one transaction per migration): table `quest_people(quest_id, player_uuid, role TEXT, added_at INTEGER, PRIMARY KEY(quest_id, player_uuid), FK quests ON DELETE CASCADE)`; table `quest_links(from_id, to_id, kind TEXT, PRIMARY KEY(from_id, to_id, kind))` with no foreign key on `to_id` (a deleted prerequisite must dangle); `quests` gains `parent_id TEXT`, `auto_join INTEGER NOT NULL DEFAULT 0`, `priv_previous TEXT`, `priv_title TEXT`, `priv_by TEXT`, `priv_at INTEGER`, `priv_saw TEXT` (comma-separated owner uuids).
- Migration: every quest's `owner_uuid` becomes an OWNER row (`added_at` 0), `contributors` rows become WRITE (`can_edit = 1`) or READ with `added_at` 1; the `contributors` table is kept and no longer written. `quests.owner_uuid` stays as the display owner and is updated to the earliest remaining owner when its player stops being one.
- `List<QuestData> getAllQuests()` (full truth, all fields, including people, parent, prerequisites, `Privatized` built for storage with title and name); `QuestGraph loadGraph()`.
- `void setParent(UUID quest, UUID parentOrNull)`, `void setPrerequisites(UUID quest, List<UUID>)`, `void setAutoJoin(UUID, boolean)`, `void setPeople(UUID quest, List<ContributorData> people, UUID displayOwner)`, `void setRole(UUID quest, UUID player, Role)`, `void removePerson(UUID quest, UUID player)`, `void setPrivatized(UUID quest, PrivatizeInfo, long at)`, `Set<UUID> privatizedSeenBy(UUID quest)`, `void clearPrivatized(UUID quest)`, `void applyDeletePlan(DeletePlan, long at)` (one transaction), `void completeQuests(Collection<UUID>, UUID by, long at)`.
- Join requests and pending counts are per owner: `getPendingRequestsForOwner`, `getPendingCountByQuest`, `getPendingRequestCount` match any OWNER row.
- `Config.getMaxQuestDepth()`: `max-quest-depth`, default 3, negative = 3; documented in `config.yml`.
- Existing `getQuest`, `getQuestsForPlayer`, `addContributor(…, boolean)`, `isContributor`, `updateContributor` keep working on top of `quest_people`.

- [ ] **Step 1: Write failing tests.** `MigrationFrom080Test`: load `disquests-0.8.0.sql` (schema dumped from the 0.8.0 `createTables` plus rows: a quest with an owner, one can-edit and one view-only contributor, a pin, a pending request, a completed quest) into a `@TempDir` database, run `initialize()`, assert roles OWNER/WRITE/READ, pins and requests intact, completion intact, and a second `initialize()` changes nothing. `DataManagerTreeTest`: parent and prerequisites round-trip through `getAllQuests`; deleting a prerequisite leaves the dangling id; `applyDeletePlan` deletes, re-homes, privatizes and removes completed prerequisites atomically; display owner moves to the earliest remaining owner; pending count reaches a second owner; `max-quest-depth` parsing with `@CsvSource({"3,3","0,0","-1,3","7,7"})`.
- [ ] **Step 2: Run** `./gradlew :server:test --tests "*DataManagerTreeTest" --tests "*MigrationFrom080Test"`; expected: FAIL.
- [ ] **Step 3: Implement** schema, migration and methods. Batch-load people and links like the existing `getContributorsBatch`.
- [ ] **Step 4: Run** `./gradlew :server:test`; expected: PASS, `DataManagerTest` still green.
- [ ] **Step 5: Commit** `feat: store roles, quest tree and prerequisites`.

### Task 6: One delivery path (server)

**Files:**
- Create: `server/.../papermc/Outbox.java`, `BukkitOutbox.java`, `Delivery.java`
- Modify: `server/.../papermc/ServerPacketHandler.java`, `WikiLinkResolver.java`, `DisquestsPlugin.java`
- Test: `server/src/test/.../papermc/DeliveryTest.java`, `FakeOutbox.java` (test helper), update `ServerPacketHandlerTest.java` to use `FakeOutbox` instead of `mockStatic(Bukkit.class)`

**Interfaces (produces):**
- `interface Outbox { void send(UUID player, byte[] data); Collection<UUID> onlineModPlayers(); String name(UUID player); }`; `BukkitOutbox` wraps the current `sendPluginMessage`/channel check. `ServerPacketHandler(DisquestsPlugin, DataManager, Config, Outbox)`.
- `FakeOutbox`: records `(player, PacketType, decoded payload)`; `List<QuestData> updatesFor(UUID)`, `List<UUID> deletesFor(UUID)`, `List<Notice> noticesFor(UUID)`, `clear()`.
- `Delivery.deliver(QuestGraph before, QuestGraph after)`: for each online mod player, `Audience.changes`; a non-null view is sent as `UPDATE_QUEST` at the player's protocol version with wiki-links resolved for them, a null view as `DELETE_QUEST_S2C`. Version rules in one place: below V4 a view with `locked` is sent as a delete (and as an update once unlocked); below V3 completed quests are deletes, as today.
- `Delivery.sync(UUID player, int protocolVersion)`: builds SYNC_MY_QUESTS (views of quests where the player holds a role) and SYNC_SERVER_QUESTS (other visible, not completed, not Private) from `QuestView`; replaces the two SQL-scoped queries in `handleRequestSync`.
- Pins: handshake sends only pinned ids the player can see and that are not locked for them; `handlePinQuest` uses `canSee && !lockedFor`.
- Tags: `SYNC_TAGS` per recipient = predefined tags plus tags of quests whose view is not locked.
- Wiki-links: `WikiLinkResolver` takes the graph; a target the recipient cannot see or sees locked resolves to the existing "Private Quest" placeholder, id kept.

- [ ] **Step 1: Write failing tests.** `DeliveryTest` (real `DataManager` on `@TempDir`, `FakeOutbox`, three players at chosen protocol versions): the spec's hub example synced to a non-member (none of the four), a member (three), the hub owner (three, not Église); setting the hub Private sends deletes for the hub and its subquests to the non-member only; a title change reaches every viewer, not only people on the quest; a quest locked for a V3 player is absent from their sync and arrives as an update when unlocked; handshake pinned ids exclude a quest the player lost; decoded `SYNC_TAGS` for a non-member excludes the Private hub's tags; a wiki-link to a locked quest resolves to the placeholder. Convert the existing handler tests to `FakeOutbox` and replace `atLeast`/`atLeastOnce` assertions with decoded expectations.
- [ ] **Step 2: Run** `./gradlew :server:test --tests "*DeliveryTest"`; expected: FAIL.
- [ ] **Step 3: Implement** `Outbox`, `Delivery`, and route `handleRequestSync`, handshake pins, tags and wiki-links through them. Other handlers keep their logic for now but call `Delivery.deliver(before, after)` instead of `broadcastQuestUpdate`, `broadcastToContributors`, `broadcastToModPlayers`, `notifyCompletionChange`; delete those helpers.
- [ ] **Step 4: Run** `./gradlew :server:test`; expected: PASS.
- [ ] **Step 5: Commit** `feat: deliver quests through one per-recipient path`.

### Task 7: Handlers for roles, links, completion and deletion (server)

**Files:**
- Modify: `server/.../papermc/ServerPacketHandler.java`
- Test: `server/src/test/.../papermc/RolesHandlerTest.java`, `LinksHandlerTest.java`, `CompleteDeleteHandlerTest.java`

**Interfaces (consumes):** Tasks 1 to 6. **Produces:** the final C2S behaviour.

Every handler follows one shape: `before = loadGraph()`; check with `LinkRules`/`Plans`; write through `DataManager`; `after = loadGraph()`; apply `Plans.unlocks(before, after)` (auto-join rows, then reload); `Delivery.deliver(before, after)`; send `Notice`s.

- Save (`SAVE_QUEST`): WRITE needed for text, location, tags. `links` applied only when present: visibility, prerequisites and auto-join need OWNER; each `TreeOp` is checked alone against the graph as it is at that moment and applied or refused with a `CHANGE_REFUSED` notice naming the quest only if the sender can see it; the rest still applies. A save without the block changes no link. A new quest is created Private (or with the block's visibility) with the sender as only owner, then its tree ops run.
- Roles: `UPDATE_PEOPLE` through `Plans.people`; `REMOVE_WITH_MY_SUBQUESTS` also removes the player from the sender's own descendants (skipping any where they are the last owner). Legacy `UPDATE_CONTRIBUTORS` maps ADD/UPDATE `canEdit` to WRITE/READ and REMOVE to remove, OWNER only. `UPDATE_VISIBILITY` stays for old clients, OWNER only; setting a non-Private visibility clears `privatized`. `APPLY_ROLE_DOWN` through `Plans.roleDown`. Join gives READ; join and request are refused when the quest is not visible or is locked for the player; a request notifies every online owner; any owner may answer. Leave: any role, refused for the last owner.
- Complete and reopen: OWNER only; `alsoMySubquests` through `Plans.completeTargets`; completing drops pins and requests of each completed quest as today. Reopen relocks follow-ups by derivation; nothing else to write.
- Delete: OWNER only; `Plans.delete` with `knownPlayers` = every uuid in `player_names`; `applyDeletePlan`; `SET_PRIVATE` notice to online owners of privatized quests other than the sender. `RESOLVE_PRIVATIZED`: OWNER only; restore sets the previous visibility; both clear the remembered state.
- Unlock notices: `QUEST_UNLOCKED_FOLLOWED` to followers, `QUEST_UNLOCKED` to the other notified players; only V4 clients get notices.
- A completed quest's links are frozen: tree ops and prerequisite changes on or of it are refused with `COMPLETED`.

- [ ] **Step 1: Write failing tests.** `RolesHandlerTest`: `@ParameterizedTest` over `Role` × action (edit text, complete, delete, change visibility, manage people, add prerequisite, attach own quest) against the spec's role table; last owner cannot leave, be demoted or removed; two owners swap in one packet; join gives READ; a request reaches two owners and the first answer settles it; a V3 can-edit contributor can no longer complete. `LinksHandlerTest`: text-only save leaves parent and prerequisites untouched after another player moved the quest; one refused tree op out of three sends one `CHANGE_REFUSED` and applies the other two; swap; new quest with an ATTACH in the same save; a V3 client's save of a quest with parent and prerequisites clears nothing; frozen links on a completed quest; depth limit from config with 0 = unlimited. `CompleteDeleteHandlerTest`: complete with and without `alsoMySubquests` when another player owns a subquest (theirs stays active); unlock sends the follow-up's full view to a Read player plus the right notice, auto-join adds READ only when Open and ticked; reopen relocks (the Read player's view is locked again, roles and pins kept); delete unticked and ticked; delete that would widen an audience privatizes and notifies the other owner, who then restores it; deleting an unfinished prerequisite leaves the follow-up locked with `deletedPrerequisite` for its owner only; deleting a completed one does not relock.
- [ ] **Step 2: Run** `./gradlew :server:test --tests "*HandlerTest"`; expected: FAIL.
- [ ] **Step 3: Implement** the handlers.
- [ ] **Step 4: Run** `./gradlew build`; expected: PASS (all modules, spotless, Error Prone).
- [ ] **Step 5: Commit** `feat: roles, links, completion and deletion rules on the server`.

### Task 8: Old-client journey and PR A

**Files:**
- Modify: `client/src/testmod/.../journeys/duo/TwoPlayerJourneys.java` only if a step relied on can-edit contributors completing quests or on save broadcast scoping; `.claude/CLAUDE.md` (Networking Protocol: V4 block, new packets, delivery rule; Key Files: `common/graph`, `Delivery`, `Outbox`)

- [ ] **Step 1: Run the full E2E suite** with the unchanged 0.8.0-level client code against the new server: `./gradlew :client:runIntegrationTest`. Expected: PASS; fix server regressions, or update a journey step only where the spec changed the rule (completion is owner-only).
- [ ] **Step 2: Update** `.claude/CLAUDE.md`.
- [ ] **Step 3: Commit** `docs: protocol v4 and delivery notes`, push, open PR A titled `feat: quest trees, prerequisites and roles on the server (protocol v4)`. Suggest `/simplify` to the owner before opening it.

### Task 9: Client model, cache and senders (client, Minecraft-free where tested)

**Files:**
- Modify: `client/.../data/Quest.java`, `Contributor.java`, `ClientCache.java`, `ClientSession.java`, `network/PacketSender.java`, `network/ClientPacketHandler.java`
- Create: `client/.../data/QuestGraphs.java`, `client/.../data/NewMarks.java`
- Test: `client/src/test/.../data/QuestTest.java` (extend), `NewMarksTest.java`

**Interfaces (produces):**
- `Quest`: `Role roleOf(UUID)`, `UUID getParentId()`, `List<UUID> getPrerequisites()`, `boolean hasHiddenPrerequisites()`, `boolean hasDeletedPrerequisite()`, `boolean isAutoJoin()`, `boolean isLocked()`, `Privatized getPrivatized()`, `QuestData toData()`; `canEdit` = WRITE or OWNER; `isOwner` = role OWNER.
- `QuestGraphs.current()` returns a `QuestGraph` over everything in `ClientCache` (rebuilt when `ClientCache.getVersion()` changes).
- `ClientSession.getMaxQuestDepth()`, `supportsTrees()` = server protocol `>= V4`. Against an older server every new control is absent.
- `NewMarks` (Minecraft-free): `mark(UUID)`, `boolean isNew(UUID)`, `opened(UUID)`, `boardOpened()` (a mark disappears after the third call), `clear()`; held by `ClientSession`, cleared on `leaveServer`.
- `PacketSender`: `saveQuest(..., LinkChanges)`, `completeQuest(UUID, boolean)`, `deleteQuest(UUID, boolean)`, `updatePeople`, `applyRoleDown`, `resolvePrivatized`.
- `ClientPacketHandler` handles `NOTICE`: toast text from lang keys; `QUEST_UNLOCKED_FOLLOWED` also calls `NewMarks.mark`.

- [ ] **Step 1: Write failing tests**: `QuestTest` role and lock accessors from a V4 `QuestData`; `NewMarksTest` (gone after open, gone after three board opens, not before).
- [ ] **Step 2: Run** `./gradlew :client:test`; expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :client:test :client:compileTestmodJava`; expected: PASS.
- [ ] **Step 5: Commit** `feat: client model for roles, trees and prerequisites`.

### Task 10: Client logic for lists, trees, drafts and suggestions (client, Minecraft-free)

**Files:**
- Create: `client/.../gui/tree/QuestTreeRows.java`, `TreeDraft.java`, `Suggestions.java`, `WaitingText.java`, `PeopleDraft.java`
- Test: one test class each under `client/src/test/.../gui/tree/`

**Interfaces (produces):**
- `QuestTreeRows.forTab(QuestGraph, UUID me, Tab, String search, Set<UUID> expanded)` returns `List<Row>`; `record Row(UUID id, int depth, int childCount, boolean expanded, boolean locked, boolean joined)`. My Quests: quests I hold a role on, plus their visible active descendants, nested; Quest Board: visible, active, not Private, nested; a quest whose parent is not listed shows at top level; collapsed by default; a search match shows with its ancestors expanded; Archived unchanged (flat). Indentation stops at 6 levels.
- `QuestTreeRows.whereThisSits(QuestGraph, UUID me, UUID quest, Set<UUID> expanded)`: visible ancestors, siblings, the quest, its subquests; empty when the quest has no visible parent and no visible children. `QuestTreeRows.wholeTree(graph, me, quest)` for the edit pane (everything under the top visible ancestor, all expanded).
- `TreeDraft` over a base `QuestGraph`: `Optional<Refusal> move(UUID quest, UUID target, UUID me, int maxDepth)` (records ATTACH, or SWAP when the target is inside the quest), `Optional<Refusal> detach(UUID quest, UUID me)`, `QuestGraph effective()`, `boolean isChanged(UUID)`, `List<UUID> takenOut(UUID treeRoot)`, `List<TreeOp> ops()` (one net op per moved quest, none for a quest moved back), `boolean isEmpty()`.
- `Suggestions.subquests(QuestGraph effective, UUID me, UUID questOrNull, String typed, int maxDepth)` and `Suggestions.prerequisites(...)` return `List<Suggestion>`; `record Suggestion(UUID id, String title, Refusal reason, String note)`: empty `typed` lists only addable quests; otherwise every visible quest, addable first then by title, never truncated.
- `WaitingText.of(Quest, QuestGraph)` returns `record Waiting(List<UUID> visible, boolean somethingElse, boolean nothingVisible)` driving the three spec sentences.
- `PeopleDraft(Quest)`: fixed order (owners first, sorted once), `setRole`, `remove(UUID, boolean withMySubquests)`, `undoRemove`, `add`, `isDirty(UUID)`, `boolean keepsAnOwner()`, `boolean givesUpOwnOwner(UUID me)`, `List<PersonOp> ops()`.

- [ ] **Step 1: Write failing tests**: nesting and "parent not listed means top level" (a subquest left active under an archived parent); collapsed by default; search expands ancestors; `TreeDraft`: move, move back yields no op, swap, detach of the edited quest, refused move leaves the draft unchanged, `takenOut`; `Suggestions`: empty text hides non-addable, typed text shows "need Owner" with the held role as `note`, loop and depth reasons, list not cut at 12; `WaitingText` three cases; `PeopleDraft`: order stable after a role change, added person last, ops are net changes.
- [ ] **Step 2: Run** `./gradlew :client:test --tests "*gui.tree*"`; expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :client:test`; expected: PASS.
- [ ] **Step 5: Commit** `feat: client logic for nested lists, tree drafts and suggestions`.

### Task 11: Shared UI pieces (client, owo-ui)

**Files:**
- Create: `client/.../gui/component/SuggestionDropdown.java` (replaces `AutocompleteDropdown` and `TagAutocompleteDropdown`), `QuestSearchField.java`, `TreeRowComponent.java`, `client/.../gui/screen/PaneSplit.java`
- Modify: `client/.../gui/screen/DisquestsBaseScreen.java`, `TagPickerScreen.java`, `MainScreen.java` (use `PaneSplit`), `templates.xml`, lang files

**Interfaces (produces):**
- `SuggestionDropdown<T>`: a `FlowLayout` inside a vertical scroll, mounted on the screen root with absolute positioning (not inside `OverlayContainer`, which centres its child); rows clickable or greyed with the reason in brackets; arrow keys and Enter; updates rows in place.
- `QuestSearchField`: text field + `SuggestionDropdown<Suggestion>`; opens on focus with the empty-text list; `onPick(Consumer<UUID>)`; ids `add-subquest-field`, `prerequisite-field`, rows `suggestion-<index>`.
- `TreeRowComponent extends BaseUIComponent`: custom-drawn leaf (title, lock or tick, count, "not saved" marker, remove hit zone), `canFocus(MOUSE_CLICK)` true; in edit mode it handles `onMouseDrag`: past 4 px it reports the absolute mouse position (`x() + click.x()`) to a `DragTarget` callback that hit-tests sibling rows, draws the highlight, and commits in `onMouseUp`. Component id `tree-row-<questId>`.
- `PaneSplit`: the clamp and persist wiring now in `MainScreen` (around the `SplitterComponent`), reusable with a config key; new config value `questPaneWidth`.
- `showConfirmOverlay(String text, String extraLine, String checkboxLabel, Consumer<Boolean> onYes)`: adds an optional muted line and a `UIComponents.smallCheckbox`; ids `confirm-checkbox`, `confirm-extra`.

- [ ] **Step 1: Implement** the components; port `TagPickerScreen` to `SuggestionDropdown` and delete the two old dropdown classes; switch `MainScreen` to `PaneSplit`.
- [ ] **Step 2: Run** `./gradlew :client:build :client:compileTestmodJava`; fix testmod references to changed component types (search testmod for the ids first).
- [ ] **Step 3: Run** `./gradlew :client:runSoloTests -PtestFilter=TagJourney` and `-PtestFilter=MapBoardJourney`; expected: PASS (tag picker and divider unchanged for the player).
- [ ] **Step 4: Dispatch the `owo-ui-reviewer` agent** on the new components and fix what it finds.
- [ ] **Step 5: Commit** `feat: shared suggestion dropdown, tree row and pane split`.

### Task 12: Lists and map (client UI)

**Files:**
- Modify: `client/.../gui/screen/MainScreen.java`, `gui/component/QuestEntryComponent.java`, `gui/map/QuestPolygonLayer.java` (or the code choosing which quests it draws), lang files
- Test: `client/src/test/.../gui/map/MapQuestFilterTest.java`; create `client/.../gui/map/MapQuestFilter.java` (Minecraft-free)

- `MapQuestFilter.visible(QuestGraph, UUID me, UUID selected, Tab)`: top-level quests' zones; plus the whole tree of the selected quest; plus subquests I hold a role on; never a quest locked for me.
- Lists: rows from `QuestTreeRows.forTab`; each row is `[toggle][QuestEntryComponent]` with a left margin per depth; toggle id `toggle-<questId>`; count shown; lock icon and grey for locked; "NEW" for `NewMarks`; a warning mark for an owner when `privatized != null` or `hasDeletedPrerequisite()`. Entering the main screen calls `NewMarks.boardOpened()`. Join/Request buttons disabled for a locked selection.

- [ ] **Step 1: Write failing test** `MapQuestFilterTest` (top-level only by default; selected tree shown; role-held subquest shown; locked hidden).
- [ ] **Step 2: Run** `./gradlew :client:test --tests "*MapQuestFilterTest"`; expected: FAIL.
- [ ] **Step 3: Implement** the filter and the list changes.
- [ ] **Step 4: Run** `./gradlew :client:build`; `./gradlew :client:runSoloTests -PtestFilter=SearchAndFilterJourney`; expected: PASS.
- [ ] **Step 5: Commit** `feat: nested quest lists and tree-aware map`.

### Task 13: Reading a quest (client UI)

**Files:**
- Modify: `client/.../gui/screen/QuestScreen.java`, `quest_screen_view.xml`, lang files

- Two panes with `PaneSplit`: left `where-this-sits` (rows from `QuestTreeRows.whereThisSits`, each opens that quest, ticks and locks not clickable, pane absent when empty); right the quest as today.
- Role label next to the badge; contributors list shows roles; "Manage people" for owners.
- Write and Owner see a "Prerequisites" section (`prerequisites-section`) with tick state.
- Locked view: title, lock, and the `WaitingText` sentences (`waiting-text`); only Leave when the player holds a role.
- Complete and Delete use the new confirm overlay with `Plans.subquestCounts` (checkbox unticked, the "N others belong to other players" line, never names). Complete is owner-only.
- Privatized banner for owners (`privatized-banner`, `btn-restore-visibility`, `btn-keep-private`); banner for a deleted prerequisite.
- Opening a quest calls `NewMarks.opened`.

- [ ] **Step 1: Implement** the view.
- [ ] **Step 2: Run** `./gradlew :client:build`; `./gradlew :client:runSoloTests -PtestFilter=QuestCompletionJourney` and `-PtestFilter=QuestLifecycleJourney`; expected: PASS (update steps only where the confirm dialog gained elements).
- [ ] **Step 3: Commit** `feat: two-pane quest view with locks, roles and new dialogs`.

### Task 14: Editing a quest (client UI)

**Files:**
- Modify: `client/.../gui/screen/QuestScreen.java`, `quest_screen_edit.xml`, lang files

- Left pane (Write and Owner): rows from `QuestTreeRows.wholeTree` over `TreeDraft.effective()`, built from `TreeRowComponent` in edit mode: drag onto a row = `TreeDraft.move`; the dashed `tree-drop-out` area and the row's remove button = `TreeDraft.detach`; changed rows show "not saved"; taken-out quests listed below as "taken out, not saved"; the "add a subquest..." `QuestSearchField` directly under the edited quest's line; rows do not navigate; a refused move shows a toast with the localized `Refusal`. No confirmation pop-ups here. A new quest shows its own line and the field.
- Right pane: the form as today, then for owners the three-button visibility setting (`vis-private`, `vis-closed`, `vis-open`), the "Prerequisites" `QuestSearchField` with chips (`TagChipComponent` style; one chip "something you don't have access to" and one "a deleted quest", each with a remove button), and the auto-join checkbox (`auto-join`), shown when there are prerequisites, disabled unless Open.
- Save sends text fields plus `LinkChanges` built from the drafts (`TreeDraft.ops()`, added and removed prerequisites, flags, visibility and auto-join only when changed). Cancel and back drop everything. Dirty tracking (both entry points named in CLAUDE.md "Wiki-links") includes the drafts.
- When the containing quest is not visible: one read-only line with `btn-detach-hidden-parent`.

- [ ] **Step 1: Implement** the editor.
- [ ] **Step 2: Run** `./gradlew :client:build`; `./gradlew :client:runSoloTests -PtestFilter=DirtyDetectionJourney` and `-PtestFilter=NavigationJourney`; expected: PASS.
- [ ] **Step 3: Dispatch the `owo-ui-reviewer` agent** on `QuestScreen` and the XML; fix findings.
- [ ] **Step 4: Commit** `feat: quest editor with tree pane and prerequisites`.

### Task 15: People screen (client UI)

**Files:**
- Modify: `client/.../gui/screen/ContributorScreen.java`, `contributor_screen.xml`, `templates.xml` (a `person-row` template), lang files

- Draft from `PeopleDraft`: three role buttons per row (current one inactive), Remove (asks "this quest only / this quest and my N subquests" only when the person holds a role on subquests the player owns), Undo on a removed row, "not saved" markers, Add row; rows updated in place so the order stays fixed.
- Save (`btn-save-people`) disabled without changes; refused with a toast when no owner would remain; asks first when the player gives up their own Owner role (spec wording); sends `UPDATE_PEOPLE`. Cancel and back discard.
- "Apply to subquests" (`btn-apply-down-<uuid>`) per row when the player owns subquests, disabled on a row with unsaved changes, with the spec's confirmation; acts at once. Join requests keep acting at once.

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Run** `./gradlew :client:build`; `./gradlew :client:runDuoTests`; expected: PASS after updating duo steps that used the old can-edit toggle.
- [ ] **Step 3: Commit** `feat: people screen with three roles and save`.

### Task 16: Journeys, docs and release prep

**Files:**
- Create: `client/src/testmod/.../journeys/solo/QuestTreeJourney.java`, `journeys/duo/RolesAndTreesJourney.java`, `journeys/duo/QuestLineJourney.java` (split out of the 577-line duo class style; use `waitForQuestCondition`, no fixed waits)
- Modify: `.claude/CLAUDE.md`, `docs/references/owo-ui.md` (0.13 names), `docs/superpowers/specs/2026-11-09-subquests-design.md` (the two deviations above), `gradle.properties` (`mod_version=0.9.0`), `fabric.mod.json` if needed; delete `docs/superpowers/plans/2026-11-09-subquests.md`

Journeys, at least (from the spec's "Left to the builder"):
- `QuestTreeJourney`: three quests created in the editor and arranged into three levels from the edit screen (field, drag, swap); Cancel discards a drag; a move back shows no "not saved"; detach from each side; nested list collapsed by default and search expanding ancestors.
- `RolesAndTreesJourney` (A owner, B other): the hub example seen by B as non-member then member; B with Write cannot complete or change prerequisites; last owner cannot leave; apply to subquests; remove with subquests; complete and delete with and without the checkbox when B owns a subquest; the deletion that privatizes B's quest, B restores it; "you need Owner on it, you have Read" greyed suggestion.
- `QuestLineJourney`: an Open locked chapter (B sees title, lock and the listed prerequisite), a hidden prerequisite (the "something else" wording), a Private chapter revealed by hand; A completes chapter 1; B gets the toast, the NEW mark and is auto-joined; A reopens and it relocks for B.

- [ ] **Step 1: Write the journeys.**
- [ ] **Step 2: Run** `./gradlew :client:runIntegrationTest`; expected: PASS, whole suite.
- [ ] **Step 3: Update docs**, bump the version, delete the old plan.
- [ ] **Step 4: Commit**, push, suggest `/simplify`, open PR B titled `feat: quest trees, quest lines and roles (0.9.0)`. Deploying the plugin and tagging the release wait for the owner's go-ahead (server restarts only when nobody is online).
