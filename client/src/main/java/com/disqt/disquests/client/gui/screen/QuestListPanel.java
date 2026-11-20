package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.NewMarks;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.data.QuestGraphs;
import com.disqt.disquests.client.gui.component.QuestEntryComponent;
import com.disqt.disquests.client.gui.component.QuestEntryLayout;
import com.disqt.disquests.client.gui.component.TreeToggleComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.tree.ListedQuests;
import com.disqt.disquests.client.gui.tree.NewMarkRows;
import com.disqt.disquests.client.gui.tree.PlacedSections;
import com.disqt.disquests.client.gui.tree.QuestTreeRows;
import com.disqt.disquests.client.gui.tree.QuestTreeRows.Row;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The main screen's quest list, in both layouts: the rows of {@link QuestTreeRows#forTab} for the
 * current tab, search and filter, each a horizontal {@code quest-row-<id>} holding the expand
 * toggle ({@code toggle-<id>}, only for a quest with listed subquests) and the {@link
 * QuestEntryComponent}, indented by depth. Which quests are expanded lives in {@link
 * ClientSession}, so it survives tab switches and new screens; a toggle rebuilds only this list.
 * Without any toggle in the list (a flat list, as against an older server) rows have no indent and
 * no toggle slot, exactly as before.
 */
public final class QuestListPanel {

  private final FlowLayout list;
  private final ScrollContainer<?> scroll;
  private final boolean mapMode;
  private final Consumer<Quest> onEntryClicked;
  private final Runnable onOpen;
  private final Runnable onPinToggle;
  private final Runnable onSelectionLost;

  private ClientSession.Tab tab = ClientSession.Tab.MY_QUESTS;
  private ClientSession.QuestFilter filter = ClientSession.QuestFilter.ALL;
  private String search = "";
  private List<Row> rows = List.of();

  @Nullable private UUID selectedId;
  @Nullable private UUID pendingScroll;

  /**
   * @param onEntryClicked a click selected this quest
   * @param onOpen a double click asks to open the selected quest
   * @param onPinToggle an entry's pin was toggled
   * @param onSelectionLost a toggle collapsed the selected quest's row away (rebuilds asked for by
   *     the screen report it through their return value instead)
   */
  QuestListPanel(
      FlowLayout list,
      ScrollContainer<?> scroll,
      boolean mapMode,
      Consumer<Quest> onEntryClicked,
      Runnable onOpen,
      Runnable onPinToggle,
      Runnable onSelectionLost) {
    this.list = list;
    this.scroll = scroll;
    this.mapMode = mapMode;
    this.onEntryClicked = onEntryClicked;
    this.onOpen = onOpen;
    this.onPinToggle = onPinToggle;
    this.onSelectionLost = onSelectionLost;
  }

  // --- what the list shows ---

  /**
   * Shows the rows for these settings; the selection stays when its row is still listed. The caller
   * refreshes what depends on the selection afterwards.
   */
  void show(ClientSession.Tab tab, ClientSession.QuestFilter filter, String search) {
    this.tab = tab;
    this.filter = filter;
    this.search = search != null ? search : "";
    rebuild();
  }

  /**
   * Rebuilds the rows with the current settings, the cache and the expanded quests. Returns true
   * when the selected quest's row is gone, in which case the selection has been cleared.
   */
  private boolean rebuild() {
    QuestGraph graph = QuestGraphs.current();
    UUID me = ClientSession.getEffectivePlayerUuid();
    rows =
        QuestTreeRows.forTab(
            graph, me, treeTab(tab), search, ClientSession.getExpandedQuests(), listOptions());

    list.clearChildren();
    OptionalInt placedStart = placedStart(graph);
    if (placedStart.isPresent()) list.child(sectionHeader("unplaced-header", "unplaced"));
    boolean toggles = rows.stream().anyMatch(r -> r.childCount() > 0);
    boolean selectedListed = false;
    NewMarks newMarks = ClientSession.newMarks();
    // A new quest marks its own row and every listed row above it, collapsed or not
    Set<UUID> showingNew =
        NewMarkRows.showingNew(
            graph,
            ListedQuests.fullyExpanded(graph, me, treeTab(tab), search, listOptions()),
            newMarks::isNew);
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      if (placedStart.isPresent() && placedStart.getAsInt() == i) {
        list.child(sectionHeader("placed-header", "placed"));
      }
      Quest quest = ClientCache.getQuestById(row.id());
      if (quest == null) continue;
      boolean selected = row.id().equals(selectedId);
      selectedListed |= selected;
      list.child(rowFor(row, quest, toggles, selected, showingNew.contains(row.id()), me));
    }
    if (selectedId != null && !selectedListed) {
      selectedId = null;
      return true;
    }
    return false;
  }

  /**
   * Where the "On the map" header goes, when the list has both unplaced and placed top-level
   * quests; the "Unplaced" header then heads the list. The headers only separate the two kinds:
   * with one kind there is nothing to separate. Never on Archived, and only on the map layout.
   */
  private OptionalInt placedStart(QuestGraph graph) {
    if (!mapMode || tab == ClientSession.Tab.ARCHIVED) return OptionalInt.empty();
    return PlacedSections.firstPlacedRow(
        rows,
        id -> {
          QuestData q = graph.get(id);
          return q != null && (q.coordinates() != null || !q.polygon().isEmpty());
        });
  }

  private static LabelComponent sectionHeader(String id, String key) {
    LabelComponent header = UIComponents.label(Component.translatable("gui.disquests.map." + key));
    header.color(Color.ofArgb(Colors.TEXT_MUTED));
    header.id(id);
    return header;
  }

  private FlowLayout rowFor(
      Row row, Quest quest, boolean toggles, boolean selected, boolean isNew, UUID me) {
    FlowLayout line = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
    line.id("quest-row-" + row.id());
    int indent = QuestEntryLayout.indent(row.depth());
    if (indent > 0) line.padding(Insets.left(indent));

    boolean needsAttention =
        quest.isOwner(me) && (quest.getPrivatized() != null || quest.hasDeletedPrerequisite());
    QuestEntryComponent entry =
        new QuestEntryComponent(
            quest,
            new QuestEntryComponent.Marks(row.locked(), isNew, needsAttention, row.childCount()));
    entry.sizing(Sizing.expand(100), Sizing.fixed(QuestEntryComponent.ENTRY_HEIGHT));
    entry.onClick(this::select);
    entry.onDoubleClick(e -> onOpen.run());
    entry.onPinToggle(e -> onPinToggle.run());
    entry.selected(selected);

    if (row.childCount() > 0) {
      line.child(new TreeToggleComponent(row.id(), row.expanded(), search.isBlank(), this::toggle));
    } else if (toggles) {
      entry.margins(Insets.left(QuestEntryLayout.toggleSlot(true)));
    }
    line.child(entry);
    return line;
  }

  /**
   * Flips the quest in the stored set. Ignored while searching: the search shows its matches with
   * their ancestors expanded, and those forced expansions must not end up in the stored set.
   */
  private void toggle(UUID questId) {
    if (!search.isBlank()) return;
    ClientSession.toggleExpanded(questId);
    if (rebuild()) onSelectionLost.run();
  }

  // --- selection ---

  private void select(QuestEntryComponent entry) {
    for (QuestEntryComponent e : entries()) e.selected(e == entry);
    selectedId = entry.getQuest().getId();
    onEntryClicked.accept(entry.getQuest());
  }

  /** The selected quest, or null. */
  @Nullable
  Quest selectedQuest() {
    return selectedId != null ? ClientCache.getQuestById(selectedId) : null;
  }

  @Nullable
  UUID selectedId() {
    return selectedId;
  }

  void clearSelection() {
    selectedId = null;
    for (QuestEntryComponent e : entries()) e.selected(false);
  }

  /**
   * Selects the quest and makes its row visible: its ancestors are expanded (in the stored set) and
   * the list scrolls to it on the next tick, once laid out. When the tab does not list it, nothing
   * stays selected. Either way the caller refreshes what depends on the selection afterwards.
   */
  void reveal(UUID questId) {
    for (QuestData ancestor : QuestGraphs.current().ancestors(questId)) {
      ClientSession.expand(ancestor.id());
    }
    selectedId = questId;
    pendingScroll = questId;
    rebuild();
  }

  /** Scrolls a revealed row into view once the list has been laid out. */
  void tick() {
    if (pendingScroll == null) return;
    for (QuestEntryComponent entry : entries()) {
      if (entry.getQuest().getId().equals(pendingScroll) && entry.hasParent()) {
        scroll.scrollTo(entry.parent());
        break;
      }
    }
    pendingScroll = null;
  }

  // --- reading the list ---

  /** The listed quests, in list order. */
  List<Quest> visibleQuests() {
    List<Quest> quests = new ArrayList<>();
    for (Row row : rows) {
      Quest quest = ClientCache.getQuestById(row.id());
      if (quest != null) quests.add(quest);
    }
    return quests;
  }

  List<QuestEntryComponent> entries() {
    return entries(list);
  }

  /**
   * The entries of a quest list in list order, wherever they sit in its rows. Used by E2E tests.
   */
  public static List<QuestEntryComponent> entries(ParentUIComponent questList) {
    List<QuestEntryComponent> entries = new ArrayList<>();
    for (UIComponent child : questList.children()) {
      QuestEntryComponent entry = entryOf(child);
      if (entry != null) entries.add(entry);
    }
    return entries;
  }

  /** The entry of a list child: the entry itself or the one in a row; null for the header. */
  @Nullable
  public static QuestEntryComponent entryOf(UIComponent child) {
    if (child instanceof QuestEntryComponent entry) return entry;
    if (child instanceof ParentUIComponent row) {
      for (UIComponent c : row.children()) {
        if (c instanceof QuestEntryComponent entry) return entry;
      }
    }
    return null;
  }

  // --- mapping to the tree rows' own enums ---

  static QuestTreeRows.Tab treeTab(ClientSession.Tab tab) {
    return switch (tab) {
      case MY_QUESTS -> QuestTreeRows.Tab.MY_QUESTS;
      case SERVER_QUESTS -> QuestTreeRows.Tab.QUEST_BOARD;
      case ARCHIVED -> QuestTreeRows.Tab.ARCHIVED;
    };
  }

  static QuestTreeRows.BoardFilter boardFilter(ClientSession.QuestFilter filter) {
    return switch (filter) {
      case ALL -> QuestTreeRows.BoardFilter.ALL;
      case OPEN -> QuestTreeRows.BoardFilter.OPEN;
      case CLOSED -> QuestTreeRows.BoardFilter.CLOSED;
    };
  }

  /** Pins, the board's filter and, on the map layout, unplaced quests first. */
  QuestTreeRows.ListOptions listOptions() {
    return new QuestTreeRows.ListOptions(
        Set.copyOf(ClientSession.getPinnedQuestIds()), boardFilter(filter), mapMode);
  }

  ClientSession.Tab tab() {
    return tab;
  }

  String search() {
    return search;
  }
}
