package com.disqt.disquests.client.hud;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HudPinRenderer {

    private static final int PADDING = 6;
    private static final int MAX_LINES = 12;
    private static final int MARGIN = 4;
    private static final int GAP = 4;
    private static final int BG_COLOR = 0x80000000;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int CONTENT_COLOR = 0xFFBBBBBB;

    private static int getMaxWidth() { return DisquestsConfig.getPinnedWidth(); }

    // --- Cache ---
    private record CachedPin(Quest quest, List<String> titleLines, List<String> contentLines, boolean truncated) {}

    private static List<UUID> lastPinnedIds = List.of();
    private static List<CachedPin> cachedPins = List.of();

    public static void render(DrawContext context) {
        List<Quest> quests = HudPinManager.getPinnedQuests();
        if (quests.isEmpty()) {
            lastPinnedIds = List.of();
            cachedPins = List.of();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return;

        // Rebuild cache if pin list changed
        List<UUID> currentIds = quests.stream().map(Quest::getId).toList();
        if (!currentIds.equals(lastPinnedIds)) {
            rebuildCache(client.textRenderer, quests);
            lastPinnedIds = currentIds;
        }

        // Render from cache
        TextRenderer tr = client.textRenderer;
        int lineHeight = tr.fontHeight + 1;
        int y = MARGIN;
        for (CachedPin pin : cachedPins) {
            y = renderCachedPin(context, tr, pin, y, lineHeight);
            y += GAP;
        }
    }

    private static void rebuildCache(TextRenderer tr, List<Quest> quests) {
        int maxWidth = getMaxWidth() - PADDING * 2;
        List<CachedPin> pins = new ArrayList<>(quests.size());
        for (Quest quest : quests) {
            List<String> titleLines = wrapText(tr, quest.getTitle(), maxWidth);

            String plainContent = MarkdownRenderer.stripToPlainText(quest.getContent());
            List<String> contentLines = wrapText(tr, plainContent, maxWidth);

            int maxContentLines = MAX_LINES - titleLines.size();
            boolean truncated = false;
            if (contentLines.size() > maxContentLines) {
                contentLines = new ArrayList<>(contentLines.subList(0, maxContentLines));
                truncated = true;
            }

            pins.add(new CachedPin(quest, titleLines, contentLines, truncated));
        }
        cachedPins = pins;
    }

    private static int renderCachedPin(DrawContext context, TextRenderer tr, CachedPin pin, int y, int lineHeight) {
        int totalLines = pin.titleLines.size() + pin.contentLines.size() + (pin.truncated ? 1 : 0);
        int boxWidth = getMaxWidth();
        int boxHeight = PADDING * 2 + totalLines * lineHeight;

        // Background
        context.fill(MARGIN, y, MARGIN + boxWidth, y + boxHeight, BG_COLOR);

        // Title (white)
        int textY = y + PADDING;
        for (String line : pin.titleLines) {
            context.drawText(tr, line, MARGIN + PADDING, textY, TITLE_COLOR, true);
            textY += lineHeight;
        }

        // Content (gray)
        for (String line : pin.contentLines) {
            context.drawText(tr, line, MARGIN + PADDING, textY, CONTENT_COLOR, true);
            textY += lineHeight;
        }

        if (pin.truncated) {
            context.drawText(tr, "...", MARGIN + PADDING, textY, CONTENT_COLOR, true);
        }

        return y + boxHeight;
    }

    private static List<String> wrapText(TextRenderer tr, String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                result.add("");
                continue;
            }
            String remaining = paragraph;
            while (!remaining.isEmpty()) {
                String trimmed = tr.trimToWidth(remaining, maxWidth);
                if (trimmed.isEmpty()) {
                    trimmed = remaining.substring(0, 1);
                }
                result.add(trimmed);
                remaining = remaining.substring(trimmed.length());
            }
        }
        return result;
    }
}
