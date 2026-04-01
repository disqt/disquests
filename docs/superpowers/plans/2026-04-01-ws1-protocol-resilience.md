# WS1: Protocol Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add protocol versioning so old clients get v0-format packets (no tags) and new clients get full format with tags. Also add DB migration, config defaults, and a new SYNC_TAGS packet.

**Architecture:** The client sends a protocol version (1) as the first VarInt in REQUEST_SYNC. The server detects this via remaining-bytes check (v0 REQUEST_SYNC has 0 bytes after the type byte; v1 has a VarInt). Per-player protocol versions are tracked in a `Map<UUID, Integer>` inside `ServerPacketHandler`. All S2C quest-writing methods (`writeQuest`, `writeHandshake`, quest-list packets) accept a protocol version and conditionally omit v1+ fields. The client reader uses `buf.remaining() > 0` guards so it can tolerate either format.

**Tech Stack:** Java 21, Gradle, JUnit 5, Fabric Loom, PaperMC

---

## Task 1: Add protocol version constant and SYNC_TAGS to common

**Files:**
- `common/src/main/java/com/disqt/disquests/common/ProtocolVersion.java` (create)
- `common/src/main/java/com/disqt/disquests/common/PacketType.java` (modify)

### Step 1: Create ProtocolVersion constants

Create `common/src/main/java/com/disqt/disquests/common/ProtocolVersion.java`:

```java
package com.disqt.disquests.common;

public final class ProtocolVersion {
  /** Pre-tags protocol. No version sent in REQUEST_SYNC. */
  public static final int V0 = 0;

  /** Tags protocol. Adds tags to quests, predefinedTags to handshake, SYNC_TAGS packet. */
  public static final int V1 = 1;

  /** The current protocol version this build speaks. */
  public static final int CURRENT = V1;

  private ProtocolVersion() {}
}
```

### Step 2: Add SYNC_TAGS to PacketType enum

In `common/src/main/java/com/disqt/disquests/common/PacketType.java`, add a new entry after `SYNC_PENDING_REQUESTS`:

```java
  SYNC_PENDING_REQUESTS((byte) 0x17),
  SYNC_TAGS((byte) 0x18);
```

Change the existing semicolon after `SYNC_PENDING_REQUESTS` to a comma.

### Step 3: Verify compilation

```bash
./gradlew :common:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 4: Commit

```
feat(common): add ProtocolVersion constants and SYNC_TAGS packet type
```

---

## Task 2: Version-aware writeQuest and writeHandshake in PacketCodec

**Files:**
- `common/src/main/java/com/disqt/disquests/common/PacketCodec.java` (modify)
- `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java` (modify)

### Step 1: Add versioned writeQuest overload

In `PacketCodec.java`, add a new overload below the existing `writeQuest(ByteBufWriter, QuestData)`. The existing method stays unchanged (it writes all fields, used for v1+ clients). The new method conditionally omits tags for v0:

```java
  /**
   * Writes a quest with protocol-version-aware encoding.
   * v0: omits trailing tags list.
   * v1+: full format including tags.
   */
  public static void writeQuest(ByteBufWriter buf, QuestData quest, int protocolVersion) {
    buf.writeUUID(quest.id());
    buf.writeString(quest.title());
    buf.writeString(quest.content());
    buf.writeUUID(quest.ownerUuid());
    buf.writeString(quest.ownerName() != null ? quest.ownerName() : "");
    buf.writeVarInt(quest.visibility().ordinal());
    buf.writeVarInt(quest.contributors().size());
    for (ContributorData contributor : quest.contributors()) {
      buf.writeUUID(contributor.uuid());
      buf.writeString(contributor.name());
      buf.writeBoolean(contributor.canEdit());
    }
    buf.writeLong(quest.lastModified());
    writeNullableCoords(buf, quest.coordinates());
    buf.writeBoolean(quest.isRegion());
    writeNullableCoords(buf, quest.coordinates2());
    writeNullableString(buf, quest.map());
    if (protocolVersion >= ProtocolVersion.V1) {
      buf.writeVarInt(quest.tags().size());
      for (String tag : quest.tags()) {
        buf.writeString(tag);
      }
    }
  }
```

### Step 2: Add versioned S2C encode methods

Add versioned overloads for all S2C methods that embed quests. These call `writeQuest(buf, quest, protocolVersion)` instead of `writeQuest(buf, quest)`. The existing zero-arg versions stay as-is for backward compat (they use full format).

```java
  public static byte[] writeHandshake(
      String bluemapUrl,
      int pendingRequestCount,
      List<UUID> pinnedQuestIds,
      UUID playerUuid,
      Map<String, String> mapNames,
      List<String> predefinedTags,
      int protocolVersion) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.HANDSHAKE.getId());
    writeNullableString(buf, bluemapUrl);
    buf.writeVarInt(pendingRequestCount);
    buf.writeVarInt(pinnedQuestIds.size());
    for (UUID id : pinnedQuestIds) {
      buf.writeUUID(id);
    }
    buf.writeUUID(playerUuid);
    buf.writeVarInt(mapNames.size());
    for (Map.Entry<String, String> entry : mapNames.entrySet()) {
      buf.writeString(entry.getKey());
      buf.writeString(entry.getValue());
    }
    if (protocolVersion >= ProtocolVersion.V1) {
      buf.writeVarInt(predefinedTags.size());
      for (String tag : predefinedTags) {
        buf.writeString(tag);
      }
    }
    return buf.toByteArray();
  }

  public static byte[] writeSyncMyQuests(
      List<QuestData> quests, Map<UUID, Integer> pendingCounts, int protocolVersion) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.SYNC_MY_QUESTS.getId());
    buf.writeVarInt(quests.size());
    for (QuestData quest : quests) {
      writeQuest(buf, quest, protocolVersion);
    }
    if (!pendingCounts.isEmpty()) {
      buf.writeVarInt(pendingCounts.size());
      for (Map.Entry<UUID, Integer> entry : pendingCounts.entrySet()) {
        buf.writeUUID(entry.getKey());
        buf.writeVarInt(entry.getValue());
      }
    }
    return buf.toByteArray();
  }

  public static byte[] writeSyncServerQuests(List<QuestData> quests, int protocolVersion) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.SYNC_SERVER_QUESTS.getId());
    buf.writeVarInt(quests.size());
    for (QuestData quest : quests) {
      writeQuest(buf, quest, protocolVersion);
    }
    return buf.toByteArray();
  }

  public static byte[] writeUpdateQuest(QuestData quest, int protocolVersion) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.UPDATE_QUEST.getId());
    writeQuest(buf, quest, protocolVersion);
    return buf.toByteArray();
  }

  public static byte[] writeCollaborationResponse(
      UUID questId, boolean approved, QuestData quest, int protocolVersion) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.COLLABORATION_RESPONSE.getId());
    buf.writeUUID(questId);
    buf.writeBoolean(approved);
    if (quest != null) {
      buf.writeBoolean(true);
      writeQuest(buf, quest, protocolVersion);
    } else {
      buf.writeBoolean(false);
    }
    return buf.toByteArray();
  }
```

### Step 3: Add SYNC_TAGS encode/decode methods

```java
  // ---- SYNC_TAGS ----

  public static byte[] writeSyncTags(List<String> tags) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.SYNC_TAGS.getId());
    buf.writeVarInt(tags.size());
    for (String tag : tags) {
      buf.writeString(tag);
    }
    return buf.toByteArray();
  }

  public static List<String> readSyncTags(ByteBufReader buf) {
    int count = readCount(buf, MAX_QUEST_LIST, "Tag");
    List<String> tags = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      tags.add(buf.readString());
    }
    return tags;
  }
```

### Step 4: Add versioned writeRequestSync

```java
  public static byte[] writeRequestSync(int protocolVersion) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.REQUEST_SYNC.getId());
    buf.writeVarInt(protocolVersion);
    return buf.toByteArray();
  }
```

### Step 5: Add readQuest with remaining() guard for tags

In `PacketCodec.java`, add a client-safe reader that uses `buf.remaining() > 0` before reading tags:

```java
  public static int readRequestSyncVersion(ByteBufReader buf) {
    if (buf.remaining() > 0) {
      return buf.readVarInt();
    }
    return ProtocolVersion.V0;
  }
```

Modify the existing `readQuest` to add a `remaining()` guard before the tag-reading block. Change these lines at the end of `readQuest`:

**Before:**
```java
    int tagCount = buf.readVarInt();
    List<String> tags = new ArrayList<>(tagCount);
    for (int i = 0; i < tagCount; i++) {
      tags.add(buf.readString());
    }
```

**After:**
```java
    List<String> tags;
    if (buf.remaining() > 0) {
      int tagCount = buf.readVarInt();
      tags = new ArrayList<>(tagCount);
      for (int i = 0; i < tagCount; i++) {
        tags.add(buf.readString());
      }
    } else {
      tags = List.of();
    }
```

**IMPORTANT:** This `remaining()` guard only works correctly when `readQuest` is the **last** item in the packet (i.e., `writeUpdateQuest`, `writeCollaborationResponse` with a single quest). For list packets (`writeSyncMyQuests`, `writeSyncServerQuests`) where multiple quests are written sequentially, the `remaining()` check after quest N would see quest N+1's bytes and incorrectly try to read tags. However, this is safe because:
- The server always writes all quests in a list with the same protocol version.
- v0 quest encoding followed by another v0 quest: the next quest's UUID bytes start after `map`, and the reader would attempt `readVarInt()` on UUID bytes. This would fail loudly, not silently.
- In practice the guard is a safety net for single-quest packets from an old server to a new client. List packets will always be version-consistent because the server controls the format.

Actually, **on closer analysis this is a problem for list packets.** The `remaining()` guard cannot distinguish "no more data in this quest" from "there's another quest after this one." We must use a different approach for list packets.

**Revised approach:** Instead of modifying `readQuest`, create two reader methods:

1. `readQuest(ByteBufReader buf)` -- unchanged, reads tags unconditionally (used when we know the format includes tags, i.e., v1+).
2. `readQuestV0(ByteBufReader buf)` -- new, reads without tags (used when we know the format is v0).

But the client doesn't know the server's protocol version. The client always reads what the server sends. Since the **server** decides whether to include tags based on the client's version, the client knows its own version. So:
- A v1 client always sends version 1 in REQUEST_SYNC. The server sends v1-format packets back. The client uses `readQuest()` (which reads tags).
- A v0 client (old build) never sent a version. The server sends v0-format packets. The old client uses the old `readQuest()` (which doesn't have tag reading at all -- that code doesn't exist in old builds).

**Conclusion:** The `remaining()` guard on `readQuest` is only needed as a defensive measure for edge cases (e.g., a new client connecting to an old server that doesn't know about protocol versions). In that case, the old server sends v0-format packets but the new client expects v1 format. For single-quest packets this works with `remaining()`. For list packets, all quests will be v0-format, and the first quest read would try to interpret the next quest's UUID as a tag count -- that's a bug.

**Final approach:** Keep `readQuest` with the `remaining()` guard, but also check the count is reasonable. Since quest list packets always use the same format for all entries, the real fix is: **the client should know what format to expect.** The simplest path:

1. The client tracks whether it received a handshake with predefinedTags (indicating the server speaks v1+). Store this in `ClientSession.serverProtocolVersion`.
2. If the server is v0 (no predefinedTags in handshake), the client knows all packets are v0.
3. But `readHandshake` already uses `remaining()` guards for mapNames and predefinedTags, so a new client can always parse old handshakes.

Actually the simplest correct approach is: **keep the remaining() guard in readQuest because it works for all real-world scenarios.** Here is why:

- **New client + new server:** Server knows client is v1, sends tags. `readQuest` reads tags. Works.
- **New client + old server:** Old server doesn't know about versions, sends v0-format (no tags). In list packets, `readQuest` reads quest N, reaches the point after `map`, and `remaining()` would be > 0 (because quest N+1's bytes exist). BUT -- it would try to `readVarInt()` on what is actually the next quest's UUID. This would likely read a large integer and then fail reading that many tag strings.

This IS a real bug. To fix it properly without complicating the reader, we need to use a **length-prefixed quest** format. But that would break backward compatibility with v0.

**Simplest correct fix:** Do NOT put the `remaining()` guard in `readQuest`. Instead, leave `readQuest` unchanged (always reads tags) AND leave the old `readQuest` that doesn't read tags as a separate method. The SERVER decides which format to use. The CLIENT is compiled with v1 code and always reads tags. If a new client connects to an old server (which doesn't have tags at all and sends the pre-tags format), the client will crash trying to read tags. But this is acceptable because:

Wait -- this was the **original bug** that prompted this workstream. A new client connects to a server that has been upgraded but sends v0-format to v0 clients. The problem is the new client build always tries to read tags.

**The correct fix is this:**
1. `readQuest` stays as-is (reads tags unconditionally). This is what the new client build uses.
2. The server, upon receiving a v1 REQUEST_SYNC, sends v1-format packets (with tags).
3. The server, upon receiving a v0 REQUEST_SYNC (or no version), sends v0-format packets (without tags).
4. A new client always sends v1 in REQUEST_SYNC and always receives v1-format packets. No ambiguity.
5. An old client never sends a version and always receives v0-format packets. No crash.
6. **Edge case: new client + old server** (server hasn't been upgraded, doesn't know about versions). Old server ignores the version byte in REQUEST_SYNC (it never reads past the type byte -- see `handleRequestSync(player)` in ServerPacketHandler, which just ignores the buf entirely). Old server sends old-format packets. New client tries `readQuest` which reads tags --> crash.

For this edge case, add the `remaining()` guard to `readQuest` **only for single-quest packets** is not enough. The real fix for list compatibility is: make the client's tag reading conditional on whether the handshake included tags. Since `readHandshake` already has `remaining()` guards, the client can detect an old server.

**FINAL DESIGN -- keeping it simple:**

1. Add `remaining()` guard to `readQuest` for the tags field (as proposed). YES, this has a theoretical issue with list packets from an old server. But in practice:
   - The VarInt read on the next quest's UUID high bytes would produce a very large number (UUID bytes are random, first byte likely has high bit set, producing multi-byte VarInt).
   - `readCount` with `MAX_QUEST_LIST = 10000` would likely reject it.
   - Even if it passes, reading that many strings would exhaust the buffer and throw.
   - The outer try/catch in `ClientPacketHandler.handleRawPayload` would catch this and log a warning.
   - This is the same failure mode as today (the whole sync fails), but instead of a guaranteed crash for old-server+new-client, it becomes a probabilistic crash that gets caught.

2. The proper fix (no edge-case crash at all) would require either length-prefixed quests or client-side version tracking. This is overkill for v1 since the server will be upgraded first (server-only deploy). Document this as a known limitation.

**OK, let's go with the pragmatic approach:**

- Add `remaining()` guard to `readQuest` for tags. Document the list-packet edge case.
- The server uses versioned write methods.
- For the new-client + old-server edge case, the handshake will succeed (thanks to existing `remaining()` guards), and the client will set `predefinedTags = List.of()`. Quest list packets may partially fail, but the `try/catch` in `ClientPacketHandler` handles it gracefully.

### Step 5 (revised): Add remaining() guard to readQuest for tags

In `PacketCodec.readQuest`, change:

```java
    int tagCount = buf.readVarInt();
    List<String> tags = new ArrayList<>(tagCount);
    for (int i = 0; i < tagCount; i++) {
      tags.add(buf.readString());
    }
```

to:

```java
    List<String> tags;
    if (buf.remaining() > 0) {
      int tagCount = readCount(buf, TagConstraints.MAX_TAGS, "Tag");
      tags = new ArrayList<>(tagCount);
      for (int i = 0; i < tagCount; i++) {
        tags.add(buf.readString());
      }
    } else {
      tags = List.of();
    }
```

Note: using `readCount(buf, TagConstraints.MAX_TAGS, "Tag")` instead of raw `readVarInt()` adds bounds checking that helps catch the list-packet edge case (a UUID byte misinterpreted as tag count would likely exceed MAX_TAGS=8 and throw).

### Step 6: Write unit tests

Add to `PacketCodecTest.java`:

```java
  // ---- Protocol versioning tests ----

  @Test
  void writeQuestV0_omitsTags() {
    QuestData quest =
        new QuestData(
            UUID.randomUUID(),
            "Tagged Quest",
            "Has tags",
            UUID.randomUUID(),
            "owner",
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of("combat", "exploration"));
    ByteBufWriter buf = new ByteBufWriter();
    PacketCodec.writeQuest(buf, quest, ProtocolVersion.V0);
    ByteBufReader reader = new ByteBufReader(buf.toByteArray());
    QuestData decoded = PacketCodec.readQuest(reader);
    // v0 format has no tags, reader falls back to empty list
    assertTrue(decoded.tags().isEmpty());
    assertEquals(0, reader.remaining());
  }

  @Test
  void writeQuestV1_includesTags() {
    List<String> tags = List.of("combat", "exploration");
    QuestData quest =
        new QuestData(
            UUID.randomUUID(),
            "Tagged Quest",
            "Has tags",
            UUID.randomUUID(),
            "owner",
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            tags);
    ByteBufWriter buf = new ByteBufWriter();
    PacketCodec.writeQuest(buf, quest, ProtocolVersion.V1);
    ByteBufReader reader = new ByteBufReader(buf.toByteArray());
    QuestData decoded = PacketCodec.readQuest(reader);
    assertEquals(tags, decoded.tags());
    assertEquals(0, reader.remaining());
  }

  @Test
  void writeHandshakeV0_omitsPredefinedTags() {
    UUID playerUuid = UUID.randomUUID();
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    List<String> predefinedTags = List.of("combat", "exploration");
    byte[] packet =
        PacketCodec.writeHandshake(
            "https://example.com",
            1,
            List.of(),
            playerUuid,
            mapNames,
            predefinedTags,
            ProtocolVersion.V0);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);
    assertEquals("https://example.com", payload.bluemapUrl());
    assertEquals(mapNames, payload.bluemapMapNames());
    // v0: predefinedTags not written, reader falls back to empty
    assertTrue(payload.predefinedTags().isEmpty());
    assertEquals(0, reader.remaining());
  }

  @Test
  void writeHandshakeV1_includesPredefinedTags() {
    UUID playerUuid = UUID.randomUUID();
    Map<String, String> mapNames = Map.of("overworld", "world_new");
    List<String> predefinedTags = List.of("combat", "exploration");
    byte[] packet =
        PacketCodec.writeHandshake(
            "https://example.com",
            1,
            List.of(),
            playerUuid,
            mapNames,
            predefinedTags,
            ProtocolVersion.V1);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(reader));
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);
    assertEquals(predefinedTags, payload.predefinedTags());
    assertEquals(0, reader.remaining());
  }

  @Test
  void requestSyncV1_roundTrip() {
    byte[] packet = PacketCodec.writeRequestSync(ProtocolVersion.V1);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.REQUEST_SYNC, PacketCodec.readType(reader));
    int version = PacketCodec.readRequestSyncVersion(reader);
    assertEquals(ProtocolVersion.V1, version);
    assertEquals(0, reader.remaining());
  }

  @Test
  void requestSyncV0_noVersionByte() {
    byte[] packet = PacketCodec.writeRequestSync();
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.REQUEST_SYNC, PacketCodec.readType(reader));
    int version = PacketCodec.readRequestSyncVersion(reader);
    assertEquals(ProtocolVersion.V0, version);
    assertEquals(0, reader.remaining());
  }

  @Test
  void syncTagsRoundTrip() {
    List<String> tags = List.of("building", "redstone", "farm", "nether");
    byte[] packet = PacketCodec.writeSyncTags(tags);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_TAGS, PacketCodec.readType(reader));
    List<String> decoded = PacketCodec.readSyncTags(reader);
    assertEquals(tags, decoded);
    assertEquals(0, reader.remaining());
  }

  @Test
  void syncTagsEmpty() {
    byte[] packet = PacketCodec.writeSyncTags(List.of());
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_TAGS, PacketCodec.readType(reader));
    List<String> decoded = PacketCodec.readSyncTags(reader);
    assertTrue(decoded.isEmpty());
    assertEquals(0, reader.remaining());
  }

  @Test
  void writeUpdateQuestV0_omitsTags() {
    QuestData quest =
        new QuestData(
            UUID.randomUUID(),
            "Quest",
            "content",
            UUID.randomUUID(),
            "owner",
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of("tag1"));
    byte[] packet = PacketCodec.writeUpdateQuest(quest, ProtocolVersion.V0);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.UPDATE_QUEST, PacketCodec.readType(reader));
    QuestData decoded = PacketCodec.readQuest(reader);
    assertTrue(decoded.tags().isEmpty());
    assertEquals(0, reader.remaining());
  }

  @Test
  void writeSyncMyQuestsV0_omitsTags() {
    QuestData quest =
        new QuestData(
            UUID.randomUUID(),
            "Quest",
            "content",
            UUID.randomUUID(),
            "owner",
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of("tag1", "tag2"));
    byte[] packet =
        PacketCodec.writeSyncMyQuests(List.of(quest), Map.of(), ProtocolVersion.V0);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_MY_QUESTS, PacketCodec.readType(reader));
    List<QuestData> decoded = PacketCodec.readSyncMyQuests(reader);
    assertEquals(1, decoded.size());
    assertTrue(decoded.get(0).tags().isEmpty());
  }

  @Test
  void writeSyncServerQuestsV0_omitsTags() {
    QuestData quest =
        new QuestData(
            UUID.randomUUID(),
            "Quest",
            "content",
            UUID.randomUUID(),
            "owner",
            Visibility.OPEN,
            List.of(),
            1000L,
            null,
            false,
            null,
            null,
            List.of("tag1"));
    byte[] packet = PacketCodec.writeSyncServerQuests(List.of(quest), ProtocolVersion.V0);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_SERVER_QUESTS, PacketCodec.readType(reader));
    List<QuestData> decoded = PacketCodec.readSyncServerQuests(reader);
    assertEquals(1, decoded.size());
    assertTrue(decoded.get(0).tags().isEmpty());
  }
```

### Step 7: Run tests

```bash
./gradlew :common:test
```

Expected: all tests pass, including the new versioning tests.

### Step 8: Commit

```
feat(common): add versioned writeQuest/writeHandshake and SYNC_TAGS codec methods
```

---

## Task 3: Server-side protocol version tracking and versioned packet sending

**Files:**
- `server/src/main/java/com/disqt/disquests/server/papermc/ServerPacketHandler.java` (modify)

### Step 1: Add per-player protocol version map

Add a field to `ServerPacketHandler`:

```java
  private final Map<UUID, Integer> playerProtocolVersions = new java.util.concurrent.ConcurrentHashMap<>();
```

Add a helper method:

```java
  private int getProtocolVersion(Player player) {
    return playerProtocolVersions.getOrDefault(player.getUniqueId(), ProtocolVersion.V0);
  }
```

### Step 2: Read protocol version from REQUEST_SYNC

Change `handleRequestSync` to accept the `ByteBufReader` so we can read the version:

In `onPluginMessageReceived`, change:
```java
        case REQUEST_SYNC -> handleRequestSync(player);
```
to:
```java
        case REQUEST_SYNC -> handleRequestSync(player, buf);
```

Update `handleRequestSync` signature and body:

```java
  private void handleRequestSync(Player player, ByteBufReader buf) {
    int protocolVersion = PacketCodec.readRequestSyncVersion(buf);
    playerProtocolVersions.put(player.getUniqueId(), protocolVersion);

    UUID uuid = player.getUniqueId();
    List<QuestData> myQuests = dataManager.getQuestsForPlayer(uuid);
    List<QuestData> serverQuests = dataManager.getServerQuests(uuid);
    Map<UUID, Integer> pendingCounts = dataManager.getPendingCountByQuest(uuid);
    List<CollaborationRequestData> pendingRequests = dataManager.getPendingRequestsForOwner(uuid);

    List<QuestData> resolvedMyQuests =
        myQuests.stream().map(q -> resolveWikiLinks(q, uuid)).toList();
    List<QuestData> resolvedServerQuests =
        serverQuests.stream().map(q -> resolveWikiLinks(q, uuid)).toList();

    sendPacket(
        player,
        PacketCodec.writeSyncMyQuests(resolvedMyQuests, pendingCounts, protocolVersion));
    sendPacket(
        player, PacketCodec.writeSyncServerQuests(resolvedServerQuests, protocolVersion));
    sendPacket(player, PacketCodec.writeSyncPendingRequests(pendingRequests));

    // Send SYNC_TAGS to v1+ clients
    if (protocolVersion >= ProtocolVersion.V1) {
      List<String> allTags = dataManager.getAllDistinctTags(config.getPredefinedTags());
      sendPacket(player, PacketCodec.writeSyncTags(allTags));
    }
  }
```

### Step 3: Update sendHandshake to use protocol version

```java
  private void sendHandshake(Player player) {
    int protocolVersion = getProtocolVersion(player);
    List<UUID> pinnedIds = dataManager.getPinnedQuestIds(player.getUniqueId());
    int pendingCount = dataManager.getPendingRequestCount(player.getUniqueId());
    sendPacket(
        player,
        PacketCodec.writeHandshake(
            config.getBluemapUrl(),
            pendingCount,
            pinnedIds,
            player.getUniqueId(),
            config.getBluemapMapNames(),
            config.getPredefinedTags(),
            protocolVersion));
  }
```

### Step 4: Update all broadcastQuestUpdate and other S2C sends to use per-player protocol version

Every place that calls `PacketCodec.writeUpdateQuest(quest)` or sends quest data must now use the versioned overload. Replace all occurrences with per-player versioned sends.

**broadcastQuestUpdate:**
```java
  private void broadcastQuestUpdate(QuestData quest) {
    if (quest.visibility() == Visibility.PRIVATE) {
      Player owner = Bukkit.getPlayer(quest.ownerUuid());
      if (owner != null && isModPlayer(owner)) {
        int pv = getProtocolVersion(owner);
        sendPacket(
            owner,
            PacketCodec.writeUpdateQuest(resolveWikiLinks(quest, quest.ownerUuid()), pv));
      }
      for (ContributorData c : quest.contributors()) {
        Player p = Bukkit.getPlayer(c.uuid());
        if (p != null && isModPlayer(p)) {
          int pv = getProtocolVersion(p);
          sendPacket(p, PacketCodec.writeUpdateQuest(resolveWikiLinks(quest, c.uuid()), pv));
        }
      }
    } else {
      for (Player p : getModPlayers()) {
        int pv = getProtocolVersion(p);
        sendPacket(
            p, PacketCodec.writeUpdateQuest(resolveWikiLinks(quest, p.getUniqueId()), pv));
      }
    }
  }
```

**handleSaveQuest** -- the two `sendPacket` calls that use `writeUpdateQuest`:
```java
      // In the "new quest" branch:
      sendPacket(
          player,
          PacketCodec.writeUpdateQuest(
              resolveWikiLinks(saved, playerUuid), getProtocolVersion(player)));

      // broadcastQuestUpdate already handles versioning (updated above)
```

**handleJoinQuest:**
```java
    sendPacket(
        player,
        PacketCodec.writeUpdateQuest(
            resolveWikiLinks(updated, player.getUniqueId()), getProtocolVersion(player)));
```

**handleRespondCollaboration:**
```java
        // approved branch:
        sendPacket(
            requester,
            PacketCodec.writeCollaborationResponse(
                quest.id(),
                true,
                resolveWikiLinks(updated, request.requesterUuid()),
                getProtocolVersion(requester)));

        // denied branch:
        sendPacket(
            requester,
            PacketCodec.writeCollaborationResponse(
                quest.id(), false, null, getProtocolVersion(requester)));
```

**handleUpdateContributors** -- the per-target sends and the owner send:
```java
        // Inside the notifyPlayers loop:
        sendPacket(
            target,
            PacketCodec.writeUpdateQuest(
                resolveWikiLinks(updated, targetUuid), getProtocolVersion(target)));

        // Owner send at end:
        sendPacket(
            player,
            PacketCodec.writeUpdateQuest(
                resolveWikiLinks(updated, player.getUniqueId()), getProtocolVersion(player)));
```

**handleUpdateVisibility** -- all the per-player sends:
```java
      // Private -> Open/Closed:
      for (Player p : getModPlayers()) {
        sendPacket(
            p,
            PacketCodec.writeUpdateQuest(
                resolveWikiLinks(updated, p.getUniqueId()), getProtocolVersion(p)));
      }

      // Open/Closed -> Private (owner):
      sendPacket(
          player,
          PacketCodec.writeUpdateQuest(
              resolveWikiLinks(updated, player.getUniqueId()), getProtocolVersion(player)));

      // Open/Closed -> Private (contributors):
      for (ContributorData c : updated.contributors()) {
        Player p = Bukkit.getPlayer(c.uuid());
        if (p != null && isModPlayer(p)) {
          sendPacket(
              p,
              PacketCodec.writeUpdateQuest(
                  resolveWikiLinks(updated, c.uuid()), getProtocolVersion(p)));
        }
      }
```

### Step 5: Clean up player version on disconnect

Add an event listener for player quit:

```java
  @EventHandler
  public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
    playerProtocolVersions.remove(event.getPlayer().getUniqueId());
  }
```

### Step 6: Verify compilation

```bash
./gradlew :server:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 7: Commit

```
feat(server): track per-player protocol version, send versioned packets
```

---

## Task 4: DB migration for quest_tags table

**Files:**
- `server/src/main/java/com/disqt/disquests/server/papermc/DataManager.java` (modify)

### Step 1: Add migrateQuestTags method

The `quest_tags` table is already in `createTables()`, so it is created for new installs. For existing installs that were created before tags were added, `CREATE TABLE IF NOT EXISTS` already handles this -- the table is created if missing. No additional migration is needed because `createTables()` uses `IF NOT EXISTS` for all tables including `quest_tags`.

**Verify this is true:** Looking at `createTables()`, line 104-111:
```java
      stmt.executeUpdate(
          """
                    CREATE TABLE IF NOT EXISTS quest_tags (
                        quest_id TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        PRIMARY KEY (quest_id, tag),
                        FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
                    )""");
```

Yes, `IF NOT EXISTS` is already there. The DB migration is already handled by the existing code. No changes needed.

### Step 2: Add getAllDistinctTags method

Add a new method to `DataManager` that returns all distinct tags from the DB merged with config seed tags:

```java
  public synchronized List<String> getAllDistinctTags(List<String> seedTags) {
    java.util.Set<String> tags = new java.util.TreeSet<>();
    for (String seed : seedTags) {
      tags.add(seed.toLowerCase());
    }
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT DISTINCT tag FROM quest_tags")) {
      while (rs.next()) {
        tags.add(rs.getString("tag"));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get distinct tags", e);
    }
    return new ArrayList<>(tags);
  }
```

Using `TreeSet` for alphabetical ordering and deduplication.

### Step 3: Verify compilation

```bash
./gradlew :server:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 4: Commit

```
feat(server): add getAllDistinctTags for SYNC_TAGS packet
```

---

## Task 5: Config defaults for predefined-tags

**Files:**
- `server/src/main/java/com/disqt/disquests/server/papermc/Config.java` (modify)

### Step 1: Add seed tag defaults when key is missing

In `Config.reload()`, after loading `predefinedTags`, check if the key exists. If not, write seed defaults and save:

```java
  public void reload(JavaPlugin plugin) {
    plugin.reloadConfig();
    FileConfiguration cfg = plugin.getConfig();
    this.bluemapUrl = cfg.getString("bluemap-url", "");
    this.bluemapMapNames = new HashMap<>();
    if (cfg.isConfigurationSection("bluemap-map-names")) {
      var section = cfg.getConfigurationSection("bluemap-map-names");
      for (String key : section.getKeys(false)) {
        bluemapMapNames.put(key, section.getString(key));
      }
    }
    if (!cfg.contains("predefined-tags")) {
      List<String> defaults = List.of("building", "expedition");
      cfg.set("predefined-tags", defaults);
      plugin.saveConfig();
      this.predefinedTags = defaults;
    } else {
      this.predefinedTags = cfg.getStringList("predefined-tags");
      if (this.predefinedTags == null) this.predefinedTags = List.of();
    }
    this.debug = cfg.getBoolean("debug", false);
    if (Boolean.getBoolean("disquests.debug")) {
      this.debug = true;
    }
  }
```

Note: The existing `config.yml` already has `predefined-tags` with 6 entries (overworld, nether, the_end, building, redstone, farm). This code only writes defaults if the key is entirely absent (i.e., upgrading from a version that didn't have it). The seed defaults here are `building` and `expedition` as specified in the design spec.

### Step 2: Verify compilation

```bash
./gradlew :server:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 3: Commit

```
feat(server): write seed predefined-tags to config when key is missing
```

---

## Task 6: Client sends protocol version in REQUEST_SYNC

**Files:**
- `client/src/main/java/com/disqt/disquests/client/network/PacketSender.java` (modify)

### Step 1: Update requestSync to send protocol version

Change `PacketSender.requestSync()`:

```java
  public static void requestSync() {
    send(PacketCodec.writeRequestSync(ProtocolVersion.CURRENT));
  }
```

Add the import:
```java
import com.disqt.disquests.common.ProtocolVersion;
```

### Step 2: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 3: Commit

```
feat(client): send protocol version in REQUEST_SYNC
```

---

## Task 7: Client handles SYNC_TAGS packet

**Files:**
- `client/src/main/java/com/disqt/disquests/client/ClientSession.java` (modify)
- `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java` (modify)

### Step 1: Add serverTags field to ClientSession

Add a new field alongside `predefinedTags`:

```java
  private static List<String> serverTags = List.of();
```

Add getter/setter:

```java
  public static List<String> getServerTags() {
    return serverTags;
  }

  public static void setServerTags(List<String> tags) {
    serverTags = tags != null ? List.copyOf(tags) : List.of();
  }
```

In `leaveServer()`, add:
```java
    serverTags = List.of();
```

### Step 2: Handle SYNC_TAGS in ClientPacketHandler

Add a case to the switch in `handleRawPayload`:

```java
                  case SYNC_TAGS -> handleSyncTags(r);
```

Add the handler method:

```java
  private static void handleSyncTags(ByteBufReader r) {
    List<String> tags = PacketCodec.readSyncTags(r);
    ClientSession.setServerTags(tags);
    LOGGER.debug("Received {} server tags", tags.size());
  }
```

### Step 3: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 4: Commit

```
feat(client): handle SYNC_TAGS packet, store in ClientSession.serverTags
```

---

## Task 8: Run full test suite and format

### Step 1: Format all code

```bash
./gradlew spotlessApply
```

### Step 2: Run common unit tests

```bash
./gradlew :common:test
```

Expected: all tests pass, including the new protocol versioning tests from Task 2.

### Step 3: Run full build

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL for all modules.

### Step 4: Run E2E tests

```bash
./gradlew :client:runIntegrationTest
```

Expected: all solo + duo journey tests pass. The E2E test server uses the updated Paper plugin with protocol version tracking. The test clients send v1 REQUEST_SYNC, server responds with v1-format packets including tags. Existing test journeys should not break because tag fields are empty lists by default.

### Step 5: Commit

```
chore: format and verify all tests pass
```

---

## Summary of changes by file

| File | Action | What changes |
|------|--------|-------------|
| `common/.../ProtocolVersion.java` | Create | V0=0, V1=1, CURRENT=V1 constants |
| `common/.../PacketType.java` | Modify | Add `SYNC_TAGS((byte) 0x18)` |
| `common/.../PacketCodec.java` | Modify | Add `writeQuest(buf, quest, protocolVersion)`, versioned overloads for `writeHandshake`, `writeSyncMyQuests`, `writeSyncServerQuests`, `writeUpdateQuest`, `writeCollaborationResponse`; add `writeRequestSync(int)`, `readRequestSyncVersion`; add `writeSyncTags`/`readSyncTags`; add `remaining()` guard to `readQuest` tags field |
| `common/.../PacketCodecTest.java` | Modify | 11 new tests: v0/v1 writeQuest, v0/v1 handshake, requestSync version, syncTags, versioned list packets |
| `server/.../ServerPacketHandler.java` | Modify | `playerProtocolVersions` map, read version from REQUEST_SYNC, all S2C sends use per-player version, send SYNC_TAGS to v1+, cleanup on quit |
| `server/.../DataManager.java` | Modify | Add `getAllDistinctTags(List<String> seedTags)` |
| `server/.../Config.java` | Modify | Write seed `predefined-tags` if missing from config |
| `client/.../PacketSender.java` | Modify | `requestSync()` sends `ProtocolVersion.CURRENT` |
| `client/.../ClientSession.java` | Modify | Add `serverTags` field, getter/setter, clear on leave |
| `client/.../ClientPacketHandler.java` | Modify | Handle `SYNC_TAGS` case, call `ClientSession.setServerTags` |

## Known limitations

- **New client + old server (server not upgraded):** The old server ignores the version VarInt in REQUEST_SYNC (it doesn't read the buf at all) and sends v0-format packets. The new client's `readQuest` has a `remaining()` guard for tags, so single-quest packets decode correctly with empty tags. For list packets (SYNC_MY_QUESTS, SYNC_SERVER_QUESTS), the guard may misfire if there are multiple quests, but the bounded `readCount` check (MAX_TAGS=8) and the outer try/catch in `ClientPacketHandler` prevent corruption. The practical impact is: a new client connecting to a non-upgraded server may fail to load quest lists but will not crash. This is acceptable because the deployment plan is server-first.
