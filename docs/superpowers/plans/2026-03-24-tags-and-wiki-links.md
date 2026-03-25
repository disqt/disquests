# Tags & Wiki-Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add quest tags/labels (predefined + custom, filterable) and `[[wiki-links]]` (cross-quest references with access control) to Disquests.

**Architecture:** Tags are a new `List<String>` field on `QuestData`, persisted in a `quest_tags` SQLite table, serialized in existing packets, and rendered as colored pills in the quest list. Wiki-links are `[[Quest Name]]` syntax in markdown content, resolved server-side per-recipient for access control, rendered client-side as styled clickable text with autocomplete in the editor.

**Tech Stack:** Java 21, Fabric 1.21.11, PaperMC, owo-ui v0.13.0, commonmark-java, SQLite, JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-24-tags-and-wiki-links-design.md`

---

## File Map

### Tags (Feature 1)

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `common/src/main/java/com/disqt/disquests/common/model/QuestData.java` | Add `List<String> tags` field |
| Modify | `common/src/main/java/com/disqt/disquests/common/PacketCodec.java` | Write/read tags in `writeQuest`, `readQuest`, `writeSaveQuest`, `readSaveQuest`, `writeHandshake`, `readHandshake`; update `SaveQuestPayload`, `HandshakePayload` |
| Modify | `server/src/main/java/com/disqt/disquests/server/papermc/DataManager.java` | `quest_tags` table, tag persistence in `saveQuest`, batch loading via `withTags`/`withTagsAll`, `resetDatabase` cleanup |
| Modify | `server/src/main/java/com/disqt/disquests/server/papermc/ServerPacketHandler.java` | Pass tags through in `handleSaveQuest`, validate tags |
| Modify | `server/src/main/java/com/disqt/disquests/server/papermc/Config.java` | Load `predefined-tags` from config |
| Modify | `server/src/main/resources/config.yml` | Add `predefined-tags` default |
| Modify | `server/src/main/java/com/disqt/disquests/server/papermc/DisquestsPlugin.java` | Pass predefined tags to handshake |
| Modify | `client/src/main/java/com/disqt/disquests/client/data/Quest.java` | Add `tags` field, getter/setter, update `fromNetwork` |
| Modify | `client/src/main/java/com/disqt/disquests/client/network/PacketSender.java` | Add `tags` parameter to `saveQuest` |
| Modify | `client/src/main/java/com/disqt/disquests/client/ClientSession.java` | Store predefined tags from handshake, clear on disconnect |
| Modify | `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java` | Pass predefined tags from handshake to ClientSession |
| Modify | `client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java` | Replace Row 3 (last-modified + location) with tag pills |
| Create | `client/src/main/java/com/disqt/disquests/client/gui/helper/TagColors.java` | Tag color mapping (predefined fixed colors, custom hash-based) |
| Modify | `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java` | Tag picker UI in edit mode, pass tags to `PacketSender.saveQuest`, display tags in view mode |
| Modify | `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java` | `#tag` search syntax parsing |
| Modify | `client/src/main/resources/assets/disquests/owo_ui/quest_screen_edit.xml` | Tag section layout below title |
| Modify | `client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml` | Tag display section |
| Modify | `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java` | Tag serialization round-trip tests |
| Modify | `server/src/test/java/com/disqt/disquests/server/papermc/DataManagerTest.java` | Tag persistence tests |

### Wiki-Links (Feature 2)

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `server/src/main/java/com/disqt/disquests/server/papermc/WikiLinkResolver.java` | Server-side `[[Quest Name]]` resolution per-recipient, reverse-resolution on save |
| Modify | `server/src/main/java/com/disqt/disquests/server/papermc/ServerPacketHandler.java` | Call resolver before sending quests, reverse-resolve on save |
| Modify | `server/src/main/java/com/disqt/disquests/server/papermc/DataManager.java` | `findQuestsByTitles(List<String>)` batch lookup for resolution |
| Modify | `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java` | Pre-process `[[uuid|title]]` to `<dqlink>`, render `HtmlInline` nodes as styled wiki-links |
| Create | `client/src/main/java/com/disqt/disquests/client/gui/component/AutocompleteDropdown.java` | Dropdown overlay for `[[` autocomplete in editor |
| Modify | `client/src/main/java/com/disqt/disquests/client/gui/component/TextFieldComponent.java` | Integrate autocomplete (detect `[[`, show/hide dropdown, insert selection) |
| Modify | `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java` | Wire autocomplete to content editor |
| Modify | `client/src/main/resources/assets/disquests/owo_ui/quest_screen_edit.xml` | Add `[[Quest]]` to formatting hints |
| Create | `server/src/test/java/com/disqt/disquests/server/papermc/WikiLinkResolverTest.java` | Unit tests for resolution logic |
| Modify | `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java` | Content with wiki-link syntax round-trips correctly |

---

## Part 1: Tags

### Task 1: QuestData record + PacketCodec

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/model/QuestData.java:6-19`
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketCodec.java` (lines 49-50, 76-88, 260-302, 310-319, 157-177, 369-391)
- Test: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`

- [ ] **Step 1: Write failing test for tag round-trip in writeQuest/readQuest**

Add to `PacketCodecTest.java`:

```java
@Test
void questRoundTrip_withTags() {
    QuestData quest = new QuestData(
            UUID.randomUUID(), "Tagged Quest", "content",
            UUID.randomUUID(), "owner", Visibility.OPEN,
            List.of(), System.currentTimeMillis(),
            null, false, null, null,
            List.of("nether", "building", "piwigord")
    );
    ByteBufWriter buf = new ByteBufWriter();
    PacketCodec.writeQuest(buf, quest);
    QuestData result = PacketCodec.readQuest(new ByteBufReader(buf.toByteArray()));
    assertQuestsEqual(quest, result);
    assertEquals(List.of("nether", "building", "piwigord"), result.tags());
}

@Test
void questRoundTrip_emptyTags() {
    QuestData quest = new QuestData(
            UUID.randomUUID(), "No Tags", "content",
            UUID.randomUUID(), "owner", Visibility.PRIVATE,
            List.of(), System.currentTimeMillis(),
            null, false, null, null,
            List.of()
    );
    ByteBufWriter buf = new ByteBufWriter();
    PacketCodec.writeQuest(buf, quest);
    QuestData result = PacketCodec.readQuest(new ByteBufReader(buf.toByteArray()));
    assertEquals(List.of(), result.tags());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests "*.PacketCodecTest.questRoundTrip_withTags" --tests "*.PacketCodecTest.questRoundTrip_emptyTags"`
Expected: compilation failure (QuestData constructor doesn't accept tags yet)

- [ ] **Step 3: Add `tags` field to QuestData**

In `QuestData.java`, add `List<String> tags` as the last field:

```java
public record QuestData(
    UUID id,
    String title,
    String content,
    UUID ownerUuid,
    String ownerName,
    Visibility visibility,
    List<ContributorData> contributors,
    long lastModified,
    CoordinatesData coordinates,
    boolean isRegion,
    CoordinatesData coordinates2,
    String map,
    List<String> tags
) {}
```

- [ ] **Step 4: Update writeQuest/readQuest in PacketCodec**

At the end of `writeQuest()` (after `writeNullableString(buf, quest.map())`), append:

```java
buf.writeVarInt(quest.tags().size());
for (String tag : quest.tags()) {
    buf.writeString(tag);
}
```

At the end of `readQuest()`, before the return, add:

```java
int tagCount = buf.readVarInt();
List<String> tags = new ArrayList<>(tagCount);
for (int i = 0; i < tagCount; i++) {
    tags.add(buf.readString());
}
```

Update the return statement to include `tags`:

```java
return new QuestData(id, title, content, ownerUuid, ownerName, visibility,
        contributors, lastModified, coordinates, isRegion, coordinates2, map, tags);
```

- [ ] **Step 5: Update SaveQuestPayload and writeSaveQuest/readSaveQuest**

Update `SaveQuestPayload` record (line 49-50):

```java
public record SaveQuestPayload(UUID questId, String title, String content,
        CoordinatesData coords, boolean isRegion, CoordinatesData coords2, String map,
        List<String> tags) {}
```

Update `writeSaveQuest()` signature and append tags:

```java
public static byte[] writeSaveQuest(UUID questId, String title, String content,
        CoordinatesData coords, boolean isRegion, CoordinatesData coords2, String map,
        List<String> tags) {
    // ... existing code ...
    writeNullableString(buf, map);
    buf.writeVarInt(tags.size());
    for (String tag : tags) {
        buf.writeString(tag);
    }
    return buf.toByteArray();
}
```

Update `readSaveQuest()` to read tags before returning:

```java
String map = readNullableString(buf);
int tagCount = buf.readVarInt();
List<String> tags = new ArrayList<>(tagCount);
for (int i = 0; i < tagCount; i++) {
    tags.add(buf.readString());
}
return new SaveQuestPayload(questId, title, content, coords, isRegion, coords2, map, tags);
```

- [ ] **Step 6: Update HandshakePayload and writeHandshake/readHandshake for predefined tags**

Update `HandshakePayload` record:

```java
public record HandshakePayload(String bluemapUrl, int pendingRequestCount,
        List<UUID> pinnedQuestIds, UUID playerUuid, Map<String, String> bluemapMapNames,
        List<String> predefinedTags) {}
```

Update the existing 5-arg `writeHandshake()` to become 6-arg by adding `List<String> predefinedTags`. After the mapNames loop, append:

```java
buf.writeVarInt(predefinedTags.size());
for (String tag : predefinedTags) {
    buf.writeString(tag);
}
```

Update the existing 4-arg convenience overload to pass `List.of()`:

```java
public static byte[] writeHandshake(String bluemapUrl, int pendingRequestCount,
        List<UUID> pinnedQuestIds, UUID playerUuid) {
    return writeHandshake(bluemapUrl, pendingRequestCount, pinnedQuestIds, playerUuid, Map.of(), List.of());
}
```

Update `readHandshake()` to read predefined tags after mapNames (with `remaining() > 0` guard since handshake is a single packet):

```java
List<String> predefinedTags;
if (buf.remaining() > 0) {
    int tagCount = buf.readVarInt();
    predefinedTags = new ArrayList<>(tagCount);
    for (int i = 0; i < tagCount; i++) {
        predefinedTags.add(buf.readString());
    }
} else {
    predefinedTags = List.of();
}
return new HandshakePayload(bluemapUrl, pendingRequestCount, pinnedQuestIds,
        playerUuid, mapNames, predefinedTags);
```

- [ ] **Step 7: Fix all compilation errors from QuestData constructor change**

Every call site constructing `QuestData` needs `List.of()` (or the actual tags) appended. Update:
- `PacketCodecTest.makeQuest()` -- add `List.of()` as last arg
- `DataManagerTest.makeQuest()` -- add `List.of()` as last arg
- Any other test helpers constructing `QuestData`

Also update `assertQuestsEqual()` in `PacketCodecTest` to compare tags:
```java
assertEquals(expected.tags(), actual.tags());
```

- [ ] **Step 8: Write failing test for SaveQuest tag round-trip**

```java
@Test
void saveQuestRoundTrip_withTags() {
    UUID id = UUID.randomUUID();
    List<String> tags = List.of("nether", "building");
    byte[] bytes = PacketCodec.writeSaveQuest(id, "title", "content",
            null, false, null, null, tags);
    ByteBufReader buf = new ByteBufReader(bytes);
    buf.readByte(); // skip packet type
    PacketCodec.SaveQuestPayload payload = PacketCodec.readSaveQuest(buf);
    assertEquals(tags, payload.tags());
}
```

- [ ] **Step 9: Run all common tests**

Run: `./gradlew :common:test`
Expected: all PASS

- [ ] **Step 10: Commit**

```bash
git add common/
git commit -m "feat(common): add tags field to QuestData, PacketCodec, and HandshakePayload"
```

---

### Task 2: Server-side tag persistence (DataManager)

**Files:**
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/DataManager.java`
- Test: `server/src/test/java/com/disqt/disquests/server/papermc/DataManagerTest.java`

- [ ] **Step 1: Write failing tests for tag persistence**

Add to `DataManagerTest.java`:

```java
@Test
void saveQuest_persistsTags() {
    UUID questId = UUID.randomUUID();
    QuestData quest = new QuestData(questId, "Tagged", "content", OWNER, null,
            Visibility.PRIVATE, List.of(), 1000L, null, false, null, null,
            List.of("nether", "building"));
    dm.saveQuest(quest);
    QuestData loaded = dm.getQuest(questId);
    assertEquals(List.of("building", "nether"), loaded.tags().stream().sorted().toList());
}

@Test
void saveQuest_replacesTags() {
    UUID questId = UUID.randomUUID();
    QuestData quest1 = new QuestData(questId, "Quest", "content", OWNER, null,
            Visibility.PRIVATE, List.of(), 1000L, null, false, null, null,
            List.of("nether", "building"));
    dm.saveQuest(quest1);

    QuestData quest2 = new QuestData(questId, "Quest", "content", OWNER, null,
            Visibility.PRIVATE, List.of(), 2000L, null, false, null, null,
            List.of("redstone"));
    dm.saveQuest(quest2);

    QuestData loaded = dm.getQuest(questId);
    assertEquals(List.of("redstone"), loaded.tags());
}

@Test
void saveQuest_emptyTags() {
    UUID questId = UUID.randomUUID();
    QuestData quest = new QuestData(questId, "No Tags", "content", OWNER, null,
            Visibility.PRIVATE, List.of(), 1000L, null, false, null, null,
            List.of());
    dm.saveQuest(quest);
    QuestData loaded = dm.getQuest(questId);
    assertEquals(List.of(), loaded.tags());
}

@Test
void resetDatabase_clearsTags() {
    UUID questId = UUID.randomUUID();
    QuestData quest = new QuestData(questId, "Tagged", "content", OWNER, null,
            Visibility.PRIVATE, List.of(), 1000L, null, false, null, null,
            List.of("nether"));
    dm.saveQuest(quest);
    dm.resetDatabase();
    assertNull(dm.getQuest(questId));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :server:test --tests "*.DataManagerTest"`
Expected: compilation failure or test failures

- [ ] **Step 3: Add `quest_tags` table to `createTables()`**

After the `player_names` table creation in `createTables()`, add:

```java
stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS quest_tags (
            quest_id TEXT NOT NULL,
            tag TEXT NOT NULL,
            PRIMARY KEY (quest_id, tag),
            FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
        )""");
```

- [ ] **Step 4: Update `saveQuest()` to persist tags**

After the existing `stmt.executeUpdate()` in `saveQuest()`, add tag persistence (still inside the try block):

```java
// Delete + re-insert tags
try (PreparedStatement delTags = connection.prepareStatement(
        "DELETE FROM quest_tags WHERE quest_id = ?")) {
    delTags.setString(1, quest.id().toString());
    delTags.executeUpdate();
}
if (!quest.tags().isEmpty()) {
    try (PreparedStatement insTags = connection.prepareStatement(
            "INSERT OR IGNORE INTO quest_tags (quest_id, tag) VALUES (?, ?)")) {
        for (String tag : quest.tags()) {
            insTags.setString(1, quest.id().toString());
            insTags.setString(2, tag);
            insTags.addBatch();
        }
        insTags.executeBatch();
    }
}
```

- [ ] **Step 5: Add `withTags()` and `withTagsAll()` methods**

Follow the `withContributors()`/`withContributorsAll()` pattern:

```java
private QuestData withTags(QuestData quest) {
    List<String> tags = getTagsForQuest(quest.id());
    return new QuestData(
            quest.id(), quest.title(), quest.content(),
            quest.ownerUuid(), quest.ownerName(), quest.visibility(),
            quest.contributors(), quest.lastModified(),
            quest.coordinates(), quest.isRegion(), quest.coordinates2(), quest.map(),
            tags);
}

private List<QuestData> withTagsAll(List<QuestData> quests) {
    List<UUID> questIds = quests.stream().map(QuestData::id).toList();
    Map<UUID, List<String>> tagMap = getTagsBatch(questIds);
    List<QuestData> result = new ArrayList<>(quests.size());
    for (QuestData quest : quests) {
        List<String> tags = tagMap.getOrDefault(quest.id(), List.of());
        result.add(new QuestData(
                quest.id(), quest.title(), quest.content(),
                quest.ownerUuid(), quest.ownerName(), quest.visibility(),
                quest.contributors(), quest.lastModified(),
                quest.coordinates(), quest.isRegion(), quest.coordinates2(), quest.map(),
                tags));
    }
    return result;
}
```

Add the helper methods:

```java
private List<String> getTagsForQuest(UUID questId) {
    List<String> tags = new ArrayList<>();
    try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT tag FROM quest_tags WHERE quest_id = ?")) {
        stmt.setString(1, questId.toString());
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            tags.add(rs.getString("tag"));
        }
    } catch (SQLException e) {
        throw new RuntimeException("Failed to load tags", e);
    }
    return tags;
}

private Map<UUID, List<String>> getTagsBatch(List<UUID> questIds) {
    if (questIds.isEmpty()) return Map.of();
    Map<UUID, List<String>> result = new HashMap<>();
    String placeholders = questIds.stream().map(id -> "?").collect(Collectors.joining(","));
    try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT quest_id, tag FROM quest_tags WHERE quest_id IN (" + placeholders + ")")) {
        for (int i = 0; i < questIds.size(); i++) {
            stmt.setString(i + 1, questIds.get(i).toString());
        }
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            UUID questId = UUID.fromString(rs.getString("quest_id"));
            result.computeIfAbsent(questId, k -> new ArrayList<>()).add(rs.getString("tag"));
        }
    } catch (SQLException e) {
        throw new RuntimeException("Failed to batch-load tags", e);
    }
    return result;
}
```

- [ ] **Step 6: Update `mapQuestRow()` to include empty tags list**

Add `List.of()` as the last argument in the `QuestData` constructor call inside `mapQuestRow()`.

- [ ] **Step 7: Update `withContributors()` and `withContributorsAll()` to pass tags through**

In `withContributors()`, add `quest.tags()` as the last constructor arg.

In `withContributorsAll()`, add `quest.tags()` as the last constructor arg.

- [ ] **Step 8: Call `withTags`/`withTagsAll` from the same places that call `withContributors`/`withContributorsAll`**

Find where `withContributors()` and `withContributorsAll()` are called (typically in `getQuest()`, `getQuestsForPlayer()`, `getServerQuests()`). Chain `withTags()` after `withContributors()` for single quests, and `withTagsAll()` after `withContributorsAll()` for lists. For example:

```java
// Before: return withContributors(mapQuestRow(rs));
// After:  return withTags(withContributors(mapQuestRow(rs)));
```

- [ ] **Step 9: Update `resetDatabase()` to clear `quest_tags`**

Add before the existing `DELETE FROM quests` line:

```java
stmt.executeUpdate("DELETE FROM quest_tags");
```

- [ ] **Step 10: Run server tests**

Run: `./gradlew :server:test`
Expected: all PASS

- [ ] **Step 11: Commit**

```bash
git add server/
git commit -m "feat(server): add quest_tags table, tag persistence and batch loading"
```

---

### Task 3: Server-side tag validation and config

**Files:**
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/Config.java`
- Modify: `server/src/main/resources/config.yml`
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/ServerPacketHandler.java:83-122`
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/DisquestsPlugin.java`

- [ ] **Step 1: Add predefined tags to Config.java**

Add field and getter:

```java
private List<String> predefinedTags;

// In reload():
this.predefinedTags = cfg.getStringList("predefined-tags");
if (this.predefinedTags == null) this.predefinedTags = List.of();

public List<String> getPredefinedTags() { return predefinedTags; }
```

- [ ] **Step 2: Add default predefined-tags to config.yml**

Append to `server/src/main/resources/config.yml`:

```yaml
predefined-tags:
  - overworld
  - nether
  - the_end
  - building
  - redstone
  - farm
```

**Note:** Existing servers with a `config.yml` won't get this new section automatically (`saveDefaultConfig()` doesn't overwrite). `cfg.getStringList()` returns an empty list for missing keys, so this is safe -- predefined tags just won't appear in the picker until the admin adds them. Document this in release notes.

- [ ] **Step 3: Add tag validation to `handleSaveQuest()`**

In `ServerPacketHandler.handleSaveQuest()`, after the existing field length checks, add tag validation:

```java
// Validate and normalize tags
List<String> tags = payload.tags().stream()
        .map(String::toLowerCase)
        .filter(t -> !t.isEmpty() && t.length() <= 32)
        .filter(t -> t.matches("[a-z0-9_-]+"))
        .distinct()
        .limit(8)
        .toList();
```

Use this `tags` variable when constructing the new/updated `QuestData` (instead of `payload.tags()`).

- [ ] **Step 4: Pass tags through in handleSaveQuest QuestData construction**

Update both `QuestData` constructor calls in `handleSaveQuest()`:

New quest (line ~95-99): add `tags` as last arg.
Existing quest (line ~112-116): add `tags` as last arg.

- [ ] **Step 5: Pass predefined tags in handshake**

Find where `writeHandshake()` is called in the plugin (likely `DisquestsPlugin.java` or `ServerPacketHandler.java`). Update to use the new overload that accepts `predefinedTags`:

```java
PacketCodec.writeHandshake(config.getBluemapUrl(), pendingCount, pinnedIds,
        player.getUniqueId(), config.getBluemapMapNames(), config.getPredefinedTags())
```

- [ ] **Step 6: Build server module to verify compilation**

Run: `./gradlew :server:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add server/
git commit -m "feat(server): add tag validation, predefined-tags config, handshake delivery"
```

---

### Task 4: Client-side tag model and networking

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/data/Quest.java:11-44`
- Modify: `client/src/main/java/com/disqt/disquests/client/network/PacketSender.java:17-20`
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientSession.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java:168-170, 474-475`

- [ ] **Step 1: Add `tags` field to Quest.java**

Add to fields (after `map`):

```java
private List<String> tags = new ArrayList<>();
```

Add getter and setter:

```java
public List<String> getTags() { return tags; }
public void setTags(List<String> tags) { this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>(); }
```

Update `fromNetwork()` -- after `quest.map = data.map();`:

```java
quest.tags = new ArrayList<>(data.tags());
```

- [ ] **Step 2: Update PacketSender.saveQuest()**

Add `List<String> tags` parameter:

```java
public static void saveQuest(UUID questId, String title, String content,
        CoordinatesData coords, boolean isRegion, CoordinatesData coords2, String map,
        List<String> tags) {
    send(PacketCodec.writeSaveQuest(questId, title, content, coords, isRegion, coords2, map, tags));
}
```

- [ ] **Step 3: Update QuestScreen save call sites**

At line ~168 (checkbox toggle save):

```java
PacketSender.saveQuest(quest.getId(), quest.getTitle(), updated,
        quest.getCoordinates(), quest.isRegion(), quest.getCoordinates2(), quest.getMap(),
        quest.getTags());
```

At line ~474 (saveAndView):

```java
PacketSender.saveQuest(quest.getId(), quest.getTitle(), quest.getContent(),
        quest.getCoordinates(), quest.isRegion(), quest.getCoordinates2(), quest.getMap(),
        quest.getTags());
```

- [ ] **Step 4: Store predefined tags in ClientSession**

Add field to `ClientSession.java` (after `bluemapMapNames`):

```java
private static List<String> predefinedTags = List.of();

public static List<String> getPredefinedTags() { return predefinedTags; }
```

Update the 5-arg `joinServer()` to become 6-arg, accepting `List<String> predefinedTags`:

```java
public static void joinServer(String bluemapUrl, int pendingCount, List<UUID> pinnedIds,
        UUID playerUuid, Map<String, String> bluemapMapNames, List<String> predefinedTags) {
    // ... existing assignments ...
    ClientSession.predefinedTags = predefinedTags != null ? List.copyOf(predefinedTags) : List.of();
}
```

Update the 4-arg convenience overload to pass `List.of()`:

```java
public static void joinServer(String bluemapUrl, int pendingCount, List<UUID> pinnedIds, UUID playerUuid) {
    joinServer(bluemapUrl, pendingCount, pinnedIds, playerUuid, Map.of(), List.of());
}
```

Add cleanup in `leaveServer()` (after existing field resets):

```java
predefinedTags = List.of();
```

- [ ] **Step 5: Update ClientPacketHandler.handleHandshake()**

In `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java:64-67`, update `handleHandshake()` to pass predefined tags:

```java
private static void handleHandshake(ByteBufReader r) {
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(r);
    ClientSession.joinServer(payload.bluemapUrl(), payload.pendingRequestCount(),
            payload.pinnedQuestIds(), payload.playerUuid(), payload.bluemapMapNames(),
            payload.predefinedTags());
    PacketSender.requestSync();
}
```

- [ ] **Step 6: Build client to verify compilation**

Run: `./gradlew :client:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add client/
git commit -m "feat(client): add tags to Quest model, PacketSender, ClientSession, and ClientPacketHandler"
```

---

### Task 5: Tag colors helper

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/helper/TagColors.java`

- [ ] **Step 1: Create TagColors.java**

```java
package com.disqt.disquests.client.gui.helper;

import com.disqt.disquests.client.ClientSession;
import java.util.List;
import java.util.Map;

public class TagColors {

    // Background + foreground pairs for predefined tags
    private static final Map<String, int[]> PREDEFINED = Map.of(
            "overworld", new int[]{0xFF5c4a2e, 0xFFe8c86d},
            "nether",    new int[]{0xFF5c3d2e, 0xFFe8a86d},
            "the_end",   new int[]{0xFF3d2e5c, 0xFFa86de8},
            "building",  new int[]{0xFF2e4a3d, 0xFF6de8a8},
            "redstone",  new int[]{0xFF2e3d5c, 0xFF6da8e8},
            "farm",      new int[]{0xFF2e5c4a, 0xFF6de8c8}
    );

    // Palette for custom tags (hash selects index)
    private static final int[][] CUSTOM_PALETTE = {
            {0xFF4a3d2e, 0xFFd8b87d}, {0xFF2e4a4a, 0xFF6dd8d8},
            {0xFF4a2e3d, 0xFFd86da8}, {0xFF3d4a2e, 0xFFa8d86d},
            {0xFF2e3d4a, 0xFF6da8d8}, {0xFF4a2e4a, 0xFFd86dd8},
            {0xFF3d2e4a, 0xFFa86dd8}, {0xFF4a4a2e, 0xFFd8d86d},
    };

    public static int getBackground(String tag) {
        int[] colors = PREDEFINED.get(tag);
        if (colors != null) return colors[0];
        int idx = (tag.hashCode() & 0x7FFFFFFF) % CUSTOM_PALETTE.length;
        return CUSTOM_PALETTE[idx][0];
    }

    public static int getForeground(String tag) {
        int[] colors = PREDEFINED.get(tag);
        if (colors != null) return colors[1];
        int idx = (tag.hashCode() & 0x7FFFFFFF) % CUSTOM_PALETTE.length;
        return CUSTOM_PALETTE[idx][1];
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/helper/TagColors.java
git commit -m "feat(client): add TagColors helper for predefined and custom tag colors"
```

---

### Task 6: QuestEntryComponent -- replace Row 3 with tags

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java`

- [ ] **Step 1: Remove Row 3 cached fields for last-modified and location**

Remove these fields and their constructor initialization:
- `cachedLastModifiedText` (field + constructor lines)
- `cachedLocationString`, `cachedLocationWidth` (fields + constructor lines)
- `formattedDateTime`, `DATE_TIME_FORMATTER` (fields + constructor lines)
- `buildLocationString()` method

Also remove the `REQUESTED_STR` and `ClientSession.isRequested()` rendering from Row 3 (the "Requested" text). Move the requested indicator elsewhere if needed (e.g., Row 1 or remove entirely -- it's also shown as a badge in the view screen).

- [ ] **Step 2: Add tag rendering to draw() Row 3**

Replace the Row 3 section in `draw()` (the last-modified + location/requested block) with:

```java
// --- Row 3: Tags ---
List<String> tags = quest.getTags();
if (tags.isEmpty()) {
    context.drawText(textRenderer, Text.literal("no tags").formatted(Formatting.ITALIC),
            entryX + 4, entryY + 24, Colors.TEXT_MUTED, false);
} else {
    int tagX = entryX + 4;
    for (String tag : tags) {
        int bg = TagColors.getBackground(tag);
        int fg = TagColors.getForeground(tag);
        int tagWidth = textRenderer.getWidth(tag) + 6; // 3px padding each side
        if (tagX + tagWidth > entryX + entryWidth - 4) break; // don't overflow
        context.fill(tagX, entryY + 24, tagX + tagWidth, entryY + 34, bg);
        context.drawText(textRenderer, Text.literal(tag), tagX + 3, entryY + 25, fg, false);
        tagX += tagWidth + 3; // 3px gap
    }
}
```

- [ ] **Step 3: Build client to check rendering compiles**

Run: `./gradlew :client:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java
git commit -m "feat(client): replace Row 3 with tag pills in QuestEntryComponent"
```

---

### Task 7: QuestScreen tag display and editor

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_edit.xml`
- Modify: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml`

- [ ] **Step 1: Add tag section to quest_screen_view.xml**

Add a `flow-layout` for tags between the title and content sections (check exact location in the XML). Use id `"tag-display"`:

```xml
<flow-layout direction="horizontal" id="tag-display">
    <sizing>
        <horizontal method="fill">100</horizontal>
        <vertical method="content"/>
    </sizing>
    <gap>3</gap>
    <margins>
        <bottom>4</bottom>
    </margins>
    <children/>
</flow-layout>
```

- [ ] **Step 2: Populate tag-display in view mode**

In `QuestScreen.buildViewMode()`, find `tag-display` by ID and populate:

```java
FlowLayout tagDisplay = root.childById(FlowLayout.class, "tag-display");
if (tagDisplay != null) {
    for (String tag : quest.getTags()) {
        LabelComponent tagLabel = UIComponents.label(Text.literal(tag));
        tagLabel.color(io.wispforest.owo.ui.core.Color.ofArgb(TagColors.getForeground(tag)));
        tagLabel.margins(io.wispforest.owo.ui.core.Insets.of(1, 3, 1, 3));
        tagLabel.sizing(Sizing.content(), Sizing.content());
        // Background via surface
        tagDisplay.child(tagLabel);
    }
    if (quest.getTags().isEmpty()) {
        tagDisplay.child(UIComponents.label(Text.literal("no tags").styled(
                s -> s.withItalic(true).withColor(Colors.TEXT_MUTED))));
    }
}
```

- [ ] **Step 3: Add tag editor section to quest_screen_edit.xml**

Add after the title field section, before the content editor:

```xml
<flow-layout direction="horizontal" id="tag-editor">
    <sizing>
        <horizontal method="fill">100</horizontal>
        <vertical method="content"/>
    </sizing>
    <gap>3</gap>
    <margins>
        <bottom>4</bottom>
    </margins>
    <children/>
</flow-layout>
```

- [ ] **Step 4: Implement tag editor in QuestScreen edit mode**

In `buildEditMode()`, populate `tag-editor`:

```java
FlowLayout tagEditor = root.childById(FlowLayout.class, "tag-editor");
if (tagEditor != null) {
    rebuildTagEditor(tagEditor);
}
```

Add `rebuildTagEditor()` method:

```java
private void rebuildTagEditor(FlowLayout tagEditor) {
    tagEditor.clearChildren();
    for (String tag : quest.getTags()) {
        // Tag pill with X button
        FlowLayout pill = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        pill.gap(2);
        pill.child(UIComponents.label(Text.literal(tag)));
        ButtonComponent removeBtn = UIComponents.button(Text.literal("x"), btn -> {
            quest.getTags().remove(tag);
            rebuildTagEditor(tagEditor);
        });
        removeBtn.sizing(Sizing.fixed(12), Sizing.fixed(12));
        pill.child(removeBtn);
        tagEditor.child(pill);
    }
    if (quest.getTags().size() < 8) {
        ButtonComponent addBtn = UIComponents.button(Text.literal("+ Tag"), btn -> {
            openTagPicker(tagEditor);
        });
        addBtn.sizing(Sizing.content(), Sizing.fixed(14));
        tagEditor.child(addBtn);
    }
}
```

Add `openTagPicker()` method. This navigates to a new lightweight screen (like `ConfirmScreen`) that shows:
- A vertical list of predefined tags from `ClientSession.getPredefinedTags()` as clickable buttons (excluding tags already on the quest)
- A `TextBoxComponent` at the bottom for typing a custom tag, with an "Add" button
- Clicking a predefined tag or pressing Add: validates against `[a-z0-9_-]+`, adds to `quest.getTags()`, navigates back to QuestScreen edit mode

```java
private void openTagPicker(FlowLayout tagEditor) {
    // Navigate to a tag picker screen, passing quest and a callback
    navigateToScreen(new TagPickerScreen(this, quest, tag -> {
        if (!quest.getTags().contains(tag) && quest.getTags().size() < 8) {
            quest.getTags().add(tag);
        }
    }));
}
```

Create `client/src/main/java/com/disqt/disquests/client/gui/screen/TagPickerScreen.java` as a simple `DisquestsBaseScreen` with:
- Title: "Add Tag"
- A vertical flow listing predefined tags as buttons (filtered to exclude already-added tags)
- A horizontal flow with TextBox + "Add" button for custom tags
- Validation: lowercase, `[a-z0-9_-]+`, max 32 chars
- On tag select/add: run the callback, navigate back to parent

- [ ] **Step 5: Only show tag editing controls if canEdit**

Wrap the tag editor population in the edit permission check:

```java
boolean canEditTags = quest.isOwner(ClientSession.getEffectivePlayerUuid())
        || quest.canEdit(ClientSession.getEffectivePlayerUuid());
if (tagEditor != null && canEditTags) {
    rebuildTagEditor(tagEditor);
}
```

- [ ] **Step 6: Build client**

Run: `./gradlew :client:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add client/
git commit -m "feat(client): add tag display in view mode and tag editor in edit mode"
```

---

### Task 8: MainScreen `#tag` search filter

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

- [ ] **Step 1: Update search filtering logic**

Find the `onSearchTermChanged()` method (or wherever search filtering is applied). Update to parse `#tag` tokens:

```java
private record SearchQuery(String textFilter, List<String> tagFilters) {}

private SearchQuery parseSearch(String raw) {
    List<String> tagFilters = new ArrayList<>();
    StringBuilder text = new StringBuilder();
    for (String token : raw.trim().split("\\s+")) {
        if (token.startsWith("#") && token.length() > 1) {
            tagFilters.add(token.substring(1).toLowerCase());
        } else {
            if (text.length() > 0) text.append(" ");
            text.append(token);
        }
    }
    return new SearchQuery(text.toString(), tagFilters);
}
```

- [ ] **Step 2: Apply tag filter in quest list filtering**

In the method that filters quests for display, after the existing text search filter, add:

```java
SearchQuery query = parseSearch(searchTerm);
// Text filter (existing)
if (!query.textFilter().isEmpty()) {
    quests = quests.stream()
            .filter(q -> q.getTitle().toLowerCase().contains(query.textFilter().toLowerCase())
                    || q.getContent().toLowerCase().contains(query.textFilter().toLowerCase()))
            .toList();
}
// Tag filter (new) -- AND semantics: quest must have ALL specified tags
if (!query.tagFilters().isEmpty()) {
    quests = quests.stream()
            .filter(q -> query.tagFilters().stream().allMatch(
                    tagFilter -> q.getTags().stream().anyMatch(t -> t.equalsIgnoreCase(tagFilter))))
            .toList();
}
```

- [ ] **Step 3: Update search placeholder text**

Change the placeholder from `"Search..."` to `"Search... (#tag to filter)"` or similar.

- [ ] **Step 4: Build and verify**

Run: `./gradlew :client:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat(client): add #tag search syntax to MainScreen"
```

---

### Task 9: E2E tests for tags

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/TagJourney.java`

- [ ] **Step 1: Write tag E2E journey**

Create a new solo journey test that:
1. Creates a quest
2. Enters edit mode, adds tags
3. Saves and verifies tags appear in view mode
4. Returns to list and verifies tag pills render in Row 3
5. Tests `#tag` search filtering
6. Tests tag removal

Follow the BDD pattern used in other journeys (`given`/`when`/`then`/`and`).

- [ ] **Step 2: Verify testmod compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the E2E test**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=TagJourney`
Expected: all steps PASS

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/
git commit -m "test: add E2E journey for tag creation, display, and search"
```

---

## Part 2: Wiki-Links

### Task 10: Server-side WikiLinkResolver

**Files:**
- Create: `server/src/main/java/com/disqt/disquests/server/papermc/WikiLinkResolver.java`
- Create: `server/src/test/java/com/disqt/disquests/server/papermc/WikiLinkResolverTest.java`
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/DataManager.java`

- [ ] **Step 1: Write failing tests for WikiLinkResolver**

Create `WikiLinkResolverTest.java`:

```java
package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WikiLinkResolverTest {

    @TempDir Path tempDir;
    private DataManager dm;
    private WikiLinkResolver resolver;

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        dm = new DataManager(tempDir);
        dm.initialize();
        resolver = new WikiLinkResolver(dm);
    }

    @AfterEach
    void tearDown() { dm.close(); }

    private QuestData makeQuest(UUID id, UUID owner, String title, Visibility vis) {
        return new QuestData(id, title, "content", owner, null, vis,
                List.of(), 1000L, null, false, null, null, List.of());
    }

    @Test
    void resolveForRecipient_accessibleQuest() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Nether Highway", Visibility.OPEN));

        String content = "See [[Nether Highway]] for details";
        String resolved = resolver.resolveForRecipient(content, OTHER);
        assertTrue(resolved.contains("[[" + questId + "|Nether Highway]]"));
    }

    @Test
    void resolveForRecipient_privateQuestHidesTitle() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Secret Base", Visibility.PRIVATE));

        String content = "See [[Secret Base]] for details";
        String resolved = resolver.resolveForRecipient(content, OTHER);
        assertTrue(resolved.contains("[[" + questId + "|Private Quest]]"));
        assertFalse(resolved.contains("Secret Base"));
    }

    @Test
    void resolveForRecipient_ownerSeesTitle() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Secret Base", Visibility.PRIVATE));

        String content = "See [[Secret Base]] for details";
        String resolved = resolver.resolveForRecipient(content, OWNER);
        assertTrue(resolved.contains("[[" + questId + "|Secret Base]]"));
    }

    @Test
    void resolveForRecipient_brokenLink() {
        String content = "See [[Nonexistent Quest]] for details";
        String resolved = resolver.resolveForRecipient(content, OWNER);
        assertTrue(resolved.contains("[[|Nonexistent Quest]]"));
    }

    @Test
    void resolveForRecipient_caseInsensitive() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Nether Highway", Visibility.OPEN));

        String content = "See [[nether highway]] for details";
        String resolved = resolver.resolveForRecipient(content, OTHER);
        assertTrue(resolved.contains("[[" + questId + "|Nether Highway]]"));
    }

    @Test
    void resolveForRecipient_noLinks() {
        String content = "No links here";
        String resolved = resolver.resolveForRecipient(content, OWNER);
        assertEquals("No links here", resolved);
    }

    @Test
    void resolveForRecipient_maxLinksEnforced() {
        // Create 17 quests with links - only first 16 should resolve
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 17; i++) {
            UUID id = UUID.randomUUID();
            dm.saveQuest(makeQuest(id, OWNER, "Quest" + i, Visibility.OPEN));
            content.append("[[Quest").append(i).append("]] ");
        }
        String resolved = resolver.resolveForRecipient(content.toString(), OWNER);
        // 17th link should remain as raw [[Quest16]]
        assertTrue(resolved.contains("[[Quest16]]"));
    }

    @Test
    void reverseResolve_uuidToTitle() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Nether Highway", Visibility.OPEN));

        String content = "See [[" + questId + "|Nether Highway]] for details";
        String reversed = resolver.reverseResolve(content, OWNER);
        assertEquals("See [[Nether Highway]] for details", reversed);
    }

    @Test
    void reverseResolve_privateQuestStaysAsIs() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Secret Base", Visibility.PRIVATE));

        String content = "See [[" + questId + "|Private Quest]] for details";
        String reversed = resolver.reverseResolve(content, OTHER);
        assertEquals("See [[" + questId + "|Private Quest]] for details", reversed);
    }

    @Test
    void reverseResolve_deletedQuestPreservesText() {
        UUID questId = UUID.randomUUID();
        String content = "See [[" + questId + "|Old Quest]] for details";
        String reversed = resolver.reverseResolve(content, OWNER);
        assertEquals("See [[Old Quest]] for details", reversed);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :server:test --tests "*.WikiLinkResolverTest"`
Expected: compilation failure (WikiLinkResolver doesn't exist)

- [ ] **Step 3: Add `findQuestsByTitles()` to DataManager**

```java
public synchronized Map<String, QuestData> findQuestsByTitlesIgnoreCase(List<String> titles) {
    if (titles.isEmpty()) return Map.of();
    Map<String, QuestData> result = new HashMap<>();
    String placeholders = titles.stream().map(t -> "?").collect(Collectors.joining(","));
    try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT q.*, pn.name as owner_name FROM quests q " +
            "LEFT JOIN player_names pn ON q.owner_uuid = pn.uuid " +
            "WHERE LOWER(q.title) IN (" + placeholders + ")")) {
        for (int i = 0; i < titles.size(); i++) {
            stmt.setString(i + 1, titles.get(i).toLowerCase());
        }
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            QuestData quest = mapQuestRow(rs);
            String lowerTitle = quest.title().toLowerCase();
            // UUID tiebreaker: keep lexicographically first UUID
            if (!result.containsKey(lowerTitle)
                    || quest.id().toString().compareTo(result.get(lowerTitle).id().toString()) < 0) {
                result.put(lowerTitle, quest);
            }
        }
    } catch (SQLException e) {
        throw new RuntimeException("Failed to find quests by titles", e);
    }
    return result;
}
```

Also add a method to check if a player can access a quest:

```java
public boolean canPlayerAccessQuest(UUID questId, UUID playerUuid) {
    QuestData quest = getQuest(questId);
    if (quest == null) return false;
    if (quest.ownerUuid().equals(playerUuid)) return true;
    if (quest.visibility() == Visibility.OPEN || quest.visibility() == Visibility.CLOSED) return true;
    return quest.contributors().stream().anyMatch(c -> c.uuid().equals(playerUuid));
}
```

- [ ] **Step 4: Implement WikiLinkResolver**

```java
package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiLinkResolver {

    private static final int MAX_WIKI_LINKS = 16;
    // Raw content: [[Quest Name]]
    private static final Pattern RAW_LINK = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");
    // Resolved content: [[uuid|title]]
    private static final Pattern RESOLVED_LINK = Pattern.compile("\\[\\[([^|\\]]*)\\|([^\\]]*)\\]\\]");

    private final DataManager dataManager;

    public WikiLinkResolver(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Resolve raw [[Quest Name]] links for a specific recipient. */
    public String resolveForRecipient(String content, UUID recipientUuid) {
        Matcher matcher = RAW_LINK.matcher(content);
        List<String> titles = new ArrayList<>();
        while (matcher.find() && titles.size() < MAX_WIKI_LINKS) {
            titles.add(matcher.group(1));
        }
        if (titles.isEmpty()) return content;

        Map<String, QuestData> questsByTitle = dataManager.findQuestsByTitlesIgnoreCase(titles);

        matcher.reset();
        StringBuilder result = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            if (count >= MAX_WIKI_LINKS) break; // leave excess as raw
            count++;
            String title = matcher.group(1);
            QuestData quest = questsByTitle.get(title.toLowerCase());
            String replacement;
            if (quest == null) {
                replacement = "[[|" + title + "]]";
            } else if (canAccess(quest, recipientUuid)) {
                replacement = "[[" + quest.id() + "|" + quest.title() + "]]";
            } else {
                replacement = "[[" + quest.id() + "|Private Quest]]";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Reverse-resolve [[uuid|title]] back to [[Quest Name]] for storage. */
    public String reverseResolve(String content, UUID senderUuid) {
        Matcher matcher = RESOLVED_LINK.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String uuidStr = matcher.group(1);
            String displayName = matcher.group(2);
            if (uuidStr.isEmpty()) {
                // Broken link -- store with raw title
                matcher.appendReplacement(result, Matcher.quoteReplacement("[[" + displayName + "]]"));
                continue;
            }
            try {
                UUID questId = UUID.fromString(uuidStr);
                QuestData quest = dataManager.getQuest(questId);
                if (quest == null) {
                    // Deleted -- preserve displayed text
                    matcher.appendReplacement(result, Matcher.quoteReplacement("[[" + displayName + "]]"));
                } else if (canAccess(quest, senderUuid)) {
                    // Sender can see it -- store canonical title
                    matcher.appendReplacement(result, Matcher.quoteReplacement("[[" + quest.title() + "]]"));
                } else {
                    // Sender can't access -- leave as-is (don't leak title)
                    matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                }
            } catch (IllegalArgumentException e) {
                // Bad UUID -- treat as broken
                matcher.appendReplacement(result, Matcher.quoteReplacement("[[" + displayName + "]]"));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private boolean canAccess(QuestData quest, UUID playerUuid) {
        if (quest.ownerUuid().equals(playerUuid)) return true;
        if (quest.visibility() == Visibility.OPEN || quest.visibility() == Visibility.CLOSED) return true;
        return quest.contributors().stream().anyMatch(c -> c.uuid().equals(playerUuid));
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :server:test --tests "*.WikiLinkResolverTest"`
Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add server/
git commit -m "feat(server): add WikiLinkResolver with per-recipient access control"
```

---

### Task 11: Wire WikiLinkResolver into ServerPacketHandler

**Files:**
- Modify: `server/src/main/java/com/disqt/disquests/server/papermc/ServerPacketHandler.java`

- [ ] **Step 1: Add WikiLinkResolver field and initialization**

Add to `ServerPacketHandler`:

```java
private final WikiLinkResolver wikiLinkResolver;
```

Initialize in constructor (after `dataManager`):

```java
this.wikiLinkResolver = new WikiLinkResolver(dataManager);
```

- [ ] **Step 2: Resolve wiki-links before sending quests**

Find all places where quests are sent to players (SYNC_MY_QUESTS, SYNC_SERVER_QUESTS, UPDATE_QUEST). Before serializing, resolve wiki-links:

Create a helper:

```java
private QuestData resolveWikiLinks(QuestData quest, UUID recipientUuid) {
    String resolvedContent = wikiLinkResolver.resolveForRecipient(quest.content(), recipientUuid);
    if (resolvedContent.equals(quest.content())) return quest;
    return new QuestData(
            quest.id(), quest.title(), resolvedContent,
            quest.ownerUuid(), quest.ownerName(), quest.visibility(),
            quest.contributors(), quest.lastModified(),
            quest.coordinates(), quest.isRegion(), quest.coordinates2(), quest.map(),
            quest.tags());
}
```

Apply before each `writeQuest` / `writeSyncMyQuests` / `writeSyncServerQuests` / `writeUpdateQuest` call. For list syncs, map each quest through `resolveWikiLinks`.

- [ ] **Step 3: Reverse-resolve wiki-links on save**

In `handleSaveQuest()`, before constructing the `QuestData` to save:

```java
String content = wikiLinkResolver.reverseResolve(payload.content(), playerUuid);
```

Use `content` (not `payload.content()`) when constructing the `QuestData`.

- [ ] **Step 4: Build server**

Run: `./gradlew :server:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(server): wire WikiLinkResolver into packet handling"
```

---

### Task 12: Client-side wiki-link rendering in MarkdownRenderer

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java`

- [ ] **Step 1: Add pre-processing for `[[uuid|title]]` to `<dqlink>`**

Add a static method:

```java
private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^|\\]]*)\\|([^\\]]*)\\]\\]");

private static String preprocessWikiLinks(String content) {
    return WIKI_LINK_PATTERN.matcher(content).replaceAll(m -> {
        String uuid = m.group(1);
        String title = m.group(2);
        // Escape HTML special chars in title
        title = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        return "<dqlink uuid=\"" + uuid + "\" title=\"" + title + "\"/>";
    });
}
```

Call this at the start of the main render method, before passing to commonmark:

```java
content = preprocessWikiLinks(content);
```

- [ ] **Step 2: Handle HtmlInline nodes in MarkdownRenderer.appendInline()**

In `appendInline()` (line ~232, before the `else` fallback), add a case for `HtmlInline`:

```java
} else if (node instanceof org.commonmark.node.HtmlInline htmlInline) {
    String literal = htmlInline.getLiteral();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "uuid=\"([^\"]*)\"\\s+title=\"([^\"]*)\"").matcher(literal);
    if (m.find()) {
        String uuid = m.group(1);
        String title = m.group(2).replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"");
        boolean isValid = !uuid.isEmpty();
        int color = isValid ? 0xe8a86d : 0xe86d6d; // amber or red
        Style wikiStyle = style.withColor(color).withUnderline(true);
        if (!isValid) wikiStyle = wikiStyle.withStrikethrough(true);
        target.append(Text.literal(title).setStyle(wikiStyle));
    }
}
```

- [ ] **Step 3: Add WikiLinkHitbox to MarkdownWidget for click handling**

The existing `MarkdownWidget` uses `LinkHitbox` records for URL clicks. Add a parallel system for wiki-links.

In `MarkdownWidget.java`, add:

```java
private record WikiLinkHitbox(int x, int y, int width, int height, String uuid) {}
private final List<WikiLinkHitbox> wikiLinkHitboxes = new ArrayList<>();
```

In the rendering loop (where `linkHitboxes` are populated from `ClickEvent.OpenUrl`), also detect wiki-link styled text. The wiki-link text uses color `0xFFe8a86d` (amber) -- use this as the signal to create a `WikiLinkHitbox`. Alternatively, add a custom marker to the `Style` (e.g., via `ClickEvent.RunCommand` with a `disquests:wikilink:UUID` pseudo-command).

**Recommended approach:** Use `ClickEvent.RunCommand` with prefix `/disquests:wikilink:` as the marker:

In `MarkdownRenderer.appendInline()`, when creating valid wiki-link text:
```java
Style wikiStyle = style.withColor(0xe8a86d).withUnderline(true)
        .withClickEvent(new ClickEvent.RunCommand("/disquests:wikilink:" + uuid));
```

For broken/private links:
```java
Style wikiStyle = style.withColor(0xe86d6d).withUnderline(true).withStrikethrough(true)
        .withClickEvent(new ClickEvent.RunCommand("/disquests:wikilink:broken"));
```

In `MarkdownWidget`, when building hitboxes, detect the `/disquests:wikilink:` prefix:

```java
} else if (clickEvent instanceof ClickEvent.RunCommand runCmd
        && runCmd.command().startsWith("/disquests:wikilink:")) {
    String uuidStr = runCmd.command().substring("/disquests:wikilink:".length());
    wikiLinkHitboxes.add(new WikiLinkHitbox(drawX, drawY, textWidth, textRenderer.fontHeight, uuidStr));
}
```

In `onMouseDown()`, add wiki-link click handling after the existing link hitbox loop:

```java
for (WikiLinkHitbox wh : wikiLinkHitboxes) {
    if (hitTest(mx, my, wh.x(), wh.y(), wh.width(), wh.height())) {
        if ("broken".equals(wh.uuid())) {
            ClientSession.setPendingToast("This quest is private or no longer exists");
            return true;
        }
        try {
            UUID questId = UUID.fromString(wh.uuid());
            Quest quest = ClientCache.getQuestById(questId);
            if (quest != null) {
                MinecraftClient.getInstance().setScreen(
                        new QuestScreen(MinecraftClient.getInstance().currentScreen, quest));
            } else {
                ClientSession.setPendingToast("This quest is private or no longer exists");
            }
        } catch (IllegalArgumentException e) {
            ClientSession.setPendingToast("This quest is private or no longer exists");
        }
        return true;
    }
}
```

- [ ] **Step 3: Build client**

Run: `./gradlew :client:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java
git commit -m "feat(client): render wiki-links in markdown with access-aware styling"
```

---

### Task 13: Wiki-link autocomplete in editor

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/component/AutocompleteDropdown.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/TextFieldComponent.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`

- [ ] **Step 1: Create AutocompleteDropdown component**

```java
package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.data.Quest;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AutocompleteDropdown {

    private static final int MAX_RESULTS = 5;
    private static final int ITEM_HEIGHT = 12;
    private static final int BG_COLOR = 0xEE1a1a2e;
    private static final int HOVER_COLOR = 0xEE3a3a5e;
    private static final int TEXT_COLOR = 0xFFe0e0e0;
    private static final int OWNER_COLOR = 0xFF888888;

    private boolean visible = false;
    private int selectedIndex = 0;
    private List<Quest> results = List.of();
    private int x, y;
    private Consumer<String> onSelect;

    public void update(String query, int cursorX, int cursorY) {
        if (query == null || query.isEmpty()) {
            hide();
            return;
        }
        String lowerQuery = query.toLowerCase();
        List<Quest> allQuests = new ArrayList<>();
        allQuests.addAll(ClientCache.getMyQuests());
        allQuests.addAll(ClientCache.getServerQuests());
        results = allQuests.stream()
                .filter(q -> q.getTitle().toLowerCase().startsWith(lowerQuery))
                .limit(MAX_RESULTS)
                .toList();
        if (results.isEmpty()) {
            hide();
            return;
        }
        this.x = cursorX;
        this.y = cursorY + 10;
        this.selectedIndex = 0;
        this.visible = true;
    }

    public void hide() { visible = false; results = List.of(); }
    public boolean isVisible() { return visible; }

    public void setOnSelect(Consumer<String> onSelect) { this.onSelect = onSelect; }

    public boolean onKeyDown(int keyCode) {
        if (!visible) return false;
        if (keyCode == 264) { // DOWN
            selectedIndex = Math.min(selectedIndex + 1, results.size() - 1);
            return true;
        } else if (keyCode == 265) { // UP
            selectedIndex = Math.max(selectedIndex - 1, 0);
            return true;
        } else if (keyCode == 257 || keyCode == 335) { // ENTER or KP_ENTER
            selectCurrent();
            return true;
        } else if (keyCode == 256) { // ESCAPE
            hide();
            return true;
        }
        return false;
    }

    private void selectCurrent() {
        if (selectedIndex < results.size() && onSelect != null) {
            onSelect.accept(results.get(selectedIndex).getTitle());
        }
        hide();
    }

    public void draw(OwoUIGraphics context) {
        if (!visible || results.isEmpty()) return;
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int width = 150;
        int height = results.size() * ITEM_HEIGHT;

        context.fill(x, y, x + width, y + height, BG_COLOR);
        for (int i = 0; i < results.size(); i++) {
            int itemY = y + i * ITEM_HEIGHT;
            if (i == selectedIndex) {
                context.fill(x, itemY, x + width, itemY + ITEM_HEIGHT, HOVER_COLOR);
            }
            Quest q = results.get(i);
            String display = textRenderer.trimToWidth(q.getTitle(), width - 4);
            context.drawText(textRenderer, display, x + 2, itemY + 2, TEXT_COLOR, false);
        }
    }
}
```

- [ ] **Step 2: Integrate autocomplete into TextFieldComponent**

Add an `AutocompleteDropdown` field to `TextFieldComponent`. In `onCharTyped()` and `onKeyPress()`:

1. After forwarding to delegate, check if text before cursor contains `[[` without closing `]]`
2. If so, extract query and call `dropdown.update(query, cursorX, cursorY)`
3. In `onKeyPress()`, check `dropdown.onKeyDown()` first -- if it returns true, don't forward to delegate
4. In `draw()`, call `dropdown.draw()` after rendering the text field
5. On select callback: insert the title + `]]` at cursor position

- [ ] **Step 3: Add `[[Quest]]` to formatting hints in quest_screen_edit.xml**

After the `fmt-link` label (line 126), add:

```xml
<label id="fmt-wikilink">
    <text>[[Quest Name]] = quest link</text>
    <shadow>true</shadow>
</label>
```

- [ ] **Step 4: Style the wikilink hint in QuestScreen.java**

After the existing `fmtLink` styling (line ~344), add:

```java
LabelComponent fmtWikiLink = root.childById(LabelComponent.class, "fmt-wikilink");
if (fmtWikiLink != null) fmtWikiLink.text(Text.literal("[[Quest Name]]: ")
        .append(Text.literal("quest link").styled(s -> s.withUnderline(true).withColor(0xe8a86d))));
```

- [ ] **Step 5: Build client**

Run: `./gradlew :client:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add client/
git commit -m "feat(client): add wiki-link autocomplete dropdown and formatting hint"
```

---

### Task 14: E2E tests for wiki-links

**Files:**
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/WikiLinkJourney.java`

- [ ] **Step 1: Write wiki-link E2E journey**

Test scenarios:
1. Create two quests (Quest A and Quest B)
2. Edit Quest A, type `[[Quest B]]` in content, save
3. View Quest A -- verify wiki-link renders as styled amber text
4. Click the wiki-link -- verify it navigates to Quest B
5. Create a PRIVATE Quest C as a different concept
6. Verify that in the rendered view, inaccessible links show "Private Quest" placeholder

- [ ] **Step 2: Verify testmod compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the E2E test**

Run: `./gradlew :client:runIntegrationTest -PtestFilter=WikiLinkJourney`
Expected: all steps PASS

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/
git commit -m "test: add E2E journey for wiki-link creation, rendering, and navigation"
```

---

### Task 15: Final integration test run

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew :common:test :server:test`
Expected: all PASS

- [ ] **Step 2: Run full E2E suite**

Run: `./gradlew :client:runIntegrationTest`
Expected: all journeys PASS (existing + new TagJourney + WikiLinkJourney)

- [ ] **Step 3: Fix any regressions**

If existing tests fail due to the QuestData constructor change or other cascading changes, fix them.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "fix: resolve any test regressions from tags and wiki-links"
```
