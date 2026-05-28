package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import io.wispforest.owo.ui.container.FlowLayout;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

@IntegrationTest
@DisplayName("Wiki-Link Journey")
class WikiLinkJourney {

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
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
  }

  /** Wait for QuestScreen view mode (tag-display present, tag-editor absent). */
  private void waitForViewMode(ClientGameTestContext context) {
    context.waitFor(
        client -> {
          if (!(client.screen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          return root.childById(FlowLayout.class, "tag-display") != null;
        },
        TIMEOUT);
    context.waitTicks(2);
  }

  /** Wait for QuestScreen edit mode (tag-editor present). */
  private void waitForEditMode(ClientGameTestContext context) {
    context.waitFor(
        client -> {
          if (!(client.screen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          return root.childById(FlowLayout.class, "tag-editor") != null;
        },
        TIMEOUT);
    context.waitTicks(2);
  }

  /** Read the text of the content-field in edit mode. */
  private String readContentField(ClientGameTestContext context) {
    return context.computeOnClient(
        c -> {
          if (!(c.screen instanceof DisquestsBaseScreen dScreen)) return null;
          var root = dScreen.getRootComponent();
          if (root == null) return null;
          var field =
              root.childById(
                  com.disqt.disquests.client.gui.component.TextFieldComponent.class,
                  "content-field");
          return field != null ? field.getText() : null;
        });
  }

  // --- tests ---

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("Create two quests: Link Source and Link Target")
  void createTwoQuests(ClientGameTestContext context) {
    given("player is connected to the server");
    when("player creates quest 'Link Target'");
    createQuest(context, "Link Target");
    then("Link Target appears in My Quests");
    openMainScreen(context);
    waitForEntryCount(context, 1);

    when("player creates quest 'Link Source'");
    createQuest(context, "Link Source");
    then("both quests appear in My Quests");
    openMainScreen(context);
    waitForEntryCount(context, 2);
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("Add wiki-link syntax in content and save")
  void addWikiLinkInContent(ClientGameTestContext context) {
    given("player is on MainScreen with two quests");
    openMainScreen(context);
    waitForEntryCount(context, 2);

    when("player opens 'Link Source' in edit mode");
    clickEntryByTitle(context, "Link Source");
    click(context, "btn-open");
    waitForViewMode(context);
    click(context, "btn-edit");
    waitForEditMode(context);

    and("types wiki-link syntax in the content field");
    type(context, "content-field", "See also [[Link Target]] for more info.");

    and("clicks Save");
    click(context, "btn-save");

    then("view mode loads without error");
    waitForViewMode(context);
    assertScreenIs(context, QuestScreen.class);
    assertLabelText(context, "title-label", "Link Source");
  }

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
          if (!(client.screen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          return root.childById(FlowLayout.class, "autocomplete-overlay") != null;
        },
        TIMEOUT);
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("Selecting autocomplete suggestion completes wiki-link")
  void autocompleteSelectionCompletesLink(ClientGameTestContext context) {
    given("player is still on edit mode with autocomplete open from previous test");

    when("player presses Enter to select the first suggestion");
    context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
    context.waitTicks(10);

    then("autocomplete overlay is dismissed");
    context.waitFor(
        client -> {
          if (!(client.screen instanceof DisquestsBaseScreen dScreen)) return false;
          var root = dScreen.getRootComponent();
          if (root == null) return false;
          return root.childById(FlowLayout.class, "autocomplete-overlay") == null;
        },
        TIMEOUT);

    then("content field contains the completed wiki-link");
    String content = readContentField(context);
    assertNotNull(content, "Content field should be readable");
    assertTrue(
        content.contains("[[Link Target]]"),
        "Content should contain completed wiki-link, got: " + content);

    and("saves to return to view mode");
    click(context, "btn-save");
    waitForViewMode(context);
  }

  @Test
  @Order(5)
  @PlayerA
  @DisplayName("View mode renders content area after wiki-link save")
  void viewModeRendersContentArea(ClientGameTestContext context) {
    given("'Link Source' quest is open in view mode");
    assertScreenIs(context, QuestScreen.class);

    then("content-area component is present (MarkdownWidget rendered successfully)");
    assertComponentExists(context, "content-area");

    and("quest title is still correct");
    assertLabelText(context, "title-label", "Link Source");
  }

  @Test
  @Order(6)
  @PlayerA
  @DisplayName("Wiki-link content survives round-trip (edit -> save -> edit)")
  void wikiLinkRoundTrip(ClientGameTestContext context) {
    given("'Link Source' is in view mode after save");
    assertScreenIs(context, QuestScreen.class);

    when("player re-enters edit mode");
    click(context, "btn-edit");
    waitForEditMode(context);

    then("content field contains the wiki-link text (raw or server-resolved)");
    String content = readContentField(context);
    assertNotNull(content, "Content field should be readable");
    // Content may be raw [[Link Target]] or server-resolved [[uuid|Link Target]]
    assertTrue(
        content.contains("Link Target]]"),
        "Content should contain wiki-link with 'Link Target', got: " + content);

    and("player cancels to return to view mode");
    click(context, "btn-cancel");
    waitForViewMode(context);
  }

  @Test
  @Order(7)
  @PlayerA
  @DisplayName("Content with only wiki-link syntax saves and view mode is stable")
  void contentOnlyWikiLink(ClientGameTestContext context) {
    given("player returns to MainScreen");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    openMainScreen(context);
    waitForEntryCount(context, 2);

    when("player opens 'Link Source' in edit mode");
    clickEntryByTitle(context, "Link Source");
    click(context, "btn-open");
    waitForViewMode(context);
    click(context, "btn-edit");
    waitForEditMode(context);

    and("replaces content with a bare wiki-link");
    type(context, "content-field", "[[Link Target]]");
    click(context, "btn-save");

    then("view mode loads without crash");
    waitForViewMode(context);
    assertScreenIs(context, QuestScreen.class);
    assertComponentExists(context, "content-area");

    and("quest is still in cache");
    context.waitFor(
        client ->
            ClientCache.getMyQuests().stream().anyMatch(q -> "Link Source".equals(q.getTitle())),
        TIMEOUT);
  }

  @Test
  @Order(8)
  @PlayerA
  @DisplayName("Wiki-link on standalone line renders in view mode after re-open")
  void standaloneWikiLinkRendersAfterReopen(ClientGameTestContext context) {
    given("'Link Source' has only a wiki-link as content");
    // Re-open from list to get server-resolved content
    click(context, "btn-back");
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

  @Test
  @Order(9)
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
        content.matches("(?s).*\\[\\[[0-9a-f-]{36}\\|.*"),
        "Content should NOT contain UUID pipe format, got: " + content);

    and("player cancels to return to view mode");
    click(context, "btn-cancel");
    waitForViewMode(context);
  }

  @Test
  @Order(10)
  @PlayerA
  @DisplayName("Hover over wiki-link in view mode shows preview popup")
  void hoverWikiLinkShowsPreview(ClientGameTestContext context) {
    given("'Link Source' is in view mode with a resolved wiki-link");
    assertScreenIs(context, QuestScreen.class);
    assertComponentExists(context, "content-area");

    when("player hovers mouse over the wiki-link text in the content area");
    var contentArea =
        findComponent(context, io.wispforest.owo.ui.container.FlowLayout.class, "content-area");
    double scale = scaleFactor(context);
    // Position cursor over the first line of content (where the wiki-link renders)
    double[] pos =
        context.computeOnClient(c -> new double[] {contentArea.x() + 20.0, contentArea.y() + 12.0});
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.waitTicks(3);

    then("hover preview is visible on the MarkdownWidget");
    boolean previewShown =
        context.computeOnClient(
            c -> {
              if (!(c.screen instanceof DisquestsBaseScreen dScreen)) return false;
              var root = dScreen.getRootComponent();
              if (root == null) return false;
              var area =
                  root.childById(io.wispforest.owo.ui.container.FlowLayout.class, "content-area");
              if (area == null) return false;
              for (var child : area.children()) {
                if (child instanceof com.disqt.disquests.client.gui.widget.MarkdownWidget mw) {
                  return mw.isPreviewVisible();
                }
              }
              return false;
            });
    assertTrue(previewShown, "Hover preview should be visible over wiki-link");
  }

  @Test
  @Order(11)
  @PlayerA
  ("Click wiki-link in view mode navigates to linked quest")
  void clickWikiLinkNavigatesToQuest(ClientGameTestContext context) {
    given("'Link Source' is in view mode with a resolved wiki-link");
    assertScreenIs(context, QuestScreen.class);

    when("player clicks the wiki-link text in the content area");
    var contentArea =
        findComponent(context, io.wispforest.owo.ui.container.FlowLayout.class, "content-area");
    double scale = scaleFactor(context);
    double[] pos =
        context.computeOnClient(c -> new double[] {contentArea.x() + 20.0, contentArea.y() + 12.0});
    context.getInput().setCursorPos(pos[0] * scale, pos[1] * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(5);

    then("screen navigates to the 'Link Target' quest");
    assertScreenIs(context, QuestScreen.class);
    assertLabelText(context, "title-label", "Link Target");
  }
}
