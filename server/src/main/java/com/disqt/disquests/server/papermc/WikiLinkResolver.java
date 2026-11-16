package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves wiki-style links ([[Quest Name]]) in quest content on a per-recipient basis.
 *
 * <ul>
 *   <li>Forward resolution ({@link #resolveForRecipient}): raw {@code [[Quest Name]]} → {@code
 *       [[uuid|Quest Name]]} (or {@code [[uuid|Private Quest]]} / {@code [[|Quest Name]]})
 *   <li>Reverse resolution ({@link #reverseResolve}): {@code [[uuid|title]]} → {@code [[Current
 *       Title]]}
 * </ul>
 *
 * Both directions decide with the quest graph and {@link QuestGraph#seesFully}, as {@link Delivery}
 * does for the quest itself: a player learns a linked quest's title only when they see it fully.
 */
public class WikiLinkResolver {

  private static final int MAX_WIKI_LINKS = 16;

  /** Matches raw {@code [[text]]} where text contains no {@code |} or {@code ]}. */
  private static final Pattern RAW_PATTERN = Pattern.compile("\\[\\[([^\\]|]+)\\]\\]");

  /**
   * Matches the {@code [[uuid|title]]} form specifically. Group 1 = UUID (may be empty string for
   * broken links), group 2 = display title.
   */
  private static final Pattern PIPE_PATTERN =
      Pattern.compile("\\[\\[([^|\\]]*?)\\|([^\\]]*)\\]\\]");

  // -------------------------------------------------------------------------
  // Forward resolution: [[Quest Name]] → [[uuid|title]] or [[|title]] or [[uuid|Private Quest]]
  // -------------------------------------------------------------------------

  /**
   * Scans content for raw {@code [[Quest Name]]} patterns and resolves each for the recipient,
   * deciding with the graph: a quest the recipient cannot see, or sees as locked, becomes {@code
   * [[uuid|Private Quest]]}; a title that matches no quest becomes {@code [[|Quest Name]]}. Titles
   * match ignoring case; when several quests share a title, one the recipient sees fully wins, and
   * among equals the smallest id. At most {@value #MAX_WIKI_LINKS} links are processed; excess are
   * left as-is.
   */
  public String resolveForRecipient(QuestGraph graph, String content, UUID recipientUuid) {
    Matcher matcher = RAW_PATTERN.matcher(content);
    Set<String> lowerTitles = new HashSet<>();
    int found = 0;
    while (matcher.find() && found < MAX_WIKI_LINKS) {
      lowerTitles.add(matcher.group(1).toLowerCase(Locale.ROOT));
      found++;
    }
    if (lowerTitles.isEmpty()) return content;
    Map<String, QuestData> byTitle = byTitle(graph, lowerTitles, recipientUuid);

    StringBuilder result = new StringBuilder();
    matcher.reset();
    int processedCount = 0;

    while (matcher.find()) {
      String displayText = matcher.group(1);
      if (processedCount >= MAX_WIKI_LINKS) {
        // Leave this link raw — append as-is
        matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
        continue;
      }
      processedCount++;

      QuestData quest = byTitle.get(displayText.toLowerCase(Locale.ROOT));
      String replacement;
      if (quest == null) {
        // Quest not found — broken link
        replacement = "[[|" + displayText + "]]";
      } else if (!graph.seesFully(recipientUuid, quest.id())) {
        replacement = "[[" + quest.id() + "|Private Quest]]";
      } else {
        replacement = "[[" + quest.id() + "|" + displayText + "]]";
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * The quest each lowercased title links to. On a tie, a quest the recipient sees fully beats one
   * they do not, so a hidden namesake never breaks a working link; then the smallest id wins.
   */
  private static Map<String, QuestData> byTitle(
      QuestGraph graph, Set<String> lowerTitles, UUID recipient) {
    Map<String, QuestData> result = new HashMap<>();
    Set<UUID> visible = new HashSet<>();
    for (QuestData quest : graph.all()) {
      String lowerTitle = quest.title().toLowerCase(Locale.ROOT);
      if (!lowerTitles.contains(lowerTitle)) continue;
      boolean questVisible = graph.seesFully(recipient, quest.id());
      if (questVisible) visible.add(quest.id());
      QuestData kept = result.get(lowerTitle);
      boolean better =
          kept == null
              || (questVisible && !visible.contains(kept.id()))
              || (questVisible == visible.contains(kept.id())
                  && quest.id().toString().compareTo(kept.id().toString()) < 0);
      if (better) result.put(lowerTitle, quest);
    }
    return result;
  }

  // -------------------------------------------------------------------------
  // Reverse resolution: [[uuid|title]] → [[Current Title]]
  // -------------------------------------------------------------------------

  /**
   * Scans content for {@code [[uuid|title]]} patterns and converts each back to the canonical
   * {@code [[Current Title]]} form when the sender sees the linked quest fully in {@code graph}. A
   * link to a quest they cannot see, or that is locked for them, is kept as it is, so a save never
   * stores the title of a quest its sender may not read. A link with no id, or the id of a quest
   * that no longer exists, keeps its display text.
   */
  public String reverseResolve(QuestGraph graph, String content, UUID senderUuid) {
    Matcher matcher = PIPE_PATTERN.matcher(content);
    if (!matcher.find()) return content;

    StringBuilder result = new StringBuilder();
    matcher.reset();

    while (matcher.find()) {
      String displayTitle = matcher.group(2);
      UUID questId = tryParseUuid(matcher.group(1));
      QuestData quest = graph.get(questId);
      String replacement;
      if (quest == null) {
        // No id, a broken one, or a deleted quest: keep the display text.
        replacement = "[[" + displayTitle + "]]";
      } else if (graph.seesFully(senderUuid, questId)) {
        replacement = "[[" + quest.title() + "]]";
      } else {
        replacement = matcher.group(0);
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private UUID tryParseUuid(String s) {
    if (s == null || s.isEmpty()) return null;
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
