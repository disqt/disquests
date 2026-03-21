# Theme Switcher Design

## Summary

Add a theme system to Disquests that lets players choose from 5 visual styles in the ModMenu config screen. Each theme changes colors, surfaces, and panel structure while preserving identical UX and layout. The selected theme persists across sessions.

## Themes

### 1. Vanilla (default)

The current look. Classic Minecraft beveled panels.

- **Root surface:** `VANILLA_TRANSLUCENT`
- **Panel surface:** `DARK_PANEL`
- **Entry highlight:** white overlay (`0x44FFFFFF` selected, `0x22FFFFFF` hover)

| Field | Value |
|-------|-------|
| PANEL_BACKGROUND | `0x77000000` |
| TEXT_PRIMARY | `0xFFFFFFFF` |
| TEXT_MUTED | `0xFFCCCCCC` |
| TEXT_DISABLED | `0xFF888888` |
| SELECTION_BACKGROUND | `0x8855AADD` |
| CARET_PRIMARY | `0xFFFFFFFF` |
| BUTTON_DISABLED | `0x44000000` |
| BUTTON_HOVER | `0xAA000000` |
| TAB_INACTIVE | `0x44000000` |
| SCROLLBAR_THUMB_INACTIVE | `0x88FFFFFF` |
| SCROLLBAR_THUMB_ACTIVE | `0xFFFFFFFF` |
| GRADIENT_START | `0xFF101010` |
| GRADIENT_END | `0xFF101010` |
| FADE_GRADIENT_TOP | `0x60000000` |
| FADE_GRADIENT_BOTTOM | `0x00000000` |
| AMBER | `0xFFFFAA33` |
| ENTRY_HOVER | `0x22FFFFFF` |
| ENTRY_SELECTED | `0x44FFFFFF` |
| ACCENT_LINE_ACTIVE | `0x00000000` |
| ACCENT_LINE_INACTIVE | `0x00000000` |

### 2. Flat

No borders, no bevels. Solid color fills only.

- **Root surface:** `flat(0xFF0E0E0E)`
- **Panel surface:** `flat(0xFF1A1A1A)`

| Field | Value |
|-------|-------|
| PANEL_BACKGROUND | `0xFF1A1A1A` |
| TEXT_PRIMARY | `0xFFE0E0E0` |
| TEXT_MUTED | `0xFF777777` |
| TEXT_DISABLED | `0xFF555555` |
| SELECTION_BACKGROUND | `0x6650AACC` |
| CARET_PRIMARY | `0xFFE0E0E0` |
| BUTTON_DISABLED | `0xFF1A1A1A` |
| BUTTON_HOVER | `0xFF2A2A2A` |
| TAB_INACTIVE | `0xFF1A1A1A` |
| SCROLLBAR_THUMB_INACTIVE | `0xFF555555` |
| SCROLLBAR_THUMB_ACTIVE | `0xFFAAAAAA` |
| GRADIENT_START | `0xFF0E0E0E` |
| GRADIENT_END | `0xFF0E0E0E` |
| FADE_GRADIENT_TOP | `0x600E0E0E` |
| FADE_GRADIENT_BOTTOM | `0x000E0E0E` |
| AMBER | `0xFFFFAA33` |
| ENTRY_HOVER | `0xFF222222` |
| ENTRY_SELECTED | `0xFF2A2A2A` |
| ACCENT_LINE_ACTIVE | `0x00000000` |
| ACCENT_LINE_INACTIVE | `0x00000000` |

### 3. Inset

Panels feel pressed into the screen. Reversed bevels create recessed depth.

- **Root surface:** `flat(0xFF181818)`
- **Panel surface:** `PANEL_INSET` or `panelWithInset(2)`

| Field | Value |
|-------|-------|
| PANEL_BACKGROUND | `0xFF141414` |
| TEXT_PRIMARY | `0xFFDDDDDD` |
| TEXT_MUTED | `0xFF888888` |
| TEXT_DISABLED | `0xFF555555` |
| SELECTION_BACKGROUND | `0x7755AADD` |
| CARET_PRIMARY | `0xFFDDDDDD` |
| BUTTON_DISABLED | `0xFF1A1A1A` |
| BUTTON_HOVER | `0xFF2A2A2A` |
| TAB_INACTIVE | `0xFF1A1A1A` |
| SCROLLBAR_THUMB_INACTIVE | `0xFF555555` |
| SCROLLBAR_THUMB_ACTIVE | `0xFFBBBBBB` |
| GRADIENT_START | `0xFF181818` |
| GRADIENT_END | `0xFF181818` |
| FADE_GRADIENT_TOP | `0x60181818` |
| FADE_GRADIENT_BOTTOM | `0x00181818` |
| AMBER | `0xFFFFAA33` |
| ENTRY_HOVER | `0x15FFFFFF` |
| ENTRY_SELECTED | `0x22FFFFFF` |
| ACCENT_LINE_ACTIVE | `0x00000000` |
| ACCENT_LINE_INACTIVE | `0x00000000` |

Entry selection highlight for Inset uses beveled edges drawn as four separate `context.fill()` calls (dark top-left, light bottom-right) to match the recessed panel style, in addition to the fill overlay.

### 4. Frosted

Semi-transparent glass panels with blur. The game world shows through.

- **Root surface:** `Surface.BLANK` (game renders behind)
- **Panel surface:** `blur(10f, 6f).and(flat(0xB30F0F14)).and(outline(0x14FFFFFF))`
- **Blur fallback:** If blur is unavailable (unsupported GPU/driver), falls back to `flat(0xCC0F0F14).and(outline(0x14FFFFFF))` -- a more opaque flat fill that remains usable without the glass effect.

| Field | Value |
|-------|-------|
| PANEL_BACKGROUND | `0xB30F0F14` |
| TEXT_PRIMARY | `0xFFEEEEEE` |
| TEXT_MUTED | `0xFF999999` |
| TEXT_DISABLED | `0xFF666666` |
| SELECTION_BACKGROUND | `0x7755AADD` |
| CARET_PRIMARY | `0xFFEEEEEE` |
| BUTTON_DISABLED | `0x33000000` |
| BUTTON_HOVER | `0x55000000` |
| TAB_INACTIVE | `0x33000000` |
| SCROLLBAR_THUMB_INACTIVE | `0x66FFFFFF` |
| SCROLLBAR_THUMB_ACTIVE | `0xCCFFFFFF` |
| GRADIENT_START | `0x00000000` |
| GRADIENT_END | `0x00000000` |
| FADE_GRADIENT_TOP | `0x40000000` |
| FADE_GRADIENT_BOTTOM | `0x00000000` |
| AMBER | `0xFFFFBB55` |
| ENTRY_HOVER | `0x0AFFFFFF` |
| ENTRY_SELECTED | `0x17FFFFFF` |
| ACCENT_LINE_ACTIVE | `0x00000000` |
| ACCENT_LINE_INACTIVE | `0x00000000` |

### 5. Accent Line

Flat dark panels with a colored accent stripe on the left edge. IDE/dashboard style.

- **Root surface:** `flat(0xFF0C0C0C)`
- **Panel surface:** `flat(0xFF161616)` (accent stripe drawn in custom surface, not as padding)

| Field | Value |
|-------|-------|
| PANEL_BACKGROUND | `0xFF161616` |
| TEXT_PRIMARY | `0xFFE0E0E0` |
| TEXT_MUTED | `0xFF777777` |
| TEXT_DISABLED | `0xFF555555` |
| SELECTION_BACKGROUND | `0x6655AACC` |
| CARET_PRIMARY | `0xFFE0E0E0` |
| BUTTON_DISABLED | `0xFF1A1A1A` |
| BUTTON_HOVER | `0xFF252525` |
| TAB_INACTIVE | `0xFF1A1A1A` |
| SCROLLBAR_THUMB_INACTIVE | `0xFF555555` |
| SCROLLBAR_THUMB_ACTIVE | `0xFFAAAAAA` |
| GRADIENT_START | `0xFF0C0C0C` |
| GRADIENT_END | `0xFF0C0C0C` |
| FADE_GRADIENT_TOP | `0x600C0C0C` |
| FADE_GRADIENT_BOTTOM | `0x000C0C0C` |
| AMBER | `0xFFFFAA33` |
| ENTRY_HOVER | `0xFF1E1E1E` |
| ENTRY_SELECTED | `0xFF222222` |
| ACCENT_LINE_ACTIVE | `0xFFFFAA33` |
| ACCENT_LINE_INACTIVE | `0xFF333333` |

## Architecture

### Theme enum

`com.disqt.disquests.client.gui.helper.Theme`

```java
public enum Theme {
    VANILLA, FLAT, INSET, FROSTED, ACCENT_LINE;

    public void applyColors();      // overwrites all Colors.* fields
    public Surface rootSurface();   // background behind everything
    public Surface panelSurface();  // main content panels (scroll areas, dialogs)
    public String displayName();    // "Vanilla", "Flat", etc.
}
```

Each enum variant holds a complete color palette as final fields. `applyColors()` bulk-overwrites every `Colors.*` static field from those values.

### Color system changes

**Promote hardcoded values to `Colors.java`:**

| New field | Current hardcoded location | Purpose |
|-----------|---------------------------|---------|
| `ENTRY_HOVER` | `0x22FFFFFF` in QuestEntryComponent | Quest entry hover overlay |
| `ENTRY_SELECTED` | `0x44FFFFFF` in QuestEntryComponent | Quest entry selected overlay |
| `ACCENT_LINE_ACTIVE` | (new) | Accent stripe on selected entry |
| `ACCENT_LINE_INACTIVE` | (new) | Accent stripe on unselected entry |

These fields exist in `Colors.java` for all themes. Themes that don't use accent lines set both to `0x00000000` (fully transparent) so the draw call is a no-op.

### Surface application

All screens apply theme surfaces in `build()`, overriding any XML-defined surfaces. XML surfaces remain in the layout files as the Vanilla baseline and as documentation of structure.

Each screen must apply surfaces to **all** paneled components, not just the top-level container. The components that need surface overrides per screen:

| Screen | Component IDs needing surface override |
|--------|---------------------------------------|
| MainScreen | `root`, `quest-scroll` |
| QuestScreen (view) | `root`, `quest-panel` (scroll container), `meta-section` |
| QuestScreen (edit) | `root`, `editor-panel`, `formatting-panel`, `coords-section` |
| ContributorScreen | `root`, `contributor-scroll`, `pending-list` |
| ConfirmScreen | `root`, `panel` |
| ConfigScreen | `root`, `panel` |

To reduce duplication, `DisquestsBaseScreen` provides a helper:

```java
protected void applyThemeSurfaces(FlowLayout root) {
    Theme theme = DisquestsConfig.getTheme();
    root.surface(theme.rootSurface());
}

protected void applyPanelSurface(ParentComponent component) {
    component.surface(DisquestsConfig.getTheme().panelSurface());
}
```

### Accent Line rendering

The accent stripe draws as a visual overlay, never as padding or margin. Content positioning stays identical across all themes.

In `QuestEntryComponent.draw()`:

```java
// Draw accent line (before text, after background)
if (Colors.ACCENT_LINE_ACTIVE != 0x00000000) {
    int stripeColor = selected ? Colors.ACCENT_LINE_ACTIVE : Colors.ACCENT_LINE_INACTIVE;
    context.fill(entryX, entryY, entryX + 2, entryY + ENTRY_HEIGHT, stripeColor);
}
```

For panel-level accent lines (the main scroll panel), a custom `Surface` implementation draws the stripe:

```java
public static Surface accentLineSurface(int bgColor, int stripeColor) {
    return (context, component) -> {
        context.fill(component.x(), component.y(),
            component.x() + component.width(), component.y() + component.height(), bgColor);
        context.fill(component.x(), component.y(),
            component.x() + 3, component.y() + component.height(), stripeColor);
    };
}
```

### Inset entry highlight

The Inset theme draws beveled selection highlights using four edge rectangles instead of a single fill:

```java
// Dark edges (top, left) + light edges (bottom, right)
context.fill(x, y, x + w, y + 1, 0xFF0A0A0A);         // top
context.fill(x, y, x + 1, y + h, 0xFF0A0A0A);         // left
context.fill(x, y + h - 1, x + w, y + h, 0xFF2A2A2A); // bottom
context.fill(x + w - 1, y, x + w, y + h, 0xFF2A2A2A); // right
```

This runs in `QuestEntryComponent.draw()` when the entry is selected and the active theme is `INSET`. Other themes use the standard `context.fill()` overlay.

### Config integration

**`DisquestsConfig.java` additions:**

```java
private static Theme theme = Theme.VANILLA;

public static Theme getTheme() { return theme; }
public static void setTheme(Theme t) { theme = t; }
```

**`ConfigData` additions:**

```java
String theme = "VANILLA";
```

On load, `Theme.valueOf(data.theme)` with fallback to `VANILLA`. On save, `theme.name()`.

### Startup order

The current init order in `DisquestsClient.onInitializeClient()` must change. The correct sequence:

1. `DisquestsConfig.load()` -- reads `theme` from `config.json`
2. `DisquestsConfig.getTheme().applyColors()` -- overwrites `Colors.*` with theme palette
3. `ColorConfig.loadColors()` -- layers `colors.json` overrides on top

`ColorConfig.saveDefaultColors()` (called when `colors.json` doesn't exist) must always snapshot the **Vanilla** palette, not the active theme. Before saving defaults, temporarily apply `Theme.VANILLA.applyColors()`, snapshot, then re-apply the active theme.

### Colors.reload()

`Colors.reload()` must apply the full theme-then-override sequence:

```java
public static void reload() {
    DisquestsConfig.getTheme().applyColors();
    ColorConfig.loadColors();
}
```

### ConfigScreen UI

Add a theme cycle button above the existing pinned width slider:

```
[< Vanilla >]        <- click cycles through themes
Pinned Width: [====] 200
[Save] [Cancel]
```

The button label shows the current theme name. Clicking cycles to the next enum value, calls `theme.applyColors()` + `ColorConfig.loadColors()`, then calls `this.clearAndInit()` to rebuild the screen with updated surfaces. The theme change is only persisted to disk when the user clicks Save.

### ConfigScreen live preview

On theme cycle, the ConfigScreen:

1. Calls `newTheme.applyColors()` to update color fields immediately
2. Calls `ColorConfig.loadColors()` to layer overrides
3. Calls `this.clearAndInit()` to re-run `build()` with new surfaces

If the user clicks Cancel, the original theme is restored via the same sequence.

## Files to modify

| File | Change |
|------|--------|
| `client/.../gui/helper/Theme.java` | **New.** Enum with 5 themes, complete color palettes, surface factories |
| `client/.../gui/helper/Colors.java` | Add `ENTRY_HOVER`, `ENTRY_SELECTED`, `ACCENT_LINE_ACTIVE`, `ACCENT_LINE_INACTIVE` |
| `client/.../gui/helper/DisquestsConfig.java` | Add `theme` field, getter/setter, serialize/deserialize |
| `client/.../gui/helper/ColorConfig.java` | `saveDefaultColors()` always snapshots Vanilla palette |
| `client/.../gui/screen/DisquestsBaseScreen.java` | Add `applyThemeSurfaces()` and `applyPanelSurface()` helpers |
| `client/.../gui/screen/MainScreen.java` | Call surface helpers in `build()`, use `Colors.ENTRY_*` |
| `client/.../gui/screen/QuestScreen.java` | Call surface helpers in `build()` |
| `client/.../gui/screen/ConfigScreen.java` | Add theme cycle button, live preview via `clearAndInit()` |
| `client/.../gui/screen/ContributorScreen.java` | Call surface helpers in `build()` |
| `client/.../gui/screen/ConfirmScreen.java` | Call surface helpers in `build()` |
| `client/.../gui/component/QuestEntryComponent.java` | Replace hardcoded hex with `Colors.ENTRY_*`, add accent line + inset bevel draw |
| `client/.../DisquestsClient.java` | Fix init order: config load -> theme apply -> color overrides |

XML layout files (`owo_ui/*.xml`) keep their `<surface>` elements as Vanilla baseline. Code-side `surface()` calls override them at runtime.

## Out of scope

- Custom user-created themes (the `colors.json` override system covers this)
- Per-screen theme overrides
- Animated theme transitions
- Custom button shapes or rounded corners
- Theme-specific textures or icons
- HUD overlay theming (HudPinRenderer uses its own color constants)
