# Collaboration Request UI

Surface pending collaboration requests to quest owners so they can Accept/Deny directly from the ContributorScreen.

## Data Flow

### Server-side additions

**DataManager:** Add `getPendingCountByQuest(UUID ownerUuid)` returning `Map<UUID, Integer>` (questId -> pending count).

**SYNC_MY_QUESTS (modified):** After writing the quest list, append the pending count map: VarInt map size, then questId UUID + VarInt count per entry. Backwards-compatible: old clients ignore trailing bytes (reader stops after quest list).

**SYNC_PENDING_REQUESTS (new S2C, 0x17):** Sent after SYNC_MY_QUESTS. Contains full `List<CollaborationRequestData>` for all the owner's pending requests. Each entry: requestId UUID, questId UUID, questTitle String, requesterName String, timestamp long.

### Client-side storage

**ClientCache:** Add `Map<UUID, List<CollaborationRequestData>> pendingRequests` keyed by questId. Add `Map<UUID, Integer> pendingCounts` for badge display. Both cleared on `leaveServer()`.

**ClientPacketHandler:** Handle `SYNC_PENDING_REQUESTS` -- parse and store in ClientCache. Handle updated `SYNC_MY_QUESTS` -- read the pending count map after the quest list.

### Respond flow (existing)

`RESPOND_COLLABORATION(requestId, approved)` already works. After sending, optimistically:
- Remove the request from `ClientCache.pendingRequests`
- Decrement the count in `ClientCache.pendingCounts`
- Decrement `ClientSession.pendingRequestCount` (global badge)
- Re-render the ContributorScreen

## UI Changes

### MainScreen quest list

For owned quests with pending requests, show "(N pending)" in amber (#FFAA33) next to the visibility badge on the same row. Only visible on "My Quests" tab for quests the player owns.

### QuestScreen edit mode -- Contributors button

Currently: `"Contributors (N)"` where N is contributor count.
Change to: `"Contributors (N + M)"` where M is pending count, rendered in amber (#FFAA33). Only show `"+ M"` when M > 0. The `+M` portion uses a separate styled `Text` appended to the button label.

### ContributorScreen

**Layout (top to bottom):**
1. Title: "Contributors"
2. **Pending Requests section** (if any exist, shown ABOVE contributors for urgency)
   - Section header: "Pending Requests" in amber (#FFAA33)
   - Separator line (1px, amber, faded)
   - Each row: requester name (white) + "Accept" button (green) + "Deny" button (red)
   - Row height matches existing contributor rows (ROW_HEIGHT = 20)
3. **Contributors section** (existing, unchanged except moved down when pending section present)
   - "No contributors yet" or contributor list with permission/remove buttons
4. Close button

**Button colors:**
- Accept: green tint (#55CC55) matching the checked-task color
- Deny: red tint (#CC5555)

**Actions:**
- Accept: `PacketSender.respondCollaboration(requestId, true)` -> optimistic remove + decrement counts + `clearAndInit()`
- Deny: `PacketSender.respondCollaboration(requestId, false)` -> same optimistic update

## Packets

| Packet | Direction | ID | Payload |
|--------|-----------|-----|---------|
| SYNC_MY_QUESTS (modified) | S2C | 0x11 | existing quest list + appended `Map<UUID, Integer>` pending counts |
| SYNC_PENDING_REQUESTS (new) | S2C | 0x17 | VarInt count, then per entry: requestId UUID, questId UUID, questTitle String, requesterName String, timestamp long |

## File Impact

| File | Changes |
|------|---------|
| `common/PacketType.java` | Add SYNC_PENDING_REQUESTS (0x17) |
| `common/PacketCodec.java` | Encode/decode pending counts in SYNC_MY_QUESTS, encode/decode SYNC_PENDING_REQUESTS |
| `paper/DataManager.java` | Add `getPendingCountByQuest(UUID)` |
| `paper/ServerPacketHandler.java` | Send pending counts with SYNC_MY_QUESTS, send SYNC_PENDING_REQUESTS after sync |
| `client/ClientCache.java` | Store `pendingRequests` and `pendingCounts` maps |
| `client/ClientPacketHandler.java` | Handle SYNC_PENDING_REQUESTS, read pending counts from SYNC_MY_QUESTS |
| `client/gui/screen/ContributorScreen.java` | Pending requests section with Accept/Deny |
| `client/gui/screen/QuestScreen.java` | Contributors button shows "+ N" in amber |
| `client/gui/widget/list/QuestListWidget.java` | Show "(N pending)" badge on owned quests |
| `client/gui/helper/Colors.java` | Add AMBER (#FFAA33) constant |
