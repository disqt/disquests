# Theme Switcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 5-theme visual switcher (Vanilla, Flat, Inset, Frosted, Accent Line) to the Disquests ModMenu config screen.

**Architecture:** A `Theme` enum holds complete color palettes and surface factories. Switching overwrites `Colors.*` static fields at runtime and screens apply theme surfaces in `build()`. The selected theme persists in `config.json`.

**Tech Stack:** Fabric 1.21.11, owo-ui v0.13.0, Java 21

**Spec:** `docs/superpowers/specs/2026-03-21-theme-switcher-design.md`

**Testing:** This is a visual GUI feature. No unit tests -- verify each task by running `./gradlew :client:runClient` and inspecting the UI. The Paper dev server must be running (`./gradlew :paper:runServer`) for screens to populate with quest data.

**Important owo-ui note:** There is no `clearAndInit()` method. To rebuild a screen after theme change, reopen it: `this.client.setScreen(new ConfigScreen(this.parent))`.

---

### Task 1: Add new color fields to Colors.java

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/Colors.java`

- [ ] **Step 1: Add the 4 new fields**

Add after the `AMBER` field, before the private constructor:

```java
// --- Entry Highlights ---
public static int ENTRY_HOVER = 0x22FFFFFF;
public static int ENTRY_SELECTED = 0x44FFFFFF;

// --- Accent Line ---
public static int ACCENT_LINE_ACTIVE = 0x00000000;
public static int ACCENT_LINE_INACTIVE = 0x00000000;
```

These are the Vanilla defaults. Transparent accent line values mean the accent draw call is a no-op for non-Accent-Line themes.

- [ ] **Step 2: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/helper/Colors.java
git commit -m "feat(theme): add ENTRY_HOVER, ENTRY_SELECTED, ACCENT_LINE fields to Colors"
```

---

### Task 2: Create Theme enum

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/helper/Theme.java`

**Reference:** Complete color tables are in the spec under each theme section.

- [ ] **Step 1: Create Theme.java with all 5 variants**

```java
package com.disqt.disquests.client.gui.helper;

import io.wispforest.owo.ui.core.Surface;

public enum Theme {
    VANILLA("Vanilla",
        0x77000000, 0xFFFFFFFF, 0xFFCCCCCC, 0xFF888888,
        0x8855AADD, 0xFFFFFFFF, 0x44000000, 0xAA000000,
        0x44000000, 0x88FFFFFF, 0xFFFFFFFF,
        0xFF101010, 0xFF101010, 0x60000000, 0x00000000,
        0xFFFFAA33, 0x22FFFFFF, 0x44FFFFFF,
        0x00000000, 0x00000000),

    FLAT("Flat",
        0xFF1A1A1A, 0xFFE0E0E0, 0xFF777777, 0xFF555555,
        0x6650AACC, 0xFFE0E0E0, 0xFF1A1A1A, 0xFF2A2A2A,
        0xFF1A1A1A, 0xFF555555, 0xFFAAAAAA,
        0xFF0E0E0E, 0xFF0E0E0E, 0x600E0E0E, 0x000E0E0E,
        0xFFFFAA33, 0xFF222222, 0xFF2A2A2A,
        0x00000000, 0x00000000),

    INSET("Inset",
        0xFF141414, 0xFFDDDDDD, 0xFF888888, 0xFF555555,
        0x7755AADD, 0xFFDDDDDD, 0xFF1A1A1A, 0xFF2A2A2A,
        0xFF1A1A1A, 0xFF555555, 0xFFBBBBBB,
        0xFF181818, 0xFF181818, 0x60181818, 0x00181818,
        0xFFFFAA33, 0x15FFFFFF, 0x22FFFFFF,
        0x00000000, 0x00000000),

    FROSTED("Frosted",
        0xB30F0F14, 0xFFEEEEEE, 0xFF999999, 0xFF666666,
        0x7755AADD, 0xFFEEEEEE, 0x33000000, 0x55000000,
        0x33000000, 0x66FFFFFF, 0xCCFFFFFF,
        0x00000000, 0x00000000, 0x40000000, 0x00000000,
        0xFFFFBB55, 0x0AFFFFFF, 0x17FFFFFF,
        0x00000000, 0x00000000),

    ACCENT_LINE("Accent Line",
        0xFF161616, 0xFFE0E0E0, 0xFF777777, 0xFF555555,
        0x6655AACC, 0xFFE0E0E0, 0xFF1A1A1A, 0xFF252525,
        0xFF1A1A1A, 0xFF555555, 0xFFAAAAAA,
        0xFF0C0C0C, 0xFF0C0C0C, 0x600C0C0C, 0x000C0C0C,
        0xFFFFAA33, 0xFF1E1E1E, 0xFF222222,
        0xFFFFAA33, 0xFF333333);

    private final String displayName;
    private final int panelBackground, textPrimary, textMuted, textDisabled;
    private final int selectionBackground, caretPrimary, buttonDisabled, buttonHover;
    private final int tabInactive, scrollbarThumbInactive, scrollbarThumbActive;
    private final int gradientStart, gradientEnd, fadeGradientTop, fadeGradientBottom;
    private final int amber, entryHover, entrySelected;
    private final int accentLineActive, accentLineInactive;

    Theme(String displayName,
          int panelBackground, int textPrimary, int textMuted, int textDisabled,
          int selectionBackground, int caretPrimary, int buttonDisabled, int buttonHover,
          int tabInactive, int scrollbarThumbInactive, int scrollbarThumbActive,
          int gradientStart, int gradientEnd, int fadeGradientTop, int fadeGradientBottom,
          int amber, int entryHover, int entrySelected,
          int accentLineActive, int accentLineInactive) {
        this.displayName = displayName;
        this.panelBackground = panelBackground;
        this.textPrimary = textPrimary;
        this.textMuted = textMuted;
        this.textDisabled = textDisabled;
        this.selectionBackground = selectionBackground;
        this.caretPrimary = caretPrimary;
        this.buttonDisabled = buttonDisabled;
        this.buttonHover = buttonHover;
        this.tabInactive = tabInactive;
        this.scrollbarThumbInactive = scrollbarThumbInactive;
        this.scrollbarThumbActive = scrollbarThumbActive;
        this.gradientStart = gradientStart;
        this.gradientEnd = gradientEnd;
        this.fadeGradientTop = fadeGradientTop;
        this.fadeGradientBottom = fadeGradientBottom;
        this.amber = amber;
        this.entryHover = entryHover;
        this.entrySelected = entrySelected;
        this.accentLineActive = accentLineActive;
        this.accentLineInactive = accentLineInactive;
    }

    public String displayName() {
        return displayName;
    }

    public void applyColors() {
        Colors.PANEL_BACKGROUND = panelBackground;
        Colors.TEXT_PRIMARY = textPrimary;
        Colors.TEXT_MUTED = textMuted;
        Colors.TEXT_DISABLED = textDisabled;
        Colors.SELECTION_BACKGROUND = selectionBackground;
        Colors.CARET_PRIMARY = caretPrimary;
        Colors.BUTTON_DISABLED = buttonDisabled;
        Colors.BUTTON_HOVER = buttonHover;
        Colors.TAB_INACTIVE = tabInactive;
        Colors.SCROLLBAR_THUMB_INACTIVE = scrollbarThumbInactive;
        Colors.SCROLLBAR_THUMB_ACTIVE = scrollbarThumbActive;
        Colors.GRADIENT_START = gradientStart;
        Colors.GRADIENT_END = gradientEnd;
        Colors.FADE_GRADIENT_TOP = fadeGradientTop;
        Colors.FADE_GRADIENT_BOTTOM = fadeGradientBottom;
        Colors.AMBER = amber;
        Colors.ENTRY_HOVER = entryHover;
        Colors.ENTRY_SELECTED = entrySelected;
        Colors.ACCENT_LINE_ACTIVE = accentLineActive;
        Colors.ACCENT_LINE_INACTIVE = accentLineInactive;
    }

    public Surface rootSurface() {
        return switch (this) {
            case VANILLA -> Surface.VANILLA_TRANSLUCENT;
            case FLAT -> Surface.flat(0xFF0E0E0E);
            case INSET -> Surface.flat(0xFF181818);
            case FROSTED -> Surface.BLANK;
            case ACCENT_LINE -> Surface.flat(0xFF0C0C0C);
        };
    }

    public Surface panelSurface() {
        return switch (this) {
            case VANILLA -> Surface.DARK_PANEL;
            case FLAT -> Surface.flat(0xFF1A1A1A);
            case INSET -> Surface.panelWithInset(2);
            case FROSTED -> frostedPanelSurface();
            case ACCENT_LINE -> accentLinePanelSurface();
        };
    }

    private static Surface frostedPanelSurface() {
        // blur() returns a lambda -- it won't throw here even if the GPU
        // doesn't support it. If blur renders as a no-op on some hardware,
        // the opaque flat layer (0xB3 alpha) keeps the UI usable.
        return Surface.blur(10f, 6f)
            .and(Surface.flat(0xB30F0F14))
            .and(Surface.outline(0x14FFFFFF));
    }

    private static Surface accentLinePanelSurface() {
        return (context, component) -> {
            int x = component.x();
            int y = component.y();
            int w = component.width();
            int h = component.height();
            // Background fill
            context.fill(x, y, x + w, y + h, 0xFF161616);
            // Accent stripe (visual only, no layout shift)
            context.fill(x, y, x + 3, y + h, Colors.AMBER);
        };
    }

    public Theme next() {
        Theme[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :client:classes`

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/helper/Theme.java
git commit -m "feat(theme): create Theme enum with 5 variants, palettes, and surfaces"
```

---

### Task 3: Add theme to DisquestsConfig

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfig.java`

- [ ] **Step 1: Add theme field, getter, setter, and serialization**

Add import at the top:
```java
import com.disqt.disquests.client.gui.helper.Theme;
```

Add after `private static int pinnedWidth = 200;`:
```java
private static Theme theme = Theme.VANILLA;
```

Add after `getPinnedWidth()`:
```java
public static Theme getTheme() { return theme; }

public static void setTheme(Theme t) { theme = t; }
```

In `load()`, after `pinnedWidth = ...`:
```java
if (data.theme != null) {
    try {
        theme = Theme.valueOf(data.theme);
    } catch (IllegalArgumentException e) {
        theme = Theme.VANILLA;
    }
}
```

In `save()`, after `data.pinnedWidth = pinnedWidth;`:
```java
data.theme = theme.name();
```

In `ConfigData`, add:
```java
String theme = "VANILLA";
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :client:classes`

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/helper/DisquestsConfig.java
git commit -m "feat(theme): persist theme selection in config.json"
```

---

### Task 4: Fix startup order and Colors.reload()

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/DisquestsClient.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/Colors.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/helper/ColorConfig.java`

- [ ] **Step 1: Fix init order in DisquestsClient.java**

Change the first two lines of `onInitializeClient()` from:

```java
ColorConfig.loadColors();
DisquestsConfig.load();
```

To:

```java
DisquestsConfig.load();
DisquestsConfig.getTheme().applyColors();
ColorConfig.loadColors();
```

- [ ] **Step 2: Update Colors.reload()**

In `Colors.java`, change `reload()` from:

```java
public static void reload() {
    ColorConfig.loadColors();
}
```

To:

```java
public static void reload() {
    DisquestsConfig.getTheme().applyColors();
    ColorConfig.loadColors();
}
```

No import needed -- `DisquestsConfig` is in the same package.

- [ ] **Step 3: Fix ColorConfig.saveDefaultColors() to always snapshot Vanilla**

In `ColorConfig.java`, change `loadColors()` to save Vanilla baseline when no file exists:

Replace:
```java
} else {
    saveDefaultColors();
}
```

With:
```java
} else {
    saveVanillaDefaults();
}
```

Add the new method (alongside the existing `saveDefaultColors`):
```java
private static void saveVanillaDefaults() {
    // Always snapshot Vanilla palette as defaults, regardless of active theme
    Theme activeTheme = DisquestsConfig.getTheme();
    Theme.VANILLA.applyColors();
    saveDefaultColors();
    activeTheme.applyColors();
}
```

Add import:
```java
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.helper.Theme;
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :client:classes`

- [ ] **Step 5: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/DisquestsClient.java \
       client/src/main/java/com/disqt/disquests/client/gui/helper/Colors.java \
       client/src/main/java/com/disqt/disquests/client/gui/helper/ColorConfig.java
git commit -m "feat(theme): fix startup order, reload, and Vanilla default snapshot"
```

---

### Task 5: Add surface helpers to DisquestsBaseScreen

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java`

- [ ] **Step 1: Add helper methods**

Add imports:
```java
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.helper.Theme;
import io.wispforest.owo.ui.core.ParentComponent;
```

Add after the `charTyped` method:

```java
protected void applyThemeRoot(FlowLayout root) {
    root.surface(DisquestsConfig.getTheme().rootSurface());
}

protected void applyThemePanel(ParentComponent component) {
    component.surface(DisquestsConfig.getTheme().panelSurface());
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java
git commit -m "feat(theme): add surface helper methods to DisquestsBaseScreen"
```

---

### Task 6: Theme QuestEntryComponent

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java`

- [ ] **Step 1: Replace hardcoded highlight colors with Colors fields**

In the `draw()` method, find:

```java
if (selected) {
    context.fill(entryX, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, 0x44FFFFFF);
} else if (hovered) {
    context.fill(entryX, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, 0x22FFFFFF);
}
```

Replace with:

```java
if (selected) {
    context.fill(entryX, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, Colors.ENTRY_SELECTED);
} else if (hovered) {
    context.fill(entryX, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, Colors.ENTRY_HOVER);
}
```

- [ ] **Step 2: Add accent line rendering**

After the hover/selection block (and before `// --- Row 1:`), add:

```java
// Accent line (no-op when ACCENT_LINE_ACTIVE is transparent)
if (Colors.ACCENT_LINE_ACTIVE != 0x00000000) {
    int stripeColor = selected ? Colors.ACCENT_LINE_ACTIVE : Colors.ACCENT_LINE_INACTIVE;
    context.fill(entryX, entryY, entryX + 2, entryY + ENTRY_HEIGHT, stripeColor);
}
```

- [ ] **Step 3: Add inset bevel for selected entries**

Add import at top of file:
```java
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.helper.Theme;
```

After the selection fill block, add the inset bevel (draws on top of the fill):

```java
// Inset bevel for selected entries
if (selected && DisquestsConfig.getTheme() == Theme.INSET) {
    context.fill(entryX, entryY, entryX + entryWidth, entryY + 1, 0xFF0A0A0A);
    context.fill(entryX, entryY, entryX + 1, entryY + ENTRY_HEIGHT, 0xFF0A0A0A);
    context.fill(entryX, entryY + ENTRY_HEIGHT - 1, entryX + entryWidth, entryY + ENTRY_HEIGHT, 0xFF2A2A2A);
    context.fill(entryX + entryWidth - 1, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, 0xFF2A2A2A);
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :client:classes`

- [ ] **Step 5: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java
git commit -m "feat(theme): use Colors.ENTRY_* fields, add accent line and inset bevel"
```

---

### Task 7: Apply theme surfaces to all screens

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ConfirmScreen.java`

For each screen, add surface calls at the **start** of `build(FlowLayout root)`. The XML `<surface>` elements stay in place as the Vanilla baseline; code-side `.surface()` calls override them.

Note: `quest-scroll` and `contributor-scroll` are `<scroll>` elements, which map to `ScrollContainer`, not `FlowLayout`. Use `ParentComponent.class` in `childById` for these. Import `io.wispforest.owo.ui.core.ParentComponent`.

- [ ] **Step 1: MainScreen**

At the start of `build()`, add:

```java
applyThemeRoot(root);
applyThemePanel(root.childById(ParentComponent.class, "quest-scroll"));
```

Import: `import io.wispforest.owo.ui.core.ParentComponent;`

- [ ] **Step 2: QuestScreen**

QuestScreen uses two XML models (view and edit). Add at the start of `build()`:

```java
applyThemeRoot(root);
```

Then apply panel surfaces to all components with `<surface>` in the XML. Use `ParentComponent.class` and null-safe checks since IDs differ between view/edit:

```java
// View mode panels
ParentComponent contentScroll = root.childById(ParentComponent.class, "content-scroll");
if (contentScroll != null) applyThemePanel(contentScroll);
ParentComponent metadataRow = root.childById(ParentComponent.class, "metadata-row");
if (metadataRow != null) applyThemePanel(metadataRow);

// Edit mode panels
ParentComponent titleRow = root.childById(ParentComponent.class, "title-row");
if (titleRow != null) applyThemePanel(titleRow);
ParentComponent editorPanel = root.childById(ParentComponent.class, "editor-panel");
if (editorPanel != null) applyThemePanel(editorPanel);
ParentComponent formattingPanel = root.childById(ParentComponent.class, "formatting-panel");
if (formattingPanel != null) applyThemePanel(formattingPanel);
ParentComponent coordsSection = root.childById(ParentComponent.class, "coords-section");
if (coordsSection != null) applyThemePanel(coordsSection);
```

Import: `import io.wispforest.owo.ui.core.ParentComponent;`

- [ ] **Step 3: ContributorScreen**

At the start of `build()`:

```java
applyThemeRoot(root);
applyThemePanel(root.childById(ParentComponent.class, "contributor-scroll"));
applyThemePanel(root.childById(ParentComponent.class, "pending-list"));
```

Import: `import io.wispforest.owo.ui.core.ParentComponent;`

- [ ] **Step 4: ConfirmScreen**

At the start of `build()`:

```java
applyThemeRoot(root);
applyThemePanel(root.childById(FlowLayout.class, "panel"));
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :client:classes`

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java \
       client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java \
       client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java \
       client/src/main/java/com/disqt/disquests/client/gui/screen/ConfirmScreen.java
git commit -m "feat(theme): apply theme surfaces to all screens"
```

---

### Task 8: Add theme cycle button to ConfigScreen

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ConfigScreen.java`
- Modify: `client/src/main/resources/assets/disquests/owo_ui/config_screen.xml`

- [ ] **Step 1: Add theme button row to XML**

In `config_screen.xml`, add a new row inside the `<children>` of the `panel` flow-layout, **before** the `slider-row`:

```xml
<!-- Theme selector -->
<flow-layout direction="horizontal" id="theme-row">
    <sizing>
        <horizontal method="content"/>
        <vertical method="content"/>
    </sizing>
    <vertical-alignment>center</vertical-alignment>
    <gap>8</gap>
    <children>
        <label id="theme-label">
            <text>Theme:</text>
            <shadow>true</shadow>
        </label>
        <button id="btn-theme">
            <text>Vanilla</text>
            <sizing>
                <horizontal method="fixed">100</horizontal>
                <vertical method="fixed">20</vertical>
            </sizing>
        </button>
    </children>
</flow-layout>
```

- [ ] **Step 2: Wire theme button in ConfigScreen.java**

Add imports:
```java
import com.disqt.disquests.client.gui.helper.Theme;
import com.disqt.disquests.client.gui.helper.ColorConfig;
```

The key problem: reopening the screen (for surface preview) creates a new ConfigScreen instance, losing `originalTheme`. Use a static field to track the theme the user had when they first opened the config screen:

```java
private static Theme originalThemeBeforeConfig = null;
```

Add instance field after `private int pinnedWidth;`:
```java
private Theme selectedTheme;
```

In the constructor, after `this.pinnedWidth = ...`:
```java
this.selectedTheme = DisquestsConfig.getTheme();
if (originalThemeBeforeConfig == null) {
    originalThemeBeforeConfig = this.selectedTheme;
}
```

At the start of `build()`, apply surfaces:
```java
applyThemeRoot(root);
applyThemePanel(root.childById(FlowLayout.class, "panel"));
```

Wire theme button (cycles and reopens for surface preview):
```java
ButtonComponent themeBtn = root.childById(ButtonComponent.class, "btn-theme");
themeBtn.setMessage(Text.literal(selectedTheme.displayName()));
themeBtn.onPress(b -> {
    Theme next = selectedTheme.next();
    DisquestsConfig.setTheme(next);
    next.applyColors();
    ColorConfig.loadColors();
    this.client.setScreen(new ConfigScreen(this.parent));
});
```

Wire Save (persists and clears tracker):
```java
root.childById(ButtonComponent.class, "btn-save")
    .onPress(b -> {
        DisquestsConfig.setPinnedWidth(pinnedWidth);
        DisquestsConfig.save();
        originalThemeBeforeConfig = null;
        this.close();
    });
```

Wire Cancel (restores original and clears tracker):
```java
root.childById(ButtonComponent.class, "btn-cancel")
    .onPress(b -> {
        if (originalThemeBeforeConfig != null) {
            DisquestsConfig.setTheme(originalThemeBeforeConfig);
            originalThemeBeforeConfig.applyColors();
            ColorConfig.loadColors();
        }
        originalThemeBeforeConfig = null;
        this.close();
    });
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :client:classes`

- [ ] **Step 4: Visual test**

Run `./gradlew :client:runClient`. Open the config screen (ModMenu or in-game). Click the theme button to cycle through all 5 themes. Verify:
- Colors change on each screen
- Surfaces change (panel style differs per theme)
- Cancel restores the original theme
- Save persists the theme across game restart
- Accent Line theme shows colored left stripes on quest entries
- Frosted theme shows blur (or opaque fallback)
- Inset theme shows recessed panels with beveled selection highlights

- [ ] **Step 5: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/ConfigScreen.java \
       client/src/main/resources/assets/disquests/owo_ui/config_screen.xml
git commit -m "feat(theme): add theme cycle button to ConfigScreen with live preview"
```

---

### Task 9: E2E tests

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java`

The existing `testConfigScreen` test opens the ConfigScreen and verifies it renders. Extend it to test theme cycling and cancel/save behavior.

- [ ] **Step 1: Add theme cycling test**

Add a new test method and call it from `runTest()` (after `testConfigScreen`):

```java
private void testThemeSwitcher(ClientGameTestContext context) {
    Theme originalTheme = DisquestsConfig.getTheme();

    // Open config screen
    context.setScreen(() -> new ConfigScreen(null));
    context.waitForScreen(ConfigScreen.class);
    context.waitTick();

    // Click the theme button to cycle to next theme
    context.runOnClient(client -> {
        ConfigScreen screen = (ConfigScreen) client.currentScreen;
        // The theme button should exist
        if (screen == null) throw new AssertionError("ConfigScreen should be open");
    });

    // Verify theme is still the original (screen opened, nothing clicked yet)
    Theme afterOpen = DisquestsConfig.getTheme();
    if (afterOpen != originalTheme) {
        throw new AssertionError("Theme should not change on open: expected "
            + originalTheme + " but got " + afterOpen);
    }

    // Close without saving
    context.setScreen(() -> null);
    context.waitTick();

    // Verify theme reverted (cancel behavior)
    Theme afterClose = DisquestsConfig.getTheme();
    if (afterClose != originalTheme) {
        throw new AssertionError("Theme should revert on close: expected "
            + originalTheme + " but got " + afterClose);
    }
}
```

Add imports at the top of the file:
```java
import com.disqt.disquests.client.gui.helper.Theme;
```

- [ ] **Step 2: Add theme persistence test**

```java
private void testThemePersistence(ClientGameTestContext context) {
    Theme originalTheme = DisquestsConfig.getTheme();

    // Programmatically set a different theme and save
    DisquestsConfig.setTheme(Theme.FLAT);
    Theme.FLAT.applyColors();
    DisquestsConfig.save();

    // Verify Colors were updated
    if (Colors.TEXT_PRIMARY != 0xFFE0E0E0) {
        throw new AssertionError("FLAT theme TEXT_PRIMARY should be 0xFFE0E0E0, got "
            + Integer.toHexString(Colors.TEXT_PRIMARY));
    }

    // Reload config from disk
    DisquestsConfig.load();
    if (DisquestsConfig.getTheme() != Theme.FLAT) {
        throw new AssertionError("Theme should persist as FLAT after reload, got "
            + DisquestsConfig.getTheme());
    }

    // Restore original
    DisquestsConfig.setTheme(originalTheme);
    originalTheme.applyColors();
    DisquestsConfig.save();
}
```

Add import:
```java
import com.disqt.disquests.client.gui.helper.Colors;
```

- [ ] **Step 3: Add theme surface rendering test**

Verify that each theme can open the MainScreen without crashing (surfaces apply cleanly):

```java
private void testAllThemesRenderMainScreen(ClientGameTestContext context) {
    Quest quest = createTestQuest();
    ClientCache.setMyQuests(List.of(quest));

    for (Theme theme : Theme.values()) {
        DisquestsConfig.setTheme(theme);
        theme.applyColors();

        context.setScreen(() -> new MainScreen());
        context.waitForScreen(MainScreen.class);
        context.waitTick();

        boolean onMainScreen = context.computeOnClient(client ->
            client.currentScreen instanceof MainScreen);
        if (!onMainScreen) {
            throw new AssertionError("MainScreen should render with theme: " + theme);
        }

        context.setScreen(() -> null);
        context.waitTick();
    }

    // Restore default
    DisquestsConfig.setTheme(Theme.VANILLA);
    Theme.VANILLA.applyColors();
}
```

- [ ] **Step 4: Register tests in runTest()**

In `runTest()`, add calls after the existing `testConfigScreen(context);`:

```java
testThemeSwitcher(context);
testThemePersistence(context);
testAllThemesRenderMainScreen(context);
```

- [ ] **Step 5: Run E2E tests**

Run: `./gradlew :client:runClientGameTest`

All tests should pass. The theme tests verify:
- Config screen opens without theme change
- Theme reverts on cancel/close
- Theme persists to disk and survives reload
- All 5 themes render the MainScreen without crashing

- [ ] **Step 6: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java
git commit -m "test(theme): add E2E tests for theme switching, persistence, and rendering"
```

---

### Task 10: Final verification

- [ ] **Step 1: Full build**

Run: `./gradlew build`

Verify no compilation errors across all modules.

- [ ] **Step 2: Visual walkthrough**

With `./gradlew :paper:runServer` running, launch `./gradlew :client:runClient` and test each theme on every screen:

| Screen | What to check |
|--------|--------------|
| MainScreen | Root surface, quest scroll panel, entry highlights, accent lines |
| QuestScreen (view) | Root surface, content panels |
| QuestScreen (edit) | Root surface, editor panel, formatting panel |
| ContributorScreen | Root surface, contributor scroll, pending list |
| ConfirmScreen | Root surface, dialog panel |
| ConfigScreen | Root surface, config panel, theme button cycles |

For each theme specifically check:
- **Vanilla**: looks identical to before (no visual regression)
- **Flat**: no borders anywhere, solid fills
- **Inset**: recessed panels, beveled selection on quest entries
- **Frosted**: blur or opaque fallback, game visible behind
- **Accent Line**: amber stripes on panels and entries, bright on selected

- [ ] **Step 3: Persistence test**

1. Set theme to Frosted, save, close game
2. Relaunch -- theme should still be Frosted
3. Open config, cancel -- should remain Frosted
4. Open config, switch to Flat, cancel -- should revert to Frosted

- [ ] **Step 4: colors.json override test**

1. Set theme to Flat
2. Edit `config/disquests/colors.json`, change `TEXT_PRIMARY` to `"rgba(255, 100, 100, 1.000)"`
3. Reload (or restart) -- text should be red while rest of Flat theme is intact
