package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.widget.ToastOverlay;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class MainScreen extends DisquestsBaseScreen {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger("Disquests.MainScreen");

  // Component references (looked up by ID from XML model)
  private FlowLayout rootLayout;
  private LabelComponent titleLabel;
  private ButtonComponent tabMyQuests;
  private ButtonComponent tabQuestBoard;
  private FlowLayout filterRow;
  private ButtonComponent filterAll;
  private ButtonComponent filterOpen;
  private ButtonComponent filterClosed;
  private FlowLayout questList;
  private FlowLayout searchRow;
  private ButtonComponent btnNewQuest;
  private ButtonComponent btnJoin;
  private ButtonComponent btnRequest;
  private ButtonComponent btnOpen;
  private ButtonComponent btnClose;

  // Search
  private TextBoxComponent searchField;
  private String searchTerm;

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
    super(DataSource.asset(Identifier.of("disquests", "main_screen")), parent);
    this.currentTab = ClientSession.getActiveTab();
    this.searchTerm = ClientSession.getSearchTerm();
    this.serverFilter = ClientSession.getServerQuestsFilter();
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
    this.filterRow = root.childById(FlowLayout.class, "filter-row");
    this.filterAll = root.childById(ButtonComponent.class, "filter-all");
    this.filterOpen = root.childById(ButtonComponent.class, "filter-open");
    this.filterClosed = root.childById(ButtonComponent.class, "filter-closed");
    this.questList = root.childById(FlowLayout.class, "quest-list");
    this.searchRow = root.childById(FlowLayout.class, "search-row");
    this.btnNewQuest = root.childById(ButtonComponent.class, "btn-new-quest");
    this.btnJoin = root.childById(ButtonComponent.class, "btn-join");
    this.btnRequest = root.childById(ButtonComponent.class, "btn-request");
    this.btnOpen = root.childById(ButtonComponent.class, "btn-open");
    this.btnClose = root.childById(ButtonComponent.class, "btn-close");

    // --- Wire tab button handlers ---
    this.tabMyQuests.onPress(btn -> selectTab(ClientSession.Tab.MY_QUESTS));
    this.tabQuestBoard.onPress(btn -> selectTab(ClientSession.Tab.SERVER_QUESTS));

    // --- Wire filter button handlers ---
    this.filterAll.onPress(btn -> selectServerFilter(ClientSession.QuestFilter.ALL));
    this.filterOpen.onPress(btn -> selectServerFilter(ClientSession.QuestFilter.OPEN));
    this.filterClosed.onPress(btn -> selectServerFilter(ClientSession.QuestFilter.CLOSED));

    this.filterAll.tooltip(Text.translatable("gui.disquests.filter.all"));
    this.filterOpen.tooltip(Text.translatable("gui.disquests.filter.open"));
    this.filterClosed.tooltip(Text.translatable("gui.disquests.filter.closed"));

    // --- Wire action button handlers ---
    this.btnNewQuest.onPress(btn -> createNewQuest());
    this.btnJoin.onPress(btn -> joinQuest());
    this.btnRequest.onPress(btn -> requestAccess());
    this.btnOpen.onPress(btn -> openSelected());
    this.btnClose.onPress(
        btn -> {
          if (this.client != null) this.client.setScreen(null);
        });

    // --- Create search text box programmatically ---
    this.searchField = UIComponents.textBox(Sizing.fixed(200));
    this.searchField.text(this.searchTerm);
    this.searchField.setPlaceholder(Text.translatable("gui.disquests.placeholder.search"));
    this.searchField.onChanged().subscribe(this::onSearchTermChanged);
    this.searchField.id("search-box");
    this.searchRow.child(this.searchField);

    // Cache title width
    this.titleWidth = MinecraftClient.getInstance().textRenderer.getWidth("Disquests");

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

    // Toggle tab active states (active selected tab appears pressed/disabled)
    tabMyQuests.active(!isMyQuests);
    tabQuestBoard.active(isMyQuests);

    // Clear selection
    clearSelection();

    // Filter row visibility: collapse to zero size when hidden
    if (isMyQuests) {
      filterRow.sizing(Sizing.fixed(0), Sizing.fixed(0));
    } else {
      filterRow.sizing(Sizing.content(), Sizing.content());
      selectServerFilter(this.serverFilter);
    }

    // Action button visibility: hide/show by removing/adding
    // New Quest only on My Quests; Join+Request only on Server Quests
    btnNewQuest.active(isMyQuests);
    btnJoin.active(!isMyQuests);
    btnRequest.active(!isMyQuests);

    // Hide/show buttons by removing/re-adding to parent flow
    FlowLayout actionRow = rootLayout.childById(FlowLayout.class, "action-row");
    actionRow.removeChild(btnNewQuest);
    actionRow.removeChild(btnJoin);
    actionRow.removeChild(btnRequest);
    if (isMyQuests) {
      // Insert New Quest before Open and Close
      actionRow.child(0, btnNewQuest);
    } else {
      // Insert Join and Request before Open and Close
      actionRow.child(0, btnRequest);
      actionRow.child(0, btnJoin);
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
              || (q.getContent() != null && q.getContent().toLowerCase().contains(tf));
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

    List<Quest> quests;

    SearchQuery query = parseSearch(searchTerm);

    if (currentTab == ClientSession.Tab.MY_QUESTS) {
      quests = new ArrayList<>(ClientCache.getMyQuests());

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
      quests = new ArrayList<>(ClientCache.getServerQuests());

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

    LOGGER.debug(
        "refreshListContents: tab={}, {} quests after filtering", currentTab, quests.size());
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

    updateActionButtons();
  }

  // --- SELECTION ---

  private void onEntryClicked(QuestEntryComponent entry) {
    LOGGER.debug("onEntryClicked: quest={}", entry.getQuest().getTitle());
    clearSelection();
    entry.selected(true);
    selectedEntry = entry;
    updateActionButtons();
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
    if (currentTab == ClientSession.Tab.MY_QUESTS) {
      boolean hasSelection = getSelectedQuest() != null;
      btnOpen.active(hasSelection);
    } else {
      Quest selected = getSelectedQuest();
      boolean hasSelection = selected != null;
      btnOpen.active(hasSelection);
      btnJoin.active(hasSelection && selected.getVisibility() == Visibility.OPEN);
      btnRequest.active(hasSelection && selected.getVisibility() == Visibility.CLOSED);
      if (selected != null && ClientSession.isRequested(selected.getId())) {
        markRequestButtonAsRequested();
      }
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
    newQuest.setOwnerName(MinecraftClient.getInstance().getSession().getUsername());
    newQuest.setLastModified(System.currentTimeMillis() / 1000);
    newQuest.setContributors(new ArrayList<>());
    this.client.setScreen(new QuestScreen(this, newQuest, true));
  }

  public void openSelected() {
    Quest sel = getSelectedQuest();
    if (sel != null) {
      this.client.setScreen(new QuestScreen(this, sel));
    }
  }

  private void joinQuest() {
    Quest sel = getSelectedQuest();
    if (sel != null && sel.getVisibility() == Visibility.OPEN) {
      PacketSender.joinQuest(sel.getId());
    }
  }

  private void markRequestButtonAsRequested() {
    btnRequest.setMessage(Text.translatable("gui.disquests.btn.requested"));
    btnRequest.active(false);
  }

  private void requestAccess() {
    Quest sel = getSelectedQuest();
    LOGGER.debug(
        "requestAccess: sel={}, visibility={}, btnActive={}",
        sel != null ? sel.getTitle() : "null",
        sel != null ? sel.getVisibility() : "n/a",
        btnRequest.active());
    if (sel != null && sel.getVisibility() == Visibility.CLOSED) {
      PacketSender.requestCollaboration(sel.getId());
      ClientSession.markRequested(sel.getId());
      markRequestButtonAsRequested();
      showToast("Request sent to " + sel.getOwnerName());
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
  }

  public void showToast(String message) {
    toast.show(message);
  }

  // --- RENDERING ---

  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    // Let owo-ui render the component tree
    super.render(context, mouseX, mouseY, delta);

    // Rainbow title on hover
    if (titleLabel != null) {
      String titleStr = "Disquests";
      int titleX = titleLabel.x();
      int titleY = titleLabel.y();

      boolean hovering =
          mouseX >= titleX
              && mouseX <= titleX + titleWidth
              && mouseY >= titleY
              && mouseY <= titleY + this.textRenderer.fontHeight;

      if (hovering) {
        // Draw rainbow characters over the label
        int charX = titleX;
        for (int i = 0; i < titleStr.length(); i++) {
          float hue = ((tickCounter * 3 + i * 30) % 360) / 360.0f;
          int color = hsbToRgb(hue, 0.8f, 1.0f);
          String ch = String.valueOf(titleStr.charAt(i));
          context.drawTextWithShadow(this.textRenderer, ch, charX, titleY, color);
          charX += this.textRenderer.getWidth(ch);
        }
      }
    }

    // Notification badge on My Quests tab (fix 13: only query when on My Quests)
    if (currentTab == ClientSession.Tab.MY_QUESTS && tabMyQuests != null) {
      int pendingCount = ClientSession.getPendingRequestCount();
      if (pendingCount > 0) {
        renderNotificationBadge(context, pendingCount);
      }
    }

    // Toast overlay (renders on top of everything)
    toast.render(context, this.textRenderer, this.width, this.height - 40);
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

  private void renderNotificationBadge(DrawContext context, int count) {
    String countStr = count > 99 ? "99+" : String.valueOf(count);
    int textWidth = this.textRenderer.getWidth(countStr);
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
    context.drawText(this.textRenderer, countStr, textX, textY, Colors.TEXT_PRIMARY, false);
  }
}
