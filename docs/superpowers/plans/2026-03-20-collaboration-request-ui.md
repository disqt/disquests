# Collaboration Request UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface pending collaboration requests to quest owners with Accept/Deny in the ContributorScreen, pending counts on Contributors button and quest list.

**Architecture:** Extend SYNC_MY_QUESTS to include per-quest pending counts. Add new SYNC_PENDING_REQUESTS (0x17) S2C packet with full request details. Client stores both in ClientCache. ContributorScreen renders pending section above contributors. Uses existing RESPOND_COLLABORATION for Accept/Deny.

**Tech Stack:** Java 21, Fabric 1.21.11, PaperMC, JUnit 5, SQLite

**Spec:** `docs/superpowers/specs/2026-03-20-collaboration-request-ui-design.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `common/PacketType.java` | Modify | Add SYNC_PENDING_REQUESTS (0x17) |
| `common/PacketCodec.java` | Modify | Overload writeSyncMyQuests with pending counts, new writeSyncPendingRequests/readSyncPendingRequests |
| `common/PacketCodecTest.java` | Modify | Tests for new packets |
| `paper/DataManager.java` | Modify | Add getPendingCountByQuest |
| `paper/DataManagerTest.java` | Modify | Test for getPendingCountByQuest |
| `paper/ServerPacketHandler.java` | Modify | Send pending counts + requests during sync |
| `client/ClientCache.java` | Modify | Store pendingRequests and pendingCounts |
| `client/ClientPacketHandler.java` | Modify | Handle SYNC_PENDING_REQUESTS, read pending counts |
| `client/gui/helper/Colors.java` | Modify | Add AMBER constant |
| `client/gui/screen/ContributorScreen.java` | Modify | Pending requests section with Accept/Deny |
| `client/gui/screen/QuestScreen.java` | Modify | Contributors button shows "+ N" |
| `client/gui/widget/list/QuestListWidget.java` | Modify | Show "(N pending)" badge |

All paths relative to `src/main/java/com/disqt/disquests/`.

---

### Task 1: SYNC_PENDING_REQUESTS Packet + Pending Counts in SYNC_MY_QUESTS (Common)

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketType.java`
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketCodec.java`
- Modify: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`

- [ ] **Step 1: Write failing tests**

In `PacketCodecTest.java`, add:

```java
@Test
void testSyncMyQuestsWithPendingCounts() {
    List<QuestData> quests = List.of(createTestQuest());
    Map<UUID, Integer> pendingCounts = Map.of(quests.get(0).id(), 3);

    byte[] packet = PacketCodec.writeSyncMyQuests(quests, pendingCounts);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_MY_QUESTS, PacketCodec.readType(reader));

    List<QuestData> readQuests = PacketCodec.readSyncMyQuests(reader);
    assertEquals(1, readQuests.size());

    Map<UUID, Integer> readCounts = PacketCodec.readPendingCounts(reader);
    assertEquals(3, readCounts.get(quests.get(0).id()));
    assertEquals(0, reader.remaining());
}

@Test
void testSyncPendingRequestsRoundTrip() {
    CollaborationRequestData req = new CollaborationRequestData(
            UUID.randomUUID(), UUID.randomUUID(), "Test Quest",
            UUID.randomUUID(), "PlayerName", 1234567890L);
    List<CollaborationRequestData> requests = List.of(req);

    byte[] packet = PacketCodec.writeSyncPendingRequests(requests);
    ByteBufReader reader = new ByteBufReader(packet);
    assertEquals(PacketType.SYNC_PENDING_REQUESTS, PacketCodec.readType(reader));

    List<CollaborationRequestData> readRequests = PacketCodec.readSyncPendingRequests(reader);
    assertEquals(1, readRequests.size());
    assertEquals(req.id(), readRequests.get(0).id());
    assertEquals(req.questId(), readRequests.get(0).questId());
    assertEquals(req.questTitle(), readRequests.get(0).questTitle());
    assertEquals(req.requesterName(), readRequests.get(0).requesterName());
    assertEquals(req.timestamp(), readRequests.get(0).timestamp());
    assertEquals(0, reader.remaining());
}
```

Add imports: `import com.disqt.disquests.common.model.CollaborationRequestData;` and `import java.util.Map;`.

You'll need a `createTestQuest()` helper if one doesn't already exist in the test file. Check first -- it likely already exists.

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :common:test --tests "*testSyncMyQuestsWithPendingCounts" --tests "*testSyncPendingRequestsRoundTrip"
```
Expected: FAIL

- [ ] **Step 3: Add SYNC_PENDING_REQUESTS to PacketType**

In `PacketType.java`, add after `COLLABORATION_RESPONSE((byte) 0x16)`:
```java
SYNC_PENDING_REQUESTS((byte) 0x17);
```
(Change the semicolon on `COLLABORATION_RESPONSE` to a comma.)

- [ ] **Step 4: Add encode/decode methods to PacketCodec**

**Overloaded writeSyncMyQuests with pending counts:**
```java
public static byte[] writeSyncMyQuests(List<QuestData> quests, Map<UUID, Integer> pendingCounts) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.SYNC_MY_QUESTS.getId());
    buf.writeVarInt(quests.size());
    for (QuestData quest : quests) {
        writeQuest(buf, quest);
    }
    // Append pending counts map
    buf.writeVarInt(pendingCounts.size());
    for (Map.Entry<UUID, Integer> entry : pendingCounts.entrySet()) {
        buf.writeUUID(entry.getKey());
        buf.writeVarInt(entry.getValue());
    }
    return buf.toByteArray();
}
```

Keep the old `writeSyncMyQuests(List<QuestData>)` signature -- it delegates with `Map.of()`.

**Read pending counts (called after readSyncMyQuests):**
```java
public static Map<UUID, Integer> readPendingCounts(ByteBufReader r) {
    if (r.remaining() <= 0) return Map.of();
    int count = r.readVarInt();
    Map<UUID, Integer> map = new HashMap<>(count);
    for (int i = 0; i < count; i++) {
        map.put(r.readUUID(), r.readVarInt());
    }
    return map;
}
```

**SYNC_PENDING_REQUESTS:**
```java
public static byte[] writeSyncPendingRequests(List<CollaborationRequestData> requests) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.SYNC_PENDING_REQUESTS.getId());
    buf.writeVarInt(requests.size());
    for (CollaborationRequestData req : requests) {
        buf.writeUUID(req.id());
        buf.writeUUID(req.questId());
        buf.writeString(req.questTitle());
        writeNullableString(buf, req.requesterName());
        buf.writeLong(req.timestamp());
    }
    return buf.toByteArray();
}

public static List<CollaborationRequestData> readSyncPendingRequests(ByteBufReader r) {
    int count = r.readVarInt();
    List<CollaborationRequestData> requests = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
        UUID id = r.readUUID();
        UUID questId = r.readUUID();
        String questTitle = r.readString();
        String requesterName = readNullableString(r);
        long timestamp = r.readLong();
        requests.add(new CollaborationRequestData(id, questId, questTitle, null, requesterName, timestamp));
    }
    return requests;
}
```

Note: `requesterUuid` is not sent to client (server-only), so pass `null` in the record.

- [ ] **Step 5: Run all common tests**

```bash
./gradlew :common:test
```
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add common/
git commit -m "feat: add SYNC_PENDING_REQUESTS packet and pending counts in SYNC_MY_QUESTS"
```

---

### Task 2: Server-Side -- getPendingCountByQuest + Send During Sync

**Files:**
- Modify: `paper/src/main/java/com/disqt/disquests/paper/DataManager.java`
- Modify: `paper/src/test/java/com/disqt/disquests/paper/DataManagerTest.java`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java`

- [ ] **Step 1: Write failing test for getPendingCountByQuest**

```java
@Test
void getPendingCountByQuest_returnsMapOfCounts() {
    UUID questId1 = UUID.randomUUID();
    UUID questId2 = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Owner");
    dm.upsertPlayerName(PLAYER2, "Player2");
    dm.upsertPlayerName(PLAYER3, "Player3");

    dm.saveQuest(new QuestData(questId1, "Q1", "", OWNER, null,
            Visibility.CLOSED, List.of(), System.currentTimeMillis(), null, false, null, null));
    dm.saveQuest(new QuestData(questId2, "Q2", "", OWNER, null,
            Visibility.CLOSED, List.of(), System.currentTimeMillis(), null, false, null, null));

    dm.createCollaborationRequest(questId1, PLAYER2);
    dm.createCollaborationRequest(questId1, PLAYER3);
    dm.createCollaborationRequest(questId2, PLAYER2);

    Map<UUID, Integer> counts = dm.getPendingCountByQuest(OWNER);
    assertEquals(2, counts.get(questId1));
    assertEquals(1, counts.get(questId2));
}
```

Add `import java.util.Map;` if needed.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :paper:test --tests "*getPendingCountByQuest*"
```
Expected: FAIL

- [ ] **Step 3: Implement DataManager.getPendingCountByQuest**

```java
public synchronized Map<UUID, Integer> getPendingCountByQuest(UUID ownerUuid) {
    Map<UUID, Integer> counts = new HashMap<>();
    try (PreparedStatement stmt = connection.prepareStatement("""
            SELECT cr.quest_id, COUNT(*) as cnt
            FROM collaboration_requests cr
            JOIN quests q ON cr.quest_id = q.id
            WHERE q.owner_uuid = ?
            GROUP BY cr.quest_id
            """)) {
        stmt.setString(1, ownerUuid.toString());
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            counts.put(UUID.fromString(rs.getString("quest_id")), rs.getInt("cnt"));
        }
    } catch (SQLException e) {
        throw new RuntimeException("Failed to count pending requests by quest", e);
    }
    return counts;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :paper:test --tests "*getPendingCountByQuest*"
```
Expected: PASS

- [ ] **Step 5: Update handleRequestSync to send pending data**

In `ServerPacketHandler.java`, change `handleRequestSync`:

```java
private void handleRequestSync(Player player) {
    UUID uuid = player.getUniqueId();
    List<QuestData> myQuests = dataManager.getQuestsForPlayer(uuid);
    List<QuestData> serverQuests = dataManager.getServerQuests(uuid);
    Map<UUID, Integer> pendingCounts = dataManager.getPendingCountByQuest(uuid);
    List<CollaborationRequestData> pendingRequests = dataManager.getPendingRequestsForOwner(uuid);

    sendPacket(player, PacketCodec.writeSyncMyQuests(myQuests, pendingCounts));
    sendPacket(player, PacketCodec.writeSyncServerQuests(serverQuests));
    sendPacket(player, PacketCodec.writeSyncPendingRequests(pendingRequests));
}
```

Add import: `import com.disqt.disquests.common.model.CollaborationRequestData;`

- [ ] **Step 6: Run all paper tests**

```bash
./gradlew :paper:test
```
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add paper/
git commit -m "feat: send pending counts and requests during sync"
```

---

### Task 3: Client-Side -- ClientCache Storage + Packet Handling

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientCache.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java`

- [ ] **Step 1: Add pending request storage to ClientCache**

```java
// Add fields:
private static final ConcurrentHashMap<UUID, List<CollaborationRequestData>> pendingRequests = new ConcurrentHashMap<>();
private static final ConcurrentHashMap<UUID, Integer> pendingCounts = new ConcurrentHashMap<>();

// Add methods:
public static void setPendingCounts(Map<UUID, Integer> counts) {
    pendingCounts.clear();
    pendingCounts.putAll(counts);
}

public static int getPendingCount(UUID questId) {
    return pendingCounts.getOrDefault(questId, 0);
}

public static void setPendingRequests(List<CollaborationRequestData> requests) {
    pendingRequests.clear();
    for (CollaborationRequestData req : requests) {
        pendingRequests.computeIfAbsent(req.questId(), k -> new ArrayList<>()).add(req);
    }
}

public static List<CollaborationRequestData> getPendingRequestsForQuest(UUID questId) {
    return pendingRequests.getOrDefault(questId, List.of());
}

public static void removePendingRequest(UUID questId, UUID requestId) {
    List<CollaborationRequestData> reqs = pendingRequests.get(questId);
    if (reqs != null) {
        reqs.removeIf(r -> r.id().equals(requestId));
        if (reqs.isEmpty()) pendingRequests.remove(questId);
    }
    pendingCounts.computeIfPresent(questId, (k, v) -> v > 1 ? v - 1 : null);
}
```

Add imports: `import com.disqt.disquests.common.model.CollaborationRequestData;`, `import java.util.concurrent.ConcurrentHashMap;`, `import java.util.Map;`, `import java.util.ArrayList;`.

In `clear()`, add:
```java
pendingRequests.clear();
pendingCounts.clear();
```

- [ ] **Step 2: Update ClientPacketHandler to handle new data**

In `handleSyncMyQuests`, after reading the quest list, read pending counts:
```java
private static void handleSyncMyQuests(ByteBufReader r) {
    List<QuestData> dataList = PacketCodec.readSyncMyQuests(r);
    Map<UUID, Integer> pendingCounts = PacketCodec.readPendingCounts(r);
    List<Quest> quests = new ArrayList<>(dataList.size());
    for (QuestData data : dataList) {
        quests.add(Quest.fromNetwork(data));
    }
    ClientCache.setMyQuests(quests);
    ClientCache.setPendingCounts(pendingCounts);
}
```

Add `import java.util.Map;` if needed.

Add the SYNC_PENDING_REQUESTS handler in the switch:
```java
case SYNC_PENDING_REQUESTS -> handleSyncPendingRequests(r);
```

Add the method:
```java
private static void handleSyncPendingRequests(ByteBufReader r) {
    List<CollaborationRequestData> requests = PacketCodec.readSyncPendingRequests(r);
    ClientCache.setPendingRequests(requests);
}
```

Add import: `import com.disqt.disquests.common.model.CollaborationRequestData;`

- [ ] **Step 3: Build client**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/ClientCache.java client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java
git commit -m "feat: client stores pending requests and counts from sync"
```

---

### Task 4: Colors + Quest List Badge

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/Colors.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java`

- [ ] **Step 1: Add AMBER color constant**

In `Colors.java`, add after the Fade Gradient section:
```java
// --- Accent ---
public static int AMBER = 0xFFFFAA33;
```

- [ ] **Step 2: Add pending badge to quest list**

In `QuestListWidget.java`, in `QuestEntry.render()`, after drawing the visibility badge (around line 176), add pending count display for owned quests:

```java
// Draw pending request count (owned quests only)
if (isOwnedByPlayer) {
    int pendingCount = ClientCache.getPendingCount(quest.getId());
    if (pendingCount > 0) {
        Text pendingText = Text.literal(" (" + pendingCount + " pending)");
        rightX -= client.textRenderer.getWidth(pendingText);
        context.drawText(client.textRenderer, pendingText, rightX, entryY + 4, Colors.AMBER, false);
    }
}
```

Add import: `import com.disqt.disquests.client.ClientCache;`

Note: This must be drawn BEFORE the visibility badge in the right-to-left rendering order, so place it between the owner text and visibility text drawing.

- [ ] **Step 3: Build and verify**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/helper/Colors.java client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java
git commit -m "feat: amber pending badge on quest list for owned quests"
```

---

### Task 5: Contributors Button Shows "+ N"

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`

- [ ] **Step 1: Update Contributors button label in edit mode**

In `QuestScreen.java`, find `buildSettingsRow` (the method that creates the Contributors button in edit mode). The current button label is something like `"Contributors (" + quest.getContributors().size() + ")"`.

Change it to include pending count:

```java
int contribCount = quest.getContributors().size();
int pendingCount = ClientCache.getPendingCount(quest.getId());
String contribLabel;
if (pendingCount > 0) {
    contribLabel = "Contributors (" + contribCount + " + " + pendingCount + ")";
} else {
    contribLabel = "Contributors (" + contribCount + ")";
}
```

For the amber coloring of "+ N", since DarkButtonWidget takes a `Text` label, use styled text:

```java
Text contribText;
if (pendingCount > 0) {
    contribText = Text.literal("Contributors (" + contribCount + " ")
            .append(Text.literal("+ " + pendingCount).withColor(Colors.AMBER))
            .append(Text.literal(")"));
} else {
    contribText = Text.literal("Contributors (" + contribCount + ")");
}
```

Add import: `import com.disqt.disquests.client.ClientCache;` (may already exist).

- [ ] **Step 2: Build and verify**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git commit -m "feat: contributors button shows pending count in amber"
```

---

### Task 6: ContributorScreen Pending Requests Section

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java`

This is the largest task. The ContributorScreen currently shows: title, contributor list, close button. We add a "Pending Requests" section ABOVE the contributor list.

- [ ] **Step 1: Read the current ContributorScreen.java**

Read the full file first to understand the layout.

- [ ] **Step 2: Add pending requests section in init()**

After the close button and before the contributor list, calculate space for pending requests:

```java
// --- PENDING REQUESTS SECTION ---
List<CollaborationRequestData> pendingRequests = ClientCache.getPendingRequestsForQuest(quest.getId());
int pendingHeight = pendingRequests.isEmpty() ? 0 : (10 + pendingRequests.size() * ROW_HEIGHT + 8);

int listStartY = ScreenLayouts.TOP_MARGIN + 10;

if (!pendingRequests.isEmpty()) {
    // Section header "Pending Requests" at listStartY
    // (rendered in render() method)

    int pendingListY = listStartY + 12; // after header
    for (int i = 0; i < pendingRequests.size(); i++) {
        int rowY = pendingListY + (i * ROW_HEIGHT);
        if (rowY + ROW_HEIGHT > maxListBottom) break;

        CollaborationRequestData req = pendingRequests.get(i);
        int btnY = rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;

        // "Accept" button (green)
        int acceptBtnWidth = Math.max(textRenderer.getWidth("Accept") + UIHelper.BUTTON_TEXT_PADDING, SMALL_BUTTON_WIDTH);
        int denyBtnX = contentX + contentWidth - SMALL_BUTTON_WIDTH;
        int acceptBtnX = denyBtnX - 4 - acceptBtnWidth;

        final UUID requestId = req.id();
        final UUID questId = req.questId();

        this.addDrawableChild(new DarkButtonWidget(
                acceptBtnX, btnY, acceptBtnWidth, BUTTON_HEIGHT,
                Text.literal("Accept").withColor(0xFF55CC55),
                b -> respondToRequest(questId, requestId, true)));

        // "Deny" button (red)
        this.addDrawableChild(new DarkButtonWidget(
                denyBtnX, btnY, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT,
                Text.literal("Deny").withColor(0xFFCC5555),
                b -> respondToRequest(questId, requestId, false)));
    }
}

// Offset contributor list start by pending section height
int contributorListStartY = listStartY + pendingHeight;
```

Update the contributor list `listStartY` references to use `contributorListStartY`.

- [ ] **Step 3: Add respondToRequest method**

```java
private void respondToRequest(UUID questId, UUID requestId, boolean accept) {
    PacketSender.respondCollaboration(requestId, accept);
    // Optimistic update
    ClientCache.removePendingRequest(questId, requestId);
    ClientSession.setPendingRequestCount(
            Math.max(0, ClientSession.getPendingRequestCount() - 1));
    this.clearAndInit();
}
```

Add import: `import com.disqt.disquests.client.ClientCache;` and `import com.disqt.disquests.common.model.CollaborationRequestData;`.

- [ ] **Step 4: Render pending section header and names**

In `render()`, before the contributor list rendering, add:

```java
// Render pending requests section
List<CollaborationRequestData> pendingRequests = ClientCache.getPendingRequestsForQuest(quest.getId());
if (!pendingRequests.isEmpty()) {
    // Section header
    context.drawText(this.textRenderer, Text.literal("Pending Requests").withColor(Colors.AMBER),
            contentX + 5, listStartY + 2, Colors.AMBER, false);

    // Separator line
    context.fill(contentX, listStartY + 11, contentX + contentWidth, listStartY + 12, 0x44FFAA33);

    // Pending request names
    int pendingListY = listStartY + 12;
    for (int i = 0; i < pendingRequests.size(); i++) {
        int rowY = pendingListY + (i * ROW_HEIGHT);
        if (rowY + ROW_HEIGHT > maxListBottom) break;
        int textY = rowY + (ROW_HEIGHT - 8) / 2;
        String name = pendingRequests.get(i).requesterName();
        if (name == null) name = "Unknown";
        context.drawText(this.textRenderer, name,
                contentX + 5, textY, Colors.TEXT_PRIMARY, false);
    }

    // Draw pending list panel
    int pendingListHeight = Math.min(pendingRequests.size() * ROW_HEIGHT, maxListBottom - pendingListY);
    UIHelper.drawPanel(context, contentX, pendingListY, contentWidth, pendingListHeight);
}
```

- [ ] **Step 5: Build and verify**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java
git commit -m "feat: pending requests section with Accept/Deny in ContributorScreen"
```

---

### Task 7: Also Decrement Global Badge on Real-Time COLLABORATION_REQUEST

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java`

Currently `handleCollaborationRequest` just increments the global count. We should also store the request data in ClientCache for real-time updates.

- [ ] **Step 1: Update handleCollaborationRequest**

```java
private static void handleCollaborationRequest(ByteBufReader r) {
    PacketCodec.CollaborationRequestPayload payload = PacketCodec.readCollaborationRequest(r);
    ClientSession.incrementPendingRequestCount();
    // Store in cache for real-time display
    CollaborationRequestData requestData = new CollaborationRequestData(
            payload.requestId(), payload.questId(), payload.questTitle(),
            null, payload.requesterName(), System.currentTimeMillis() / 1000L);
    ClientCache.addPendingRequest(requestData);
}
```

Add `addPendingRequest` to ClientCache:
```java
public static void addPendingRequest(CollaborationRequestData request) {
    pendingRequests.computeIfAbsent(request.questId(), k -> new CopyOnWriteArrayList<>()).add(request);
    pendingCounts.merge(request.questId(), 1, Integer::sum);
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java client/src/main/java/com/disqt/disquests/client/ClientCache.java
git commit -m "feat: store real-time collaboration requests in cache"
```

---

### Task 8: Build + Test Verification

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew :common:test :paper:test
```
Expected: ALL PASS

- [ ] **Step 2: Full build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Deploy server plugin**

```bash
gh release download v0.2.4 --pattern "disquests-paper-*.jar" --dir /tmp/ 2>/dev/null || true
# Build locally since this is unreleased
scp paper/build/libs/paper.jar minecraft:~/serverfiles/plugins/Disquests.jar
ssh minecraft "tmux -S /tmp/tmux-1000/pmcserver-bb664df1 send-keys -t pmcserver 'plugman reload Disquests' Enter"
```

- [ ] **Step 4: Update Prism instance**

```bash
cp client/build/libs/client.jar "C:/Users/leole/AppData/Roaming/PrismLauncher/instances/1.21.11 v2.7/.minecraft/mods/disquests-client-0.2.4.jar"
```
