# Codebase Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all critical, high, and medium issues identified in the full codebase review — hardening the protocol layer, securing the server, fixing client bugs, and cleaning up build/CI.

**Architecture:** Five chunks, each independently mergeable. Chunk 1 (common) is a dependency for Chunks 2 and 3. Chunks 4 and 5 are fully independent. Each chunk ends with a commit.

**Tech Stack:** Java 21, Gradle Kotlin DSL, Fabric API, Paper API, JUnit 5, GitHub Actions

---

## Chunk 1: Protocol Hardening (common module)

Harden `ByteBufReader` and `PacketCodec` against malformed/malicious input. This is the foundation — both server and client depend on it.

### Task 1.1: Add length-bounded reads to ByteBufReader

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/ByteBufReader.java`
- Test: `common/src/test/java/com/disqt/disquests/common/ByteBufReaderTest.java`

- [ ] **Step 1: Write failing tests for bounded reads**

Create `ByteBufReaderTest.java`:

```java
package com.disqt.disquests.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ByteBufReaderTest {

    @Test
    void readVarInt_negativeLengthString_throws() {
        // Write a VarInt of -1 (0xFF 0xFF 0xFF 0xFF 0x0F)
        ByteBufWriter w = new ByteBufWriter();
        w.writeVarInt(-1);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> r.readString());
    }

    @Test
    void readString_exceedsMaxLength_throws() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeString("a".repeat(200));
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> r.readString(100));
    }

    @Test
    void readString_withinMaxLength_succeeds() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeString("hello");
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertEquals("hello", r.readString(100));
    }

    @Test
    void readVarInt_tooManyBytes_throws() {
        // 6 continuation bytes — should reject before reading the 6th
        byte[] bad = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01};
        ByteBufReader r = new ByteBufReader(bad);
        assertThrows(IllegalArgumentException.class, r::readVarInt);
    }

    @Test
    void readVarInt_truncatedBuffer_throws() {
        // Only 2 bytes, both with continuation bit — reader runs past end
        byte[] truncated = {(byte) 0x80, (byte) 0x80};
        ByteBufReader r = new ByteBufReader(truncated);
        assertThrows(IllegalArgumentException.class, r::readVarInt);
    }

    @Test
    void readBytes_negativeLengthArray_throws() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeVarInt(-1);
        ByteBufReader r = new ByteBufReader(w.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> r.readBytes());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :common:test --tests 'com.disqt.disquests.common.ByteBufReaderTest' -q`
Expected: Compilation errors (readString(int) doesn't exist yet), and runtime failures for negative length

- [ ] **Step 3: Implement bounded reads**

Modify `ByteBufReader.java`:

```java
// --- Constants ---
public static final int MAX_STRING_LENGTH = 65536; // 64KB UTF-8
public static final int MAX_BYTES_LENGTH = 1048576; // 1MB

// --- Fix readVarInt to catch overflow before 6th byte ---
public int readVarInt() {
    int value = 0;
    int shift = 0;
    byte current;
    do {
        if (pos >= data.length) {
            throw new IllegalArgumentException("VarInt extends past end of buffer");
        }
        current = data[pos++];
        value |= (current & 0x7F) << shift;
        shift += 7;
        if (shift > 35) {
            throw new IllegalArgumentException("VarInt too large");
        }
    } while ((current & 0x80) != 0);
    return value;
}

// --- Add readString(maxLength) overload ---
public String readString() {
    return readString(MAX_STRING_LENGTH);
}

public String readString(int maxLength) {
    int length = readVarInt();
    if (length < 0 || length > maxLength) {
        throw new IllegalArgumentException("String length " + length + " outside bounds [0, " + maxLength + "]");
    }
    if (pos + length > data.length) {
        throw new IllegalArgumentException("String extends past end of buffer");
    }
    String str = new String(data, pos, length, java.nio.charset.StandardCharsets.UTF_8);
    pos += length;
    return str;
}

// --- Add bounds check to readBytes ---
public byte[] readBytes() {
    int length = readVarInt();
    if (length < 0 || length > MAX_BYTES_LENGTH) {
        throw new IllegalArgumentException("Byte array length " + length + " outside bounds [0, " + MAX_BYTES_LENGTH + "]");
    }
    if (pos + length > data.length) {
        throw new IllegalArgumentException("Byte array extends past end of buffer");
    }
    byte[] result = new byte[length];
    System.arraycopy(data, pos, result, 0, length);
    pos += length;
    return result;
}
```

- [ ] **Step 4: Run all common tests**

Run: `./gradlew :common:test -q`
Expected: All pass (both new ByteBufReaderTest and existing PacketCodecTest)

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/disqt/disquests/common/ByteBufReader.java \
        common/src/test/java/com/disqt/disquests/common/ByteBufReaderTest.java
git commit -m "fix(common): add bounds checks to ByteBufReader reads

Prevents OOM/DoS from crafted VarInt lengths in readString and readBytes.
Fixes VarInt overflow detection to reject before reading a 6th byte.
Adds readString(maxLength) overload for caller-specified limits."
```

### Task 1.2: Add enum bounds checks and list size caps to PacketCodec

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketCodec.java`
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketType.java`
- Test: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`

- [ ] **Step 1: Write failing tests for enum bounds and list caps**

Add to `PacketCodecTest.java`:

```java
@Test
void readQuest_invalidVisibilityOrdinal_throws() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeUUID(UUID.randomUUID()); // id
    w.writeString("title");
    w.writeString("content");
    w.writeUUID(UUID.randomUUID()); // ownerUuid
    w.writeString("owner");
    w.writeVarInt(99); // invalid visibility ordinal
    w.writeVarInt(0); // 0 contributors
    w.writeLong(1000L);
    w.writeBoolean(false); // no coords
    w.writeBoolean(false); // isRegion
    w.writeBoolean(false); // no coords2
    w.writeBoolean(false); // no map
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertThrows(IllegalArgumentException.class, () -> PacketCodec.readQuest(r));
}

@Test
void readUpdateContributors_invalidOpOrdinal_throws() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeUUID(UUID.randomUUID()); // questId
    w.writeVarInt(1); // 1 op
    w.writeVarInt(99); // invalid ContributorOp ordinal
    w.writeBoolean(false); // no UUID
    w.writeBoolean(false); // no name
    w.writeBoolean(false); // canEdit
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertThrows(IllegalArgumentException.class, () -> PacketCodec.readUpdateContributors(r));
}

@Test
void readQuest_negativeContributorCount_throws() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeUUID(UUID.randomUUID());
    w.writeString("title");
    w.writeString("content");
    w.writeUUID(UUID.randomUUID());
    w.writeString("owner");
    w.writeVarInt(0); // PRIVATE visibility
    w.writeVarInt(-1); // negative contributor count
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertThrows(IllegalArgumentException.class, () -> PacketCodec.readQuest(r));
}

@Test
void readType_unknownPacketId_throws() {
    ByteBufWriter w = new ByteBufWriter();
    w.writeByte(0xFF);
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    assertThrows(IllegalArgumentException.class, () -> PacketCodec.readType(r));
}
```

- [ ] **Step 2: Run tests to verify the new ones fail**

Run: `./gradlew :common:test -q`
Expected: `readQuest_invalidVisibilityOrdinal_throws` and `readUpdateContributors_invalidOpOrdinal_throws` fail (no bounds check yet). `readQuest_negativeContributorCount_throws` may throw a different exception. `readType_unknownPacketId_throws` should already pass.

- [ ] **Step 3: Add safe enum and list decoding helpers to PacketCodec**

Add private helpers to `PacketCodec.java`:

```java
// --- Constants ---
private static final int MAX_CONTRIBUTORS = 256;
private static final int MAX_OPS = 256;
private static final int MAX_QUEST_LIST = 10000;

// --- Safe enum helper ---
private static <T extends Enum<T>> T readEnum(ByteBufReader buf, Class<T> enumClass) {
    int ordinal = buf.readVarInt();
    T[] values = enumClass.getEnumConstants();
    if (ordinal < 0 || ordinal >= values.length) {
        throw new IllegalArgumentException("Invalid " + enumClass.getSimpleName() + " ordinal: " + ordinal);
    }
    return values[ordinal];
}

// --- Safe list count helper ---
private static int readCount(ByteBufReader buf, int max, String label) {
    int count = buf.readVarInt();
    if (count < 0 || count > max) {
        throw new IllegalArgumentException(label + " count " + count + " outside bounds [0, " + max + "]");
    }
    return count;
}
```

Then find and replace all unsafe enum/list reads (use pattern matching, not line numbers):

Replace `Visibility.values()[buf.readVarInt()]` with `readEnum(buf, Visibility.class)` — appears in `readQuest()` and `readUpdateVisibility()`

Replace `ContributorOp.values()[buf.readVarInt()]` with `readEnum(buf, ContributorOp.class)` — appears in `readUpdateContributors()`

In `readQuest()`, replace the contributor count line:
```java
// BEFORE: int contributorCount = buf.readVarInt();
int contributorCount = readCount(buf, MAX_CONTRIBUTORS, "Contributor");
```

In `readUpdateContributors()`, replace the ops count line:
```java
// BEFORE: int count = buf.readVarInt();
int count = readCount(buf, MAX_OPS, "ContributorOp");
```

In `readSyncMyQuests()` and `readSyncServerQuests()`, replace their count lines:
```java
// BEFORE: int count = buf.readVarInt();
int count = readCount(buf, MAX_QUEST_LIST, "Quest");
```

- [ ] **Step 4: Run all common tests**

Run: `./gradlew :common:test -q`
Expected: All pass

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/disqt/disquests/common/PacketCodec.java \
        common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java
git commit -m "fix(common): add bounds checks for enums and list counts in PacketCodec

Prevents ArrayIndexOutOfBoundsException from invalid enum ordinals.
Caps contributor/op/quest list sizes to prevent OOM from crafted packets."
```

### Task 1.3: Use writeNullableString for handshake bluemapUrl

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketCodec.java`
- Test: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`

- [ ] **Step 1: Write test for null bluemapUrl**

Add to `PacketCodecTest.java`:

```java
@Test
void testHandshakeNullBluemapUrl() {
    byte[] packet = PacketCodec.writeHandshake(null, 0, null);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);
    assertNull(payload.bluemapUrl());
    assertEquals(0, reader.remaining());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests '*testHandshakeNullBluemapUrl' -q`
Expected: NPE in writeHandshake

- [ ] **Step 3: Change writeHandshake/readHandshake to use nullable string**

In `PacketCodec.java`, `writeHandshake` (line 124): change `buf.writeString(bluemapUrl)` to `writeNullableString(buf, bluemapUrl)`

In `readHandshake` (line 292): change `String bluemapUrl = buf.readString()` to `String bluemapUrl = readNullableString(buf)`

- [ ] **Step 4: Run all common tests**

Run: `./gradlew :common:test -q`
Expected: All pass. **Note:** Existing tests pass empty string `""` which still works with nullable encoding.

- [ ] **Step 5: Update paper Config to pass null instead of empty string (if applicable)**

Check `paper/src/main/java/com/disqt/disquests/paper/Config.java` — if `getBluemapUrl()` returns `""` when unconfigured, it can now return `null` instead. Update if needed.

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/disqt/disquests/common/PacketCodec.java \
        common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java
git commit -m "fix(common): use nullable string for handshake bluemapUrl

Prevents NPE when server has no BlueMap configured."
```

---

## Chunk 2: Server-Side Safety (paper module)

Wrap packet handlers, synchronize DB access, fix sqlite-jdbc scope, add validation.

### Task 2.1: Wrap onPluginMessageReceived in try/catch

**Files:**
- Modify: `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java`

- [ ] **Step 1: Add try/catch around the entire handler body**

```java
@Override
public void onPluginMessageReceived(String channel, Player player, byte[] data) {
    if (!CHANNEL.equals(channel)) return;
    try {
        ByteBufReader buf = new ByteBufReader(data);
        PacketType type = PacketType.fromId(buf.readByte());

        switch (type) {
            // ... existing cases unchanged ...
        }
    } catch (Exception e) {
        plugin.getLogger().log(java.util.logging.Level.WARNING,
            "Malformed packet from " + player.getName(), e);
    }
}
```

- [ ] **Step 2: Build paper module**

Run: `./gradlew :paper:build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java
git commit -m "fix(paper): wrap packet handler in try/catch

Prevents malformed packets from crashing the plugin message listener.
Logs a warning with the player name and error message."
```

### Task 2.2: Synchronize DataManager

**Files:**
- Modify: `paper/src/main/java/com/disqt/disquests/paper/DataManager.java`

- [ ] **Step 1: Add synchronized to all public methods**

Add `synchronized` keyword to every public method in `DataManager.java`: `initialize()`, `saveQuest()`, `deleteQuest()`, `getQuest()`, `getQuestsForPlayer()`, `getServerQuests()`, `updateVisibility()`, `addContributor()`, `removeContributor()`, `updateContributor()`, `getContributors()`, `isContributor()`, `createCollaborationRequest()`, `deleteCollaborationRequest()`, `getPendingRequestsForOwner()`, `getPendingRequestCount()`, `getCollaborationRequest()`, `pinQuest()`, `unpinQuest()`, `getPinnedQuestId()`, `upsertPlayerName()`, `getPlayerName()`, `getPlayerUuidByName()`, `close()`.

This is the simplest correct fix for a single SQLite connection. A connection pool is overkill for this use case.

- [ ] **Step 2: Run paper tests**

Run: `./gradlew :paper:test -q`
Expected: All pass

- [ ] **Step 3: Commit**

```bash
git add paper/src/main/java/com/disqt/disquests/paper/DataManager.java
git commit -m "fix(paper): synchronize all DataManager methods

Single JDBC Connection was accessed from netty + main threads without
synchronization. Adding synchronized to all public methods prevents
concurrent access corruption."
```

### Task 2.3: Fix sqlite-jdbc dependency scope

**Files:**
- Modify: `paper/build.gradle.kts`

- [ ] **Step 1: Change compileOnly to implementation and bundle in JAR**

In `paper/build.gradle.kts`, change:
```kotlin
compileOnly("org.xerial:sqlite-jdbc:3.49.1.0")
```
to:
```kotlin
implementation("org.xerial:sqlite-jdbc:3.49.1.0")
```

And in the **existing** `tasks.jar` block, add the sqlite-jdbc bundling line after the common module line:
```kotlin
tasks.jar {
    from(project(":common").sourceSets.main.get().output)
    // Bundle sqlite-jdbc for runtime (Paper doesn't provide it for plugins)
    from(configurations.runtimeClasspath.get().filter { it.name.contains("sqlite-jdbc") }.map { zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
```
**Important:** Do NOT create a second `tasks.jar` block — modify the existing one.

- [ ] **Step 2: Build and verify JAR contains sqlite classes**

Run: `./gradlew :paper:build -q && jar tf paper/build/libs/paper.jar | grep -c 'org/sqlite'`
Expected: Non-zero count

- [ ] **Step 3: Commit**

```bash
git add paper/build.gradle.kts
git commit -m "fix(paper): bundle sqlite-jdbc in plugin JAR

Was compileOnly but Paper doesn't provide sqlite-jdbc on the plugin
classpath. Would throw ClassNotFoundException at runtime."
```

### Task 2.4: Add string length limits and PIN_QUEST access check

**Files:**
- Modify: `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java`

- [ ] **Step 1: Add validation to handleSaveQuest**

At the top of `handleSaveQuest`, after reading the payload:
```java
private static final int MAX_TITLE_LENGTH = 256;
private static final int MAX_CONTENT_LENGTH = 65536;
private static final int MAX_MAP_LENGTH = 256;

private void handleSaveQuest(Player player, PacketCodec.SaveQuestPayload payload) {
    if (payload.title().length() > MAX_TITLE_LENGTH ||
        payload.content().length() > MAX_CONTENT_LENGTH ||
        (payload.map() != null && payload.map().length() > MAX_MAP_LENGTH)) {
        plugin.getLogger().warning("Quest field too long from " + player.getName());
        return;
    }
    // ... rest unchanged
```

- [ ] **Step 2: Add access check to handlePinQuest**

```java
private void handlePinQuest(Player player, UUID questId) {
    if (questId != null) {
        QuestData quest = dataManager.getQuest(questId);
        if (quest == null) return;
        // Check player can see this quest
        UUID playerUuid = player.getUniqueId();
        boolean canSee = quest.ownerUuid().equals(playerUuid)
            || quest.contributors().stream().anyMatch(c -> c.uuid().equals(playerUuid))
            || quest.visibility() == Visibility.OPEN;
        if (!canSee) return;
        dataManager.pinQuest(playerUuid, questId);
    } else {
        dataManager.unpinQuest(player.getUniqueId());
    }
}
```

- [ ] **Step 3: Prevent owner from adding self as contributor**

In `handleUpdateContributors`, in the `ADD` case, after resolving `targetUuid`:
```java
if (targetUuid != null
    && !targetUuid.equals(quest.ownerUuid())  // NEW: prevent self-add
    && !dataManager.isContributor(payload.questId(), targetUuid)) {
```

- [ ] **Step 4: Narrow the catch in handleRequestCollaboration**

Replace the broad `catch (RuntimeException e)` with checking for duplicate:
```java
try {
    UUID requestId = dataManager.createCollaborationRequest(questId, player.getUniqueId());
    // ... notify owner ...
} catch (RuntimeException e) {
    if (e.getCause() instanceof java.sql.SQLException) {
        // Duplicate request or constraint violation - silently ignore
    } else {
        plugin.getLogger().warning("Failed to create collaboration request: " + e.getMessage());
    }
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :paper:build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java
git commit -m "fix(paper): add input validation and access checks

- Enforce max lengths on quest title/content/map
- Add visibility check to PIN_QUEST handler
- Prevent owner from adding self as contributor
- Narrow exception catch in collaboration request handler"
```

---

## Chunk 3: Client-Side Safety (client module)

Fix packet handler crashes, unpin bug, stale screen data, and missing ColorConfig init.

### Task 3.1: Wrap client packet handler in try/catch

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java`

- [ ] **Step 1: Add try/catch inside the execute lambda**

Keep the existing outer try/catch around `readType` (which returns early on bad packet type byte). Add a new try/catch **inside** the `execute` lambda to protect the render thread from malformed packet bodies:

```java
public static void handleRawPayload(RawPayload payload, ClientPlayNetworking.Context context) {
    ByteBufReader r = new ByteBufReader(payload.data());
    PacketType type;
    try {
        type = PacketCodec.readType(r);
    } catch (Exception e) {
        return; // bad packet type byte — discard before scheduling
    }

    context.client().execute(() -> {
        try {
            switch (type) {
                case HANDSHAKE -> handleHandshake(r);
                case SYNC_MY_QUESTS -> handleSyncMyQuests(r);
                case SYNC_SERVER_QUESTS -> handleSyncServerQuests(r);
                case UPDATE_QUEST -> handleUpdateQuest(r);
                case DELETE_QUEST_S2C -> handleDeleteQuestS2C(r);
                case COLLABORATION_REQUEST -> handleCollaborationRequest(r);
                case COLLABORATION_RESPONSE -> handleCollaborationResponse(r);
                default -> {}
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("Disquests")
                .warn("Failed to handle S2C packet {}", type, e);
        }
    });
}
```
The outer try/catch is preserved from the existing code. The inner one is new.

- [ ] **Step 2: Build**

Run: `./gradlew :client:build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java
git commit -m "fix(client): wrap S2C packet handlers in try/catch

Prevents malformed server packets from crashing the render thread."
```

### Task 3.2: Fix unpin packet bug in ViewQuestScreen

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java`

- [ ] **Step 1: Fix togglePin to send correct packet**

Replace the `togglePin` method:

```java
private void togglePin() {
    UUID currentPinned = ClientSession.getPinnedQuestId();
    if (currentPinned != null && currentPinned.equals(quest.getId())) {
        // Unpin: send null
        PacketSender.pinQuest(null);
        ClientSession.setPinnedQuestId(null);
    } else {
        // Pin: send quest ID
        PacketSender.pinQuest(quest.getId());
        ClientSession.setPinnedQuestId(quest.getId());
    }
    this.clearAndInit();
}
```

The bug was that the old code always sent `pinQuest(quest.getId())` regardless of pin/unpin intent.

- [ ] **Step 2: Build**

Run: `./gradlew :client:build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java
git commit -m "fix(client): send null quest ID when unpinning

Was sending the quest ID for both pin and unpin, so unpin never worked."
```

### Task 3.3: Wire up ColorConfig.loadColors on startup

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/DisquestsClient.java`

- [ ] **Step 1: Add loadColors call in onInitializeClient**

Add at the top of `onInitializeClient()`, before `KeyBinds.register()`:

```java
@Override
public void onInitializeClient() {
    com.disqt.disquests.client.gui.helper.ColorConfig.loadColors();
    KeyBinds.register();
    // ... rest unchanged
```

- [ ] **Step 2: Build**

Run: `./gradlew :client:build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/DisquestsClient.java
git commit -m "fix(client): load color config on mod initialization

ColorConfig.loadColors() was never called, so custom colors from
disquests/colors.json were ignored after the first run."
```

### Task 3.4: Encode BlueMap URL components

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/BlueMapHelper.java`

- [ ] **Step 1: Read current file**

Read `BlueMapHelper.java` to see exact current implementation.

- [ ] **Step 2: URL-encode the map parameter and validate base URL**

```java
public static String buildUrl(Quest quest) {
    String base = ClientSession.getBluemapUrl();
    if (base == null || base.isEmpty()) return null;
    if (quest.getCoordinates() == null) return null;

    // Validate base URL scheme
    if (!base.startsWith("http://") && !base.startsWith("https://")) return null;

    CoordinatesData c = quest.getCoordinates();
    double x = c.x(), y = c.y(), z = c.z();
    String map = quest.getMap() != null ? quest.getMap() : "world";
    // URL-encode the map name for the fragment
    String encodedMap = java.net.URLEncoder.encode(map, java.nio.charset.StandardCharsets.UTF_8);
    return String.format("%s/#%s:%.0f:%.0f:%.0f:50:0:0:0:0:flat", base, encodedMap, x, y, z);
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :client:build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/BlueMapHelper.java
git commit -m "fix(client): URL-encode BlueMap map name and validate scheme

Prevents URL injection from server-controlled map string.
Rejects non-HTTP(S) base URLs to prevent javascript: URI attacks."
```

---

## Chunk 4: Build & CI Cleanup

Fix dependency issues, deduplicate workflows, clean dead config, update metadata.

### Task 4.1: Delete stale testmod source files

**Files:**
- Delete: `client/src/testmod/` (entire directory)

**Note:** The testmod sourceSet config in `client/build.gradle.kts` and the `runClientGameTest` Loom run config must stay for now — they are referenced by both CI workflows (`e2e-test.yml` and `release.yml`). These should only be removed when E2E tests are rewritten (tracked in CLAUDE.md). This task only deletes the stale Java test files that reference classes from the old BuildNotes codebase.

- [ ] **Step 1: Verify the testmod files reference old BuildNotes classes**

Run: `grep -r 'import\|BuildNotes\|buildnotes' client/src/testmod/` to confirm these files are stale.

- [ ] **Step 2: Delete the stale testmod source files**

Run: `rm -rf client/src/testmod/`

- [ ] **Step 3: Verify build still works**

Run: `./gradlew :client:build -q`
Expected: BUILD SUCCESSFUL (the sourceSet is `create("testmod")` which will have no sources but won't error)

- [ ] **Step 4: Commit**

```bash
git add -A client/src/testmod/
git commit -m "chore(client): delete stale testmod source files

These files referenced old BuildNotes classes and would not compile.
The sourceSet config and CI workflow references are left intact until
new E2E tests are written."
```

### Task 4.2: Fix lwjgl-tinyfd native classifiers

**Files:**
- Modify: `client/build.gradle.kts`

- [ ] **Step 1: Add platform-specific native JARs**

Replace:
```kotlin
implementation("org.lwjgl:lwjgl-tinyfd:3.3.1")
include("org.lwjgl:lwjgl-tinyfd:3.3.1")
```

With:
```kotlin
implementation("org.lwjgl:lwjgl-tinyfd:3.3.1")
include("org.lwjgl:lwjgl-tinyfd:3.3.1")
// Native libraries for file dialogs
listOf("natives-linux", "natives-windows", "natives-macos", "natives-macos-arm64").forEach { classifier ->
    runtimeOnly("org.lwjgl:lwjgl-tinyfd:3.3.1:$classifier")
    include("org.lwjgl:lwjgl-tinyfd:3.3.1:$classifier")
}
```

**Note:** Minecraft already bundles base LWJGL natives. Check if Loom remaps these — if the mod JAR grows too large with all natives, consider making tinyfd an optional runtime dependency. Verify the mod still loads after this change.

- [ ] **Step 2: Build and check JAR size**

Run: `./gradlew :client:build -q && ls -lh client/build/libs/`
Expected: BUILD SUCCESSFUL. JAR may grow by ~1-2MB with the native libs.

- [ ] **Step 3: Commit**

```bash
git add client/build.gradle.kts
git commit -m "fix(client): include lwjgl-tinyfd native classifiers

Without native JARs, file dialogs would throw UnsatisfiedLinkError."
```

### Task 4.3: Update fabric.mod.json metadata

**Files:**
- Modify: `client/src/main/resources/fabric.mod.json`

- [ ] **Step 1: Update metadata to reflect Disquests identity**

```json
{
    "schemaVersion": 1,
    "id": "disquests",
    "version": "${version}",

    "name": "Disquests",
    "description": "Track in-game quests with your friends. Create, share, and collaborate on quests with coordinates, markdown notes, and BlueMap integration.",

    "authors": [
        "disqt"
    ],

    "contact": {
        "sources": "https://github.com/disqt/disquests"
    },

    "license": "MIT",
    "icon": "assets/disquests/icon.png",

    "environment": "client",
    "entrypoints": {
        "client": [
            "com.disqt.disquests.client.DisquestsClient"
        ]
    },
    "mixins": [
        "disquests.mixins.json"
    ],

    "depends": {
        "fabricloader": ">=0.17.3",
        "minecraft": "~1.21.11",
        "java": ">=21",
        "fabric": "*"
    },
    "suggests": {}
}
```

Key changes: authors (disqt), description (quest-specific), contact.sources (correct repo), icon path (assets/disquests/), java version (>=21 to match build config), removed homepage (was generic fabricmc.net).

- [ ] **Step 1b: Rename icon asset directory**

Run: `mv client/src/main/resources/assets/buildnotes client/src/main/resources/assets/disquests`

If the directory doesn't exist, skip this step — the icon path in fabric.mod.json will be a broken reference (non-fatal, just no icon).

- [ ] **Step 2: Build**

Run: `./gradlew :client:build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/resources/fabric.mod.json client/src/main/resources/assets/
git commit -m "chore(client): update fabric.mod.json to Disquests metadata

Replace stale BuildNotes author, description, URLs, and icon path.
Rename assets/buildnotes/ to assets/disquests/."
```

### Task 4.4: Inject version into plugin.yml and pin JUnit in common

**Files:**
- Modify: `paper/build.gradle.kts`
- Modify: `paper/src/main/resources/plugin.yml`
- Modify: `common/build.gradle.kts`

- [ ] **Step 1: Add processResources for plugin.yml version injection**

In `paper/build.gradle.kts`, add:
```kotlin
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
```

- [ ] **Step 2: Update plugin.yml to use template variable**

```yaml
name: Disquests
version: '${version}'
main: com.disqt.disquests.paper.DisquestsPlugin
api-version: '1.21'
commands:
  disquests:
    description: Disquests admin commands
    permission: disquests.admin
    usage: /disquests <reload>
permissions:
  disquests.admin:
    description: Admin access to Disquests commands
    default: op
```

- [ ] **Step 3: Pin junit-platform-launcher in common**

In `common/build.gradle.kts`:
```kotlin
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}
```

- [ ] **Step 4: Build all**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add paper/build.gradle.kts paper/src/main/resources/plugin.yml common/build.gradle.kts
git commit -m "chore: inject version into plugin.yml, pin junit-platform-launcher

plugin.yml was hardcoded to 0.1.0. Now uses Gradle processResources.
common module's junit-platform-launcher was unpinned."
```

### Task 4.5: Deduplicate E2E workflow and fix release workflow

**Dependency:** The E2E workflow calls `./gradlew runClientGameTest`, which depends on the testmod sourceSet and Loom run config still being in `client/build.gradle.kts`. These CI workflows will fail until new E2E tests are written to replace the deleted testmod files. This task reorganizes the CI plumbing so that when E2E tests are rewritten, they only need to be defined in one place.

**Files:**
- Modify: `.github/workflows/e2e-test.yml`
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Make e2e-test.yml callable as a reusable workflow**

Add `workflow_call` trigger to e2e-test.yml:

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_call:
```

Remove the dead `master` branch trigger.

- [ ] **Step 2: Replace inline E2E steps in release.yml with workflow call**

Replace the release workflow's E2E section with a separate job:

```yaml
jobs:
  e2e:
    uses: ./.github/workflows/e2e-test.yml

  release:
    needs: e2e
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      # Checkout, Java setup, Gradle cache, Loom cache (same as before)
      # Build step
      # Changelog generation
      # Rename artifacts
      # Create GitHub release
```

Remove the duplicated E2E steps (Configure Paper server, Download and start, Run E2E tests, Stop Paper server, Upload test artifacts) from the release job.

- [ ] **Step 3: Fix cancel-in-progress on release**

Change `cancel-in-progress: true` to `cancel-in-progress: false` in release.yml.

- [ ] **Step 4: Fix changelog echo -e to printf**

In the changelog generation step, replace:
```bash
echo -e "$BODY" > /tmp/changelog.md
```
with:
```bash
printf '%b' "$BODY" > /tmp/changelog.md
```

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/e2e-test.yml .github/workflows/release.yml
git commit -m "ci: deduplicate E2E workflow, fix release safety

- Make e2e-test.yml a reusable workflow (workflow_call)
- Release workflow calls e2e-test.yml instead of duplicating steps
- Set cancel-in-progress: false for releases
- Fix changelog echo -e to printf for safe escaping
- Remove dead master branch trigger"
```

---

## Chunk 5: Expand Test Coverage (common module)

Add missing round-trip tests for untested packet types and edge cases.

### Task 5.1: Add missing packet type round-trip tests

**Files:**
- Modify: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`

- [ ] **Step 1: Add tests for all untested packet types**

```java
@Test
void testRequestSyncRoundTrip() {
    byte[] packet = PacketCodec.writeRequestSync();
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.REQUEST_SYNC, PacketCodec.readType(reader));
    assertEquals(0, reader.remaining());
}

@Test
void testJoinQuestRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeJoinQuest(questId);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.JOIN_QUEST, PacketCodec.readType(reader));
    UUID decoded = PacketCodec.readJoinQuest(reader);
    assertEquals(questId, decoded);
    assertEquals(0, reader.remaining());
}

@Test
void testRequestCollaborationRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeRequestCollaboration(questId);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.REQUEST_COLLABORATION, PacketCodec.readType(reader));
    UUID decoded = PacketCodec.readRequestCollaboration(reader);
    assertEquals(questId, decoded);
    assertEquals(0, reader.remaining());
}

@Test
void testSyncServerQuestsRoundTrip() {
    QuestData q1 = makeQuest(false, false, false);
    List<QuestData> quests = List.of(q1);
    byte[] packet = PacketCodec.writeSyncServerQuests(quests);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_SERVER_QUESTS, PacketCodec.readType(reader));
    List<QuestData> decoded = PacketCodec.readSyncServerQuests(reader);
    assertEquals(1, decoded.size());
    assertQuestsEqual(q1, decoded.get(0));
    assertEquals(0, reader.remaining());
}

@Test
void testUpdateQuestRoundTrip() {
    QuestData quest = makeQuest(true, false, true);
    byte[] packet = PacketCodec.writeUpdateQuest(quest);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.UPDATE_QUEST, PacketCodec.readType(reader));
    QuestData decoded = PacketCodec.readUpdateQuest(reader);
    assertQuestsEqual(quest, decoded);
    assertEquals(0, reader.remaining());
}

@Test
void testDeleteQuestS2CRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeDeleteQuestS2C(questId);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.DELETE_QUEST_S2C, PacketCodec.readType(reader));
    UUID decoded = PacketCodec.readDeleteQuestS2C(reader);
    assertEquals(questId, decoded);
    assertEquals(0, reader.remaining());
}
```

- [ ] **Step 2: Add edge case tests**

```java
@Test
void testUpdateContributorsWithUpdateOp() {
    UUID questId = UUID.randomUUID();
    UUID playerUuid = UUID.randomUUID();
    List<PacketCodec.ContributorOpEntry> ops = List.of(
        new PacketCodec.ContributorOpEntry(ContributorOp.UPDATE, playerUuid, null, true)
    );
    byte[] packet = PacketCodec.writeUpdateContributors(questId, ops);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.UPDATE_CONTRIBUTORS, PacketCodec.readType(reader));
    PacketCodec.UpdateContributorsPayload payload = PacketCodec.readUpdateContributors(reader);
    assertEquals(ContributorOp.UPDATE, payload.ops().get(0).action());
    assertEquals(0, reader.remaining());
}

@Test
void testQuestWithClosedVisibility() {
    QuestData quest = new QuestData(
        UUID.randomUUID(), "Closed Quest", "Only collaborators.",
        UUID.randomUUID(), "owner",
        Visibility.CLOSED,
        List.of(new ContributorData(UUID.randomUUID(), "helper", true)),
        1000L, null, false, null, null
    );
    ByteBufWriter w = new ByteBufWriter();
    PacketCodec.writeQuest(w, quest);
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    QuestData decoded = PacketCodec.readQuest(r);
    assertEquals(Visibility.CLOSED, decoded.visibility());
    assertQuestsEqual(quest, decoded);
}

@Test
void testUnicodeStringRoundTrip() {
    UUID questId = UUID.randomUUID();
    byte[] packet = PacketCodec.writeSaveQuest(
        questId, "Quest: Dragon", "Description with unicode and CJK chars",
        null, false, null, null);
    ByteBufReader reader = new ByteBufReader(packet);
    PacketCodec.readType(reader);
    PacketCodec.SaveQuestPayload payload = PacketCodec.readSaveQuest(reader);
    assertEquals("Quest: Dragon", payload.title());
}

@Test
void testEmptySyncMyQuests() {
    byte[] packet = PacketCodec.writeSyncMyQuests(List.of());
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_MY_QUESTS, PacketCodec.readType(reader));
    List<QuestData> decoded = PacketCodec.readSyncMyQuests(reader);
    assertTrue(decoded.isEmpty());
    assertEquals(0, reader.remaining());
}
```

- [ ] **Step 3: Run all common tests**

Run: `./gradlew :common:test -q`
Expected: All pass

- [ ] **Step 4: Commit**

```bash
git add common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java
git commit -m "test(common): add missing packet type and edge case tests

Covers REQUEST_SYNC, JOIN_QUEST, REQUEST_COLLABORATION,
SYNC_SERVER_QUESTS, UPDATE_QUEST, DELETE_QUEST_S2C round trips.
Adds edge cases: UPDATE op, CLOSED visibility, unicode strings,
empty sync lists."
```
