package com.disqt.disquests.client.hud;

import com.disqt.disquests.client.DisquestsClient;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;

public class HudPinRenderer {

  private static final int PADDING = 6;
  private static final int MAX_LINES = 12;
  private static final int DEFAULT_MARGIN = 4;
  private static final int GAP = 4;
  private static final int BG_COLOR = 0x80000000;
  private static final int TITLE_COLOR = 0xFFFFFFFF;
  private static final int CONTENT_COLOR = 0xFFBBBBBB;

  /** Sentinel value meaning "use default position" (top-left corner). */
  private static final int DEFAULT_POSITION = -1;

  private static int getMaxWidth() {
    return DisquestsClient.CONFIG.pinnedWidth();
  }

  // --- Cache ---
  private record CachedPin(
      Quest quest, List<String> titleLines, List<OrderedText> contentLines, boolean truncated) {}

  private static List<UUID> lastPinnedIds = List.of();
  private static long lastContentHash = 0;
  private static int lastWidth = 0;
  private static List<CachedPin> cachedPins = List.of();

  public static void toggleVisibility() {
    boolean current = DisquestsClient.CONFIG.pinnedVisible();
    DisquestsClient.CONFIG.pinnedVisible(!current);
  }

  /**
   * Returns the X origin for pin rendering. If configX is -1 (default), pins are placed at the
   * top-left corner. Otherwise, the configured value is used directly.
   */
  private static int resolveX(int screenWidth, int maxWidth, int configX) {
    if (configX == DEFAULT_POSITION) {
      return DEFAULT_MARGIN;
    }
    return configX;
  }

  /**
   * Returns the Y origin for pin rendering. If configY is -1 (default), pins start at the top
   * margin. Otherwise, the configured value is used directly.
   */
  private static int resolveY(int configY) {
    if (configY == DEFAULT_POSITION) {
      return DEFAULT_MARGIN;
    }
    return configY;
  }

  public static void render(DrawContext context) {
    if (!DisquestsClient.CONFIG.pinnedVisible()) return;

    // Don't render pins over Disquests screens (they have their own UI)
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.currentScreen instanceof DisquestsBaseScreen) return;

    List<Quest> quests = HudPinManager.getPinnedQuests();
    if (quests.isEmpty()) {
      lastPinnedIds = List.of();
      lastContentHash = 0;
      lastWidth = 0;
      cachedPins = List.of();
      return;
    }

    if (client.inGameHud.getDebugHud().shouldShowDebugHud()) return;

    // Rebuild cache if pin list or quest content changed
    List<UUID> currentIds = quests.stream().map(Quest::getId).toList();
    long contentHash =
        quests.stream().mapToLong(q -> q.getId().hashCode() + q.getLastModified()).sum();
    int currentWidth = getMaxWidth();
    if (!currentIds.equals(lastPinnedIds)
        || contentHash != lastContentHash
        || currentWidth != lastWidth) {
      rebuildCache(client.textRenderer, quests);
      lastPinnedIds = currentIds;
      lastContentHash = contentHash;
      lastWidth = currentWidth;
    }

    // Resolve position from config
    int screenWidth = client.getWindow().getScaledWidth();
    int configX = DisquestsClient.CONFIG.pinnedX();
    int configY = DisquestsClient.CONFIG.pinnedY();
    int originX = resolveX(screenWidth, currentWidth, configX);
    int originY = resolveY(configY);

    // Render from cache
    TextRenderer tr = client.textRenderer;
    int lineHeight = tr.fontHeight + 1;
    int y = originY;
    for (CachedPin pin : cachedPins) {
      y = renderCachedPin(context, tr, pin, originX, y, lineHeight, currentWidth);
      y += GAP;
    }
  }

  private static void rebuildCache(TextRenderer tr, List<Quest> quests) {
    int maxWidth = getMaxWidth() - PADDING * 2;
    List<CachedPin> pins = new ArrayList<>(quests.size());
    for (Quest quest : quests) {
      List<String> titleLines =
          wrapText(tr, MarkdownRenderer.stripToPlainText(quest.getTitle()), maxWidth);

      // Render content with markdown formatting
      List<RenderedLine> rendered =
          MarkdownRenderer.render(quest.getContent() != null ? quest.getContent() : "");
      List<OrderedText> contentLines = new ArrayList<>();
      for (RenderedLine line : rendered) {
        // Wrap each rendered line to fit the HUD width
        List<OrderedText> wrapped = tr.wrapLines(line.text(), maxWidth);
        contentLines.addAll(wrapped);
      }

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

  private static int renderCachedPin(
      DrawContext context,
      TextRenderer tr,
      CachedPin pin,
      int originX,
      int y,
      int lineHeight,
      int boxWidth) {
    int totalLines = pin.titleLines.size() + pin.contentLines.size() + (pin.truncated ? 1 : 0);
    int boxHeight = PADDING * 2 + totalLines * lineHeight;

    // Background
    context.fill(originX, y, originX + boxWidth, y + boxHeight, BG_COLOR);

    // Title (white)
    int textY = y + PADDING;
    for (String line : pin.titleLines) {
      context.drawText(tr, line, originX + PADDING, textY, TITLE_COLOR, true);
      textY += lineHeight;
    }

    // Content (formatted)
    for (OrderedText line : pin.contentLines) {
      context.drawText(tr, line, originX + PADDING, textY, CONTENT_COLOR, true);
      textY += lineHeight;
    }

    if (pin.truncated) {
      context.drawText(tr, "...", originX + PADDING, textY, CONTENT_COLOR, true);
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
