# Quest Completion and Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let owners and editors mark a quest completed, move it to an Archived tab for its participants, and reopen it.

**Architecture:** Completion is three trailing fields on `QuestData` (protocol V3), two new C2S packets, two SQLite columns, a server-side lock on archived quests, and a third home tab plus Complete/Reopen buttons on the client. Clients below V3 never receive completed quests.

**Tech Stack:** Java 25, Paper 26.3 plugin (SQLite), Fabric client (Minecraft 26.3, owo-lib 0.13.1+26.3), JUnit 5 + Mockito, Fabric client gametest E2E harness.

**Spec:** `docs/superpowers/specs/2026-11-08-quest-completion-design.md`

**Branch:** `feat/quest-completion`, created from `main` after PR #95 is merged (#95 changes `MainScreen` and `main_screen.xml`, which Task 5 also edits).

## Global Constraints

- Completion is NOT a `Visibility` value; it is `long completedAt` (epoch millis, `0` = not completed), `UUID completedBy` (nullable), `String completedByName` (nullable). Archived ⇔ `completedAt != 0`.
- Protocol: `ProtocolVersion.V3 = 3`, `CURRENT = V3`. New quest fields are written/read only at version ≥ V3, after the polygon block, in this order: `long completedAt`, nullable UUID `completedBy`, nullable string `completedByName`. Never insert mid-packet.
- New packets: `COMPLETE_QUEST` = `0x0B`, `REOPEN_QUEST` = `0x0C`, both C2S with a single `UUID questId`.
- Clients below V3 never receive completed quests (sync or updates); on completion they get `DELETE_QUEST_S2C`.
- Complete and Reopen allowed to the owner and contributors with `canEdit` (current state). Everything else is refused for a completed quest except the owner's `DELETE_QUEST`.
- Copy (EN / FR, French informal "tu"):
  - tab `Archived` / `Archivées`
  - button `Complete` / `Terminer`, `Reopen` / `Rouvrir`
  - confirm complete: `Mark this quest as completed? It moves to the Archived tab for everyone who worked on it.` / `Marquer cette quête comme terminée ? Elle passera dans l'onglet Archivées pour tous ceux qui y ont participé.`
  - confirm reopen: `Reopen this quest? It goes back to the active lists, and its completion time will be replaced if it is completed again.` / `Rouvrir cette quête ? Elle revient dans les listes actives, et sa date d'achèvement sera remplacée si elle est terminée à nouveau.`
  - label `Completed on %s by %s` / `Terminée le %s par %s`; without a name `Completed on %s` / `Terminée le %s`
- Formatting: `./gradlew spotlessApply` before every commit. Unit tests: `./gradlew build`. Testmod compile: `./gradlew :client:compileTestmodJava`.

## Review Focus

1. A V2 (0.7.x) client online while a V3 client completes an Open quest: the V2 client must see the quest disappear, not crash or keep it. (Task 3: `complete_sendsDeleteToV2Viewers`.)
2. A `SAVE_QUEST` arriving for an already-completed quest (stale editor open in another client): must be ignored and must not clear the completion. (Task 3: `saveOnCompletedQuest_isIgnored`.)
3. Reopening a quest whose owner left the quest's contributors list since completion, or an editor demoted to view-only: reopen refused. (Task 3: `reopen_byDemotedContributor_isRefused`.)
4. A completed quest that was pinned on someone's HUD: the pin disappears and does not come back after reopen. (Task 2: `complete_removesPins`; Task 4: HUD drops completed quests.)
5. A wiki-link `[[Title]]` in an active quest pointing at an archived quest still resolves to its UUID. (Task 3: `wikiLink_toCompletedQuest_stillResolves`.)

---

### Task 1: Common model and V3 codec

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/model/QuestData.java`
- Modify: `common/src/main/java/com/disqt/disquests/common/ProtocolVersion.java`
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketType.java`
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketCodec.java`
- Test: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`

**Interfaces:**
- Produces: `QuestData` gains trailing components `long completedAt, UUID completedBy, String completedByName`; existing constructors (13- and 14-arg) delegate with `0L, null, null`; `boolean isCompleted()`; `QuestData withCompletion(long completedAt, UUID completedBy, String completedByName)`; existing `with*` helpers carry the three fields through.
- Produces: `ProtocolVersion.V3 = 3`, `CURRENT = V3`; `PacketType.COMPLETE_QUEST (0x0B)`, `REOPEN_QUEST (0x0C)`.
- Produces: `PacketCodec.writeCompleteQuest(UUID)`, `readCompleteQuest(ByteBufReader) -> UUID`, `writeReopenQuest(UUID)`, `readReopenQuest(ByteBufReader) -> UUID`.

- [ ] **Step 1: Write the failing tests** in `PacketCodecTest`:
  - `quest_v3_roundTrip_withCompletion`: quest with `completedAt=1_700_000_000_000L`, `completedBy=UUID`, `completedByName="Leo"` → `readQuest(buf, V3)` equals all three, `remaining()==0`.
  - `quest_v3_roundTrip_notCompleted`: `completedAt=0`, nulls → read back `0`, null, null; `isCompleted()` false.
  - `quest_v2_write_omitsCompletion`: write with V2, read with V2 → `completedAt==0`, `remaining()==0`.
  - `completeQuest_roundTrip` and `reopenQuest_roundTrip`: type byte `0x0B`/`0x0C`, id round-trips.
- [ ] **Step 2:** `./gradlew :common:test --tests "*PacketCodecTest"` → FAIL (compile).
- [ ] **Step 3:** Implement the model, constants and codec per Global Constraints (reuse the nullable UUID/string helpers).
- [ ] **Step 4:** `./gradlew :common:test` → PASS; `./gradlew build` → PASS (server/client still compile through the delegating constructors).
- [ ] **Step 5:** Commit `feat: quest completion fields and protocol V3`.

### Task 2: SQLite completion columns and queries

**Files:**
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/DataManager.java`
- Test: `server/src/test/java/com/disqt/disquests/server/papermc/DataManagerTest.java`

**Interfaces:**
- Consumes: Task 1 `QuestData` fields, `withCompletion`.
- Produces: `void completeQuest(UUID questId, UUID completedBy, long completedAt)` (sets both columns, deletes `pinned_quests` and `collaboration_requests` rows for the quest, one transaction); `void reopenQuest(UUID questId)` (sets `completed_at=0`, `completed_by=NULL`); `getQuest`/`getQuestsForPlayer` return completion with `completedByName` joined from `player_names`; `getServerQuests` excludes `completed_at != 0`; `saveQuest` never writes the two columns (upsert leaves them).

- [ ] **Step 1: Write the failing tests:**
  - `migration_addsCompletionColumns_idempotent`: legacy DB without the columns (use the existing `LEGACY_DDL`/`insertLegacyRow` helper) → `initialize` twice → `PRAGMA table_info(quests)` has `completed_at`, `completed_by`; legacy quest reads `completedAt==0`.
  - `completeQuest_setsFieldsAndName`: upsert player name "Alice", complete → `getQuest` has `completedAt`, `completedBy`, `completedByName=="Alice"`.
  - `complete_removesPins` and `complete_deletesPendingRequests`.
  - `reopenQuest_clearsCompletion`.
  - `getServerQuests_excludesCompleted`; `getQuestsForPlayer_includesCompleted`.
  - `saveQuest_preservesCompletion`: complete, then `saveQuest(questWithoutCompletion)` → still completed.
- [ ] **Step 2:** `./gradlew :server:test --tests "*DataManagerTest"` → FAIL.
- [ ] **Step 3:** Implement: `migrateCompletion()` called from `initialize` after `createTables` (`ALTER TABLE quests ADD COLUMN completed_at INTEGER NOT NULL DEFAULT 0` / `completed_by TEXT` when missing); new table DDL includes both columns; queries as above.
- [ ] **Step 4:** `./gradlew :server:test` → PASS.
- [ ] **Step 5:** Commit `feat: store quest completion and keep completed quests off the board`.

### Task 3: Server handlers, lock and per-version delivery

**Files:**
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/ServerPacketHandler.java`
- Test: `server/src/test/java/com/disqt/disquests/server/papermc/ServerPacketHandlerTest.java`

**Interfaces:**
- Consumes: Task 1 packets/codec, Task 2 `completeQuest`/`reopenQuest`.
- Produces: `handleCompleteQuest(Player, UUID)`, `handleReopenQuest(Player, UUID)`; a private `boolean canEdit(QuestData, UUID)` shared with save; a private `void sendQuestTo(Player, QuestData)` that sends `UPDATE_QUEST` at the player's version or `DELETE_QUEST_S2C` when the quest is completed and the player is below V3.

- [ ] **Step 1: Write the failing tests:**
  - `complete_byOwner_marksCompleted_andUpdatesV3Owner` (owner registered V3 receives `UPDATE_QUEST` whose `readQuest(buf, V3).isCompleted()`).
  - `complete_byViewOnlyContributor_isRefused` (DB unchanged).
  - `complete_sendsDeleteToV2Viewers` (Open quest; bystander registered V2 receives `DELETE_QUEST_S2C` with the id; V3 bystander who is not a participant also receives `DELETE_QUEST_S2C`).
  - `reopen_byOwner_restoresAndBroadcasts` (Open quest → bystanders V3 receive `UPDATE_QUEST` not completed).
  - `reopen_byDemotedContributor_isRefused`.
  - `saveOnCompletedQuest_isIgnored`, `joinCompletedQuest_isIgnored`, `pinCompletedQuest_isIgnored`, `leaveCompletedQuest_isIgnored`.
  - `sync_v2_excludesCompleted_v3_includesForParticipants` (`REQUEST_SYNC` V2 vs V3; parse `SYNC_MY_QUESTS`).
  - `wikiLink_toCompletedQuest_stillResolves` (`WikiLinkResolver.resolveForRecipient` on content `[[Title]]` returns the UUID form).
- [ ] **Step 2:** `./gradlew :server:test --tests "*ServerPacketHandlerTest"` → FAIL.
- [ ] **Step 3:** Implement: dispatch the two new types; guards at the top of save/join/request/contributors/visibility/pin/leave (`if (quest.isCompleted()) return;`); complete/reopen send to owner + contributors with `sendQuestTo`, and for Open/Closed quests send `DELETE_QUEST_S2C` (complete) or `UPDATE_QUEST` per version (reopen) to every other mod player; `handleRequestSync` filters completed quests out of `myQuests` when the client is below V3; pending-request counts resent to the owner after complete.
- [ ] **Step 4:** `./gradlew :server:test` → PASS; `./gradlew build` → PASS.
- [ ] **Step 5:** Commit `feat: complete and reopen quests on the server; lock archived quests`.

### Task 4: Client data, networking and HUD

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/data/Quest.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/network/PacketSender.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientSession.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java` (or `HudPinManager`, wherever pinned quests are resolved)
- Test: `client/src/test/java/com/disqt/disquests/client/data/QuestTest.java`

**Interfaces:**
- Produces: `Quest.getCompletedAt()`, `getCompletedBy()`, `getCompletedByName()`, `isCompleted()` (copied in `fromNetwork`); `PacketSender.completeQuest(UUID)`, `PacketSender.reopenQuest(UUID)`; `ClientSession.Tab.ARCHIVED`.

- [ ] **Step 1:** Test `fromNetwork_copiesCompletion` (completed and not completed) in `QuestTest` → FAIL.
- [ ] **Step 2:** Implement; the HUD skips quests with `isCompleted()` and `ClientSession.removePinnedQuest` is called for a quest that arrives completed (in `ClientPacketHandler.handleUpdateQuest`).
- [ ] **Step 3:** `./gradlew :client:test` → PASS; `./gradlew build` → PASS.
- [ ] **Step 4:** Commit `feat: client carries quest completion`.

### Task 5: Archived tab and Complete/Reopen UI

**Files:**
- Modify: `client/src/main/resources/assets/disquests/owo_ui/main_screen.xml`, `main_screen_legacy.xml` (add `tab-archived` after `tab-quest-board`, same sizing as the other tabs; in the map layout the three tabs must still fit the minimum panel width 180: use fixed 56 each or shorten via lang)
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestListOrder.java` (pure helpers)
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java` (`NavEntry.MainNav` stores the tab, not a boolean)
- Modify: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml`, `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/resources/assets/disquests/lang/en_us.json5`, `fr_fr.json5`
- Test: `client/src/test/java/com/disqt/disquests/client/gui/screen/QuestListOrderTest.java`

**Interfaces:**
- Consumes: Task 4.
- Produces: `QuestListOrder.archived(List<Quest> mine) -> List<Quest>` (completed only, `completedAt` descending); `QuestListOrder.active(List<Quest>) -> List<Quest>` (drops completed). Ids `tab-archived`, `btn-complete`, `btn-reopen`, `completed-label`.

- [ ] **Step 1:** Tests `archived_onlyCompleted_newestFirst`, `active_dropsCompleted` → FAIL.
- [ ] **Step 2:** Implement the helpers; `MainScreen.visibleQuests()`: My Quests and Quest Board use `active(...)`, Archived uses `archived(ClientCache.getMyQuests())` then search/tag filter; Archived hides the filter row and the New Quest/Join buttons (remove/re-add, not zero-size); the map layer always gets `active(...)` of the My Quests + board union currently drawn (completed never drawn); `NavEntry.MainNav(ClientSession.Tab tab, String search)`.
- [ ] **Step 3:** `QuestScreen` view mode: if not completed and `canEdit`, add `btn-complete` → confirm overlay (copy above) → `PacketSender.completeQuest` and navigate back; if completed: remove Edit, Leave, Interact; add `completed-label` (date via `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` in the system zone; name-less variant when `completedByName` is null) and, if `canEdit`, `btn-reopen` → confirm overlay → `PacketSender.reopenQuest`. Delete stays for the owner.
- [ ] **Step 4:** `./gradlew build :client:compileTestmodJava` → PASS.
- [ ] **Step 5:** Commit `feat: Archived tab and complete/reopen buttons`.

### Task 6: E2E journeys, docs, version

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/QuestCompletionJourney.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/duo/TwoPlayerJourneys.java`
- Modify: `.claude/CLAUDE.md` (protocol V3 bullet, packet lists, Archived/lock gotchas), `gradle.properties` (`mod_version=0.8.0`)

- [ ] **Step 1:** Solo journey: create quest "Done Quest" → save → back → open it → `btn-complete` → confirm (`btn-confirm-yes`) → `waitForQuestCondition("Done Quest", true, Quest::isCompleted)` → My Quests list has no "Done Quest" → `tab-archived` → entry present → open → `completed-label` text starts with "Completed on" → `btn-edit` missing → `btn-reopen` → confirm → `waitForQuestCondition(..., q -> !q.isCompleted())` → back in My Quests.
- [ ] **Step 2:** Duo step: A makes an Open quest, B joins with edit rights granted by A, A completes it, B's cache shows it completed and B's Archived tab lists it (PhaseSync signals as in the existing duo journeys).
- [ ] **Step 3:** `./gradlew :client:compileTestmodJava` → PASS (journeys run in CI).
- [ ] **Step 4:** Docs and version bump; commit `test: quest completion journeys; docs and 0.8.0`.
