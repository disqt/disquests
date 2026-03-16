# Disquests Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite BuildNotes into Disquests -- a unified quest system with server-only storage, per-quest permissions, markdown content, collaboration, and BlueMap integration.

**Architecture:** Monorepo with three Gradle subprojects: `common` (shared data models + packet codec), `client` (Fabric mod), `paper` (Paper plugin). All quest data lives in Paper's SQLite database. Client communicates via plugin messages on `disquests:main`. Markdown rendering uses commonmark-java shaded into the client JAR.

**Tech Stack:** Java 21, Gradle (Kotlin DSL), Fabric API 1.21.11, Paper API 1.21.11, SQLite (JDBC), commonmark-java 0.24.0.

**Spec:** `docs/superpowers/specs/2026-03-15-disquests-design.md`

**Important notes:**
- This is a rewrite, not an incremental change. Old BuildNotes code will be gutted and replaced module by module.
- Package namespaces change: `com.disqt.buildnotes.*` -> `com.disqt.disquests.*` (common + paper), `net.atif.buildnotes.*` -> `com.disqt.disquests.client.*` (client).
- The existing E2E test framework (`client/src/testmod/`) will break during the rewrite. It will be updated in the final chunk.
- Each chunk produces a compilable state. The system is not fully functional until all chunks are complete, but each chunk can be built and unit-tested independently.

---

## File Structure

### Files to Delete (old BuildNotes code)

```
common/src/main/java/com/disqt/buildnotes/          # entire old package
client/src/main/java/net/atif/buildnotes/            # entire old package
paper/src/main/java/com/disqt/buildnotes/             # entire old package
```

### New File Structure

```
common/src/main/java/com/disqt/disquests/common/
  PacketType.java                    # Enum: all C2S + S2C packet type IDs
  PacketCodec.java                   # Encode/decode all packets
  ByteBufReader.java                 # Keep existing, update package
  ByteBufWriter.java                 # Keep existing, update package
  model/
    QuestData.java                   # Quest wire format (record)
    ContributorData.java             # Contributor wire format (record)
    CoordinatesData.java             # Coordinates wire format (record)
    Visibility.java                  # Enum: PRIVATE, CLOSED, OPEN
    CollaborationRequestData.java    # Collab request wire format (record)
    ContributorOp.java               # Enum: ADD, REMOVE, UPDATE

common/src/test/java/com/disqt/disquests/common/
  PacketCodecTest.java               # Round-trip tests for all packet types

paper/src/main/java/com/disqt/disquests/paper/
  DisquestsPlugin.java               # Plugin entry point
  Config.java                        # Load config.yml (bluemap-url)
  DataManager.java                   # SQLite CRUD for all tables
  ServerPacketHandler.java           # Handle C2S, send S2C, broadcast rules
  Commands.java                      # /disquests reload
  PlayerNameTracker.java             # PlayerJoinEvent -> upsert player_names
paper/src/main/resources/
  plugin.yml                         # Renamed from buildnotes to disquests
  config.yml                         # Default config (bluemap-url)

client/src/main/java/com/disqt/disquests/client/
  DisquestsClient.java               # ClientModInitializer entry point
  ClientSession.java                 # Server state: bluemap URL, pending count, pinned ID
  ClientCache.java                   # In-memory quest storage (my quests + server quests)
  KeyBinds.java                      # Keybind registration
  network/
    ClientPacketHandler.java         # Handle S2C packets -> update cache/session
    PacketSender.java                # Send C2S packets (static helpers)
    RawPayload.java                  # Keep existing CustomPayload wrapper, update package
  data/
    Quest.java                       # Client-side quest model (mutable, for UI)
    Contributor.java                 # Client-side contributor model
  gui/
    helper/
      Colors.java                    # Keep existing, update package
      UIHelper.java                  # Keep existing, update package
      ScreenLayouts.java             # Layout constants (merged from Note/Build/Main layouts)
    screen/
      BaseScreen.java               # Keep existing, update package
      MainScreen.java               # Rewritten: two tabs, quest list, filters
      ViewQuestScreen.java          # Rewritten: markdown rendered view
      EditQuestScreen.java          # Rewritten: title + content + optional fields + visibility
      ContributorScreen.java        # New: manage contributors, approve requests, invite
      ConfirmScreen.java            # Keep existing, update package
    widget/
      DarkButtonWidget.java          # Keep existing, update package
      TabButtonWidget.java           # Keep existing, update package
      MultiLineTextFieldWidget.java  # Keep existing, update package
      ReadOnlyMultiLineTextFieldWidget.java  # Keep existing, update package
      QuestListWidget.java           # Rewritten: unified quest list (replaces Note/BuildListWidget)
      MarkdownWidget.java            # New: scrollable rendered markdown display
  BlueMapHelper.java                # Construct BlueMap URLs from quest coordinates
  markdown/
    MarkdownRenderer.java            # commonmark AST -> List<RenderedLine>
    RenderedLine.java                # One line of rendered markdown (MutableText + indent + height)
  hud/
    HudPinManager.java               # Rewritten: server-side pin state
    HudPinRenderer.java              # Keep existing, update package
  mixin/
    InGameHudMixin.java              # Keep existing, update package
    InventoryBadgeMixin.java         # New: render badge on inventory screen

client/src/main/resources/
  fabric.mod.json                    # Update mod ID, entrypoint, mixin config
  disquests.mixins.json              # Rename from buildnotes.mixins.json
```

---

## Chunk 1: Rebrand + Common Module

This chunk renames the project, creates the new common data models and packet codec, and verifies everything compiles with unit tests.

### Task 1: Rename project and update Gradle config

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle.properties`
- Modify: `build.gradle.kts` (no changes needed, just verify)

- [ ] **Step 1: Update settings.gradle.kts**

Change `rootProject.name`:
```kotlin
rootProject.name = "disquests"
```

- [ ] **Step 2: Update gradle.properties**

Change:
```properties
maven_group=com.disqt.disquests
archives_base_name=disquests
```

- [ ] **Step 3: Verify Gradle sync**

```bash
./gradlew :common:classes
```

Expected: BUILD SUCCESSFUL (no source files yet in new package).

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts gradle.properties
git commit -m "chore: rename project to disquests"
```

### Task 2: Create common data models

**Files:**
- Create: `common/src/main/java/com/disqt/disquests/common/model/Visibility.java`
- Create: `common/src/main/java/com/disqt/disquests/common/model/CoordinatesData.java`
- Create: `common/src/main/java/com/disqt/disquests/common/model/ContributorData.java`
- Create: `common/src/main/java/com/disqt/disquests/common/model/QuestData.java`
- Create: `common/src/main/java/com/disqt/disquests/common/model/CollaborationRequestData.java`
- Create: `common/src/main/java/com/disqt/disquests/common/model/ContributorOp.java`

- [ ] **Step 1: Create Visibility enum**

```java
package com.disqt.disquests.common.model;

public enum Visibility {
    PRIVATE,
    CLOSED,
    OPEN
}
```

- [ ] **Step 2: Create CoordinatesData record**

```java
package com.disqt.disquests.common.model;

public record CoordinatesData(double x, double y, double z) {}
```

- [ ] **Step 3: Create ContributorData record**

```java
package com.disqt.disquests.common.model;

import java.util.UUID;

public record ContributorData(UUID uuid, String name, boolean canEdit) {}
```

- [ ] **Step 4: Create QuestData record**

```java
package com.disqt.disquests.common.model;

import java.util.List;
import java.util.UUID;

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
    String map
) {}
```

- [ ] **Step 5: Create CollaborationRequestData record**

```java
package com.disqt.disquests.common.model;

import java.util.UUID;

public record CollaborationRequestData(
    UUID id,
    UUID questId,
    String questTitle,
    UUID requesterUuid,
    String requesterName,
    long timestamp
) {}
```

- [ ] **Step 6: Create ContributorOp enum**

```java
package com.disqt.disquests.common.model;

public enum ContributorOp {
    ADD,
    REMOVE,
    UPDATE
}
```

- [ ] **Step 7: Verify compilation**

```bash
./gradlew :common:classes
```

- [ ] **Step 8: Commit**

```bash
git add common/src/main/java/com/disqt/disquests/
git commit -m "feat(common): add Disquests data models"
```

### Task 3: Create PacketType enum

**Files:**
- Create: `common/src/main/java/com/disqt/disquests/common/PacketType.java`

- [ ] **Step 1: Create the enum**

```java
package com.disqt.disquests.common;

public enum PacketType {
    // C2S
    REQUEST_SYNC((byte) 0x01),
    SAVE_QUEST((byte) 0x02),
    DELETE_QUEST((byte) 0x03),
    JOIN_QUEST((byte) 0x04),
    REQUEST_COLLABORATION((byte) 0x05),
    RESPOND_COLLABORATION((byte) 0x06),
    UPDATE_CONTRIBUTORS((byte) 0x07),
    UPDATE_VISIBILITY((byte) 0x08),
    PIN_QUEST((byte) 0x09),

    // S2C
    HANDSHAKE((byte) 0x10),
    SYNC_MY_QUESTS((byte) 0x11),
    SYNC_SERVER_QUESTS((byte) 0x12),
    UPDATE_QUEST((byte) 0x13),
    DELETE_QUEST_S2C((byte) 0x14),
    COLLABORATION_REQUEST((byte) 0x15),
    COLLABORATION_RESPONSE((byte) 0x16);

    private final byte id;

    PacketType(byte id) { this.id = id; }

    public byte getId() { return id; }

    public static PacketType fromId(byte id) {
        for (PacketType type : values()) {
            if (type.id == id) return type;
        }
        throw new IllegalArgumentException("Unknown packet type: 0x" + String.format("%02X", id));
    }
}
```

Note: PIN_QUEST (0x09) is a C2S packet for the client to tell the server which quest to pin. Not in the original spec packet table but needed since pin state is server-side.

- [ ] **Step 2: Commit**

```bash
git add common/src/main/java/com/disqt/disquests/common/PacketType.java
git commit -m "feat(common): add Disquests packet type enum"
```

### Task 4: Copy and rename ByteBufReader/Writer

**Files:**
- Create: `common/src/main/java/com/disqt/disquests/common/ByteBufReader.java`
- Create: `common/src/main/java/com/disqt/disquests/common/ByteBufWriter.java`

- [ ] **Step 1: Copy existing files with new package**

Copy `common/src/main/java/com/disqt/buildnotes/common/ByteBufReader.java` and `ByteBufWriter.java` to the new package `com.disqt.disquests.common`, updating only the `package` declaration.

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :common:classes
```

- [ ] **Step 3: Commit**

```bash
git add common/src/main/java/com/disqt/disquests/common/ByteBuf*.java
git commit -m "feat(common): copy ByteBufReader/Writer to new package"
```

### Task 5: Create PacketCodec

**Files:**
- Create: `common/src/main/java/com/disqt/disquests/common/PacketCodec.java`

This is the largest file in common. It encodes and decodes all packet types. The pattern is the same as the existing BuildNotes codec but with new packet types.

- [ ] **Step 1: Create PacketCodec with all encode/decode methods**

Structure:
```java
package com.disqt.disquests.common;

import com.disqt.disquests.common.model.*;
import java.util.*;

public final class PacketCodec {
    private PacketCodec() {}

    // --- Shared helpers ---
    private static void writeQuest(ByteBufWriter buf, QuestData quest) { ... }
    private static QuestData readQuest(ByteBufReader buf) { ... }
    private static void writeCoords(ByteBufWriter buf, CoordinatesData coords) { ... }
    private static CoordinatesData readCoords(ByteBufReader buf) { ... }
    private static void writeContributor(ByteBufWriter buf, ContributorData c) { ... }
    private static ContributorData readContributor(ByteBufReader buf) { ... }

    // --- C2S encode ---
    public static byte[] writeRequestSync() { ... }
    public static byte[] writeSaveQuest(UUID questId, String title, String content,
        CoordinatesData coords, boolean isRegion, CoordinatesData coords2, String map) { ... }
    public static byte[] writeDeleteQuest(UUID questId) { ... }
    public static byte[] writeJoinQuest(UUID questId) { ... }
    public static byte[] writeRequestCollaboration(UUID questId) { ... }
    public static byte[] writeRespondCollaboration(UUID requestId, boolean approved) { ... }
    public static byte[] writeUpdateContributors(UUID questId,
        List<ContributorOpEntry> ops) { ... }
    public static byte[] writeUpdateVisibility(UUID questId, Visibility visibility) { ... }
    public static byte[] writePinQuest(UUID questId) { ... } // null = unpin

    // --- S2C encode ---
    public static byte[] writeHandshake(String bluemapUrl, int pendingRequestCount,
        UUID pinnedQuestId) { ... }
    public static byte[] writeSyncMyQuests(List<QuestData> quests) { ... }
    public static byte[] writeSyncServerQuests(List<QuestData> quests) { ... }
    public static byte[] writeUpdateQuest(QuestData quest) { ... }
    public static byte[] writeDeleteQuestS2C(UUID questId) { ... }
    public static byte[] writeCollaborationRequest(UUID requestId, UUID questId,
        String questTitle, String requesterName) { ... }
    public static byte[] writeCollaborationResponse(UUID questId, boolean approved,
        QuestData quest) { ... }

    // --- C2S decode ---
    public static SaveQuestPayload decodeSaveQuest(ByteBufReader buf) { ... }
    public static UUID decodeDeleteQuest(ByteBufReader buf) { ... }
    public static UUID decodeJoinQuest(ByteBufReader buf) { ... }
    public static UUID decodeRequestCollaboration(ByteBufReader buf) { ... }
    public static RespondCollaborationPayload decodeRespondCollaboration(ByteBufReader buf) { ... }
    public static UpdateContributorsPayload decodeUpdateContributors(ByteBufReader buf) { ... }
    public static UpdateVisibilityPayload decodeUpdateVisibility(ByteBufReader buf) { ... }
    public static UUID decodePinQuest(ByteBufReader buf) { ... } // null = unpin

    // --- S2C decode ---
    public static HandshakePayload decodeHandshake(ByteBufReader buf) { ... }
    public static List<QuestData> decodeSyncMyQuests(ByteBufReader buf) { ... }
    public static List<QuestData> decodeSyncServerQuests(ByteBufReader buf) { ... }
    public static QuestData decodeUpdateQuest(ByteBufReader buf) { ... }
    public static UUID decodeDeleteQuestS2C(ByteBufReader buf) { ... }
    public static CollaborationRequestPayload decodeCollaborationRequest(ByteBufReader buf) { ... }
    public static CollaborationResponsePayload decodeCollaborationResponse(ByteBufReader buf) { ... }

    // --- Payload records (for multi-field decode results) ---
    public record SaveQuestPayload(UUID questId, String title, String content,
        CoordinatesData coords, boolean isRegion, CoordinatesData coords2, String map) {}
    public record RespondCollaborationPayload(UUID requestId, boolean approved) {}
    public record UpdateContributorsPayload(UUID questId, List<ContributorOpEntry> ops) {}
    public record ContributorOpEntry(ContributorOp action, UUID playerUuid,
        String playerName, boolean canEdit) {}
    // For ADD ops: playerName is set (server resolves to UUID), playerUuid may be null
    // For REMOVE/UPDATE ops: playerUuid is set, playerName may be null
    public record UpdateVisibilityPayload(UUID questId, Visibility visibility) {}
    public record HandshakePayload(String bluemapUrl, int pendingRequestCount,
        UUID pinnedQuestId) {}
    public record CollaborationRequestPayload(UUID requestId, UUID questId,
        String questTitle, String requesterName) {}
    public record CollaborationResponsePayload(UUID questId, boolean approved,
        QuestData quest) {}
}
```

Key encoding patterns:
- Nullable fields: write a boolean `hasValue` flag, then the value if true
- Nullable UUID: write boolean, then UUID bytes if present
- Nullable String: write boolean, then string if present (or write empty string -- pick one pattern and be consistent)
- Lists: write VarInt count, then each element
- Coordinates: 3 doubles (x, y, z)

Use the **boolean flag** pattern for all nullable fields for consistency.

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :common:classes
```

- [ ] **Step 3: Commit**

```bash
git add common/src/main/java/com/disqt/disquests/common/PacketCodec.java
git commit -m "feat(common): add Disquests packet codec with all encode/decode methods"
```

### Task 6: Write PacketCodec unit tests

**Files:**
- Create: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`

- [ ] **Step 1: Write round-trip tests for each packet type**

Test pattern: encode -> decode -> assert all fields match. Test both with and without nullable fields.

```java
package com.disqt.disquests.common;

import com.disqt.disquests.common.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTest {

    @Test
    void testHandshakeRoundTrip() {
        UUID pinnedId = UUID.randomUUID();
        byte[] bytes = PacketCodec.writeHandshake("https://example.com/map", 3, pinnedId);
        ByteBufReader buf = new ByteBufReader(bytes);
        assertEquals(PacketType.HANDSHAKE, PacketType.fromId(buf.readByte()));
        var payload = PacketCodec.decodeHandshake(buf);
        assertEquals("https://example.com/map", payload.bluemapUrl());
        assertEquals(3, payload.pendingRequestCount());
        assertEquals(pinnedId, payload.pinnedQuestId());
    }

    @Test
    void testHandshakeNoPinnedQuest() {
        byte[] bytes = PacketCodec.writeHandshake("", 0, null);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte(); // skip type
        var payload = PacketCodec.decodeHandshake(buf);
        assertEquals("", payload.bluemapUrl());
        assertEquals(0, payload.pendingRequestCount());
        assertNull(payload.pinnedQuestId());
    }

    @Test
    void testQuestRoundTrip() {
        QuestData quest = new QuestData(
            UUID.randomUUID(), "Test Quest", "# Hello\n- item 1",
            UUID.randomUUID(), "Steve", Visibility.OPEN,
            List.of(new ContributorData(UUID.randomUUID(), "Alex", true)),
            System.currentTimeMillis() / 1000,
            new CoordinatesData(100.5, 64.0, -200.3),
            false, null, "world_new"
        );
        byte[] bytes = PacketCodec.writeUpdateQuest(quest);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte(); // skip type
        QuestData decoded = PacketCodec.decodeUpdateQuest(buf);
        assertEquals(quest.id(), decoded.id());
        assertEquals(quest.title(), decoded.title());
        assertEquals(quest.content(), decoded.content());
        assertEquals(quest.ownerName(), decoded.ownerName());
        assertEquals(quest.visibility(), decoded.visibility());
        assertEquals(1, decoded.contributors().size());
        assertEquals(quest.contributors().get(0).name(), decoded.contributors().get(0).name());
        assertNotNull(decoded.coordinates());
        assertEquals(100.5, decoded.coordinates().x(), 0.001);
        assertFalse(decoded.isRegion());
        assertNull(decoded.coordinates2());
        assertEquals("world_new", decoded.map());
    }

    @Test
    void testQuestWithRegion() {
        QuestData quest = new QuestData(
            UUID.randomUUID(), "Region Quest", "",
            UUID.randomUUID(), "Steve", Visibility.PRIVATE,
            List.of(), System.currentTimeMillis() / 1000,
            new CoordinatesData(0, 64, 0), true,
            new CoordinatesData(100, 80, 100), "creative"
        );
        byte[] bytes = PacketCodec.writeUpdateQuest(quest);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        QuestData decoded = PacketCodec.decodeUpdateQuest(buf);
        assertTrue(decoded.isRegion());
        assertNotNull(decoded.coordinates2());
        assertEquals(100, decoded.coordinates2().x(), 0.001);
    }

    @Test
    void testQuestNoOptionalFields() {
        QuestData quest = new QuestData(
            UUID.randomUUID(), "Simple Quest", "Just text",
            UUID.randomUUID(), "Steve", Visibility.PRIVATE,
            List.of(), System.currentTimeMillis() / 1000,
            null, false, null, null
        );
        byte[] bytes = PacketCodec.writeUpdateQuest(quest);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        QuestData decoded = PacketCodec.decodeUpdateQuest(buf);
        assertNull(decoded.coordinates());
        assertNull(decoded.coordinates2());
        assertNull(decoded.map());
    }

    @Test
    void testSyncMyQuestsRoundTrip() {
        List<QuestData> quests = List.of(
            new QuestData(UUID.randomUUID(), "Q1", "c1", UUID.randomUUID(), "A",
                Visibility.PRIVATE, List.of(), 1000, null, false, null, null),
            new QuestData(UUID.randomUUID(), "Q2", "c2", UUID.randomUUID(), "B",
                Visibility.OPEN, List.of(), 2000, null, false, null, "world_new")
        );
        byte[] bytes = PacketCodec.writeSyncMyQuests(quests);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        List<QuestData> decoded = PacketCodec.decodeSyncMyQuests(buf);
        assertEquals(2, decoded.size());
        assertEquals("Q1", decoded.get(0).title());
        assertEquals("Q2", decoded.get(1).title());
    }

    @Test
    void testSaveQuestC2SRoundTrip() {
        UUID questId = UUID.randomUUID();
        var coords = new CoordinatesData(10, 20, 30);
        byte[] bytes = PacketCodec.writeSaveQuest(questId, "Title", "Content",
            coords, false, null, "sandbox");
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        var payload = PacketCodec.decodeSaveQuest(buf);
        assertEquals(questId, payload.questId());
        assertEquals("Title", payload.title());
        assertEquals("Content", payload.content());
        assertEquals(10, payload.coords().x(), 0.001);
        assertFalse(payload.isRegion());
        assertNull(payload.coords2());
        assertEquals("sandbox", payload.map());
    }

    @Test
    void testDeleteQuestRoundTrip() {
        UUID id = UUID.randomUUID();
        byte[] bytes = PacketCodec.writeDeleteQuest(id);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        assertEquals(id, PacketCodec.decodeDeleteQuest(buf));
    }

    @Test
    void testRespondCollaborationRoundTrip() {
        UUID reqId = UUID.randomUUID();
        byte[] bytes = PacketCodec.writeRespondCollaboration(reqId, true);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        var payload = PacketCodec.decodeRespondCollaboration(buf);
        assertEquals(reqId, payload.requestId());
        assertTrue(payload.approved());
    }

    @Test
    void testUpdateVisibilityRoundTrip() {
        UUID questId = UUID.randomUUID();
        byte[] bytes = PacketCodec.writeUpdateVisibility(questId, Visibility.OPEN);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        var payload = PacketCodec.decodeUpdateVisibility(buf);
        assertEquals(questId, payload.questId());
        assertEquals(Visibility.OPEN, payload.visibility());
    }

    @Test
    void testCollaborationRequestS2CRoundTrip() {
        UUID reqId = UUID.randomUUID();
        UUID questId = UUID.randomUUID();
        byte[] bytes = PacketCodec.writeCollaborationRequest(reqId, questId,
            "Cool Build", "Alex");
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        var payload = PacketCodec.decodeCollaborationRequest(buf);
        assertEquals(reqId, payload.requestId());
        assertEquals(questId, payload.questId());
        assertEquals("Cool Build", payload.questTitle());
        assertEquals("Alex", payload.requesterName());
    }

    @Test
    void testCollaborationResponseApproved() {
        UUID questId = UUID.randomUUID();
        QuestData quest = new QuestData(questId, "Q", "", UUID.randomUUID(), "S",
            Visibility.CLOSED, List.of(), 1000, null, false, null, null);
        byte[] bytes = PacketCodec.writeCollaborationResponse(questId, true, quest);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        var payload = PacketCodec.decodeCollaborationResponse(buf);
        assertTrue(payload.approved());
        assertNotNull(payload.quest());
        assertEquals("Q", payload.quest().title());
    }

    @Test
    void testCollaborationResponseDenied() {
        UUID questId = UUID.randomUUID();
        byte[] bytes = PacketCodec.writeCollaborationResponse(questId, false, null);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        var payload = PacketCodec.decodeCollaborationResponse(buf);
        assertFalse(payload.approved());
        assertNull(payload.quest());
    }

    @Test
    void testPinQuestRoundTrip() {
        UUID questId = UUID.randomUUID();
        byte[] bytes = PacketCodec.writePinQuest(questId);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        assertEquals(questId, PacketCodec.decodePinQuest(buf));
    }

    @Test
    void testPinQuestUnpin() {
        byte[] bytes = PacketCodec.writePinQuest(null);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        assertNull(PacketCodec.decodePinQuest(buf));
    }

    @Test
    void testUpdateContributorsRoundTrip() {
        UUID questId = UUID.randomUUID();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        var ops = List.of(
            new PacketCodec.ContributorOpEntry(
                com.disqt.disquests.common.model.ContributorOp.ADD, player1, true),
            new PacketCodec.ContributorOpEntry(
                com.disqt.disquests.common.model.ContributorOp.REMOVE, player2, false)
        );
        byte[] bytes = PacketCodec.writeUpdateContributors(questId, ops);
        ByteBufReader buf = new ByteBufReader(bytes);
        buf.readByte();
        var payload = PacketCodec.decodeUpdateContributors(buf);
        assertEquals(questId, payload.questId());
        assertEquals(2, payload.ops().size());
        assertEquals(com.disqt.disquests.common.model.ContributorOp.ADD,
            payload.ops().get(0).action());
        assertTrue(payload.ops().get(0).canEdit());
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew :common:test
```

Expected: All tests PASS once PacketCodec is implemented.

- [ ] **Step 3: Commit**

```bash
git add common/src/test/java/com/disqt/disquests/
git commit -m "test(common): add PacketCodec round-trip tests for all packet types"
```

---

## Chunk 2: Paper Plugin

Rewrites the Paper plugin with the new schema, DataManager, packet handler, and supporting classes.

### Task 7: Create plugin.yml and Config

**Files:**
- Create: `paper/src/main/resources/plugin.yml`
- Create: `paper/src/main/java/com/disqt/disquests/paper/Config.java`

- [ ] **Step 1: Create plugin.yml**

```yaml
name: Disquests
version: '0.1.0'
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

- [ ] **Step 2: Create Config class**

```java
package com.disqt.disquests.paper;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Config {
    private String bluemapUrl;

    public Config(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        reload(plugin);
    }

    public void reload(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        this.bluemapUrl = cfg.getString("bluemap-url", "");
    }

    public String getBluemapUrl() { return bluemapUrl; }
}
```

- [ ] **Step 3: Create default config.yml resource**

Create `paper/src/main/resources/config.yml`:
```yaml
# BlueMap web map URL. Leave empty to disable BlueMap links.
bluemap-url: ""
```

- [ ] **Step 4: Commit**

```bash
git add paper/src/main/resources/ paper/src/main/java/com/disqt/disquests/paper/Config.java
git commit -m "feat(paper): add plugin.yml and config for Disquests"
```

### Task 8: Create DataManager (SQLite)

**Files:**
- Create: `paper/src/main/java/com/disqt/disquests/paper/DataManager.java`

This is the core persistence layer. All 5 tables from the spec.

- [ ] **Step 1: Create DataManager with full CRUD**

Key methods:
```java
package com.disqt.disquests.paper;

import com.disqt.disquests.common.model.*;
import java.sql.*;
import java.nio.file.*;
import java.util.*;

public class DataManager {
    private Connection connection;

    public DataManager(Path dataDir) { ... }
    public void initialize() { ... }  // create connection + tables
    private void createTables() { ... }  // all 5 CREATE TABLE statements from spec

    // Quests
    public void saveQuest(QuestData quest) { ... }  // INSERT ON CONFLICT UPDATE
    public boolean deleteQuest(UUID id) { ... }
    public QuestData getQuest(UUID id) { ... }
    public List<QuestData> getQuestsForPlayer(UUID playerUuid) { ... }  // owned + contributor
    public List<QuestData> getServerQuests(UUID excludePlayer) { ... }  // open + closed, not owned/contributed by player

    // Contributors
    public void addContributor(UUID questId, UUID playerUuid, boolean canEdit) { ... }
    public void removeContributor(UUID questId, UUID playerUuid) { ... }
    public void updateContributor(UUID questId, UUID playerUuid, boolean canEdit) { ... }
    public List<ContributorData> getContributors(UUID questId) { ... }
    public boolean isContributor(UUID questId, UUID playerUuid) { ... }

    // Collaboration requests
    public void createCollaborationRequest(UUID questId, UUID requesterUuid) { ... }
    public void deleteCollaborationRequest(UUID requestId) { ... }
    public CollaborationRequestData getCollaborationRequest(UUID requestId) { ... }
    public List<CollaborationRequestData> getPendingRequestsForOwner(UUID ownerUuid) { ... }
    public int getPendingRequestCount(UUID ownerUuid) { ... }

    // Pins
    public void pinQuest(UUID playerUuid, UUID questId) { ... }  // UPSERT
    public void unpinQuest(UUID playerUuid) { ... }
    public UUID getPinnedQuestId(UUID playerUuid) { ... }

    // Player names
    public void upsertPlayerName(UUID uuid, String name) { ... }
    public String getPlayerName(UUID uuid) { ... }
    public UUID getPlayerUuidByName(String name) { ... }

    // Visibility
    public void updateVisibility(UUID questId, Visibility visibility) { ... }

    public void close() { ... }
}
```

The quest loading methods need to join with `contributors` and `player_names` to build the full `QuestData` with contributor lists and denormalized names.

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :paper:classes
```

- [ ] **Step 3: Commit**

```bash
git add paper/src/main/java/com/disqt/disquests/paper/DataManager.java
git commit -m "feat(paper): add DataManager with SQLite CRUD for all tables"
```

### Task 8b: Write DataManager unit tests

**Files:**
- Create: `paper/src/test/java/com/disqt/disquests/paper/DataManagerTest.java`

- [ ] **Step 1: Add SQLite test dependency to paper build.gradle.kts**

Paper bundles SQLite at runtime, but tests need it on the test classpath:

```kotlin
testImplementation("org.xerial:sqlite-jdbc:3.47.1.0")
```

- [ ] **Step 2: Write DataManager tests**

Test with an in-memory or temp-file SQLite database. Cover:

```java
package com.disqt.disquests.paper;

import com.disqt.disquests.common.model.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DataManagerTest {
    private DataManager dm;
    private Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        tempDir = java.nio.file.Files.createTempDirectory("disquests-test");
        dm = new DataManager(tempDir);
        dm.initialize();
    }

    @AfterEach
    void cleanup() { dm.close(); }

    @Test void saveAndGetQuest() { ... }
    @Test void deleteQuest() { ... }
    @Test void getQuestsForPlayer_ownerAndContributor() { ... }
    @Test void getServerQuests_excludesOwnedAndContributed() { ... }
    @Test void addAndRemoveContributor() { ... }
    @Test void updateContributorEditPermission() { ... }
    @Test void collaborationRequest_createAndGet() { ... }
    @Test void collaborationRequest_duplicatePrevented() { ... }
    @Test void pinAndUnpinQuest() { ... }
    @Test void playerNameUpsert() { ... }
    @Test void getPlayerUuidByName_returnsNull_ifNotFound() { ... }
    @Test void deleteQuest_cascadesContributorsAndRequests() { ... }
    @Test void updateVisibility() { ... }
}
```

- [ ] **Step 3: Run tests**

```bash
./gradlew :paper:test
```

Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add paper/build.gradle.kts paper/src/test/
git commit -m "test(paper): add DataManager unit tests for all CRUD operations"
```

### Task 9: Create PlayerNameTracker

**Files:**
- Create: `paper/src/main/java/com/disqt/disquests/paper/PlayerNameTracker.java`

- [ ] **Step 1: Create event listener**

```java
package com.disqt.disquests.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerNameTracker implements Listener {
    private final DataManager dataManager;

    public PlayerNameTracker(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        dataManager.upsertPlayerName(
            event.getPlayer().getUniqueId(),
            event.getPlayer().getName()
        );
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add paper/src/main/java/com/disqt/disquests/paper/PlayerNameTracker.java
git commit -m "feat(paper): add PlayerNameTracker for name resolution"
```

### Task 10: Create ServerPacketHandler

**Files:**
- Create: `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java`

This is the most complex Paper class. It handles all C2S packets and implements the broadcast rules from the spec.

- [ ] **Step 1: Create ServerPacketHandler**

```java
package com.disqt.disquests.paper;

import com.disqt.disquests.common.*;
import com.disqt.disquests.common.model.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class ServerPacketHandler implements PluginMessageListener, Listener {
    private static final String CHANNEL = "disquests:main";
    private final DisquestsPlugin plugin;
    private final DataManager dataManager;
    private final Config config;

    // Methods:
    // onPluginMessageReceived() - dispatch by PacketType
    // handleRequestSync(Player) - send SYNC_MY_QUESTS + SYNC_SERVER_QUESTS
    // handleSaveQuest(Player, SaveQuestPayload) - validate ownership/permission, save, broadcast
    // handleDeleteQuest(Player, UUID) - validate ownership, delete, broadcast
    // handleJoinQuest(Player, UUID) - validate open, add contributor, notify
    // handleRequestCollaboration(Player, UUID) - validate closed, create request, notify owner
    // handleRespondCollaboration(Player, RespondCollaborationPayload) - validate ownership, approve/deny
    // handleUpdateContributors(Player, UpdateContributorsPayload) - validate ownership, apply ops
    // handleUpdateVisibility(Player, UpdateVisibilityPayload) - validate ownership, update, broadcast
    // handlePinQuest(Player, UUID) - save pin state

    // onPlayerJoin() - schedule delayed handshake (40 ticks)
    // onPlayerQuit() - cleanup

    // sendPacket(Player, byte[]) - helper to send via plugin channel
    // broadcastToModUsers(byte[]) - send to all players with channel registered
    // broadcastToContributors(UUID questId, byte[]) - send to owner + contributors
    // getModPlayers() - players with disquests:main registered
    // isModPlayer(Player) - check channel registration
}
```

Key implementation notes:
- All mutation handlers must validate that the sender has permission (owner for delete/visibility/contributors, owner or canEdit contributor for save).
- Broadcast rules follow the spec table: private quests only notify owner+contributors, open/closed quests notify all mod players.
- The handshake is delayed 40 ticks after PlayerJoinEvent to allow Fabric channel registration.
- `isModPlayer()` checks `player.getListeningPluginChannels().contains(CHANNEL)`.

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :paper:classes
```

- [ ] **Step 3: Commit**

```bash
git add paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java
git commit -m "feat(paper): add ServerPacketHandler with all C2S handlers and broadcast rules"
```

### Task 11: Create Commands and Plugin entry point

**Files:**
- Create: `paper/src/main/java/com/disqt/disquests/paper/Commands.java`
- Create: `paper/src/main/java/com/disqt/disquests/paper/DisquestsPlugin.java`

- [ ] **Step 1: Create Commands**

```java
package com.disqt.disquests.paper;

import org.bukkit.command.*;

public class Commands implements CommandExecutor {
    private final DisquestsPlugin plugin;

    public Commands(DisquestsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("Usage: /disquests reload");
            return true;
        }
        plugin.getDisquestsConfig().reload(plugin);
        sender.sendMessage("Disquests config reloaded.");
        return true;
    }
}
```

- [ ] **Step 2: Create DisquestsPlugin**

```java
package com.disqt.disquests.paper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

public class DisquestsPlugin extends JavaPlugin {
    public static final String CHANNEL = "disquests:main";

    private DataManager dataManager;
    private Config disquestsConfig;
    private ServerPacketHandler packetHandler;

    @Override
    public void onEnable() {
        disquestsConfig = new Config(this);
        dataManager = new DataManager(getDataFolder().toPath());
        dataManager.initialize();

        packetHandler = new ServerPacketHandler(this, dataManager, disquestsConfig);

        Messenger messenger = getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, CHANNEL);
        messenger.registerIncomingPluginChannel(this, CHANNEL, packetHandler);

        Bukkit.getPluginManager().registerEvents(packetHandler, this);
        Bukkit.getPluginManager().registerEvents(new PlayerNameTracker(dataManager), this);

        getCommand("disquests").setExecutor(new Commands(this));

        getLogger().info("Disquests enabled");
    }

    @Override
    public void onDisable() {
        Messenger messenger = getServer().getMessenger();
        messenger.unregisterOutgoingPluginChannel(this, CHANNEL);
        messenger.unregisterIncomingPluginChannel(this, CHANNEL, packetHandler);
        if (dataManager != null) dataManager.close();
        getLogger().info("Disquests disabled");
    }

    public DataManager getDataManager() { return dataManager; }
    public Config getDisquestsConfig() { return disquestsConfig; }
}
```

- [ ] **Step 3: Verify Paper builds**

```bash
./gradlew :paper:build
```

- [ ] **Step 4: Commit**

```bash
git add paper/src/main/java/com/disqt/disquests/paper/Commands.java
git add paper/src/main/java/com/disqt/disquests/paper/DisquestsPlugin.java
git commit -m "feat(paper): add DisquestsPlugin entry point and reload command"
```

---

## Chunk 3: Client Networking + State

Rewrites the client-side networking, session state, and cache to work with the new protocol.

### Task 12: Create client data models

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/data/Quest.java`
- Create: `client/src/main/java/com/disqt/disquests/client/data/Contributor.java`

- [ ] **Step 1: Create Quest (mutable client model)**

```java
package com.disqt.disquests.client.data;

import com.disqt.disquests.common.model.*;
import java.util.*;

public class Quest {
    private final UUID id;
    private String title;
    private String content;
    private final UUID ownerUuid;
    private String ownerName;
    private Visibility visibility;
    private final List<Contributor> contributors;
    private long lastModified;
    private CoordinatesData coordinates;
    private boolean isRegion;
    private CoordinatesData coordinates2;
    private String map;

    // Constructor from QuestData (network -> client)
    public static Quest fromNetwork(QuestData data) { ... }

    // Getters and setters for all fields
    // updateTimestamp() helper
}
```

- [ ] **Step 2: Create Contributor**

```java
package com.disqt.disquests.client.data;

import java.util.UUID;

public class Contributor {
    private final UUID uuid;
    private final String name;
    private boolean canEdit;

    // Constructor, getters, setters
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/data/
git commit -m "feat(client): add Quest and Contributor client-side models"
```

### Task 13: Create ClientSession and ClientCache

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/ClientSession.java`
- Create: `client/src/main/java/com/disqt/disquests/client/ClientCache.java`

- [ ] **Step 1: Create ClientSession**

```java
package com.disqt.disquests.client;

import java.util.UUID;

public class ClientSession {
    private static boolean onServer = false;
    private static String bluemapUrl = "";
    private static int pendingRequestCount = 0;
    private static UUID pinnedQuestId = null;

    // Session UI state (persists across screen opens within a session)
    private static int activeTab = 0;  // 0 = My Quests, 1 = Server Quests
    private static String searchTerm = "";
    private static int serverQuestsFilter = 0;  // 0 = All, 1 = Open, 2 = Closed

    public static void joinServer(String bluemapUrl, int pendingCount, UUID pinnedId) { ... }
    public static void leaveServer() { ... }  // reset all state

    // Getters and setters for all fields
    public static boolean isOnServer() { ... }
    public static boolean hasBluemap() { return !bluemapUrl.isEmpty(); }
    public static String getBluemapUrl() { ... }
    public static UUID getPinnedQuestId() { ... }
    public static void setPinnedQuestId(UUID id) { ... }
    public static int getPendingRequestCount() { ... }
    public static void setPendingRequestCount(int count) { ... }
    public static void incrementPendingRequestCount() { ... }

    // UI state
    public static int getActiveTab() { ... }
    public static void setActiveTab(int tab) { ... }
    public static String getSearchTerm() { ... }
    public static void setSearchTerm(String term) { ... }
    public static int getServerQuestsFilter() { ... }
    public static void setServerQuestsFilter(int filter) { ... }
}
```

- [ ] **Step 2: Create ClientCache**

```java
package com.disqt.disquests.client;

import com.disqt.disquests.client.data.Quest;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientCache {
    private static final List<Quest> myQuests = new CopyOnWriteArrayList<>();
    private static final List<Quest> serverQuests = new CopyOnWriteArrayList<>();

    public static List<Quest> getMyQuests() { return myQuests; }
    public static List<Quest> getServerQuests() { return serverQuests; }

    public static void setMyQuests(List<Quest> quests) { ... }
    public static void setServerQuests(List<Quest> quests) { ... }

    public static void addOrUpdateMyQuest(Quest quest) { ... }
    public static void addOrUpdateServerQuest(Quest quest) { ... }
    public static void removeQuestById(UUID id) { ... }  // removes from both lists
    public static void removeFromServerQuests(UUID id) { ... }  // removes from server quests only
    public static void removeFromMyQuests(UUID id) { ... }  // removes from my quests only

    // Move quest between lists (e.g., when joining a server quest)
    public static void moveToMyQuests(UUID questId) { ... }
    public static void moveToServerQuests(UUID questId) { ... }

    public static Quest getQuestById(UUID id) { ... }  // search both lists

    public static void clear() { ... }
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/ClientSession.java
git add client/src/main/java/com/disqt/disquests/client/ClientCache.java
git commit -m "feat(client): add ClientSession and ClientCache for Disquests"
```

### Task 14: Create RawPayload and PacketSender

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/network/RawPayload.java`
- Create: `client/src/main/java/com/disqt/disquests/client/network/PacketSender.java`

- [ ] **Step 1: Create RawPayload**

Copy from existing `net.atif.buildnotes.network.RawPayload`, update package and channel ID to `disquests:main`.

- [ ] **Step 2: Create PacketSender**

Static helper methods that wrap PacketCodec encode calls + `ClientPlayNetworking.send()`:

```java
package com.disqt.disquests.client.network;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.model.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.*;

public class PacketSender {
    public static void requestSync() {
        send(PacketCodec.writeRequestSync());
    }

    public static void saveQuest(UUID id, String title, String content,
            CoordinatesData coords, boolean isRegion, CoordinatesData coords2, String map) {
        send(PacketCodec.writeSaveQuest(id, title, content, coords, isRegion, coords2, map));
    }

    public static void deleteQuest(UUID id) { send(PacketCodec.writeDeleteQuest(id)); }
    public static void joinQuest(UUID id) { send(PacketCodec.writeJoinQuest(id)); }
    public static void requestCollaboration(UUID id) { send(PacketCodec.writeRequestCollaboration(id)); }
    public static void respondCollaboration(UUID requestId, boolean approved) {
        send(PacketCodec.writeRespondCollaboration(requestId, approved));
    }
    public static void updateContributors(UUID questId, List<PacketCodec.ContributorOpEntry> ops) {
        send(PacketCodec.writeUpdateContributors(questId, ops));
    }
    public static void updateVisibility(UUID questId, Visibility visibility) {
        send(PacketCodec.writeUpdateVisibility(questId, visibility));
    }
    public static void pinQuest(UUID questId) { send(PacketCodec.writePinQuest(questId)); }

    private static void send(byte[] data) {
        ClientPlayNetworking.send(new RawPayload(data));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/network/
git commit -m "feat(client): add RawPayload and PacketSender for Disquests protocol"
```

### Task 15: Create ClientPacketHandler

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java`

- [ ] **Step 1: Create handler that dispatches all S2C packets**

```java
package com.disqt.disquests.client.network;

import com.disqt.disquests.client.*;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.*;
import com.disqt.disquests.common.model.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public class ClientPacketHandler {
    public static void handleRawPayload(RawPayload payload,
            ClientPlayNetworking.Context context) {
        byte[] data = payload.data();
        ByteBufReader buf = new ByteBufReader(data);
        PacketType type = PacketType.fromId(buf.readByte());

        context.client().execute(() -> {
            switch (type) {
                case HANDSHAKE -> handleHandshake(buf);
                case SYNC_MY_QUESTS -> handleSyncMyQuests(buf);
                case SYNC_SERVER_QUESTS -> handleSyncServerQuests(buf);
                case UPDATE_QUEST -> handleUpdateQuest(buf);
                case DELETE_QUEST_S2C -> handleDeleteQuest(buf);
                case COLLABORATION_REQUEST -> handleCollaborationRequest(buf);
                case COLLABORATION_RESPONSE -> handleCollaborationResponse(buf);
                default -> System.err.println("Disquests: unknown S2C packet: " + type);
            }
        });
    }

    private static void handleHandshake(ByteBufReader buf) {
        var payload = PacketCodec.decodeHandshake(buf);
        ClientSession.joinServer(payload.bluemapUrl(),
            payload.pendingRequestCount(), payload.pinnedQuestId());
        PacketSender.requestSync();
    }

    private static void handleSyncMyQuests(ByteBufReader buf) {
        var quests = PacketCodec.decodeSyncMyQuests(buf);
        ClientCache.setMyQuests(quests.stream().map(Quest::fromNetwork).toList());
    }

    private static void handleSyncServerQuests(ByteBufReader buf) {
        var quests = PacketCodec.decodeSyncServerQuests(buf);
        ClientCache.setServerQuests(quests.stream().map(Quest::fromNetwork).toList());
    }

    private static void handleUpdateQuest(ByteBufReader buf) {
        QuestData data = PacketCodec.decodeUpdateQuest(buf);
        Quest quest = Quest.fromNetwork(data);
        // Determine which list it belongs to
        var myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        if (myUuid != null && (data.ownerUuid().equals(myUuid) ||
                data.contributors().stream().anyMatch(c -> c.uuid().equals(myUuid)))) {
            ClientCache.addOrUpdateMyQuest(quest);
            ClientCache.removeFromServerQuests(quest.getId());
        } else {
            ClientCache.addOrUpdateServerQuest(quest);
        }
    }

    private static void handleDeleteQuest(ByteBufReader buf) {
        UUID questId = PacketCodec.decodeDeleteQuestS2C(buf);
        ClientCache.removeQuestById(questId);
        // Clear pin if this was the pinned quest
        if (questId.equals(ClientSession.getPinnedQuestId())) {
            ClientSession.setPinnedQuestId(null);
        }
        // Close screen if viewing this quest
        // (handled by the screen checking if its quest still exists)
    }

    private static void handleCollaborationRequest(ByteBufReader buf) {
        var payload = PacketCodec.decodeCollaborationRequest(buf);
        ClientSession.incrementPendingRequestCount();
        // Could also store the request details for the notification UI
    }

    private static void handleCollaborationResponse(ByteBufReader buf) {
        var payload = PacketCodec.decodeCollaborationResponse(buf);
        if (payload.approved() && payload.quest() != null) {
            Quest quest = Quest.fromNetwork(payload.quest());
            ClientCache.addOrUpdateMyQuest(quest);
            ClientCache.removeFromServerQuests(quest.getId());
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java
git commit -m "feat(client): add ClientPacketHandler for all S2C Disquests packets"
```

### Task 16: Create DisquestsClient entry point and keybinds

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/DisquestsClient.java`
- Create: `client/src/main/java/com/disqt/disquests/client/KeyBinds.java`

- [ ] **Step 1: Create KeyBinds**

Same structure as existing, new package and mod ID.

- [ ] **Step 2: Create DisquestsClient**

```java
package com.disqt.disquests.client;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.network.ClientPacketHandler;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.client.network.RawPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class DisquestsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBinds.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KeyBinds.openGuiKey.wasPressed()) {
                if (client.currentScreen == null && ClientSession.isOnServer()) {
                    client.setScreen(new MainScreen());
                }
            }
            while (KeyBinds.pinKey.wasPressed()) {
                if (ClientSession.getPinnedQuestId() != null) {
                    PacketSender.pinQuest(null);
                    ClientSession.setPinnedQuestId(null);
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(RawPayload.ID,
            ClientPacketHandler::handleRawPayload);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientCache.clear();
            ClientSession.leaveServer();
        });
    }
}
```

- [ ] **Step 3: Update fabric.mod.json**

Update mod ID to `disquests`, entrypoint class to `com.disqt.disquests.client.DisquestsClient`, and mixin config reference to `disquests.mixins.json`.

- [ ] **Step 4: Create disquests.mixins.json**

Create `client/src/main/resources/disquests.mixins.json`:

```json
{
  "required": true,
  "package": "com.disqt.disquests.client.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "InGameHudMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

`InventoryBadgeMixin` will be added to the `client` array in Task 27.

- [ ] **Step 4: Create stub MainScreen**

Create `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java` as a minimal stub so the client module compiles:

```java
package com.disqt.disquests.client.gui.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class MainScreen extends Screen {
    public MainScreen() {
        super(Text.literal("Disquests"));
    }
}
```

This stub will be replaced in Chunk 4, Task 19.

- [ ] **Step 5: Verify client compiles**

```bash
./gradlew :client:classes
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/DisquestsClient.java
git add client/src/main/java/com/disqt/disquests/client/KeyBinds.java
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git add client/src/main/resources/
git commit -m "feat(client): add DisquestsClient entry point, keybinds, fabric.mod.json, mixin config"
```

---

## Chunk 4: Client UI - Core Screens

Implements the main screen (two tabs), view quest screen, edit quest screen, and the quest list widget.

### Task 17: Copy and rename reusable UI components

**Files:**
- Copy with package rename: `BaseScreen.java`, `ConfirmScreen.java`, `Colors.java`, `UIHelper.java`, `DarkButtonWidget.java`, `TabButtonWidget.java`, `MultiLineTextFieldWidget.java`, `ReadOnlyMultiLineTextFieldWidget.java`
- Create: `client/src/main/java/com/disqt/disquests/client/gui/helper/ScreenLayouts.java`

- [ ] **Step 1: Copy all reusable widget/helper classes**

Copy each file from the old `net.atif.buildnotes.*` package to the new `com.disqt.disquests.client.gui.*` package. Only change the `package` declaration and update imports.

- [ ] **Step 2: Create ScreenLayouts**

Merge constants from `MainScreenLayouts`, `NoteScreenLayouts`, `BuildScreenLayouts` into a single class.

- [ ] **Step 3: Verify compilation**

```bash
./gradlew :client:classes
```

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/
git commit -m "feat(client): copy and rename reusable UI widgets to new package"
```

### Task 18: Create QuestListWidget

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/widget/QuestListWidget.java`

- [ ] **Step 1: Create unified quest list widget**

Replaces both `NoteListWidget` and `BuildListWidget`. Each entry renders:
- Title (left-aligned)
- Visibility badge (top-right): Private = pink/muted, Closed = yellow, Open = green
- "by [owner]" for quests not owned by the player (top-right, after badge)
- First line of content preview (second row, gray)
- Last modified date (third row, muted)
- Map + coordinates summary (third row, right-aligned, if set)

Double-click opens the quest.

Structure follows the existing `AbstractListWidget` pattern.

- [ ] **Step 2: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/widget/QuestListWidget.java
git commit -m "feat(client): add QuestListWidget with visibility badges and metadata"
```

### Task 19: Create MainScreen

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

- [ ] **Step 1: Create MainScreen with two tabs**

Key differences from old MainScreen:
- Two tabs: "My Quests" (default) and "Server Quests"
- **Search field** between list and buttons (same as current), filters by title. Value saved to/restored from `ClientSession.searchTerm`
- My Quests tab: notification badge on tab header (pending request count), buttons: New Quest, Open, Close
- Server Quests tab: sub-filter buttons (All / Open / Closed), buttons: Join, Request Access, Open, Close
- "Join" button active only when an Open quest is selected
- "Request Access" button active only when a Closed quest is selected
- Tab selection, search term, and sub-filter restored from `ClientSession` on init
- Pinned quest sorted to top of My Quests list

**Screen auto-close on quest removal:** Override `tick()` to check if the currently viewed/edited quest still exists in `ClientCache`. If it has been removed (e.g., deleted by owner or visibility changed to Private), close the screen and return to the parent. This pattern applies to `ViewQuestScreen`, `EditQuestScreen`, and `ContributorScreen` as well.

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :client:classes
```

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat(client): add MainScreen with My Quests and Server Quests tabs"
```

### Task 20: Create ViewQuestScreen

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java`

- [ ] **Step 1: Create ViewQuestScreen**

Shows:
- Title panel with owner name + visibility badge
- Content panel (initially plain text rendering, markdown in Chunk 6)
- Metadata bar below content: coordinates, map, BlueMap link (if available)
- Buttons: Edit (if canEdit), Delete (if owner), Pin/Unpin, Close

Edit/Delete button visibility determined by checking:
```java
UUID myUuid = client.getSession().getUuidOrNull();
boolean isOwner = quest.getOwnerUuid().equals(myUuid);
boolean canEdit = isOwner || quest.getContributors().stream()
    .anyMatch(c -> c.getUuid().equals(myUuid) && c.isCanEdit());
```

BlueMap link rendering:
```java
if (ClientSession.hasBluemap() && quest.getCoordinates() != null) {
    // Construct URL and render as clickable text
    String url = BlueMapHelper.buildUrl(quest);
    // Use ClickEvent.Action.OPEN_URL
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java
git commit -m "feat(client): add ViewQuestScreen with metadata and permission-aware buttons"
```

### Task 21: Create EditQuestScreen

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/screen/EditQuestScreen.java`

- [ ] **Step 1: Create EditQuestScreen**

Fields:
- Title text field
- Content multiline text field (plain text editing)
- Optional fields panel:
  - Coordinates display + "Set Pos" button + "Region" checkbox (shows Corner 1 / Corner 2 if checked)
  - Map display + "Auto" button + "Clear" button
- Settings row (owner only):
  - Visibility button (cycles Private -> Closed -> Open)
  - Contributors button (opens ContributorScreen)
- Action row: Save, Close

"Set Pos" captures `client.player.getX/Y/Z()` and fills the coordinates.
"Auto" fills map from `client.world.getRegistryKey().getValue().getPath()`.
Close prompts "Discard unsaved changes?" if content differs from original.

Track `isDirty` by comparing current field values against the values when the screen opened.

- [ ] **Step 2: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/EditQuestScreen.java
git commit -m "feat(client): add EditQuestScreen with optional fields and visibility control"
```

### Task 22: Create ContributorScreen

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java`

- [ ] **Step 1: Create ContributorScreen**

Layout:
- List of current contributors (scrollable), each row: player name, edit toggle button, remove button
- Section divider: "Pending Requests" (only shown if there are requests)
- List of pending collaboration requests, each row: player name, Approve button, Deny button
- Bottom: text field + "Invite" button to add contributor by name

Invite flow: the client sends UPDATE_CONTRIBUTORS with an ADD op containing the **player name** (not UUID) in the `playerName` field. The `ContributorOpEntry` wire format includes an optional `playerName: String` field for ADD ops. The server resolves the name to UUID via `player_names` table. If resolution fails (unknown player), the server ignores the op and the contributor list remains unchanged. For REMOVE/UPDATE ops, the client sends the UUID directly (already known from the contributor list).

When toggle/remove/approve/deny is clicked, send the appropriate packet immediately and optimistically update the local UI.

- [ ] **Step 2: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java
git commit -m "feat(client): add ContributorScreen for managing quest contributors"
```

---

## Chunk 5: Markdown Rendering + HUD Pin + Inventory Badge + BlueMap

### Task 23: Add commonmark-java dependency

**Files:**
- Modify: `client/build.gradle.kts`

- [ ] **Step 1: Add dependency with shading**

```kotlin
dependencies {
    // ... existing deps ...

    // Markdown rendering
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.24.0")
    include("org.commonmark:commonmark:0.24.0")
    include("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    include("org.commonmark:commonmark-ext-task-list-items:0.24.0")
}
```

Note: Fabric's `include` JiJ (Jar-in-Jar) handles the shading. No shadow plugin needed. The classes are nested inside the mod JAR. For classpath isolation, if issues arise, switch to the Gradle shadow plugin with relocation. Start with `include` as it's simpler.

- [ ] **Step 2: Verify build**

```bash
./gradlew :client:build
```

- [ ] **Step 3: Commit**

```bash
git add client/build.gradle.kts
git commit -m "build(client): add commonmark-java dependency for markdown rendering"
```

### Task 24: Create MarkdownRenderer

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/markdown/RenderedLine.java`
- Create: `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java`

- [ ] **Step 1: Create RenderedLine**

```java
package com.disqt.disquests.client.markdown;

import net.minecraft.text.MutableText;

public record RenderedLine(MutableText text, int indent, float scale) {
    public static RenderedLine normal(MutableText text, int indent) {
        return new RenderedLine(text, indent, 1.0f);
    }
    public static RenderedLine heading(MutableText text, float scale) {
        return new RenderedLine(text, 0, scale);
    }
}
```

- [ ] **Step 2: Create MarkdownRenderer**

```java
package com.disqt.disquests.client.markdown;

import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.commonmark.ext.gfm.strikethrough.*;
import org.commonmark.ext.task.list.items.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import java.util.*;

public class MarkdownRenderer {
    private static final Parser PARSER = Parser.builder()
        .extensions(List.of(
            StrikethroughExtension.create(),
            TaskListItemsExtension.create()
        )).build();

    public static List<RenderedLine> render(String markdown) {
        Node document = PARSER.parse(markdown);
        List<RenderedLine> lines = new ArrayList<>();
        renderNode(document, lines, 0, Style.EMPTY);
        return lines;
    }

    private static void renderNode(Node node, List<RenderedLine> lines,
            int indent, Style style) {
        // Walk the AST:
        // Heading -> bold text, scale based on level (h1=1.5, h2=1.25, h3=1.0)
        // Paragraph -> collect inline children, add as line(s)
        // BulletList/OrderedList -> increase indent, recurse
        // ListItem -> prefix with bullet or number
        // TaskListItemMarker -> prefix with checkbox character
        // Emphasis -> italic style
        // StrongEmphasis -> bold style
        // Strikethrough -> strikethrough style
        // Code -> gray colored style
        // Link -> aqua + underline + click event
        // BlockQuote -> indent + gray "|" prefix
        // Text -> append to current line with current style
        // SoftLineBreak -> space
        // HardLineBreak -> new line
        // ThematicBreak -> horizontal line (rendered as "---" in gray)
    }
}
```

The visitor recursively walks the AST, tracking current style (bold/italic/strikethrough) as it enters/exits inline nodes, and accumulating `MutableText` fragments into lines. Block-level nodes (paragraphs, headings, lists) create new lines.

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/markdown/
git commit -m "feat(client): add MarkdownRenderer using commonmark-java"
```

### Task 25: Create MarkdownWidget

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/widget/MarkdownWidget.java`

- [ ] **Step 1: Create scrollable markdown display widget**

Extends a scrollable widget pattern. Takes a list of `RenderedLine` objects and renders them with word wrapping, indentation, and optional matrix scaling for headings.

```java
package com.disqt.disquests.client.gui.widget;

import com.disqt.disquests.client.markdown.RenderedLine;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import java.util.List;

public class MarkdownWidget {
    // Renders a list of RenderedLines with:
    // - Word wrapping via TextRenderer.wrapLines()
    // - Indentation (pixels from left)
    // - Scroll support (mouseScrolled, track scrollOffset)
    // - Heading scale (push matrix, scale, draw, pop)
    //   Fallback: if scale is 1.0, just draw normally (bold already applied)
    // - Click handling for links (check click position against link text bounds)
}
```

- [ ] **Step 2: Integrate into ViewQuestScreen**

Replace the `ReadOnlyMultiLineTextFieldWidget` content display with `MarkdownWidget`:

```java
// In ViewQuestScreen.init():
List<RenderedLine> rendered = MarkdownRenderer.render(quest.getContent());
this.markdownWidget = new MarkdownWidget(textRenderer, contentX, contentY,
    contentWidth, contentHeight, rendered);
```

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/widget/MarkdownWidget.java
git commit -m "feat(client): add MarkdownWidget and integrate into ViewQuestScreen"
```

### Task 26: Update HUD Pin system

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/hud/HudPinManager.java`
- Copy with rename: `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java`
- Update: `InGameHudMixin.java` (copy with new package)

- [ ] **Step 1: Rewrite HudPinManager**

No longer reads/writes local files. Pin state comes from `ClientSession.getPinnedQuestId()` and is sent to the server via `PacketSender.pinQuest()`.

```java
package com.disqt.disquests.client.hud;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.network.PacketSender;
import java.util.UUID;

public class HudPinManager {
    public static void pin(UUID questId) {
        ClientSession.setPinnedQuestId(questId);
        PacketSender.pinQuest(questId);
    }

    public static void unpin() {
        ClientSession.setPinnedQuestId(null);
        PacketSender.pinQuest(null);
    }

    public static void toggle(UUID questId) {
        if (questId.equals(ClientSession.getPinnedQuestId())) unpin();
        else pin(questId);
    }

    public static boolean isPinned(UUID questId) {
        return questId != null && questId.equals(ClientSession.getPinnedQuestId());
    }

    public static Quest getPinnedQuest() {
        UUID id = ClientSession.getPinnedQuestId();
        return id != null ? ClientCache.getQuestById(id) : null;
    }
}
```

- [ ] **Step 2: Copy HudPinRenderer with updated imports**

Update to use `Quest` instead of `Note`, get content from `quest.getContent()` (plain text rendering for HUD -- strip markdown or just show raw text).

- [ ] **Step 3: Copy InGameHudMixin with updated imports**

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/hud/
git add client/src/main/java/com/disqt/disquests/client/mixin/
git commit -m "feat(client): rewrite HUD pin to use server-side state"
```

### Task 27: Create InventoryBadgeMixin

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/mixin/InventoryBadgeMixin.java`

- [ ] **Step 1: Create mixin into HandledScreen**

```java
package com.disqt.disquests.client.mixin;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.screen.MainScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class InventoryBadgeMixin {
    // Inject into render() to draw the badge icon
    @Inject(method = "render", at = @At("TAIL"))
    private void renderDisquestsBadge(DrawContext context, int mouseX, int mouseY,
            float delta, CallbackInfo ci) {
        if (!ClientSession.isOnServer()) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        int badgeX = screen.width - 20;
        int badgeY = 4;
        int badgeSize = 16;

        // Draw icon (a simple notepad-like icon using DrawContext.fill for now)
        context.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize,
            0xAA333333);
        // Draw "Q" letter as placeholder icon
        context.drawCenteredTextWithShadow(
            MinecraftClient.getInstance().textRenderer, "Q",
            badgeX + badgeSize / 2, badgeY + 4, 0xFFCCCCCC);

        // Draw notification badge if pending requests
        int count = ClientSession.getPendingRequestCount();
        if (count > 0) {
            String countStr = count > 9 ? "9+" : String.valueOf(count);
            int dotX = badgeX + badgeSize - 2;
            int dotY = badgeY - 2;
            context.fill(dotX - 4, dotY, dotX + 6, dotY + 10, 0xFFEE4444);
            context.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer, countStr,
                dotX + 1, dotY + 1, 0xFFFFFFFF);
        }
    }

    // Inject into mouseClicked() to handle badge clicks
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onBadgeClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ClientSession.isOnServer() || button != 0) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        int badgeX = screen.width - 20;
        int badgeY = 4;
        int badgeSize = 16;

        if (mouseX >= badgeX && mouseX <= badgeX + badgeSize &&
                mouseY >= badgeY && mouseY <= badgeY + badgeSize) {
            MinecraftClient.getInstance().setScreen(new MainScreen());
            cir.setReturnValue(true);
        }
    }
}
```

- [ ] **Step 2: Register in mixin config**

Add `InventoryBadgeMixin` to `disquests.mixins.json`.

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/mixin/InventoryBadgeMixin.java
git add client/src/main/resources/disquests.mixins.json
git commit -m "feat(client): add inventory badge mixin with notification count"
```

### Task 28: Create BlueMap link helper

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/BlueMapHelper.java`

- [ ] **Step 1: Create helper**

```java
package com.disqt.disquests.client;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.model.CoordinatesData;

public class BlueMapHelper {
    public static String buildUrl(Quest quest) {
        if (!ClientSession.hasBluemap() || quest.getCoordinates() == null) return null;

        String baseUrl = ClientSession.getBluemapUrl();
        String map = quest.getMap() != null ? quest.getMap() : "world";

        double x, y, z;
        if (quest.isRegion() && quest.getCoordinates2() != null) {
            CoordinatesData c1 = quest.getCoordinates();
            CoordinatesData c2 = quest.getCoordinates2();
            x = (c1.x() + c2.x()) / 2;
            y = (c1.y() + c2.y()) / 2;
            z = (c1.z() + c2.z()) / 2;
        } else {
            CoordinatesData c = quest.getCoordinates();
            x = c.x();
            y = c.y();
            z = c.z();
        }

        return String.format("%s/#%s:%.0f:%.0f:%.0f:50:0:0:0:0:flat",
            baseUrl, map, x, y, z);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/BlueMapHelper.java
git commit -m "feat(client): add BlueMap URL helper with region center calculation"
```

---

## Chunk 6: Cleanup + Integration Testing

### Task 29: Delete old BuildNotes code

**Files:**
- Delete: `common/src/main/java/com/disqt/buildnotes/` (entire directory)
- Delete: `client/src/main/java/net/atif/buildnotes/` (entire directory)
- Delete: `paper/src/main/java/com/disqt/buildnotes/` (entire directory)
- Delete: old `paper/src/main/resources/plugin.yml` (if still present)
- Delete: old test files referencing BuildNotes

- [ ] **Step 1: Delete old source trees**

```bash
rm -rf common/src/main/java/com/disqt/buildnotes
rm -rf client/src/main/java/net/atif/buildnotes
rm -rf paper/src/main/java/com/disqt/buildnotes
```

- [ ] **Step 2: Delete old test code**

```bash
rm -rf common/src/test/java/com/disqt/buildnotes
rm -rf client/src/testmod/  # Will be rewritten later
```

- [ ] **Step 3: Verify full build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL for all modules.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: delete old BuildNotes source code"
```

### Task 30: Update CLAUDE.md

**Files:**
- Modify: `.claude/CLAUDE.md`

- [ ] **Step 1: Update CLAUDE.md to reflect Disquests**

Update all references from BuildNotes to Disquests:
- Project description
- Architecture section (no more `client/src/main/java/net/atif/buildnotes/`)
- Package namespaces
- Channel name
- Key files table
- Networking protocol section
- Build commands (same, but note new package structure)
- Deploy commands (new JAR names)

- [ ] **Step 2: Commit**

```bash
git add .claude/CLAUDE.md
git commit -m "docs: update CLAUDE.md for Disquests rebrand"
```

### Task 31: Smoke test with Paper dev server

- [ ] **Step 1: Build all**

```bash
./gradlew build
```

- [ ] **Step 2: Start Paper dev server**

```bash
./gradlew :paper:runServer
```

Verify:
- Plugin loads: "Disquests enabled" in console
- SQLite database created in `paper/run/plugins/Disquests/`
- `/disquests reload` works
- No errors in console

- [ ] **Step 3: Connect with client**

If possible, run the client and connect to localhost:25565. Verify:
- Handshake packet received (check client log)
- Press N to open main screen
- Create a quest, save it, verify it appears in list
- View the quest, check markdown rendering
- Pin the quest, verify HUD overlay

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: smoke test fixes for Disquests integration"
```

---

## Chunk 7: .gitignore and final cleanup

### Task 32: Update .gitignore

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Add .superpowers/ to .gitignore**

```
# Brainstorming mockups
.superpowers/
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: add .superpowers/ to .gitignore"
```
