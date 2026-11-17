package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.data.QuestGraphs;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.client.gui.component.SplitterComponent;
import com.disqt.disquests.client.gui.component.SuggestionDropdown;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.map.BannerLayer;
import com.disqt.disquests.client.gui.map.MapCameraMemory;
import com.disqt.disquests.client.gui.map.MapMath;
import com.disqt.disquests.client.gui.map.MapQuestFilter;
import com.disqt.disquests.client.gui.map.MapView;
import com.disqt.disquests.client.gui.map.PlayerMarkerLayer;
import com.disqt.disquests.client.gui.map.QuestPolygonLayer;
import com.disqt.disquests.client.gui.map.SharedTileCache;
import com.disqt.disquests.client.gui.widget.ToastOverlay;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.DimensionKeys;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class MainScreen extends DisquestsBaseScreen {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger("Disquests.MainScreen");
  private static final List<ClientTooltipComponent> NO_TOOLTIP = Collections.emptyList();
  private static final List<String> DIMENSIONS = List.of("overworld", "the_nether", "the_end");
  private static final int INITIAL_ZOOM = 2;

  /** The search box's #tag autocomplete lists at most this many tags. */
  private static final int TAG_SUGGESTIONS = 8;

  // Component references (looked up by ID from XML model)
  private FlowLayout rootLayout;
  private LabelComponent titleLabel;
  private ButtonComponent tabMyQuests;
  private ButtonComponent tabQuestBoard;
  private ButtonComponent tabArchived;
  private FlowLayout tabRow;
  private FlowLayout filterRow;
  // Holds tab-row and filter-row: side-panel in the map layout, root in the legacy one
  private FlowLayout tabsParent;
  private ButtonComponent filterAll;
  private ButtonComponent filterOpen;
  private ButtonComponent filterClosed;
  private QuestListPanel listPanel;
  private FlowLayout searchRow;
  private ButtonComponent btnNewQuest;
  private ButtonComponent btnInteract;
  private ButtonComponent btnOpen;
  private FlowLayout actionRow;

  // Map mode (ClientSession.mapAvailable()); all null in legacy mode
  private final boolean mapMode;
  @Nullable private MapView mapView;
  @Nullable private QuestPolygonLayer polygonLayer;
  @Nullable private FlowLayout sidePanel;
  @Nullable private SplitterComponent panelDivider;
  @Nullable private ButtonComponent dimensionButton;
  // Popup under btn-dimension, added to the root with absolute positioning while open
  @Nullable private FlowLayout dimensionMenu;
  @Nullable private ButtonComponent zoomOut;
  @Nullable private ButtonComponent zoomIn;
  @Nullable private ButtonComponent listToggle;
  private final Map<String, ButtonComponent> dimensionButtons = new LinkedHashMap<>();
  private int shownZoom = -1;

  // Search
  private TextBoxComponent searchField;
  private String searchTerm;
  private SuggestionDropdown<String> tagDropdown;

  // State
  private ClientSession.Tab currentTab;
  private ClientSession.QuestFilter serverFilter;
  private int tickCounter = 0;
  private long lastCacheVersion = -1;
  private final ToastOverlay toast = new ToastOverlay();

  // Cached title width

  private int titleWidth;

  public MainScreen() {
    this(null);
  }

  /**
   * Opens the board (open key, inventory badge, back or forward to it). Each opening counts once
   * for the "NEW" marks; switching the map layout, rebuilding the list, a toggle or a tab switch do
   * not, nor does returning from a quest to this same screen (owo builds a screen only once).
   */
  public MainScreen(Screen parent) {
    this(parent, ClientSession.mapAvailable());
    ClientSession.newMarks().boardOpened();
  }

  private MainScreen(Screen parent, boolean mapMode) {
    super(
        DataSource.asset(
            Identifier.fromNamespaceAndPath(
                "disquests", mapMode ? "main_screen" : "main_screen_legacy")),
        parent);
    this.mapMode = mapMode;
    this.currentTab = ClientSession.getActiveTab();
    this.searchTerm = ClientSession.getSearchTerm();
    this.serverFilter = ClientSession.getServerQuestsFilter();
  }

  @Override
  public NavEntry toNavEntry() {
    return new NavEntry.MainNav(currentTab, searchTerm != null ? searchTerm : "");
  }

  @Override
  protected void build(FlowLayout root) {
    applyThemeRoot(root);
    applyThemePanel(root.childById(ParentUIComponent.class, "quest-scroll"));

    this.rootLayout = root;

    // --- Look up components by ID ---
    this.titleLabel = root.childById(LabelComponent.class, "title-label");
    this.tabMyQuests = root.childById(ButtonComponent.class, "tab-my-quests");
    this.tabQuestBoard = root.childById(ButtonComponent.class, "tab-quest-board");
    this.tabArchived = root.childById(ButtonComponent.class, "tab-archived");
    this.tabRow = root.childById(FlowLayout.class, "tab-row");
    if (!ClientSession.supportsCompletion()) {
      // An older server cannot archive quests: drop the tab (removed by id, not parent())
      tabRow.removeChild(tabArchived);
      if (currentTab == ClientSession.Tab.ARCHIVED) {
        currentTab = ClientSession.Tab.MY_QUESTS;
        ClientSession.setActiveTab(currentTab);
      }
    }
    if (mapMode) {
      // The tabs split the row evenly; expand() is a percentage of the remaining space each
      int share = ClientSession.supportsCompletion() ? 33 : 50;
      tabMyQuests.horizontalSizing(Sizing.expand(share));
      tabQuestBoard.horizontalSizing(Sizing.expand(share));
      tabArchived.horizontalSizing(Sizing.expand(share));
    }
    // parent() is only set once the tree is mounted, so look the holder up by id.
    this.tabsParent = mapMode ? root.childById(FlowLayout.class, "side-panel") : root;
    this.filterRow = root.childById(FlowLayout.class, "filter-row");
    this.filterAll = root.childById(ButtonComponent.class, "filter-all");
    this.filterOpen = root.childById(ButtonComponent.class, "filter-open");
    this.filterClosed = root.childById(ButtonComponent.class, "filter-closed");
    this.listPanel =
        new QuestListPanel(
            root.childById(FlowLayout.class, "quest-list"),
            root.childById(ScrollContainer.class, "quest-scroll"),
            mapMode,
            this::onEntryClicked,
            this::openSelected,
            this::refreshAfterPinToggle,
            this::onSelectionChanged);
    this.searchRow = root.childById(FlowLayout.class, "search-row");
    this.btnNewQuest = root.childById(ButtonComponent.class, "btn-new-quest");
    this.btnInteract = root.childById(ButtonComponent.class, "btn-interact");
    this.btnOpen = root.childById(ButtonComponent.class, "btn-open");
    this.actionRow = root.childById(FlowLayout.class, "action-row");

    // --- Wire tab button handlers ---
    this.tabMyQuests.onPress(ignored -> selectTab(ClientSession.Tab.MY_QUESTS));
    this.tabQuestBoard.onPress(ignored -> selectTab(ClientSession.Tab.SERVER_QUESTS));
    this.tabArchived.setMessage(Component.translatable("gui.disquests.tab.archived"));
    this.tabArchived.onPress(ignored -> selectTab(ClientSession.Tab.ARCHIVED));

    // --- Wire filter button handlers ---
    this.filterAll.onPress(ignored -> selectServerFilter(ClientSession.QuestFilter.ALL));
    this.filterOpen.onPress(ignored -> selectServerFilter(ClientSession.QuestFilter.OPEN));
    this.filterClosed.onPress(ignored -> selectServerFilter(ClientSession.QuestFilter.CLOSED));

    this.filterAll.tooltip(Component.translatable("gui.disquests.filter.all"));
    this.filterOpen.tooltip(Component.translatable("gui.disquests.filter.open"));
    this.filterClosed.tooltip(Component.translatable("gui.disquests.filter.closed"));

    // --- Wire action button handlers ---
    this.btnNewQuest.onPress(ignored -> createNewQuest());
    this.btnInteract.onPress(ignored -> interactWithQuest());
    this.btnOpen.onPress(ignored -> openSelected());
    wireBackButton(root);
    ButtonComponent toggleMap = root.childById(ButtonComponent.class, "btn-toggle-map");
    if (mapMode || ClientSession.mapAvailableIgnoringSetting()) {
      toggleMap.setMessage(
          Component.translatable(
              mapMode ? "gui.disquests.map.map_on" : "gui.disquests.map.map_off"));
      toggleMap.onPress(ignored -> setWorldMap(!mapMode));
    } else {
      // Look the header up by id: parent() is only set once the tree is mounted.
      root.childById(FlowLayout.class, "header-row").removeChild(toggleMap);
    }

    // --- Create search text box programmatically ---
    // The map layout's panel is resizable, so the search box follows its width there.
    this.searchField = UIComponents.textBox(mapMode ? Sizing.fill(100) : Sizing.fixed(168));
    this.searchField.text(this.searchTerm);
    this.searchField.setHint(Component.translatable("gui.disquests.placeholder.search"));
    this.searchField.onChanged().subscribe(this::onSearchTermChanged);
    this.searchField.id("search-box");
    this.searchRow.child(this.searchField);

    // --- Tag autocomplete dropdown ---
    tagDropdown =
        new SuggestionDropdown<String>(
                root,
                "tag-autocomplete-overlay",
                tag -> new SuggestionDropdown.Entry("#" + tag, null, true, null),
                tag -> {
                  // Replace the current #partial with #tagname
                  String current = searchField.getValue();
                  int hashIndex = current.lastIndexOf('#');
                  String before = hashIndex >= 0 ? current.substring(0, hashIndex) : current;
                  searchField.text(before + "#" + tag + " ");
                })
            .pickOnTab(true)
            .preferAbove(true);

    // Cache title width
    this.titleWidth = Minecraft.getInstance().font.width("Disquests");

    if (mapMode) {
      buildMapArea(root);
    } else {
      addServerNotice(root);
    }

    // --- Apply saved state ---
    selectTab(this.currentTab);
  }

  // --- TAB SWITCHING ---

  private void selectTab(ClientSession.Tab tab) {
    LOGGER.debug("selectTab({})", tab);
    this.currentTab = tab;
    ClientSession.setActiveTab(tab);

    boolean isMyQuests = tab == ClientSession.Tab.MY_QUESTS;
    boolean isBoard = tab == ClientSession.Tab.SERVER_QUESTS;

    // Toggle tab active states (active selected tab appears pressed/disabled)
    tabMyQuests.active(!isMyQuests);
    tabQuestBoard.active(!isBoard);
    tabArchived.active(tab != ClientSession.Tab.ARCHIVED);

    // Clear selection
    listPanel.clearSelection();

    // Filter row only on the Quest Board: removed, not zero-sized, so its buttons do not render.
    // It always sits right after the tab row.
    tabsParent.removeChild(filterRow);
    if (isBoard) {
      tabsParent.child(tabsParent.children().indexOf(tabRow) + 1, filterRow);
      selectServerFilter(this.serverFilter);
    }

    // Action button visibility: hide/show by removing/adding
    // New Quest only on My Quests; Interact only on the Quest Board; Archived has Open alone
    btnNewQuest.active(isMyQuests);
    btnInteract.active(isBoard);

    // Hide/show buttons by removing/re-adding to parent flow
    actionRow.removeChild(btnNewQuest);
    actionRow.removeChild(btnInteract);
    if (isMyQuests) {
      // Insert New Quest before Open
      actionRow.child(0, btnNewQuest);
    } else if (isBoard) {
      // Insert Interact before Open
      actionRow.child(0, btnInteract);
    }

    refreshListContents();
    updateActionButtons();
  }

  // --- SERVER FILTER ---

  private void selectServerFilter(ClientSession.QuestFilter filter) {
    LOGGER.debug("selectServerFilter({})", filter);
    this.serverFilter = filter;
    ClientSession.setServerQuestsFilter(filter);

    // Active = clickable, so the currently-selected filter is NOT active (appears pressed)
    filterAll.active(filter != ClientSession.QuestFilter.ALL);
    filterOpen.active(filter != ClientSession.QuestFilter.OPEN);
    filterClosed.active(filter != ClientSession.QuestFilter.CLOSED);

    refreshListContents();
    updateActionButtons();
  }

  // --- SEARCH ---

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
    String lower = partial.toLowerCase(Locale.ROOT);
    List<String> tags =
        ClientCache.getAllKnownTags().stream()
            .filter(t -> lower.isEmpty() || t.toLowerCase(Locale.ROOT).contains(lower))
            .limit(TAG_SUGGESTIONS)
            .toList();
    tagDropdown.show(tags, searchField);
  }

  @Override
  public boolean keyPressed(KeyEvent keyInput) {
    // Escape closes the dimension menu before it closes the screen
    if (keyInput.key() == InputConstants.KEY_ESCAPE && isDimensionMenuOpen()) {
      closeDimensionMenu();
      return true;
    }
    // Intercept arrow/enter/tab/escape for the tag autocomplete dropdown
    if (tagDropdown != null && tagDropdown.onKey(keyInput)) {
      return true;
    }
    return super.keyPressed(keyInput);
  }

  // --- DATA REFRESH ---

  public void refreshListContents() {
    if (listPanel == null) return;
    listPanel.show(currentTab, serverFilter, searchTerm);
    refreshMapLayer();
    updateActionButtons();
  }

  /**
   * Draws the zones {@link MapQuestFilter} picks for the tab, its search and filter and the
   * selection: top-level quests, the selected quest's tree and quests the player holds a role on.
   */
  private void refreshMapLayer() {
    if (polygonLayer == null) return;
    List<Quest> quests = new ArrayList<>();
    for (UUID id :
        MapQuestFilter.visible(
            QuestGraphs.current(),
            ClientSession.getEffectivePlayerUuid(),
            listPanel.selectedId(),
            QuestListPanel.treeTab(currentTab),
            searchTerm,
            listPanel.listOptions())) {
      Quest quest = ClientCache.getQuestById(id);
      if (quest != null) quests.add(quest);
    }
    polygonLayer.setQuests(quests);
  }

  /** The quests the list shows for the current tab, filter and search, in list order. */
  public List<Quest> visibleQuests() {
    return listPanel.visibleQuests();
  }

  // --- SELECTION ---

  private void onEntryClicked(Quest quest) {
    LOGGER.debug("onEntryClicked: quest={}", quest.getTitle());
    onSelectionChanged();
    if (mapView != null && quest.hasLocation() && !quest.isCompleted()) {
      if (quest.getMap() != null) {
        selectDimension(quest.getMap());
      }
      double[] centre = mapCentre(quest);
      mapView.centerOn(centre[0], centre[1]);
    }
  }

  private void onSelectionChanged() {
    updateActionButtons();
    refreshMapLayer();
  }

  // --- MAP ---

  /** The map component, or null when the map is off (legacy layout). */
  @Nullable
  public MapView getMapView() {
    return mapView;
  }

  private void buildMapArea(FlowLayout root) {
    this.sidePanel = root.childById(FlowLayout.class, "side-panel");
    this.polygonLayer = new QuestPolygonLayer();
    // A zone click selects the quest, its row revealed in the list, and opens it as before
    this.polygonLayer.onQuestClick(
        quest -> {
          listPanel.reveal(quest.getId());
          onSelectionChanged();
          openQuest(quest);
        });
    this.mapView = new MapView(SharedTileCache.get());
    this.mapView.addLayer(new BannerLayer(SharedTileCache.banners())).addLayer(polygonLayer);
    boolean playerMarker = ClientSession.isPlayerMarkerEnabled();
    if (playerMarker) {
      this.mapView.addLayer(new PlayerMarkerLayer());
    }
    this.mapView.sizing(Sizing.fill(100), Sizing.fill(100));
    this.mapView.id("map-view");
    root.childById(FlowLayout.class, "map-slot").child(mapView);

    wireDimensionMenu(root);
    ButtonComponent centreMe = root.childById(ButtonComponent.class, "btn-centre-me");
    if (playerMarker) {
      centreMe.setMessage(Component.translatable("gui.disquests.map.centre_me"));
      centreMe.onPress(ignored -> centreOnPlayer());
    } else {
      // Server switched the player marker off: no GPS shortcut either. Look the toolbar up by id:
      // owo only sets parent() when the tree is mounted, which happens after build().
      root.childById(FlowLayout.class, "map-toolbar").removeChild(centreMe);
    }
    this.listToggle = root.childById(ButtonComponent.class, "btn-toggle-list");
    listToggle.onPress(ignored -> toggleList());
    this.zoomOut = root.childById(ButtonComponent.class, "btn-zoom-out");
    this.zoomIn = root.childById(ButtonComponent.class, "btn-zoom-in");
    zoomOut.onPress(ignored -> zoomStep(-1));
    zoomIn.onPress(ignored -> zoomStep(1));

    mapView.setZoom(INITIAL_ZOOM);
    if (playerMarker) {
      centreOnPlayer();
    } else {
      showRememberedOrWorld();
    }
    updateZoomControls();
    buildPanelDivider(root);
    boolean collapse = QuestListOrder.shouldCollapseList(this.width);
    if (collapse) {
      root.removeChild(panelDivider);
      root.removeChild(sidePanel);
    }
    updateListToggle(!collapse);
  }

  /** Inserts the draggable divider before the side panel and applies the saved panel width. */
  private void buildPanelDivider(FlowLayout root) {
    PaneSplit split =
        new PaneSplit(
            sidePanel,
            PaneSplit.Side.RIGHT,
            "panel-divider",
            DisquestsClient.CONFIG::sidePanelWidth,
            DisquestsClient.CONFIG::sidePanelWidth,
            requested -> QuestListOrder.clampPanelWidth(requested, this.width));
    this.panelDivider = split.divider();
    root.child(root.children().indexOf(sidePanel), panelDivider);
  }

  /**
   * btn-dimension names the shown dimension and opens a small menu below it with one button per
   * dimension. The menu is built once and added to the root (absolute positioning) only while open;
   * not an OverlayContainer, which would centre it.
   */
  private void wireDimensionMenu(FlowLayout root) {
    this.dimensionButton = root.childById(ButtonComponent.class, "btn-dimension");
    dimensionButton.onPress(ignored -> toggleDimensionMenu());
    FlowLayout menu = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
    menu.id("dimension-menu");
    menu.surface(Surface.DARK_PANEL);
    menu.padding(Insets.of(3));
    menu.gap(2);
    for (String dimension : DIMENSIONS) {
      ButtonComponent button =
          UIComponents.button(
              Component.translatable("gui.disquests.map.dim." + dimension),
              ignored -> {
                selectDimension(dimension);
                closeDimensionMenu();
              });
      button.id("btn-dim-" + dimension);
      button.sizing(Sizing.fixed(64), Sizing.fixed(14));
      menu.child(button);
      dimensionButtons.put(dimension, button);
    }
    this.dimensionMenu = menu;
  }

  private boolean isDimensionMenuOpen() {
    return dimensionMenu != null && rootLayout.children().contains(dimensionMenu);
  }

  private void toggleDimensionMenu() {
    if (isDimensionMenuOpen()) {
      closeDimensionMenu();
      return;
    }
    // The root sits at 0,0, so the button's screen position is also its position in the root
    dimensionMenu.positioning(
        Positioning.absolute(
            dimensionButton.x(), dimensionButton.y() + dimensionButton.height() + 1));
    rootLayout.child(dimensionMenu);
  }

  private void closeDimensionMenu() {
    if (isDimensionMenuOpen()) {
      rootLayout.removeChild(dimensionMenu);
    }
  }

  /** A click outside the open menu and its button closes it; on the map it does nothing else. */
  @Override
  public boolean mouseClicked(MouseButtonEvent click, boolean simulated) {
    if (isDimensionMenuOpen()
        && !dimensionMenu.isInBoundingBox(click.x(), click.y())
        && !dimensionButton.isInBoundingBox(click.x(), click.y())) {
      closeDimensionMenu();
      if (mapView != null && mapView.isInBoundingBox(click.x(), click.y())) {
        return true;
      }
    }
    return super.mouseClicked(click, simulated);
  }

  /**
   * Shows {@code dimension} on the map: btn-dimension names it and its menu entry looks pressed,
   * like the selected tab.
   */
  private void selectDimension(String dimension) {
    mapView.setDimension(dimension);
    dimensionButtons.forEach((key, button) -> button.active(!key.equals(dimension)));
    if (dimensionButton != null) {
      dimensionButton.setMessage(
          Component.translatable("gui.disquests.map.dim." + dimension).append(" \u25BE"));
    }
  }

  private void centreOnPlayer() {
    Minecraft minecraft = Minecraft.getInstance();
    selectDimension(
        minecraft.level != null ? minecraft.level.dimension().identifier().getPath() : "overworld");
    LocalPlayer player = minecraft.player;
    if (player != null) {
      mapView.centerOn(player.getX(), player.getZ());
    }
  }

  /**
   * Without the player marker the board opens where the player last left the map this session, else
   * on the whole world in the server's default dimension.
   */
  private void showRememberedOrWorld() {
    String dimension = ClientSession.lastCameraDimension();
    if (dimension == null) {
      dimension = DimensionKeys.normalize(ClientSession.getBluemapDefaultMap());
    }
    if (dimension == null) {
      dimension = DimensionKeys.OVERWORLD;
    }
    selectDimension(dimension);
    MapMath.Camera remembered = ClientSession.rememberedCamera(dimension);
    mapView.setCamera(remembered != null ? remembered : MapCameraMemory.WORLD_VIEW);
  }

  /** Remembers the map camera whenever the board goes away (close, navigation, back/forward). */
  @Override
  public void removed() {
    if (mapView != null) {
      ClientSession.rememberCamera(mapView.getDimension(), mapView.getCamera());
    }
    super.removed();
  }

  private void toggleList() {
    boolean shown = rootLayout.children().contains(sidePanel);
    if (shown) {
      rootLayout.removeChild(panelDivider);
      rootLayout.removeChild(sidePanel);
    } else {
      // Insert before the dimension menu, if open, so the popup stays last (on top)
      int index = isDimensionMenuOpen() ? rootLayout.children().indexOf(dimensionMenu) : -1;
      if (index >= 0) {
        rootLayout.child(index, sidePanel);
        rootLayout.child(index, panelDivider);
      } else {
        rootLayout.child(panelDivider);
        rootLayout.child(sidePanel);
      }
    }
    updateListToggle(!shown);
  }

  private void updateListToggle(boolean listShown) {
    listToggle.setMessage(
        Component.translatable(
            listShown ? "gui.disquests.map.list_on" : "gui.disquests.map.list_off"));
  }

  /** One zoom step around the map centre, from the toolbar's zoom buttons. */
  private void zoomStep(int delta) {
    mapView.zoomBy(delta, mapView.width() / 2.0, mapView.height() / 2.0);
    updateZoomControls();
  }

  /**
   * Greys out zoom-out at the widest zoom and zoom-in at the closest, and refreshes their "Zoom n /
   * max" tooltip. Runs every tick, so scroll zooming is picked up too.
   */
  private void updateZoomControls() {
    int zoom = mapView.getCamera().zoom();
    if (zoom != shownZoom) {
      shownZoom = zoom;
      Component tooltip =
          Component.translatable("gui.disquests.map.zoom_tooltip", zoom, MapMath.MAX_ZOOM);
      zoomOut.active(zoom > 0);
      zoomIn.active(zoom < MapMath.MAX_ZOOM);
      zoomOut.tooltip(tooltip);
      zoomIn.tooltip(tooltip);
    }
  }

  /** Block {x, z} the map centres on: a polygon's bounding-box centre, else the pin. */
  private static double[] mapCentre(Quest quest) {
    if (!quest.getPolygon().isEmpty()) {
      CoordinatesData[] box = QuestLocation.boundingBox(quest.getPolygon(), 0);
      return new double[] {(box[0].x() + box[1].x()) / 2, (box[0].z() + box[1].z()) / 2};
    }
    CoordinatesData pin = quest.getCoordinates();
    return new double[] {pin.x(), pin.z()};
  }

  /** Persists the {@code worldMap} setting and reopens the board in the matching layout. */
  private void setWorldMap(boolean enabled) {
    DisquestsClient.CONFIG.worldMap(enabled);
    DisquestsClient.CONFIG.save();
    // The private constructor: a layout switch is not a new opening of the board
    this.minecraft.gui.setScreen(new MainScreen(this.parent, ClientSession.mapAvailable()));
  }

  /** Explains why the map is off (outdated server, BlueMap unreachable); none when switched off. */
  private void addServerNotice(FlowLayout root) {
    String key = LocationCard.mapOffNoticeKey();
    if (key == null) {
      return;
    }
    LabelComponent notice = UIComponents.label(Component.translatable(key));
    notice.color(Color.ofArgb(Colors.TEXT_MUTED));
    notice.id("server-notice");
    FlowLayout header = root.childById(FlowLayout.class, "header-row");
    root.child(root.children().indexOf(header) + 1, notice);
  }

  @Nullable
  private Quest getSelectedQuest() {
    return listPanel != null ? listPanel.selectedQuest() : null;
  }

  public void refreshAfterPinToggle() {
    LOGGER.debug("refreshAfterPinToggle called");
    updateActionButtons();
  }

  /** Returns the current quest entries in list order. Used by E2E tests. */
  public List<QuestEntryComponent> getQuestEntries() {
    return listPanel != null ? listPanel.entries() : Collections.emptyList();
  }

  private void updateActionButtons() {
    Quest selected = getSelectedQuest();
    btnOpen.active(selected != null);
    if (currentTab == ClientSession.Tab.SERVER_QUESTS) {
      updateInteractButton(selected);
    }
  }

  private void updateInteractButton(Quest selected) {
    if (selected == null) {
      btnInteract.active(false);
      btnInteract.setMessage(Component.translatable("gui.disquests.btn.join"));
      btnInteract.tooltip(NO_TOOLTIP);
      return;
    }
    if (selected.isLocked()) {
      // No joining or requesting until it unlocks
      btnInteract.setMessage(
          Component.translatable(
              selected.getVisibility() == Visibility.CLOSED
                  ? "gui.disquests.btn.request"
                  : "gui.disquests.btn.join"));
      btnInteract.active(false);
      btnInteract.tooltip(NO_TOOLTIP);
    } else if (ClientSession.isRequested(selected.getId())) {
      btnInteract.setMessage(Component.translatable("gui.disquests.btn.requested"));
      btnInteract.active(false);
      btnInteract.tooltip(Component.translatable("gui.disquests.tooltip.already_requested"));
    } else if (selected.getVisibility() == Visibility.OPEN) {
      btnInteract.setMessage(Component.translatable("gui.disquests.btn.join"));
      btnInteract.active(true);
      btnInteract.tooltip(NO_TOOLTIP);
    } else if (selected.getVisibility() == Visibility.CLOSED) {
      btnInteract.setMessage(Component.translatable("gui.disquests.btn.request"));
      btnInteract.active(true);
      btnInteract.tooltip(NO_TOOLTIP);
    } else {
      btnInteract.active(false);
      btnInteract.tooltip(NO_TOOLTIP);
    }
  }

  // --- ACTIONS ---

  private void createNewQuest() {
    Quest newQuest = new Quest();
    newQuest.setId(UUID.randomUUID());
    newQuest.setTitle("New Quest");
    newQuest.setContent("");
    newQuest.setVisibility(Visibility.PRIVATE);
    newQuest.setOwnerUuid(ClientSession.getEffectivePlayerUuid());
    newQuest.setOwnerName(Minecraft.getInstance().getUser().getName());
    newQuest.setLastModified(System.currentTimeMillis() / 1000);
    newQuest.setContributors(new ArrayList<>());
    navigateToScreen(new QuestScreen(this, newQuest, true));
  }

  public void openSelected() {
    Quest sel = getSelectedQuest();
    if (sel != null) {
      openQuest(sel);
    }
  }

  /** Opens the quest's page (a locked quest opens to its locked page); the page clears NEW. */
  private void openQuest(Quest quest) {
    navigateToScreen(new QuestScreen(this, quest));
  }

  private void joinQuest() {
    Quest sel = getSelectedQuest();
    if (sel != null && sel.getVisibility() == Visibility.OPEN) {
      PacketSender.joinQuest(sel.getId());
    }
  }

  private void requestAccess() {
    Quest sel = getSelectedQuest();
    LOGGER.debug(
        "requestAccess: sel={}, visibility={}, btnActive={}",
        sel != null ? sel.getTitle() : "null",
        sel != null ? sel.getVisibility() : "n/a",
        btnInteract.active());
    if (sel != null && sel.getVisibility() == Visibility.CLOSED) {
      PacketSender.requestCollaboration(sel.getId());
      ClientSession.markRequested(sel.getId());
      updateInteractButton(sel);
      showToast("Request sent to " + sel.getOwnerName());
    }
  }

  private void interactWithQuest() {
    Quest sel = getSelectedQuest();
    if (sel == null || sel.isLocked()) return;
    if (sel.getVisibility() == Visibility.OPEN) {
      joinQuest();
    } else if (sel.getVisibility() == Visibility.CLOSED) {
      requestAccess();
    }
  }

  // --- TICK ---

  @Override
  public void tick() {
    super.tick();
    tickCounter++;
    toast.tick();
    String pending = ClientSession.consumePendingToast();
    if (pending != null) toast.show(pending);
    // Refresh list when cache changes (quest created, deleted, synced from server)
    long currentVersion = ClientCache.getVersion();
    if (currentVersion != lastCacheVersion) {
      lastCacheVersion = currentVersion;
      refreshListContents();
    }
    listPanel.tick();
    if (mapView != null) {
      updateZoomControls();
    }
  }

  public void showToast(String message) {
    toast.show(message);
  }

  // --- RENDERING ---

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    // Let owo-ui render the component tree
    super.extractRenderState(context, mouseX, mouseY, delta);

    // Rainbow title on hover (the title and tabs live in the side panel, skip when it is hidden)
    boolean panelShown = sidePanel == null || rootLayout.children().contains(sidePanel);
    if (titleLabel != null && panelShown) {
      String titleStr = "Disquests";
      int titleX = titleLabel.x();
      int titleY = titleLabel.y();

      boolean hovering =
          mouseX >= titleX
              && mouseX <= titleX + titleWidth
              && mouseY >= titleY
              && mouseY <= titleY + this.font.lineHeight;

      if (hovering) {
        // Draw rainbow characters over the label
        int charX = titleX;
        for (int i = 0; i < titleStr.length(); i++) {
          float hue = ((tickCounter * 3 + i * 30) % 360) / 360.0f;
          int color = hsbToRgb(hue, 0.8f, 1.0f);
          String ch = String.valueOf(titleStr.charAt(i));
          context.text(this.font, ch, charX, titleY, color, true);
          charX += this.font.width(ch);
        }
      }
    }

    // Notification badge on My Quests tab (fix 13: only query when on My Quests)
    if (currentTab == ClientSession.Tab.MY_QUESTS && tabMyQuests != null && panelShown) {
      int pendingCount = ClientSession.getPendingRequestCount();
      if (pendingCount > 0) {
        renderNotificationBadge(context, pendingCount);
      }
    }

    // Toast overlay (renders on top of everything)
    toast.render(context, this.font, this.width, this.height - 40);
  }

  // --- HELPERS ---

  private static int hsbToRgb(float hue, float saturation, float brightness) {
    float h = (hue - (float) Math.floor(hue)) * 6.0f;
    float f = h - (float) Math.floor(h);
    float p = brightness * (1.0f - saturation);
    float q = brightness * (1.0f - saturation * f);
    float t = brightness * (1.0f - (saturation * (1.0f - f)));
    int r, g, b;
    switch ((int) h) {
      case 0 -> {
        r = (int) (brightness * 255);
        g = (int) (t * 255);
        b = (int) (p * 255);
      }
      case 1 -> {
        r = (int) (q * 255);
        g = (int) (brightness * 255);
        b = (int) (p * 255);
      }
      case 2 -> {
        r = (int) (p * 255);
        g = (int) (brightness * 255);
        b = (int) (t * 255);
      }
      case 3 -> {
        r = (int) (p * 255);
        g = (int) (q * 255);
        b = (int) (brightness * 255);
      }
      case 4 -> {
        r = (int) (t * 255);
        g = (int) (p * 255);
        b = (int) (brightness * 255);
      }
      default -> {
        r = (int) (brightness * 255);
        g = (int) (p * 255);
        b = (int) (q * 255);
      }
    }
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  private void renderNotificationBadge(GuiGraphicsExtractor context, int count) {
    String countStr = count > 99 ? "99+" : String.valueOf(count);
    int textWidth = this.font.width(countStr);
    int badgeWidth = Math.max(textWidth + 4, 10);
    int badgeHeight = 10;

    // Position at top-right of My Quests tab button
    int badgeX = tabMyQuests.getX() + tabMyQuests.getWidth() - badgeWidth / 2;
    int badgeY = tabMyQuests.getY() - badgeHeight / 2;

    // Draw red badge background
    context.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, Colors.BADGE_RED);

    // Draw count text centered in badge
    int textX = badgeX + (badgeWidth - textWidth) / 2;
    int textY = badgeY + 1;
    context.text(this.font, countStr, textX, textY, Colors.TEXT_PRIMARY, false);
  }
}
