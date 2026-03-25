package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves wiki-style links ([[Quest Name]]) in quest content on a per-recipient basis.
 *
 * <ul>
 *   <li>Forward resolution ({@link #resolveForRecipient}): raw {@code [[Quest Name]]} →
 *       {@code [[uuid|Quest Name]]} (or {@code [[uuid|Private Quest]]} / {@code [[|Quest Name]]})
 *   <li>Reverse resolution ({@link #reverseResolve}): {@code [[uuid|title]]} → {@code [[Current Title]]}
 * </ul>
 *
 * Access rules:
 * <ul>
 *   <li>Owner always has access.
 *   <li>OPEN and CLOSED quests are visible to everyone.
 *   <li>PRIVATE quests are visible only to owner and contributors.
 * </ul>
 */
public class WikiLinkResolver {

    private static final int MAX_WIKI_LINKS = 16;

    /** Matches raw {@code [[text]]} where text contains no {@code |} or {@code ]}. */
    private static final Pattern RAW_PATTERN = Pattern.compile("\\[\\[([^\\]|]+)\\]\\]");

    /**
     * Matches the {@code [[uuid|title]]} form specifically.
     * Group 1 = UUID (may be empty string for broken links), group 2 = display title.
     */
    private static final Pattern PIPE_PATTERN = Pattern.compile("\\[\\[([^|\\]]*?)\\|([^\\]]*)\\]\\]");

    private final DataManager dataManager;

    public WikiLinkResolver(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    // -------------------------------------------------------------------------
    // Forward resolution: [[Quest Name]] → [[uuid|title]] or [[|title]] or [[uuid|Private Quest]]
    // -------------------------------------------------------------------------

    /**
     * Scans content for raw {@code [[Quest Name]]} patterns and resolves each per-recipient.
     * At most {@value #MAX_WIKI_LINKS} links are processed; excess are left as-is.
     */
    public String resolveForRecipient(String content, UUID recipientUuid) {
        Matcher matcher = RAW_PATTERN.matcher(content);
        List<String> rawTitles = new ArrayList<>();
        while (matcher.find()) {
            if (rawTitles.size() < MAX_WIKI_LINKS) {
                rawTitles.add(matcher.group(1));
            }
        }
        if (rawTitles.isEmpty()) return content;

        // Batch-load all quests by title (case-insensitive)
        List<String> lowerTitles = rawTitles.stream().map(String::toLowerCase).distinct().toList();
        Map<String, QuestData> byTitle = dataManager.findQuestsByTitlesIgnoreCase(lowerTitles);

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

            QuestData quest = byTitle.get(displayText.toLowerCase());
            String replacement;
            if (quest == null) {
                // Quest not found — broken link
                replacement = "[[|" + displayText + "]]";
            } else {
                // For PRIVATE quests, we need contributors to check access;
                // findQuestsByTitlesIgnoreCase doesn't load them, so fetch fully.
                QuestData questWithContribs = (quest.visibility() == Visibility.PRIVATE)
                        ? dataManager.getQuest(quest.id())
                        : quest;
                if (questWithContribs == null || !hasAccess(questWithContribs, recipientUuid)) {
                    replacement = "[[" + quest.id() + "|Private Quest]]";
                } else {
                    replacement = "[[" + quest.id() + "|" + displayText + "]]";
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // -------------------------------------------------------------------------
    // Reverse resolution: [[uuid|title]] → [[Current Title]]
    // -------------------------------------------------------------------------

    /**
     * Scans content for {@code [[uuid|title]]} patterns and converts back to canonical
     * {@code [[Current Title]]} form when the sender has access to the linked quest.
     */
    public String reverseResolve(String content, UUID senderUuid) {
        Matcher matcher = PIPE_PATTERN.matcher(content);
        if (!matcher.find()) return content;

        StringBuilder result = new StringBuilder();
        matcher.reset();

        while (matcher.find()) {
            String uuidStr = matcher.group(1);
            String displayTitle = matcher.group(2);

            UUID questId = tryParseUuid(uuidStr);
            String replacement;

            if (questId == null) {
                // Empty or invalid UUID — deleted/broken link → preserve display text
                replacement = "[[" + displayTitle + "]]";
            } else {
                QuestData quest = dataManager.getQuest(questId);
                if (quest == null) {
                    // Quest deleted → preserve display text
                    replacement = "[[" + displayTitle + "]]";
                } else if (hasAccess(quest, senderUuid)) {
                    // Use current canonical title
                    replacement = "[[" + quest.title() + "]]";
                } else {
                    // No access → leave as-is
                    replacement = matcher.group(0);
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if the given player has access to the quest.
     * Owner always has access. OPEN/CLOSED quests are visible to everyone.
     * PRIVATE quests are visible only to owner and contributors.
     */
    private boolean hasAccess(QuestData quest, UUID playerUuid) {
        if (quest.ownerUuid().equals(playerUuid)) return true;
        if (quest.visibility() == Visibility.OPEN || quest.visibility() == Visibility.CLOSED) return true;
        // PRIVATE: check contributors
        for (ContributorData contributor : quest.contributors()) {
            if (contributor.uuid().equals(playerUuid)) return true;
        }
        return false;
    }

    private UUID tryParseUuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
