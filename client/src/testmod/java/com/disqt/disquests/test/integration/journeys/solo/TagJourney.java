package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.gui.screen.TagPickerScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.UIComponent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

@IntegrationTest
@DisplayName("Tag Journey")
class TagJourney {

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
  }

  // --- helpers ---

  private void createQuest(ClientGameTestContext context, String title) {
    openMainScreen(context);
    click(context, "btn-new-quest");
    waitForScreen(context, QuestScreen.class);
    type(context, "title-field", title);
    click(context, "btn-save");
    waitForViewMode(context);
  }

  /** Wait for QuestScreen to be in view mode (tag-display is present, tag-editor is absent). */
  private void waitForViewMode(ClientGameTestContext context) {
    context.waitFor(
        client -> {
          if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          return root.childById(FlowLayout.class, "tag-display") != null;
        },
        TIMEOUT);
    context.waitTicks(2);
  }

  /** Wait for QuestScreen to be in edit mode (tag-editor is present). */
  private void waitForEditMode(ClientGameTestContext context) {
    context.waitFor(
        client -> {
          if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          return root.childById(FlowLayout.class, "tag-editor") != null;
        },
        TIMEOUT);
    context.waitTicks(2);
  }

  /**
   * Count the number of tags currently shown in the tag-editor FlowLayout. Each tag occupies two
   * children: a label and an "x" button. The final child is the "+ Tag" button (present when below
   * MAX_TAGS). So tag count = (children.size() - 1) / 2. Note: this formula is only correct below
   * MAX_TAGS (8); tests stay well below that.
   */
  private int tagEditorTagCount(ClientGameTestContext context) {
    return context.computeOnClient(
        c -> {
          if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return 0;
          var root = dScreen.getRootComponent();
          if (root == null) return 0;
          FlowLayout tagEditor = root.childById(FlowLayout.class, "tag-editor");
          if (tagEditor == null) return 0;
          int size = tagEditor.children().size();
          // children = [label, x, label, x, ..., +Tag button]
          // tag count = (size - 1) / 2, but guard against no +Tag button
          if (size == 0) return 0;
          return (size - 1) / 2;
        });
  }

  /** Count the number of tags in the tag-display FlowLayout (view mode). */
  private int tagDisplayTagCount(ClientGameTestContext context) {
    return context.computeOnClient(
        c -> {
          if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return 0;
          var root = dScreen.getRootComponent();
          if (root == null) return 0;
          FlowLayout tagDisplay = root.childById(FlowLayout.class, "tag-display");
          if (tagDisplay == null) return 0;
          return tagDisplay.children().size();
        });
  }

  /**
   * Click the first predefined tag button in TagPickerScreen. Predefined buttons are children of
   * "predefined-list".
   */
  private void clickFirstPredefinedTag(ClientGameTestContext context) {
    // Wait for the picker to open
    waitForScreen(context, TagPickerScreen.class);

    double[] pos =
        context.computeOnClient(
            c -> {
              if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return null;
              var root = dScreen.getRootComponent();
              if (root == null) return null;
              FlowLayout predefinedList = root.childById(FlowLayout.class, "predefined-list");
              if (predefinedList == null || predefinedList.children().isEmpty()) return null;
              UIComponent first = predefinedList.children().get(0);
              return new double[] {
                first.x() + first.width() / 2.0, first.y() + first.height() / 2.0
              };
            });

    if (pos == null) {
      throw new AssertionError("No predefined tags found in predefined-list");
    }

    double scale = scaleFactor(context);
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
  }

  // --- tests ---

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Create quest and open tag picker")
  void createQuestAndOpenTagPicker(ClientGameTestContext context) {
    given("player is connected to the server");
    when("player creates a new quest 'Tag Test'");
    createQuest(context, "Tag Test");
    then("view mode is shown");
    assertScreenIs(context, QuestScreen.class);

    when("player clicks Edit");
    click(context, "btn-edit");
    waitForEditMode(context);
    and("clicks the '+ Tag' button");
    clickAddTagButton(context);
    then("TagPickerScreen opens");
    waitForScreen(context, TagPickerScreen.class);
    assertScreenIs(context, TagPickerScreen.class);
  }

  /** Click the "+ Tag" button, which is the last child of the tag-editor FlowLayout. */
  private void clickAddTagButton(ClientGameTestContext context) {
    // Wait for edit mode so tag-editor is present
    waitForEditMode(context);

    double[] pos =
        context.computeOnClient(
            c -> {
              if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return null;
              var root = dScreen.getRootComponent();
              if (root == null) return null;
              FlowLayout tagEditor = root.childById(FlowLayout.class, "tag-editor");
              if (tagEditor == null || tagEditor.children().isEmpty()) return null;
              // The "+ Tag" button is always the last child
              UIComponent addBtn = tagEditor.children().get(tagEditor.children().size() - 1);
              return new double[] {
                addBtn.x() + addBtn.width() / 2.0, addBtn.y() + addBtn.height() / 2.0
              };
            });

    if (pos == null) {
      throw new AssertionError("Could not find '+ Tag' button in tag-editor");
    }

    double scale = scaleFactor(context);
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Add predefined tag via picker")
  void addPredefinedTag(ClientGameTestContext context) {
    given("TagPickerScreen is open");
    assertScreenIs(context, TagPickerScreen.class);
    when("player clicks the first predefined tag");
    clickFirstPredefinedTag(context);
    then("returns to edit mode with one tag in tag-editor");
    waitForEditMode(context);
    context.waitFor(client -> tagEditorTagCount(client) >= 1, TIMEOUT);
    int count = tagEditorTagCount(context);
    org.junit.jupiter.api.Assertions.assertTrue(
        count >= 1, "Expected at least 1 tag in tag-editor, got: " + count);
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("Save and verify tag in view mode")
  void saveAndVerifyTagInViewMode(ClientGameTestContext context) {
    given("quest is in edit mode with one tag");
    waitForEditMode(context);
    when("player clicks Save");
    click(context, "btn-save");
    then("view mode shows the tag in tag-display");
    waitForViewMode(context);
    // tag-display should have 1 label (the tag), not the "no tags" placeholder
    context.waitFor(
        client -> {
          if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          FlowLayout tagDisplay = root.childById(FlowLayout.class, "tag-display");
          return tagDisplay != null && !tagDisplay.children().isEmpty();
        },
        TIMEOUT);
    int tagCount = tagDisplayTagCount(context);
    org.junit.jupiter.api.Assertions.assertEquals(
        1, tagCount, "Expected 1 tag in tag-display, got: " + tagCount);
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("Verify quest with tags in quest list")
  void verifyQuestInList(ClientGameTestContext context) {
    given("quest is saved with a tag");
    when("player closes quest and opens MainScreen");
    click(context, "btn-close");
    waitForScreen(context, MainScreen.class);
    openMainScreen(context);
    then("quest appears in the list");
    waitForEntryCount(context, 1);
    assertEntryCount(context, 1);
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("Tag search filter shows and hides quest")
  void tagSearchFilter(ClientGameTestContext context) {
    given("player is on MainScreen with 'Tag Test' quest");
    openMainScreen(context);
    waitForEntryCount(context, 1);

    // Get the actual tag name from cache to use in the search filter
    String tagName =
        context.computeOnClient(
            c ->
                ClientCache.getMyQuests().stream()
                    .filter(q -> "Tag Test".equals(q.getTitle()))
                    .findFirst()
                    .flatMap(q -> q.getTags().stream().findFirst())
                    .orElse(null));
    org.junit.jupiter.api.Assertions.assertNotNull(tagName, "Quest should have at least one tag");

    when("player types '#" + tagName + "' in the search box");
    type(context, "search-box", "#" + tagName);
    then("quest is shown");
    waitForEntryCount(context, 1);

    when("player types '#zzznomatch' in the search box");
    type(context, "search-box", "#zzznomatch");
    then("quest is NOT shown");
    waitForEntryCount(context, 0);

    when("player clears the search");
    type(context, "search-box", "");
    then("quest returns");
    waitForEntryCount(context, 1);
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("Add custom tag")
  void addCustomTag(ClientGameTestContext context) {
    given("player is on MainScreen");
    openMainScreen(context);
    when("player opens quest in edit mode");
    clickEntry(context, 0);
    click(context, "btn-open");
    waitForViewMode(context);
    click(context, "btn-edit");
    waitForEditMode(context);
    and("clicks '+ Tag' to open picker");
    clickAddTagButton(context);
    waitForScreen(context, TagPickerScreen.class);
    and("types 'piwigord' in the custom field and clicks Add");
    type(context, "custom-tag-field", "piwigord");
    // Find the Add button in custom-row (last child of custom-row)
    clickAddButtonInCustomRow(context);
    then("edit mode shows the custom tag");
    waitForEditMode(context);
    context.waitFor(
        client -> {
          if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          FlowLayout tagEditor = root.childById(FlowLayout.class, "tag-editor");
          if (tagEditor == null) return false;
          // Check that any tag label text contains "piwigord"
          return tagEditor.children().stream()
              .anyMatch(
                  child -> {
                    if (child instanceof io.wispforest.owo.ui.component.LabelComponent label) {
                      return label.text().getString().contains("piwigord");
                    }
                    return false;
                  });
        },
        TIMEOUT);
  }

  /**
   * Click the "Add" button in the custom-row of TagPickerScreen. It is the last child of
   * custom-row.
   */
  private void clickAddButtonInCustomRow(ClientGameTestContext context) {
    double[] pos =
        context.computeOnClient(
            c -> {
              if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return null;
              var root = dScreen.getRootComponent();
              if (root == null) return null;
              FlowLayout customRow = root.childById(FlowLayout.class, "custom-row");
              if (customRow == null || customRow.children().isEmpty()) return null;
              UIComponent addBtn = customRow.children().get(customRow.children().size() - 1);
              return new double[] {
                addBtn.x() + addBtn.width() / 2.0, addBtn.y() + addBtn.height() / 2.0
              };
            });

    if (pos == null) {
      throw new AssertionError("Could not find 'Add' button in custom-row");
    }

    double scale = scaleFactor(context);
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
  }

  @Test
  @Order(7)
  @PlayerA
  @DisplayName("Remove tag from edit mode")
  void removeTag(ClientGameTestContext context) {
    given("quest is in edit mode with tags");
    waitForEditMode(context);
    int countBefore = tagEditorTagCount(context);
    org.junit.jupiter.api.Assertions.assertTrue(
        countBefore > 0, "Expected at least 1 tag before removal, got: " + countBefore);

    when("player clicks the 'x' button next to the first tag");
    clickFirstRemoveTagButton(context);
    then("one fewer tag appears in tag-editor");
    int expectedAfter = countBefore - 1;
    // After removing, the screen rebuilds (rebuildEditMode), so wait for edit mode again
    waitForEditMode(context);
    context.waitFor(client -> tagEditorTagCount(client) == expectedAfter, TIMEOUT);
    int countAfter = tagEditorTagCount(context);
    org.junit.jupiter.api.Assertions.assertEquals(
        expectedAfter,
        countAfter,
        "Expected " + expectedAfter + " tags after removal, got: " + countAfter);
  }

  /**
   * Click the first "x" (remove) button for the first tag in tag-editor. Layout: [tagLabel, xBtn,
   * tagLabel, xBtn, ..., +TagBtn] The first "x" button is at index 1.
   */
  private void clickFirstRemoveTagButton(ClientGameTestContext context) {
    double[] pos =
        context.computeOnClient(
            c -> {
              if (!(c.currentScreen instanceof DisquestsBaseScreen dScreen)) return null;
              var root = dScreen.getRootComponent();
              if (root == null) return null;
              FlowLayout tagEditor = root.childById(FlowLayout.class, "tag-editor");
              if (tagEditor == null || tagEditor.children().size() < 2) return null;
              // First "x" button is at index 1 (after the first tag label)
              UIComponent xBtn = tagEditor.children().get(1);
              return new double[] {xBtn.x() + xBtn.width() / 2.0, xBtn.y() + xBtn.height() / 2.0};
            });

    if (pos == null) {
      throw new AssertionError("Could not find remove button in tag-editor");
    }

    double scale = scaleFactor(context);
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
  }

  // Helper to make the context.waitFor lambda cleaner for screen-type checks
  private int tagEditorTagCount(net.minecraft.client.MinecraftClient client) {
    if (!(client.currentScreen instanceof DisquestsBaseScreen dScreen)) return -1;
    var root = dScreen.getRootComponent();
    if (root == null) return -1;
    FlowLayout tagEditor = root.childById(FlowLayout.class, "tag-editor");
    if (tagEditor == null) return -1;
    int size = tagEditor.children().size();
    if (size == 0) return 0;
    return (size - 1) / 2;
  }
}
