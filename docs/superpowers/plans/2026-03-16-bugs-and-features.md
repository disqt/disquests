# Disquests Bugs & Features Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 5 bugs and implement 10 features for the Disquests Fabric+Paper quest mod, as specified in `docs/superpowers/specs/2026-03-16-bugs-and-features-design.md`.

**Architecture:** Bug fixes are applied first (B1-B5), then the QuestScreen merge (F1), then remaining features. B1 and F2 both modify the HANDSHAKE protocol -- they are coordinated in a single task. E2E tests use Fabric's ClientGameTest framework connecting to an external Paper server.

**Tech Stack:** Java 21, Fabric 1.21.11, PaperMC, SQLite, commonmark-java, JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-16-bugs-and-features-design.md`

---

## File Map

| File | Responsibility | Tasks |
|------|---------------|-------|
| `common/.../PacketCodec.java` | Handshake + pin packet encode/decode | T1, T7 |
| `common/src/test/.../PacketCodecTest.java` | Codec unit tests | T1, T7 |
| `client/.../ClientSession.java` | Player UUID, pinned quest IDs, UI state | T1, T7 |
| `client/.../network/ClientPacketHandler.java` | S2C handler, cross-list cleanup | T1, T5 |
| `client/.../markdown/MarkdownRenderer.java` | Fix task list render + stripToPlainText | T2, T3 |
| `client/.../gui/screen/ViewQuestScreen.java` | Optimistic delete (then deleted in T6) | T4 |
| `client/.../gui/screen/QuestScreen.java` | NEW: merged view/edit screen | T6 |
| `client/.../gui/screen/MainScreen.java` | Quest Board rename, pin button, rainbow title, date format | T6, T8, T9, T10, T11, T12 |
| `client/.../gui/widget/list/QuestListWidget.java` | Date format, pinned indicator for multi-pin | T7, T12 |
| `client/.../hud/HudPinManager.java` | Multi-pin logic | T7 |
| `client/.../hud/HudPinRenderer.java` | Stacked rendering, configurable width | T7, T10 |
| `client/.../gui/helper/DisquestsConfig.java` | NEW: general config loader | T10 |
| `paper/.../ServerPacketHandler.java` | Handshake with playerUuid, toggle-pin | T1, T7 |
| `paper/.../DataManager.java` | Multi-pin DB schema + migration | T7 |
| `paper/src/test/.../DataManagerTest.java` | DB migration + multi-pin tests | T7 |
| `client/src/testmod/.../DisquestsE2ETest.java` | NEW: E2E test entry point | T13 |
| `client/src/testmod/resources/fabric.mod.json` | NEW: testmod metadata | T13 |

---

## Chunk 1: Bug Fixes (T1-T5)

### Task 1: B1 — Fix UUID mismatch after reconnect (Handshake + ClientSession)

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketCodec.java:55,144-150,314-318`
- Modify: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientSession.java:10,17-22,24-31,62-68`
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java:47-50`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java:319-323`

- [ ] **Step 1: Update HandshakePayload record to include playerUuid**

In `PacketCodec.java`, change the `HandshakePayload` record:

```java
public record HandshakePayload(String bluemapUrl, int pendingRequestCount, UUID pinnedQuestId, UUID playerUuid) {}
```

- [ ] **Step 2: Update writeHandshake to send playerUuid**

In `PacketCodec.java`, change `writeHandshake`:

```java
public static byte[] writeHandshake(String bluemapUrl, int pendingRequestCount, UUID pinnedQuestId, UUID playerUuid) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.HANDSHAKE.getId());
    writeNullableString(buf, bluemapUrl);
    buf.writeVarInt(pendingRequestCount);
    writeNullableUUID(buf, pinnedQuestId);
    buf.writeUUID(playerUuid);
    return buf.toByteArray();
}
```

- [ ] **Step 3: Update readHandshake to read playerUuid**

In `PacketCodec.java`, change `readHandshake`:

```java
public static HandshakePayload readHandshake(ByteBufReader buf) {
    String bluemapUrl = readNullableString(buf);
    int pendingRequestCount = buf.readVarInt();
    UUID pinnedQuestId = readNullableUUID(buf);
    UUID playerUuid = buf.readUUID();
    return new HandshakePayload(bluemapUrl, pendingRequestCount, pinnedQuestId, playerUuid);
}
```

- [ ] **Step 4: Update ServerPacketHandler to send playerUuid in handshake**

In `ServerPacketHandler.java`, change `sendHandshake`:

```java
private void sendHandshake(Player player) {
    UUID pinnedId = dataManager.getPinnedQuestId(player.getUniqueId());
    int pendingCount = dataManager.getPendingRequestCount(player.getUniqueId());
    sendPacket(player, PacketCodec.writeHandshake(
        config.getBluemapUrl(), pendingCount, pinnedId, player.getUniqueId()));
}
```

- [ ] **Step 5: Add playerUuid field to ClientSession**

In `ClientSession.java`, add field and update methods:

```java
private static UUID playerUuid = null;

public static void joinServer(String bluemapUrl, int pendingCount, UUID pinnedId, UUID playerUuid) {
    onServer = true;
    ClientSession.bluemapUrl = bluemapUrl;
    pendingRequestCount = pendingCount;
    pinnedQuestId = pinnedId;
    ClientSession.playerUuid = playerUuid;
}

public static void leaveServer() {
    onServer = false;
    bluemapUrl = null;
    pendingRequestCount = 0;
    pinnedQuestId = null;
    playerUuid = null;
    activeTab = 0;
    searchTerm = "";
    serverQuestsFilter = 0;
}

public static UUID getPlayerUuid() {
    return playerUuid;
}
```

- [ ] **Step 6: Update ClientPacketHandler to pass playerUuid from handshake**

In `ClientPacketHandler.java`, change `handleHandshake`:

```java
private static void handleHandshake(ByteBufReader r) {
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(r);
    ClientSession.joinServer(payload.bluemapUrl(), payload.pendingRequestCount(),
            payload.pinnedQuestId(), payload.playerUuid());
    PacketSender.requestSync();
}
```

- [ ] **Step 7: Update existing handshake tests and add new test**

The existing tests `testHandshakeRoundTrip`, `testHandshakeNoPinnedQuest`, and `testHandshakeNullBluemapUrl` in `PacketCodecTest.java` call the old 3-argument `writeHandshake`. They must all be updated to pass a 4th `playerUuid` argument and assert on `payload.playerUuid()`.

For each existing test, add a `UUID playerUuid = UUID.randomUUID()` (or use a fixed UUID), pass it to `writeHandshake`, and add `assertEquals(playerUuid, payload.playerUuid())`.

Example update for `testHandshakeRoundTrip`:

```java
@Test
void testHandshakeRoundTrip() {
    UUID pinnedQuestId = UUID.randomUUID();
    UUID playerUuid = UUID.randomUUID();
    byte[] data = PacketCodec.writeHandshake("https://bluemap.disqt.com/", 3, pinnedQuestId, playerUuid);
    ByteBufReader reader = new ByteBufReader(data);
    PacketCodec.readType(reader);
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);
    assertEquals("https://bluemap.disqt.com/", payload.bluemapUrl());
    assertEquals(3, payload.pendingRequestCount());
    assertEquals(pinnedQuestId, payload.pinnedQuestId());
    assertEquals(playerUuid, payload.playerUuid());
}
```

Apply the same pattern to `testHandshakeNoPinnedQuest` (pass `null` for pinnedQuestId, real UUID for playerUuid) and `testHandshakeNullBluemapUrl` (pass `null` for bluemapUrl, real UUID for playerUuid).

- [ ] **Step 8: Run tests**

```bash
./gradlew :common:test
```

Expected: All tests pass, including the new handshake test.

- [ ] **Step 9: Commit**

```bash
git add common/src/main/java/com/disqt/disquests/common/PacketCodec.java \
        common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java \
        client/src/main/java/com/disqt/disquests/client/ClientSession.java \
        client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java \
        paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java
git commit -m "fix: send canonical player UUID in handshake to fix edit/delete after reconnect"
```

### Task 2: B2 — Fix task list items not displaying in view mode

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java:85-165`

The issue is in `renderBlock()` for ListItem with TaskListItemMarker (lines 111-130). The root cause is unknown -- it could be a parse tree structure issue, an exception in the rendering path, or a `MarkdownWidget` layout bug.

- [ ] **Step 1: Investigate the rendering path**

Read `MarkdownRenderer.java` lines 85-165. Add temporary debug logging to trace the issue:

```java
// In renderBlock(), at the top:
System.out.println("[Disquests MD Debug] renderBlock: " + node.getClass().getSimpleName());

// In the ListItem handler, inside the TaskListItemMarker branch:
System.out.println("[Disquests MD Debug] TaskListItemMarker checked=" + marker.isChecked()
    + " nextSibling=" + (inlineFirst.getNext() != null ? inlineFirst.getNext().getClass().getSimpleName() : "null"));
```

Build and run the client. Create a quest with content `- [ ] test task` and open it in ViewQuestScreen. Check the game log for the debug output. This reveals whether:
- The node tree is structured as expected (BulletList > ListItem > Paragraph > TaskListItemMarker + Text)
- The code reaches the TaskListItemMarker branch at all
- An exception is thrown (check for `Failed to handle` warnings in the log)

Note: `collectInlineText(para, style, inlineFirst.getNext())` already handles a null `startFrom` gracefully (returns `Text.empty()`), so a null-safety fix alone won't help. The actual root cause must be identified via debugging.

- [ ] **Step 2: Apply the fix based on investigation findings**

After identifying the root cause from debug output, apply the appropriate fix. Common possibilities:
- If the parse tree structure differs from expected, update the node traversal logic
- If an exception is thrown in `MarkdownWidget` (e.g., empty line list), add defensive handling
- If the `RenderedLine` produced has zero-width text, investigate why `MarkdownWidget.rebuildWrappedLines()` produces no output for it

Remove all debug logging after the fix is confirmed.

- [ ] **Step 3: Verify manually**

Build the client mod and test in-game: create a quest with content `- [ ] task item` and verify it renders in the formatted view (ViewQuestScreen).

```bash
./gradlew :client:build
```

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java
git commit -m "fix: handle null next-sibling in task list item rendering"
```

### Task 3: B4 — Fix checkboxes not rendering in pinned HUD

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java:48-75`

- [ ] **Step 1: Add TaskListItemMarker handling to collectPlainText**

In `MarkdownRenderer.java`, inside `collectPlainText()`, add a case before the generic else block:

```java
} else if (node instanceof TaskListItemMarker marker) {
    sb.append(marker.isChecked() ? "[x] " : "[ ] ");
```

The full method after the fix (showing the relevant section):

```java
private static void collectPlainText(Node node, StringBuilder sb) {
    if (node instanceof org.commonmark.node.Text textNode) {
        sb.append(textNode.getLiteral());
    } else if (node instanceof SoftLineBreak) {
        sb.append(' ');
    } else if (node instanceof HardLineBreak) {
        sb.append('\n');
    } else if (node instanceof Code code) {
        sb.append(code.getLiteral());
    } else if (node instanceof FencedCodeBlock code) {
        sb.append(code.getLiteral());
    } else if (node instanceof IndentedCodeBlock code) {
        sb.append(code.getLiteral());
    } else if (node instanceof TaskListItemMarker marker) {
        sb.append(marker.isChecked() ? "[x] " : "[ ] ");
    } else if (node instanceof Paragraph) {
        Node child = node.getFirstChild();
        while (child != null) {
            collectPlainText(child, sb);
            child = child.getNext();
        }
        sb.append('\n');
    } else {
        Node child = node.getFirstChild();
        while (child != null) {
            collectPlainText(child, sb);
            child = child.getNext();
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew :client:build
```

Test in-game: pin a quest with `- [ ] task` content and verify `[ ] task` appears in the HUD overlay.

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java
git commit -m "fix: render task list checkboxes in pinned HUD plain text"
```

### Task 4: B3 — Fix delete not updating quest list immediately

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java:159-163`

- [ ] **Step 1: Add optimistic cache removal before closing**

In `ViewQuestScreen.java`, change `confirmDelete()`:

```java
private void confirmDelete() {
    showConfirm(Text.literal("Delete quest \"" + quest.getTitle() + "\"?"), () -> {
        ClientCache.removeQuestById(quest.getId());
        PacketSender.deleteQuest(quest.getId());
        this.close();
    });
}
```

Note: `ClientCache.removeQuestById()` is called BEFORE `PacketSender.deleteQuest()` so the cache is clean when MainScreen re-initializes.

- [ ] **Step 2: Build and verify**

```bash
./gradlew :client:build
```

Test in-game: delete a quest, verify it disappears from My Quests immediately without re-opening.

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java
git commit -m "fix: optimistically remove quest from cache on delete for instant list update"
```

### Task 5: B5 — Fix quest appearing in wrong list (cross-list cleanup)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java:71-81`

- [ ] **Step 1: Update handleUpdateQuest to use canonical UUID and clean up cross-list**

In `ClientPacketHandler.java`, change `handleUpdateQuest()`:

```java
private static void handleUpdateQuest(ByteBufReader r) {
    QuestData data = PacketCodec.readUpdateQuest(r);
    Quest quest = Quest.fromNetwork(data);
    UUID myUuid = ClientSession.getPlayerUuid();
    if (myUuid == null) {
        myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
    }
    boolean isMine = data.ownerUuid().equals(myUuid) ||
            data.contributors().stream().anyMatch(c -> c.uuid().equals(myUuid));
    if (isMine) {
        ClientCache.addOrUpdateMyQuest(quest);
        ClientCache.removeFromServerQuests(quest.getId());
    } else {
        ClientCache.addOrUpdateServerQuest(quest);
        ClientCache.removeFromMyQuests(quest.getId());
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew :client:build
```

Test in-game: join an OPEN quest, verify it moves from Quest Board to My Quests and doesn't appear in both.

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java
git commit -m "fix: use canonical UUID and clean cross-list on quest update"
```

---

## Chunk 2: QuestScreen Merge (T6)

### Task 6: F1 — Create merged QuestScreen replacing ViewQuestScreen + EditQuestScreen

This is the largest task. The new `QuestScreen` combines both screens into one with a view/edit toggle.

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Delete: `client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java`
- Delete: `client/src/main/java/com/disqt/disquests/client/gui/screen/EditQuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java:318,321-332`

- [ ] **Step 1: Create QuestScreen.java with view mode**

Create `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`. Start with just the view mode, which replicates ViewQuestScreen's functionality:

```java
package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.BlueMapHelper;
import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.ScreenLayouts;
import com.disqt.disquests.client.gui.helper.UIHelper;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import com.disqt.disquests.client.gui.widget.MarkdownWidget;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.client.gui.widget.ReadOnlyMultiLineTextFieldWidget;
import com.disqt.disquests.client.hud.HudPinManager;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class QuestScreen extends BaseScreen {

    private final Quest quest;
    private boolean editing;
    private final boolean isNewQuest;

    // Permissions
    private boolean isOwner;
    private boolean canEdit;

    // View mode widgets
    private ReadOnlyMultiLineTextFieldWidget titleView;
    private MarkdownWidget contentView;

    // Edit mode widgets
    private MultiLineTextFieldWidget titleField;
    private MultiLineTextFieldWidget contentField;

    // Dirty tracking
    private String originalTitle;
    private String originalContent;

    // Edit mode state
    private boolean regionEnabled;

    // Buttons that need state tracking
    private DarkButtonWidget editButton;
    private DarkButtonWidget deleteButton;
    private DarkButtonWidget pinButton;

    /**
     * Open in view mode for an existing quest.
     */
    public QuestScreen(Screen parent, Quest quest) {
        this(parent, quest, false);
    }

    /**
     * Open in edit mode for a new quest.
     */
    public QuestScreen(Screen parent, Quest quest, boolean startInEditMode) {
        super(Text.literal("Quest"), parent);
        this.quest = quest;
        this.editing = startInEditMode;
        this.isNewQuest = startInEditMode;
        this.regionEnabled = quest.isRegion();
    }

    @Override
    protected void init() {
        super.init();

        UUID myUuid = ClientSession.getPlayerUuid();
        if (myUuid == null) {
            myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        }
        this.isOwner = quest.getOwnerUuid().equals(myUuid);
        this.canEdit = isOwner || quest.getContributors().stream()
                .anyMatch(c -> c.getUuid().equals(myUuid) && c.canEdit());

        if (editing) {
            initEditMode();
        } else {
            initViewMode();
        }
    }

    // ==================== VIEW MODE ====================

    private void initViewMode() {
        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        boolean hasCoords = quest.getCoordinates() != null;
        int metadataHeight = hasCoords ? 24 : 0;

        String bluemapUrl = BlueMapHelper.buildUrl(quest);
        boolean hasBluemap = bluemapUrl != null;

        int buttonsY = UIHelper.getBottomButtonY(this);
        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();

        // --- BUTTONS ---
        List<Text> buttonTexts = new ArrayList<>();
        buttonTexts.add(Text.literal("Edit"));
        buttonTexts.add(Text.literal("Delete"));
        boolean isPinned = HudPinManager.isPinned(quest.getId());
        buttonTexts.add(Text.literal(isPinned ? "Unpin" : "Pin to HUD"));
        buttonTexts.add(Text.literal("Close"));

        UIHelper.createButtonRow(this, buttonsY, buttonTexts, (index, x, width) -> {
            switch (index) {
                case 0 -> {
                    this.editButton = this.addDrawableChild(new DarkButtonWidget(
                            x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                            buttonTexts.get(0), b -> enterEditMode()));
                    this.editButton.active = canEdit;
                }
                case 1 -> {
                    this.deleteButton = this.addDrawableChild(new DarkButtonWidget(
                            x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                            buttonTexts.get(1), b -> confirmDelete()));
                    this.deleteButton.active = isOwner;
                }
                case 2 -> {
                    this.pinButton = this.addDrawableChild(new DarkButtonWidget(
                            x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                            buttonTexts.get(2), b -> togglePin()));
                }
                case 3 -> this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                        buttonTexts.get(3), b -> this.close()));
            }
        });

        // --- TITLE ---
        int titleY = ScreenLayouts.TOP_MARGIN + 5;
        this.titleView = new ReadOnlyMultiLineTextFieldWidget(
                this.textRenderer, contentX, titleY,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT,
                quest.getTitle(), 1, false
        );
        this.addSelectableChild(this.titleView);

        // --- CONTENT ---
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin - metadataHeight;
        if (metadataHeight > 0) contentPanelBottom -= ScreenLayouts.PANEL_SPACING;
        int contentPanelHeight = contentPanelBottom - contentPanelY;

        List<RenderedLine> rendered = MarkdownRenderer.render(
                Objects.requireNonNullElse(quest.getContent(), ""));
        this.contentView = new MarkdownWidget(
                this.textRenderer, contentX, contentPanelY,
                contentWidth, contentPanelHeight, rendered
        );
        this.addSelectableChild(this.contentView);

        // --- BLUEMAP BUTTON ---
        if (hasCoords && hasBluemap) {
            int metaY = contentPanelBottom + ScreenLayouts.PANEL_SPACING;
            int bmBtnWidth = this.textRenderer.getWidth("View on BlueMap") + UIHelper.BUTTON_TEXT_PADDING * 2;
            bmBtnWidth = Math.max(bmBtnWidth, UIHelper.MIN_BUTTON_WIDTH);
            int bmBtnX = contentX + contentWidth - bmBtnWidth;

            final String url = bluemapUrl;
            this.addDrawableChild(new DarkButtonWidget(
                    bmBtnX, metaY, bmBtnWidth, 20,
                    Text.literal("View on BlueMap"),
                    b -> {
                        try { Util.getOperatingSystem().open(URI.create(url)); }
                        catch (Exception ignored) {}
                    }
            ));
        }
    }

    // ==================== EDIT MODE ====================

    private void initEditMode() {
        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        int optionalFieldsHeight = 30;
        if (regionEnabled) optionalFieldsHeight += 18;
        optionalFieldsHeight += 18; // map row
        optionalFieldsHeight += 8; // padding

        int settingsRowHeight = isOwner ? 24 : 0;
        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();
        int buttonsY = UIHelper.getBottomButtonY(this);

        // --- BUTTONS: Save, Cancel ---
        List<Text> bottomButtonTexts = List.of(Text.literal("Save"), Text.literal("Cancel"));
        UIHelper.createButtonRow(this, buttonsY, bottomButtonTexts, (index, x, width) -> {
            switch (index) {
                case 0 -> this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                        bottomButtonTexts.get(0), b -> saveAndView()));
                case 1 -> this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                        bottomButtonTexts.get(1), b -> cancelEdit()));
            }
        });

        // --- TITLE FIELD ---
        int titleY = ScreenLayouts.TOP_MARGIN + 5;
        this.titleField = new MultiLineTextFieldWidget(
                this.textRenderer, contentX, titleY,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT,
                quest.getTitle(), "Quest title...", 1, false
        );
        this.addSelectableChild(this.titleField);

        if (originalTitle == null) {
            originalTitle = quest.getTitle() != null ? quest.getTitle() : "";
            originalContent = quest.getContent() != null ? quest.getContent() : "";
        }

        // --- CONTENT FIELD ---
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin
                - optionalFieldsHeight - ScreenLayouts.PANEL_SPACING
                - settingsRowHeight - (settingsRowHeight > 0 ? ScreenLayouts.PANEL_SPACING : 0);
        int contentPanelHeight = Math.max(30, contentPanelBottom - contentPanelY);

        this.contentField = new MultiLineTextFieldWidget(
                this.textRenderer, contentX, contentPanelY,
                contentWidth, contentPanelHeight,
                quest.getContent() != null ? quest.getContent() : "",
                "Quest content...", Integer.MAX_VALUE, true
        );
        this.addSelectableChild(this.contentField);

        // --- OPTIONAL FIELDS ---
        int optPanelY = contentPanelY + contentPanelHeight + ScreenLayouts.PANEL_SPACING;
        int rowY = optPanelY + 4;

        buildCoordsRow(contentX, rowY, contentWidth);
        rowY += 18;
        if (regionEnabled) {
            buildCorner2Row(contentX, rowY, contentWidth);
            rowY += 18;
        }
        buildMapRow(contentX, rowY, contentWidth);

        // --- SETTINGS ROW (owner only) ---
        if (isOwner) {
            int settingsY = optPanelY + optionalFieldsHeight + ScreenLayouts.PANEL_SPACING;
            buildSettingsRow(contentX, settingsY, contentWidth);
        }

        this.setInitialFocus(this.titleField);
    }

    // ==================== MODE SWITCHING ====================

    private void enterEditMode() {
        if (!canEdit) return;
        persistFieldValues();
        this.editing = true;
        this.clearAndInit();
    }

    private void cancelEdit() {
        if (isNewQuest) {
            this.close();
            return;
        }
        if (isDirty()) {
            showConfirm(Text.literal("Discard unsaved changes?"), () -> {
                // Revert quest state
                quest.setTitle(originalTitle);
                quest.setContent(originalContent);
                this.editing = false;
                this.clearAndInit();
            });
        } else {
            this.editing = false;
            this.clearAndInit();
        }
    }

    private void saveAndView() {
        persistFieldValues();
        ClientCache.addOrUpdateMyQuest(quest);
        PacketSender.saveQuest(
                quest.getId(), quest.getTitle(), quest.getContent(),
                quest.getCoordinates(), quest.isRegion(),
                quest.getCoordinates2(), quest.getMap()
        );
        UUID myUuid = ClientSession.getPlayerUuid();
        if (myUuid == null) myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        if (quest.getOwnerUuid().equals(myUuid)) {
            PacketSender.updateVisibility(quest.getId(), quest.getVisibility());
        }
        // Update originals for dirty tracking
        originalTitle = quest.getTitle();
        originalContent = quest.getContent();
        this.editing = false;
        this.clearAndInit();
    }

    // ==================== ACTIONS ====================

    private void confirmDelete() {
        showConfirm(Text.literal("Delete quest \"" + quest.getTitle() + "\"?"), () -> {
            ClientCache.removeQuestById(quest.getId());
            PacketSender.deleteQuest(quest.getId());
            this.close();
        });
    }

    private void togglePin() {
        HudPinManager.toggle(quest.getId());
        this.clearAndInit();
    }

    // ==================== EDIT HELPERS ====================

    private void buildCoordsRow(int panelX, int rowY, int panelWidth) {
        int labelWidth = this.textRenderer.getWidth("Coords: ");
        int btnWidth = 50;
        int btnHeight = 14;
        int spacing = 4;

        int setPosX = panelX + labelWidth + getDisplayedCoordsWidth() + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                setPosX, rowY, btnWidth, btnHeight,
                Text.literal("Set Pos"), b -> setPlayerPosition()));

        String regionText = regionEnabled ? "[x] Region" : "[ ] Region";
        int regionBtnWidth = this.textRenderer.getWidth(regionText) + UIHelper.BUTTON_TEXT_PADDING;
        int regionBtnX = setPosX + btnWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                regionBtnX, rowY, regionBtnWidth, btnHeight,
                Text.literal(regionText), b -> toggleRegion()));

        int clearX = regionBtnX + regionBtnWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                clearX, rowY, btnWidth, btnHeight,
                Text.literal("Clear"), b -> clearCoords()));
    }

    private int getDisplayedCoordsWidth() {
        return this.textRenderer.getWidth(getCoordsDisplayText());
    }

    private String getCoordsDisplayText() {
        CoordinatesData c = quest.getCoordinates();
        if (c == null) return "Not set";
        return String.format("X:%.0f Y:%.0f Z:%.0f", c.x(), c.y(), c.z());
    }

    private void buildCorner2Row(int panelX, int rowY, int panelWidth) {
        int labelWidth = this.textRenderer.getWidth("Corner 2: ");
        int btnWidth = 50;
        int btnHeight = 14;
        int spacing = 4;

        CoordinatesData c2 = quest.getCoordinates2();
        String corner2Text = c2 != null ? String.format("X:%.0f Y:%.0f Z:%.0f", c2.x(), c2.y(), c2.z()) : "Not set";
        int corner2TextWidth = this.textRenderer.getWidth(corner2Text);

        int setBtnX = panelX + labelWidth + corner2TextWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                setBtnX, rowY, btnWidth, btnHeight,
                Text.literal("Set"), b -> setCorner2Position()));
    }

    private void buildMapRow(int panelX, int rowY, int panelWidth) {
        int labelWidth = this.textRenderer.getWidth("Map: ");
        String mapText = quest.getMap() != null ? quest.getMap() : "Any";
        int mapTextWidth = this.textRenderer.getWidth(mapText);
        int btnWidth = 50;
        int btnHeight = 14;
        int spacing = 4;

        int autoBtnX = panelX + labelWidth + mapTextWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                autoBtnX, rowY, btnWidth, btnHeight,
                Text.literal("Auto"), b -> autoMap()));

        int clearBtnX = autoBtnX + btnWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                clearBtnX, rowY, btnWidth, btnHeight,
                Text.literal("Clear"), b -> clearMap()));
    }

    private void buildSettingsRow(int panelX, int settingsY, int panelWidth) {
        int btnHeight = 16;
        int spacing = 8;

        String visText = "Visibility: " + quest.getVisibility().name();
        int visBtnWidth = this.textRenderer.getWidth(visText) + UIHelper.BUTTON_TEXT_PADDING * 2;
        visBtnWidth = Math.max(visBtnWidth, UIHelper.MIN_BUTTON_WIDTH);
        this.addDrawableChild(new DarkButtonWidget(
                panelX, settingsY, visBtnWidth, btnHeight,
                Text.literal(visText), b -> cycleVisibility()));

        int contribCount = quest.getContributors() != null ? quest.getContributors().size() : 0;
        String contribText = "Contributors (" + contribCount + ")";
        int contribBtnWidth = this.textRenderer.getWidth(contribText) + UIHelper.BUTTON_TEXT_PADDING * 2;
        contribBtnWidth = Math.max(contribBtnWidth, UIHelper.MIN_BUTTON_WIDTH);
        this.addDrawableChild(new DarkButtonWidget(
                panelX + visBtnWidth + spacing, settingsY, contribBtnWidth, btnHeight,
                Text.literal(contribText), b -> openContributors()));
    }

    private void setPlayerPosition() {
        if (client != null && client.player != null) {
            quest.setCoordinates(new CoordinatesData(
                    client.player.getX(), client.player.getY(), client.player.getZ()));
            if (quest.getMap() == null && client.world != null) {
                quest.setMap(client.world.getRegistryKey().getValue().getPath());
            }
            persistFieldValues();
            this.clearAndInit();
        }
    }

    private void setCorner2Position() {
        if (client != null && client.player != null) {
            quest.setCoordinates2(new CoordinatesData(
                    client.player.getX(), client.player.getY(), client.player.getZ()));
            persistFieldValues();
            this.clearAndInit();
        }
    }

    private void toggleRegion() {
        persistFieldValues();
        this.regionEnabled = !this.regionEnabled;
        quest.setRegion(this.regionEnabled);
        if (!this.regionEnabled) quest.setCoordinates2(null);
        this.clearAndInit();
    }

    private void clearCoords() {
        quest.setCoordinates(null);
        quest.setCoordinates2(null);
        this.regionEnabled = false;
        quest.setRegion(false);
        persistFieldValues();
        this.clearAndInit();
    }

    private void autoMap() {
        if (client != null && client.world != null) {
            quest.setMap(client.world.getRegistryKey().getValue().getPath());
            persistFieldValues();
            this.clearAndInit();
        }
    }

    private void clearMap() {
        quest.setMap(null);
        persistFieldValues();
        this.clearAndInit();
    }

    private void cycleVisibility() {
        Visibility current = quest.getVisibility();
        Visibility next = switch (current) {
            case PRIVATE -> Visibility.CLOSED;
            case CLOSED -> Visibility.OPEN;
            case OPEN -> Visibility.PRIVATE;
        };
        quest.setVisibility(next);
        persistFieldValues();
        this.clearAndInit();
    }

    private void openContributors() {
        persistFieldValues();
        open(new ContributorScreen(this, quest));
    }

    private void persistFieldValues() {
        if (this.titleField != null) quest.setTitle(this.titleField.getText());
        if (this.contentField != null) quest.setContent(this.contentField.getText());
    }

    private boolean isDirty() {
        String currentTitle = this.titleField != null ? this.titleField.getText() : quest.getTitle();
        String currentContent = this.contentField != null ? this.contentField.getText() : quest.getContent();
        if (currentTitle == null) currentTitle = "";
        if (currentContent == null) currentContent = "";
        return !currentTitle.equals(originalTitle) || !currentContent.equals(originalContent);
    }

    // ==================== AUTO-CLOSE ====================

    @Override
    public void tick() {
        super.tick();
        if (!isNewQuest && ClientCache.getQuestById(quest.getId()) == null) {
            this.close();
        }
    }

    // ==================== RENDERING ====================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        if (editing) {
            renderEditMode(context, contentX, contentWidth, mouseX, mouseY, delta);
        } else {
            renderViewMode(context, contentX, contentWidth, mouseX, mouseY, delta);
        }
    }

    private void renderViewMode(DrawContext context, int contentX, int contentWidth,
                                int mouseX, int mouseY, float delta) {
        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();
        boolean hasCoords = quest.getCoordinates() != null;
        int metadataHeight = hasCoords ? 24 : 0;

        // Screen title
        context.drawCenteredTextWithShadow(this.textRenderer, "View Quest", this.width / 2, 8, Colors.TEXT_PRIMARY);

        // Owner & visibility info
        String ownerInfo = "by " + quest.getOwnerName();
        if (quest.getVisibility() != null) {
            ownerInfo += "  [" + quest.getVisibility().name() + "]";
        }
        int ownerInfoWidth = this.textRenderer.getWidth(ownerInfo);
        context.drawText(this.textRenderer, ownerInfo,
                contentX + contentWidth - ownerInfoWidth,
                ScreenLayouts.TOP_MARGIN - 2, Colors.TEXT_MUTED, false);

        // Title Panel
        UIHelper.drawPanel(context, contentX, ScreenLayouts.TOP_MARGIN,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT);
        this.titleView.render(context, mouseX, mouseY, delta);

        // Content Panel
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin - metadataHeight;
        if (metadataHeight > 0) contentPanelBottom -= ScreenLayouts.PANEL_SPACING;

        UIHelper.drawPanel(context, contentX, contentPanelY, contentWidth, contentPanelBottom - contentPanelY);
        this.contentView.render(context, mouseX, mouseY, delta);

        // Metadata bar
        if (hasCoords) {
            int metaY = contentPanelBottom + ScreenLayouts.PANEL_SPACING;
            UIHelper.drawPanel(context, contentX, metaY, contentWidth, metadataHeight);
            String coordsText = buildCoordsText();
            int textY = metaY + (metadataHeight - 8) / 2;
            context.drawText(this.textRenderer, coordsText, contentX + 5, textY, Colors.TEXT_MUTED, false);
            if (quest.getMap() != null) {
                String mapText = "Map: " + quest.getMap();
                int coordsWidth = this.textRenderer.getWidth(coordsText);
                context.drawText(this.textRenderer, mapText,
                        contentX + 5 + coordsWidth + 12, textY, Colors.TEXT_MUTED, false);
            }
        }
    }

    private void renderEditMode(DrawContext context, int contentX, int contentWidth,
                                int mouseX, int mouseY, float delta) {
        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();

        int optionalFieldsHeight = 30;
        if (regionEnabled) optionalFieldsHeight += 18;
        optionalFieldsHeight += 18;
        optionalFieldsHeight += 8;

        int settingsRowHeight = isOwner ? 24 : 0;

        // Screen title
        context.drawCenteredTextWithShadow(this.textRenderer, "Edit Quest", this.width / 2, 8, Colors.TEXT_PRIMARY);

        // Title Panel
        UIHelper.drawPanel(context, contentX, ScreenLayouts.TOP_MARGIN,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT);
        this.titleField.render(context, mouseX, mouseY, delta);

        // Content Panel
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin
                - optionalFieldsHeight - ScreenLayouts.PANEL_SPACING
                - settingsRowHeight - (settingsRowHeight > 0 ? ScreenLayouts.PANEL_SPACING : 0);
        int contentPanelHeight = Math.max(30, contentPanelBottom - contentPanelY);

        UIHelper.drawPanel(context, contentX, contentPanelY, contentWidth, contentPanelHeight);
        this.contentField.render(context, mouseX, mouseY, delta);

        // Optional fields panel
        int optPanelY = contentPanelY + contentPanelHeight + ScreenLayouts.PANEL_SPACING;
        UIHelper.drawPanel(context, contentX, optPanelY, contentWidth, optionalFieldsHeight);

        int rowY = optPanelY + 4;

        // Coords label + value
        context.drawText(this.textRenderer, "Coords: ", contentX + 5, rowY + 3, Colors.TEXT_MUTED, false);
        int labelWidth = this.textRenderer.getWidth("Coords: ");
        String coordsText = getCoordsDisplayText();
        int coordsColor = quest.getCoordinates() != null ? Colors.TEXT_PRIMARY : Colors.TEXT_DISABLED;
        context.drawText(this.textRenderer, coordsText, contentX + 5 + labelWidth, rowY + 3, coordsColor, false);
        rowY += 18;

        if (regionEnabled) {
            context.drawText(this.textRenderer, "Corner 2: ", contentX + 5, rowY + 3, Colors.TEXT_MUTED, false);
            int corner2LabelWidth = this.textRenderer.getWidth("Corner 2: ");
            CoordinatesData c2 = quest.getCoordinates2();
            String corner2Text = c2 != null ? String.format("X:%.0f Y:%.0f Z:%.0f", c2.x(), c2.y(), c2.z()) : "Not set";
            int c2Color = c2 != null ? Colors.TEXT_PRIMARY : Colors.TEXT_DISABLED;
            context.drawText(this.textRenderer, corner2Text, contentX + 5 + corner2LabelWidth, rowY + 3, c2Color, false);
            rowY += 18;
        }

        context.drawText(this.textRenderer, "Map: ", contentX + 5, rowY + 3, Colors.TEXT_MUTED, false);
        int mapLabelWidth = this.textRenderer.getWidth("Map: ");
        String mapText = quest.getMap() != null ? quest.getMap() : "Any";
        int mapColor = quest.getMap() != null ? Colors.TEXT_PRIMARY : Colors.TEXT_DISABLED;
        context.drawText(this.textRenderer, mapText, contentX + 5 + mapLabelWidth, rowY + 3, mapColor, false);
    }

    private String buildCoordsText() {
        CoordinatesData c = quest.getCoordinates();
        if (quest.isRegion() && quest.getCoordinates2() != null) {
            CoordinatesData c2 = quest.getCoordinates2();
            return String.format("Region: X:%.0f-%.0f Y:%.0f-%.0f Z:%.0f-%.0f",
                    Math.min(c.x(), c2.x()), Math.max(c.x(), c2.x()),
                    Math.min(c.y(), c2.y()), Math.max(c.y(), c2.y()),
                    Math.min(c.z(), c2.z()), Math.max(c.z(), c2.z()));
        }
        return String.format("X:%.0f Y:%.0f Z:%.0f", c.x(), c.y(), c.z());
    }

    // ==================== INPUT ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // In view mode, clicking the content area enters edit mode (if permitted)
        if (!editing && canEdit && button == 0 && contentView != null && contentView.isMouseOver(mouseX, mouseY)) {
            enterEditMode();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (!editing && this.titleView != null && this.titleView.keyPressed(keyInput)) return true;
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (editing) {
            if (this.contentField != null && this.contentField.isMouseOver(mouseX, mouseY)) {
                return this.contentField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
            if (this.titleField != null && this.titleField.isMouseOver(mouseX, mouseY)) {
                return this.titleField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        } else {
            if (this.titleView != null && this.titleView.isMouseOver(mouseX, mouseY)) {
                return this.titleView.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
            if (this.contentView != null && this.contentView.isMouseOver(mouseX, mouseY)) {
                return this.contentView.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        }
        return false;
    }
}
```

- [ ] **Step 2: Update MainScreen to use QuestScreen**

In `MainScreen.java`, change `createNewQuest()` and `openSelected()`:

```java
private void createNewQuest() {
    Quest newQuest = new Quest();
    newQuest.setId(UUID.randomUUID());
    newQuest.setTitle("New Quest");
    newQuest.setContent("");
    newQuest.setVisibility(Visibility.PRIVATE);
    UUID myUuid = ClientSession.getPlayerUuid();
    if (myUuid == null) myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
    newQuest.setOwnerUuid(myUuid);
    newQuest.setOwnerName(MinecraftClient.getInstance().getSession().getUsername());
    newQuest.setLastModified(System.currentTimeMillis() / 1000);
    newQuest.setContributors(new ArrayList<>());
    open(new QuestScreen(this, newQuest, true));
}

public void openSelected() {
    if (currentTab == TAB_MY_QUESTS) {
        Quest sel = myQuestListWidget.getSelectedQuest();
        if (sel != null) open(new QuestScreen(this, sel));
    } else {
        Quest sel = serverQuestListWidget.getSelectedQuest();
        if (sel != null) open(new QuestScreen(this, sel));
    }
}
```

- [ ] **Step 3: Delete ViewQuestScreen.java and EditQuestScreen.java**

```bash
git rm client/src/main/java/com/disqt/disquests/client/gui/screen/ViewQuestScreen.java
git rm client/src/main/java/com/disqt/disquests/client/gui/screen/EditQuestScreen.java
```

- [ ] **Step 4: Fix any remaining references to deleted screens**

Search for `ViewQuestScreen` and `EditQuestScreen` in the codebase and update all references:

```bash
grep -r "ViewQuestScreen\|EditQuestScreen" client/src/main/ --include="*.java"
```

Key locations to check:
- `ContributorScreen.java` -- may reference EditQuestScreen as parent
- `ConfirmScreen.java` -- should be fine (uses generic Screen parent)

- [ ] **Step 5: Build and verify**

```bash
./gradlew :client:build
```

Test in-game:
1. Open a quest from My Quests -- should show formatted view
2. Click Edit or click content area -- should switch to edit mode
3. Save -- should return to view mode with updated content
4. Cancel with no changes -- should return to view mode
5. Cancel with changes -- should show confirm dialog
6. Create new quest -- should open directly in edit mode
7. Delete -- should remove from cache and return to list

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java \
        client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat: merge View+Edit into single QuestScreen with view/edit toggle"
```

---

## Chunk 3: Multi-Pin + Protocol (T7) and Pin on MainScreen (T8)

### Task 7: F2 + B1 protocol coordination — Multi-pin quests

This task completes the HANDSHAKE protocol change (started in T1 with playerUuid) by replacing the single pinnedQuestId with a list, and updates all pin-related code.

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/PacketCodec.java`
- Modify: `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientSession.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/hud/HudPinManager.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/DataManager.java`
- Modify: `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java`
- Modify: `paper/src/test/java/com/disqt/disquests/paper/DataManagerTest.java`

- [ ] **Step 1: Update HandshakePayload and wire format for multi-pin**

In `PacketCodec.java`, update the record and methods:

```java
public record HandshakePayload(String bluemapUrl, int pendingRequestCount,
        List<UUID> pinnedQuestIds, UUID playerUuid) {}

public static byte[] writeHandshake(String bluemapUrl, int pendingRequestCount,
        List<UUID> pinnedQuestIds, UUID playerUuid) {
    ByteBufWriter buf = new ByteBufWriter();
    buf.writeByte(PacketType.HANDSHAKE.getId());
    writeNullableString(buf, bluemapUrl);
    buf.writeVarInt(pendingRequestCount);
    buf.writeVarInt(pinnedQuestIds.size());
    for (UUID id : pinnedQuestIds) {
        buf.writeUUID(id);
    }
    buf.writeUUID(playerUuid);
    return buf.toByteArray();
}

public static HandshakePayload readHandshake(ByteBufReader buf) {
    String bluemapUrl = readNullableString(buf);
    int pendingRequestCount = buf.readVarInt();
    int pinnedCount = buf.readVarInt();
    List<UUID> pinnedIds = new ArrayList<>(pinnedCount);
    for (int i = 0; i < pinnedCount; i++) {
        pinnedIds.add(buf.readUUID());
    }
    UUID playerUuid = buf.readUUID();
    return new HandshakePayload(bluemapUrl, pendingRequestCount, pinnedIds, playerUuid);
}
```

- [ ] **Step 2: Update ClientSession for multi-pin**

In `ClientSession.java`, replace `pinnedQuestId` with a list:

```java
private static final List<UUID> pinnedQuestIds = new ArrayList<>();

public static void joinServer(String bluemapUrl, int pendingCount, List<UUID> pinnedIds, UUID playerUuid) {
    onServer = true;
    ClientSession.bluemapUrl = bluemapUrl;
    pendingRequestCount = pendingCount;
    pinnedQuestIds.clear();
    pinnedQuestIds.addAll(pinnedIds);
    ClientSession.playerUuid = playerUuid;
}

public static void leaveServer() {
    onServer = false;
    bluemapUrl = null;
    pendingRequestCount = 0;
    pinnedQuestIds.clear();
    playerUuid = null;
    activeTab = 0;
    searchTerm = "";
    serverQuestsFilter = 0;
}

public static List<UUID> getPinnedQuestIds() {
    return pinnedQuestIds;
}

public static boolean isPinned(UUID questId) {
    return questId != null && pinnedQuestIds.contains(questId);
}

public static void addPinnedQuest(UUID questId) {
    if (!pinnedQuestIds.contains(questId)) {
        pinnedQuestIds.add(questId);
    }
}

public static void removePinnedQuest(UUID questId) {
    pinnedQuestIds.remove(questId);
}
```

Remove the old `getPinnedQuestId()` / `setPinnedQuestId()` methods.

- [ ] **Step 3: Update HudPinManager for multi-pin**

Rewrite `HudPinManager.java`:

```java
package com.disqt.disquests.client.hud;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.network.PacketSender;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HudPinManager {

    public static void toggle(UUID questId) {
        if (ClientSession.isPinned(questId)) {
            ClientSession.removePinnedQuest(questId);
        } else {
            ClientSession.addPinnedQuest(questId);
        }
        PacketSender.pinQuest(questId); // server interprets as toggle
    }

    public static boolean isPinned(UUID questId) {
        return ClientSession.isPinned(questId);
    }

    public static List<Quest> getPinnedQuests() {
        List<Quest> quests = new ArrayList<>();
        for (UUID id : ClientSession.getPinnedQuestIds()) {
            Quest q = ClientCache.getQuestById(id);
            if (q != null) quests.add(q);
        }
        return quests;
    }
}
```

- [ ] **Step 4: Update HudPinRenderer for stacked rendering**

Rewrite `HudPinRenderer.java` to iterate pinned quests:

```java
public static void render(DrawContext context) {
    List<Quest> quests = HudPinManager.getPinnedQuests();
    if (quests.isEmpty()) return;

    MinecraftClient client = MinecraftClient.getInstance();
    if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return;

    TextRenderer tr = client.textRenderer;
    int lineHeight = tr.fontHeight + 1;
    int y = MARGIN;

    for (Quest quest : quests) {
        y = renderSinglePin(context, tr, quest, y, lineHeight);
        y += 4; // gap between pinned quests
    }
}

private static int renderSinglePin(DrawContext context, TextRenderer tr, Quest quest, int startY, int lineHeight) {
    List<String> titleLines = wrapText(tr, quest.getTitle(), MAX_WIDTH - PADDING * 2);
    String plainContent = MarkdownRenderer.stripToPlainText(quest.getContent());
    List<String> contentLines = wrapText(tr, plainContent, MAX_WIDTH - PADDING * 2);

    int maxContentLines = MAX_LINES - titleLines.size();
    boolean truncated = false;
    if (contentLines.size() > maxContentLines) {
        contentLines = new ArrayList<>(contentLines.subList(0, maxContentLines));
        truncated = true;
    }

    int totalLines = titleLines.size() + contentLines.size() + (truncated ? 1 : 0);
    int boxHeight = PADDING * 2 + totalLines * lineHeight;

    // Background
    context.fill(MARGIN, startY, MARGIN + MAX_WIDTH, startY + boxHeight, BG_COLOR);

    // Title
    int textY = startY + PADDING;
    for (String line : titleLines) {
        context.drawText(tr, line, MARGIN + PADDING, textY, TITLE_COLOR, true);
        textY += lineHeight;
    }

    // Content
    for (String line : contentLines) {
        context.drawText(tr, line, MARGIN + PADDING, textY, CONTENT_COLOR, true);
        textY += lineHeight;
    }

    if (truncated) {
        context.drawText(tr, "...", MARGIN + PADDING, textY, CONTENT_COLOR, true);
    }

    return startY + boxHeight;
}
```

- [ ] **Step 5: Update ClientPacketHandler for multi-pin handshake**

In `ClientPacketHandler.java`, change `handleHandshake`:

```java
private static void handleHandshake(ByteBufReader r) {
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(r);
    ClientSession.joinServer(payload.bluemapUrl(), payload.pendingRequestCount(),
            payload.pinnedQuestIds(), payload.playerUuid());
    PacketSender.requestSync();
}
```

Also update `handleDeleteQuestS2C` to use the new API:

```java
private static void handleDeleteQuestS2C(ByteBufReader r) {
    UUID questId = PacketCodec.readDeleteQuestS2C(r);
    ClientCache.removeQuestById(questId);
    ClientSession.removePinnedQuest(questId);
}
```

- [ ] **Step 6: Update DataManager for multi-pin DB schema**

In `DataManager.java`, update `createTables()` to handle migration:

```java
// In createTables(), replace the pinned_quests table creation:
stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS pinned_quests (
            player_uuid TEXT NOT NULL,
            quest_id TEXT NOT NULL,
            pinned_at INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY (player_uuid, quest_id),
            FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
        )""");
```

Add migration logic in `initialize()` after `createTables()`:

```java
migrateIfNeeded();
```

```java
private void migrateIfNeeded() throws SQLException {
    // Check if pinned_quests has old schema (player_uuid as sole PRIMARY KEY)
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery("PRAGMA table_info(pinned_quests)")) {
        boolean hasPinnedAt = false;
        while (rs.next()) {
            if ("pinned_at".equals(rs.getString("name"))) {
                hasPinnedAt = true;
                break;
            }
        }
        if (!hasPinnedAt) {
            migratePinnedQuests();
        }
    }
}

private void migratePinnedQuests() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
        stmt.executeUpdate("ALTER TABLE pinned_quests RENAME TO pinned_quests_old");
        stmt.executeUpdate("""
                CREATE TABLE pinned_quests (
                    player_uuid TEXT NOT NULL,
                    quest_id TEXT NOT NULL,
                    pinned_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, quest_id),
                    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
                )""");
        stmt.executeUpdate("""
                INSERT INTO pinned_quests (player_uuid, quest_id, pinned_at)
                SELECT player_uuid, quest_id, 0 FROM pinned_quests_old
                """);
        stmt.executeUpdate("DROP TABLE pinned_quests_old");
    }
}
```

Update pin methods:

```java
public synchronized void pinQuest(UUID playerUuid, UUID questId) {
    try (PreparedStatement stmt = connection.prepareStatement("""
            INSERT OR IGNORE INTO pinned_quests (player_uuid, quest_id, pinned_at)
            VALUES (?, ?, ?)
            """)) {
        stmt.setString(1, playerUuid.toString());
        stmt.setString(2, questId.toString());
        stmt.setLong(3, System.currentTimeMillis());
        stmt.executeUpdate();
    } catch (SQLException e) {
        throw new RuntimeException("Failed to pin quest", e);
    }
}

public synchronized void unpinQuest(UUID playerUuid, UUID questId) {
    try (PreparedStatement stmt = connection.prepareStatement(
            "DELETE FROM pinned_quests WHERE player_uuid = ? AND quest_id = ?")) {
        stmt.setString(1, playerUuid.toString());
        stmt.setString(2, questId.toString());
        stmt.executeUpdate();
    } catch (SQLException e) {
        throw new RuntimeException("Failed to unpin quest", e);
    }
}

public synchronized boolean isQuestPinned(UUID playerUuid, UUID questId) {
    try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT 1 FROM pinned_quests WHERE player_uuid = ? AND quest_id = ?")) {
        stmt.setString(1, playerUuid.toString());
        stmt.setString(2, questId.toString());
        return stmt.executeQuery().next();
    } catch (SQLException e) {
        throw new RuntimeException("Failed to check pin status", e);
    }
}

public synchronized List<UUID> getPinnedQuestIds(UUID playerUuid) {
    List<UUID> ids = new ArrayList<>();
    try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT quest_id FROM pinned_quests WHERE player_uuid = ? ORDER BY pinned_at ASC")) {
        stmt.setString(1, playerUuid.toString());
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            ids.add(UUID.fromString(rs.getString("quest_id")));
        }
    } catch (SQLException e) {
        throw new RuntimeException("Failed to get pinned quests", e);
    }
    return ids;
}
```

Remove the old `getPinnedQuestId()` and `unpinQuest(UUID playerUuid)` (no questId param) methods.

- [ ] **Step 7: Update ServerPacketHandler for toggle-pin and multi-pin handshake**

In `ServerPacketHandler.java`, update `handlePinQuest`:

```java
private void handlePinQuest(Player player, UUID questId) {
    if (questId == null) return;
    QuestData quest = dataManager.getQuest(questId);
    if (quest == null) return;
    UUID playerUuid = player.getUniqueId();
    boolean canSee = quest.ownerUuid().equals(playerUuid)
        || quest.contributors().stream().anyMatch(c -> c.uuid().equals(playerUuid))
        || quest.visibility() == Visibility.OPEN;
    if (!canSee) return;

    if (dataManager.isQuestPinned(playerUuid, questId)) {
        dataManager.unpinQuest(playerUuid, questId);
    } else {
        dataManager.pinQuest(playerUuid, questId);
    }
}
```

Update `sendHandshake`:

```java
private void sendHandshake(Player player) {
    List<UUID> pinnedIds = dataManager.getPinnedQuestIds(player.getUniqueId());
    int pendingCount = dataManager.getPendingRequestCount(player.getUniqueId());
    sendPacket(player, PacketCodec.writeHandshake(
        config.getBluemapUrl(), pendingCount, pinnedIds, player.getUniqueId()));
}
```

- [ ] **Step 8: Update QuestListWidget for multi-pin and canonical UUID**

In `QuestListWidget.java`, line 84-85, update the pin check:

```java
this.isPinned = ClientSession.isPinned(quest.getId());
```

Also at line 88, update the ownership check to use the canonical UUID:

```java
UUID playerUuid = ClientSession.getPlayerUuid();
if (playerUuid == null) playerUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
this.isOwnedByPlayer = playerUuid != null && playerUuid.equals(quest.getOwnerUuid());
```

Also update `MainScreen.refreshListContents()` -- the sort comparator at line 249-252 uses the removed `getPinnedQuestId()`. Replace:

```java
// Old:
UUID pinnedId = ClientSession.getPinnedQuestId();
quests.sort(Comparator
    .<Quest, Boolean>comparing(q -> pinnedId != null && pinnedId.equals(q.getId()), Comparator.reverseOrder())
    .thenComparing(Quest::getLastModified, Comparator.reverseOrder()));

// New:
quests.sort(Comparator
    .<Quest, Boolean>comparing(q -> ClientSession.isPinned(q.getId()), Comparator.reverseOrder())
    .thenComparing(Quest::getLastModified, Comparator.reverseOrder()));
```

- [ ] **Step 9: Update QuestScreen pin button for multi-pin**

In `QuestScreen.java`, the `togglePin()` method already uses `HudPinManager.toggle()` which now handles multi-pin. Update the pin button text in `initViewMode()`:

```java
boolean isPinned = HudPinManager.isPinned(quest.getId());
```

This is already correct from T6.

- [ ] **Step 10: Replace T1's handshake tests with multi-pin versions**

T1 added tests using the old single-UUID handshake signature. Those tests must be **deleted and replaced** (not added alongside) with these multi-pin versions:

```java
@Test
void handshakeRoundTrip_multiPin() {
    List<UUID> pinnedIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    UUID playerUuid = UUID.randomUUID();
    byte[] data = PacketCodec.writeHandshake("http://bluemap.example.com", 3, pinnedIds, playerUuid);
    ByteBufReader reader = new ByteBufReader(data);
    PacketCodec.readType(reader);
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);
    assertEquals("http://bluemap.example.com", payload.bluemapUrl());
    assertEquals(3, payload.pendingRequestCount());
    assertEquals(pinnedIds, payload.pinnedQuestIds());
    assertEquals(playerUuid, payload.playerUuid());
}

@Test
void handshakeRoundTrip_emptyPins() {
    UUID playerUuid = UUID.randomUUID();
    byte[] data = PacketCodec.writeHandshake(null, 0, List.of(), playerUuid);
    ByteBufReader reader = new ByteBufReader(data);
    PacketCodec.readType(reader);
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(reader);
    assertNull(payload.bluemapUrl());
    assertEquals(0, payload.pendingRequestCount());
    assertTrue(payload.pinnedQuestIds().isEmpty());
    assertEquals(playerUuid, payload.playerUuid());
}
```

- [ ] **Step 11: Add DataManager multi-pin tests**

In `DataManagerTest.java`, add:

```java
@Test
void multiPin_addAndList() {
    UUID questId1 = UUID.randomUUID();
    UUID questId2 = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(questId1, OWNER, "Quest 1", Visibility.PRIVATE));
    dm.saveQuest(makeQuest(questId2, OWNER, "Quest 2", Visibility.PRIVATE));

    dm.pinQuest(OWNER, questId1);
    dm.pinQuest(OWNER, questId2);

    List<UUID> pinned = dm.getPinnedQuestIds(OWNER);
    assertEquals(2, pinned.size());
    assertEquals(questId1, pinned.get(0));
    assertEquals(questId2, pinned.get(1));
}

@Test
void multiPin_toggleUnpin() {
    UUID questId = UUID.randomUUID();
    dm.upsertPlayerName(OWNER, "Alice");
    dm.saveQuest(makeQuest(questId, OWNER, "Quest", Visibility.PRIVATE));

    dm.pinQuest(OWNER, questId);
    assertTrue(dm.isQuestPinned(OWNER, questId));

    dm.unpinQuest(OWNER, questId);
    assertFalse(dm.isQuestPinned(OWNER, questId));
    assertTrue(dm.getPinnedQuestIds(OWNER).isEmpty());
}
```

- [ ] **Step 12: Run all tests**

```bash
./gradlew :common:test :paper:test
```

Expected: All tests pass.

- [ ] **Step 13: Build and verify**

```bash
./gradlew build
```

Test in-game: pin multiple quests, verify they stack on the HUD. Unpin one, verify it disappears. Reconnect, verify pins persist.

- [ ] **Step 14: Commit**

```bash
git add -A
git commit -m "feat: multi-pin quests with stacked HUD rendering and toggle semantics"
```

### Task 8: F3 — Pin button on MainScreen

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

- [ ] **Step 1: Add Pin button to bottom button row**

In `MainScreen.java`, add a `pinButton` field and rewrite the entire button row creation in `init()` to include 6 buttons. The existing code uses `UIHelper.createButtonRow(this, buttonsY, 5, x -> {...})` with an index computed from X position. Replace the entire block:

```java
// Add field at class level:
private DarkButtonWidget pinButton;

// Replace the button row creation in init():
UIHelper.createButtonRow(this, buttonsY, 6, x -> {
    int index = (x - UIHelper.getCenteredButtonStartX(this.width, 6)) / (UIHelper.BUTTON_WIDTH + UIHelper.BUTTON_SPACING);
    switch (index) {
        case 0 -> this.newQuestButton = this.addDrawableChild(new DarkButtonWidget(
                x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                Text.literal("New Quest"), b -> createNewQuest()));
        case 1 -> this.joinButton = this.addDrawableChild(new DarkButtonWidget(
                x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                Text.literal("Join"), b -> joinQuest()));
        case 2 -> this.requestAccessButton = this.addDrawableChild(new DarkButtonWidget(
                x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                Text.literal("Request"), b -> requestAccess()));
        case 3 -> this.pinButton = this.addDrawableChild(new DarkButtonWidget(
                x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                Text.literal("Pin"), b -> togglePinSelected()));
        case 4 -> this.openButton = this.addDrawableChild(new DarkButtonWidget(
                x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                Text.literal("Open"), b -> openSelected()));
        case 5 -> this.closeButton = this.addDrawableChild(new DarkButtonWidget(
                x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                Text.literal("Close"), b -> this.client.setScreen(null)));
    }
});
```

- [ ] **Step 2: Add togglePinSelected method**

```java
private void togglePinSelected() {
    Quest sel = currentTab == TAB_MY_QUESTS
            ? myQuestListWidget.getSelectedQuest()
            : serverQuestListWidget.getSelectedQuest();
    if (sel != null) {
        HudPinManager.toggle(sel.getId());
        refreshListContents(); // refresh to update pin indicators
        updateActionButtons();
    }
}
```

- [ ] **Step 3: Update updateActionButtons to handle pin button text and state**

```java
// In updateActionButtons(), add pin button logic:
Quest pinSel = currentTab == TAB_MY_QUESTS
        ? myQuestListWidget.getSelectedQuest()
        : serverQuestListWidget.getSelectedQuest();
pinButton.active = pinSel != null;
if (pinSel != null && HudPinManager.isPinned(pinSel.getId())) {
    pinButton.setMessage(Text.literal("Unpin"));
} else {
    pinButton.setMessage(Text.literal("Pin"));
}
```

- [ ] **Step 4: Make pin button visible on both tabs**

The pin button should always be visible. Ensure it's not hidden in `selectTab()`.

- [ ] **Step 5: Build and verify**

```bash
./gradlew :client:build
```

Test: select a quest, click Pin, verify HUD updates. Select pinned quest, verify button says "Unpin".

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat: add Pin/Unpin button to MainScreen quest list"
```

---

## Chunk 4: Remaining Features (T9-T13)

### Task 9: F9 + F10 — Rename Server tab to Quest Board + date format change

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java:93-96`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java:24`

- [ ] **Step 1: Rename Server tab**

In `MainScreen.java`, change line 96:

```java
Text.literal("Quest Board"), b -> selectTab(TAB_SERVER_QUESTS)
```

Optionally widen the tab. Check if `ScreenLayouts.TAB_WIDTH` (80) fits "Quest Board" with the current font. If not, use a wider value for the server tab only:

```java
int questBoardTabWidth = Math.max(ScreenLayouts.TAB_WIDTH,
        MinecraftClient.getInstance().textRenderer.getWidth("Quest Board") + 12);
this.serverQuestsTab = this.addDrawableChild(new TabButtonWidget(
        (this.width / 2) + 2, tabY,
        questBoardTabWidth, ScreenLayouts.TAB_HEIGHT,
        Text.literal("Quest Board"), b -> selectTab(TAB_SERVER_QUESTS)
));
```

- [ ] **Step 2: Change date format**

In `QuestListWidget.java`, line 24:

```java
private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd MM yyyy");
```

- [ ] **Step 3: Build and verify**

```bash
./gradlew :client:build
```

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java \
        client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java
git commit -m "feat: rename Server tab to Quest Board and change date format to HH:mm dd MM yyyy"
```

### Task 10: F7 — Configurable pinned quest width

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfig.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java`

- [ ] **Step 1: Create DisquestsConfig**

```java
package com.disqt.disquests.client.gui.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DisquestsConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Disquests");
    private static final Path CONFIG_PATH = Path.of(
            System.getProperty("user.home"), ".fabric", "config", "disquests", "config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int pinnedWidth = 200;

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save(); // create default
            return;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            ConfigData data = GSON.fromJson(json, ConfigData.class);
            if (data != null) {
                pinnedWidth = Math.max(100, Math.min(400, data.pinnedWidth));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load disquests config, using defaults", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            ConfigData data = new ConfigData();
            data.pinnedWidth = pinnedWidth;
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.warn("Failed to save disquests config", e);
        }
    }

    public static int getPinnedWidth() { return pinnedWidth; }

    private static class ConfigData {
        int pinnedWidth = 200;
    }
}
```

- [ ] **Step 2: Load config on mod init**

In `DisquestsClient.java` (the Fabric mod entrypoint), add in `onInitializeClient()`:

```java
DisquestsConfig.load();
```

- [ ] **Step 3: Use config in HudPinRenderer**

In `HudPinRenderer.java`, replace `MAX_WIDTH` usage:

```java
private static int getMaxWidth() {
    return DisquestsConfig.getPinnedWidth();
}
```

Replace all references to `MAX_WIDTH` with `getMaxWidth()`.

- [ ] **Step 4: Build and verify**

```bash
./gradlew :client:build
```

Test: change `pinnedWidth` in `~/.fabric/config/disquests/config.json` and verify pinned HUD width changes.

- [ ] **Step 5: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfig.java \
        client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java \
        client/src/main/java/com/disqt/disquests/client/DisquestsClient.java
git commit -m "feat: add configurable pinned quest width via config.json"
```

### Task 11: F8 — Rainbow Disquests title on hover

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

- [ ] **Step 1: Add rainbow title rendering in MainScreen.render()**

The MainScreen currently uses `Text.literal("Disquests")` as its title (via `super(Text.literal("Disquests"), null)` in the constructor). Override title rendering in `render()`. Add a tick counter field:

```java
private int tickCounter = 0;

@Override
public void tick() {
    super.tick();
    tickCounter++;
}
```

In `render()`, replace the default title draw with custom rainbow rendering:

```java
// Rainbow title
String titleStr = "Disquests";
int titleWidth = this.textRenderer.getWidth(titleStr);
int titleX = (this.width - titleWidth) / 2;
int titleY = (ScreenLayouts.TOP_MARGIN - this.textRenderer.fontHeight) / 2;

boolean hovering = mouseX >= titleX && mouseX <= titleX + titleWidth
        && mouseY >= titleY && mouseY <= titleY + this.textRenderer.fontHeight;

if (hovering) {
    // Render each character with rainbow colors
    int charX = titleX;
    for (int i = 0; i < titleStr.length(); i++) {
        float hue = ((tickCounter * 3 + i * 30) % 360) / 360.0f;
        int color = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f) | 0xFF000000;
        String ch = String.valueOf(titleStr.charAt(i));
        context.drawTextWithShadow(this.textRenderer, ch, charX, titleY, color);
        charX += this.textRenderer.getWidth(ch);
    }
} else {
    context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, titleY, Colors.TEXT_PRIMARY);
}
```

Note: `java.awt.Color` may not be available in the MC runtime. If not, compute HSB manually:

```java
private static int hsbToRgb(float hue, float saturation, float brightness) {
    int r, g, b;
    float h = (hue - (float) Math.floor(hue)) * 6.0f;
    float f = h - (float) Math.floor(h);
    float p = brightness * (1.0f - saturation);
    float q = brightness * (1.0f - saturation * f);
    float t = brightness * (1.0f - (saturation * (1.0f - f)));
    switch ((int) h) {
        case 0 -> { r = (int)(brightness * 255); g = (int)(t * 255); b = (int)(p * 255); }
        case 1 -> { r = (int)(q * 255); g = (int)(brightness * 255); b = (int)(p * 255); }
        case 2 -> { r = (int)(p * 255); g = (int)(brightness * 255); b = (int)(t * 255); }
        case 3 -> { r = (int)(p * 255); g = (int)(q * 255); b = (int)(brightness * 255); }
        case 4 -> { r = (int)(t * 255); g = (int)(p * 255); b = (int)(brightness * 255); }
        default -> { r = (int)(brightness * 255); g = (int)(p * 255); b = (int)(q * 255); }
    };
    return 0xFF000000 | (r << 16) | (g << 8) | b;
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew :client:build
```

Test: hover over "Disquests" title, verify rainbow animation. Move mouse away, verify normal rendering.

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat: rainbow color animation on Disquests title hover"
```

### Task 12: F6 — Tooltips + F4 — Editable coordinates + F5 — Formatting help panel

These three features all modify `QuestScreen.java` and `MainScreen.java`. They can be done as separate steps within one task to minimize merge conflicts.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

- [ ] **Step 1: Add tooltips (F6)**

In `QuestScreen.java`, after creating buttons, add tooltips using MC's `Tooltip` API:

```java
import net.minecraft.client.gui.tooltip.Tooltip;

// In initViewMode(), after creating editButton:
this.editButton.setTooltip(Tooltip.of(Text.literal("Edit this quest")));
this.deleteButton.setTooltip(Tooltip.of(Text.literal("Permanently delete this quest")));
this.pinButton.setTooltip(Tooltip.of(Text.literal("Pin/unpin this quest to your HUD")));

// In initEditMode(), for visibility button:
// After creating the visibility cycle button, add:
visButton.setTooltip(Tooltip.of(Text.literal(switch (quest.getVisibility()) {
    case PRIVATE -> "Only you can see this quest";
    case CLOSED -> "Visible to all, join by request";
    case OPEN -> "Visible to all, anyone can join";
})));

// For region toggle button:
regionButton.setTooltip(Tooltip.of(Text.literal("Define a rectangular area with two corners")));

// For contributors button:
contribButton.setTooltip(Tooltip.of(Text.literal("Manage who can view/edit this quest")));
```

In `MainScreen.java`, add tooltips to filter buttons:

```java
this.filterAllButton.setTooltip(Tooltip.of(Text.literal("Show all visible quests")));
this.filterOpenButton.setTooltip(Tooltip.of(Text.literal("Quests anyone can join")));
this.filterClosedButton.setTooltip(Tooltip.of(Text.literal("Quests that require access request")));
```

Note: Store button references as fields where needed (e.g., the visibility button in `buildSettingsRow`).

- [ ] **Step 2: Build and verify tooltips**

```bash
./gradlew :client:build
```

Test: hover over buttons, verify tooltips appear.

- [ ] **Step 3: Commit tooltips**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java \
        client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat: add descriptive tooltips to UI buttons"
```

- [ ] **Step 4: Add editable coordinate fields (F4)**

In `QuestScreen.java`, modify `buildCoordsRow()` to include three small text fields for X, Y, Z. Replace the static label with editable fields:

```java
// Add fields to QuestScreen class:
private MultiLineTextFieldWidget coordXField;
private MultiLineTextFieldWidget coordYField;
private MultiLineTextFieldWidget coordZField;
```

In `buildCoordsRow()`:

```java
private void buildCoordsRow(int panelX, int rowY, int panelWidth) {
    int labelWidth = this.textRenderer.getWidth("Coords: ");
    int fieldWidth = 50;
    int fieldHeight = 14;
    int spacing = 4;

    // X field
    int fieldX = panelX + labelWidth;
    CoordinatesData c = quest.getCoordinates();
    String xVal = c != null ? String.format("%.0f", c.x()) : "";
    String yVal = c != null ? String.format("%.0f", c.y()) : "";
    String zVal = c != null ? String.format("%.0f", c.z()) : "";

    this.coordXField = new MultiLineTextFieldWidget(
            this.textRenderer, fieldX, rowY, fieldWidth, fieldHeight, xVal, "X", 1, false);
    this.addSelectableChild(this.coordXField);

    this.coordYField = new MultiLineTextFieldWidget(
            this.textRenderer, fieldX + fieldWidth + spacing, rowY, fieldWidth, fieldHeight, yVal, "Y", 1, false);
    this.addSelectableChild(this.coordYField);

    this.coordZField = new MultiLineTextFieldWidget(
            this.textRenderer, fieldX + (fieldWidth + spacing) * 2, rowY, fieldWidth, fieldHeight, zVal, "Z", 1, false);
    this.addSelectableChild(this.coordZField);

    // Buttons after fields
    int btnX = fieldX + (fieldWidth + spacing) * 3;
    int btnWidth = 50;

    this.addDrawableChild(new DarkButtonWidget(
            btnX, rowY, btnWidth, fieldHeight,
            Text.literal("Set Pos"), b -> setPlayerPosition()));

    String regionText = regionEnabled ? "[x] Region" : "[ ] Region";
    int regionBtnWidth = this.textRenderer.getWidth(regionText) + UIHelper.BUTTON_TEXT_PADDING;
    this.addDrawableChild(new DarkButtonWidget(
            btnX + btnWidth + spacing, rowY, regionBtnWidth, fieldHeight,
            Text.literal(regionText), b -> toggleRegion()));

    this.addDrawableChild(new DarkButtonWidget(
            btnX + btnWidth + spacing + regionBtnWidth + spacing, rowY, btnWidth, fieldHeight,
            Text.literal("Clear"), b -> clearCoords()));
}
```

Update `persistFieldValues()` to read coord fields:

```java
private void persistFieldValues() {
    if (this.titleField != null) quest.setTitle(this.titleField.getText());
    if (this.contentField != null) quest.setContent(this.contentField.getText());
    // Parse coordinate fields
    if (this.coordXField != null) {
        try {
            double x = Double.parseDouble(coordXField.getText().trim());
            double y = Double.parseDouble(coordYField.getText().trim());
            double z = Double.parseDouble(coordZField.getText().trim());
            quest.setCoordinates(new CoordinatesData(x, y, z));
        } catch (NumberFormatException e) {
            if (coordXField.getText().trim().isEmpty()
                    && coordYField.getText().trim().isEmpty()
                    && coordZField.getText().trim().isEmpty()) {
                quest.setCoordinates(null);
            }
            // else: leave coordinates unchanged if fields are invalid
        }
    }
}
```

Update `setPlayerPosition()` to populate fields instead of just setting quest data:

```java
private void setPlayerPosition() {
    if (client != null && client.player != null) {
        quest.setCoordinates(new CoordinatesData(
                client.player.getX(), client.player.getY(), client.player.getZ()));
        if (quest.getMap() == null && client.world != null) {
            quest.setMap(client.world.getRegistryKey().getValue().getPath());
        }
        persistFieldValues();
        this.clearAndInit(); // reinit to populate fields with new values
    }
}
```

Also render the coord fields in `renderEditMode()`:

```java
// Replace the static coords label rendering with field rendering:
if (coordXField != null) {
    context.drawText(this.textRenderer, "Coords: ", contentX + 5, rowY + 3, Colors.TEXT_MUTED, false);
    coordXField.render(context, mouseX, mouseY, delta);
    coordYField.render(context, mouseX, mouseY, delta);
    coordZField.render(context, mouseX, mouseY, delta);
}
```

- [ ] **Step 5: Build and verify editable coords**

```bash
./gradlew :client:build
```

Test: type coordinates manually, click Set Pos to auto-fill, verify save persists manual values.

- [ ] **Step 6: Commit editable coords**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git commit -m "feat: editable X/Y/Z coordinate text fields in edit mode"
```

- [ ] **Step 7: Add formatting help side panel (F5)**

Add a toggle state and rendering to `QuestScreen.java`:

```java
// Add field:
private boolean showFormattingHelp = false;
private static final int FORMATTING_PANEL_WIDTH = 120;
```

In `initEditMode()`, add a "?" button in the top-right of the content area:

```java
// After creating contentField, add "?" button:
int helpBtnSize = 14;
this.addDrawableChild(new DarkButtonWidget(
        contentX + contentWidth - helpBtnSize - 2,
        contentPanelY + 2,
        helpBtnSize, helpBtnSize,
        Text.literal("?"), b -> {
            persistFieldValues();
            showFormattingHelp = !showFormattingHelp;
            this.clearAndInit();
        }
));
```

When `showFormattingHelp` is true, shrink the content field width and draw the panel:

```java
// In initEditMode(), adjust content width:
int editorWidth = contentWidth;
if (showFormattingHelp) {
    editorWidth -= FORMATTING_PANEL_WIDTH + ScreenLayouts.PANEL_SPACING;
}
// Use editorWidth instead of contentWidth for contentField creation
```

In `renderEditMode()`, draw the formatting panel:

```java
if (showFormattingHelp) {
    int panelX = contentX + contentWidth - FORMATTING_PANEL_WIDTH;
    int panelHeight = contentPanelHeight;
    UIHelper.drawPanel(context, panelX, contentPanelY, FORMATTING_PANEL_WIDTH, panelHeight);

    int textX = panelX + 5;
    int textY = contentPanelY + 5;
    int lineH = this.textRenderer.fontHeight + 2;

    context.drawText(this.textRenderer, "Formatting", textX, textY, Colors.TEXT_PRIMARY, false);
    textY += lineH + 2;

    String[][] help = {
        {"**text**", "bold"},
        {"*text*", "italic"},
        {"~~text~~", "strikethrough"},
        {"# Heading", "heading"},
        {"- [ ] task", "checkbox"},
        {"- [x] done", "checked"},
        {"> text", "quote"},
        {"[text](url)", "link"},
    };
    for (String[] entry : help) {
        context.drawText(this.textRenderer, entry[0], textX, textY, Colors.TEXT_MUTED, false);
        textY += lineH;
    }
}
```

- [ ] **Step 8: Build and verify formatting help**

```bash
./gradlew :client:build
```

Test: click "?" in edit mode, verify side panel appears with formatting reference. Click again to hide.

- [ ] **Step 9: Commit formatting help**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git commit -m "feat: toggleable formatting help side panel in edit mode"
```

### Task 13: E2E Test Skeleton

**Files:**
- Create: `client/src/testmod/resources/fabric.mod.json`
- Create: `client/src/testmod/java/com/disqt/disquests/test/DisquestsE2ETest.java`

- [ ] **Step 1: Create testmod fabric.mod.json**

```json
{
  "schemaVersion": 1,
  "id": "disquests-test",
  "version": "1.0.0",
  "name": "Disquests E2E Tests",
  "environment": "client",
  "entrypoints": {
    "fabric-client-gametest": [
      "com.disqt.disquests.test.DisquestsE2ETest"
    ]
  },
  "depends": {
    "disquests": "*",
    "fabric-client-gametest-api-v1": "*"
  }
}
```

- [ ] **Step 2: Look up the Fabric Client GameTest API for 1.21.11**

Before writing the test, check the actual API signatures. The Fabric Client GameTest API changed across versions. Use context7 or the Fabric API source to find:
- The correct interface to implement (`FabricClientGameTest`)
- The method signature for the test entry point (likely `void runTest(TestClientWorldContext context)`)
- How to connect to an external server
- How to wait for conditions
- How to interact with screens

```bash
# Check Fabric API source for the gametest interface
find ~/.gradle/caches -path "*/fabric-client-gametest-api*" -name "*.jar" 2>/dev/null | head -3
```

- [ ] **Step 3: Create E2E test entry point**

Based on the API research from Step 2, create the test class. The skeleton should:
1. Connect to the external Paper server using the system properties from `build.gradle.kts`
2. Wait for the Disquests handshake (`ClientSession.isOnServer()`)
3. Open MainScreen and verify it loads
4. Take a screenshot for CI artifacts

```java
package com.disqt.disquests.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
// Import the correct context type based on Step 2 research

public class DisquestsE2ETest implements FabricClientGameTest {

    // Implement the correct method signature from Step 2.
    // The test should:
    // 1. Connect to server at disquests.test.server.host:disquests.test.server.port
    // 2. Wait for ClientSession.isOnServer() to become true
    // 3. Open MainScreen via client.setScreen(new MainScreen())
    // 4. Verify screen is shown
    // 5. Take screenshot
    // 6. Close screen

    // TODO: Add CRUD tests, multi-pin tests, view/edit toggle tests
}
```

**Important:** The exact API must be verified in Step 2. Do not guess method signatures -- the Fabric Client GameTest API varies between MC versions and the test won't compile with wrong signatures.

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :client:classes -x test
```

Expected: Compiles with the testmod source set.

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/
git commit -m "test: add E2E test skeleton with server connection and MainScreen smoke test"
```

---

## Execution Handoff

All chunks are complete. Implementation order:

1. **Chunk 1** (T1-T5): Bug fixes — independent, do first
2. **Chunk 2** (T6): QuestScreen merge — depends on T4 being done (delete fix moves into QuestScreen)
3. **Chunk 3** (T7-T8): Multi-pin + MainScreen pin button — modifies handshake from T1
4. **Chunk 4** (T9-T13): Remaining features + E2E tests — independent, do last

Run `./gradlew build` and `./gradlew :common:test :paper:test` after each chunk to catch regressions early.
