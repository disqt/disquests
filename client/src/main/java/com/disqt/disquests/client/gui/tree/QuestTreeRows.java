package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The indented quest lists: the My Quests, Quest Board and Archived tabs, the "where this sits"
 * pane of the reading view and the tree of the edit pane. Works on the graph of what the server
 * sent this player, so every quest in it is one the player can see.
 *
 * <p>Siblings keep today's list order: on My Quests pinned quests first, then the most recently
 * changed first; on the board the most recently changed first; on the map layout unplaced quests
 * before all of that. Locked views carry no time of last change, so they follow the other siblings,
 * by title, instead of sinking in an arbitrary order.
 */
public final class QuestTreeRows {

  /**
   * The list tabs, mirroring {@code ClientSession.Tab}. This package keeps its own enums because
   * {@code ClientSession} reaches Minecraft classes (through {@code SharedTileCache}), and the
   * classes here must load in plain unit tests.
   */
  public enum Tab {
    MY_QUESTS,
    QUEST_BOARD,
    ARCHIVED
  }

  /** The Quest Board's Open/Closed filter, mirroring {@code ClientSession.QuestFilter}. */
  public enum BoardFilter {
    ALL,
    OPEN,
    CLOSED
  }

  /** Rows indent at most this many levels; deeper quests line up with the sixth level. */
  public static final int MAX_INDENT = 6;

  /**
   * One line of a list. {@code depth} 0 is top level, capped at {@link #MAX_INDENT}. {@code
   * childCount} counts the listed quests directly inside this one, and {@code expanded} says they
   * are shown below it. {@code locked} is the view's own flag; {@code joined} that the player holds
   * a role on the quest.
   */
  public record Row(
      UUID id, int depth, int childCount, boolean expanded, boolean locked, boolean joined) {}

  /**
   * The list settings kept by the main screen: pinned quests (My Quests only), the Open/Closed
   * filter (Quest Board only) and whether unplaced quests come first (map layout).
   */
  public record ListOptions(Set<UUID> pinned, BoardFilter filter, boolean unplacedFirst) {
    public static final ListOptions DEFAULT = new ListOptions(Set.of(), BoardFilter.ALL, false);
  }

  private QuestTreeRows() {}

  public static List<Row> forTab(
      QuestGraph graph, UUID me, Tab tab, String search, Set<UUID> expanded) {
    return forTab(graph, me, tab, search, expanded, ListOptions.DEFAULT);
  }

  /**
   * The rows of a tab. My Quests: the active quests the player holds a role on, and an active quest
   * they hold no role on only when its parent is listed in My Quests (so it always nests under
   * something of theirs). Quest Board: the active quests that are not Private and that the player
   * holds no role on, as today. A quest whose parent is not listed is top level. Without a search,
   * a quest's subquests show only when it is in {@code expanded} (and its own parent is shown
   * expanded); with one, the matches show with every listed ancestor expanded and nothing else.
   * Archived stays a flat list of the player's completed quests, newest completion first.
   */
  public static List<Row> forTab(
      QuestGraph graph, UUID me, Tab tab, String search, Set<UUID> expanded, ListOptions options) {
    Predicate<QuestData> matches = Search.parse(search);
    if (tab == Tab.ARCHIVED) return archived(graph, me, matches);

    Map<UUID, QuestData> listed = new LinkedHashMap<>();
    for (QuestData q : graph.all()) {
      boolean in =
          tab == Tab.MY_QUESTS ? inMyQuests(graph, me, q) : onBoard(me, options.filter(), q);
      if (in) listed.put(q.id(), q);
    }
    Map<UUID, List<QuestData>> children = new HashMap<>();
    List<QuestData> roots = new ArrayList<>();
    for (QuestData q : listed.values()) {
      if (listed.containsKey(q.parentId())) {
        children.computeIfAbsent(q.parentId(), k -> new ArrayList<>()).add(q);
      } else {
        roots.add(q);
      }
    }

    Set<UUID> open = expanded;
    if (!Search.isEmpty(search)) {
      Set<UUID> shown = new HashSet<>();
      for (QuestData q : listed.values()) {
        if (!matches.test(q)) continue;
        QuestData current = q;
        for (int step = 0; current != null && step <= QuestGraph.MAX_WALK; step++) {
          if (!shown.add(current.id())) break;
          current = listed.get(current.parentId());
        }
      }
      roots.removeIf(q -> !shown.contains(q.id()));
      for (List<QuestData> list : children.values()) list.removeIf(q -> !shown.contains(q.id()));
      open = shown;
    }

    Comparator<QuestData> order = siblingOrder(tab, options);
    Builder builder = new Builder(me, children, order);
    roots.sort(order);
    for (QuestData root : roots) builder.subtree(root, 0, open);
    return builder.rows;
  }

  /**
   * The "where this sits" pane: the quest's ancestors, top first, then the quests under the same
   * parent with the quest among them and its subquests below it, completed quests included. The
   * quest's own subquests always show; deeper levels and the siblings' subquests only when
   * expanded. Empty for a quest with no visible parent and no visible subquests.
   */
  public static List<Row> whereThisSits(QuestGraph graph, UUID me, UUID quest, Set<UUID> expanded) {
    QuestData q = graph.get(quest);
    if (q == null) return List.of();
    QuestData parent = graph.get(q.parentId());
    if (parent == null && graph.children(quest).isEmpty()) return List.of();

    Comparator<QuestData> order = siblingOrder(null, ListOptions.DEFAULT);
    Builder builder = new Builder(me, childrenOf(graph), order);
    List<QuestData> ancestors = new ArrayList<>(graph.ancestors(quest));
    Collections.reverse(ancestors);
    List<QuestData> level =
        new ArrayList<>(parent == null ? List.of(q) : graph.children(parent.id()));
    // Each ancestor shows only the next one down, and the parent shows the quest's level.
    int depth = 0;
    for (QuestData ancestor : ancestors) {
      int shown = depth == ancestors.size() - 1 ? level.size() : 1;
      builder.line(ancestor, depth++, true, shown);
    }

    level.sort(order);
    Set<UUID> open = new HashSet<>(expanded);
    open.add(quest);
    for (QuestData sibling : level) builder.subtree(sibling, depth, open);
    return builder.rows;
  }

  /**
   * The edit pane's tree: the quest's top visible ancestor and everything under it, all expanded,
   * completed quests included. A quest on its own is its own line.
   */
  public static List<Row> wholeTree(QuestGraph graph, UUID me, UUID quest) {
    QuestData q = graph.get(quest);
    if (q == null) return List.of();
    List<QuestData> ancestors = graph.ancestors(quest);
    QuestData top = ancestors.isEmpty() ? q : ancestors.get(ancestors.size() - 1);
    Builder builder = new Builder(me, childrenOf(graph), siblingOrder(null, ListOptions.DEFAULT));
    builder.expandAll = true;
    builder.subtree(top, 0, Set.of());
    return builder.rows;
  }

  // --- tabs ---

  /**
   * Active, and either held by the player or directly inside a quest that is itself in My Quests.
   * Walks up until it meets a quest the player holds, a completed one or the top.
   */
  private static boolean inMyQuests(QuestGraph graph, UUID me, QuestData q) {
    for (int step = 0; q != null && step <= QuestGraph.MAX_WALK; step++) {
      if (q.isCompleted()) return false;
      if (q.roleOf(me) != null) return true;
      q = graph.get(q.parentId());
    }
    return false;
  }

  private static boolean onBoard(UUID me, BoardFilter filter, QuestData q) {
    if (q.isCompleted()) return false;
    if (q.visibility() == Visibility.PRIVATE || q.roleOf(me) != null) return false;
    return switch (filter) {
      case ALL -> true;
      case OPEN -> q.visibility() == Visibility.OPEN;
      case CLOSED -> q.visibility() == Visibility.CLOSED;
    };
  }

  private static List<Row> archived(QuestGraph graph, UUID me, Predicate<QuestData> matches) {
    List<QuestData> quests = new ArrayList<>();
    for (QuestData q : graph.all()) {
      if (q.isCompleted() && !q.locked() && q.roleOf(me) != null && matches.test(q)) quests.add(q);
    }
    quests.sort(Comparator.comparingLong(QuestData::completedAt).reversed());
    List<Row> rows = new ArrayList<>();
    for (QuestData q : quests) rows.add(new Row(q.id(), 0, 0, false, false, true));
    return rows;
  }

  // --- order ---

  /** Today's list order for one level of siblings; {@code tab} null for the panes of a quest. */
  private static Comparator<QuestData> siblingOrder(Tab tab, ListOptions options) {
    Comparator<QuestData> order = (a, b) -> 0;
    if (options.unplacedFirst()) order = order.thenComparing(QuestTreeRows::isPlaced);
    if (tab == Tab.MY_QUESTS) {
      order = order.thenComparing(q -> !options.pinned().contains(q.id()));
    }
    return order
        .thenComparing(QuestData::locked)
        .thenComparing(
            (a, b) ->
                a.locked()
                    ? titleKey(a).compareTo(titleKey(b))
                    : Long.compare(b.lastModified(), a.lastModified()));
  }

  private static boolean isPlaced(QuestData q) {
    return q.coordinates() != null || !q.polygon().isEmpty();
  }

  private static String titleKey(QuestData q) {
    return q.title() == null ? "" : q.title().toLowerCase(Locale.ROOT);
  }

  // --- building ---

  private static Map<UUID, List<QuestData>> childrenOf(QuestGraph graph) {
    Map<UUID, List<QuestData>> children = new HashMap<>();
    for (QuestData q : graph.all()) {
      List<QuestData> list = graph.children(q.id());
      if (!list.isEmpty()) children.put(q.id(), new ArrayList<>(list));
    }
    return children;
  }

  private static final class Builder {
    private final UUID me;
    private final Map<UUID, List<QuestData>> children;
    private final Comparator<QuestData> order;
    private final Set<UUID> seen = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private boolean expandAll;

    Builder(UUID me, Map<UUID, List<QuestData>> children, Comparator<QuestData> order) {
      this.me = me;
      this.children = children;
      this.order = order;
    }

    void line(QuestData q, int depth, boolean expanded) {
      line(q, depth, expanded, children.getOrDefault(q.id(), List.of()).size());
    }

    void line(QuestData q, int depth, boolean expanded, int childCount) {
      seen.add(q.id());
      rows.add(
          new Row(
              q.id(),
              Math.min(depth, MAX_INDENT),
              childCount,
              expanded && childCount > 0,
              q.locked(),
              q.roleOf(me) != null));
    }

    /** The quest's line and, when it is open, its children's subtrees in sibling order. */
    void subtree(QuestData q, int depth, Set<UUID> open) {
      if (seen.contains(q.id())) return;
      boolean expanded = expandAll || open.contains(q.id());
      line(q, depth, expanded);
      if (!expanded) return;
      List<QuestData> below = new ArrayList<>(children.getOrDefault(q.id(), List.of()));
      below.sort(order);
      for (QuestData child : below) subtree(child, depth + 1, open);
    }
  }

  // --- search ---

  /**
   * The main screen's search: words must appear in the title, description or a tag, and every
   * {@code #tag} must be one of the quest's tags. Case does not matter.
   */
  static final class Search {
    private Search() {}

    static boolean isEmpty(String raw) {
      return raw == null || raw.isBlank();
    }

    static Predicate<QuestData> parse(String raw) {
      if (isEmpty(raw)) return q -> true;
      List<String> tagFilters = new ArrayList<>();
      StringBuilder text = new StringBuilder();
      for (String token : raw.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
        if (token.startsWith("#") && token.length() > 1) {
          tagFilters.add(token.substring(1));
        } else {
          if (text.length() > 0) text.append(' ');
          text.append(token);
        }
      }
      String tf = text.toString();
      return q -> {
        if (!tf.isEmpty()) {
          boolean textMatch =
              lower(q.title()).contains(tf)
                  || lower(q.content()).contains(tf)
                  || q.tags().stream().anyMatch(t -> lower(t).contains(tf));
          if (!textMatch) return false;
        }
        for (String tag : tagFilters) {
          if (q.tags().stream().noneMatch(t -> t.equalsIgnoreCase(tag))) return false;
        }
        return true;
      };
    }

    private static String lower(String s) {
      return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
  }
}
