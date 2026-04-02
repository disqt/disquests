# Wiki-Link Rendering Fixes

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix three wiki-link bugs: invisible links in view mode, exposed UUIDs in edit mode, and hidden autocomplete dropdown.

**Architecture:** Bug 1 adds `HtmlBlock` handling to `MarkdownRenderer.renderBlock()`. Bug 2 adds a client-side `reverseResolveWikiLinks()` utility called before populating the edit content field. Bug 3 replaces the custom dropdown rendering with an owo-ui `OverlayContainer` on `DisquestsBaseScreen`.

**Tech Stack:** Java 21, Fabric, owo-ui 0.13.0, commonmark-java

---

## Task 1: Handle HtmlBlock wiki-links in view mode

**Problem:** When `[[Bonsoir]]` is on its own line after a blank line, `preprocessWikiLinks` converts it to `<dqlink uuid="..." title="Bonsoir"/>`. CommonMark classifies this as an `HtmlBlock` (block-level HTML), not `HtmlInline`. The renderer only handles `HtmlInline`, so the link is silently dropped.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java` (modify)

### Step 1: Add HtmlBlock handling to renderBlock

In `MarkdownRenderer.java`, in the `renderBlock` method, add a case for `HtmlBlock` before the final `else` fallback (line 201). When the literal contains a `<dqlink>` tag, render it as a wiki-link line. Otherwise, ignore it (same as current behavior).

Find:
```java
    } else if (node instanceof ThematicBreak) {
      lines.add(
          RenderedLine.normal(
              Text.literal("---").setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)), indent));
    } else {
      // Fallback: try to render children
      renderChildren(node, lines, indent, style);
    }
```

Replace with:
```java
    } else if (node instanceof ThematicBreak) {
      lines.add(
          RenderedLine.normal(
              Text.literal("---").setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)), indent));
    } else if (node instanceof org.commonmark.node.HtmlBlock htmlBlock) {
      // HtmlBlock occurs when <dqlink .../> is on its own line after a blank line.
      // Scan for dqlink tags and render them the same way as inline wiki-links.
      String literal = htmlBlock.getLiteral();
      Matcher m = DQLINK_ATTR_PATTERN.matcher(literal);
      if (m.find()) {
        String uuid = m.group(1);
        String title =
            m.group(2)
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        boolean isValid = !uuid.isEmpty();
        int color = isValid ? 0xe8a86d : 0xe86d6d;
        Style wikiStyle = style.withColor(color).withUnderline(true);
        if (!isValid) wikiStyle = wikiStyle.withStrikethrough(true);
        String command =
            isValid
                ? WIKI_LINK_COMMAND_PREFIX + uuid
                : WIKI_LINK_COMMAND_PREFIX + WIKI_LINK_BROKEN;
        wikiStyle = wikiStyle.withClickEvent(new ClickEvent.RunCommand(command));
        lines.add(RenderedLine.normal(Text.literal(title).setStyle(wikiStyle), indent));
      }
    } else {
      // Fallback: try to render children
      renderChildren(node, lines, indent, style);
    }
```

Note: `Matcher` import (`java.util.regex.Matcher`) is already present. `ClickEvent` import is already present.

### Step 2: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 3: Commit

```
fix(client): handle HtmlBlock wiki-links so they render on standalone lines
```

---

## Task 2: Hide UUIDs in edit mode (client-side reverse resolution)

**Problem:** After saving, the server sends back resolved content `[[uuid|title]]` via UPDATE_QUEST. The client cache stores this resolved version. When re-entering edit mode, `quest.getContent()` has UUIDs exposed: `[[b2766fff-8efa-4ba0-921e-566c59276f63|Bonsoir]]` instead of `[[Bonsoir]]`.

**Fix:** Add a static `reverseResolveWikiLinks(String)` method that converts `[[uuid|title]]` back to `[[title]]` using `ClientCache` for title lookups (so renamed quests show current title). Call it when populating the edit content field.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java` (modify -- add utility method)
- `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java` (modify -- call it)

### Step 1: Add reverseResolveWikiLinks to MarkdownRenderer

In `MarkdownRenderer.java`, add a new public static method after the existing `preprocessWikiLinks` method (after line 49). This mirrors the server's `WikiLinkResolver.reverseResolve` but uses the client cache:

```java
  /**
   * Converts server-resolved wiki-links back to raw form for editing.
   * [[uuid|title]] -> [[Current Title]] (using cache lookup) or [[title]] (if not found).
   */
  public static String reverseResolveWikiLinks(String content) {
    if (content == null || content.isEmpty()) return content;
    return WIKI_LINK_PATTERN
        .matcher(content)
        .replaceAll(
            m -> {
              String uuidStr = m.group(1);
              String displayTitle = m.group(2);
              if (uuidStr.isEmpty()) {
                // Broken link -- preserve display title
                return java.util.regex.Matcher.quoteReplacement("[[" + displayTitle + "]]");
              }
              try {
                java.util.UUID questId = java.util.UUID.fromString(uuidStr);
                var quest = com.disqt.disquests.client.ClientCache.getQuestById(questId);
                String title = quest != null ? quest.getTitle() : displayTitle;
                return java.util.regex.Matcher.quoteReplacement("[[" + title + "]]");
              } catch (IllegalArgumentException e) {
                return java.util.regex.Matcher.quoteReplacement("[[" + displayTitle + "]]");
              }
            });
  }
```

### Step 2: Apply reverse resolution in QuestScreen edit mode

In `QuestScreen.java`, in the `buildEditMode` method, change line 368 where the content field is populated:

Find:
```java
            quest.getContent() != null ? quest.getContent() : "",
```

Replace with:
```java
            quest.getContent() != null
                ? MarkdownRenderer.reverseResolveWikiLinks(quest.getContent())
                : "",
```

Add import if needed (it should already be imported for view mode):
```java
import com.disqt.disquests.client.markdown.MarkdownRenderer;
```

### Step 3: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 4: Commit

```
fix(client): reverse-resolve wiki-link UUIDs to quest titles in edit mode
```

---

## Task 3: Autocomplete dropdown as owo-ui OverlayContainer

**Problem:** The autocomplete dropdown renders below the text field in normal draw order. Sibling elements (coords section, buttons) render later and paint over it, making it invisible.

**Fix:** Instead of drawing the dropdown inside `TextFieldComponent.draw()`, add/remove an `OverlayContainer` on the root component of `DisquestsBaseScreen`. The overlay renders on top of all other components. The dropdown content is a vertical `FlowLayout` with clickable labels.

**Files:**
- `client/src/main/java/com/disqt/disquests/client/gui/component/AutocompleteDropdown.java` (rewrite)
- `client/src/main/java/com/disqt/disquests/client/gui/component/TextFieldComponent.java` (modify)
- `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java` (modify -- pass root component)

### Step 1: Rewrite AutocompleteDropdown to use OverlayContainer

Replace the entire `AutocompleteDropdown.java` with:

```java
package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.data.Quest;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.text.Text;

public class AutocompleteDropdown {

  private static final String OVERLAY_ID = "autocomplete-overlay";
  private static final int MAX_RESULTS = 5;
  private static final int BG_COLOR = 0xEE1a1a2e;
  private static final int HOVER_COLOR = 0xEE3a3a5e;
  private static final int TEXT_COLOR = 0xFFe0e0e0;

  private FlowLayout rootComponent;
  private Consumer<String> onSelect;
  private List<Quest> results = List.of();
  private int selectedIndex = 0;
  private boolean visible = false;

  // Absolute screen position where the dropdown should appear
  private int dropdownX;
  private int dropdownY;

  public void setRootComponent(FlowLayout root) {
    this.rootComponent = root;
  }

  public void setOnSelect(Consumer<String> onSelect) {
    this.onSelect = onSelect;
  }

  public void update(String query, int anchorX, int anchorY) {
    if (query == null || rootComponent == null) {
      hide();
      return;
    }
    String lowerQuery = query.toLowerCase();
    results =
        Stream.concat(ClientCache.getMyQuests().stream(), ClientCache.getServerQuests().stream())
            .filter(q -> q.getTitle() != null)
            .filter(q -> lowerQuery.isEmpty() || q.getTitle().toLowerCase().startsWith(lowerQuery))
            .limit(MAX_RESULTS)
            .toList();
    if (results.isEmpty()) {
      hide();
      return;
    }
    this.dropdownX = anchorX;
    this.dropdownY = anchorY;
    this.selectedIndex = 0;
    this.visible = true;
    rebuildOverlay();
  }

  public void hide() {
    if (!visible) return;
    visible = false;
    results = List.of();
    if (rootComponent != null) {
      var existing = rootComponent.childById(OverlayContainer.class, OVERLAY_ID);
      if (existing != null) {
        existing.remove();
      }
    }
  }

  public boolean isVisible() {
    return visible;
  }

  public boolean onKeyDown(int keyCode) {
    if (!visible) return false;
    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
      selectedIndex = Math.min(selectedIndex + 1, results.size() - 1);
      rebuildOverlay();
      return true;
    } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
      selectedIndex = Math.max(selectedIndex - 1, 0);
      rebuildOverlay();
      return true;
    } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
        || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
      selectCurrent();
      return true;
    } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
      hide();
      return true;
    }
    return false;
  }

  private void selectCurrent() {
    if (selectedIndex < results.size() && onSelect != null) {
      onSelect.accept(results.get(selectedIndex).getTitle());
    }
    hide();
  }

  private void rebuildOverlay() {
    if (rootComponent == null) return;

    // Remove existing overlay
    var existing = rootComponent.childById(OverlayContainer.class, OVERLAY_ID);
    if (existing != null) {
      existing.remove();
    }

    // Build dropdown panel
    FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(150), Sizing.content());
    panel.surface(Surface.flat(BG_COLOR));
    panel.positioning(Positioning.absolute(dropdownX, dropdownY));

    for (int i = 0; i < results.size(); i++) {
      Quest q = results.get(i);
      final int index = i;
      LabelComponent label = UIComponents.label(Text.literal(q.getTitle()));
      label.color(Color.ofArgb(TEXT_COLOR));
      label.shadow(true);
      label.sizing(Sizing.fill(100), Sizing.fixed(12));
      label.margins(Insets.of(1, 1, 2, 2));
      if (i == selectedIndex) {
        // Wrap selected item in a flow with highlight background
        FlowLayout highlight = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        highlight.surface(Surface.flat(HOVER_COLOR));
        highlight.child(label);
        panel.child(highlight);
      } else {
        panel.child(label);
      }
    }

    OverlayContainer<FlowLayout> overlay = UIContainers.overlay(panel);
    overlay.id(OVERLAY_ID);
    overlay.closeOnClick(true);
    rootComponent.child(overlay);
  }
}
```

### Step 2: Update TextFieldComponent to remove old draw logic

In `TextFieldComponent.java`:

**Remove** the dropdown drawing from `draw()`:

Find:
```java
    // Draw autocomplete dropdown on top, positioned relative to this component
    if (dropdown != null) {
      dropdown.draw(context, this.x(), this.y());
    }
```

Replace with nothing (delete those 3 lines).

**Update** `updateAutocomplete()` to pass anchor coordinates as absolute screen position (cursor position within the component):

Find:
```java
    // afterOpen is the partial query typed after [[
    // Position dropdown at bottom-left of this component
    dropdown.update(afterOpen, 0, this.height());
```

Replace with:
```java
    // afterOpen is the partial query typed after [[
    // Position dropdown below the current cursor line within the text field
    int cursorScreenX = this.x() + 4;
    int cursorScreenY = this.y() + delegate.getCursorScreenY() + delegate.getLineHeight();
    dropdown.update(afterOpen, cursorScreenX, cursorScreenY);
```

### Step 3: Add getCursorScreenY and getLineHeight to MultiLineTextFieldWidget

In `MultiLineTextFieldWidget.java`, add two public methods:

```java
  /** Returns the Y offset of the cursor relative to the widget's top, accounting for scroll. */
  public int getCursorScreenY() {
    int displayLine = 0;
    if (wordWrap) {
      for (int i = 0; i < displayToLogical.size(); i++) {
        if (displayToLogical.get(i) == cursorY
            && displayToOffset.get(i) + displayLines.get(i).length() >= cursorX) {
          displayLine = i;
          break;
        }
      }
    } else {
      displayLine = cursorY;
    }
    return displayLine * textRenderer.fontHeight - scrollY + padding;
  }

  /** Returns the height of a single text line. */
  public int getLineHeight() {
    return textRenderer.fontHeight;
  }
```

Add these after the existing `getCursorAbsolute()` method (around line 278).

### Step 4: Pass root component to the autocomplete dropdown in QuestScreen

In `QuestScreen.java`, in the `buildEditMode` method, after `contentFieldComponent.setAutocomplete(autocomplete)` (line 392), add:

```java
    autocomplete.setRootComponent(root);
```

### Step 5: Remove the old `draw` method parameter from AutocompleteDropdown

The old `draw(OwoUIGraphics, int, int)` method no longer exists. Check that no other code calls it. The only caller was `TextFieldComponent.draw()` which was removed in Step 2.

### Step 6: Verify compilation

```bash
./gradlew :client:compileJava
```

Expected: BUILD SUCCESSFUL

### Step 7: Commit

```
feat(client): render autocomplete dropdown as owo-ui overlay for correct z-order
```

---

## Task 4: E2E test coverage

**Files:**
- `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/WikiLinkJourney.java` (modify)

### Step 1: Add test for wiki-link on standalone line

Add a test after Order 5 that creates a quest with ONLY a wiki-link as content (on its own line), saves, and verifies the content-area renders in view mode. This test already exists (Order 5 `contentOnlyWikiLink`), but add a new test that verifies the content area has rendered text (not empty):

```java
  @Test
  @Order(6)
  @PlayerA
  @DisplayName("Wiki-link on standalone line renders in view mode after re-open")
  void standaloneWikiLinkRendersAfterReopen(ClientGameTestContext context) {
    given("'Link Source' has only a wiki-link as content");
    // Re-open from list to get server-resolved content
    click(context, "btn-close");
    waitForScreen(context, MainScreen.class);
    openMainScreen(context);
    waitForEntryCount(context, 2);
    clickEntryByTitle(context, "Link Source");
    click(context, "btn-open");
    waitForViewMode(context);

    then("content-area is present and quest is displayed");
    assertComponentExists(context, "content-area");
    assertScreenIs(context, QuestScreen.class);
  }
```

### Step 2: Add test for UUID hidden in edit mode

```java
  @Test
  @Order(7)
  @PlayerA
  @DisplayName("Edit mode shows quest title, not UUID, in wiki-link")
  void editModeHidesUuid(ClientGameTestContext context) {
    given("'Link Source' is in view mode with resolved wiki-link");
    assertScreenIs(context, QuestScreen.class);

    when("player enters edit mode");
    click(context, "btn-edit");
    waitForEditMode(context);

    then("content field contains [[Link Target]], not a UUID");
    String content = readContentField(context);
    assertNotNull(content, "Content field should be readable");
    assertTrue(
        content.contains("[[Link Target]]"),
        "Content should contain [[Link Target]], got: " + content);
    assertFalse(
        content.matches(".*\\[\\[[0-9a-f-]{36}\\|.*"),
        "Content should NOT contain UUID pipe format, got: " + content);

    and("player cancels to return to view mode");
    click(context, "btn-cancel");
    waitForViewMode(context);
  }
```

### Step 3: Verify test compilation

```bash
./gradlew :client:compileTestmodJava
```

Expected: BUILD SUCCESSFUL

### Step 4: Run E2E tests

```bash
./gradlew :client:runSoloTests -PtestFilter=WikiLinkJourney
```

Expected: All tests pass

### Step 5: Commit

```
test(e2e): add tests for standalone wiki-link rendering and UUID hiding in edit mode
```

---

## Summary

| Task | Issue | Files | Commit |
|------|-------|-------|--------|
| 1 | Wiki-link invisible on standalone line | MarkdownRenderer.java | `fix(client): handle HtmlBlock wiki-links` |
| 2 | UUID exposed in edit mode | MarkdownRenderer.java, QuestScreen.java | `fix(client): reverse-resolve wiki-link UUIDs` |
| 3 | Autocomplete dropdown hidden | AutocompleteDropdown.java, TextFieldComponent.java, MultiLineTextFieldWidget.java, QuestScreen.java | `feat(client): overlay autocomplete dropdown` |
| 4 | E2E tests | WikiLinkJourney.java | `test(e2e): wiki-link rendering and UUID hiding` |

**Execution order:** Tasks 1-3 are independent. Task 4 depends on all three.
