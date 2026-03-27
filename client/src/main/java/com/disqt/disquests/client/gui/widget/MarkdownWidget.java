package com.disqt.disquests.client.gui.widget;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * An owo-ui component that renders pre-parsed markdown as styled text lines. Word-wraps lines,
 * supports scrolling, checkbox toggling, and link clicking.
 */
public class MarkdownWidget extends BaseUIComponent {

  private static final int PADDING = 5;
  private static final int SCROLLBAR_THICKNESS = 6;
  private static final String QUEST_INACCESSIBLE_MSG = "This quest is private or no longer exists";

  private final List<WrappedEntry> wrappedLines = new ArrayList<>();
  private final List<CheckboxHitbox> checkboxHitboxes = new ArrayList<>();
  private final List<LinkHitbox> linkHitboxes = new ArrayList<>();
  private final List<WikiLinkHitbox> wikiLinkHitboxes = new ArrayList<>();
  private int totalContentHeight = 0;
  private double scrollOffset = 0;
  private CheckboxToggleListener checkboxToggleListener;
  private int lastKnownWidth = -1;
  private List<RenderedLine> currentLines;

  @FunctionalInterface
  public interface CheckboxToggleListener {
    void onCheckboxToggled(int checkboxIndex, boolean nowChecked);
  }

  public void setCheckboxToggleListener(CheckboxToggleListener listener) {
    this.checkboxToggleListener = listener;
  }

  private record CheckboxHitbox(
      int x, int y, int width, int height, int checkboxIndex, boolean checked) {}

  private record LinkHitbox(int x, int y, int width, int height, String url, Text displayText) {}

  private record WikiLinkHitbox(int x, int y, int width, int height, String uuid) {}

  private static boolean hitTest(double mx, double my, int x, int y, int w, int h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  private static String findUrlInText(OrderedText text) {
    String[] result = {null};
    text.accept(
        (index, style, codepoint) -> {
          ClickEvent clickEvent = style.getClickEvent();
          if (clickEvent instanceof ClickEvent.OpenUrl openUrl) {
            result[0] = openUrl.uri().toString();
            return false;
          }
          return true;
        });
    return result[0];
  }

  private static String findWikiLinkUuidInText(OrderedText text) {
    String[] result = {null};
    text.accept(
        (index, style, codepoint) -> {
          ClickEvent clickEvent = style.getClickEvent();
          if (clickEvent instanceof ClickEvent.RunCommand runCmd
              && runCmd.command().startsWith(MarkdownRenderer.WIKI_LINK_COMMAND_PREFIX)) {
            result[0] =
                runCmd.command().substring(MarkdownRenderer.WIKI_LINK_COMMAND_PREFIX.length());
            return false;
          }
          return true;
        });
    return result[0];
  }

  private record WrappedEntry(
      OrderedText text, int indent, float scale, int checkboxIndex, boolean checked) {
    int height(TextRenderer tr) {
      return Math.round(tr.fontHeight * scale);
    }
  }

  public MarkdownWidget(List<RenderedLine> lines) {
    this.currentLines = lines;
  }

  public void setContent(List<RenderedLine> lines) {
    this.currentLines = lines;
    this.scrollOffset = 0;
    this.lastKnownWidth = -1; // force rebuild
  }

  private void rebuildWrappedLines(List<RenderedLine> lines, int componentWidth) {
    TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    wrappedLines.clear();
    checkboxHitboxes.clear();
    totalContentHeight = 0;
    int checkboxIndex = 0;

    int availableWidth = componentWidth - PADDING * 2 - SCROLLBAR_THICKNESS - 2;

    for (RenderedLine line : lines) {
      int indent = line.indent();
      float scale = line.scale();
      int lineAvailWidth = (int) ((availableWidth - indent) / scale);

      if (lineAvailWidth <= 0) lineAvailWidth = availableWidth;

      String lineStr = line.text().getString();
      boolean isCheckbox = lineStr.startsWith("[x] ") || lineStr.startsWith("[ ] ");
      boolean checked = lineStr.startsWith("[x] ");
      int cbIdx = isCheckbox ? checkboxIndex++ : -1;

      if (lineStr.isEmpty()) {
        wrappedLines.add(new WrappedEntry(OrderedText.EMPTY, indent, 1.0f, -1, false));
        totalContentHeight += textRenderer.fontHeight;
        continue;
      }

      List<OrderedText> wrapped = textRenderer.wrapLines(line.text(), lineAvailWidth);
      if (wrapped.isEmpty()) {
        wrappedLines.add(new WrappedEntry(OrderedText.EMPTY, indent, scale, -1, false));
        totalContentHeight += Math.round(textRenderer.fontHeight * scale);
      } else {
        boolean first = true;
        for (OrderedText ot : wrapped) {
          wrappedLines.add(
              new WrappedEntry(ot, indent, scale, first ? cbIdx : -1, first && checked));
          totalContentHeight += Math.round(textRenderer.fontHeight * scale);
          first = false;
        }
      }
    }
  }

  // --- owo-ui component API ---

  @Override
  protected int determineHorizontalContentSize(Sizing sizing) {
    return 200; // default, overridden by parent sizing
  }

  @Override
  protected int determineVerticalContentSize(Sizing sizing) {
    return totalContentHeight + PADDING * 2;
  }

  @Override
  public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
    TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

    // Rebuild wrapped lines if width changed
    if (this.width() != lastKnownWidth && currentLines != null) {
      lastKnownWidth = this.width();
      rebuildWrappedLines(currentLines, this.width());
    }

    int compX = this.x();
    int compY = this.y();
    int compW = this.width();
    int compH = this.height();

    context.enableScissor(compX, compY, compX + compW, compY + compH);

    checkboxHitboxes.clear();
    linkHitboxes.clear();
    wikiLinkHitboxes.clear();
    int contentX = compX + PADDING;
    int contentY = compY + PADDING;
    int drawY = contentY - (int) scrollOffset;

    for (WrappedEntry entry : wrappedLines) {
      int lineHeight = entry.height(textRenderer);

      if (drawY + lineHeight > compY && drawY < compY + compH) {
        int drawX = contentX + entry.indent;

        if (entry.scale != 1.0f) {
          context.getMatrices().pushMatrix();
          context.getMatrices().translate(drawX, drawY);
          context.getMatrices().scale(entry.scale, entry.scale);
          context.drawText(textRenderer, entry.text, 0, 0, Colors.TEXT_PRIMARY, false);
          context.getMatrices().popMatrix();
        } else {
          context.drawText(textRenderer, entry.text, drawX, drawY, Colors.TEXT_PRIMARY, false);
        }

        if (entry.checkboxIndex >= 0) {
          int cbWidth = textRenderer.getWidth("[x] ");
          checkboxHitboxes.add(
              new CheckboxHitbox(
                  drawX, drawY, cbWidth, lineHeight, entry.checkboxIndex, entry.checked));
        }

        String url = findUrlInText(entry.text());
        if (url != null) {
          int textWidth = textRenderer.getWidth(entry.text());
          linkHitboxes.add(
              new LinkHitbox(
                  drawX, drawY, textWidth, textRenderer.fontHeight, url, Text.literal(url)));
        }

        String wikiUuid = findWikiLinkUuidInText(entry.text());
        if (wikiUuid != null) {
          int textWidth = textRenderer.getWidth(entry.text());
          wikiLinkHitboxes.add(
              new WikiLinkHitbox(drawX, drawY, textWidth, textRenderer.fontHeight, wikiUuid));
        }
      }

      drawY += lineHeight;
    }

    context.disableScissor();

    // Link hover tooltip
    for (LinkHitbox lh : linkHitboxes) {
      if (hitTest(mouseX, mouseY, lh.x(), lh.y(), lh.width(), lh.height())) {
        context.drawTooltip(textRenderer, lh.displayText(), mouseX, mouseY);
        break;
      }
    }

    // Scrollbar
    if (needsScrollbar()) {
      renderScrollbar(context, compX, compY, compW, compH);
    }
  }

  private void renderScrollbar(OwoUIGraphics context, int compX, int compY, int compW, int compH) {
    int scrollbarX = compX + compW - SCROLLBAR_THICKNESS - 2;
    int trackHeight = compH - PADDING * 2;
    int trackY = compY + PADDING;

    float visibleRatio = (float) compH / totalContentHeight;
    float thumbHeight = Math.max(10, visibleRatio * trackHeight);
    float maxScroll = getMaxScroll();
    float thumbY =
        maxScroll > 0 ? (float) (scrollOffset / maxScroll) * (trackHeight - thumbHeight) : 0;

    context.fill(
        scrollbarX,
        trackY + (int) thumbY,
        scrollbarX + SCROLLBAR_THICKNESS,
        trackY + (int) (thumbY + thumbHeight),
        Colors.SCROLLBAR_THUMB_INACTIVE);
  }

  private boolean needsScrollbar() {
    return totalContentHeight > this.height() - PADDING * 2;
  }

  private float getMaxScroll() {
    return Math.max(0, totalContentHeight - (this.height() - PADDING * 2));
  }

  private void clampScroll() {
    if (scrollOffset < 0) scrollOffset = 0;
    float max = getMaxScroll();
    if (scrollOffset > max) scrollOffset = max;
  }

  @Override
  public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
    scrollOffset -= amount * 10.0;
    clampScroll();
    return true;
  }

  @Override
  public boolean onMouseDown(Click click, boolean doubled) {
    // Click coordinates are component-relative in owo-ui
    double mx = click.x() + this.x();
    double my = click.y() + this.y();

    if (checkboxToggleListener != null) {
      for (CheckboxHitbox cb : checkboxHitboxes) {
        if (hitTest(mx, my, cb.x, cb.y, cb.width, cb.height)) {
          checkboxToggleListener.onCheckboxToggled(cb.checkboxIndex, !cb.checked);
          return true;
        }
      }
    }
    for (LinkHitbox lh : linkHitboxes) {
      if (hitTest(mx, my, lh.x(), lh.y(), lh.width(), lh.height())) {
        try {
          net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(lh.url()));
        } catch (Exception ignored) {
        }
        return true;
      }
    }
    for (WikiLinkHitbox wh : wikiLinkHitboxes) {
      if (hitTest(mx, my, wh.x(), wh.y(), wh.width(), wh.height())) {
        if (MarkdownRenderer.WIKI_LINK_BROKEN.equals(wh.uuid())) {
          ClientSession.setPendingToast(QUEST_INACCESSIBLE_MSG);
          return true;
        }
        try {
          UUID questId = UUID.fromString(wh.uuid());
          var quest = ClientCache.getQuestById(questId);
          if (quest != null) {
            MinecraftClient.getInstance()
                .setScreen(new QuestScreen(MinecraftClient.getInstance().currentScreen, quest));
          } else {
            ClientSession.setPendingToast(QUEST_INACCESSIBLE_MSG);
          }
        } catch (IllegalArgumentException e) {
          ClientSession.setPendingToast(QUEST_INACCESSIBLE_MSG);
        }
        return true;
      }
    }
    return super.onMouseDown(click, doubled);
  }
}
