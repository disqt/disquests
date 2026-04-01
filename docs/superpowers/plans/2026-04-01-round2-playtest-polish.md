# Round 2: v2.9 Playtest Polish Fixes

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix 8 polish issues from second playtest round: tag chip wrapping, hover feedback, input merge, HUD positioning, BlueMap config, and keybind toggle.

**Architecture:** All changes are client-side except BlueMap config (server `Config.java` + production `config.yml`). Tag chip layout switches from owo-ui `horizontal` FlowLayout to `ltr-text-flow` (wrapping algorithm). TagPickerScreen merges two text fields into one unified input. HudPinRenderer position defaults are fixed. The keybind handler adds toggle logic by checking the current screen type.

**Tech Stack:** Java 21, Fabric, owo-ui 0.13.0, XML layouts

---

## Task 1: Tag chips wrap to next line (Issue #11)

**Problem:** The chip cloud in TagPickerScreen, the tag-editor in QuestScreen (edit mode), and the tag-display in QuestScreen (view mode) all use `direction="horizontal"` FlowLayout. owo-ui's `HORIZONTAL` algorithm lays children in a single row and clips anything beyond the container width. owo-ui provides an `LTR_TEXT` algorithm (`direction="ltr-text-flow"` in XML) that wraps children to the next line when they exceed the container width. This algorithm requires non-content horizontal sizing (e.g. `fill`), which all three containers already use.

**Files:**
- `client/src/main/resources/assets/disquests/owo_ui/tag_picker_screen.xml` (modify)
- `client/src/main/resources/assets/disquests/owo_ui/quest_screen_edit.xml` (modify)
- `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java` (modify)

### Step 1: Change chip-cloud to ltr-text-flow in tag_picker_screen.xml

In `client/src/main/resources/assets/disquests/owo_ui/tag_picker_screen.xml`, change the chip-cloud container from `direction="horizontal"` to `direction="ltr-text-flow"` and remove the `<allow-overflow>true</allow-overflow>` line (overflow is no longer needed since chips wrap):

```xml
                    <!-- Chip cloud (tags added programmatically as TagChipComponents) -->
                    <flow-layout direction="ltr-text-flow" id="chip-cloud">
                        <sizing>
                            <horizontal method="fill">100</horizontal>
                            <vertical method="content"/>
                        </sizing>
                        <gap>4</gap>
                        <children/>
                    </flow-layout>
```

Replace the existing `<flow-layout direction="horizontal" id="chip-cloud">` block (lines 58-66) with the above.

### Step 2: Change tag-editor to ltr-text-flow in quest_screen_edit.xml

In `client/src/main/resources/assets/disquests/owo_ui/quest_screen_edit.xml`, change the tag-editor container from `direction="horizontal"` to `direction="ltr-text-flow"`:

```xml
            <!-- Tag editor row (populated programmatically) -->
            <flow-layout direction="ltr-text-flow" id="tag-editor">
                <sizing>
                    <horizontal method="fill">85</horizontal>
                    <vertical method="content"/>
                </sizing>
                <gap>3</gap>
                <margins>
                    <bottom>4</bottom>
                </margins>
                <children/>
            </flow-layout>
```

Replace the existing `<flow-layout direction="horizontal" id="tag-editor">` block (lines 54-64) with the above.

### Step 3: Change tag-display in QuestScreen.java to ltr-text-flow

In `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`, in the `buildViewMode` method, the `tagDisplay` is created programmatically with `UIContainers.horizontalFlow(...)`. Change it to use `UIContainers.ltrTextFlow(...)`:

Find:
```java
    FlowLayout tagDisplay = UIContainers.horizontalFlow(Sizing.fill(85), Sizing.content());
```

Replace with:
```java
    FlowLayout tagDisplay = UIContainers.ltrTextFlow(Sizing.fill(85), Sizing.content());
```

### Step 4: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 5: Commit

```
fix(client): use ltr-text-flow for tag chip containers to enable wrapping
```

---

## Task 2: Hover and click feedback on tag chips (Issue #12)

**Problem:** `TagChipComponent` draws a static rounded rectangle with no visual state changes on hover or click. Users have no feedback that chips are interactive.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/gui/component/TagChipComponent.java` (modify)

### Step 1: Add hover and press state tracking and visual feedback

Replace the entire `TagChipComponent.java` with:

```java
package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.gui.helper.RoundedRect;
import com.disqt.disquests.client.gui.helper.TagColors;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;

/**
 * A rounded-rectangle tag chip component. Renders the tag name on a coloured background with
 * optional remove ("x") button. Provides hover highlight and click press feedback.
 */
public class TagChipComponent extends BaseUIComponent {

  private static final int H_PADDING = 5;
  private static final int V_PADDING = 2;
  private static final int REMOVE_AREA_WIDTH = 10;

  /** Additive brightness applied to the background when hovered. */
  private static final int HOVER_BRIGHTEN = 0x22;

  /** Subtractive brightness applied to the background when pressed. */
  private static final int PRESS_DARKEN = 0x20;

  /** Duration in ticks for the press flash effect. */
  private static final int PRESS_FLASH_TICKS = 3;

  private final String tag;
  private final int bgColor;
  private final int fgColor;
  private final boolean showRemove;
  private Consumer<String> onRemove;
  private Consumer<String> onSelect;

  private boolean hovered;
  private boolean removeHovered;
  private int pressFlashRemaining;

  /** Creates a chip for the given tag. If showRemove is true, an "x" button is rendered. */
  public TagChipComponent(String tag, boolean showRemove) {
    this.tag = tag;
    this.bgColor = TagColors.getBackground(tag);
    this.fgColor = TagColors.getForeground(tag);
    this.showRemove = showRemove;
  }

  /** Creates a read-only chip (no remove button). */
  public TagChipComponent(String tag) {
    this(tag, false);
  }

  public TagChipComponent onRemove(Consumer<String> callback) {
    this.onRemove = callback;
    return this;
  }

  public TagChipComponent onSelect(Consumer<String> callback) {
    this.onSelect = callback;
    return this;
  }

  public String getTag() {
    return tag;
  }

  @Override
  protected int determineHorizontalContentSize(Sizing sizing) {
    TextRenderer tr = MinecraftClient.getInstance().textRenderer;
    int textWidth = tr.getWidth(tag);
    int width = H_PADDING + textWidth + H_PADDING;
    if (showRemove) {
      width += REMOVE_AREA_WIDTH;
    }
    return width;
  }

  @Override
  protected int determineVerticalContentSize(Sizing sizing) {
    return MinecraftClient.getInstance().textRenderer.fontHeight + V_PADDING * 2;
  }

  @Override
  public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
    int x = this.x();
    int y = this.y();
    int w = this.width();
    int h = this.height();
    TextRenderer tr = MinecraftClient.getInstance().textRenderer;

    // Update hover state from mouse position
    this.hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    this.removeHovered =
        this.hovered && showRemove && mouseX >= x + w - REMOVE_AREA_WIDTH;

    // Tick press flash
    if (pressFlashRemaining > 0) {
      pressFlashRemaining--;
    }

    // Compute effective background color
    int effectiveBg = bgColor;
    if (pressFlashRemaining > 0) {
      effectiveBg = darkenColor(effectiveBg, PRESS_DARKEN);
    } else if (hovered) {
      effectiveBg = brightenColor(effectiveBg, HOVER_BRIGHTEN);
    }

    RoundedRect.draw(context, x, y, w, h, effectiveBg);

    // Tag text
    context.drawText(tr, Text.literal(tag), x + H_PADDING, y + V_PADDING, fgColor, false);

    // Remove "x" indicator
    if (showRemove) {
      int xBtnColor = removeHovered ? brightenColor(fgColor, 0x30) : fgColor;
      int xBtnX = x + w - REMOVE_AREA_WIDTH + (REMOVE_AREA_WIDTH - tr.getWidth("x")) / 2;
      context.drawText(tr, Text.literal("x"), xBtnX, y + V_PADDING, xBtnColor, false);
    }
  }

  @Override
  public boolean onMouseDown(Click click, boolean doubled) {
    if (click.button() != 0) return false;

    pressFlashRemaining = PRESS_FLASH_TICKS;

    if (showRemove && onRemove != null) {
      double relX = click.x();
      if (relX >= this.width() - REMOVE_AREA_WIDTH) {
        onRemove.accept(tag);
        return true;
      }
    }

    if (onSelect != null) {
      onSelect.accept(tag);
      return true;
    }
    return false;
  }

  /** Brighten each RGB channel by amount, clamped to 255. Alpha is preserved. */
  private static int brightenColor(int argb, int amount) {
    int a = (argb >> 24) & 0xFF;
    int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
    int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
    int b = Math.min(255, (argb & 0xFF) + amount);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  /** Darken each RGB channel by amount, clamped to 0. Alpha is preserved. */
  private static int darkenColor(int argb, int amount) {
    int a = (argb >> 24) & 0xFF;
    int r = Math.max(0, ((argb >> 16) & 0xFF) - amount);
    int g = Math.max(0, ((argb >> 8) & 0xFF) - amount);
    int b = Math.max(0, (argb & 0xFF) - amount);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }
}
```

### Step 2: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 3: Commit

```
feat(client): add hover highlight and click flash to TagChipComponent
```

---

## Task 3: Merge filter and custom tag inputs (Issue #13)

**Problem:** TagPickerScreen has two separate text inputs ("Filter tags..." and "custom-tag..."). Users are confused by the redundancy. Merge into a single input: typing filters existing tags, pressing Enter on text that doesn't match any existing tag creates a new custom tag.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/gui/screen/TagPickerScreen.java` (modify)
- `client/src/main/resources/assets/disquests/owo_ui/tag_picker_screen.xml` (modify)
- `client/src/main/resources/assets/disquests/lang/en_us.json5` (modify)
- `client/src/main/resources/assets/disquests/lang/fr_fr.json5` (modify)

### Step 1: Remove custom-row from XML layout

In `client/src/main/resources/assets/disquests/owo_ui/tag_picker_screen.xml`, remove the entire custom-row block (the `<flow-layout direction="horizontal" id="custom-row">` element and all its children). The XML should go from the chip-cloud closing tag directly to the Cancel button.

Replace lines 68-77 (the custom-row block):
```xml
                    <!-- Custom tag row (text field + Add button added programmatically) -->
                    <flow-layout direction="horizontal" id="custom-row">
                        <sizing>
                            <horizontal method="fill">100</horizontal>
                            <vertical method="content"/>
                        </sizing>
                        <gap>4</gap>
                        <vertical-alignment>center</vertical-alignment>
                        <children/>
                    </flow-layout>
```

With nothing (delete it entirely). The remaining XML after the chip-cloud should be:

```xml
                    <!-- Cancel button -->
                    <button id="btn-cancel">
```

### Step 2: Add hint label below the input

After the chip-cloud in the XML, add a small hint label that tells users they can press Enter to create a new tag:

```xml
                    <!-- Hint for creating new tags -->
                    <label id="hint-label">
                        <text></text>
                        <shadow>true</shadow>
                        <sizing>
                            <horizontal method="fill">100</horizontal>
                            <vertical method="content"/>
                        </sizing>
                    </label>
```

Insert this between the chip-cloud closing `</flow-layout>` and the Cancel button.

### Step 3: Rewrite TagPickerScreen.java

Replace the entire `TagPickerScreen.java` with:

```java
package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TagChipComponent;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.common.TagConstraints;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class TagPickerScreen extends DisquestsBaseScreen {

  private final Quest quest;
  private final Screen returnScreen;

  private TextFieldComponent filterField;
  private FlowLayout chipCloud;
  private LabelComponent hintLabel;

  public TagPickerScreen(@Nullable Screen parent, Quest quest, Screen returnScreen) {
    super(DataSource.asset(Identifier.of("disquests", "tag_picker_screen")), parent);
    this.quest = quest;
    this.returnScreen = returnScreen;
  }

  @Override
  protected void build(FlowLayout root) {
    applyThemeRoot(root);

    FlowLayout panel = root.childById(FlowLayout.class, "panel");
    if (panel != null) applyThemePanel(panel);

    // Unified filter/create field
    FlowLayout filterRow = root.childById(FlowLayout.class, "filter-row");
    if (filterRow != null) {
      MultiLineTextFieldWidget filterWidget =
          new MultiLineTextFieldWidget(
              client.textRenderer,
              0,
              0,
              160,
              14,
              "",
              Text.translatable("gui.disquests.placeholder.filter_or_create").getString(),
              1,
              false);
      filterField = new TextFieldComponent(filterWidget);
      filterField.sizing(Sizing.fill(100), Sizing.fixed(14));
      filterField.id("filter-field");
      filterField.getDelegate().setChangedListener(text -> rebuildChipCloud());
      filterRow.child(filterField);
    }

    // Chip cloud container
    chipCloud = root.childById(FlowLayout.class, "chip-cloud");

    // Hint label
    hintLabel = root.childById(LabelComponent.class, "hint-label");
    if (hintLabel != null) {
      hintLabel.text(
          Text.translatable("gui.disquests.label.enter_to_create").withColor(Colors.TEXT_MUTED));
      hintLabel.shadow(true);
    }

    rebuildChipCloud();

    // Cancel button
    root.childById(ButtonComponent.class, "btn-cancel")
        .onPress(b -> navigateToScreen(returnScreen));
  }

  @Override
  public boolean keyPressed(KeyInput keyInput) {
    // Intercept Enter to create a custom tag from the current filter text
    if (keyInput.keyCode() == GLFW.GLFW_KEY_ENTER
        || keyInput.keyCode() == GLFW.GLFW_KEY_KP_ENTER) {
      if (filterField != null) {
        String raw = filterField.getText().trim().toLowerCase();
        if (!raw.isEmpty()) {
          // Strip leading # for convenience
          if (raw.startsWith("#")) {
            raw = raw.substring(1);
          }
          if (raw.length() > TagConstraints.MAX_TAG_LENGTH) {
            raw = raw.substring(0, TagConstraints.MAX_TAG_LENGTH);
          }
          if (TagConstraints.TAG_PATTERN.matcher(raw).matches()) {
            addTagAndReturn(raw);
            return true;
          }
        }
      }
    }
    return super.keyPressed(keyInput);
  }

  /** Build or rebuild the chip cloud based on the current filter text. */
  private void rebuildChipCloud() {
    if (chipCloud == null) return;
    chipCloud.clearChildren();

    String filter = filterField != null ? filterField.getText().trim().toLowerCase() : "";
    // Strip leading # for hashtag-independent search (Issue #16)
    String normalizedFilter = filter.startsWith("#") ? filter.substring(1) : filter;

    List<String> existing = quest.getTags();

    // Merge predefined + server tags, deduplicated, preserving order
    Set<String> allTags = new LinkedHashSet<>();
    allTags.addAll(ClientSession.getPredefinedTags());
    allTags.addAll(ClientSession.getServerTags());

    List<String> available = new ArrayList<>();
    for (String tag : allTags) {
      if (existing.contains(tag)) continue; // already on quest
      if (!normalizedFilter.isEmpty() && !tag.contains(normalizedFilter)) continue;
      available.add(tag);
    }

    boolean hasExactMatch =
        !normalizedFilter.isEmpty()
            && (allTags.contains(normalizedFilter) || existing.contains(normalizedFilter));

    if (available.isEmpty()) {
      LabelComponent none =
          UIComponents.label(
              Text.translatable("gui.disquests.label.no_matching_tags")
                  .withColor(Colors.TEXT_MUTED));
      none.shadow(true);
      chipCloud.child(none);
    } else {
      for (String tag : available) {
        TagChipComponent chip = new TagChipComponent(tag).onSelect(t -> addTagAndReturn(t));
        chipCloud.child(chip);
      }
    }

    // Update hint visibility: show "Press Enter to create" when text is non-empty
    // and doesn't exactly match an existing tag
    if (hintLabel != null) {
      if (!normalizedFilter.isEmpty()
          && !hasExactMatch
          && TagConstraints.TAG_PATTERN.matcher(normalizedFilter).matches()) {
        hintLabel.text(
            Text.translatable("gui.disquests.label.enter_to_create").withColor(Colors.TEXT_MUTED));
      } else {
        hintLabel.text(Text.empty());
      }
    }
  }

  private void addTagAndReturn(String tag) {
    List<String> tags = quest.getTags();
    if (!tags.contains(tag) && tags.size() < TagConstraints.MAX_TAGS) {
      tags.add(tag);
    }
    navigateToScreen(returnScreen);
  }
}
```

### Step 4: Update English translations

In `client/src/main/resources/assets/disquests/lang/en_us.json5`, in the `"gui.disquests.placeholder.{}"` section:

Remove:
```json5
    "custom_tag": "custom-tag...",
    "filter_tags": "Filter tags...",
```

Replace with:
```json5
    "filter_or_create": "Search or create tag...",
```

In the `"gui.disquests.label.{}"` section, add:
```json5
    "enter_to_create": "Press Enter to create new tag",
```

### Step 5: Update French translations

In `client/src/main/resources/assets/disquests/lang/fr_fr.json5`, in the `"gui.disquests.placeholder.{}"` section:

Remove:
```json5
    "custom_tag": "tag-personnalisé...",
    "filter_tags": "Filtrer les tags...",
```

Replace with:
```json5
    "filter_or_create": "Chercher ou créer un tag...",
```

In the `"gui.disquests.label.{}"` section, add:
```json5
    "enter_to_create": "Appuie sur Entrée pour créer un tag",
```

### Step 6: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 7: Commit

```
feat(client): merge filter and custom tag inputs into unified search-or-create field
```

---

## Task 4: Hide pinned quests HUD on TagPickerScreen (Issue #14)

**Problem:** The HudPinRenderer mixin injects into `InGameHud.render()`, which only runs during gameplay (not on GUI screens). However, owo-ui screens that don't pause the game (`shouldPause() returns false`) still show the in-game HUD behind them, including the pinned quests overlay. The pinned quests are drawn on top of the TagPickerScreen because both the HUD and the screen render simultaneously.

The fix is to suppress pin rendering when certain screens are open. Since `DisquestsBaseScreen.shouldPause()` already returns `false` (to allow background gameplay), we can simply check in `HudPinRenderer.render()` whether the current screen is a `TagPickerScreen` (or any Disquests screen) and skip rendering.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java` (modify)

### Step 1: Add screen check to HudPinRenderer.render()

In `HudPinRenderer.java`, add an import for `DisquestsBaseScreen` and a screen check at the top of the `render()` method, right after the `if (!visible) return;` line:

Add import:
```java
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
```

In the `render()` method, after `if (!visible) return;` and before `List<Quest> quests = ...`, add:

```java
    // Don't render pins over Disquests screens (they have their own UI)
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.currentScreen instanceof DisquestsBaseScreen) return;
```

Then move the existing `MinecraftClient client = MinecraftClient.getInstance();` line below the quests check (or reuse the one just added). The resulting top of `render()` should be:

```java
  public static void render(DrawContext context) {
    if (!visible) return;

    // Don't render pins over Disquests screens (they have their own UI)
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.currentScreen instanceof DisquestsBaseScreen) return;

    List<Quest> quests = HudPinManager.getPinnedQuests();
    if (quests.isEmpty()) {
      lastPinnedIds = List.of();
      lastContentHash = 0;
      lastWidth = 0;
      cachedPins = List.of();
      return;
    }

    if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return;
```

Note: the `MinecraftClient client` local is now declared before the quests check, so remove the duplicate declaration that was previously after the quests check.

### Step 2: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 3: Commit

```
fix(client): hide pinned quests HUD when Disquests screens are open
```

---

## Task 5: Fix pinned quests position (Issue #15)

**Problem:** Pinned quests appear on the right side of the screen instead of the left. Looking at `HudPinRenderer.resolveX()`, the default position (`configX == -1`) computes `screenWidth - maxWidth - DEFAULT_MARGIN`, which places the pin at the top-right corner. The comment says "top-right" explicitly. The user expects top-left. This means either the default was always top-right (and was recently changed from a previous implementation), or the code was always right and something else moved.

Since the user says "was always on the left side", the `resolveX` default needs to return `DEFAULT_MARGIN` (top-left) instead of `screenWidth - maxWidth - DEFAULT_MARGIN` (top-right).

**Files:**
- `client/src/main/java/com/disqt/disquests/client/hud/HudPinRenderer.java` (modify)

### Step 1: Fix resolveX default to left side

In `HudPinRenderer.java`, change the `resolveX` method:

Find:
```java
  /**
   * Returns the X origin for pin rendering. If configX is -1 (default), pins are placed at the
   * top-right corner. Otherwise, the configured value is used directly.
   */
  private static int resolveX(int screenWidth, int maxWidth, int configX) {
    if (configX == DEFAULT_POSITION) {
      return screenWidth - maxWidth - DEFAULT_MARGIN;
    }
    return configX;
  }
```

Replace with:
```java
  /**
   * Returns the X origin for pin rendering. If configX is -1 (default), pins are placed at the
   * top-left corner. Otherwise, the configured value is used directly.
   */
  private static int resolveX(int screenWidth, int maxWidth, int configX) {
    if (configX == DEFAULT_POSITION) {
      return DEFAULT_MARGIN;
    }
    return configX;
  }
```

### Step 2: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 3: Commit

```
fix(client): restore pinned quests HUD to top-left default position
```

---

## Task 6: Hashtag-independent tag search (Issue #16)

**Problem:** In the TagPickerScreen filter field, searching "#overworld" does not match the tag "overworld" because the `#` is included in the comparison. Similarly, the MainScreen search box uses `#tag` syntax to filter by tags, but the tag picker should be hashtag-independent for convenience.

The MainScreen search already strips the `#` prefix when parsing `#tag` tokens. The TagPickerScreen filter needs the same treatment.

**Note:** This was already handled in Task 3 (the rewritten `TagPickerScreen.rebuildChipCloud()` strips leading `#` from the filter). This task only covers the remaining piece: updating the MainScreen search placeholder and the TagPickerScreen placeholder to mention this behavior.

**Files:**
- `client/src/main/resources/assets/disquests/lang/en_us.json5` (modify)
- `client/src/main/resources/assets/disquests/lang/fr_fr.json5` (modify)

### Step 1: Update placeholder text (already done in Task 3)

The new placeholder `filter_or_create` from Task 3 already covers this. The "Search or create tag..." wording makes it clear that typing a tag name (with or without `#`) will work.

The `#` stripping logic was added in the `rebuildChipCloud()` method in Task 3 (`normalizedFilter` strips leading `#`). The Enter-to-create path in `keyPressed()` also strips the `#`.

No additional code changes needed. This task is completed by Task 3.

### Step 2: Verify

Verify by re-reading the Task 3 code:
- `rebuildChipCloud()` line: `String normalizedFilter = filter.startsWith("#") ? filter.substring(1) : filter;`
- `keyPressed()` line: `if (raw.startsWith("#")) { raw = raw.substring(1); }`

### Step 3: Commit

No separate commit needed -- already included in Task 3's commit.

---

## Task 7: Fix BlueMap URL builder default map ID (Issue #17)

**Problem:** `BlueMapUrlBuilder.buildUrl()` falls back to `"world"` when `quest.getMap()` is null, but the production BlueMap uses map IDs like `"world_new"`. Two changes needed:

1. **Server config**: Add a `bluemap-default-map` config key so the admin can set the fallback map ID. Read it in `Config.java` and pass it through to clients via the handshake.
2. **Code**: Change the fallback from hardcoded `"world"` to use the configured default, or if not configured, use `"overworld"` (which is the standard Minecraft dimension path and maps to the correct BlueMap ID via `bluemap-map-names`).
3. **Production**: Deploy the config to the live server.

**Files:**
- `server/src/main/java/com/disqt/disquests/server/papermc/Config.java` (modify)
- `server/src/main/resources/config.yml` (modify)
- `common/src/main/java/com/disqt/disquests/common/BlueMapUrlBuilder.java` (modify)

### Step 1: Add bluemap-default-map to Config.java

In `server/src/main/java/com/disqt/disquests/server/papermc/Config.java`, add a field:

```java
  private String bluemapDefaultMap;
```

In the `reload()` method, after the `bluemapMapNames` section loading, add:

```java
    this.bluemapDefaultMap = cfg.getString("bluemap-default-map", "overworld");
```

Add a getter:

```java
  public String getBluemapDefaultMap() {
    return bluemapDefaultMap;
  }
```

### Step 2: Add bluemap-default-map to config.yml template

In `server/src/main/resources/config.yml`, add after the `bluemap-url` line:

```yaml
# Default map dimension used when a quest has no map set.
# Must be a key in bluemap-map-names (e.g. "overworld").
bluemap-default-map: "overworld"
```

The full file should now be:

```yaml
# BlueMap web map URL. Leave empty to disable BlueMap links.
bluemap-url: ""

# Default map dimension used when a quest has no map set.
# Must be a key in bluemap-map-names (e.g. "overworld").
bluemap-default-map: "overworld"

# Map name mappings: quest map name -> BlueMap map ID.
# Used to generate correct BlueMap URLs from quest coordinates.
bluemap-map-names:
  overworld: "world_new"
  nether: "world_new_nether"
  the_end: "world_new_the_end"

# Predefined tags shown to clients for quick selection.
predefined-tags:
  - overworld
  - nether
  - the_end
  - building
  - redstone
  - farm
```

### Step 3: Update BlueMapUrlBuilder to accept a default map parameter

In `common/src/main/java/com/disqt/disquests/common/BlueMapUrlBuilder.java`, change the `buildUrl` method signature and fallback:

Find:
```java
    String mapId = map != null ? mapNames.getOrDefault(map, map) : "world";
```

Replace with:
```java
    String effectiveMap = map != null ? map : defaultMap;
    String mapId = mapNames.getOrDefault(effectiveMap, effectiveMap);
```

Update both method signatures to add a `String defaultMap` parameter:

```java
  public static String buildUrl(
      String base,
      double x,
      double y,
      double z,
      String map,
      Map<String, String> mapNames,
      String defaultMap) {
```

```java
  public static String buildUrlRegion(
      String base,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      String map,
      Map<String, String> mapNames,
      String defaultMap) {
```

Update `buildUrlRegion` to pass `defaultMap` through:
```java
    return buildUrl(base, cx, cy, cz, map, mapNames, defaultMap);
```

### Step 4: Update BlueMapHelper to pass default map

In `client/src/main/java/com/disqt/disquests/client/BlueMapHelper.java`, update the calls to pass the default map from ClientSession:

Find:
```java
      return BlueMapUrlBuilder.buildUrlRegion(
          base, c1.x(), c1.y(), c1.z(), c2.x(), c2.y(), c2.z(), map, mapNames);
```

Replace with:
```java
      String defaultMap = ClientSession.getBluemapDefaultMap();
      return BlueMapUrlBuilder.buildUrlRegion(
          base, c1.x(), c1.y(), c1.z(), c2.x(), c2.y(), c2.z(), map, mapNames, defaultMap);
```

Find:
```java
      return BlueMapUrlBuilder.buildUrl(base, c.x(), c.y(), c.z(), map, mapNames);
```

Replace with:
```java
      String defaultMap = ClientSession.getBluemapDefaultMap();
      return BlueMapUrlBuilder.buildUrl(base, c.x(), c.y(), c.z(), map, mapNames, defaultMap);
```

### Step 5: Add getBluemapDefaultMap to ClientSession

This requires adding the default map to the handshake protocol. The server already sends `bluemapUrl` and `bluemapMapNames` in the handshake. Add `bluemapDefaultMap` alongside them.

In `ClientSession.java`, add:
```java
  private static String bluemapDefaultMap = "overworld";

  public static String getBluemapDefaultMap() {
    return bluemapDefaultMap;
  }

  public static void setBluemapDefaultMap(String defaultMap) {
    bluemapDefaultMap = defaultMap;
  }
```

**Handshake protocol update:** The default map must be sent from server to client. Add it to the handshake packet:

In `PacketCodec.java`, in `writeHandshake()` (after writing `mapNames`), add:
```java
    writer.writeUtf(config.getBluemapDefaultMap());
```

In `readHandshake()`, after reading `mapNames`, add:
```java
    String defaultMap = buf.remaining() > 0 ? reader.readUtf() : "overworld";
```

Then in the client handshake handler, call `ClientSession.setBluemapDefaultMap(defaultMap)`.

**Important:** Use `buf.remaining() > 0` guard so old servers without this field default to `"overworld"`. This follows the same trailing-optional-field pattern used for tags in WS1.

### Step 6: Deploy production config

After the code changes are merged, SSH to the Minecraft server and add the missing sections to the production config:

```bash
ssh minecraft "cat /home/minecraft/serverfiles/plugins/Disquests/config.yml"
```

If `bluemap-default-map` is missing, add it. If `bluemap-url` is empty, set it:

```bash
ssh minecraft "cat >> /home/minecraft/serverfiles/plugins/Disquests/config.yml << 'EOF'

bluemap-default-map: \"overworld\"
EOF"
```

Verify the `bluemap-map-names` section is present with `overworld: "world_new"` mapping. If missing, add it.

Then reload: `ssh minecraft "tmux -S /tmp/tmux-1000/pmcserver-bb664df1 send-keys -t pmcserver 'disquests reload' Enter"`

**Note:** Production deployment is a post-merge operational step, not part of the code commit.

### Step 7: Update PacketCodec tests

In `common/src/test/java/com/disqt/disquests/common/PacketCodecTest.java`, update any handshake round-trip tests to include the new `defaultMap` field.

### Step 8: Verify compilation

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

### Step 9: Commit

```
fix(bluemap): make default map ID configurable and pass through handshake
```

---

## Task 8: N keybind toggles quest screen (Issue #18)

**Problem:** Pressing N opens the quest screen, but pressing N again while the screen is open does nothing. The keybind handler in `DisquestsClient.java` only opens the screen when `client.currentScreen == null`. When a screen is open, the keybind is consumed by the screen's key handler. However, `DisquestsBaseScreen` extends `BaseUIModelScreen` which calls `super.keyPressed()`, and vanilla `Screen.keyPressed()` does not consume unbound keys. The real issue is that when a screen is open, `wasPressed()` is not called because Minecraft stops polling keybinds when a screen is active.

The fix is to override `keyPressed` in `DisquestsBaseScreen` to check if the pressed key matches the `openGuiKey` binding, and if so, close the screen chain.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java` (modify)
- `client/src/main/java/com/disqt/disquests/client/KeyBinds.java` (no changes needed, already public)

### Step 1: Add keybind toggle to DisquestsBaseScreen

In `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java`, add an import for `KeyBinds`:

```java
import com.disqt.disquests.client.KeyBinds;
```

In the `keyPressed(KeyInput keyInput)` method, add a check at the very beginning (before the greedy input routing) that closes the screen if the open-GUI key is pressed:

Find:
```java
  @Override
  public boolean keyPressed(KeyInput keyInput) {
    // BaseOwoScreen skips GreedyInputUIComponent routing when Ctrl is held,
    // which prevents Ctrl+A, Ctrl+Z, Ctrl+Y etc from reaching text fields.
    // Route ALL key events to the focused greedy component first.
    if (this.uiAdapter != null) {
```

Replace with:
```java
  @Override
  public boolean keyPressed(KeyInput keyInput) {
    // Toggle: pressing the open-GUI key while a Disquests screen is open closes it.
    // Check that no text field is focused (avoid closing while typing).
    if (KeyBinds.openGuiKey.matchesKey(keyInput.keyCode(), keyInput.scanCode())) {
      boolean textFieldFocused = false;
      if (this.uiAdapter != null) {
        var focused = this.uiAdapter.rootComponent.focusHandler().focused();
        textFieldFocused = focused instanceof io.wispforest.owo.ui.inject.GreedyInputUIComponent;
      }
      if (!textFieldFocused) {
        if (this.client != null) {
          this.client.setScreen(null);
        }
        return true;
      }
    }

    // BaseOwoScreen skips GreedyInputUIComponent routing when Ctrl is held,
    // which prevents Ctrl+A, Ctrl+Z, Ctrl+Y etc from reaching text fields.
    // Route ALL key events to the focused greedy component first.
    if (this.uiAdapter != null) {
```

**Behavior:** When N is pressed and no text field has focus, the entire screen chain closes (returns to gameplay). When a text field has focus, the keypress goes to the text field normally (so typing "N" in a quest title works).

### Step 2: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 3: Commit

```
feat(client): toggle Disquests screen closed with N keybind
```

---

## Task 9: E2E test coverage

**Cross-cutting requirement:** Every fix above must have E2E test coverage. This task adds new test methods to the existing `TagJourney.java` and creates a new journey for the keybind toggle.

**Files:**
- `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/TagJourney.java` (modify)
- `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/KeybindToggleJourney.java` (create)

### Step 1: Add tag chip wrapping test to TagJourney

Add a test after the existing `addCustomTag` test (Order 8) that adds enough tags to force wrapping and verifies the chip cloud has a height greater than a single row:

```java
  @Test
  @Order(8)
  @PlayerA
  @DisplayName("Multiple tags wrap in chip cloud")
  void multipleTagsWrap(ClientGameTestContext context) {
    given("quest is in edit mode");
    waitForEditMode(context);

    // Add several tags to force wrapping
    String[] tagsToAdd = {"building", "redstone", "farm"};
    for (String tag : tagsToAdd) {
      click(context, "btn-add-tag");
      waitForScreen(context, TagPickerScreen.class);
      clickChipByTag(context, tag);
      waitForEditMode(context);
    }

    then("tag editor contains multiple tags");
    int count = tagEditorTagCount(context);
    org.junit.jupiter.api.Assertions.assertTrue(
        count >= 3, "Expected at least 3 tags, got: " + count);

    when("player saves");
    click(context, "btn-save");
    waitForViewMode(context);
    then("view mode shows wrapped tags");
    int viewCount = tagDisplayTagCount(context);
    org.junit.jupiter.api.Assertions.assertTrue(
        viewCount >= 3, "Expected at least 3 tags in view mode, got: " + viewCount);
  }
```

Also add the helper method `clickChipByTag`:

```java
  /** Click a specific tag chip by tag name in the TagPickerScreen chip cloud. */
  private void clickChipByTag(ClientGameTestContext context, String tagName) {
    waitForScreen(context, TagPickerScreen.class);

    double[] pos =
        context.computeOnClient(
            c -> {
              if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return null;
              var root = dScreen.getRootComponent();
              if (root == null) return null;
              FlowLayout chipCloud = root.childById(FlowLayout.class, "chip-cloud");
              if (chipCloud == null) return null;
              for (UIComponent child : chipCloud.children()) {
                if (child instanceof TagChipComponent chip && tagName.equals(chip.getTag())) {
                  return new double[] {
                    chip.x() + chip.width() / 2.0, chip.y() + chip.height() / 2.0
                  };
                }
              }
              return null;
            });

    if (pos == null) {
      throw new AssertionError("Tag chip '" + tagName + "' not found in chip-cloud");
    }

    double scale = scaleFactor(context);
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
  }
```

### Step 2: Add merged input test to TagJourney

Add a test (Order 9) that uses the unified input to filter and then create a custom tag via Enter:

```java
  @Test
  @Order(9)
  @PlayerA
  @DisplayName("Unified input filters then creates tag on Enter")
  void unifiedInputFilterAndCreate(ClientGameTestContext context) {
    given("player opens tag picker");
    openMainScreen(context);
    clickEntry(context, 0);
    click(context, "btn-open");
    waitForViewMode(context);
    click(context, "btn-edit");
    waitForEditMode(context);
    click(context, "btn-add-tag");
    waitForScreen(context, TagPickerScreen.class);

    when("player types 'mynewtag' and presses Enter");
    type(context, "filter-field", "mynewtag");
    context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
    context.waitTicks(2);

    then("returns to edit mode with the new custom tag");
    waitForEditMode(context);
    context.waitFor(
        client -> {
          if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          FlowLayout tagEditor = root.childById(FlowLayout.class, "tag-editor");
          if (tagEditor == null) return false;
          return tagEditor.children().stream()
              .anyMatch(
                  child ->
                      child instanceof TagChipComponent chip
                          && "mynewtag".equals(chip.getTag()));
        },
        TIMEOUT);
  }
```

### Step 3: Add hashtag-independent filter test

Add a test (Order 10) that verifies `#building` matches the `building` tag:

```java
  @Test
  @Order(10)
  @PlayerA
  @DisplayName("Hashtag-independent tag search in picker")
  void hashtagIndependentSearch(ClientGameTestContext context) {
    given("player opens tag picker");
    waitForEditMode(context);
    click(context, "btn-add-tag");
    waitForScreen(context, TagPickerScreen.class);

    when("player types '#building' in the filter");
    type(context, "filter-field", "#building");
    context.waitTicks(2);

    then("building tag chip appears in the cloud");
    boolean found =
        context.computeOnClient(
            c -> {
              if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
              var root = dScreen.getRootComponent();
              if (root == null) return false;
              FlowLayout chipCloud = root.childById(FlowLayout.class, "chip-cloud");
              if (chipCloud == null) return false;
              return chipCloud.children().stream()
                  .anyMatch(
                      child ->
                          child instanceof TagChipComponent chip
                              && "building".equals(chip.getTag()));
            });
    org.junit.jupiter.api.Assertions.assertTrue(found, "Expected 'building' chip to be visible");

    // Cancel to return
    click(context, "btn-cancel");
    waitForEditMode(context);
  }
```

### Step 4: Create KeybindToggleJourney

Create `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/KeybindToggleJourney.java`:

```java
package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

@IntegrationTest
@DisplayName("Keybind Toggle Journey")
class KeybindToggleJourney {

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("N key opens and closes quest screen")
  void nKeyToggle(ClientGameTestContext context) {
    given("player is connected with no screen open");
    context.waitFor(client -> client.currentScreen == null, TIMEOUT);

    when("player presses N");
    context.getInput().pressKey(GLFW.GLFW_KEY_N);
    context.waitTicks(5);

    then("MainScreen opens");
    waitForScreen(context, MainScreen.class);
    assertScreenIs(context, MainScreen.class);

    when("player presses N again");
    context.getInput().pressKey(GLFW.GLFW_KEY_N);
    context.waitTicks(5);

    then("screen closes and returns to gameplay");
    context.waitFor(client -> client.currentScreen == null, TIMEOUT);
  }
}
```

### Step 5: Verify test compilation

```bash
./gradlew :client:compileTestmodJava
```

Expected: BUILD SUCCESSFUL

### Step 6: Run E2E tests

```bash
./gradlew :client:runSoloTests -PtestFilter=TagJourney
./gradlew :client:runSoloTests -PtestFilter=KeybindToggleJourney
```

Expected: All tests pass

### Step 7: Commit

```
test(e2e): add tests for tag wrapping, unified input, hashtag search, and keybind toggle
```

---

## Summary

| Task | Issue | Files | Commit |
|------|-------|-------|--------|
| 1 | #11 Tag chip wrapping | XML x2, QuestScreen.java | `fix(client): use ltr-text-flow for tag chip containers` |
| 2 | #12 Hover/click feedback | TagChipComponent.java | `feat(client): add hover highlight and click flash` |
| 3 | #13 Merge inputs + #16 hashtag search | TagPickerScreen.java, XML, lang x2 | `feat(client): merge filter and custom tag inputs` |
| 4 | #14 Hide HUD on tag picker | HudPinRenderer.java | `fix(client): hide pinned quests HUD on screens` |
| 5 | #15 Pin position | HudPinRenderer.java | `fix(client): restore top-left default position` |
| 6 | #16 Hashtag search | (covered by Task 3) | -- |
| 7 | #17 BlueMap config | Config.java, config.yml, BlueMapUrlBuilder.java, BlueMapHelper.java, ClientSession.java, PacketCodec.java | `fix(bluemap): configurable default map ID` |
| 8 | #18 N keybind toggle | DisquestsBaseScreen.java | `feat(client): toggle screen with N keybind` |
| 9 | E2E tests | TagJourney.java, KeybindToggleJourney.java | `test(e2e): coverage for round 2 fixes` |

**Execution order:** Tasks 1-5 and 8 are independent and can run in parallel. Task 6 is a no-op (covered by Task 3). Task 7 has both code and production-deployment steps. Task 9 depends on all other tasks being complete.
