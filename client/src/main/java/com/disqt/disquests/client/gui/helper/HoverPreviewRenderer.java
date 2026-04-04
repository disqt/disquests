package com.disqt.disquests.client.gui.helper;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class HoverPreviewRenderer {

  private static final int MIN_WIDTH = 120;
  private static final int MAX_WIDTH = 250;
  private static final int MAX_LINES = 3;
  private static final int PADDING = 4;
  private static final int OFFSET_X = 12;
  private static final int OFFSET_Y = 16;
  private static final int BG_COLOR = 0xEE1a1a2e;
  private static final int BORDER_COLOR = 0xAA555555;
  private static final int TITLE_COLOR = 0xFFFFFFFF;
  private static final int ELLIPSIS_COLOR = 0xFF888888;

  private HoverPreviewRenderer() {}

  /**
   * Draws a hover preview card for the given quest near the mouse cursor.
   *
   * @param context the draw context
   * @param quest the quest to preview
   * @param mouseX mouse x in screen coordinates
   * @param mouseY mouse y in screen coordinates
   * @param screenWidth total screen width (for edge clamping)
   * @param screenHeight total screen height (for edge clamping)
   */
  public static void draw(
      DrawContext context, Quest quest, int mouseX, int mouseY, int screenWidth, int screenHeight) {
    TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    int lineHeight = textRenderer.fontHeight;

    // Render content lines (up to MAX_LINES)
    String content = quest.getContent();
    List<RenderedLine> renderedLines = List.of();
    boolean truncated = false;
    if (content != null && !content.isEmpty()) {
      renderedLines = MarkdownRenderer.render(content);
      if (renderedLines.size() > MAX_LINES) {
        renderedLines = renderedLines.subList(0, MAX_LINES);
        truncated = true;
      }
    }

    // Compute tag chips
    List<String> tags = quest.getTags();

    // Compute width to fit content (clamped between MIN_WIDTH and MAX_WIDTH)
    String title = quest.getTitle() != null ? quest.getTitle() : "Untitled";
    int contentArea = PADDING * 2;
    int neededWidth = textRenderer.getWidth(title) + contentArea;
    for (RenderedLine line : renderedLines) {
      neededWidth =
          Math.max(neededWidth, textRenderer.getWidth(line.text().getString()) + contentArea);
    }
    int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, neededWidth));

    // Compute card height: padding + title + gap + content lines + gap + tags + padding
    int contentLinesHeight = renderedLines.size() * lineHeight;
    int tagsHeight = tags.isEmpty() ? 0 : lineHeight + 2; // one row of tag text
    int totalHeight = PADDING + lineHeight; // title
    if (!renderedLines.isEmpty()) {
      totalHeight += 2 + contentLinesHeight; // gap + content
      if (truncated) totalHeight += lineHeight; // ellipsis line
    }
    if (!tags.isEmpty()) {
      totalHeight += 2 + tagsHeight; // gap + tags
    }
    totalHeight += PADDING;

    // Position with offset, clamp to screen edges
    int x = mouseX + OFFSET_X;
    int y = mouseY + OFFSET_Y;
    if (x + width > screenWidth) x = mouseX - width - 4;
    if (y + totalHeight > screenHeight) y = mouseY - totalHeight - 4;
    if (x < 0) x = 0;
    if (y < 0) y = 0;

    // Draw background + border
    context.fill(x - 1, y - 1, x + width + 1, y + totalHeight + 1, BORDER_COLOR);
    context.fill(x, y, x + width, y + totalHeight, BG_COLOR);

    int drawY = y + PADDING;

    // Title (bold)
    String rawTitle = textRenderer.trimToWidth(title, width - PADDING * 2);
    if (rawTitle.length() < title.length()) rawTitle = rawTitle + "...";
    Text titleText =
        Text.literal(rawTitle).setStyle(Style.EMPTY.withBold(true).withColor(TITLE_COLOR));
    context.drawText(textRenderer, titleText, x + PADDING, drawY, TITLE_COLOR, false);
    drawY += lineHeight + 2;

    // Content lines. Scale and indent from RenderedLine are intentionally ignored -- this is a
    // compact tooltip preview, not a full markdown renderer. Headings and bullet indentation are
    // deliberately flattened.
    for (RenderedLine line : renderedLines) {
      String rawLine = textRenderer.trimToWidth(line.text().getString(), width - PADDING * 2);
      context.drawText(
          textRenderer, Text.literal(rawLine), x + PADDING, drawY, Colors.TEXT_MUTED, false);
      drawY += lineHeight;
    }
    if (truncated) {
      context.drawText(
          textRenderer, Text.literal("..."), x + PADDING, drawY, ELLIPSIS_COLOR, false);
      drawY += lineHeight;
    }

    // Tags
    if (!tags.isEmpty()) {
      drawY += 2;
      int tagX = x + PADDING;
      for (String tag : tags) {
        int tagColor = TagColors.getForeground(tag);
        int tagBg = TagColors.getBackground(tag);
        int tagWidth = textRenderer.getWidth(tag) + 6; // 3px padding each side
        if (tagX + tagWidth > x + width - PADDING) break; // don't overflow
        context.fill(tagX, drawY, tagX + tagWidth, drawY + lineHeight, tagBg);
        context.drawText(textRenderer, Text.literal(tag), tagX + 3, drawY + 1, tagColor, false);
        tagX += tagWidth + 2;
      }
    }
  }
}
