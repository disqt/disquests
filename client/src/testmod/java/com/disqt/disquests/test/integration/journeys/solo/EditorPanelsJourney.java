package com.disqt.disquests.test.integration.journeys.solo;

import static com.disqt.disquests.test.integration.bdd.BDD.*;
import static com.disqt.disquests.test.integration.bdd.UIActions.*;
import static com.disqt.disquests.test.integration.bdd.UIAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import com.disqt.disquests.test.integration.harness.IntegrationTest;
import com.disqt.disquests.test.integration.harness.PlayerA;
import com.disqt.disquests.test.integration.harness.TestContext;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The panels a player can put away: the editor's formatting help ({@code btn-formatting}, config
 * {@code formattingHelp}) and the quest tree pane of the reading view and the editor ({@code
 * btn-tree-collapse} / {@code btn-tree-expand}, config {@code questPaneOpen}). Neither rebuilds the
 * editor, so what was typed and the tree's unsaved moves stay; both choices are saved and apply the
 * next time a page opens. The last step lays the editor out on a 640x360 screen.
 */
@IntegrationTest
@DisplayName("Editor Panels Journey")
class EditorPanelsJourney {

  private static final String TOP = "Panels Top";
  private static final String CHILD = "Panels Child";
  private static final String DRAFT_TITLE = "Panels Draft";

  private static final String READ_PANE = "where-this-sits";
  private static final String EDIT_PANE = "edit-tree-pane";
  private static final String DIVIDER = "quest-pane-divider";
  private static final String COLLAPSE = "btn-tree-collapse";
  private static final String EXPAND = "btn-tree-expand";
  private static final String FORMATTING_PANEL = "formatting-panel";
  private static final String FORMATTING_BUTTON = "btn-formatting";

  private static boolean savedPaneOpen;
  private static boolean savedFormatting;
  private static int savedWindowWidth;
  private static int savedWindowHeight;
  private static UUID child;

  @BeforeAll
  static void resetServer() throws Exception {
    resetServerAndSync();
    AbortOnFailureExtension.clearFailures();
    ClientGameTestContext context = TestContext.get();
    savedPaneOpen = context.computeOnClient(c -> DisquestsClient.CONFIG.questPaneOpen());
    savedFormatting = context.computeOnClient(c -> DisquestsClient.CONFIG.formattingHelp());
    savedWindowWidth = context.computeOnClient(c -> c.getWindow().getWidth());
    savedWindowHeight = context.computeOnClient(c -> c.getWindow().getHeight());
    setChoices(context, true, true);
  }

  /** Both choices are client config, kept in a file: put back what the journey found. */
  @AfterAll
  static void restore() {
    ClientGameTestContext context = TestContext.get();
    setChoices(context, savedPaneOpen, savedFormatting);
    context.getInput().resizeWindow(savedWindowWidth, savedWindowHeight);
    context.waitTicks(2);
  }

  private static void setChoices(ClientGameTestContext context, boolean pane, boolean formatting) {
    context.runOnClient(
        c -> {
          DisquestsClient.CONFIG.questPaneOpen(pane);
          DisquestsClient.CONFIG.formattingHelp(formatting);
          DisquestsClient.CONFIG.save();
        });
  }

  @Test
  @Order(1)
  @PlayerA
  @DisplayName("The formatting help hides and shows without touching the draft")
  void formattingHelpHides(ClientGameTestContext context) {
    given("a quest with a subquest, open in the editor with a new title typed");
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
        });
    UUID top = createQuest(context, TOP, null);
    child = createQuest(context, CHILD, null);
    openQuest(context, "tab-my-quests", TOP);
    enterEditor(context);
    pickSuggestion(context, "add-subquest-field", CHILD);
    saveEditor(context);
    waitForQuestCondition(context, CHILD, true, q -> top.equals(q.getParentId()));
    enterEditor(context);
    assertComponentExists(context, FORMATTING_PANEL);
    type(context, "title-field", DRAFT_TITLE);
    int editorWidth = width(context, "editor-panel");

    when("player presses the Formatting button");
    click(context, FORMATTING_BUTTON);

    then("the help is gone, the text editor takes its width and the choice is saved");
    waitForMissing(context, FORMATTING_PANEL);
    context.waitFor(c -> !DisquestsClient.CONFIG.formattingHelp(), TIMEOUT);
    assertTrue(
        width(context, "editor-panel") > editorWidth,
        "the text editor is wider than " + editorWidth + " without the help");

    and("the typed title is still there: the editor was not rebuilt");
    assertEquals(DRAFT_TITLE, titleText(context));

    when("player presses it again");
    click(context, FORMATTING_BUTTON);

    then("the help is back, with the editor as narrow as before");
    waitForComponent(context, FORMATTING_PANEL);
    context.waitFor(c -> DisquestsClient.CONFIG.formattingHelp(), TIMEOUT);
    assertEquals(editorWidth, width(context, "editor-panel"));
    assertEquals(DRAFT_TITLE, titleText(context));

    when("player hides it, discards the edit and opens the editor again");
    click(context, FORMATTING_BUTTON);
    waitForMissing(context, FORMATTING_PANEL);
    discardEdit(context);
    enterEditor(context);

    then("the editor opens without the help");
    assertComponentMissing(context, FORMATTING_PANEL);
    assertComponentExists(context, FORMATTING_BUTTON);

    and("showing it again brings every line back");
    click(context, FORMATTING_BUTTON);
    waitForComponent(context, FORMATTING_PANEL);
    assertComponentExists(context, "fmt-wikilink");
    click(context, "btn-cancel");
    waitForComponent(context, "btn-edit");
  }

  @Test
  @Order(2)
  @PlayerA
  @DisplayName("The reading view's tree pane collapses to a strip and stays so")
  void readingPaneCollapses(ClientGameTestContext context) {
    given("the quest page with its tree pane");
    openQuest(context, "tab-my-quests", TOP);
    waitForComponent(context, READ_PANE);
    assertComponentMissing(context, EXPAND);
    int columnWidth = width(context, "quest-column");

    when("player presses the collapse button in the pane's header");
    click(context, COLLAPSE);

    then("the pane and its divider are gone, a strip with the expand button is at the left edge");
    waitForMissing(context, READ_PANE);
    assertComponentMissing(context, DIVIDER);
    waitForComponent(context, EXPAND);
    assertTrue(left(context, EXPAND) < 12, "the expand button is at the left edge");
    assertTrue(width(context, "quest-column") > columnWidth, "the page takes the freed width");
    context.waitFor(c -> !DisquestsClient.CONFIG.questPaneOpen(), TIMEOUT);

    and("the page opens collapsed the next time");
    openQuest(context, "tab-my-quests", TOP);
    waitForComponent(context, EXPAND);
    assertComponentMissing(context, READ_PANE);

    when("player presses the expand button");
    click(context, EXPAND);

    then("the pane is back with its rows and its divider");
    waitForComponent(context, READ_PANE);
    assertComponentExists(context, DIVIDER);
    assertComponentMissing(context, EXPAND);
    context.waitFor(c -> treeRow(c, child) != null, TIMEOUT);
    context.waitFor(c -> DisquestsClient.CONFIG.questPaneOpen(), TIMEOUT);
    assertEquals(columnWidth, width(context, "quest-column"));
  }

  @Test
  @Order(3)
  @PlayerA
  @DisplayName("The editor's tree pane collapses and comes back with the draft untouched")
  void editorPaneCollapses(ClientGameTestContext context) {
    given("the editor with a new title typed and the subquest taken out, nothing saved");
    openQuest(context, "tab-my-quests", TOP);
    enterEditor(context);
    type(context, "title-field", DRAFT_TITLE);
    clickTreeRowRemove(context, child);
    waitForTreeRow(context, child, l -> l.takenOut());

    when("player collapses the pane");
    click(context, COLLAPSE);

    then("the pane is gone and the title is still there");
    waitForMissing(context, EDIT_PANE);
    assertComponentMissing(context, DIVIDER);
    waitForComponent(context, EXPAND);
    context.waitFor(c -> !DisquestsClient.CONFIG.questPaneOpen(), TIMEOUT);
    assertEquals(DRAFT_TITLE, titleText(context));

    when("player expands it again");
    click(context, EXPAND);

    then("the pane shows the same unsaved tree, and the title is still there");
    waitForComponent(context, EDIT_PANE);
    waitForTreeRow(context, child, l -> l.takenOut());
    assertComponentExists(context, "tree-hint");
    assertComponentExists(context, "add-subquest-field");
    assertEquals(DRAFT_TITLE, titleText(context));

    when("player collapses it, discards the edit and is back on the quest page");
    click(context, COLLAPSE);
    waitForMissing(context, EDIT_PANE);
    discardEdit(context);

    then("the page's pane is collapsed too: the choice is shared");
    waitForComponent(context, EXPAND);
    assertComponentMissing(context, READ_PANE);

    and("the editor opens collapsed, with nothing of the discarded edit");
    click(context, "btn-edit");
    waitForComponent(context, "title-field");
    assertComponentMissing(context, EDIT_PANE);
    assertEquals(TOP, titleText(context));
    click(context, EXPAND);
    waitForComponent(context, EDIT_PANE);
    waitForTreeRow(context, child, l -> !l.takenOut() && l.depth() == 1);
    click(context, "btn-cancel");
    waitForComponent(context, "btn-edit");
    waitForComponent(context, READ_PANE);
  }

  @Test
  @Order(4)
  @PlayerA
  @DisplayName("On a 640x360 screen the pane's hints are whole and the help scrolls")
  void smallScreen(ClientGameTestContext context) {
    given("a 640x360 screen, as a 1080p display gives at GUI scale 3");
    openMainScreen(context);
    context.getInput().resizeWindow(640, 360);
    context.waitFor(c -> c.gui.screen() != null && c.gui.screen().width == 640, TIMEOUT);
    int screenHeight = context.computeOnClient(c -> c.gui.screen().height);
    assertEquals(360, screenHeight);

    when("player opens the editor of a quest in a tree");
    openQuest(context, "tab-my-quests", TOP);
    enterEditor(context);
    waitForComponent(context, "tree-hint");
    context.waitTicks(2);

    then("the drop strip and the hint are whole, inside the pane, above the screen's bottom edge");
    int paneBottom = bottom(context, EDIT_PANE);
    assertTrue(paneBottom <= screenHeight, "pane ends at " + paneBottom);
    int hintBottom = bottom(context, "tree-hint");
    assertTrue(hintBottom <= paneBottom, "hint ends at " + hintBottom + ", pane at " + paneBottom);
    assertTrue(
        bottom(context, "tree-drop-out") <= top(context, "tree-hint"),
        "the drop strip is above the hint");
    assertTrue(
        top(context, "edit-tree-list") >= bottom(context, COLLAPSE) - 12,
        "the list is under the header");

    and("the formatting help stays inside its row, every line reachable by scrolling");
    int panelBottom = bottom(context, FORMATTING_PANEL);
    assertTrue(
        panelBottom <= bottom(context, "content-row"),
        "help ends at " + panelBottom + ", its row at " + bottom(context, "content-row"));
    ScrollContainer<?> scroll = findComponent(context, ScrollContainer.class, "formatting-scroll");
    int linesHeight = context.computeOnClient(c -> scroll.child().height());
    int scrollHeight = context.computeOnClient(c -> scroll.height());
    assertTrue(scrollHeight > 0, "the help has some height");
    if (linesHeight > scrollHeight) {
      scrollOver(context, "formatting-scroll", -20);
      int scrollBottom = bottom(context, "formatting-scroll");
      int[] last = {0};
      try {
        context.waitFor(
            c -> {
              UIComponent line = onScreen(c, UIComponent.class, "fmt-wikilink");
              last[0] = line == null ? -1 : line.y() + line.height();
              return line != null && last[0] <= scrollBottom;
            },
            TIMEOUT);
      } catch (Throwable t) {
        throw new AssertionError(
            "Last line ends at " + last[0] + " after scrolling, the scroll at " + scrollBottom, t);
      }
    }

    and("Save and Cancel are on screen");
    assertTrue(
        bottom(context, "btn-save") <= screenHeight, "Save ends at " + bottom(context, "btn-save"));
    click(context, "btn-cancel");
    waitForComponent(context, "btn-edit");
  }

  // --- helpers ---

  /** Cancel with unsaved changes: confirm the discard, back on the reading view. */
  private static void discardEdit(ClientGameTestContext context) {
    click(context, "btn-cancel");
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);
    waitForComponent(context, "btn-edit");
  }

  private static void waitForMissing(ClientGameTestContext context, String id) {
    try {
      context.waitFor(
          c ->
              c.gui.screen() instanceof DisquestsBaseScreen
                  && onScreen(c, UIComponent.class, id) == null,
          TIMEOUT);
    } catch (Throwable t) {
      throw new AssertionError("Component '" + id + "' is still on screen", t);
    }
  }

  private static String titleText(ClientGameTestContext context) {
    TextFieldComponent field = findComponent(context, TextFieldComponent.class, "title-field");
    return context.computeOnClient(c -> field.getText());
  }

  private static int width(ClientGameTestContext context, String id) {
    FlowLayout component = findComponent(context, FlowLayout.class, id);
    return context.computeOnClient(c -> component.width());
  }

  private static int left(ClientGameTestContext context, String id) {
    UIComponent component = findComponent(context, UIComponent.class, id);
    return context.computeOnClient(c -> component.x());
  }

  private static int top(ClientGameTestContext context, String id) {
    UIComponent component = findComponent(context, UIComponent.class, id);
    return context.computeOnClient(c -> component.y());
  }

  private static int bottom(ClientGameTestContext context, String id) {
    UIComponent component = findComponent(context, UIComponent.class, id);
    return context.computeOnClient(c -> component.y() + component.height());
  }

  /** Scrolls the wheel with the cursor over a component. */
  private static void scrollOver(ClientGameTestContext context, String id, double amount) {
    UIComponent component = findComponent(context, UIComponent.class, id);
    double[] at = componentCenter(context, component);
    double scale = scaleFactor(context);
    context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
    context.waitTicks(1);
    context.getInput().scroll(amount);
    context.waitTicks(4);
  }
}
