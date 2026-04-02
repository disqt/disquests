# Tag Picker Stale Tags Fix

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make newly-created tags appear immediately in the tag picker when adding tags to other quests, without requiring a reconnect.

**Architecture:** Two-pronged fix: (1) client-side, the tag picker also collects tags from cached quests for instant feedback; (2) server-side, `handleSaveQuest` broadcasts a fresh `SYNC_TAGS` packet to all V1+ mod players when the saved quest's tags include any tag not already in the DB. This ensures both immediate UX and cross-player tag discovery.

**Tech Stack:** Paper plugin (Java 21), Fabric client (owo-ui), SQLite, JUnit 5 / Mockito (server), E2E (FabricClientGameTest)

---

### Task 1: Client-side — collect tags from cached quests

**Problem:** `TagPickerScreen.rebuildChipCloud()` only merges `ClientSession.getPredefinedTags()` + `ClientSession.getServerTags()`. Tags on quests already in `ClientCache` are ignored, so a tag just added to Quest 1 won't appear when opening the picker for Quest 2.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/TagPickerScreen.java:120-125`

- [ ] **Step 1: Add ClientCache import**

Add at the top of the file:

```java
import com.disqt.disquests.client.ClientCache;
```

- [ ] **Step 2: Merge cached quest tags into allTags**

In `rebuildChipCloud()`, after the existing merge of predefined + server tags (line 123-125), add collection of tags from cached quests:

```java
    // Merge predefined + server tags, deduplicated, preserving order
    Set<String> allTags = new LinkedHashSet<>();
    allTags.addAll(ClientSession.getPredefinedTags());
    allTags.addAll(ClientSession.getServerTags());
    // Also include tags from cached quests (immediate feedback for newly-created tags)
    for (Quest q : ClientCache.getMyQuests()) {
      allTags.addAll(q.getTags());
    }
    for (Quest q : ClientCache.getServerQuests()) {
      allTags.addAll(q.getTags());
    }
```

This requires importing `com.disqt.disquests.client.data.Quest` — check if it's already imported (it is, via the constructor parameter).

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/TagPickerScreen.java
git commit -m "fix: include cached quest tags in tag picker suggestions

Tags from quests in ClientCache now appear immediately in the
tag picker, so a tag added to Quest 1 shows up when tagging
Quest 2 without needing a server re-sync."
```

---

### Task 2: Server-side — broadcast SYNC_TAGS after save

**Problem:** `SYNC_TAGS` is only sent during `handleRequestSync` (initial connection). When a quest is saved with a new tag, other players (and even the saving player after the initial sync) never get the updated tag list.

**Files:**
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/ServerPacketHandler.java:127-201`

- [ ] **Step 1: Add a helper method to broadcast SYNC_TAGS to all V1+ players**

Add this private method after the existing `broadcastToContributors` method (around line 539):

```java
  /** Broadcasts an updated SYNC_TAGS packet to all connected V1+ mod players. */
  private void broadcastSyncTags() {
    List<String> allTags = dataManager.getAllDistinctTags(config.getPredefinedTags());
    byte[] packet = PacketCodec.writeSyncTags(allTags);
    for (Player p : getModPlayers()) {
      if (getProtocolVersion(p) >= ProtocolVersion.V1) {
        sendPacket(p, packet);
      }
    }
  }
```

- [ ] **Step 2: Call broadcastSyncTags after saving a new quest**

In `handleSaveQuest`, after the new quest save block (after line 169, the `sendPacket` for the new quest), add:

```java
      broadcastSyncTags();
```

Place it after the `sendPacket(player, ...)` line inside the `if (existing == null)` block, before the closing brace.

- [ ] **Step 3: Call broadcastSyncTags after saving an existing quest**

In `handleSaveQuest`, after the existing quest update block (after line 200, the `broadcastToContributors` call), add:

```java
      broadcastSyncTags();
```

Place it after `broadcastToContributors(saved, packet);` inside the `else` block, before the closing brace.

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew :server:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/disqt/disquests/server/papermc/ServerPacketHandler.java
git commit -m "fix: broadcast SYNC_TAGS after quest save

New tags are now immediately available to all connected players
after any quest save, not just on initial connection sync."
```

---

### Task 3: Server unit test for SYNC_TAGS broadcast

**Files:**
- Modify: `server/src/test/java/com/disqt/disquests/server/papermc/ServerPacketHandlerTest.java`

- [ ] **Step 1: Check existing test structure**

Read `server/src/test/java/com/disqt/disquests/server/papermc/ServerPacketHandlerTest.java` to understand the test setup pattern (mock players, mock Bukkit, DataManager usage).

- [ ] **Step 2: Add test verifying SYNC_TAGS is sent after saving a quest with a new tag**

Add a test that:
1. Sets up a mock player with V1 protocol version
2. Calls `handleSaveQuest` with a quest containing a new tag
3. Verifies that a `SYNC_TAGS` packet was sent (check that `sendPluginMessage` was called with data starting with the `SYNC_TAGS` packet type byte)

The exact test code depends on the existing test fixtures in the file. Read the file first, then write the test following the established pattern.

- [ ] **Step 3: Run server tests**

Run: `./gradlew :server:test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add server/src/test/java/com/disqt/disquests/server/papermc/ServerPacketHandlerTest.java
git commit -m "test: verify SYNC_TAGS broadcast after quest save"
```

---

### Task 4: E2E test — tag from Quest 1 appears in Quest 2 picker

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/TagJourney.java`

- [ ] **Step 1: Add a new test at the end of TagJourney**

After the last existing test (check the current max `@Order` value and increment), add:

```java
@Test
@Order(N)  // N = last existing order + 1
@PlayerA
@DisplayName("Custom tag from one quest appears in another quest's picker")
void customTagAppearsInOtherQuestPicker(ClientGameTestContext context) {
  given("'Tag Test' has custom tag 'piwigord' from previous test");

  when("player creates a second quest 'Tag Test 2'");
  click(context, "btn-close");
  waitForScreen(context, MainScreen.class);
  openMainScreen(context);
  click(context, "btn-new-quest");
  waitForScreen(context, QuestScreen.class);
  type(context, "title-field", "Tag Test 2");
  click(context, "btn-save");
  waitForViewMode(context);

  and("opens edit mode and tag picker");
  click(context, "btn-edit");
  waitForEditMode(context);
  click(context, "btn-add-tag");
  waitForScreen(context, TagPickerScreen.class);

  then("'piwigord' appears as a chip in the picker");
  context.waitFor(
      client -> {
        if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
        var root = dScreen.getRootComponent();
        if (root == null) return false;
        FlowLayout chipCloud = root.childById(FlowLayout.class, "chip-cloud");
        if (chipCloud == null) return false;
        return chipCloud.children().stream()
            .anyMatch(
                child ->
                    child instanceof TagChipComponent chip
                        && "piwigord".equals(chip.getTag()));
      },
      TIMEOUT);

  and("player cancels picker");
  click(context, "btn-cancel");
  waitForEditMode(context);
  click(context, "btn-cancel");
  waitForViewMode(context);
}
```

This test depends on Order 6 (`addCustomTag`) having already saved `piwigord` to Quest 1. The new test creates Quest 2 and verifies `piwigord` appears in its tag picker.

- [ ] **Step 2: Verify testmod compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the tag journey tests**

Run: `./gradlew :client:runSoloTests -PtestFilter=TagJourney`
Expected: All tests pass, including the new cross-quest tag test.

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/TagJourney.java
git commit -m "test: verify custom tags appear in other quests' tag picker

E2E test creates two quests, adds a custom tag to the first,
then verifies it appears in the second quest's tag picker."
```
