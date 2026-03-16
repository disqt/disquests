package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.ScreenLayouts;
import com.disqt.disquests.client.gui.helper.UIHelper;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.client.gui.widget.TabButtonWidget;
import com.disqt.disquests.client.gui.widget.list.QuestListWidget;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Visibility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MainScreen extends BaseScreen {

    // Tabs
    private static final int TAB_MY_QUESTS = 0;
    private static final int TAB_SERVER_QUESTS = 1;

    private TabButtonWidget myQuestsTab;
    private TabButtonWidget serverQuestsTab;

    // Sub-filter buttons (Server Quests tab)
    private static final int FILTER_ALL = 0;
    private static final int FILTER_OPEN = 1;
    private static final int FILTER_CLOSED = 2;

    private DarkButtonWidget filterAllButton;
    private DarkButtonWidget filterOpenButton;
    private DarkButtonWidget filterClosedButton;

    // Action buttons
    private DarkButtonWidget newQuestButton;
    private DarkButtonWidget openButton;
    private DarkButtonWidget joinButton;
    private DarkButtonWidget requestAccessButton;
    private DarkButtonWidget closeButton;

    // Lists
    private QuestListWidget myQuestListWidget;
    private QuestListWidget serverQuestListWidget;

    // Search
    private MultiLineTextFieldWidget searchField;
    private String searchTerm;

    // State
    private int currentTab;
    private int serverFilter;

    public MainScreen() {
        super(Text.literal("Disquests"), null);
        this.currentTab = ClientSession.getActiveTab();
        this.searchTerm = ClientSession.getSearchTerm();
        this.serverFilter = ClientSession.getServerQuestsFilter();
    }

    @Override
    protected void init() {
        super.init();

        // --- LAYOUT CALCULATIONS ---
        int buttonsY = UIHelper.getBottomButtonY(this);
        int searchBarY = buttonsY - UIHelper.OUTER_PADDING - ScreenLayouts.SEARCH_BAR_HEIGHT;
        int bottomMargin = this.height - searchBarY + UIHelper.OUTER_PADDING;

        // Sub-filter row sits between tabs and list (only visible on Server Quests tab)
        int subFilterHeight = 16;
        int subFilterY = ScreenLayouts.TOP_MARGIN - 2;
        int listTop = ScreenLayouts.TOP_MARGIN;

        // If server quests tab is active, push list down to make room for sub-filter
        // We'll handle this dynamically in selectTab

        // --- TABS ---
        int tabY = (ScreenLayouts.TOP_MARGIN - ScreenLayouts.TAB_HEIGHT) / 2;
        this.myQuestsTab = this.addDrawableChild(new TabButtonWidget(
                (this.width / 2) - ScreenLayouts.TAB_WIDTH - 2, tabY,
                ScreenLayouts.TAB_WIDTH, ScreenLayouts.TAB_HEIGHT,
                Text.literal("My Quests"), b -> selectTab(TAB_MY_QUESTS)
        ));
        this.serverQuestsTab = this.addDrawableChild(new TabButtonWidget(
                (this.width / 2) + 2, tabY,
                ScreenLayouts.TAB_WIDTH, ScreenLayouts.TAB_HEIGHT,
                Text.literal("Server"), b -> selectTab(TAB_SERVER_QUESTS)
        ));

        // --- SUB-FILTER BUTTONS (Server Quests) ---
        int filterBtnWidth = 50;
        int filterBtnHeight = 14;
        int filterBtnSpacing = 4;
        int totalFilterWidth = (filterBtnWidth * 3) + (filterBtnSpacing * 2);
        int filterStartX = (this.width - totalFilterWidth) / 2;

        this.filterAllButton = this.addDrawableChild(new DarkButtonWidget(
                filterStartX, subFilterY, filterBtnWidth, filterBtnHeight,
                Text.literal("All"), b -> selectServerFilter(FILTER_ALL)
        ));
        this.filterOpenButton = this.addDrawableChild(new DarkButtonWidget(
                filterStartX + filterBtnWidth + filterBtnSpacing, subFilterY, filterBtnWidth, filterBtnHeight,
                Text.literal("Open"), b -> selectServerFilter(FILTER_OPEN)
        ));
        this.filterClosedButton = this.addDrawableChild(new DarkButtonWidget(
                filterStartX + (filterBtnWidth + filterBtnSpacing) * 2, subFilterY, filterBtnWidth, filterBtnHeight,
                Text.literal("Closed"), b -> selectServerFilter(FILTER_CLOSED)
        ));

        // --- LISTS ---
        int myListTop = ScreenLayouts.TOP_MARGIN;
        int serverListTop = subFilterY + filterBtnHeight + 4;
        int listBottom = this.height - bottomMargin;

        this.myQuestListWidget = new QuestListWidget(this, this.client, myListTop, listBottom, 38);
        this.myQuestListWidget.setSelectionListener(this::onQuestSelected);
        this.myQuestListWidget.setDoubleClickAction(this::openSelected);
        this.addSelectableChild(myQuestListWidget);

        this.serverQuestListWidget = new QuestListWidget(this, this.client, serverListTop, listBottom, 38);
        this.serverQuestListWidget.setSelectionListener(this::onQuestSelected);
        this.serverQuestListWidget.setDoubleClickAction(this::openSelected);
        this.addSelectableChild(serverQuestListWidget);

        // --- SEARCH FIELD ---
        int searchFieldX = (this.width - ScreenLayouts.SEARCH_FIELD_WIDTH) / 2;
        this.searchField = new MultiLineTextFieldWidget(
                this.textRenderer, searchFieldX, searchBarY,
                ScreenLayouts.SEARCH_FIELD_WIDTH, ScreenLayouts.SEARCH_BAR_HEIGHT,
                this.searchTerm, "Search...", 1, false
        );
        this.searchField.setChangedListener(this::onSearchTermChanged);
        this.addSelectableChild(searchField);

        // --- ACTION BUTTONS ---
        // My Quests tab: New Quest, Open, Close
        // Server Quests tab: Join, Request Access, Open, Close
        // We create all buttons but show/hide based on tab
        UIHelper.createButtonRow(this, buttonsY, 5, x -> {
            int index = (x - UIHelper.getCenteredButtonStartX(this.width, 5)) / (UIHelper.BUTTON_WIDTH + UIHelper.BUTTON_SPACING);
            switch (index) {
                case 0 -> this.newQuestButton = this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                        Text.literal("New Quest"), b -> createNewQuest()));
                case 1 -> this.joinButton = this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                        Text.literal("Join"), b -> joinQuest()));
                case 2 -> this.requestAccessButton = this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                        Text.literal("Request"), b -> requestAccess()));
                case 3 -> this.openButton = this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                        Text.literal("Open"), b -> openSelected()));
                case 4 -> this.closeButton = this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, UIHelper.BUTTON_WIDTH, UIHelper.BUTTON_HEIGHT,
                        Text.literal("Close"), b -> this.client.setScreen(null)));
            }
        });

        // Apply saved state
        selectTab(this.currentTab);
        this.setInitialFocus(this.searchField);
    }

    // --- TAB SWITCHING ---

    private void selectTab(int tab) {
        this.currentTab = tab;
        ClientSession.setActiveTab(tab);

        boolean isMyQuests = tab == TAB_MY_QUESTS;

        myQuestsTab.setActive(isMyQuests);
        serverQuestsTab.setActive(!isMyQuests);

        myQuestListWidget.setVisible(isMyQuests);
        serverQuestListWidget.setVisible(!isMyQuests);

        myQuestListWidget.setSelected(null);
        serverQuestListWidget.setSelected(null);

        // Sub-filter visibility
        filterAllButton.visible = !isMyQuests;
        filterOpenButton.visible = !isMyQuests;
        filterClosedButton.visible = !isMyQuests;

        // Button visibility by tab
        newQuestButton.visible = isMyQuests;
        joinButton.visible = !isMyQuests;
        requestAccessButton.visible = !isMyQuests;

        if (!isMyQuests) {
            selectServerFilter(this.serverFilter);
        }

        refreshListContents();
        updateActionButtons();
    }

    // --- SERVER FILTER ---

    private void selectServerFilter(int filter) {
        this.serverFilter = filter;
        ClientSession.setServerQuestsFilter(filter);

        // Update filter button active states using the active field
        filterAllButton.active = (filter != FILTER_ALL);
        filterOpenButton.active = (filter != FILTER_OPEN);
        filterClosedButton.active = (filter != FILTER_CLOSED);

        refreshListContents();
        updateActionButtons();
    }

    // --- SEARCH ---

    private void onSearchTermChanged(String newTerm) {
        this.searchTerm = newTerm.toLowerCase().trim();
        ClientSession.setSearchTerm(this.searchTerm);
        refreshListContents();
    }

    // --- DATA REFRESH ---

    private void refreshListContents() {
        if (currentTab == TAB_MY_QUESTS) {
            List<Quest> quests = ClientCache.getMyQuests();

            // Filter by search term
            if (!searchTerm.isEmpty()) {
                quests = quests.stream()
                        .filter(q -> q.getTitle().toLowerCase().contains(searchTerm)
                                || (q.getContent() != null && q.getContent().toLowerCase().contains(searchTerm)))
                        .collect(Collectors.toList());
            } else {
                quests = new ArrayList<>(quests);
            }

            // Sort: pinned first, then by lastModified descending
            UUID pinnedId = ClientSession.getPinnedQuestId();
            quests.sort(Comparator
                    .<Quest, Boolean>comparing(q -> pinnedId != null && pinnedId.equals(q.getId()), Comparator.reverseOrder())
                    .thenComparing(Quest::getLastModified, Comparator.reverseOrder()));

            myQuestListWidget.setQuests(quests);
        } else {
            List<Quest> quests = ClientCache.getServerQuests();

            // Apply sub-filter
            if (serverFilter == FILTER_OPEN) {
                quests = quests.stream()
                        .filter(q -> q.getVisibility() == Visibility.OPEN)
                        .collect(Collectors.toList());
            } else if (serverFilter == FILTER_CLOSED) {
                quests = quests.stream()
                        .filter(q -> q.getVisibility() == Visibility.CLOSED)
                        .collect(Collectors.toList());
            } else {
                quests = new ArrayList<>(quests);
            }

            // Filter by search term
            if (!searchTerm.isEmpty()) {
                quests = quests.stream()
                        .filter(q -> q.getTitle().toLowerCase().contains(searchTerm)
                                || (q.getContent() != null && q.getContent().toLowerCase().contains(searchTerm)))
                        .collect(Collectors.toList());
            }

            // Sort by lastModified descending
            quests.sort(Comparator.comparing(Quest::getLastModified, Comparator.reverseOrder()));

            serverQuestListWidget.setQuests(quests);
        }
        updateActionButtons();
    }

    // --- SELECTION ---

    public void onQuestSelected() {
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (currentTab == TAB_MY_QUESTS) {
            boolean hasSelection = myQuestListWidget.getSelectedQuest() != null;
            openButton.active = hasSelection;
        } else {
            Quest selected = serverQuestListWidget.getSelectedQuest();
            boolean hasSelection = selected != null;
            openButton.active = hasSelection;
            joinButton.active = hasSelection && selected.getVisibility() == Visibility.OPEN;
            requestAccessButton.active = hasSelection && selected.getVisibility() == Visibility.CLOSED;
        }
    }

    // --- ACTIONS ---

    private void createNewQuest() {
        Quest newQuest = new Quest();
        newQuest.setId(UUID.randomUUID());
        newQuest.setTitle("New Quest");
        newQuest.setContent("");
        newQuest.setVisibility(Visibility.PRIVATE);
        UUID myUuid = ClientSession.getPlayerUuid();
        if (myUuid == null) {
            myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        }
        newQuest.setOwnerUuid(myUuid);
        newQuest.setOwnerName(MinecraftClient.getInstance().getSession().getUsername());
        newQuest.setLastModified(System.currentTimeMillis() / 1000);
        newQuest.setContributors(new ArrayList<>());
        open(new QuestScreen(this, newQuest, true));
    }

    public void openSelected() {
        if (currentTab == TAB_MY_QUESTS) {
            Quest sel = myQuestListWidget.getSelectedQuest();
            if (sel != null) {
                open(new QuestScreen(this, sel));
            }
        } else {
            Quest sel = serverQuestListWidget.getSelectedQuest();
            if (sel != null) {
                open(new QuestScreen(this, sel));
            }
        }
    }

    private void joinQuest() {
        Quest sel = serverQuestListWidget.getSelectedQuest();
        if (sel != null && sel.getVisibility() == Visibility.OPEN) {
            PacketSender.joinQuest(sel.getId());
        }
    }

    private void requestAccess() {
        Quest sel = serverQuestListWidget.getSelectedQuest();
        if (sel != null && sel.getVisibility() == Visibility.CLOSED) {
            PacketSender.requestCollaboration(sel.getId());
        }
    }

    // --- RENDERING ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Render active list
        if (currentTab == TAB_MY_QUESTS) {
            myQuestListWidget.render(context, mouseX, mouseY, delta);
        } else {
            serverQuestListWidget.render(context, mouseX, mouseY, delta);
        }

        // Render search field background + field
        UIHelper.drawPanel(context, this.searchField.x - 2, this.searchField.y,
                this.searchField.width + 4, this.searchField.height);
        this.searchField.render(context, mouseX, mouseY, delta);

        // Render notification badge on My Quests tab if there are pending requests
        int pendingCount = ClientSession.getPendingRequestCount();
        if (pendingCount > 0) {
            renderNotificationBadge(context, pendingCount);
        }
    }

    private void renderNotificationBadge(DrawContext context, int count) {
        String countStr = count > 99 ? "99+" : String.valueOf(count);
        int textWidth = this.textRenderer.getWidth(countStr);
        int badgeWidth = Math.max(textWidth + 4, 10);
        int badgeHeight = 10;

        // Position at top-right of My Quests tab
        int badgeX = myQuestsTab.getX() + myQuestsTab.getWidth() - badgeWidth / 2;
        int badgeY = myQuestsTab.getY() - badgeHeight / 2;

        // Draw red badge background
        int badgeColor = 0xFFCC3333;
        context.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, badgeColor);

        // Draw count text centered in badge
        int textX = badgeX + (badgeWidth - textWidth) / 2;
        int textY = badgeY + 1;
        context.drawText(this.textRenderer, countStr, textX, textY, Colors.TEXT_PRIMARY, false);
    }
}
