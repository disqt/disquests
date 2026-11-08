package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.client.gui.component.SplitterComponent;
import com.disqt.disquests.client.gui.component.TagAutocompleteDropdown;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.map.BannerLayer;
import com.disqt.disquests.client.gui.map.MapCameraMemory;
import com.disqt.disquests.client.gui.map.MapMath;
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
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
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
  private FlowLayout questList;
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
  private int sidePanelWidth = QuestListOrder.MIN_PANEL_WIDTH;
  @Nullable private LabelComponent zoomLabel;
  private final Map<String, ButtonComponent> dimensionButtons = new LinkedHashMap<>();
  private int shownZoom = -1;

  // Search
  private TextBoxComponent searchField;
  private String searchTerm;
  private TagAutocompleteDropdown tagDropdown;

  // State
  private ClientSession.Tab currentTab;
  private ClientSession.QuestFilter serverFilter;
  private int tickCounter = 0;
  private long lastCacheVersion = -1;
  private final ToastOverlay toast = new ToastOverlay();

  // Selection tracking
  @Nullable private QuestEntryComponent selectedEntry = null;

  // Cached title width

  private int titleWidth;

  public MainScreen() {
    this(null);
  }

  public MainScreen(Screen parent) {
    this(parent, ClientSession.mapAvailable());
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
    // parent() is only set once the tree is mounted, so look the holder up by id.
    this.tabsParent = mapMode ? root.childById(FlowLayout.class, "side-panel") : root;
    this.filterRow = root.childById(FlowLayout.class, "filter-row");
    this.filterAll = root.childById(ButtonComponent.class, "filter-all");
    this.filterOpen = root.childById(ButtonComponent.class, "filter-open");
    this.filterClosed = root.childById(ButtonComponent.class, "filter-closed");
    this.questList = root.childById(FlowLayout.class, "quest-list");
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

    // --- Create search text box programmatically ---
    // The map layout's panel is resizable, so the search box follows its width there.
    this.searchField = UIComponents.textBox(mapMode ? Sizing.fill(100) : Sizing.fixed(168));
    this.searchField.text(this.searchTerm);
    this.searchField.setHint(Component.translatable("gui.disquests.placeholder.search"));
    this.searchField.onChanged().subscribe(this::onSearchTermChanged);
    this.searchField.id("search-box");
    this.searchRow.child(this.searchField);

    // --- Tag autocomplete dropdown ---
    tagDropdown = new TagAutocompleteDropdown();
    tagDropdown.setRootComponent(root);
    tagDropdown.setOnSelect(
        tag -> {
          // Replace the current #partial with #tagname
          String current = searchField.getValue();
          int hashIndex = current.lastIndexOf('#');
          String before = hashIndex >= 0 ? current.substring(0, hashIndex) : current;
          searchField.text(before + "#" + tag + " ");
        });

    // Cache title width
    this.titleWidth = Minecraft.getInstance().font.width("Disquests");

    if (mapMode) {
      buildMapArea(root);
    } else {
      addServerNotice(root);
    }

    // --- Apply saved state ---
    LOGGER.debug(
        "build() complete. questList={}, entries will be added by selectTab", questList != null);
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
    clearSelection();

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

  private record SearchQuery(String textFilter, List<String> tagFilters) {}

  private SearchQuery parseSearch(String raw) {
    if (raw == null || raw.isEmpty()) return new SearchQuery("", List.of());
    List<String> tagFilters = new ArrayList<>();
    StringBuilder text = new StringBuilder();
    for (String token : raw.trim().split("\\s+")) {
      if (token.startsWith("#") && token.length() > 1) {
        tagFilters.add(token.substring(1).toLowerCase());
      } else {
        if (text.length() > 0) text.append(" ");
        text.append(token);
      }
    }
    return new SearchQuery(text.toString(), tagFilters);
  }

  private boolean matchesSearch(Quest q, SearchQuery query) {
    if (!query.textFilter().isEmpty()) {
      String tf = query.textFilter();
      boolean textMatch =
          q.getTitle().toLowerCase().contains(tf)
              || (q.getContent() != null && q.getContent().toLowerCase().contains(tf))
              || q.getTags().stream().anyMatch(t -> t.toLowerCase().contains(tf));
      if (!textMatch) return false;
    }
    if (!query.tagFilters().isEmpty()) {
      boolean tagMatch =
          query.tagFilters().stream()
              .allMatch(
                  tagFilter -> q.getTags().stream().anyMatch(t -> t.equalsIgnoreCase(tagFilter)));
      if (!tagMatch) return false;
    }
    return true;
  }

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
    int anchorY = searchField.y();
    tagDropdown.update(partial, anchorX, anchorY, true);
  }

  @Override
  public boolean keyPressed(KeyEvent keyInput) {
    // Intercept arrow/enter/tab/escape for the tag autocomplete dropdown
    if (tagDropdown != null && tagDropdown.isVisible()) {
      if (tagDropdown.onKeyDown(keyInput)) {
        return true;
      }
    }
    return super.keyPressed(keyInput);
  }

  // --- DATA REFRESH ---

  public void refreshListContents() {
    if (questList == null) {
      LOGGER.debug("refreshListContents: questList is null!");
      return;
    }

    UUID previousSelectedId = selectedEntry != null ? selectedEntry.getQuest().getId() : null;
    LOGGER.debug("refreshListContents: previousSelectedId={}", previousSelectedId);
    questList.clearChildren();
    selectedEntry = null;

    List<Quest> quests = visibleQuests();
    LOGGER.debug(
        "refreshListContents: tab={}, {} quests after filtering", currentTab, quests.size());

    // The header only separates the two groups: when every visible quest is unplaced (or
    // every one is placed) there is nothing to separate, so no header is shown.
    boolean anyUnplaced = quests.stream().anyMatch(q -> !q.hasLocation());
    boolean anyPlaced = quests.stream().anyMatch(Quest::hasLocation);
    boolean archived = currentTab == ClientSession.Tab.ARCHIVED;
    if (mapMode && !archived && anyUnplaced && anyPlaced) {
      LabelComponent header =
          UIComponents.label(Component.translatable("gui.disquests.map.unplaced"));
      header.color(Color.ofArgb(Colors.TEXT_MUTED));
      header.id("unplaced-header");
      questList.child(header);
    }

    for (Quest quest : quests) {
      QuestEntryComponent entry = new QuestEntryComponent(quest);
      entry.sizing(Sizing.fill(100), Sizing.fixed(QuestEntryComponent.ENTRY_HEIGHT));
      entry.onClick(this::onEntryClicked);
      entry.onDoubleClick(e -> openSelected());
      entry.onPinToggle(e -> refreshAfterPinToggle());
      questList.child(entry);
      if (previousSelectedId != null && quest.getId().equals(previousSelectedId)) {
        entry.selected(true);
        selectedEntry = entry;
      }
    }
    LOGGER.debug("refreshListContents: added {} entries to questList", questList.children().size());

    if (polygonLayer != null) {
      // The map only ever draws active quests: the Archived tab shows the active My Quests there.
      polygonLayer.setQuests(QuestListOrder.active(archived ? ClientCache.getMyQuests() : quests));
    }
    updateActionButtons();
  }

  /**
   * The quests the current tab, Open/Closed filter and search leave visible, in list order. My
   * Quests and the Quest Board skip completed quests; Archived lists only the player's completed
   * ones, newest completion first. On those two tabs the map layer draws from this list and, in map
   * mode, unplaced quests come first; on Archived the map shows the active My Quests instead.
   */
  public List<Quest> visibleQuests() {
    List<Quest> quests;

    SearchQuery query = parseSearch(searchTerm);

    if (currentTab == ClientSession.Tab.ARCHIVED) {
      quests = QuestListOrder.archived(ClientCache.getMyQuests());
      if (!searchTerm.isEmpty()) {
        quests = quests.stream().filter(q -> matchesSearch(q, query)).collect(Collectors.toList());
      }
      // Keep the completion order: archived quests are never drawn on the map.
      return quests;
    } else if (currentTab == ClientSession.Tab.MY_QUESTS) {
      quests = QuestListOrder.active(ClientCache.getMyQuests());

      // Filter by search term
      if (!searchTerm.isEmpty()) {
        quests = quests.stream().filter(q -> matchesSearch(q, query)).collect(Collectors.toList());
      }

      // Sort: pinned first, then by lastModified descending
      quests.sort(
          Comparator.<Quest, Boolean>comparing(
                  q -> ClientSession.isPinned(q.getId()), Comparator.reverseOrder())
              .thenComparing(Quest::getLastModified, Comparator.reverseOrder()));
    } else {
      quests = QuestListOrder.active(ClientCache.getServerQuests());

      // Apply sub-filter
      if (serverFilter == ClientSession.QuestFilter.OPEN) {
        quests =
            quests.stream()
                .filter(q -> q.getVisibility() == Visibility.OPEN)
                .collect(Collectors.toList());
      } else if (serverFilter == ClientSession.QuestFilter.CLOSED) {
        quests =
            quests.stream()
                .filter(q -> q.getVisibility() == Visibility.CLOSED)
                .collect(Collectors.toList());
      }

      // Filter by search term
      if (!searchTerm.isEmpty()) {
        quests = quests.stream().filter(q -> matchesSearch(q, query)).collect(Collectors.toList());
      }

      // Sort by lastModified descending
      quests.sort(Comparator.comparing(Quest::getLastModified, Comparator.reverseOrder()));
    }

    return mapMode ? QuestListOrder.orderForList(quests) : quests;
  }

  // --- SELECTION ---

  private void onEntryClicked(QuestEntryComponent entry) {
    LOGGER.debug("onEntryClicked: quest={}", entry.getQuest().getTitle());
    clearSelection();
    entry.selected(true);
    selectedEntry = entry;
    updateActionButtons();
    Quest quest = entry.getQuest();
    if (mapView != null && quest.hasLocation() && !quest.isCompleted()) {
      if (quest.getMap() != null) {
        selectDimension(quest.getMap());
      }
      double[] centre = mapCentre(quest);
      mapView.centerOn(centre[0], centre[1]);
    }
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
    this.polygonLayer.onQuestClick(quest -> navigateToScreen(new QuestScreen(this, quest)));
    this.mapView = new MapView(SharedTileCache.get());
    this.mapView.addLayer(new BannerLayer(SharedTileCache.banners())).addLayer(polygonLayer);
    boolean playerMarker = ClientSession.isPlayerMarkerEnabled();
    if (playerMarker) {
      this.mapView.addLayer(new PlayerMarkerLayer());
    }
    this.mapView.sizing(Sizing.fill(100), Sizing.fill(100));
    this.mapView.id("map-view");
    root.childById(FlowLayout.class, "map-slot").child(mapView);

    wireDimensionButtons(root);
    ButtonComponent centreMe = root.childById(ButtonComponent.class, "btn-centre-me");
    if (playerMarker) {
      centreMe.setMessage(Component.translatable("gui.disquests.map.centre_me"));
      centreMe.onPress(ignored -> centreOnPlayer());
    } else {
      // Server switched the player marker off: no GPS shortcut either. Look the toolbar up by id:
      // owo only sets parent() when the tree is mounted, which happens after build().
      root.childById(FlowLayout.class, "map-toolbar").removeChild(centreMe);
    }
    ButtonComponent toggleList = root.childById(ButtonComponent.class, "btn-toggle-list");
    toggleList.setMessage(Component.translatable("gui.disquests.map.toggle_list"));
    toggleList.onPress(ignored -> toggleList());
    this.zoomLabel = root.childById(LabelComponent.class, "zoom-label");

    mapView.setZoom(INITIAL_ZOOM);
    if (playerMarker) {
      centreOnPlayer();
    } else {
      showRememberedOrWorld();
    }
    updateZoomLabel();
    buildPanelDivider(root);
    if (QuestListOrder.shouldCollapseList(this.width)) {
      root.removeChild(panelDivider);
      root.removeChild(sidePanel);
    }
  }

  /** Inserts the draggable divider before the side panel and applies the saved panel width. */
  private void buildPanelDivider(FlowLayout root) {
    this.sidePanelWidth =
        QuestListOrder.clampPanelWidth(DisquestsClient.CONFIG.sidePanelWidth(), this.width);
    sidePanel.horizontalSizing(Sizing.fixed(sidePanelWidth));
    // Width at the press: only drags change it, and each commit re-anchors it.
    int[] startWidth = {sidePanelWidth};
    this.panelDivider = new SplitterComponent();
    panelDivider.id("panel-divider");
    panelDivider.onDrag(
        totalDx -> {
          // Dragging left widens the panel, so the divider follows the cursor.
          int width =
              QuestListOrder.clampPanelWidth((int) Math.round(startWidth[0] - totalDx), this.width);
          if (width != sidePanelWidth) {
            sidePanelWidth = width;
            sidePanel.horizontalSizing(Sizing.fixed(width));
          }
        });
    panelDivider.onCommit(
        () -> {
          startWidth[0] = sidePanelWidth;
          if (DisquestsClient.CONFIG.sidePanelWidth() != sidePanelWidth) {
            DisquestsClient.CONFIG.sidePanelWidth(sidePanelWidth);
            // Option#set does not write the file.
            DisquestsClient.CONFIG.save();
          }
        });
    root.child(root.children().indexOf(sidePanel), panelDivider);
  }

  private void wireDimensionButtons(FlowLayout root) {
    for (String dimension : DIMENSIONS) {
      ButtonComponent button = root.childById(ButtonComponent.class, "btn-dim-" + dimension);
      button.setMessage(Component.translatable("gui.disquests.map.dim." + dimension));
      button.onPress(ignored -> selectDimension(dimension));
      dimensionButtons.put(dimension, button);
    }
  }

  /** Shows {@code dimension} on the map; its button looks pressed, like the selected tab. */
  private void selectDimension(String dimension) {
    mapView.setDimension(dimension);
    dimensionButtons.forEach((key, button) -> button.active(!key.equals(dimension)));
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
    if (rootLayout.children().contains(sidePanel)) {
      rootLayout.removeChild(panelDivider);
      rootLayout.removeChild(sidePanel);
    } else {
      rootLayout.child(panelDivider);
      rootLayout.child(sidePanel);
    }
  }

  private void updateZoomLabel() {
    int zoom = mapView.getCamera().zoom();
    if (zoom != shownZoom) {
      shownZoom = zoom;
      zoomLabel.text(Component.translatable("gui.disquests.map.zoom", zoom));
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

  private void clearSelection() {
    if (selectedEntry != null) {
      selectedEntry.selected(false);
    }
    selectedEntry = null;
  }

  @Nullable
  private Quest getSelectedQuest() {
    return selectedEntry != null ? selectedEntry.getQuest() : null;
  }

  public void refreshAfterPinToggle() {
    LOGGER.debug("refreshAfterPinToggle called");
    updateActionButtons();
  }

  /** Returns the current quest entries in list order. Used by E2E tests. */
  public List<QuestEntryComponent> getQuestEntries() {
    if (questList == null) return Collections.emptyList();
    return questList.children().stream()
        .filter(c -> c instanceof QuestEntryComponent)
        .map(c -> (QuestEntryComponent) c)
        .collect(Collectors.toList());
  }

  private void updateActionButtons() {
    if (currentTab != ClientSession.Tab.SERVER_QUESTS) {
      boolean hasSelection = getSelectedQuest() != null;
      btnOpen.active(hasSelection);
    } else {
      Quest selected = getSelectedQuest();
      boolean hasSelection = selected != null;
      btnOpen.active(hasSelection);
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
    if (ClientSession.isRequested(selected.getId())) {
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
      navigateToScreen(new QuestScreen(this, sel));
    }
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
    if (sel == null) return;
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
    if (mapView != null) {
      updateZoomLabel();
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
