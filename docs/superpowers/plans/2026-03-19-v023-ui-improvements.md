# v0.2.3 UI Improvements Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 11 UI/UX issues from v0.2.2 playtesting with full TDD coverage.

**Architecture:** All changes are client-side (Fabric mod). Most issues are independent. Tests use FabricClientGameTest with `runOnClient`/`computeOnClient` for screen interaction (see `docs/references/fabric-client-gametest.md`). TestInput.pressMouse does NOT work reliably on Xvfb -- use direct method calls instead.

**Tech Stack:** Java 21, Fabric 1.21.11, FabricClientGameTest

**Branch:** `fix/v023-ui-improvements` off `main`

**Spec:** `docs/superpowers/specs/2026-03-19-v023-ui-improvements-design.md`

**IMPORTANT REMINDERS:**
- TDD: Write failing test first, then implement
- Run `./gradlew :client:runClientGameTest` locally before PR (DisquestsE2ETest will fail without server -- that's OK, QuestScreenTest must pass)
- Run `/simplify` before PR
- Run `./gradlew clean build` to verify all unit tests pass

---

## Chunk 1: Quick Fixes (Issues 1, 3)

### Task 1: Create Branch

- [ ] **Step 1: Create feature branch**
```bash
git checkout -b fix/v023-ui-improvements main
```

- [ ] **Step 2: Verify clean build**
```bash
./gradlew build
```

---

### Task 2: Deferred Pin Re-sort (Issue 1)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java:305-308`
- Test: `client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java`

- [ ] **Step 1: Write failing test**

Add to `QuestScreenTest.java`:

```java
/**
 * Issue 1: Pin toggle should NOT re-sort the list immediately.
 * Verify list order is unchanged after toggling pin.
 */
private void testPinDoesNotResort(ClientGameTestContext context) {
    // Create two quests -- quest2 is newer
    Quest quest1 = createTestQuest();
    quest1.setTitle("Quest 1");
    quest1.setLastModified(1000);
    Quest quest2 = createTestQuest();
    quest2.setTitle("Quest 2");
    quest2.setLastModified(2000);

    ClientCache.addOrUpdateMyQuest(quest1);
    ClientCache.addOrUpdateMyQuest(quest2);

    context.setScreen(MainScreen::new);
    context.waitForScreen(MainScreen.class);
    context.waitTick();

    // Quest 2 should be first (newer). Get first quest title.
    String firstBefore = context.computeOnClient(client -> {
        MainScreen screen = (MainScreen) client.currentScreen;
        // First quest in My Quests list
        return screen.getMyQuestListFirstTitle();
    });

    // Pin quest1 (the older one) -- should NOT jump to top immediately
    context.runOnClient(client -> {
        HudPinManager.toggle(quest1.getId());
        MainScreen screen = (MainScreen) client.currentScreen;
        screen.refreshAfterPinToggle();
    });
    context.waitTick();

    String firstAfter = context.computeOnClient(client -> {
        MainScreen screen = (MainScreen) client.currentScreen;
        return screen.getMyQuestListFirstTitle();
    });

    // Order should be unchanged -- quest2 still first
    if (!firstBefore.equals(firstAfter)) {
        throw new AssertionError("Pin toggle should not re-sort. Before: " + firstBefore + " After: " + firstAfter);
    }

    // Cleanup
    ClientCache.removeQuestById(quest1.getId());
    ClientCache.removeQuestById(quest2.getId());
    HudPinManager.toggle(quest1.getId()); // unpin
    context.setScreen(() -> null);
    context.waitTick();
}
```

Add a call in `runTest()`:
```java
testPinDoesNotResort(context);
```

Add required imports for `HudPinManager`, `ClientCache`, `MainScreen`.

Add test helper to `MainScreen`:
```java
public String getMyQuestListFirstTitle() {
    if (myQuestListWidget.children().isEmpty()) return "";
    return myQuestListWidget.children().get(0).getQuest().getTitle();
}
```

- [ ] **Step 2: Compile and verify test exists**
```bash
./gradlew :client:compileTestmodJava
```

- [ ] **Step 3: Implement fix**

In `MainScreen.java`, change `refreshAfterPinToggle()` (line 305-308):

```java
public void refreshAfterPinToggle() {
    // Only repaint -- don't re-sort. Pin icon updates automatically
    // since QuestEntry.render() reads isPinned live.
    // Full re-sort happens on screen open (init) and tab switch.
    updateActionButtons();
}
```

- [ ] **Step 4: Build and run tests locally**
```bash
./gradlew :client:build
./gradlew :client:runClientGameTest
```
QuestScreenTest must pass.

- [ ] **Step 5: Commit**
```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git add client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java
git commit -m "fix: defer pin re-sort to screen open and tab switch"
```

---

### Task 3: Remove Click-to-Edit (Issue 3)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java:75-78, 989-996`
- Test: `client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java`

- [ ] **Step 1: Write failing test**

```java
/**
 * Issue 3: Clicking content area should NOT enter edit mode.
 * Only the Edit button should trigger edit mode.
 */
private void testClickContentDoesNotEdit(ClientGameTestContext context) {
    Quest quest = createTestQuest();

    context.setScreen(() -> new QuestScreen(null, quest));
    context.waitForScreen(QuestScreen.class);
    context.waitTick();

    // Simulate click in the content area
    context.runOnClient(client -> {
        QuestScreen screen = (QuestScreen) client.currentScreen;
        // Click center of screen -- should be in content area
        screen.mouseClicked(
            new net.minecraft.client.gui.Click(screen.width / 2.0, screen.height / 2.0, 0),
            false
        );
    });
    context.waitTick();

    // Should still be in view mode (not editing)
    boolean isEditing = context.computeOnClient(client -> {
        QuestScreen screen = (QuestScreen) client.currentScreen;
        return screen.isEditing();
    });

    if (isEditing) {
        throw new AssertionError("Clicking content area should not enter edit mode");
    }

    context.setScreen(() -> null);
    context.waitTick();
}
```

Note: Need to add `public boolean isEditing() { return editing; }` accessor to `QuestScreen`.

- [ ] **Step 2: Compile test**
```bash
./gradlew :client:compileTestmodJava
```

- [ ] **Step 3: Implement fix**

In `QuestScreen.java`:

1. Remove the `contentAreaX/Y/Width/Height` fields (lines 75-78)
2. Remove the `contentAreaX/Y/Width/Height` assignments in `initViewMode()` (lines 206-210)
3. Remove the click-to-edit block in `mouseClicked()` (lines 989-996):
```java
// DELETE THIS BLOCK:
// Click-to-edit for content area (only if not a checkbox click)
if (!editing && canEdit) {
    double mx = click.x();
    double my = click.y();
    if (mx >= contentAreaX && mx < contentAreaX + contentAreaWidth
            && my >= contentAreaY && my < contentAreaY + contentAreaHeight) {
        enterEditMode();
        return true;
    }
}
```

4. Add `isEditing()` accessor:
```java
public boolean isEditing() { return editing; }
```

- [ ] **Step 4: Build and run tests**
```bash
./gradlew :client:build
./gradlew :client:runClientGameTest
```

- [ ] **Step 5: Commit**
```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git add client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java
git commit -m "fix: remove click-to-edit, keep Edit button only"
```

---

## Chunk 2: Toast + Formatting (Issues 2, 4)

### Task 4: In-Screen Toast Overlay (Issue 2)

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/widget/ToastOverlay.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/ClientSession.java`

- [ ] **Step 1: Create ToastOverlay class**

```java
package com.disqt.disquests.client.gui.widget;

import com.disqt.disquests.client.gui.helper.Colors;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class ToastOverlay {
    private String message;
    private int ticksRemaining;
    private static final int DURATION_TICKS = 60; // 3 seconds
    private static final int FADE_TICKS = 20;     // last 1 second fades
    private static final int BG_COLOR = 0xCC222222;
    private static final int PADDING_X = 12;
    private static final int PADDING_Y = 6;

    public void show(String message) {
        this.message = message;
        this.ticksRemaining = DURATION_TICKS;
    }

    public void tick() {
        if (ticksRemaining > 0) ticksRemaining--;
    }

    public boolean isVisible() {
        return ticksRemaining > 0 && message != null;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight, int bottomY) {
        if (!isVisible()) return;

        float alpha = ticksRemaining <= FADE_TICKS
                ? (float) ticksRemaining / FADE_TICKS
                : 1.0f;
        int alphaInt = (int) (alpha * 255) << 24;

        int textWidth = textRenderer.getWidth(message);
        int boxWidth = textWidth + PADDING_X * 2;
        int boxHeight = textRenderer.fontHeight + PADDING_Y * 2;
        int x = (screenWidth - boxWidth) / 2;
        int y = bottomY - boxHeight - 4;

        int bg = (alphaInt & 0xFF000000) | (BG_COLOR & 0x00FFFFFF);
        context.fill(x, y, x + boxWidth, y + boxHeight, bg);

        int textColor = (alphaInt & 0xFF000000) | (Colors.TEXT_PRIMARY & 0x00FFFFFF);
        context.drawText(textRenderer, message, x + PADDING_X, y + PADDING_Y, textColor, false);
    }
}
```

- [ ] **Step 2: Add toast to MainScreen**

Add field:
```java
private final ToastOverlay toast = new ToastOverlay();
```

In `tick()` method, add:
```java
toast.tick();
```

In `render()` method, after all other rendering, add:
```java
// Toast overlay (renders on top of everything)
int buttonsY = UIHelper.getBottomButtonY(this);
toast.render(context, this.textRenderer, this.width, this.height, buttonsY);
```

Add public method:
```java
public void showToast(String message) {
    toast.show(message);
}
```

- [ ] **Step 3: Add pending toast to ClientSession**

```java
private static String pendingToast = null;

public static void setPendingToast(String message) {
    pendingToast = message;
}

public static String consumePendingToast() {
    String msg = pendingToast;
    pendingToast = null;
    return msg;
}
```

Clear in `leaveServer()`:
```java
pendingToast = null;
```

- [ ] **Step 4: Consume pending toast in MainScreen**

In `MainScreen.tick()`:
```java
String pending = ClientSession.consumePendingToast();
if (pending != null) {
    toast.show(pending);
}
```

- [ ] **Step 5: Replace setOverlayMessage calls**

In `MainScreen.requestAccess()`, replace:
```java
MinecraftClient.getInstance().inGameHud.setOverlayMessage(
        Text.literal("Request sent to " + sel.getOwnerName()), false);
```
With:
```java
showToast("Request sent to " + sel.getOwnerName());
```

In `ClientPacketHandler.handleCollaborationResponse()`, replace:
```java
MinecraftClient client = MinecraftClient.getInstance();
client.inGameHud.setOverlayMessage(
        Text.literal("Joined \"" + quest.getTitle() + "\" \u2014 see My Quests"), false);
if (client.currentScreen instanceof MainScreen mainScreen) {
    mainScreen.refreshListContents();
}
```
With:
```java
MinecraftClient client = MinecraftClient.getInstance();
if (client.currentScreen instanceof MainScreen mainScreen) {
    mainScreen.refreshListContents();
    mainScreen.showToast("Joined \"" + quest.getTitle() + "\" \u2014 see My Quests");
} else {
    ClientSession.setPendingToast("Joined \"" + quest.getTitle() + "\" \u2014 see My Quests");
}
```

- [ ] **Step 6: Build and test**
```bash
./gradlew :client:build
./gradlew :client:runClientGameTest
```

- [ ] **Step 7: Commit**
```bash
git add client/src/main/java/com/disqt/disquests/client/gui/widget/ToastOverlay.java
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git add client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java
git add client/src/main/java/com/disqt/disquests/client/ClientSession.java
git commit -m "feat: replace actionbar overlay with in-screen toast notifications"
```

---

### Task 5: Formatting Help Rendered Preview (Issue 4)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java:63-64, 456-481`
- Test: `client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java`

- [ ] **Step 1: Update test for default-open formatting panel**

Modify `testHelpButtonToggle` -- the panel is now open by default, so toggling should EXPAND the content field (not shrink it):

```java
private void testHelpButtonToggle(ClientGameTestContext context) {
    Quest quest = createTestQuest();

    context.setScreen(() -> new QuestScreen(null, quest, true));
    context.waitForScreen(QuestScreen.class);
    context.waitTick();

    // Formatting panel is open by default -- content field should be narrower
    int widthWithPanel = context.computeOnClient(client -> {
        QuestScreen screen = (QuestScreen) client.currentScreen;
        return screen.getContentField().width;
    });

    // Toggle OFF -- content field should expand
    context.runOnClient(client -> {
        QuestScreen screen = (QuestScreen) client.currentScreen;
        screen.toggleFormattingHelp();
    });
    context.waitTicks(2);

    int widthWithoutPanel = context.computeOnClient(client -> {
        QuestScreen screen = (QuestScreen) client.currentScreen;
        return screen.getContentField().width;
    });

    if (widthWithoutPanel <= widthWithPanel) {
        throw new AssertionError(
                "Toggling formatting panel off should expand content field. " +
                "WithPanel=" + widthWithPanel + " WithoutPanel=" + widthWithoutPanel);
    }

    context.setScreen(() -> null);
    context.waitTick();
}
```

- [ ] **Step 2: Change default and increase panel width**

In `QuestScreen.java`:
- Line 63: Change `private boolean showFormattingHelp = false;` to `private boolean showFormattingHelp = true;`
- Line 64: Change `private static final int FORMATTING_PANEL_WIDTH = 120;` to `private static final int FORMATTING_PANEL_WIDTH = 160;`

- [ ] **Step 3: Replace formatting help rendering**

Replace the formatting help panel rendering in `renderEditMode()` (lines 456-481) with a two-column layout showing raw syntax and rendered result:

```java
// Formatting help side panel
if (showFormattingHelp) {
    int panelX = contentX + contentWidth - FORMATTING_PANEL_WIDTH;
    UIHelper.drawPanel(context, panelX, contentPanelY, FORMATTING_PANEL_WIDTH, contentPanelHeight);

    int textX = panelX + 5;
    int textY = contentPanelY + 5;
    int lineH = this.textRenderer.fontHeight + 2;

    context.drawText(this.textRenderer, "Formatting", textX, textY, Colors.TEXT_PRIMARY, false);
    textY += lineH + 4;

    // Two-column: syntax (muted) | rendered preview
    int colSplit = panelX + 80; // split point

    // Bold
    context.drawText(this.textRenderer, "**text**", textX, textY, Colors.TEXT_MUTED, false);
    context.drawText(this.textRenderer, Text.literal("text").formatted(Formatting.BOLD),
            colSplit, textY, Colors.TEXT_PRIMARY, false);
    textY += lineH;

    // Italic
    context.drawText(this.textRenderer, "*text*", textX, textY, Colors.TEXT_MUTED, false);
    context.drawText(this.textRenderer, Text.literal("text").formatted(Formatting.ITALIC),
            colSplit, textY, Colors.TEXT_PRIMARY, false);
    textY += lineH;

    // Strikethrough
    context.drawText(this.textRenderer, "~~text~~", textX, textY, Colors.TEXT_MUTED, false);
    context.drawText(this.textRenderer, Text.literal("text").formatted(Formatting.STRIKETHROUGH),
            colSplit, textY, Colors.TEXT_PRIMARY, false);
    textY += lineH;

    // Heading
    context.drawText(this.textRenderer, "# Heading", textX, textY, Colors.TEXT_MUTED, false);
    context.drawText(this.textRenderer, Text.literal("Heading").formatted(Formatting.BOLD),
            colSplit, textY, 0xFFFFFF55, false);
    textY += lineH;

    // Checkbox unchecked
    context.drawText(this.textRenderer, "- [ ] task", textX, textY, Colors.TEXT_MUTED, false);
    context.drawText(this.textRenderer, "\u2610 task", colSplit, textY, Colors.TEXT_PRIMARY, false);
    textY += lineH;

    // Checkbox checked
    context.drawText(this.textRenderer, "- [x] done", textX, textY, Colors.TEXT_MUTED, false);
    context.drawText(this.textRenderer, "\u2611 done", colSplit, textY, 0xFF88FF88, false);
    textY += lineH;

    // Quote
    context.drawText(this.textRenderer, "> text", textX, textY, Colors.TEXT_MUTED, false);
    context.drawText(this.textRenderer, "\u2502 text", colSplit, textY, 0xFFAAAAFF, false);
    textY += lineH;

    // Link
    context.drawText(this.textRenderer, "[a](url)", textX, textY, Colors.TEXT_MUTED, false);
    context.drawText(this.textRenderer, Text.literal("a").formatted(Formatting.UNDERLINE),
            colSplit, textY, 0xFF5599FF, false);
}
```

Note: Ensure `Formatting` is imported (`import net.minecraft.util.Formatting;`). Check that the checkbox Unicode chars render in MC's font -- if not, use `[ ]` and `[x]` as fallbacks.

- [ ] **Step 4: Build and run tests**
```bash
./gradlew :client:build
./gradlew :client:runClientGameTest
```

- [ ] **Step 5: Commit**
```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git add client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java
git commit -m "feat: show rendered formatting preview, open by default"
```

---

## Chunk 3: Quest List + View Mode (Issues 6, 7, 8)

### Task 6: "Requested" Status in Quest List (Issue 6)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java:182-192`

- [ ] **Step 1: Add "Requested" text to row 3**

In `QuestEntry.render()`, after the location string rendering (around line 192), add:

```java
// "Requested" indicator (right-aligned on row 3, after location)
if (ClientSession.isRequested(quest.getId())) {
    String requestedText = "Requested";
    int requestedWidth = client.textRenderer.getWidth(requestedText);
    int requestedX = entryX + entryWidth - requestedWidth - 4;
    context.drawText(client.textRenderer, requestedText,
            requestedX, entryY + 24, 0xFFCCCC44, false);
}
```

Add import for `ClientSession` if not already present.

- [ ] **Step 2: Build and commit**
```bash
./gradlew :client:build
git add client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java
git commit -m "feat: show 'Requested' status on row 3 of quest list entries"
```

---

### Task 7: Hide Closed Quest Content (Issue 7)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`

- [ ] **Step 1: Hide content preview in quest list for closed quests**

In `QuestListWidget.QuestEntry`, the constructor or render method needs to check if this is a closed quest being shown on the Quest Board. Add a field to `QuestEntry`:

```java
private final boolean isServerQuest;
```

Set it from the constructor or from the parent widget. Then in `render()`, replace the row 2 content preview (around line 170-180):

```java
// --- Row 2: Content preview ---
if (isServerQuest && quest.getVisibility() == Visibility.CLOSED) {
    // Hide content for closed server quests
    context.drawText(client.textRenderer,
            Text.literal("Request access to view").formatted(Formatting.ITALIC),
            entryX + 4, entryY + 14, Colors.TEXT_MUTED, false);
} else {
    String truncatedContent = client.textRenderer.trimToWidth(firstLine, entryWidth - 22);
    context.drawText(client.textRenderer, Text.literal(truncatedContent).formatted(Formatting.GRAY),
            entryX + 4, entryY + 14, Colors.TEXT_MUTED, false);
}
```

The `isServerQuest` flag needs to be passed when creating entries. Check how `QuestListWidget.setQuests()` works and add the flag there.

- [ ] **Step 2: Hide content in view mode for closed quests**

In `QuestScreen.initViewMode()`, after creating the content area, check if the quest is closed and the viewer is not an owner/contributor:

```java
boolean hideContent = quest.getVisibility() == Visibility.CLOSED && !isOwner && !isContributor;
```

Where `isContributor` is:
```java
boolean isContributor = quest.getContributors().stream()
        .anyMatch(c -> c.getUuid().equals(myUuid));
```

If `hideContent` is true:
- Replace the markdown content widget with a centered text: "Request access to view this quest"
- Show a "Request Access" button instead of "Edit"

In `renderViewMode()`, if `hideContent`, skip rendering the content panel and draw the placeholder text instead.

- [ ] **Step 3: Build and commit**
```bash
./gradlew :client:build
git add client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git commit -m "feat: hide closed quest content until access is granted"
```

---

### Task 8: Contributors in View Mode (Issue 8)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`

- [ ] **Step 1: Add contributor display in initViewMode**

In `initViewMode()`, after the metadata bar setup, compute the contributors string:

```java
// Contributors line
String contributorsText = null;
if (!quest.getContributors().isEmpty()) {
    contributorsText = quest.getContributors().stream()
            .map(c -> c.getName())
            .collect(Collectors.joining(", "));
}
this.viewContributorsText = contributorsText;
```

Add field:
```java
private String viewContributorsText;
```

- [ ] **Step 2: Render contributors in renderViewMode**

In `renderViewMode()`, after the metadata bar rendering and before the buttons, add:

```java
// Contributors (below metadata, above buttons)
if (viewContributorsText != null) {
    int contribY = /* after metadata bar Y + height */ ;
    context.drawText(this.textRenderer, "Contributors: " + viewContributorsText,
            contentX + 5, contribY, Colors.TEXT_MUTED, false);
}
```

Adjust the layout math to account for the new contributors row. The content panel bottom needs to shrink by ~12px when contributors exist. This follows the same pattern as the metadata bar height calculation.

- [ ] **Step 3: Build and commit**
```bash
./gradlew :client:build
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git commit -m "feat: show contributors in quest view mode"
```

---

## Chunk 4: Config Screen + HUD Fixes (Issues 5, 10, 11)

### Task 9: Dedicated Config Screen (Issue 5)

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/screen/ConfigScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/compat/ModMenuIntegration.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfig.java`

- [ ] **Step 1: Create ConfigScreen**

```java
package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends BaseScreen {

    private int pinnedWidth;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Disquests Settings"), parent);
        this.pinnedWidth = DisquestsConfig.getPinnedWidth();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int y = this.height / 2 - 30;

        // Label
        // (drawn in render)

        // Slider for pinned width (100-400)
        this.addDrawableChild(new SliderWidget(
                centerX - 75, y, 150, 20,
                Text.literal("Pinned Width: " + pinnedWidth),
                (pinnedWidth - 100) / 300.0) {
            @Override
            protected void updateMessage() {
                int val = 100 + (int) (this.value * 300);
                pinnedWidth = val;
                this.setMessage(Text.literal("Pinned Width: " + val));
            }

            @Override
            protected void applyValue() {
                pinnedWidth = 100 + (int) (this.value * 300);
            }
        });

        y += 30;

        // Save button
        this.addDrawableChild(new DarkButtonWidget(
                centerX - 55, y, 50, 20,
                Text.literal("Save"), b -> {
                    DisquestsConfig.setPinnedWidth(pinnedWidth);
                    DisquestsConfig.save();
                    this.close();
                }));

        // Cancel button
        this.addDrawableChild(new DarkButtonWidget(
                centerX + 5, y, 50, 20,
                Text.literal("Cancel"), b -> this.close()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 50, Colors.TEXT_PRIMARY);
    }
}
```

Note: `SliderWidget` is a Minecraft vanilla widget. Check if it exists in 1.21.11 yarn mappings. If not, use a text field with +/- buttons instead.

Add `setPinnedWidth` to `DisquestsConfig`:
```java
public static void setPinnedWidth(int width) {
    pinnedWidth = Math.max(100, Math.min(400, width));
}
```

- [ ] **Step 2: Update ModMenuIntegration**

```java
return ConfigScreen::new;
```

- [ ] **Step 3: Build and commit**
```bash
./gradlew :client:build
git add client/src/main/java/com/disqt/disquests/client/gui/screen/ConfigScreen.java
git add client/src/main/java/com/disqt/disquests/client/compat/ModMenuIntegration.java
git add client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfig.java
git commit -m "feat: add dedicated config screen with pinned width slider"
```

---

### Task 10: Fix Stale Pinned HUD (Issue 10)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java:43-48`

- [ ] **Step 1: Fix cache invalidation**

The current cache check (line 44-48) only compares pin IDs:
```java
List<UUID> currentIds = quests.stream().map(Quest::getId).toList();
if (!currentIds.equals(lastPinnedIds)) {
    rebuildCache(client.textRenderer, quests);
    lastPinnedIds = currentIds;
}
```

Also compare quest lastModified timestamps to detect content changes:

```java
List<UUID> currentIds = quests.stream().map(Quest::getId).toList();
long currentHash = quests.stream()
        .mapToLong(q -> q.getId().hashCode() + q.getLastModified())
        .sum();
if (!currentIds.equals(lastPinnedIds) || currentHash != lastContentHash) {
    rebuildCache(client.textRenderer, quests);
    lastPinnedIds = currentIds;
    lastContentHash = currentHash;
}
```

Add field:
```java
private static long lastContentHash = 0;
```

Reset in the empty check:
```java
if (quests.isEmpty()) {
    lastPinnedIds = List.of();
    lastContentHash = 0;
    cachedPins = List.of();
    return;
}
```

- [ ] **Step 2: Build and commit**
```bash
./gradlew :client:build
git add client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java
git commit -m "fix: refresh pinned HUD when quest content changes"
```

---

### Task 11: Markdown Rendering in Pinned HUD (Issue 11)

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java:60-78, 96-100`

- [ ] **Step 1: Replace plain text with formatted rendering**

Currently `rebuildCache()` uses `MarkdownRenderer.stripToPlainText()` (lines 64-67). Instead, use the markdown renderer to produce formatted `Text` objects.

This requires changing the `CachedPin` record to store `List<Text>` instead of `List<String>` for content lines, and updating `renderCachedPin` to use `context.drawText(tr, textObj, ...)` instead of `context.drawText(tr, string, ...)`.

Check if `MarkdownRenderer` has a method that returns `Text` objects with formatting. If it returns `RenderedLine` objects, adapt those. If it only returns plain strings, this task requires adding a new method to `MarkdownRenderer` that produces `Text` with `Formatting` applied.

The simplest approach: change content rendering from `stripToPlainText` to a basic markdown-to-Text converter that handles `**bold**`, `*italic*`, and `~~strikethrough~~` by producing `Text.literal().formatted()` chains.

- [ ] **Step 2: Build and commit**
```bash
./gradlew :client:build
git add client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java
git commit -m "feat: render markdown formatting in pinned HUD"
```

---

## Chunk 5: Migration + Final (Issue 9)

### Task 12: BuildNotes Migration (Issue 9)

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/migration/BuildNotesMigrator.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java`
- Test: `common/src/test/java/com/disqt/disquests/common/BuildNotesMigratorTest.java` (unit test for file parsing logic)

- [ ] **Step 1: Create BuildNotesMigrator**

```java
package com.disqt.disquests.client.migration;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

public class BuildNotesMigrator {

    private static final Logger LOGGER = LoggerFactory.getLogger("Disquests");

    public static void migrateIfNeeded(String serverAddress) {
        Path notesDir = FabricLoader.getInstance().getGameDir()
                .resolve("notes/remote/" + serverAddress);

        if (!Files.isDirectory(notesDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir, "*.txt")) {
            for (Path file : stream) {
                migrateFile(file);
            }
            // Delete directory if empty
            try (DirectoryStream<Path> check = Files.newDirectoryStream(notesDir)) {
                if (!check.iterator().hasNext()) {
                    Files.delete(notesDir);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to migrate BuildNotes", e);
        }
    }

    private static void migrateFile(Path file) throws IOException {
        String content = Files.readString(file);
        if (content.isBlank()) {
            Files.delete(file);
            return;
        }

        String title = file.getFileName().toString();
        if (title.endsWith(".txt")) {
            title = title.substring(0, title.length() - 4);
        }

        UUID questId = UUID.randomUUID();
        UUID ownerUuid = ClientSession.getEffectivePlayerUuid();
        long now = System.currentTimeMillis() / 1000;

        QuestData quest = new QuestData(
                questId, title, content, Visibility.PRIVATE,
                ownerUuid, null, now,
                null, null, null, List.of()
        );

        PacketSender.saveQuest(quest);
        Files.delete(file);
    }
}
```

Note: Check the `QuestData` constructor parameters and `PacketSender.saveQuest` signature -- they may differ. Adjust accordingly.

- [ ] **Step 2: Call migrator after handshake**

In `ClientPacketHandler.handleHandshake()`, after `PacketSender.requestSync()`:

```java
// Migrate old BuildNotes if present
String serverAddress = /* get from connection info */;
BuildNotesMigrator.migrateIfNeeded(serverAddress);
```

The server address needs to come from the connection. Check if `MinecraftClient.getInstance().getCurrentServerEntry()` provides it, or if the handshake payload includes it.

- [ ] **Step 3: Build and commit**
```bash
./gradlew :client:build
git add client/src/main/java/com/disqt/disquests/client/migration/BuildNotesMigrator.java
git add client/src/main/java/com/disqt/disquests/client/network/ClientPacketHandler.java
git commit -m "feat: silently migrate old BuildNotes to private server quests"
```

---

### Task 13: Final Verification

- [ ] **Step 1: Full build**
```bash
./gradlew clean build
```

- [ ] **Step 2: Run E2E tests locally**
```bash
./gradlew :client:runClientGameTest
```
QuestScreenTest must pass. DisquestsE2ETest will fail without server (expected).

- [ ] **Step 3: Run /simplify**

Review all changed files for reuse, quality, and efficiency. Fix any issues found.

- [ ] **Step 4: Run E2E tests again after simplify**
```bash
./gradlew :client:runClientGameTest
```

- [ ] **Step 5: Create PR**
```bash
git push -u origin fix/v023-ui-improvements
gh pr create --title "fix: v0.2.3 UI improvements" --body "..."
```

---

## File Map

| File | Action | Tasks |
|------|--------|-------|
| `MainScreen.java` | Modify | 2, 4 |
| `QuestScreen.java` | Modify | 3, 5, 7, 8 |
| `QuestListWidget.java` | Modify | 6, 7 |
| `ToastOverlay.java` | Create | 4 |
| `ConfigScreen.java` | Create | 9 |
| `ModMenuIntegration.java` | Modify | 9 |
| `DisquestsConfig.java` | Modify | 9 |
| `ClientSession.java` | Modify | 4 |
| `ClientPacketHandler.java` | Modify | 4, 12 |
| `HudPinRenderer.java` | Modify | 10, 11 |
| `BuildNotesMigrator.java` | Create | 12 |
| `QuestScreenTest.java` | Modify | 2, 3, 5 |
