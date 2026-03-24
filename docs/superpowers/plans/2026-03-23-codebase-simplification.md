# Codebase Simplification Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate duplication, magic numbers, and code smells across all three modules while keeping E2E tests green.

**Architecture:** Mechanical refactoring only -- extract helpers, consolidate duplicates, add constants/enums, fix thread safety. No structural rewrites (no god-class splits, no static-to-instance conversion).

**Tech Stack:** Java 21, Fabric 1.21.11, PaperMC, owo-ui 0.13.0, SQLite

---

### Task 1: Common Module Cleanup

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketType.java`
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketCodec.java`

- [ ] **Step 1: PacketType.fromId() -- array lookup instead of linear scan**

Replace the `for` loop in `fromId()` with a static lookup array:

```java
private static final PacketType[] BY_ID;
static {
    BY_ID = new PacketType[256];
    for (PacketType type : values()) {
        BY_ID[type.id & 0xFF] = type;
    }
}

public static PacketType fromId(byte id) {
    PacketType type = BY_ID[id & 0xFF];
    if (type == null) {
        throw new IllegalArgumentException("Unknown packet type: 0x" + String.format("%02X", id));
    }
    return type;
}
```

- [ ] **Step 2: PacketCodec.readEnum() -- cache enum constants**

Add a static cache to avoid `getEnumConstants()` reflection allocation on every call:

```java
private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Object[]> ENUM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

@SuppressWarnings("unchecked")
private static <T extends Enum<T>> T readEnum(ByteBufReader buf, Class<T> enumClass) {
    int ordinal = buf.readVarInt();
    T[] values = (T[]) ENUM_CACHE.computeIfAbsent(enumClass, Class::getEnumConstants);
    if (ordinal < 0 || ordinal >= values.length) {
        throw new IllegalArgumentException(
                "Invalid " + enumClass.getSimpleName() + " ordinal: " + ordinal);
    }
    return values[ordinal];
}
```

- [ ] **Step 3: Extract readQuestList() -- deduplicate readSyncMyQuests/readSyncServerQuests**

Both methods are identical. Extract:

```java
private static List<QuestData> readQuestList(ByteBufReader buf) {
    int count = readCount(buf, MAX_QUEST_LIST, "Quest");
    List<QuestData> quests = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
        quests.add(readQuest(buf));
    }
    return quests;
}
```

Then `readSyncMyQuests` and `readSyncServerQuests` both delegate to it.

- [ ] **Step 4: writeSyncMyQuests -- one-arg delegates to two-arg**

Replace the one-arg overload body with: `return writeSyncMyQuests(quests, Map.of());`

- [ ] **Step 5: Run unit tests**

Run: `./gradlew :common:test`

- [ ] **Step 6: Commit**

```
refactor(common): optimize PacketType lookup, cache enum constants, deduplicate codec methods
```

---

### Task 2: Base Screen Consolidation + Quick Fixes

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ConfirmScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ConfigScreen.java`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientCache.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java`

- [ ] **Step 1: Move shouldPause() into DisquestsBaseScreen**

Add to `DisquestsBaseScreen`:
```java
@Override
public boolean shouldPause() {
    return false;
}
```

Remove `shouldPause()` from: MainScreen (line 466-468), QuestScreen (line 714-717), ContributorScreen (line 138-141), ConfirmScreen (line 39-42), ConfigScreen (line 97-100).

- [ ] **Step 2: Add navigateToScreen() helper in DisquestsBaseScreen**

```java
protected void navigateToScreen(Screen screen) {
    if (this.client != null) {
        this.client.setScreen(screen);
    }
}
```

Replace all `if (this.client != null) { this.client.setScreen(...); }` patterns across QuestScreen and ContributorScreen with `navigateToScreen(...)`. There are ~14 call sites.

**IMPORTANT:** Do NOT replace `this.client.setScreen(...)` calls where `this.client` is already known non-null (e.g. inside `createNewQuest()` in MainScreen where `this.client` is used for other things too). Only replace the guarded `if (this.client != null)` pattern.

- [ ] **Step 3: CHANNEL constant dedup in ServerPacketHandler**

Replace `private static final String CHANNEL = "disquests:main";` in ServerPacketHandler with a reference: use `DisquestsPlugin.CHANNEL`.

- [ ] **Step 4: Remove empty onPlayerQuit handler**

Delete the `onPlayerQuit` method (lines 71-74) in ServerPacketHandler.

- [ ] **Step 5: ClientCache.clear() -- bump version**

Add `version++;` (or `version.incrementAndGet()` after Task 6) at the end of `clear()`.

- [ ] **Step 6: Gate debug event subscriptions in QuestEntryComponent**

Wrap the `mouseDown().subscribe(...)` and `mouseEnter().subscribe(...)` calls (lines 148-155) in:
```java
if (LOGGER.isDebugEnabled()) { ... }
```

- [ ] **Step 7: Fix empty catch blocks**

In `QuestScreen.buildViewMode()` line 194: change `catch (Exception ignored) {}` to log a warning.
In `ClientPacketHandler.handleRawPayload()` lines 28-29: log a warning instead of silently returning.

- [ ] **Step 8: Move inline logger to class field in ClientPacketHandler**

Replace the inline `org.slf4j.LoggerFactory.getLogger("Disquests")` on line 46 with a class-level field:
```java
private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Disquests.ClientPacketHandler");
```

- [ ] **Step 9: Cache "Last Modified: " string in QuestEntryComponent**

In the constructor, cache `"Last Modified: " + formattedDateTime` as a field instead of concatenating every frame in `draw()` (line 303).

- [ ] **Step 10: Verify compilation**

Run: `./gradlew :client:compileTestmodJava :paper:classes`

- [ ] **Step 11: Commit**

```
refactor(client,paper): consolidate base screen, fix quick code smells
```

---

### Task 3: Quest Permission Helpers + Deduplication

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/data/Quest.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java`

- [ ] **Step 1: Add permission helpers to Quest**

```java
public boolean isOwner(UUID playerUuid) {
    return playerUuid != null && playerUuid.equals(ownerUuid);
}

public boolean isContributor(UUID playerUuid) {
    return contributors != null && contributors.stream()
            .anyMatch(c -> c.getUuid().equals(playerUuid));
}

public boolean canEdit(UUID playerUuid) {
    return isOwner(playerUuid) || (contributors != null && contributors.stream()
            .anyMatch(c -> c.getUuid().equals(playerUuid) && c.canEdit()));
}

public boolean isContentHidden(UUID playerUuid) {
    return visibility == Visibility.CLOSED && !isOwner(playerUuid) && !isContributor(playerUuid);
}
```

- [ ] **Step 2: Use permission helpers in QuestScreen.build()**

Replace lines 119-125:
```java
this.isOwner = quest.isOwner(myUuid);
this.canEdit = quest.canEdit(myUuid);
this.isContributor = quest.isContributor(myUuid);
this.hideContent = quest.isContentHidden(myUuid);
```

- [ ] **Step 3: Use permission helpers in QuestEntryComponent**

Replace lines 85-90:
```java
this.isOwnedByPlayer = quest.isOwner(playerUuid);
this.isContributor = quest.isContributor(playerUuid);
this.hideContent = quest.isContentHidden(playerUuid);
```

- [ ] **Step 4: Use permission helpers in ClientPacketHandler.handleUpdateQuest()**

Replace lines 94-95:
```java
boolean isMine = quest.isOwner(myUuid) || quest.isContributor(myUuid);
```

(Note: this uses the Quest wrapper, not QuestData, since `quest` is already created on line 92.)

- [ ] **Step 5: Extract coordinate parsing helper in QuestScreen**

Add private method:
```java
private CoordinatesData parseCoordinates(TextFieldComponent xComp, TextFieldComponent yComp, TextFieldComponent zComp) {
    if (xComp == null || yComp == null || zComp == null) return null;
    try {
        double x = Double.parseDouble(xComp.getText().trim());
        double y = Double.parseDouble(yComp.getText().trim());
        double z = Double.parseDouble(zComp.getText().trim());
        return new CoordinatesData(x, y, z);
    } catch (NumberFormatException e) {
        if (xComp.getText().trim().isEmpty()
                && yComp.getText().trim().isEmpty()
                && zComp.getText().trim().isEmpty()) {
            return null;
        }
        return quest.getCoordinates(); // preserve existing on partial input
    }
}
```

Replace the duplicated blocks in `persistFieldValues()` (lines 630-657) with:
```java
CoordinatesData parsed1 = parseCoordinates(coordXComponent, coordYComponent, coordZComponent);
if (coordXComponent != null) quest.setCoordinates(parsed1);

CoordinatesData parsed2 = parseCoordinates(coord2XComponent, coord2YComponent, coord2ZComponent);
if (coord2XComponent != null) quest.setCoordinates2(parsed2);
```

Note: The "preserve existing" fallback for corner 1 uses `quest.getCoordinates()`, for corner 2 uses `quest.getCoordinates2()`. The helper needs a parameter for this or the caller handles it.

- [ ] **Step 6: Deduplicate code block rendering in MarkdownRenderer**

Extract:
```java
private static void renderCodeLines(String literal, List<RenderedLine> lines, int indent) {
    String[] codeLines = literal.split("\n", -1);
    for (String cl : codeLines) {
        lines.add(RenderedLine.normal(
                Text.literal(cl).setStyle(Style.EMPTY.withColor(Formatting.GRAY)), indent + 8));
    }
}
```

Replace FencedCodeBlock (lines 160-165) and IndentedCodeBlock (lines 166-171) with calls to `renderCodeLines(code.getLiteral(), lines, indent)`.

- [ ] **Step 7: Deduplicate toast + refresh in ClientPacketHandler**

Extract:
```java
private static void showOrDeferToast(String message) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.currentScreen instanceof MainScreen mainScreen) {
        mainScreen.refreshListContents();
        mainScreen.showToast(message);
    } else {
        ClientSession.setPendingToast(message);
    }
}
```

Replace duplicated blocks in `handleUpdateQuest()` (lines 111-119) and `handleCollaborationResponse()` (lines 150-157).

- [ ] **Step 8: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`

- [ ] **Step 9: Commit**

```
refactor(client): extract permission helpers, deduplicate coord parsing, toast, and markdown rendering
```

---

### Task 4: Enums and Constants

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientSession.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/Colors.java`

- [ ] **Step 1: Create Tab enum in ClientSession**

```java
public enum Tab {
    MY_QUESTS, SERVER_QUESTS;

    public int index() { return ordinal(); }
}
```

Replace `activeTab` (int) with `Tab`, update `getActiveTab()`/`setActiveTab()` signatures. Update MainScreen to use `Tab.MY_QUESTS`/`Tab.SERVER_QUESTS` instead of `TAB_MY_QUESTS`/`TAB_SERVER_QUESTS` constants.

- [ ] **Step 2: Create QuestFilter enum in ClientSession**

```java
public enum QuestFilter {
    ALL, OPEN, CLOSED
}
```

Replace `serverQuestsFilter` (int) with `QuestFilter`, update getter/setter. Update MainScreen to use enum instead of `FILTER_ALL`/`FILTER_OPEN`/`FILTER_CLOSED` constants.

- [ ] **Step 3: Add map name constants in QuestScreen**

```java
private static final String MAP_OVERWORLD = "overworld";
private static final String MAP_NETHER = "the_nether";
private static final String MAP_END = "the_end";
```

Use in `cycleMap()` switch.

- [ ] **Step 4: Add badge color constant**

In `Colors.java`, add:
```java
public static int BADGE_RED = 0xFFCC3333;
```

Use in `MainScreen.renderNotificationBadge()` and `InventoryBadgeMixin`.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`

- [ ] **Step 6: Commit**

```
refactor(client): replace magic ints with Tab/QuestFilter enums and named constants
```

---

### Task 5: Thread Safety and Data Structures

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientSession.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientCache.java`

- [ ] **Step 1: pinnedQuestIds -- ArrayList to CopyOnWriteArrayList**

Replace `private static final List<UUID> pinnedQuestIds = new ArrayList<>()` with `private static final List<UUID> pinnedQuestIds = new java.util.concurrent.CopyOnWriteArrayList<>()`.

This gives thread-safe iteration (render thread) and modification (network thread) without changing the API. Keep it as List (not Set) to preserve insertion order for HUD display. The `contains()` check in `isPinned()` is still O(n) but the list is typically <5 items, and CopyOnWriteArrayList makes it thread-safe.

- [ ] **Step 2: requestedQuestIds -- HashSet to ConcurrentHashMap.newKeySet()**

Replace `private static final Set<UUID> requestedQuestIds = new HashSet<>()` with `private static final Set<UUID> requestedQuestIds = ConcurrentHashMap.newKeySet()`.

- [ ] **Step 3: ClientCache.version -- volatile long to AtomicLong**

Replace `private static volatile long version = 0` with `private static final java.util.concurrent.atomic.AtomicLong version = new java.util.concurrent.atomic.AtomicLong()`.

Update `getVersion()` to `version.get()`, all `version++` to `version.incrementAndGet()`.

- [ ] **Step 4: ClientCache.clear() -- bump version (if not done in Task 2)**

Ensure `clear()` calls `version.incrementAndGet()`.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`

- [ ] **Step 6: Commit**

```
refactor(client): improve thread safety with CopyOnWriteArrayList, ConcurrentHashMap, AtomicLong
```

---

### Task 6: DataManager N+1 Query Fix

**Files:**
- Modify: `paper/src/main/java/com/disqt/disquests/paper/DataManager.java`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java`

- [ ] **Step 1: Batch contributor loading in DataManager**

Add a new method that loads contributors for multiple quests in one query:

```java
private Map<UUID, List<ContributorData>> getContributorsBatch(List<UUID> questIds) {
    if (questIds.isEmpty()) return Map.of();
    Map<UUID, List<ContributorData>> result = new HashMap<>();
    // Initialize empty lists for all quest IDs
    for (UUID id : questIds) {
        result.put(id, new ArrayList<>());
    }
    String placeholders = String.join(",", questIds.stream().map(id -> "?").toList());
    String sql = "SELECT c.quest_id, c.player_uuid, c.can_edit, pn.name FROM contributors c " +
                 "LEFT JOIN player_names pn ON c.player_uuid = pn.uuid " +
                 "WHERE c.quest_id IN (" + placeholders + ")";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        for (int i = 0; i < questIds.size(); i++) {
            stmt.setString(i + 1, questIds.get(i).toString());
        }
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            UUID questId = UUID.fromString(rs.getString("quest_id"));
            result.computeIfAbsent(questId, k -> new ArrayList<>()).add(new ContributorData(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("name"),
                rs.getInt("can_edit") != 0
            ));
        }
    } catch (SQLException e) {
        throw new RuntimeException("Failed to batch-load contributors", e);
    }
    return result;
}
```

- [ ] **Step 2: Replace withContributorsAll() to use batch loading**

```java
private List<QuestData> withContributorsAll(List<QuestData> quests) {
    List<UUID> questIds = quests.stream().map(QuestData::id).toList();
    Map<UUID, List<ContributorData>> contributorMap = getContributorsBatch(questIds);
    List<QuestData> result = new ArrayList<>(quests.size());
    for (QuestData quest : quests) {
        List<ContributorData> contributors = contributorMap.getOrDefault(quest.id(), List.of());
        result.add(new QuestData(
            quest.id(), quest.title(), quest.content(),
            quest.ownerUuid(), quest.ownerName(), quest.visibility(),
            contributors, quest.lastModified(),
            quest.coordinates(), quest.isRegion(), quest.coordinates2(), quest.map()
        ));
    }
    return result;
}
```

- [ ] **Step 3: Reduce redundant getQuest() calls in ServerPacketHandler**

In `handleSaveQuest`: Cache the result of `getQuest()` after save (line 109/126) instead of calling it separately for new vs existing paths. The `existing` variable already serves as the pre-check.

In `handleUpdateVisibility`: Call `getQuest()` once after the `updateVisibility()` call, not before and after.

In `handleUpdateContributors`: Call `getQuest()` once at the end (line 271) after all ops, not inside the ADD case (line 242) or REMOVE case (line 256). For the per-player notifications inside the loop, pass the quest data that will be fetched once after the loop.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :paper:classes`

- [ ] **Step 5: Commit**

```
perf(paper): batch contributor loading, reduce redundant DB queries in packet handlers
```

---

### Task 7: Remaining Small Fixes

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfig.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/ColorConfig.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/migration/BuildNotesMigrator.java`

- [ ] **Step 1: Share Gson instance**

In `DisquestsConfig.java`, change the private `GSON` field to `static final` (package-visible or public):
```java
static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
```

In `ColorConfig.java`, replace `private static final Gson GSON = ...` with a reference to `DisquestsConfig.GSON`.

- [ ] **Step 2: Fix charset in ColorConfig**

Replace `new FileReader(configFile)` with `Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)`.
Replace `new FileWriter(configFile)` with `Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)`.

- [ ] **Step 3: Fix double Files.size() call in BuildNotesMigrator**

Store `Files.size(file)` in a local variable and reuse.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :client:compileTestmodJava`

- [ ] **Step 5: Commit**

```
refactor(client): share Gson instance, fix charset handling, eliminate redundant I/O
```

---

### Task 8: E2E Test Verification

- [ ] **Step 1: Run full E2E test suite**

Run: `./gradlew :client:runIntegrationTest`

- [ ] **Step 2: Fix any failures**

If tests fail, analyze the failure and fix. The most likely failure points are:
- Quest permission helper changes affecting E2E behavior
- Tab/Filter enum changes if test code references the int constants
- navigateToScreen() changes if any screen transition timing changes

- [ ] **Step 3: Run common unit tests as final check**

Run: `./gradlew :common:test`

- [ ] **Step 4: Final commit if any test fixes needed**

```
fix: resolve E2E test failures from refactoring
```
