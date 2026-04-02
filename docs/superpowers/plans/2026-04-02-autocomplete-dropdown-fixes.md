# Autocomplete Dropdown Fixes

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the wiki-link autocomplete dropdown so quest names display fully, the dropdown appears at the cursor position, and the full-screen dark overlay is removed.

**Architecture:** Three targeted fixes in `AutocompleteDropdown.java`, `MultiLineTextFieldWidget.java`, and `TextFieldComponent.java`. Add a `getCursorScreenX()` method to the widget, pass it through to the dropdown anchor, and fix the dropdown's label sizing and overlay surface. Add E2E test coverage for autocomplete behavior.

**Tech Stack:** owo-ui 0.13.0, Fabric 1.21.11, JUnit 5 (E2E via FabricClientGameTest)

---

### Task 1: Fix label text truncation

**Problem:** Labels use `Sizing.fill(100)` which doesn't resolve correctly inside an absolutely-positioned panel within an `OverlayContainer`. Only the first character of each quest name renders.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/AutocompleteDropdown.java:116-135`

- [ ] **Step 1: Change panel width to content-based with minimum**

Replace the fixed-width panel and fill-based label sizing with content-driven sizing. The panel should auto-size to the longest quest name, with a minimum width for short names.

In `rebuildOverlay()`, change:

```java
FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(150), Sizing.content());
```

to:

```java
FlowLayout panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
```

- [ ] **Step 2: Change label sizing from fill to content**

In the loop inside `rebuildOverlay()`, change:

```java
label.sizing(Sizing.fill(100), Sizing.fixed(12));
```

to:

```java
label.sizing(Sizing.content(), Sizing.content());
```

- [ ] **Step 3: Add padding to the panel for visual breathing room**

After `panel.surface(...)`, add:

```java
panel.padding(Insets.of(2));
```

And increase label margins for readability:

```java
label.margins(Insets.of(2, 2, 4, 4));
```

- [ ] **Step 4: Update highlight wrapper to use content sizing**

Change the highlight flow from:

```java
FlowLayout highlight = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
```

to:

```java
FlowLayout highlight = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
```

- [ ] **Step 5: Build client to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/component/AutocompleteDropdown.java
git commit -m "fix: autocomplete dropdown label text truncation

Sizing.fill(100) doesn't resolve correctly inside an
absolutely-positioned OverlayContainer. Switch to content-based
sizing so quest names render in full."
```

---

### Task 2: Position dropdown at cursor, not field edge

**Problem:** The dropdown anchors at `this.x() + 4` (the text field's left edge). It should appear at the cursor's X position within the text.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/MultiLineTextFieldWidget.java` (add `getCursorScreenX()`)
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/TextFieldComponent.java:166` (use cursor X)

- [ ] **Step 1: Add `getCursorScreenX()` to MultiLineTextFieldWidget**

Add this method after the existing `getCursorScreenY()` (around line 345):

```java
/** Returns the X offset of the cursor relative to the widget's left, accounting for scroll. */
public int getCursorScreenX() {
  int displayCursorX = getDisplayCursorX();
  int displayLine = getDisplayLineForCursor();
  String dispLineStr = displayLines.get(displayLine);
  String beforeCursor =
      dispLineStr.substring(0, Math.min(displayCursorX, dispLineStr.length()));
  int padding = 5;
  return padding + (int) Math.round(textRenderer.getWidth(beforeCursor) - scrollX);
}
```

This mirrors `getCursorScreenY()` — returns the offset relative to the widget's top-left, accounting for scroll and padding. The `padding = 5` matches `contentX = this.x + padding` in the render method.

- [ ] **Step 2: Use cursor X position in TextFieldComponent**

In `updateAutocomplete()`, change:

```java
int cursorScreenX = this.x() + 4;
```

to:

```java
int cursorScreenX = this.x() + delegate.getCursorScreenX();
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/widget/MultiLineTextFieldWidget.java
git add client/src/main/java/com/disqt/disquests/client/gui/component/TextFieldComponent.java
git commit -m "fix: position autocomplete dropdown at cursor position

Add getCursorScreenX() to MultiLineTextFieldWidget and use it to
anchor the dropdown at the cursor's X coordinate instead of the
text field's left edge."
```

---

### Task 3: Remove full-screen dark overlay background

**Problem:** `OverlayContainer` renders a semi-transparent dark background covering the entire screen. The dropdown should float without dimming the game.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/AutocompleteDropdown.java:137-140`

- [ ] **Step 1: Set overlay surface to BLANK**

In `rebuildOverlay()`, after creating the overlay, add a surface override before adding to root:

```java
OverlayContainer<FlowLayout> overlay = UIContainers.overlay(panel);
overlay.id(OVERLAY_ID);
overlay.surface(Surface.BLANK);
overlay.closeOnClick(true);
rootComponent.child(overlay);
```

Add the `overlay.surface(Surface.BLANK);` line between the `overlay.id(...)` and `overlay.closeOnClick(...)` lines.

- [ ] **Step 2: Add a subtle outline to the dropdown panel for definition**

To ensure the dropdown is visually distinct from the game background without the dark overlay, add an outline to the panel surface. Change:

```java
panel.surface(Surface.flat(BG_COLOR));
```

to:

```java
panel.surface(Surface.flat(BG_COLOR).and(Surface.outline(0xFF3a3a5e)));
```

This uses `HOVER_COLOR` (the existing highlight color) as the outline, giving the dropdown a subtle border.

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/component/AutocompleteDropdown.java
git commit -m "fix: remove dark overlay from autocomplete dropdown

Set OverlayContainer surface to BLANK so the game isn't dimmed.
Add outline to the dropdown panel for visual definition."
```

---

### Task 4: E2E test for autocomplete dropdown

**Problem:** No existing test coverage for the autocomplete dropdown behavior. Need to verify the dropdown appears when typing `[[` and that selecting a suggestion completes the wiki-link.

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/WikiLinkJourney.java`

- [ ] **Step 1: Add test for autocomplete dropdown appearing**

Add this test after the existing `addWikiLinkInContent` test (Order 2). Shift subsequent `@Order` values up by 2 to make room.

Insert at Order 3:

```java
@Test
@Order(3)
@PlayerA
@DisplayName("Autocomplete dropdown appears when typing [[")
void autocompleteDropdownAppears(ClientGameTestContext context) {
  given("player has quests 'Link Source' and 'Link Target'");
  openMainScreen(context);
  waitForEntryCount(context, 2);

  when("player opens 'Link Source' in edit mode");
  clickEntryByTitle(context, "Link Source");
  click(context, "btn-open");
  waitForViewMode(context);
  click(context, "btn-edit");
  waitForEditMode(context);

  and("types [[ in the content field");
  type(context, "content-field", "[[");

  then("autocomplete overlay appears");
  context.waitFor(
      client -> {
        if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
        var root = dScreen.getRootComponent();
        if (root == null) return false;
        return root.childById(
                io.wispforest.owo.ui.container.OverlayContainer.class,
                "autocomplete-overlay")
            != null;
      },
      TIMEOUT);

  and("player presses Escape to dismiss");
  context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
  context.waitTicks(2);

  and("cancels edit");
  click(context, "btn-cancel");
  waitForViewMode(context);
}
```

- [ ] **Step 2: Add test for autocomplete selection completing the wiki-link**

Insert at Order 4:

```java
@Test
@Order(4)
@PlayerA
@DisplayName("Selecting autocomplete suggestion completes wiki-link")
void autocompleteSelectionCompletesLink(ClientGameTestContext context) {
  given("player is viewing 'Link Source'");
  assertScreenIs(context, QuestScreen.class);

  when("player enters edit mode and types [[L");
  click(context, "btn-edit");
  waitForEditMode(context);
  type(context, "content-field", "[[L");

  and("waits for autocomplete to appear");
  context.waitFor(
      client -> {
        if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
        var root = dScreen.getRootComponent();
        if (root == null) return false;
        return root.childById(
                io.wispforest.owo.ui.container.OverlayContainer.class,
                "autocomplete-overlay")
            != null;
      },
      TIMEOUT);

  and("presses Enter to select the first suggestion");
  context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
  context.waitTicks(2);

  then("content field contains the completed wiki-link");
  String content = readContentField(context);
  assertNotNull(content, "Content field should be readable");
  assertTrue(
      content.contains("[[Link Target]]"),
      "Content should contain completed wiki-link, got: " + content);

  and("cancels edit");
  click(context, "btn-cancel");
  waitForViewMode(context);
}
```

- [ ] **Step 3: Update @Order values on existing tests**

Shift the existing tests that were Order 3-9 to Order 5-11 to accommodate the two new tests.

- [ ] **Step 4: Verify testmod compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run the wiki-link journey tests**

Run: `./gradlew :client:runSoloTests -PtestFilter=WikiLinkJourney`
Expected: All tests pass, including the two new autocomplete tests.

- [ ] **Step 6: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/WikiLinkJourney.java
git commit -m "test: add E2E tests for autocomplete dropdown

Verify dropdown appears on [[ input and that selecting a
suggestion completes the wiki-link with ]] suffix."
```
