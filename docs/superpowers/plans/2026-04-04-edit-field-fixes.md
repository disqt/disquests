# Edit Field Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three bugs in the quest edit screen: text cutoff on the right, missing scroll support, and bare URLs not rendered as clickable links.

**Architecture:** Issues 1 (text cutoff) and 2 (no scrolling) share a root cause: `MultiLineTextFieldWidget` is constructed with fixed 400x200 dimensions that never update to match the owo-ui container's actual resolved size. The fix makes dimensions mutable and syncs them in the owo-ui wrapper. Issue 3 (bare URLs) is a missing commonmark extension.

**Tech Stack:** owo-ui (BaseUIComponent), commonmark-java 0.27.1, Fabric 1.21.11

---

### Task 1: Make MultiLineTextFieldWidget dimensions dynamic

The widget's `width` and `height` fields are `public final int`, set once in the constructor. Word wrap uses `this.width - 10` to break lines, and scroll metrics use `this.height` to determine if content overflows. When the owo-ui container is narrower than 400px, wrapped lines extend past the visible area, cutting off characters. When the container height differs from 200px, scroll calculations are wrong.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/MultiLineTextFieldWidget.java:31-34`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/TextFieldComponent.java`

- [ ] **Step 1: Make width/height non-final and add resize method**

In `MultiLineTextFieldWidget.java`, change the field declarations from:

```java
public final int x;
public final int y;
public final int width;
public final int height;
```

To:

```java
public final int x;
public final int y;
public int width;
public int height;
```

Then add this method after the constructor (after line ~157):

```java
/**
 * Updates the widget dimensions and rebuilds display lines for the new width.
 * Called by TextFieldComponent when owo-ui resolves the actual container size.
 */
public void resize(int newWidth, int newHeight) {
  if (this.width == newWidth && this.height == newHeight) return;
  this.width = newWidth;
  this.height = newHeight;
  rebuildDisplayLines();
}
```

- [ ] **Step 2: Sync delegate dimensions in TextFieldComponent.draw()**

In `TextFieldComponent.java`, add dimension syncing at the top of the `draw()` method, before `computeOffset()`:

```java
@Override
public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
  // Sync delegate dimensions with owo-ui resolved size
  delegate.resize(this.width(), this.height());

  computeOffset();
  // ... rest unchanged
}
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew :client:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/widget/MultiLineTextFieldWidget.java
git add client/src/main/java/com/disqt/disquests/client/gui/component/TextFieldComponent.java
git commit -m "fix: sync text field dimensions with owo-ui container

MultiLineTextFieldWidget was created with fixed 400x200 dimensions that
never updated to match the actual owo-ui layout size. This caused:
- Word wrap using wrong width, cutting off text on the right edge
- Scroll metrics using wrong height, preventing scrolling"
```

---

### Task 2: Add bare URL auto-linking in markdown renderer

Bare URLs like `https://youtu.be/Wxuh8anF10I` are parsed as plain `Text` nodes by commonmark-java, so they never get a `ClickEvent.OpenUrl` attached. The `commonmark-ext-autolink` extension detects bare URLs during parsing and wraps them in `Link` nodes, which the existing `appendInline()` handler (line 352-363 of MarkdownRenderer.java) already renders as clickable aqua-colored text.

**Files:**
- Modify: `client/build.gradle.kts:69-74` (dependencies block)
- Modify: `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java:13-27`

- [ ] **Step 1: Add autolink dependency**

In `client/build.gradle.kts`, add the autolink extension alongside the existing commonmark dependencies. Find:

```kotlin
    implementation("org.commonmark:commonmark:0.27.1")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.27.1")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.27.1")
    include("org.commonmark:commonmark:0.27.1")
    include("org.commonmark:commonmark-ext-gfm-strikethrough:0.27.1")
    include("org.commonmark:commonmark-ext-task-list-items:0.27.1")
```

Add after `task-list-items` lines:

```kotlin
    implementation("org.commonmark:commonmark-ext-autolink:0.27.1")
    include("org.commonmark:commonmark-ext-autolink:0.27.1")
```

- [ ] **Step 2: Register AutolinkExtension in the parser**

In `MarkdownRenderer.java`, add the import:

```java
import org.commonmark.ext.autolink.AutolinkExtension;
```

Change the PARSER initialization from:

```java
private static final Parser PARSER =
    Parser.builder()
        .extensions(List.of(StrikethroughExtension.create(), TaskListItemsExtension.create()))
        .build();
```

To:

```java
private static final Parser PARSER =
    Parser.builder()
        .extensions(
            List.of(
                StrikethroughExtension.create(),
                TaskListItemsExtension.create(),
                AutolinkExtension.create()))
        .build();
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew :client:build`
Expected: BUILD SUCCESSFUL. The autolink extension produces standard `Link` nodes, so the existing link rendering code handles them without changes.

- [ ] **Step 4: Commit**

```bash
git add client/build.gradle.kts
git add client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java
git commit -m "feat: auto-link bare URLs in quest descriptions

Add commonmark-ext-autolink extension so bare URLs like
https://youtu.be/... are rendered as clickable links without
requiring explicit [text](url) markdown syntax."
```

---

### Task 3: Run E2E tests

Both changes affect the quest edit/view screens. Run the full E2E suite to catch regressions.

- [ ] **Step 1: Run solo integration tests**

Run: `./gradlew :client:runSoloTests`
Expected: All tests pass. The `QuestContentJourney` tests exercise markdown rendering and the edit screen.

- [ ] **Step 2: Run duo integration tests**

Run: `./gradlew :client:runDuoTests`
Expected: All tests pass.
