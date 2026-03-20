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
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MainScreen extends DisquestsBaseScreen {

    // Tabs
    private static final int TAB_MY_QUESTS = 0;
    private static final int TAB_SERVER_QUESTS = 1;

    // Sub-filter constants
    private static final int FILTER_ALL = 0;
    private static final int FILTER_OPEN = 1;
    private static final int FILTER_CLOSED = 2;

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
    private int currentTab;
    private int serverFilter;
    private int tickCounter = 0;
    private final ToastOverlay toast = new ToastOverlay();

    // Selection tracking
    @Nullable
    private QuestEntryComponent selectedEntry = null;

    // Saved filter row parent index for reinsertion
    private int filterRowIndex = -1;

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
        this.tabMyQuests.onPress(btn -> selectTab(TAB_MY_QUESTS));
        this.tabQuestBoard.onPress(btn -> selectTab(TAB_SERVER_QUESTS));

        // --- Wire filter button handlers ---
        this.filterAll.onPress(btn -> selectServerFilter(FILTER_ALL));
        this.filterOpen.onPress(btn -> selectServerFilter(FILTER_OPEN));
        this.filterClosed.onPress(btn -> selectServerFilter(FILTER_CLOSED));

        this.filterAll.tooltip(Text.literal("Show all visible quests"));
        this.filterOpen.tooltip(Text.literal("Quests anyone can join"));
        this.filterClosed.tooltip(Text.literal("Quests that require access request"));

        // --- Wire action button handlers ---
        this.btnNewQuest.onPress(btn -> createNewQuest());
        this.btnJoin.onPress(btn -> joinQuest());
        this.btnRequest.onPress(btn -> requestAccess());
        this.btnOpen.onPress(btn -> openSelected());
        this.btnClose.onPress(btn -> {
            if (this.client != null) this.client.setScreen(null);
        });

        // --- Create search text box programmatically ---
        this.searchField = UIComponents.textBox(Sizing.fixed(200));
        this.searchField.text(this.searchTerm);
        this.searchField.setPlaceholder(Text.literal("Search..."));
        this.searchField.onChanged().subscribe(this::onSearchTermChanged);
        this.searchRow.child(this.searchField);

        // Record filter row position for show/hide
        List<? extends io.wispforest.owo.ui.core.UIComponent> rootChildren = root.children();
        for (int i = 0; i < rootChildren.size(); i++) {
            if (rootChildren.get(i) == this.filterRow) {
                this.filterRowIndex = i;
                break;
            }
        }

        // --- Apply saved state ---
        selectTab(this.currentTab);
    }

    // --- TAB SWITCHING ---

    private void selectTab(int tab) {
        this.currentTab = tab;
        ClientSession.setActiveTab(tab);

        boolean isMyQuests = tab == TAB_MY_QUESTS;

        // Toggle tab active states (active selected tab appears pressed/disabled)
        tabMyQuests.active(!isMyQuests);
        tabQuestBoard.active(isMyQuests);

        // Clear selection
        clearSelection();

        // Filter row visibility: remove or re-add from root
        if (isMyQuests) {
            // Hide filter row
            if (filterRow.parent() != null) {
                rootLayout.removeChild(filterRow);
            }
        } else {
            // Show filter row (re-insert at saved index if not already present)
            if (filterRow.parent() == null && filterRowIndex >= 0) {
                rootLayout.child(filterRowIndex, filterRow);
            }
            selectServerFilter(this.serverFilter);
        }

        // Action button visibility: hide/show by removing/adding
        // New Quest only on My Quests; Join+Request only on Server Quests
        btnNewQuest.active(isMyQuests);
        btnJoin.active(!isMyQuests);
        btnRequest.active(!isMyQuests);

        // Use sizing to hide buttons not relevant to the current tab
        if (isMyQuests) {
            btnNewQuest.sizing(Sizing.fixed(70), Sizing.fixed(20));
            btnJoin.sizing(Sizing.fixed(0), Sizing.fixed(0));
            btnRequest.sizing(Sizing.fixed(0), Sizing.fixed(0));
        } else {
            btnNewQuest.sizing(Sizing.fixed(0), Sizing.fixed(0));
            btnJoin.sizing(Sizing.fixed(70), Sizing.fixed(20));
            btnRequest.sizing(Sizing.fixed(70), Sizing.fixed(20));
        }

        refreshListContents();
        updateActionButtons();
    }

    // --- SERVER FILTER ---

    private void selectServerFilter(int filter) {
        this.serverFilter = filter;
        ClientSession.setServerQuestsFilter(filter);

        // Active = clickable, so the currently-selected filter is NOT active (appears pressed)
        filterAll.active(filter != FILTER_ALL);
        filterOpen.active(filter != FILTER_OPEN);
        filterClosed.active(filter != FILTER_CLOSED);

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

    public void refreshListContents() {
        if (questList == null) return;

        questList.clearChildren();
        selectedEntry = null;

        List<Quest> quests;

        if (currentTab == TAB_MY_QUESTS) {
            quests = new ArrayList<>(ClientCache.getMyQuests());

            // Filter by search term
            if (!searchTerm.isEmpty()) {
                quests = quests.stream()
                        .filter(q -> q.getTitle().toLowerCase().contains(searchTerm)
                                || (q.getContent() != null && q.getContent().toLowerCase().contains(searchTerm)))
                        .collect(Collectors.toList());
            }

            // Sort: pinned first, then by lastModified descending
            quests.sort(Comparator
                    .<Quest, Boolean>comparing(q -> ClientSession.isPinned(q.getId()), Comparator.reverseOrder())
                    .thenComparing(Quest::getLastModified, Comparator.reverseOrder()));
        } else {
            quests = new ArrayList<>(ClientCache.getServerQuests());

            // Apply sub-filter
            if (serverFilter == FILTER_OPEN) {
                quests = quests.stream()
                        .filter(q -> q.getVisibility() == Visibility.OPEN)
                        .collect(Collectors.toList());
            } else if (serverFilter == FILTER_CLOSED) {
                quests = quests.stream()
                        .filter(q -> q.getVisibility() == Visibility.CLOSED)
                        .collect(Collectors.toList());
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
        }

        for (Quest quest : quests) {
            QuestEntryComponent entry = new QuestEntryComponent(quest);
            entry.sizing(Sizing.fill(100), Sizing.fixed(QuestEntryComponent.ENTRY_HEIGHT));
            entry.onClick(this::onEntryClicked);
            entry.onDoubleClick(e -> openSelected());
            entry.onPinToggle(e -> refreshAfterPinToggle());
            questList.child(entry);
        }

        updateActionButtons();
    }

    // --- SELECTION ---

    private void onEntryClicked(QuestEntryComponent entry) {
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
        // Only update buttons -- don't re-sort. Pin icon reads isPinned live each frame.
        // Full re-sort happens on screen open (build) and tab switch.
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (currentTab == TAB_MY_QUESTS) {
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
        btnRequest.setMessage(Text.literal("Requested"));
        btnRequest.active(false);
    }

    private void requestAccess() {
        Quest sel = getSelectedQuest();
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
            int titleWidth = this.textRenderer.getWidth(titleStr);
            int titleX = titleLabel.x();
            int titleY = titleLabel.y();

            boolean hovering = mouseX >= titleX && mouseX <= titleX + titleWidth
                    && mouseY >= titleY && mouseY <= titleY + this.textRenderer.fontHeight;

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

        // Notification badge on My Quests tab
        int pendingCount = ClientSession.getPendingRequestCount();
        if (pendingCount > 0 && tabMyQuests != null) {
            renderNotificationBadge(context, pendingCount);
        }

        // Toast overlay (renders on top of everything)
        toast.render(context, this.textRenderer, this.width, this.height - 40);
    }

    @Override
    public boolean shouldPause() {
        return false;
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
            case 0 -> { r = (int)(brightness * 255); g = (int)(t * 255); b = (int)(p * 255); }
            case 1 -> { r = (int)(q * 255); g = (int)(brightness * 255); b = (int)(p * 255); }
            case 2 -> { r = (int)(p * 255); g = (int)(brightness * 255); b = (int)(t * 255); }
            case 3 -> { r = (int)(p * 255); g = (int)(q * 255); b = (int)(brightness * 255); }
            case 4 -> { r = (int)(t * 255); g = (int)(p * 255); b = (int)(brightness * 255); }
            default -> { r = (int)(brightness * 255); g = (int)(p * 255); b = (int)(q * 255); }
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
        int badgeColor = 0xFFCC3333;
        context.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, badgeColor);

        // Draw count text centered in badge
        int textX = badgeX + (badgeWidth - textWidth) / 2;
        int textY = badgeY + 1;
        context.drawText(this.textRenderer, countStr, textX, textY, Colors.TEXT_PRIMARY, false);
    }
}
