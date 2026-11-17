package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.common.graph.LinkRules;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * What the "add a subquest..." and "Prerequisites" fields suggest. With nothing typed, only the
 * quests that can be added now; once the player types, every quest whose title contains the text,
 * the addable ones first, then by title, each refused one with its reason. The list is never cut.
 *
 * <p>The checks run {@link LinkRules} on the client's graph, which only holds what the player can
 * see: a best-effort answer for greying, the server decides on Save.
 */
public final class Suggestions {

  /** The {@code note} of an addable subquest that sits inside another quest and will move. */
  public static final String INSIDE_ANOTHER = "INSIDE_ANOTHER";

  /**
   * One suggested quest. {@code reason} is null when it can be added. {@code note}: for a subquest
   * refused with {@link Refusal#NEED_OWNER}, the role the player holds on it ("READ", "WRITE", or
   * "" for none); for an addable subquest inside another quest, {@link #INSIDE_ANOTHER}; otherwise
   * null.
   */
  public record Suggestion(UUID id, String title, Refusal reason, String note) {}

  private Suggestions() {}

  /**
   * Quests to put under {@code questOrNull} (null for a quest not saved yet, for which only
   * ownership, completion and depth are checked). Candidates are the quests the player sees fully:
   * not the quest itself and not locked for the player. With nothing typed, direct subquests are
   * left out too.
   */
  public static List<Suggestion> subquests(
      QuestGraph effective, UUID me, UUID questOrNull, String typed, int maxDepth) {
    String text = normalise(typed);
    List<Suggestion> result = new ArrayList<>();
    for (QuestData c : effective.all()) {
      if (c.locked() || c.id().equals(questOrNull) || !titleContains(c, text)) continue;
      if (text.isEmpty() && questOrNull != null && questOrNull.equals(c.parentId())) continue;
      Optional<Refusal> refusal =
          questOrNull == null
              ? underNewQuest(effective, me, c, maxDepth)
              : LinkRules.attach(effective, me, c.id(), questOrNull, maxDepth);
      if (text.isEmpty() && refusal.isPresent()) continue;
      result.add(new Suggestion(c.id(), c.title(), refusal.orElse(null), noteFor(c, me, refusal)));
    }
    result.sort(ORDER);
    return result;
  }

  /**
   * Quests {@code questOrNull} could wait on (null for a quest not saved yet). Candidates are every
   * quest the player can see, locked ones included, except the quest itself and the ones in {@code
   * alreadyChosen}, the editor's current list, which stands for the quest's prerequisites in the
   * checks.
   */
  public static List<Suggestion> prerequisites(
      QuestGraph effective,
      UUID me,
      UUID questOrNull,
      Collection<UUID> alreadyChosen,
      String typed) {
    String text = normalise(typed);
    QuestGraph g = withPrerequisites(effective, questOrNull, alreadyChosen);
    List<Suggestion> result = new ArrayList<>();
    for (QuestData c : g.all()) {
      if (c.id().equals(questOrNull) || alreadyChosen.contains(c.id())) continue;
      if (!titleContains(c, text)) continue;
      Optional<Refusal> refusal =
          questOrNull == null
              ? alreadyChosen.size() >= LinkRules.MAX_PREREQUISITES
                  ? Optional.of(Refusal.TOO_MANY_PREREQUISITES)
                  : Optional.empty()
              : LinkRules.prerequisite(g, me, questOrNull, c.id());
      if (text.isEmpty() && refusal.isPresent()) continue;
      result.add(new Suggestion(c.id(), c.title(), refusal.orElse(null), null));
    }
    result.sort(ORDER);
    return result;
  }

  private static final Comparator<Suggestion> ORDER =
      Comparator.<Suggestion, Boolean>comparing(s -> s.reason() != null)
          .thenComparing(s -> s.title() == null ? "" : s.title().toLowerCase(Locale.ROOT))
          .thenComparing(Suggestion::id);

  /** The checks {@link LinkRules#attach} makes that apply to a parent with no place yet. */
  private static Optional<Refusal> underNewQuest(QuestGraph g, UUID me, QuestData c, int maxDepth) {
    if (c.roleOf(me) != Role.OWNER) return Optional.of(Refusal.NEED_OWNER);
    if (c.isCompleted()) return Optional.of(Refusal.COMPLETED);
    int limit = maxDepth > 0 ? Math.min(maxDepth, QuestGraph.MAX_WALK) : QuestGraph.MAX_WALK;
    if (1 + g.height(c.id()) > limit) return Optional.of(Refusal.TOO_DEEP);
    return Optional.empty();
  }

  private static String noteFor(QuestData c, UUID me, Optional<Refusal> refusal) {
    if (refusal.isEmpty()) {
      return c.parentId() != null || c.hiddenParent() ? INSIDE_ANOTHER : null;
    }
    if (refusal.get() == Refusal.NEED_OWNER) {
      Role held = c.roleOf(me);
      return held == null ? "" : held.name();
    }
    return null;
  }

  private static QuestGraph withPrerequisites(
      QuestGraph g, UUID quest, Collection<UUID> prerequisites) {
    if (quest == null || g.get(quest) == null) return g;
    List<QuestData> quests = new ArrayList<>();
    for (QuestData q : g.all()) {
      quests.add(q.id().equals(quest) ? q.withPrerequisites(List.copyOf(prerequisites)) : q);
    }
    return QuestGraph.of(quests);
  }

  private static String normalise(String typed) {
    return typed == null ? "" : typed.strip().toLowerCase(Locale.ROOT);
  }

  private static boolean titleContains(QuestData q, String text) {
    return text.isEmpty()
        || (q.title() != null && q.title().toLowerCase(Locale.ROOT).contains(text));
  }
}
