package com.disqt.disquests.test.integration.bdd;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.client.gui.component.SuggestionDropdown;
import com.disqt.disquests.client.gui.component.TreeRowComponent;
import com.disqt.disquests.client.gui.component.TreeRowGeometry;
import com.disqt.disquests.client.gui.map.MapView;
import com.disqt.disquests.client.gui.map.OpeningGuard;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.gui.screen.MapEditScreen;
import com.disqt.disquests.client.gui.screen.QuestListPanel;
import com.disqt.disquests.test.integration.harness.RconClient;
import com.disqt.disquests.test.integration.harness.TestContext;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI interaction helpers using physical input (TestInput). Never call screen methods directly --
 * always go through the real input pipeline.
 */
public final class UIActions {
  private static final Logger LOG = LoggerFactory.getLogger("Disquests.E2E");

  /** Convert seconds to game ticks (20 ticks/second). */
  public static int seconds(int s) {
    return s * 20;
  }

  public static final int CONNECT_TIMEOUT = seconds(30);
  public static final int TIMEOUT = seconds(10);

  private static final double PIN_ICON_CLICK_X = 10.0; // offset from right edge
  private static final double PIN_ICON_CLICK_Y = 19.0; // offset from entry top

  private UIActions() {}

  // --- Component lookup helpers ---

  /**
   * Find a component by ID on the current Disquests screen. Runs on the client thread. Throws
   * AssertionError if screen or component not found.
   */
  public static <T extends UIComponent> T findComponent(
      ClientGameTestContext context, Class<T> type, String id) {
    // Wait for a Disquests screen to be active (handles brief screen transitions on CI)
    context.waitFor(client -> client.gui.screen() instanceof DisquestsBaseScreen, TIMEOUT);
    return context.computeOnClient(
        c -> {
          if (c.gui.screen() instanceof DisquestsBaseScreen dScreen) {
            var root = dScreen.getRootComponent();
            if (root == null) throw new AssertionError("Screen root not initialized");
            T component = root.childById(type, id);
            if (component == null) throw new AssertionError("Component not found: " + id);
            return component;
          }
          throw new AssertionError("Current screen is not a Disquests screen");
        });
  }

  /** Find a component by ID, returning null if not found. */
  public static <T extends UIComponent> T findComponentOrNull(
      ClientGameTestContext context, Class<T> type, String id) {
    return context.computeOnClient(
        c -> {
          if (c.gui.screen() instanceof DisquestsBaseScreen dScreen) {
            var root = dScreen.getRootComponent();
            if (root == null) return null;
            return root.childById(type, id);
          }
          return null;
        });
  }

  /** Get the center coordinates of a component (screen-space, pre-scale). */
  public static double[] componentCenter(ClientGameTestContext context, UIComponent component) {
    return context.computeOnClient(
        c ->
            new double[] {
              component.x() + component.width() / 2.0, component.y() + component.height() / 2.0
            });
  }

  /** Get the window scale factor. */
  public static double scaleFactor(ClientGameTestContext context) {
    return context.computeOnClient(c -> (double) c.getWindow().getGuiScale());
  }

  // --- Server reset ---

  /**
   * Sends RCON "disquests reset" to wipe the server DB, then clears client cache and requests a
   * fresh sync. Waits for the sync to complete. Call this from @BeforeAll in solo journey classes.
   */
  public static void resetServerAndSync() throws Exception {
    // RCON reset: wipes DB, triggers re-handshake
    var rcon =
        new RconClient(
            "localhost", Integer.parseInt(System.getProperty("disquests.test.rcon.port", "25575")));
    rcon.login(System.getProperty("disquests.test.rcon.password", "testpassword"));
    rcon.command("disquests reset");
    rcon.close();

    // Clear client cache and close screens
    ClientGameTestContext context = TestContext.get();
    context.runOnClient(
        c -> {
          ClientCache.clear();
          if (c.gui.screen() != null) c.gui.setScreen(null);
        });

    // Request sync and wait for cache to update (version bumps when SYNC packets arrive)
    // Request sync and wait for cache to update (SYNC packets bump version)
    long versionBefore = context.computeOnClient(c -> ClientCache.getVersion());
    context.runOnClient(
        c -> {
          com.disqt.disquests.client.network.PacketSender.requestSync();
        });
    context.waitFor(client -> ClientCache.getVersion() != versionBefore, TIMEOUT);
  }

  // --- Connection ---

  public static void connectAndWait(ClientGameTestContext context) {
    String host = System.getProperty("disquests.test.server.host", "localhost");
    int port = Integer.getInteger("disquests.test.server.port", 25565);
    String address = host + ":" + port;

    context.runOnClient(
        client -> {
          ServerAddress serverAddress = ServerAddress.parseString(address);
          ServerData serverData = new ServerData("Test", address, ServerData.Type.OTHER);
          ConnectScreen.startConnecting(
              client.gui.screen(), client, serverAddress, serverData, false, null);
        });

    // Wait for player entity
    context.waitFor(client -> client.player != null, CONNECT_TIMEOUT);
    // Wait for Disquests handshake
    context.waitFor(client -> ClientSession.isOnServer(), CONNECT_TIMEOUT);
    context.waitTicks(10);
  }

  public static void disconnect(ClientGameTestContext context) {
    context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
    context.waitForScreen(TitleScreen.class);
  }

  // --- Screen navigation ---

  public static void openMainScreen(ClientGameTestContext context) {
    context.runOnClient(client -> client.gui.setScreen(new MainScreen(null)));
    context.waitForScreen(MainScreen.class);
    context.waitTicks(2);
  }

  public static <T extends Screen> void waitForScreen(
      ClientGameTestContext context, Class<T> screenClass) {
    context.waitFor(client -> screenClass.isInstance(client.gui.screen()), TIMEOUT);
    context.waitTicks(2);
  }

  /**
   * Waits for the full-screen map editor, then until its {@link OpeningGuard} window has passed:
   * the editor drops left clicks that arrive sooner (the second half of a double-click on the
   * preview), and game-test ticks are not paced in real time. The wait reads the guard's clock and
   * starts once the screen is seen, which is after it opened.
   */
  public static void waitForMapEditor(ClientGameTestContext context) {
    waitForScreen(context, MapEditScreen.class);
    long seen = System.currentTimeMillis();
    context.waitFor(client -> System.currentTimeMillis() - seen >= OpeningGuard.WINDOW_MS, TIMEOUT);
  }

  // --- Component interaction ---

  /**
   * Left-click at a GUI-space position through the real input pipeline (cursor move + press), then
   * wait for the click to be processed.
   */
  public static void clickAt(ClientGameTestContext context, double[] guiPos) {
    clickAt(context, guiPos, 2);
  }

  public static void clickAt(ClientGameTestContext context, double[] guiPos, int waitTicks) {
    double scale = scaleFactor(context);
    context.getInput().setCursorPos(guiPos[0] * scale, guiPos[1] * scale);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(waitTicks);
  }

  /** Click a component by its XML/programmatic ID. */
  public static void click(ClientGameTestContext context, String componentId) {
    var component = findComponent(context, UIComponent.class, componentId);
    double[] pos = componentCenter(context, component);
    clickAt(context, pos);
  }

  /**
   * Click a QuestEntryComponent by index in the quest list. Each child of the "quest-list"
   * FlowLayout is a row holding one entry (or a section header); the entry itself is clicked.
   */
  public static void clickEntry(ClientGameTestContext context, int index) {
    var questList =
        findComponent(context, io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
    double[] pos =
        context.computeOnClient(
            c -> {
              var children = questList.children();
              if (index >= children.size())
                throw new AssertionError(
                    "Entry index " + index + " out of bounds, size=" + children.size());
              var child = children.get(index);
              var entry = com.disqt.disquests.client.gui.screen.QuestListPanel.entryOf(child);
              var target = entry != null ? entry : child;
              return new double[] {
                target.x() + target.width() / 2.0, target.y() + target.height() / 2.0
              };
            });
    clickAt(context, pos);
  }

  /**
   * Click a QuestEntryComponent by matching quest title. Searches all children of "quest-list" for
   * a QuestEntryComponent with the given title.
   */
  public static void clickEntryByTitle(ClientGameTestContext context, String title) {
    var questList =
        findComponent(context, io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
    double[] pos =
        context.computeOnClient(
            c -> {
              for (var entry :
                  com.disqt.disquests.client.gui.screen.QuestListPanel.entries(questList)) {
                if (title.equals(entry.getQuest().getTitle())) {
                  return new double[] {
                    entry.x() + entry.width() / 2.0, entry.y() + entry.height() / 2.0
                  };
                }
              }
              throw new AssertionError("No entry with title '" + title + "' found in quest list");
            });
    clickAt(context, pos);
  }

  /** Double-click a QuestEntryComponent by index. */
  public static void doubleClickEntry(ClientGameTestContext context, int index) {
    clickEntry(context, index);
    context.waitTicks(1);
    clickEntry(context, index);
  }

  /**
   * Click the pin icon on a QuestEntryComponent by index. Pin icon is in the rightmost 20px of the
   * entry, vertically centered.
   */
  public static void clickPinIcon(ClientGameTestContext context, int index) {
    var questList =
        findComponent(context, io.wispforest.owo.ui.container.FlowLayout.class, "quest-list");
    double[] pos =
        context.computeOnClient(
            c -> {
              var child = questList.children().get(index);
              var entry = com.disqt.disquests.client.gui.screen.QuestListPanel.entryOf(child);
              var target = entry != null ? entry : child;
              return new double[] {
                target.x() + target.width() - PIN_ICON_CLICK_X, target.y() + PIN_ICON_CLICK_Y
              };
            });
    clickAt(context, pos);
  }

  /**
   * Type text into a focused text field. Must click the field first to focus it. Clears existing
   * text by directly manipulating the widget, then types the new text via character input. Handles
   * both TextFieldComponent (our custom multi-line) and TextBoxComponent (owo-ui single-line).
   */
  public static void type(ClientGameTestContext context, String componentId, String text) {
    click(context, componentId);
    context.waitTicks(1); // focus desync workaround for GreedyInputUIComponent
    // Clear existing text by directly manipulating the widget.
    // Ctrl+A routing can be unreliable through owo-ui layers,
    // so we clear via runOnClient for robustness.
    context.runOnClient(
        c -> {
          if (c.gui.screen() instanceof DisquestsBaseScreen dScreen) {
            var root = dScreen.getRootComponent();
            if (root != null) {
              // Find component by ID as generic UIComponent, then check type
              var component = root.childById(UIComponent.class, componentId);
              if (component
                  instanceof
                  com.disqt.disquests.client.gui.component.TextFieldComponent textField) {
                textField.getDelegate().setText("");
                textField.getDelegate().setFocused(true);
              } else if (component
                  instanceof io.wispforest.owo.ui.component.TextBoxComponent textBox) {
                textBox.text("");
              }
            }
          }
        });
    context.waitTicks(1);
    // Re-click to ensure owo-ui focus is restored after setText (which clears focused)
    click(context, componentId);
    context.waitTicks(1);
    // Type new text
    context.getInput().typeChars(text);
    context.waitTicks(2);
  }

  /**
   * Like {@link #type} but newlines become Enter key presses. {@code typeChars} sends a bare
   * newline as a character event, which the editor drops (not an allowed chat character), so a
   * multi-line string typed with {@link #type} ends up on one line.
   */
  public static void typeMultiline(ClientGameTestContext context, String componentId, String text) {
    String[] lines = text.split("\n", -1);
    type(context, componentId, lines[0]);
    for (int i = 1; i < lines.length; i++) {
      context.getInput().pressKey(InputConstants.KEY_RETURN);
      context.waitTicks(1);
      if (!lines[i].isEmpty()) context.getInput().typeChars(lines[i]);
      context.waitTicks(1);
    }
    context.waitTicks(2);
  }

  /** Append text to a text field without clearing existing content. */
  public static void appendText(ClientGameTestContext context, String componentId, String text) {
    click(context, componentId);
    context.waitTicks(1);
    // Move to end (Ctrl+End)
    context.getInput().holdControl();
    context.getInput().pressKey(InputConstants.KEY_END);
    context.getInput().releaseControl();
    context.getInput().typeChars(text);
    context.waitTicks(2);
  }

  /** Press Ctrl+Z (undo) on the currently focused field. */
  public static void undo(ClientGameTestContext context) {
    context.getInput().holdControl();
    context.getInput().pressKey(InputConstants.KEY_Z);
    context.getInput().releaseControl();
    context.waitTicks(2);
  }

  /**
   * Press Ctrl+Z repeatedly to undo N actions. Each typeChars character creates a separate undo
   * action, so use this to undo an entire typed string.
   */
  public static void undoN(ClientGameTestContext context, int count) {
    for (int i = 0; i < count; i++) {
      context.getInput().holdControl();
      context.getInput().pressKey(InputConstants.KEY_Z);
      context.getInput().releaseControl();
    }
    context.waitTicks(2);
  }

  /** Press Ctrl+Y (redo) on the currently focused field. */
  public static void redo(ClientGameTestContext context) {
    context.getInput().holdControl();
    context.getInput().pressKey(InputConstants.KEY_Y);
    context.getInput().releaseControl();
    context.waitTicks(2);
  }

  /** Press Ctrl+Y repeatedly to redo N actions. */
  public static void redoN(ClientGameTestContext context, int count) {
    for (int i = 0; i < count; i++) {
      context.getInput().holdControl();
      context.getInput().pressKey(InputConstants.KEY_Y);
      context.getInput().releaseControl();
    }
    context.waitTicks(2);
  }

  /** Press a key (InputConstants key code) on the current screen, then wait for it to land. */
  public static void pressKey(ClientGameTestContext context, int key) {
    context.getInput().pressKey(key);
    context.waitTicks(2);
  }

  /**
   * Scroll the mouse wheel at a GUI-space position. Positive amounts scroll up (zoom in on the
   * map), negative amounts scroll down.
   */
  public static void scrollAt(ClientGameTestContext context, double[] guiPos, double amount) {
    double scale = scaleFactor(context);
    context.getInput().setCursorPos(guiPos[0] * scale, guiPos[1] * scale);
    context.getInput().scroll(amount);
    context.waitTicks(2);
  }

  // --- Map helpers ---

  /** The MainScreen's map component (id "map-view"). */
  public static MapView mapView(ClientGameTestContext context) {
    return findComponent(context, MapView.class, "map-view");
  }

  /** GUI-space screen position of a block on the map, using the map's current camera. */
  public static double[] mapScreenPos(ClientGameTestContext context, double bx, double bz) {
    MapView view = mapView(context);
    return context.computeOnClient(
        c -> {
          double[] rel = view.blockToScreen(bx, bz);
          return new double[] {view.x() + rel[0], view.y() + rel[1]};
        });
  }

  /** Left-click the map at a block position through the real input pipeline. */
  public static void clickMapAtBlock(ClientGameTestContext context, double bx, double bz) {
    clickAt(context, mapScreenPos(context, bx, bz));
  }

  // --- Quest helpers ---

  /**
   * Wait for a quest to appear in cache by title.
   *
   * @param myQuests true = search myQuests, false = search serverQuests
   */
  public static Quest waitForQuestByTitle(
      ClientGameTestContext context, String title, boolean myQuests) {
    context.waitFor(
        client -> {
          var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
          return list.stream().anyMatch(q -> title.equals(q.getTitle()));
        },
        TIMEOUT);
    return context.computeOnClient(
        c -> {
          var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
          return list.stream().filter(q -> title.equals(q.getTitle())).findFirst().orElse(null);
        });
  }

  /**
   * Wait for a quest matching title to satisfy a condition in the cache.
   *
   * @param myQuests true = search myQuests, false = search serverQuests
   */
  public static void waitForQuestCondition(
      ClientGameTestContext context,
      String title,
      boolean myQuests,
      java.util.function.Predicate<Quest> condition) {
    context.waitFor(
        client -> {
          var list = myQuests ? ClientCache.getMyQuests() : ClientCache.getServerQuests();
          return list.stream()
              .filter(q -> title.equals(q.getTitle()))
              .findFirst()
              .map(condition::test)
              .orElse(false);
        },
        TIMEOUT);
  }

  /** Wait for a quest to be removed from myQuests. */
  public static void waitForQuestRemoved(ClientGameTestContext context, UUID questId) {
    context.waitFor(
        client -> ClientCache.getMyQuests().stream().noneMatch(q -> q.getId().equals(questId)),
        TIMEOUT);
  }

  /** Wait for quest list in MainScreen to have exactly N entries. */
  public static void waitForEntryCount(ClientGameTestContext context, int count) {
    context.waitFor(
        client -> {
          Screen screen = client.gui.screen();
          if (screen instanceof DisquestsBaseScreen dScreen) {
            var root = dScreen.getRootComponent();
            var questList =
                root != null
                    ? root.childById(io.wispforest.owo.ui.container.FlowLayout.class, "quest-list")
                    : null;
            return questList != null && questList.children().size() == count;
          }
          return false;
        },
        TIMEOUT);
  }

  /**
   * Wait for a component to appear on the current Disquests screen. Useful when programmatic
   * components are added after the screen class is set.
   */
  public static void waitForComponent(ClientGameTestContext context, String componentId) {
    context.waitFor(
        client -> {
          if (client.gui.screen() instanceof DisquestsBaseScreen screen) {
            return screen
                    .getRootComponent()
                    .childById(io.wispforest.owo.ui.core.UIComponent.class, componentId)
                != null;
          }
          return false;
        },
        TIMEOUT);
  }

  // --- Quests ---

  /**
   * Creates a quest in the editor from the main screen: title, then the visibility button ({@code
   * vis-open}, {@code vis-closed}; null keeps Private), Save, back to the main screen. Returns its
   * id once the cache has it.
   */
  public static UUID createQuest(ClientGameTestContext context, String title, String visibilityId) {
    openMainScreen(context);
    // The Archived tab has no New button; the tab persists across screens
    click(context, "tab-my-quests");
    click(context, "btn-new-quest");
    waitForScreen(context, com.disqt.disquests.client.gui.screen.QuestScreen.class);
    type(context, "title-field", title);
    if (visibilityId != null) choose(context, visibilityId);
    click(context, "btn-save");
    // Same-class rebuild: the reading view has Edit, the editor has not
    waitForComponent(context, "btn-edit");
    click(context, "btn-back");
    waitForScreen(context, MainScreen.class);
    return waitForQuestByTitle(context, title, true).getId();
  }

  /** From a quest's reading view: Edit, then wait for the editor's tree pane field. */
  public static void enterEditor(ClientGameTestContext context) {
    click(context, "btn-edit");
    waitForComponent(context, "add-subquest-field");
  }

  /** Saves the editor and waits for the reading view (same-class rebuild). */
  public static void saveEditor(ClientGameTestContext context) {
    click(context, "btn-save");
    waitForComponent(context, "btn-edit");
  }

  /** Clicks the confirm dialog's Yes. */
  public static void confirmYes(ClientGameTestContext context) {
    waitForComponent(context, DisquestsBaseScreen.CONFIRM_OVERLAY_ID);
    click(context, DisquestsBaseScreen.CONFIRM_YES_ID);
  }

  // --- Buttons that show their state ---

  /** A component on the current Disquests screen, or null; for use inside waitFor. */
  public static <T extends UIComponent> T onScreen(
      net.minecraft.client.Minecraft client, Class<T> type, String id) {
    if (client.gui.screen() instanceof DisquestsBaseScreen screen
        && screen.getRootComponent() != null) {
      return screen.getRootComponent().childById(type, id);
    }
    return null;
  }

  /** Waits until the button exists and is enabled, then clicks it. */
  public static void clickWhenActive(ClientGameTestContext context, String id) {
    context.waitFor(
        client -> {
          ButtonComponent b = onScreen(client, ButtonComponent.class, id);
          return b != null && b.isActive();
        },
        TIMEOUT);
    click(context, id);
  }

  /**
   * Presses a choice button that shows the current choice pressed (visibility {@code vis-*}, role
   * {@code role-*-<uuid>}, {@code add-role-*}) and waits until it shows as chosen.
   */
  public static void choose(ClientGameTestContext context, String id) {
    click(context, id);
    context.waitFor(
        client -> {
          ButtonComponent b = onScreen(client, ButtonComponent.class, id);
          return b != null && !b.isActive();
        },
        TIMEOUT);
  }

  /** Ticks the confirm dialog's checkbox ({@code confirm-checkbox}) and waits for the tick. */
  public static void tickConfirmCheckbox(ClientGameTestContext context) {
    tickCheckbox(context, DisquestsBaseScreen.CONFIRM_CHECKBOX_ID);
  }

  /** Clicks a checkbox and waits until it shows ticked. */
  public static void tickCheckbox(ClientGameTestContext context, String id) {
    var box = findComponent(context, SmallCheckboxComponent.class, id);
    if (context.computeOnClient(c -> box.checked())) return;
    // The box itself, at the left of its label
    double[] pos = context.computeOnClient(c -> new double[] {box.x() + 4, box.y() + 4});
    clickAt(context, pos);
    context.waitFor(
        client -> {
          SmallCheckboxComponent b = onScreen(client, SmallCheckboxComponent.class, id);
          return b != null && b.checked();
        },
        TIMEOUT);
  }

  // --- Quest search fields ("add a subquest...", "Prerequisites") ---

  /**
   * Types {@code typed} into a quest search field and waits for its dropdown to list a row whose
   * text starts with {@code rowText}; returns that row.
   */
  public static SuggestionDropdown.Row typeAndFindSuggestion(
      ClientGameTestContext context, String fieldId, String typed, String rowText) {
    return typeAndFindSuggestion(context, fieldId, fieldId + "-suggestions", typed, rowText);
  }

  /** As above, for a field whose dropdown list has its own id ({@code add-name-suggestions}). */
  public static SuggestionDropdown.Row typeAndFindSuggestion(
      ClientGameTestContext context, String fieldId, String listId, String typed, String rowText) {
    type(context, fieldId, typed);
    try {
      context.waitFor(client -> suggestionRow(client, listId, rowText) != null, TIMEOUT);
    } catch (Throwable t) {
      throw new AssertionError(
          "No suggestion '"
              + rowText
              + "' after typing '"
              + typed
              + "': "
              + describeList(context, fieldId, listId),
          t);
    }
    return context.computeOnClient(c -> suggestionRow(c, listId, rowText));
  }

  /** The field's text and the list's rows, for a failure message. */
  private static String describeList(ClientGameTestContext context, String fieldId, String listId) {
    return context.computeOnClient(
        c -> {
          UIComponent field = onScreen(c, UIComponent.class, fieldId);
          String text =
              field instanceof io.wispforest.owo.ui.component.TextBoxComponent box
                  ? box.getValue()
                  : field instanceof com.disqt.disquests.client.gui.component.TextFieldComponent f
                      ? f.getText()
                      : String.valueOf(field);
          FlowLayout list = onScreen(c, FlowLayout.class, listId);
          java.util.List<String> rows = new java.util.ArrayList<>();
          if (list != null) {
            for (UIComponent child : list.children()) {
              if (child instanceof SuggestionDropdown.Row row) rows.add(row.text());
            }
          }
          return "field='" + text + "', list " + (list == null ? "not shown" : rows);
        });
  }

  /**
   * Types a quest's title into a quest search field and clicks its (enabled) suggestion, as a
   * player picks from the list.
   */
  public static void pickSuggestion(ClientGameTestContext context, String fieldId, String title) {
    SuggestionDropdown.Row row = typeAndFindSuggestion(context, fieldId, title, title);
    boolean enabled = context.computeOnClient(c -> row.enabled());
    if (!enabled) {
      String text = context.computeOnClient(c -> row.text());
      throw new AssertionError("Suggestion '" + text + "' is greyed and cannot be picked");
    }
    clickAt(context, componentCenter(context, row));
  }

  /** The row of an open dropdown list whose text starts with {@code rowText}, or null. */
  public static SuggestionDropdown.Row suggestionRow(
      net.minecraft.client.Minecraft client, String listId, String rowText) {
    FlowLayout list = onScreen(client, FlowLayout.class, listId);
    if (list == null) return null;
    for (UIComponent child : list.children()) {
      if (child instanceof SuggestionDropdown.Row row && row.text().startsWith(rowText)) {
        return row;
      }
    }
    return null;
  }

  /** Clicks the x at the right end of a removable chip (tags, prerequisites). */
  public static void clickChipRemove(ClientGameTestContext context, String chipId) {
    var chip = findComponent(context, UIComponent.class, chipId);
    double[] pos =
        context.computeOnClient(
            c -> new double[] {chip.x() + chip.width() - 5, chip.y() + chip.height() / 2.0});
    clickAt(context, pos);
  }

  // --- People screen ---

  /** From a quest's reading view, opens its people screen ({@code btn-manage-people}). */
  public static void openPeople(ClientGameTestContext context) {
    click(context, "btn-manage-people");
    waitForScreen(context, com.disqt.disquests.client.gui.screen.ContributorScreen.class);
    waitForComponent(context, "add-name-field");
  }

  /**
   * On the people screen: types a player's name, picks it from the suggestions, chooses the role
   * ({@code add-role-read}, {@code add-role-write}, {@code add-role-owner}) and presses Add. The
   * person joins the draft; Save sends it.
   */
  public static void addPerson(ClientGameTestContext context, String name, String roleId) {
    // A name typed in full is not suggested (nothing left to complete): type all but its last
    // letter
    String partial = name.substring(0, name.length() - 1);
    SuggestionDropdown.Row row =
        typeAndFindSuggestion(context, "add-name-field", "add-name-suggestions", partial, name);
    clickAt(context, componentCenter(context, row));
    choose(context, roleId);
    clickWhenActive(context, "btn-add-person");
    // Added to the draft: Save becomes possible
    context.waitFor(
        client -> {
          ButtonComponent save = onScreen(client, ButtonComponent.class, "btn-save-people");
          return save != null && save.isActive();
        },
        TIMEOUT);
  }

  /** Saves the people draft; the screen returns to the one it was opened from. */
  public static void savePeople(ClientGameTestContext context) {
    clickWhenActive(context, "btn-save-people");
    waitForScreen(context, com.disqt.disquests.client.gui.screen.QuestScreen.class);
  }

  // --- Quest tree rows ---

  /** The {@code tree-row-<id>} on the current screen, or null. */
  public static TreeRowComponent treeRow(net.minecraft.client.Minecraft client, UUID questId) {
    return onScreen(client, TreeRowComponent.class, "tree-row-" + questId);
  }

  /** Waits until the quest's tree row exists and its line satisfies {@code condition}. */
  public static void waitForTreeRow(
      ClientGameTestContext context,
      UUID questId,
      java.util.function.Predicate<TreeRowComponent.Line> condition) {
    context.waitFor(
        client -> {
          TreeRowComponent row = treeRow(client, questId);
          return row != null && condition.test(row.line());
        },
        TIMEOUT);
  }

  /** Where a part of a tree row is, in GUI coordinates, for a click or a drop. */
  public static double[] treeRowPoint(
      ClientGameTestContext context, UUID questId, TreeRowGeometry.Part part) {
    TreeRowComponent row = findComponent(context, TreeRowComponent.class, "tree-row-" + questId);
    double[] point = context.computeOnClient(c -> row.pointFor(part));
    if (point == null) throw new AssertionError("Row of " + questId + " has no " + part);
    return point;
  }

  /** Clicks the remove zone (x) of a tree row in the editor. */
  public static void clickTreeRowRemove(ClientGameTestContext context, UUID questId) {
    clickAt(context, treeRowPoint(context, questId, TreeRowGeometry.Part.REMOVE));
  }

  /**
   * Drags a tree row in the editor to a point with the real mouse: press on the row's title, move
   * in small steps (past the drag threshold, as a hand does), release over the target.
   */
  public static void dragTreeRow(ClientGameTestContext context, UUID questId, double[] target) {
    double[] from = treeRowPoint(context, questId, TreeRowGeometry.Part.BODY);
    double scale = scaleFactor(context);
    var input = context.getInput();
    input.setCursorPos(from[0] * scale, from[1] * scale);
    context.waitTicks(1);
    input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(1);
    int steps = 8;
    for (int i = 1; i <= steps; i++) {
      double x = from[0] + (target[0] - from[0]) * i / steps;
      // Start sideways so the drag threshold is passed even between neighbouring rows
      double y = from[1] + (target[1] - from[1]) * i / steps + (i < steps ? 3 : 0);
      input.setCursorPos(x * scale, y * scale);
      context.waitTicks(1);
    }
    context.waitTicks(1);
    input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
  }

  /** Drags one tree row onto another (attach, or swap when the target is inside it). */
  public static void dragTreeRowOnto(ClientGameTestContext context, UUID questId, UUID target) {
    dragTreeRow(context, questId, treeRowPoint(context, target, TreeRowGeometry.Part.BODY));
  }

  /** Drags a tree row onto the "take out of the tree" strip ({@code tree-drop-out}). */
  public static void dragTreeRowOut(ClientGameTestContext context, UUID questId) {
    var strip = findComponent(context, UIComponent.class, "tree-drop-out");
    dragTreeRow(context, questId, componentCenter(context, strip));
  }

  // --- Lists ---

  /** The main screen's list entry for a quest title, or null when it is not listed. */
  public static QuestEntryComponent listedEntry(
      net.minecraft.client.Minecraft client, String title) {
    FlowLayout list = onScreen(client, FlowLayout.class, "quest-list");
    if (list == null) return null;
    for (QuestEntryComponent entry : QuestListPanel.entries(list)) {
      if (title.equals(entry.getQuest().getTitle())) return entry;
    }
    return null;
  }

  /** Waits until the current screen's quest list does (or does not) show an entry. */
  public static void waitForListed(ClientGameTestContext context, String title, boolean listed) {
    context.waitFor(client -> (listedEntry(client, title) != null) == listed, TIMEOUT);
  }

  /** The cached quest (mine or the board's) with this title, or null. */
  public static Quest cachedQuest(String title) {
    for (Quest q : ClientCache.getMyQuests()) if (title.equals(q.getTitle())) return q;
    for (Quest q : ClientCache.getServerQuests()) if (title.equals(q.getTitle())) return q;
    return null;
  }

  /** Waits until a quest with this title is cached (mine or the board's) and matches. */
  public static void waitForCachedQuest(
      ClientGameTestContext context, String title, java.util.function.Predicate<Quest> condition) {
    context.waitFor(
        client -> {
          Quest q = cachedQuest(title);
          return q != null && condition.test(q);
        },
        TIMEOUT);
  }

  /** Waits until no quest with this title is cached. */
  public static void waitForNotCached(ClientGameTestContext context, String title) {
    context.waitFor(client -> cachedQuest(title) == null, TIMEOUT);
  }

  /** Opens a listed quest from the main screen on the given tab, in view mode. */
  public static void openQuest(ClientGameTestContext context, String tabId, String title) {
    openMainScreen(context);
    click(context, tabId);
    waitForListed(context, title, true);
    clickEntryByTitle(context, title);
    click(context, "btn-open");
    waitForScreen(context, com.disqt.disquests.client.gui.screen.QuestScreen.class);
    // Same-class rebuilds: the title label shows the opened quest
    context.waitFor(
        client -> {
          var label =
              onScreen(client, io.wispforest.owo.ui.component.LabelComponent.class, "title-label");
          return label != null && title.equals(label.text().getString());
        },
        TIMEOUT);
  }

  // --- Duo journeys ---

  /**
   * Start of a duo journey class: both clients meet, then Player A resets the server and Player B
   * resyncs. Signal names are prefixed with {@code journey}, so classes never share one.
   */
  public static void resetServerForDuo(String journey) throws Exception {
    ClientGameTestContext context = TestContext.get();
    if (TestContext.isPlayerA()) {
      com.disqt.disquests.test.integration.PhaseSync.waitFor(
          journey + "-b-arrived", context, seconds(120));
      resetServerAndSync();
      com.disqt.disquests.test.integration.PhaseSync.signal(journey + "-reset");
    } else {
      com.disqt.disquests.test.integration.PhaseSync.signal(journey + "-b-arrived");
      com.disqt.disquests.test.integration.PhaseSync.waitFor(
          journey + "-reset", context, seconds(60));
      context.runOnClient(
          c -> {
            ClientCache.clear();
            if (c.gui.screen() != null) c.gui.setScreen(null);
          });
      long versionBefore = context.computeOnClient(c -> ClientCache.getVersion());
      context.runOnClient(c -> com.disqt.disquests.client.network.PacketSender.requestSync());
      context.waitFor(client -> ClientCache.getVersion() != versionBefore, TIMEOUT);
    }
    context.runOnClient(
        c -> {
          ClientSession.setActiveTab(ClientSession.Tab.MY_QUESTS);
          ClientSession.setSearchTerm("");
          ClientSession.setServerQuestsFilter(ClientSession.QuestFilter.ALL);
        });
  }

  /** Offline-mode UUID generation (matches Paper's offline UUID format). */
  public static UUID offlineUuid(String playerName) {
    return UUID.nameUUIDFromBytes(
        ("OfflinePlayer:" + playerName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /**
   * Check if this test journey should be skipped based on -Ddisquests.test.journey filter. Used by
   * HarnessPlayerA/B to decide whether to run.
   */
  public static boolean shouldSkip(String journeyName) {
    String filter = System.getProperty("disquests.test.journey");
    return filter != null && !filter.isEmpty() && !filter.equals(journeyName);
  }
}
