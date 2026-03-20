# MainScreen owo-ui Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate MainScreen from manual pixel-positioned vanilla widgets to owo-ui's declarative layout with XML hot-reload.

**Architecture:** Add owo-lib dependency, create DisquestsBaseScreen (shared base), QuestEntryComponent (custom entry renderer), and rewrite MainScreen as a BaseUIModelScreen with an XML layout model. Delete TabButtonWidget and QuestListWidget.

**Tech Stack:** Java 21, Fabric 1.21.11, owo-lib 0.13.0+1.21.11, XML UI models

**Spec:** `docs/superpowers/specs/2026-03-20-owo-ui-migration-design.md`
**owo-ui Reference:** `docs/references/owo-ui.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `gradle.properties` | Modify | Add `owo_version` |
| `client/build.gradle.kts` | Modify | Add owo-lib dependency + sentinel |
| `client/src/main/resources/fabric.mod.json` | Modify | Add owo-lib to `depends` |
| `client/.../gui/screen/DisquestsBaseScreen.java` | Create | Shared owo-ui base screen |
| `client/.../gui/component/QuestEntryComponent.java` | Create | Custom owo-ui component for quest list entries |
| `client/.../gui/screen/MainScreen.java` | Rewrite | owo-ui MainScreen |
| `client/src/main/resources/assets/disquests/owo_ui/main_screen.xml` | Create | XML UI model |
| `client/.../gui/widget/TabButtonWidget.java` | Delete | Replaced by owo-ui buttons |
| `client/.../gui/widget/list/QuestListWidget.java` | Delete | Replaced by ScrollContainer + QuestEntryComponent |
| `client/src/testmod/.../QuestScreenTest.java` | Modify | Update MainScreen tests |

All paths under `client/src/main/java/com/disqt/disquests/client/`.

---

### Task 1: Add owo-lib Dependency

**Files:**
- Modify: `gradle.properties`
- Modify: `client/build.gradle.kts`
- Modify: `client/src/main/resources/fabric.mod.json`

- [ ] **Step 1: Create feature branch**

```bash
git checkout -b feat/owo-ui-migration
```

- [ ] **Step 2: Add owo_version to gradle.properties**

Add after `modmenu_version`:
```properties
owo_version=0.13.0+1.21.11
```

- [ ] **Step 3: Add owo-lib dependency to client/build.gradle.kts**

Add maven repository:
```kotlin
maven("https://maven.wispforest.io/releases/")
```

Add dependencies after the modmenu block:
```kotlin
// owo-lib UI framework
val owo_version: String by project
modImplementation("io.wispforest:owo-lib:$owo_version")
include("io.wispforest:owo-sentinel:$owo_version")
```

- [ ] **Step 4: Add owo-lib to fabric.mod.json depends**

In the `depends` section, add:
```json
"owo-lib": "*"
```

- [ ] **Step 5: Verify build compiles with owo-lib**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL. owo-lib downloads and resolves.

- [ ] **Step 6: Commit**

```bash
git add gradle.properties client/build.gradle.kts client/src/main/resources/fabric.mod.json
git commit -m "chore: add owo-lib dependency for UI framework"
```

---

### Task 2: DisquestsBaseScreen

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java`

- [ ] **Step 1: Create the base screen class**

```java
package com.disqt.disquests.client.gui.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

public abstract class DisquestsBaseScreen extends BaseUIModelScreen<FlowLayout> {

    @Nullable
    protected final Screen parent;

    protected DisquestsBaseScreen(DataSource source, @Nullable Screen parent) {
        super(FlowLayout.class, source);
        this.parent = parent;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java
git commit -m "feat: add DisquestsBaseScreen for owo-ui screens"
```

---

### Task 3: QuestEntryComponent

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java`

This is the custom owo-ui component that renders a single quest list entry. Port the rendering logic from `QuestListWidget.QuestEntry.render()`.

- [ ] **Step 1: Create QuestEntryComponent**

Read `QuestListWidget.java` first to understand the entry rendering (3 rows: title+visibility+pending, content preview+pin, timestamp+location).

```java
package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.hud.HudPinManager;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;

public class QuestEntryComponent extends BaseComponent {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd MM yyyy");
    private static final Identifier PIN_ICON = Identifier.of("disquests", "icon/pin");
    private static final Identifier PIN_ACTIVE_ICON = Identifier.of("disquests", "icon/pin_active");
    private static final int ENTRY_HEIGHT = 38;

    private final Quest quest;
    private final String firstLine;
    private final String formattedDateTime;
    private final boolean isOwnedByPlayer;
    private final boolean hideContent;

    private boolean selected = false;
    private Consumer<QuestEntryComponent> onClickAction;
    private Consumer<QuestEntryComponent> onDoubleClickAction;
    private Consumer<QuestEntryComponent> onPinToggleAction;
    private long lastClickTime = 0;

    public QuestEntryComponent(Quest quest) {
        this.quest = quest;

        UUID playerUuid = ClientSession.getEffectivePlayerUuid();
        this.isOwnedByPlayer = playerUuid != null && playerUuid.equals(quest.getOwnerUuid());
        boolean isContributor = quest.getContributors().stream()
                .anyMatch(c -> c.getUuid().equals(playerUuid));
        this.hideContent = quest.getVisibility() == Visibility.CLOSED
                && !isOwnedByPlayer && !isContributor;

        if (hideContent) {
            this.firstLine = "";
        } else {
            String content = quest.getContent();
            if (content == null || content.isEmpty()) {
                this.firstLine = "";
            } else {
                String plain = MarkdownRenderer.stripToPlainText(content);
                String[] lines = plain.split("\n");
                this.firstLine = lines.length > 0 ? lines[0] : "";
            }
        }

        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(quest.getLastModified()), ZoneId.systemDefault());
        this.formattedDateTime = dateTime.format(DATE_TIME_FORMATTER);
    }

    public Quest getQuest() { return quest; }

    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isSelected() { return selected; }

    public QuestEntryComponent onClick(Consumer<QuestEntryComponent> action) {
        this.onClickAction = action;
        return this;
    }

    public QuestEntryComponent onDoubleClick(Consumer<QuestEntryComponent> action) {
        this.onDoubleClickAction = action;
        return this;
    }

    public QuestEntryComponent onPinToggle(Consumer<QuestEntryComponent> action) {
        this.onPinToggleAction = action;
        return this;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return 200; // will be overridden by fill sizing
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return ENTRY_HEIGHT;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = this.x;
        int y = this.y;
        int w = this.width;

        // Selection highlight
        if (selected) {
            context.fill(x, y, x + w, y + ENTRY_HEIGHT, 0x44FFFFFF);
        }

        // Hover highlight
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
        if (hovered && !selected) {
            context.fill(x, y, x + w, y + ENTRY_HEIGHT, 0x22FFFFFF);
        }

        // --- Row 1: Title + Visibility + Pending + Owner ---
        Text visText = null;
        if (quest.getVisibility() != null) {
            visText = switch (quest.getVisibility()) {
                case PRIVATE -> Text.literal("Private").formatted(Formatting.LIGHT_PURPLE);
                case CLOSED -> Text.literal("Closed").formatted(Formatting.YELLOW);
                case OPEN -> Text.literal("Open").formatted(Formatting.GREEN);
            };
        }

        Text ownerText = null;
        int ownerWidth = 0;
        if (!isOwnedByPlayer && quest.getOwnerName() != null) {
            ownerText = Text.literal(" by " + quest.getOwnerName()).formatted(Formatting.GRAY);
            ownerWidth = tr.getWidth(ownerText);
        }

        int rightX = x + w - 4;
        if (ownerText != null) {
            rightX -= ownerWidth;
            context.drawText(tr, ownerText, rightX, y + 4, Colors.TEXT_MUTED, false);
        }
        if (isOwnedByPlayer) {
            int pendingCount = ClientCache.getPendingCount(quest.getId());
            if (pendingCount > 0) {
                Text pendingText = Text.literal(" (" + pendingCount + " pending)");
                int pendingWidth = tr.getWidth(pendingText);
                rightX -= pendingWidth;
                context.drawText(tr, pendingText, rightX, y + 4, Colors.AMBER, false);
            }
        }
        if (visText != null) {
            rightX -= tr.getWidth(visText);
            context.drawText(tr, visText, rightX, y + 4, Colors.TEXT_PRIMARY, false);
        }

        int availableTitleWidth = rightX - x - 8;
        String truncatedTitle = tr.trimToWidth(quest.getTitle(), availableTitleWidth);
        context.drawText(tr, truncatedTitle, x + 4, y + 4, Colors.TEXT_PRIMARY, false);

        // --- Row 2: Content preview + pin icon ---
        if (hideContent) {
            context.drawText(tr, Text.literal("Request access to view").formatted(Formatting.ITALIC),
                    x + 4, y + 14, Colors.TEXT_MUTED, false);
        } else {
            String truncatedContent = tr.trimToWidth(firstLine, w - 22);
            context.drawText(tr, Text.literal(truncatedContent).formatted(Formatting.GRAY),
                    x + 4, y + 14, Colors.TEXT_MUTED, false);
        }

        int pinIconSize = 10;
        int pinIconX = x + w - pinIconSize - 4;
        int pinIconY = y + 14;
        boolean pinned = ClientSession.isPinned(quest.getId());
        Identifier pinIcon = pinned ? PIN_ACTIVE_ICON : PIN_ICON;
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, pinIcon, pinIconX, pinIconY, pinIconSize, pinIconSize);

        // --- Row 3: Timestamp + location ---
        context.drawText(tr, "Last Modified: " + formattedDateTime, x + 4, y + 24, Colors.TEXT_MUTED, false);

        String locationStr = buildLocationString();
        if (!locationStr.isEmpty()) {
            int locWidth = tr.getWidth(locationStr);
            context.drawText(tr, locationStr, x + w - locWidth - 4, y + 24, Colors.TEXT_MUTED, false);
        }

        if (ClientSession.isRequested(quest.getId())) {
            String reqText = "Requested";
            int reqWidth = tr.getWidth(reqText);
            context.drawText(tr, reqText, x + w - reqWidth - 4, y + 24, 0xFFCCCC44, false);
        }
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Pin icon click detection
        int pinIconX = this.x + this.width - 14;
        int pinIconY = this.y + 12;
        if (mouseX >= pinIconX && mouseX <= this.x + this.width && mouseY >= pinIconY && mouseY <= pinIconY + 14) {
            HudPinManager.toggle(quest.getId());
            if (onPinToggleAction != null) onPinToggleAction.accept(this);
            return true;
        }

        // Double click detection
        long now = System.currentTimeMillis();
        if (now - lastClickTime < 400 && onDoubleClickAction != null) {
            onDoubleClickAction.accept(this);
            lastClickTime = 0;
            return true;
        }
        lastClickTime = now;

        if (onClickAction != null) onClickAction.accept(this);
        return true;
    }

    private String buildLocationString() {
        if (quest.isRegion()) {
            String mapName = quest.getMap();
            return (mapName != null && !mapName.isEmpty()) ? mapName + " (Region)" : "Region";
        }
        CoordinatesData coords = quest.getCoordinates();
        if (coords == null) return "";
        StringBuilder sb = new StringBuilder();
        String mapName = quest.getMap();
        if (mapName != null && !mapName.isEmpty()) sb.append(mapName);
        String coordStr = "X:" + (int) coords.x() + " Z:" + (int) coords.z();
        if (sb.length() > 0) sb.append(" \u2022 ");
        sb.append(coordStr);
        return sb.toString();
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java
git commit -m "feat: QuestEntryComponent - custom owo-ui component for quest list entries"
```

---

### Task 4: XML UI Model for MainScreen

**Files:**
- Create: `client/src/main/resources/assets/disquests/owo_ui/main_screen.xml`

- [ ] **Step 1: Create the XML layout**

```xml
<owo-ui xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:noNamespaceSchemaLocation="https://raw.githubusercontent.com/wisp-forest/owo-lib/1.21/owo-ui.xsd">
    <components>
        <flow-layout direction="vertical" id="root">
            <children>
                <!-- Title -->
                <label id="title-label">
                    <text>Disquests</text>
                    <shadow>true</shadow>
                    <sizing>
                        <horizontal method="content"/>
                        <vertical method="content"/>
                    </sizing>
                    <margins><top>4</top><bottom>4</bottom></margins>
                </label>

                <!-- Tab row -->
                <flow-layout direction="horizontal" id="tab-row">
                    <children>
                        <button id="tab-my-quests">
                            <text>My Quests</text>
                        </button>
                        <button id="tab-quest-board">
                            <text>Quest Board</text>
                        </button>
                    </children>
                    <horizontal-alignment>center</horizontal-alignment>
                    <gap>4</gap>
                    <sizing>
                        <horizontal method="fill">100</horizontal>
                        <vertical method="content"/>
                    </sizing>
                    <margins><bottom>2</bottom></margins>
                </flow-layout>

                <!-- Filter row (Quest Board only) -->
                <flow-layout direction="horizontal" id="filter-row">
                    <children>
                        <button id="filter-all">
                            <text>All</text>
                        </button>
                        <button id="filter-open">
                            <text>Open</text>
                        </button>
                        <button id="filter-closed">
                            <text>Closed</text>
                        </button>
                    </children>
                    <horizontal-alignment>center</horizontal-alignment>
                    <gap>4</gap>
                    <sizing>
                        <horizontal method="fill">100</horizontal>
                        <vertical method="content"/>
                    </sizing>
                    <margins><bottom>4</bottom></margins>
                </flow-layout>

                <!-- Quest list scroll area -->
                <scroll direction="vertical" id="quest-scroll">
                    <flow-layout direction="vertical" id="quest-list">
                        <children/>
                        <sizing>
                            <horizontal method="fill">100</horizontal>
                            <vertical method="content"/>
                        </sizing>
                        <gap>2</gap>
                    </flow-layout>
                    <sizing>
                        <horizontal method="fill">85</horizontal>
                        <vertical method="fill">100</vertical>
                    </sizing>
                    <surface>
                        <panel dark="true"/>
                    </surface>
                    <padding><all>4</all></padding>
                </scroll>

                <!-- Search bar -->
                <flow-layout direction="horizontal" id="search-row">
                    <children/>
                    <horizontal-alignment>center</horizontal-alignment>
                    <sizing>
                        <horizontal method="fill">100</horizontal>
                        <vertical method="content"/>
                    </sizing>
                    <margins><top>4</top><bottom>4</bottom></margins>
                </flow-layout>

                <!-- Action buttons row -->
                <flow-layout direction="horizontal" id="action-row">
                    <children>
                        <button id="btn-new-quest">
                            <text>New Quest</text>
                        </button>
                        <button id="btn-join">
                            <text>Join</text>
                        </button>
                        <button id="btn-request">
                            <text>Request</text>
                        </button>
                        <button id="btn-open">
                            <text>Open</text>
                        </button>
                        <button id="btn-close">
                            <text>Close</text>
                        </button>
                    </children>
                    <horizontal-alignment>center</horizontal-alignment>
                    <gap>4</gap>
                    <sizing>
                        <horizontal method="fill">100</horizontal>
                        <vertical method="content"/>
                    </sizing>
                </flow-layout>
            </children>

            <surface>
                <vanilla-translucent/>
            </surface>
            <horizontal-alignment>center</horizontal-alignment>
            <padding><all>4</all><top>0</top></padding>
            <sizing>
                <horizontal method="fill">100</horizontal>
                <vertical method="fill">100</vertical>
            </sizing>
        </flow-layout>
    </components>
</owo-ui>
```

- [ ] **Step 2: Build to verify XML is valid**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/resources/assets/disquests/owo_ui/main_screen.xml
git commit -m "feat: XML UI model for MainScreen layout"
```

---

### Task 5: Rewrite MainScreen

**Files:**
- Rewrite: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

This is the largest task. The new MainScreen extends `DisquestsBaseScreen`, loads the XML model, and wires up all behavior.

- [ ] **Step 1: Read the current MainScreen.java fully**

Read all 488 lines. Understand every method: `selectTab`, `selectServerFilter`, `refreshListContents`, `updateActionButtons`, `createNewQuest`, `openSelected`, `joinQuest`, `requestAccess`, `showToast`, `render` (rainbow title, badge, toast).

- [ ] **Step 2: Rewrite MainScreen**

Key changes from the old screen:
- Extends `DisquestsBaseScreen` instead of `BaseScreen`
- `build(FlowLayout root)` wires up button handlers via `childById()`
- `refreshListContents()` clears and re-populates the `quest-list` FlowLayout with QuestEntryComponents
- Tab switching shows/hides the filter row and updates button visibility
- Search uses `Components.textBox()` added dynamically to the search-row
- Rainbow title hover effect in a custom `tick()` + label update
- Toast overlay uses a label with absolute positioning
- Notification badge rendered via a custom label on the tab button

The constructor:
```java
public MainScreen() { this(null); }
public MainScreen(Screen parent) {
    super(DataSource.asset(Identifier.of("disquests", "main_screen")), parent);
    this.currentTab = ClientSession.getActiveTab();
    this.searchTerm = ClientSession.getSearchTerm();
    this.serverFilter = ClientSession.getServerQuestsFilter();
}
```

In `build(FlowLayout root)`:
- Look up all components by ID: `root.childById(ButtonComponent.class, "tab-my-quests")` etc.
- Wire `.onPress()` handlers
- Create the text box for search: `Components.textBox(Sizing.fixed(200))` and add to the `search-row` flow
- Call `selectTab(currentTab)` to set initial state

`refreshListContents()`:
- Get the `quest-list` FlowLayout via `childById()`
- `.clearChildren()`
- Build filtered/sorted quest list (same logic as current)
- For each quest, create `new QuestEntryComponent(quest)` with sizing `Sizing.fill(100), Sizing.fixed(38)`
- Set click/doubleClick/pinToggle handlers
- Add as child to the flow layout

`selectTab(int tab)`:
- Show/hide `filter-row` via `.positioning(Positioning.absolute(-9999, -9999))` to hide, or re-add
- Actually simpler: use the flow layout's `.removeChild()` / `.child()` to add/remove filter row
- Or: set `filterRow.sizing(Sizing.fixed(0), Sizing.fixed(0))` to collapse it when hidden
- Best: keep it simple -- just set visibility. Check if owo-ui has a visibility concept. If not, use `margins(Insets.of(-9999))` hack or remove/re-add.

The implementer should reference `docs/references/owo-ui.md` for API details.

Port these methods from old MainScreen:
- `selectTab()`, `selectServerFilter()`, `onSearchTermChanged()`, `refreshListContents()`, `updateActionButtons()`
- `createNewQuest()`, `openSelected()`, `joinQuest()`, `requestAccess()`, `showToast()`
- `tick()` (toast, pending toast consumption, rainbow counter)
- Rainbow title: update label text with styled colored characters on hover
- Notification badge: update tab button label with count

Public methods that other code calls (must keep the same signatures):
- `refreshListContents()` -- called by ClientPacketHandler
- `refreshAfterPinToggle()` -- called by QuestEntryComponent pin toggle
- `showToast(String)` -- called by ClientPacketHandler
- `getMyQuestList()` -- called by E2E tests. Return type changes (no longer QuestListWidget). The test needs updating.

- [ ] **Step 3: Build and fix compilation errors iteratively**

```bash
./gradlew :client:build
```

Expect several rounds of fixes. The key is getting it to compile first, then verify behavior in-game.

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat: rewrite MainScreen using owo-ui declarative layout"
```

---

### Task 6: Delete Old Widgets

**Files:**
- Delete: `client/src/main/java/com/disqt/disquests/client/gui/widget/TabButtonWidget.java`
- Delete: `client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java`
- Check: `client/src/main/java/com/disqt/disquests/client/gui/widget/list/AbstractListWidget.java`

- [ ] **Step 1: Check if AbstractListWidget is used elsewhere**

```bash
grep -r "AbstractListWidget" client/src/main/java/ --include="*.java" | grep -v "QuestListWidget"
```

If nothing references it, delete it too.

- [ ] **Step 2: Check if TabButtonWidget is used elsewhere**

```bash
grep -r "TabButtonWidget" client/src/main/java/ --include="*.java" | grep -v "MainScreen"
```

If nothing references it, delete it.

- [ ] **Step 3: Delete unreferenced files**

```bash
rm client/src/main/java/com/disqt/disquests/client/gui/widget/TabButtonWidget.java
rm client/src/main/java/com/disqt/disquests/client/gui/widget/list/QuestListWidget.java
# Also delete AbstractListWidget if unreferenced
```

- [ ] **Step 4: Build to verify no broken references**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: delete TabButtonWidget and QuestListWidget (replaced by owo-ui)"
```

---

### Task 7: Update E2E Tests

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java`

- [ ] **Step 1: Update MainScreen tests**

Tests that reference `MainScreen` APIs need updating:
- `testPinDoesNotResort`: calls `screen.getMyQuestList().children()` -- this API no longer exists. Update to use the new MainScreen's quest list access method.
- `testToastOverlay`: calls `screen.showToast()` -- should still work if we kept the method.
- Any test using `MainScreen::new` -- constructor should still work.

Read the test file and update references to removed APIs. If `getMyQuestList()` is removed, either add a new accessor or change the test approach.

- [ ] **Step 2: Build**

```bash
./gradlew :client:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/testmod/
git commit -m "test: update E2E tests for owo-ui MainScreen"
```

---

### Task 8: Full Build + Manual Test

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

- [ ] **Step 3: Update Prism instance for manual testing**

```bash
cp client/build/libs/client.jar "C:/Users/leole/AppData/Roaming/PrismLauncher/instances/1.21.11 v2.7/.minecraft/mods/disquests-client-0.2.4.jar"
```

Also need to add owo-lib jar to the Prism instance mods folder:
```bash
# Download owo-lib from Modrinth or use the cached gradle jar
# The exact path depends on where Gradle cached it
find ~/.gradle/caches -name "owo-lib-0.13.0*" -name "*.jar" 2>/dev/null | head -1
# Copy to Prism mods folder
```

- [ ] **Step 4: Manual test in-game**

Verify:
- MainScreen opens with tabs, search, quest list
- Tab switching works (My Quests / Quest Board)
- Filter buttons work (All / Open / Closed)
- Search filters quest list
- Quest entry click selects, double-click opens
- Pin icon toggles pin state
- New Quest button creates new quest
- Join / Request Access buttons work
- Toast overlay shows and fades
- Notification badge shows pending count on My Quests tab
- Rainbow title hover effect works
- `Ctrl+Shift` opens owo-ui component inspector
- `Ctrl+F5` enables XML hot-reload

- [ ] **Step 5: Fix any issues found during testing**

- [ ] **Step 6: Commit fixes**

```bash
git add -A
git commit -m "fix: address manual testing issues"
```
