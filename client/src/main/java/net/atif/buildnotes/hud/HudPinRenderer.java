package net.atif.buildnotes.hud;

import net.atif.buildnotes.data.Note;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class HudPinRenderer {

    private static final int PADDING = 6;
    private static final int MAX_WIDTH = 200;
    private static final int MAX_LINES = 12;
    private static final int MARGIN = 4;
    private static final int BG_COLOR = 0x80000000;  // semi-transparent black
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int CONTENT_COLOR = 0xFFBBBBBB;

    public static void render(DrawContext context) {
        Note note = HudPinManager.getPinnedNote();
        if (note == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return; // don't render over F3

        TextRenderer tr = client.textRenderer;
        int lineHeight = tr.fontHeight + 1;

        // Wrap title and content
        List<String> titleLines = wrapText(tr, note.getTitle(), MAX_WIDTH - PADDING * 2);
        List<String> contentLines = wrapText(tr, note.getContent(), MAX_WIDTH - PADDING * 2);

        // Truncate content if too many lines
        int maxContentLines = MAX_LINES - titleLines.size();
        boolean truncated = false;
        if (contentLines.size() > maxContentLines) {
            contentLines = new ArrayList<>(contentLines.subList(0, maxContentLines));
            truncated = true;
        }

        int totalLines = titleLines.size() + contentLines.size() + (truncated ? 1 : 0);
        int boxWidth = MAX_WIDTH;
        int boxHeight = PADDING * 2 + totalLines * lineHeight;

        // Position: top-left with margin
        int x = MARGIN;
        int y = MARGIN;

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
                    // Single character wider than maxWidth — force at least 1 char
                    trimmed = remaining.substring(0, 1);
                }
                result.add(trimmed);
                remaining = remaining.substring(trimmed.length());
            }
        }
        return result;
    }
}
