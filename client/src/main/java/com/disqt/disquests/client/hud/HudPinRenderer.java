package com.disqt.disquests.client.hud;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class HudPinRenderer {

    private static final int PADDING = 6;
    private static final int MAX_LINES = 12;
    private static final int MARGIN = 4;
    private static final int GAP = 4;
    private static final int BG_COLOR = 0x80000000;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int CONTENT_COLOR = 0xFFBBBBBB;

    private static int getMaxWidth() { return DisquestsConfig.getPinnedWidth(); }

    public static void render(DrawContext context) {
        List<Quest> pinnedQuests = HudPinManager.getPinnedQuests();
        if (pinnedQuests.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return;

        TextRenderer tr = client.textRenderer;
        int y = MARGIN;

        for (Quest quest : pinnedQuests) {
            y = renderSinglePin(context, tr, quest, MARGIN, y);
            y += GAP;
        }
    }

    private static int renderSinglePin(DrawContext context, TextRenderer tr, Quest quest, int x, int y) {
        int lineHeight = tr.fontHeight + 1;

        // Wrap title
        List<String> titleLines = wrapText(tr, quest.getTitle(), getMaxWidth() - PADDING * 2);

        // Strip markdown from content for plain text HUD display
        String plainContent = MarkdownRenderer.stripToPlainText(quest.getContent());
        List<String> contentLines = wrapText(tr, plainContent, getMaxWidth() - PADDING * 2);

        // Truncate content if too many lines
        int maxContentLines = MAX_LINES - titleLines.size();
        boolean truncated = false;
        if (contentLines.size() > maxContentLines) {
            contentLines = new ArrayList<>(contentLines.subList(0, maxContentLines));
            truncated = true;
        }

        int totalLines = titleLines.size() + contentLines.size() + (truncated ? 1 : 0);
        int boxWidth = getMaxWidth();
        int boxHeight = PADDING * 2 + totalLines * lineHeight;

        // Background
        context.fill(x, y, x + boxWidth, y + boxHeight, BG_COLOR);

        // Title (white)
        int textY = y + PADDING;
        for (String line : titleLines) {
            context.drawText(tr, line, x + PADDING, textY, TITLE_COLOR, true);
            textY += lineHeight;
        }

        // Content (gray)
        for (String line : contentLines) {
            context.drawText(tr, line, x + PADDING, textY, CONTENT_COLOR, true);
            textY += lineHeight;
        }

        if (truncated) {
            context.drawText(tr, "...", x + PADDING, textY, CONTENT_COLOR, true);
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
