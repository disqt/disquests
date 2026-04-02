# Search Tag Improvements

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the MainScreen search box match tags as part of text search (so typing `merde` finds quests tagged `merde`), and show a tag autocomplete dropdown when `#` is typed.

**Architecture:** Two changes in `MainScreen.java`: (1) expand `matchesSearch` to also check tags during text filtering; (2) add an `AutocompleteDropdown`-style tag suggestion popup triggered by `#` in the search box. The tag autocomplete reuses the same pattern as the wiki-link autocomplete but adapted for the single-line owo-ui `TextBoxComponent`.

**Tech Stack:** owo-ui 0.13.0 (`TextBoxComponent`, `OverlayContainer`), Fabric 1.21.11, JUnit 5 E2E

---

### Task 1: Include tags in text search matching

**Problem:** `matchesSearch` only checks `textFilter` against title and content (line 231-233). Typing `merde` without `#` matches nothing because tags are only checked when the `#`-prefixed `tagFilters` list is non-empty. Users expect any search term to also match tag names.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java:228-244`

- [ ] **Step 1: Expand text filter to also match tags**

In `matchesSearch`, change the text filter block from:

```java
    if (!query.textFilter().isEmpty()) {
      String tf = query.textFilter();
      boolean textMatch =
          q.getTitle().toLowerCase().contains(tf)
              || (q.getContent() != null && q.getContent().toLowerCase().contains(tf));
      if (!textMatch) return false;
    }
```

to:

```java
    if (!query.textFilter().isEmpty()) {
      String tf = query.textFilter();
      boolean textMatch =
          q.getTitle().toLowerCase().contains(tf)
              || (q.getContent() != null && q.getContent().toLowerCase().contains(tf))
              || q.getTags().stream().anyMatch(t -> t.toLowerCase().contains(tf));
      if (!textMatch) return false;
    }
```

This adds a third `||` branch that checks if any of the quest's tags contain the text filter.

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "fix: text search also matches quest tags

Typing 'merde' now finds quests tagged 'merde', not just quests
with 'merde' in their title or content."
```

---

### Task 2: Tag autocomplete dropdown on `#` in search box

**Problem:** Typing `#` in the search box should show a dropdown of available tags. This lets users discover and select tags without knowing the exact name. Selecting a tag from the dropdown should insert `#tagname` into the search box.

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/component/TagAutocompleteDropdown.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

- [ ] **Step 1: Create TagAutocompleteDropdown**

Create a new file `client/src/main/java/com/disqt/disquests/client/gui/component/TagAutocompleteDropdown.java`. This is similar to `AutocompleteDropdown` but adapted for tags:

```java
package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.text.Text;

public class TagAutocompleteDropdown {

  private static final String OVERLAY_ID = "tag-autocomplete-overlay";
  private static final int MAX_RESULTS = 8;
  private static final int BG_COLOR = 0xEE1a1a2e;
  private static final int HOVER_COLOR = 0xEE3a3a5e;
  private static final int TEXT_COLOR = 0xFFe0e0e0;

  private FlowLayout rootComponent;
  private Consumer<String> onSelect;
  private List<String> results = List.of();
  private int selectedIndex = 0;
  private boolean visible = false;

  private int dropdownX;
  private int dropdownY;

  public void setRootComponent(FlowLayout root) {
    this.rootComponent = root;
  }

  public void setOnSelect(Consumer<String> onSelect) {
    this.onSelect = onSelect;
  }

  public void update(String query, int anchorX, int anchorY) {
    if (rootComponent == null) {
      hide();
      return;
    }
    String lowerQuery = query != null ? query.toLowerCase() : "";

    // Collect all known tags: predefined + server + cached quests
    Set<String> allTags = new LinkedHashSet<>();
    allTags.addAll(ClientSession.getPredefinedTags());
    allTags.addAll(ClientSession.getServerTags());
    ClientCache.getMyQuests().forEach(q -> allTags.addAll(q.getTags()));
    ClientCache.getServerQuests().forEach(q -> allTags.addAll(q.getTags()));

    results =
        allTags.stream()
            .filter(t -> lowerQuery.isEmpty() || t.toLowerCase().contains(lowerQuery))
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
        || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
        || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
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
      onSelect.accept(results.get(selectedIndex));
    }
    hide();
  }

  private void rebuildOverlay() {
    if (rootComponent == null) return;

    var existing = rootComponent.childById(OverlayContainer.class, OVERLAY_ID);
    if (existing != null) {
      existing.remove();
    }

    FlowLayout panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
    panel.surface(Surface.flat(BG_COLOR).and(Surface.outline(0xFF3a3a5e)));
    panel.padding(Insets.of(2));
    panel.positioning(Positioning.absolute(dropdownX, dropdownY));

    for (int i = 0; i < results.size(); i++) {
      String tag = results.get(i);
      LabelComponent label = UIComponents.label(Text.literal("#" + tag));
      label.color(Color.ofArgb(TEXT_COLOR));
      label.shadow(true);
      label.sizing(Sizing.content(), Sizing.content());
      label.margins(Insets.of(2, 2, 4, 4));
      if (i == selectedIndex) {
        FlowLayout highlight = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        highlight.surface(Surface.flat(HOVER_COLOR));
        highlight.child(label);
        panel.child(highlight);
      } else {
        panel.child(label);
      }
    }

    OverlayContainer<FlowLayout> overlay = UIContainers.overlay(panel);
    overlay.id(OVERLAY_ID);
    overlay.surface(Surface.BLANK);
    overlay.closeOnClick(true);
    rootComponent.child(overlay);
  }
}
```

- [ ] **Step 2: Wire the autocomplete into MainScreen**

In `MainScreen.java`, add a field for the dropdown and wire it to the search box.

Add the field:

```java
private TagAutocompleteDropdown tagDropdown;
```

In the `build` method, after the search field is created and added (after line 134), initialize the dropdown:

```java
    tagDropdown = new TagAutocompleteDropdown();
    tagDropdown.setRootComponent(root);
    tagDropdown.setOnSelect(tag -> {
      // Replace the current #partial with #tagname
      String current = searchField.getText();
      int hashIndex = current.lastIndexOf('#');
      String before = hashIndex >= 0 ? current.substring(0, hashIndex) : current;
      searchField.text(before + "#" + tag + " ");
    });
```

- [ ] **Step 3: Trigger the dropdown on search text changes**

In `onSearchTermChanged`, after the existing logic, add autocomplete handling:

```java
  private void onSearchTermChanged(String newTerm) {
    this.searchTerm = newTerm.toLowerCase().trim();
    ClientSession.setSearchTerm(this.searchTerm);
    refreshListContents();
    updateTagAutocomplete(newTerm);
  }

  private void updateTagAutocomplete(String text) {
    if (tagDropdown == null || searchField == null) return;
    // Find the last # in the text
    int hashIndex = text.lastIndexOf('#');
    if (hashIndex < 0) {
      tagDropdown.hide();
      return;
    }
    // Extract partial tag after #
    String partial = text.substring(hashIndex + 1);
    // If there's a space after the #tag, it's already completed
    if (partial.contains(" ")) {
      tagDropdown.hide();
      return;
    }
    // Position dropdown above the search box
    int anchorX = searchField.x();
    int anchorY = searchField.y() - 4; // will be adjusted by dropdown height
    tagDropdown.update(partial, anchorX, anchorY);
  }
```

Note: The dropdown positioning needs to go ABOVE the search box since the search box is at the bottom of the screen. The `anchorY` calculation may need adjustment — the dropdown `Positioning.absolute` places the top-left corner, so we need to subtract the dropdown's height. An alternative is to position it at `searchField.y()` and let it render upward. Since owo-ui doesn't natively support upward-growing layouts, position at a fixed offset above the search field:

```java
    int anchorY = searchField.y() - (results_count * line_height) - padding;
```

Since we don't know the result count before calling `update()`, a simpler approach is to have `TagAutocompleteDropdown.update()` calculate and apply positioning internally after building the results list. Add a parameter or have it compute the height and adjust `dropdownY` to `anchorY - panelHeight`.

Modify `TagAutocompleteDropdown.update()` to accept a `boolean above` parameter:

```java
  public void update(String query, int anchorX, int anchorY, boolean positionAbove) {
    // ... existing filtering logic ...
    this.dropdownX = anchorX;
    // If positioning above, subtract estimated height
    int lineHeight = 16; // ~12px text + 4px margin + 4px padding
    int panelHeight = results.size() * lineHeight + 4; // 4px panel padding
    this.dropdownY = positionAbove ? anchorY - panelHeight : anchorY;
    this.selectedIndex = 0;
    this.visible = true;
    rebuildOverlay();
  }
```

Call it from `updateTagAutocomplete` with `positionAbove = true`.

- [ ] **Step 4: Intercept keyboard input for the dropdown**

Override `keyPressed` in `MainScreen` to intercept arrow keys and Enter/Tab when the dropdown is visible:

```java
  @Override
  public boolean keyPressed(KeyInput keyInput) {
    if (tagDropdown != null && tagDropdown.isVisible()) {
      if (tagDropdown.onKeyDown(keyInput.key())) {
        return true;
      }
    }
    return super.keyPressed(keyInput);
  }
```

Add this import at the top:

```java
import net.minecraft.client.input.KeyInput;
```

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/component/TagAutocompleteDropdown.java
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat: tag autocomplete dropdown in search box

Typing # in the search box shows a dropdown of available tags.
Arrow keys navigate, Enter/Tab selects. Dropdown positions above
the search field and includes tags from all sources (predefined,
server, cached quests)."
```

---

### Task 3: E2E tests for search improvements

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/TagJourney.java`

- [ ] **Step 1: Add test for text search matching tags**

After the existing `tagSearchFilter` test, add a test verifying that plain text search (without `#`) also matches tags:

```java
@Test
@Order(N)  // after tagSearchFilter
@PlayerA
@DisplayName("Plain text search matches tag names")
void plainTextSearchMatchesTags(ClientGameTestContext context) {
  given("player is on MainScreen with a tagged quest");
  openMainScreen(context);
  waitForEntryCount(context, expectedCount);

  // Get tag name from cache
  String tagName =
      context.computeOnClient(
          c ->
              ClientCache.getMyQuests().stream()
                  .filter(q -> "Tag Test".equals(q.getTitle()))
                  .findFirst()
                  .flatMap(q -> q.getTags().stream().findFirst())
                  .orElse(null));
  org.junit.jupiter.api.Assertions.assertNotNull(tagName, "Quest should have a tag");

  when("player types the tag name WITHOUT # prefix");
  type(context, "search-box", tagName);
  then("quest with that tag is shown");
  waitForEntryCount(context, 1);

  when("player clears search");
  type(context, "search-box", "");
  then("all quests return");
  waitForEntryCount(context, expectedCount);
}
```

Replace `expectedCount` and `N` with appropriate values based on the existing test order.

- [ ] **Step 2: Add test for tag autocomplete dropdown appearing on #**

```java
@Test
@Order(N+1)
@PlayerA
@DisplayName("Tag autocomplete appears when typing # in search")
void tagAutocompleteAppearsOnHash(ClientGameTestContext context) {
  given("player is on MainScreen");
  openMainScreen(context);

  when("player types '#' in the search box");
  type(context, "search-box", "#");

  then("tag autocomplete overlay appears");
  context.waitFor(
      client -> {
        if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
        var root = dScreen.getRootComponent();
        if (root == null) return false;
        return root.childById(
                io.wispforest.owo.ui.container.OverlayContainer.class,
                "tag-autocomplete-overlay")
            != null;
      },
      TIMEOUT);

  and("player presses Escape to dismiss");
  context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
  context.waitTicks(2);

  when("player clears search");
  type(context, "search-box", "");
}
```

- [ ] **Step 3: Verify testmod compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run tag journey tests**

Run: `./gradlew :client:runSoloTests -PtestFilter=TagJourney`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/TagJourney.java
git commit -m "test: verify text search matches tags and # autocomplete

E2E tests for plain-text tag matching and tag autocomplete
dropdown triggered by # in the search box."
```
