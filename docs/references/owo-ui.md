# owo-ui Reference (v0.13.0 for MC 1.21.11)

Fabric GUI framework by Wisp Forest. Declarative component tree (React/Flutter-like). Two paradigms: code-driven (Java) or data-driven (XML with hot-reload).

**Web docs**: https://docs.wispforest.io/owo/ui/
**GitHub**: https://github.com/wisp-forest/owo-lib
**Maven**: https://maven.wispforest.io/io/wispforest/owo-lib/

## Setup

```kotlin
// build.gradle.kts
repositories { maven("https://maven.wispforest.io/releases/") }
dependencies {
    modImplementation("io.wispforest:owo-lib:${properties["owo_version"]}")
    include("io.wispforest:owo-sentinel:${properties["owo_version"]}")
}
```
```properties
# gradle.properties
owo_version=0.13.0+1.21.11
```

Runtime dependency -- players must install owo-lib separately. owo-sentinel (jar-in-jar) shows a friendly download dialog if missing.

## Screen Creation

**Code-driven**: Extend `BaseOwoScreen<FlowLayout>`:
```java
public class MyScreen extends BaseOwoScreen<FlowLayout> {
    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER);

        root.child(
            Containers.verticalFlow(Sizing.content(), Sizing.content())
                .child(Components.button(Text.literal("Click"), btn -> {}))
                .padding(Insets.of(10))
                .surface(Surface.DARK_PANEL)
        );
    }
}
```

**XML-driven**: Extend `BaseUIModelScreen<FlowLayout>`, XML at `assets/modid/owo_ui/name.xml`. Hot-reload with `Ctrl+F5` in-game.

**Embed in existing screen**: `OwoUIAdapter.create(this, Containers::verticalFlow)` -> `addDrawableChild(adapter)`.

## Sizing

- `Sizing.content()` -- fit content + padding
- `Sizing.fill(percentage)` -- % of parent's available space
- `Sizing.fixed(pixels)` -- exact pixel size

## Positioning

- `Positioning.layout()` -- default, participates in parent layout
- `Positioning.relative(x%, y%)` -- percentage-based (50/50 = centered)
- `Positioning.absolute(x, y)` -- pixel offset from parent origin

## Layout Components

| Component | Java | XML |
|-----------|------|-----|
| Vertical flow | `Containers.verticalFlow(hSize, vSize)` | `<flow-layout direction="vertical">` |
| Horizontal flow | `Containers.horizontalFlow(hSize, vSize)` | `<flow-layout direction="horizontal">` |
| Grid | `Containers.grid(hSize, vSize, rows, cols)` | `<grid-layout rows="2" columns="2">` |
| Scroll | `Containers.verticalScroll(hSize, vSize, child)` | `<scroll direction="vertical">` |
| Collapsible | `Containers.collapsible(hSize, vSize, Text, expanded)` | `<collapsible expanded="true">` |
| Draggable | `Containers.draggable(hSize, vSize, child)` | `<drag>` |
| Overlay | `Containers.overlay(child)` | Java only |

Flow layouts support `.gap(int)` for spacing between children.
Scroll container accepts a single child -- wrap multiple items in a flow layout.

## Interactive Components

| Component | Java | Key Methods |
|-----------|------|-------------|
| Button | `Components.button(Text, Consumer<ButtonComponent>)` | `.active(bool)` |
| Checkbox | `Components.checkbox(Text)` | `.checked(bool)` |
| Label | `Components.label(Text)` | `.color(Color)`, `.shadow(bool)`, `.maxWidth(int)` |
| Slider | `Components.slider(Sizing)` | `.value(double)`, `.message(Function)` |
| Dropdown | `Components.dropdown(Sizing)` | `.button()`, `.checkbox()`, `.nested()` |
| Text box | `Components.textBox(Sizing)` | standard text input |

## Surfaces (Panel Backgrounds)

| Surface | Description |
|---------|-------------|
| `Surface.VANILLA_TRANSLUCENT` | Dark translucent (like vanilla menus) |
| `Surface.DARK_PANEL` | Dark panel |
| `Surface.PANEL` | Standard panel |
| `Surface.TOOLTIP` | Tooltip style |

Chain: `Surface.PANEL.and(Surface.VANILLA_TRANSLUCENT)`

## Common Component Methods

```java
.sizing(Sizing h, Sizing v)       // set both axes
.margins(Insets.of(10))            // outer spacing
.padding(Insets.of(10))            // inner spacing (parents only)
.surface(Surface.DARK_PANEL)       // background (parents only)
.horizontalAlignment(HorizontalAlignment.CENTER)
.verticalAlignment(VerticalAlignment.CENTER)
.id("my-id")                       // for childById() lookup
.tooltip(Text.literal("hover"))    // hover tooltip
.gap(10)                           // child spacing (flow layouts)
```

## Templates (XML Reuse)

Define in `<templates>`, expand in `init()` (NOT `build()`):
```java
this.model.expandTemplate(FlowLayout.class, "name@modid:model", Map.of("key", "value"));
```
XML placeholders: `{{key}}`.

## Component Inspector

`Ctrl+Shift` on any owo screen -- hover to inspect type, ID, coordinates, size, margins, padding. `Alt+Shift` for global bounding box overlay.

## Key Imports

```java
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.*;
```

## Gotchas

1. **Type decay**: `.horizontalAlignment()` returns `ParentComponent`, not `FlowLayout`. Call `.child()` before styling methods.
2. **Templates in init(), not build()**: `this.uiAdapter` is null during `build()`.
3. **Scroll container**: single child only -- wrap in flow layout.
4. **Content sizing**: not all components have intrinsic size (throws exception).
5. **Padding chains in XML**: `<all>10</all><bottom>5</bottom>` = 10 on top/left/right, 5 on bottom.
6. **Surfaces chain in XML**: multiple surface elements stack in order.
