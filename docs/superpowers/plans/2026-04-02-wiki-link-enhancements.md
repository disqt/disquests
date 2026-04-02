# Wiki-Link Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add hover preview popups and edit mode syntax highlighting to wiki-links, improving discoverability and readability of linked quests.

**Architecture:** A shared `HoverPreviewRenderer` utility draws a tooltip-style card (title, 3 rendered markdown lines, tag chips) at cursor position. `MarkdownWidget` (view mode) uses existing hitboxes for hover detection; `MultiLineTextFieldWidget` (edit mode) adds segmented text rendering for `[[...]]` styling and character-position-based hover detection. Both widgets expose `isPreviewVisible()` for E2E testing.

**Tech Stack:** Java 21, Fabric, owo-ui 0.13.0, commonmark-java, JUnit 5 (Fabric GameTest harness)

---

## Task 1: Create HoverPreviewRenderer utility

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/helper/HoverPreviewRenderer.java`

This stateless utility draws a hover preview card for a given `Quest`. Both `MarkdownWidget` and `MultiLineTextFieldWidget` will call it.

- [ ] **Step 1: Create HoverPreviewRenderer class**

```java
package com.disqt.disquests.client.gui.helper;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class HoverPreviewRenderer {

  private static final int WIDTH = 150;
  private static final int MAX_LINES = 3;
  private static final int PADDING = 4;
  private static final int OFFSET_X = 12;
  private static final int OFFSET_Y = 16;
  private static final int BG_COLOR = 0xEE1a1a2e;
  private static final int BORDER_COLOR = 0xAA555555;
  private static final int TITLE_COLOR = 0xFFFFFFFF;
  private static final int ELLIPSIS_COLOR = 0xFF888888;

  private HoverPreviewRenderer() {}

  /**
   * Draws a hover preview card for the given quest near the mouse cursor.
   *
   * @param context the draw context
   * @param quest the quest to preview
   * @param mouseX mouse x in screen coordinates
   * @param mouseY mouse y in screen coordinates
   * @param screenWidth total screen width (for edge clamping)
   * @param screenHeight total screen height (for edge clamping)
   */
  public static void draw(
      DrawContext context,
      Quest quest,
      int mouseX,
      int mouseY,
      int screenWidth,
      int screenHeight) {
    TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    int lineHeight = textRenderer.fontHeight;

    // Render content lines (up to MAX_LINES)
    String content = quest.getContent();
    List<RenderedLine> renderedLines = List.of();
    boolean truncated = false;
    if (content != null && !content.isEmpty()) {
      renderedLines = MarkdownRenderer.render(content);
      if (renderedLines.size() > MAX_LINES) {
        renderedLines = renderedLines.subList(0, MAX_LINES);
        truncated = true;
      }
    }

    // Compute tag chips
    List<String> tags = quest.getTags();

    // Compute card height: padding + title + gap + content lines + gap + tags + padding
    int contentLinesHeight = renderedLines.size() * lineHeight;
    int tagsHeight = tags.isEmpty() ? 0 : lineHeight + 2; // one row of tag text
    int totalHeight = PADDING + lineHeight; // title
    if (!renderedLines.isEmpty()) {
      totalHeight += 2 + contentLinesHeight; // gap + content
      if (truncated) totalHeight += lineHeight; // ellipsis line
    }
    if (!tags.isEmpty()) {
      totalHeight += 2 + tagsHeight; // gap + tags
    }
    totalHeight += PADDING;

    // Position with offset, clamp to screen edges
    int x = mouseX + OFFSET_X;
    int y = mouseY + OFFSET_Y;
    if (x + WIDTH > screenWidth) x = mouseX - WIDTH - 4;
    if (y + totalHeight > screenHeight) y = mouseY - totalHeight - 4;
    if (x < 0) x = 0;
    if (y < 0) y = 0;

    // Draw background + border
    context.fill(x - 1, y - 1, x + WIDTH + 1, y + totalHeight + 1, BORDER_COLOR);
    context.fill(x, y, x + WIDTH, y + totalHeight, BG_COLOR);

    int drawY = y + PADDING;

    // Title (bold)
    String title = quest.getTitle() != null ? quest.getTitle() : "Untitled";
    Text titleText =
        Text.literal(title).setStyle(Style.EMPTY.withBold(true).withColor(TITLE_COLOR));
    context.drawText(textRenderer, titleText, x + PADDING, drawY, TITLE_COLOR, false);
    drawY += lineHeight + 2;

    // Content lines
    for (RenderedLine line : renderedLines) {
      context.drawText(textRenderer, line.text(), x + PADDING, drawY, Colors.TEXT_MUTED, false);
      drawY += lineHeight;
    }
    if (truncated) {
      context.drawText(
          textRenderer, Text.literal("..."), x + PADDING, drawY, ELLIPSIS_COLOR, false);
      drawY += lineHeight;
    }

    // Tags
    if (!tags.isEmpty()) {
      drawY += 2;
      int tagX = x + PADDING;
      for (String tag : tags) {
        int tagColor = TagColors.getForeground(tag);
        int tagBg = TagColors.getBackground(tag);
        int tagWidth = textRenderer.getWidth(tag) + 6; // 3px padding each side
        if (tagX + tagWidth > x + WIDTH - PADDING) break; // don't overflow
        context.fill(tagX, drawY, tagX + tagWidth, drawY + lineHeight, tagBg);
        context.drawText(textRenderer, Text.literal(tag), tagX + 3, drawY + 1, tagColor, false);
        tagX += tagWidth + 2;
      }
    }
  }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :client:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/helper/HoverPreviewRenderer.java
git commit -m "feat(client): add HoverPreviewRenderer utility for wiki-link hover previews"
```

---

## Task 2: Edit mode wiki-link syntax highlighting

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/MultiLineTextFieldWidget.java`

Add segmented text rendering so `[[...]]` patterns are drawn in amber + italics + underline.

- [ ] **Step 1: Add wiki-link segment data structures and pattern**

In `MultiLineTextFieldWidget.java`, add imports and fields after the existing field declarations (after line 81):

Add import at the top with the other imports:
```java
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
```

Add fields after line 81 (`displayToOffset`):
```java
  // Wiki-link syntax highlighting
  private static final Pattern WIKI_LINK_EDIT_PATTERN = Pattern.compile("\\[\\[[^\\]]*\\]\\]");
  private static final int WIKI_LINK_COLOR = 0xFFe8a86d; // amber
  private List<List<int[]>> displayLineWikiLinks = new ArrayList<>();
  private boolean wikiLinkSegmentsDirty = true;
```

- [ ] **Step 2: Add rebuildWikiLinkSegments method**

Add this method after `rebuildDisplayLines()` (after line 192):

```java
  /**
   * Scans display lines for [[...]] patterns and caches their character ranges. Each entry in
   * displayLineWikiLinks corresponds to a display line and contains a list of [start, end] pairs
   * (relative to the display line string).
   */
  private void rebuildWikiLinkSegments() {
    displayLineWikiLinks.clear();
    for (int di = 0; di < displayLines.size(); di++) {
      List<int[]> segments = new ArrayList<>();
      int logicalLine = displayToLogical.get(di);
      int dispOffset = displayToOffset.get(di);
      String fullLogical = lines.get(logicalLine);

      // Find all [[...]] in the logical line
      Matcher m = WIKI_LINK_EDIT_PATTERN.matcher(fullLogical);
      int dispLen = displayLines.get(di).length();
      int dispEnd = dispOffset + dispLen;
      while (m.find()) {
        int matchStart = m.start();
        int matchEnd = m.end();
        // Clip to this display line's range
        int segStart = Math.max(matchStart, dispOffset) - dispOffset;
        int segEnd = Math.min(matchEnd, dispEnd) - dispOffset;
        if (segStart < segEnd && segStart < dispLen) {
          segments.add(new int[] {segStart, segEnd});
        }
      }
      displayLineWikiLinks.add(segments);
    }
    wikiLinkSegmentsDirty = false;
  }
```

- [ ] **Step 3: Mark segments dirty when display lines rebuild**

In `rebuildDisplayLines()`, add at the end (before the closing `}`):

Find:
```java
      offset += trimmed.length();
    }
  }
}
```

This is the end of the `rebuildDisplayLines()` method. The last `}` closes the method. Add the dirty flag before that final `}`:

Replace with:
```java
      offset += trimmed.length();
    }
  }
  wikiLinkSegmentsDirty = true;
}
```

- [ ] **Step 4: Replace single-draw text rendering with segmented drawing**

In `render()`, find the text drawing loop (lines 541-549):

Find:
```java
    // Draw lines (with horizontal scroll applied)
    for (int di = firstVisibleLine; di <= lastVisibleLine; di++) {
      int lineYPos = contentY + (di * textRenderer.fontHeight) - (int) scrollY;
      if (lineYPos > this.y - textRenderer.fontHeight && lineYPos < this.y + this.height) {
        int drawX = contentX - (int) Math.round(scrollX);
        context.drawText(
            this.textRenderer, displayLines.get(di), drawX, lineYPos, Colors.TEXT_PRIMARY, false);
      }
    }
```

Replace with:
```java
    // Draw lines with wiki-link syntax highlighting
    if (wikiLinkSegmentsDirty) rebuildWikiLinkSegments();
    for (int di = firstVisibleLine; di <= lastVisibleLine; di++) {
      int lineYPos = contentY + (di * textRenderer.fontHeight) - (int) scrollY;
      if (lineYPos > this.y - textRenderer.fontHeight && lineYPos < this.y + this.height) {
        int drawX = contentX - (int) Math.round(scrollX);
        String dispLine = displayLines.get(di);
        List<int[]> wikiLinks =
            di < displayLineWikiLinks.size() ? displayLineWikiLinks.get(di) : List.of();

        if (wikiLinks.isEmpty()) {
          // Fast path: no wiki-links on this line
          context.drawText(
              this.textRenderer, dispLine, drawX, lineYPos, Colors.TEXT_PRIMARY, false);
        } else {
          // Segmented drawing: alternate between normal and wiki-link styled text
          int pos = 0;
          int currentX = drawX;
          for (int[] seg : wikiLinks) {
            // Normal segment before this wiki-link
            if (pos < seg[0]) {
              String normal = dispLine.substring(pos, seg[0]);
              context.drawText(
                  this.textRenderer, normal, currentX, lineYPos, Colors.TEXT_PRIMARY, false);
              currentX += this.textRenderer.getWidth(normal);
            }
            // Wiki-link segment: amber + italics + underline
            String wikiText = dispLine.substring(seg[0], seg[1]);
            Text styledText =
                Text.literal(wikiText)
                    .setStyle(
                        Style.EMPTY
                            .withColor(WIKI_LINK_COLOR)
                            .withItalic(true)
                            .withUnderline(true));
            context.drawText(
                this.textRenderer, styledText.asOrderedText(), currentX, lineYPos, -1, false);
            currentX += this.textRenderer.getWidth(wikiText);
            pos = seg[1];
          }
          // Remaining normal text after last wiki-link
          if (pos < dispLine.length()) {
            String remaining = dispLine.substring(pos);
            context.drawText(
                this.textRenderer, remaining, currentX, lineYPos, Colors.TEXT_PRIMARY, false);
          }
        }
      }
    }
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :client:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/widget/MultiLineTextFieldWidget.java
git commit -m "feat(client): highlight [[wiki-link]] syntax in edit mode with amber italics underline"
```

---

## Task 3: View mode hover preview

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/MarkdownWidget.java`

Add hover detection over wiki-link hitboxes and call `HoverPreviewRenderer.draw()`.

- [ ] **Step 1: Add hover state field and isPreviewVisible getter**

In `MarkdownWidget.java`, add import:
```java
import com.disqt.disquests.client.gui.helper.HoverPreviewRenderer;
import com.disqt.disquests.client.data.Quest;
```

Add field after `private List<RenderedLine> currentLines;` (line 40):
```java
  private boolean previewVisible = false;
```

Add method after `setCheckboxToggleListener` (find the method, add after its closing `}`):
```java
  /** Returns true if a hover preview is currently being drawn. For E2E testing. */
  public boolean isPreviewVisible() {
    return previewVisible;
  }
```

- [ ] **Step 2: Add hover preview rendering to draw()**

In `draw()`, replace the existing link hover tooltip and scrollbar section (lines 232-243):

Find:
```java
    // Link hover tooltip
    for (LinkHitbox lh : linkHitboxes) {
      if (hitTest(mouseX, mouseY, lh.x(), lh.y(), lh.width(), lh.height())) {
        context.drawTooltip(textRenderer, lh.displayText(), mouseX, mouseY);
        break;
      }
    }

    // Scrollbar
    if (needsScrollbar()) {
      renderScrollbar(context, compX, compY, compW, compH);
    }
```

Replace with:
```java
    // Link hover tooltip
    for (LinkHitbox lh : linkHitboxes) {
      if (hitTest(mouseX, mouseY, lh.x(), lh.y(), lh.width(), lh.height())) {
        context.drawTooltip(textRenderer, lh.displayText(), mouseX, mouseY);
        break;
      }
    }

    // Wiki-link hover preview
    previewVisible = false;
    for (WikiLinkHitbox wh : wikiLinkHitboxes) {
      if (hitTest(mouseX, mouseY, wh.x(), wh.y(), wh.width(), wh.height())) {
        if (!MarkdownRenderer.WIKI_LINK_BROKEN.equals(wh.uuid())) {
          try {
            UUID questId = UUID.fromString(wh.uuid());
            Quest quest = ClientCache.getQuestById(questId);
            if (quest != null) {
              MinecraftClient mc = MinecraftClient.getInstance();
              HoverPreviewRenderer.draw(
                  (net.minecraft.client.gui.DrawContext) context,
                  quest,
                  mouseX,
                  mouseY,
                  mc.getWindow().getScaledWidth(),
                  mc.getWindow().getScaledHeight());
              previewVisible = true;
            }
          } catch (IllegalArgumentException ignored) {
          }
        }
        break;
      }
    }

    // Scrollbar
    if (needsScrollbar()) {
      renderScrollbar(context, compX, compY, compW, compH);
    }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :client:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/widget/MarkdownWidget.java
git commit -m "feat(client): show hover preview popup over wiki-links in view mode"
```

---

## Task 4: Edit mode hover preview

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/MultiLineTextFieldWidget.java`

Add mouse-position-based hover detection over `[[...]]` segments and call `HoverPreviewRenderer.draw()`.

- [ ] **Step 1: Add imports and hover state fields**

Add imports at the top of `MultiLineTextFieldWidget.java`:
```java
import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.HoverPreviewRenderer;
import java.util.UUID;
import java.util.stream.Stream;
```

Add fields after `private boolean wikiLinkSegmentsDirty = true;`:
```java
  private boolean previewVisible = false;
```

Add getter method after `getLineHeight()`:
```java
  /** Returns true if a wiki-link hover preview is currently being drawn. For E2E testing. */
  public boolean isPreviewVisible() {
    return previewVisible;
  }
```

- [ ] **Step 2: Add findQuestByTitle helper**

Add after `isPreviewVisible()`:
```java
  /** Searches ClientCache for a quest matching the given title. Returns null if not found. */
  private static Quest findQuestByTitle(String title) {
    if (title == null || title.isEmpty()) return null;
    return Stream.concat(
            ClientCache.getMyQuests().stream(), ClientCache.getServerQuests().stream())
        .filter(q -> title.equals(q.getTitle()))
        .findFirst()
        .orElse(null);
  }
```

- [ ] **Step 3: Add hover preview rendering to render()**

In `render()`, after the wiki-link segmented drawing loop (after the `}` that closes the `for (int di = firstVisibleLine...` loop), and before the caret blink section, add:

Find:
```java
    // caret blink
    long now = System.currentTimeMillis();
```

Insert before that line:
```java
    // Wiki-link hover preview in edit mode
    previewVisible = false;
    if (this.focused) {
      // Check if mouse is over a wiki-link segment
      int hoverAbsIndex = absoluteIndexFromMouse(mouseX, mouseY);
      // Find which display line the mouse is on
      int hoverDisplayLine =
          (int) ((mouseY - (this.y + padding) + scrollY) / textRenderer.fontHeight);
      hoverDisplayLine = Math.max(0, Math.min(hoverDisplayLine, displayLines.size() - 1));
      if (hoverDisplayLine < displayLineWikiLinks.size()) {
        List<int[]> segments = displayLineWikiLinks.get(hoverDisplayLine);
        int dispOffset = displayToOffset.get(hoverDisplayLine);
        int logicalLine = displayToLogical.get(hoverDisplayLine);
        // Convert absolute index to position within this display line
        int absLineStart = getAbsoluteIndex(logicalLine, dispOffset);
        int posInDispLine = hoverAbsIndex - absLineStart;
        for (int[] seg : segments) {
          if (posInDispLine >= seg[0] && posInDispLine < seg[1]) {
            // Extract title from between [[ and ]]
            String dispLine = displayLines.get(hoverDisplayLine);
            String wikiText = dispLine.substring(seg[0], seg[1]);
            if (wikiText.startsWith("[[") && wikiText.endsWith("]]")) {
              String title = wikiText.substring(2, wikiText.length() - 2);
              Quest quest = findQuestByTitle(title);
              if (quest != null) {
                MinecraftClient mc = MinecraftClient.getInstance();
                HoverPreviewRenderer.draw(
                    context, quest, mouseX, mouseY,
                    mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight());
                previewVisible = true;
              }
            }
            break;
          }
        }
      }
    }

```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :client:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/widget/MultiLineTextFieldWidget.java
git commit -m "feat(client): show hover preview popup over [[wiki-links]] in edit mode"
```

---

## Task 5: E2E tests for wiki-link enhancements

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/WikiLinkJourney.java`

Add tests that verify hover detection pipeline and that segmented rendering doesn't break text content.

- [ ] **Step 1: Add import for MarkdownWidget**

Add import at the top of `WikiLinkJourney.java`:
```java
import com.disqt.disquests.client.gui.widget.MarkdownWidget;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
```

- [ ] **Step 2: Add helper to hover over the content area**

Add helper method after the existing `readContentField` helper:

```java
  /**
   * Move the mouse cursor over the content-area MarkdownWidget (center of it). This simulates
   * hovering without clicking, which should trigger wiki-link preview detection in draw().
   */
  private void hoverContentArea(ClientGameTestContext context) {
    double[] pos =
        context.computeOnClient(
            c -> {
              if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return null;
              var root = dScreen.getRootComponent();
              if (root == null) return null;
              var contentArea = root.childById(FlowLayout.class, "content-area");
              if (contentArea == null) return null;
              // Hover near the top-left where text starts (with small offset for padding)
              return new double[] {contentArea.x() + 20.0, contentArea.y() + 10.0};
            });
    assertNotNull(pos, "content-area should exist for hover");
    double scale =
        context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.waitTicks(2); // allow a draw frame to process the hover
  }

  /**
   * Move the mouse cursor over the content-field TextFieldComponent. Hovers near the start of
   * text where [[...]] content is likely to be.
   */
  private void hoverContentField(ClientGameTestContext context) {
    double[] pos =
        context.computeOnClient(
            c -> {
              if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return null;
              var root = dScreen.getRootComponent();
              if (root == null) return null;
              var field =
                  root.childById(
                      com.disqt.disquests.client.gui.component.TextFieldComponent.class,
                      "content-field");
              if (field == null) return null;
              return new double[] {field.x() + 20.0, field.y() + 10.0};
            });
    assertNotNull(pos, "content-field should exist for hover");
    double scale =
        context.computeOnClient(c -> (double) c.getWindow().getScaleFactor());
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.waitTicks(2);
  }

  /**
   * Move the mouse away from any interactive area (to the corner of the screen).
   */
  private void hoverAway(ClientGameTestContext context) {
    context.getInput().setCursorPos(0, 0);
    context.waitTicks(2);
  }

  /** Check if the MarkdownWidget in content-area has previewVisible == true. */
  private boolean isViewPreviewVisible(ClientGameTestContext context) {
    return context.computeOnClient(
        c -> {
          if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          var contentArea = root.childById(FlowLayout.class, "content-area");
          if (contentArea == null) return false;
          for (var child : contentArea.children()) {
            if (child instanceof MarkdownWidget mw) {
              return mw.isPreviewVisible();
            }
          }
          return false;
        });
  }

  /** Check if the MultiLineTextFieldWidget in edit mode has previewVisible == true. */
  private boolean isEditPreviewVisible(ClientGameTestContext context) {
    return context.computeOnClient(
        c -> {
          if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          var field =
              root.childById(
                  com.disqt.disquests.client.gui.component.TextFieldComponent.class,
                  "content-field");
          if (field == null) return false;
          return field.getDelegate().isPreviewVisible();
        });
  }
```

- [ ] **Step 3: Add test for view mode hover preview**

Add after the existing Order 7 test:

```java
  @Test
  @Order(8)
  @PlayerA
  @DisplayName("Hovering wiki-link in view mode shows preview")
  void viewModeHoverPreview(ClientGameTestContext context) {
    given("'Link Source' is in view mode with wiki-link content");
    // Navigate back to Link Source view mode
    click(context, "btn-close");
    waitForScreen(context, MainScreen.class);
    openMainScreen(context);
    waitForEntryCount(context, 2);
    clickEntryByTitle(context, "Link Source");
    click(context, "btn-open");
    waitForViewMode(context);

    when("player hovers over the content area where the wiki-link is");
    hoverContentArea(context);

    then("hover preview becomes visible");
    assertTrue(isViewPreviewVisible(context), "Preview should be visible when hovering wiki-link");
  }

  @Test
  @Order(9)
  @PlayerA
  @DisplayName("Moving mouse away hides view mode preview")
  void viewModeHoverPreviewDismisses(ClientGameTestContext context) {
    given("preview is visible from hovering wiki-link");
    assertScreenIs(context, QuestScreen.class);

    when("player moves mouse away");
    hoverAway(context);

    then("hover preview is no longer visible");
    assertFalse(
        isViewPreviewVisible(context), "Preview should hide when mouse moves away");
  }
```

- [ ] **Step 4: Add test for edit mode text integrity**

```java
  @Test
  @Order(10)
  @PlayerA
  @DisplayName("Edit mode text is intact after segmented rendering")
  void editModeTextIntact(ClientGameTestContext context) {
    given("'Link Source' is in view mode");
    assertScreenIs(context, QuestScreen.class);

    when("player enters edit mode");
    click(context, "btn-edit");
    waitForEditMode(context);

    then("content field contains [[Link Target]] text (not broken by segmented rendering)");
    String content = readContentField(context);
    assertNotNull(content, "Content field should be readable");
    assertTrue(
        content.contains("[[Link Target]]"),
        "Content should contain [[Link Target]], got: " + content);
  }
```

- [ ] **Step 5: Add test for edit mode hover preview**

```java
  @Test
  @Order(11)
  @PlayerA
  @DisplayName("Hovering [[wiki-link]] in edit mode shows preview")
  void editModeHoverPreview(ClientGameTestContext context) {
    given("'Link Source' is in edit mode with [[Link Target]] content");
    assertScreenIs(context, QuestScreen.class);

    when("player hovers over the content field where the wiki-link text is");
    hoverContentField(context);

    then("hover preview becomes visible in edit mode");
    assertTrue(
        isEditPreviewVisible(context), "Edit mode preview should be visible when hovering wiki-link");

    and("player cancels to return to view mode");
    hoverAway(context);
    click(context, "btn-cancel");
    waitForViewMode(context);
  }
```

- [ ] **Step 6: Verify test compilation**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run the wiki-link E2E tests**

Run: `./gradlew :client:runSoloTests -PtestFilter=WikiLinkJourney`
Expected: All 11 tests pass

- [ ] **Step 8: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/WikiLinkJourney.java
git commit -m "test(e2e): add tests for hover preview and edit mode syntax highlighting"
```

---

## Summary

| Task | Description | Files | Commit |
|------|-------------|-------|--------|
| 1 | HoverPreviewRenderer utility | HoverPreviewRenderer.java (new) | `feat(client): add HoverPreviewRenderer utility` |
| 2 | Edit mode syntax highlighting | MultiLineTextFieldWidget.java | `feat(client): highlight [[wiki-link]] syntax in edit mode` |
| 3 | View mode hover preview | MarkdownWidget.java | `feat(client): show hover preview popup over wiki-links in view mode` |
| 4 | Edit mode hover preview | MultiLineTextFieldWidget.java | `feat(client): show hover preview popup over [[wiki-links]] in edit mode` |
| 5 | E2E tests | WikiLinkJourney.java | `test(e2e): add tests for hover preview and edit mode syntax highlighting` |

**Execution order:** Tasks 1 must be first (shared dependency). Tasks 2 and 3 are independent. Task 4 depends on Task 2 (segment cache). Task 5 depends on all others.
