package com.disqt.disquests.client.markdown;

import com.disqt.disquests.client.data.Quest;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Wiki-links as the client reads them, without Minecraft. The server sends {@code [[uuid|Title]]}
 * for a quest the reader sees fully and {@code [[uuid|Private Quest]]} otherwise; {@link #target}
 * decides whether a link opens anything for the player (rendering, hover and click all ask it), and
 * {@link #toEditingForm} turns received content into the text the editor shows.
 */
public final class WikiLinks {

  /** {@code [[id|text]]}: group 1 the id (may be empty), group 2 the text. */
  static final Pattern ID_FORM = Pattern.compile("\\[\\[([^|\\]]*)\\|([^\\]]*)\\]\\]");

  private WikiLinks() {}

  /**
   * The quest a link with this id opens for the player, or null when it does not resolve: no id, a
   * malformed one, a quest not in the cache, or a quest held as a locked view. A locked view is
   * treated as absent so the client never undoes the server's placeholder (its title, hover preview
   * or page).
   */
  @Nullable
  public static Quest target(@Nullable String id, Function<UUID, Quest> lookup) {
    return target(parse(id), lookup);
  }

  /**
   * The same rule for a quest id already parsed: the quest when the player sees it fully, else
   * null. Every hover preview of a quest (wiki-links, prerequisites, tree rows) asks this.
   */
  @Nullable
  public static Quest target(@Nullable UUID questId, Function<UUID, Quest> lookup) {
    if (questId == null) return null;
    Quest quest = lookup.apply(questId);
    return quest != null && !quest.isLocked() ? quest : null;
  }

  /**
   * Received content as the editor shows it: a link to a quest that resolves ({@link #target})
   * becomes {@code [[Current Title]]}; a link with no id or a malformed one becomes {@code
   * [[text]]}; a link to a quest the player does not see fully keeps its {@code [[uuid|text]]}
   * form, so saving it neither stores the placeholder as a title nor breaks the link for others.
   * Converting the result again changes nothing.
   */
  public static String toEditingForm(@Nullable String content, Function<UUID, Quest> lookup) {
    if (content == null || content.isEmpty()) return content;
    return ID_FORM
        .matcher(content)
        .replaceAll(
            m -> {
              String text = m.group(2);
              if (parse(m.group(1)) == null) return Matcher.quoteReplacement("[[" + text + "]]");
              Quest quest = target(m.group(1), lookup);
              if (quest == null) return Matcher.quoteReplacement(m.group(0));
              String title = quest.getTitle() != null ? quest.getTitle() : text;
              return Matcher.quoteReplacement("[[" + title + "]]");
            });
  }

  /**
   * The {@code [[} autocomplete: titles of the given quests (mine first, then the board's) that
   * start with the query, ignoring case, at most {@code limit}. Only quests the player sees fully
   * are offered: a link to a locked view would render as the placeholder for its own author.
   */
  public static java.util.List<String> suggestions(
      java.util.List<Quest> quests, String query, int limit) {
    String lower = query.toLowerCase(java.util.Locale.ROOT);
    return quests.stream()
        .filter(q -> !q.isLocked())
        .map(Quest::getTitle)
        .filter(java.util.Objects::nonNull)
        .filter(t -> lower.isEmpty() || t.toLowerCase(java.util.Locale.ROOT).startsWith(lower))
        .limit(limit)
        .toList();
  }

  @Nullable
  private static UUID parse(@Nullable String id) {
    if (id == null || id.isEmpty()) return null;
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
