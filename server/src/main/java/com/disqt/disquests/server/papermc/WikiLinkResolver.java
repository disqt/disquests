package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 *       [[uuid|Quest Name]]} (or {@code [[uuid|Private Quest]]} / {@code [[|Quest Name]]}), and
 *       stored {@code [[uuid|text]]} → {@code [[uuid|Current Title]]} or {@code [[uuid|Private
 *       Quest]]}
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

  /**
   * The stored id form for the forward pass: like {@link #PIPE_PATTERN}, but group 1 also excludes
   * {@code [}, so an extra {@code [[} in front cannot hide the link from it.
   */
  private static final Pattern ID_FORM_PATTERN =
      Pattern.compile("\\[\\[([^\\[|\\]]*)\\|([^\\]]*)\\]\\]");

  /** What a recipient who may not read a linked quest's title sees instead. */
  private static final String PRIVATE_PLACEHOLDER = "Private Quest";

  // -------------------------------------------------------------------------
  // Forward resolution: [[Quest Name]] → [[uuid|title]] or [[|title]] or [[uuid|Private Quest]]
  // -------------------------------------------------------------------------

  /**
   * Scans content for raw {@code [[Quest Name]]} patterns and resolves each for the recipient,
   * deciding with the graph: a quest the recipient cannot see, or sees as locked, becomes {@code
   * [[uuid|Private Quest]]}; a title that matches no quest becomes {@code [[|Quest Name]]}. Titles
   * match ignoring case; when several quests share a title, one the recipient sees fully wins, and
   * among equals the smallest id. At most {@value #MAX_WIKI_LINKS} raw links are processed; excess
   * are left as-is.
   *
   * <p>Links stored in id form {@code [[uuid|text]]} (kept by {@link #reverseResolve} for a sender
   * who could not see the target fully) are resolved too, each once: {@code [[uuid|Current Title]]}
   * when the recipient sees the quest fully, else {@code [[uuid|Private Quest]]}, also when no
   * quest has that id. The stored text is never sent.
   */
  public String resolveForRecipient(QuestGraph graph, String content, UUID recipientUuid) {
    // Every link is rewritten once, from the original content: the id-form links first, then
    // the raw links outside them, so the output of one pass is never fed to the other.
    List<Replacement> replacements = new ArrayList<>();

    Matcher idLinks = ID_FORM_PATTERN.matcher(content);
    while (idLinks.find()) {
      UUID questId = tryParseUuid(idLinks.group(1));
      if (questId == null) continue;
      replacements.add(
          new Replacement(
              idLinks.start(), idLinks.end(), idFormFor(graph, questId, recipientUuid)));
    }

    Matcher raw = RAW_PATTERN.matcher(content);
    Set<String> lowerTitles = new HashSet<>();
    int found = 0;
    while (raw.find() && found < MAX_WIKI_LINKS) {
      if (insideAny(replacements, raw.start(), raw.end())) continue;
      lowerTitles.add(raw.group(1).toLowerCase(Locale.ROOT));
      found++;
    }
    if (!lowerTitles.isEmpty()) {
      Map<String, QuestData> byTitle = byTitle(graph, lowerTitles, recipientUuid);
      raw.reset();
      int processedCount = 0;
      List<Replacement> rawReplacements = new ArrayList<>();
      while (raw.find() && processedCount < MAX_WIKI_LINKS) {
        if (insideAny(replacements, raw.start(), raw.end())) continue;
        processedCount++; // links past the limit are left raw

        String displayText = raw.group(1);
        QuestData quest = byTitle.get(displayText.toLowerCase(Locale.ROOT));
        String replacement;
        if (quest == null) {
          // Quest not found — broken link
          replacement = "[[|" + displayText + "]]";
        } else if (!graph.seesFully(recipientUuid, quest.id())) {
          replacement = privateLink(quest.id());
        } else {
          replacement = "[[" + quest.id() + "|" + displayText + "]]";
        }
        rawReplacements.add(new Replacement(raw.start(), raw.end(), replacement));
      }
      replacements.addAll(rawReplacements);
    }
    if (replacements.isEmpty()) return content;

    replacements.sort(Comparator.comparingInt(Replacement::start));
    StringBuilder result = new StringBuilder();
    int at = 0;
    for (Replacement r : replacements) {
      result.append(content, at, r.start()).append(r.text());
      at = r.end();
    }
    result.append(content, at, content.length());
    return result.toString();
  }

  /**
   * A stored {@code [[uuid|text]]} as the recipient gets it: the quest's current title when they
   * see it fully, else the placeholder. The stored text is never sent on.
   */
  private static String idFormFor(QuestGraph graph, UUID questId, UUID recipient) {
    QuestData quest = graph.get(questId);
    if (quest == null || !graph.seesFully(recipient, questId)) return privateLink(questId);
    return "[[" + questId + "|" + quest.title() + "]]";
  }

  private static String privateLink(UUID questId) {
    return "[[" + questId + "|" + PRIVATE_PLACEHOLDER + "]]";
  }

  private static boolean insideAny(List<Replacement> spans, int start, int end) {
    for (Replacement span : spans) {
      if (start < span.end() && span.start() < end) return true;
    }
    return false;
  }

  private record Replacement(int start, int end, String text) {}

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
