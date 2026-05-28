package com.disqt.disquests.client.gui.widget;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.HoverPreviewRenderer;
import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import com.disqt.disquests.client.gui.screen.QuestScreen;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

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
  private boolean previewVisible = false;

  @FunctionalInterface
  public interface CheckboxToggleListener {
    void onCheckboxToggled(int checkboxIndex, boolean nowChecked);
  }

  public void setCheckboxToggleListener(CheckboxToggleListener listener) {
    this.checkboxToggleListener = listener;
  }

  /** Returns true if a hover preview is currently being drawn. For E2E testing. */
  public boolean isPreviewVisible() {
    return previewVisible;
  }

  private record CheckboxHitbox(
      int x, int y, int width, int height, int checkboxIndex, boolean checked) {}

  private record LinkHitbox(int x, int y, int width, int height, String url, Component displayText) {}

  private record WikiLinkHitbox(int x, int y, int width, int height, String uuid) {}

  private static boolean hitTest(double mx, double my, int x, int y, int w, int h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  /**
   * Walks an FormattedCharSequence character-by-character, computing pixel offsets for each styled segment
   * (links and wiki-links), and creates tight hitboxes only around the actual link text.
   */
  private void collectStyledHitboxes(
      FormattedCharSequence text, int lineX, int lineY, Font textRenderer) {
    // Track current segment state
    final int[] pixelX = {0}; // running pixel offset
    final String[] currentUrl = {null};
    final String[] currentWikiUuid = {null};
    final int[] segStartX = {0}; // pixel X where current segment started

    text.accept(
        (index, style, codepoint) -> {
          int charWidth = textRenderer.width(String.valueOf(Character.toChars(codepoint)));

          // Determine what this character belongs to
          String url = null;
          String wikiUuid = null;
          ClickEvent clickEvent = style.getClickEvent();
          if (clickEvent instanceof ClickEvent.OpenUrl openUrl) {
            url = openUrl.uri().toString();
          } else if (clickEvent instanceof ClickEvent.RunCommand runCmd
              && runCmd.command().startsWith(MarkdownRenderer.WIKI_LINK_COMMAND_PREFIX)) {
            wikiUuid =
                runCmd.command().substring(MarkdownRenderer.WIKI_LINK_COMMAND_PREFIX.length());
          }

          // If we were in a URL segment and this char is different, close it
          if (currentUrl[0] != null && !currentUrl[0].equals(url)) {
            int w = pixelX[0] - segStartX[0];
            if (w > 0) {
              linkHitboxes.add(
                  new LinkHitbox(
                      lineX + segStartX[0],
                      lineY,
                      w,
                      textRenderer.lineHeight,
                      currentUrl[0],
                      Component.literal(currentUrl[0])));
            }
            currentUrl[0] = null;
          }

          // If we were in a wiki-link segment and this char is different, close it
          if (currentWikiUuid[0] != null && !currentWikiUuid[0].equals(wikiUuid)) {
            int w = pixelX[0] - segStartX[0];
            if (w > 0) {
              wikiLinkHitboxes.add(
                  new WikiLinkHitbox(
                      lineX + segStartX[0], lineY, w, textRenderer.lineHeight, currentWikiUuid[0]));
            }
            currentWikiUuid[0] = null;
          }

          // Start new segments if needed
          if (url != null && currentUrl[0] == null) {
            currentUrl[0] = url;
            segStartX[0] = pixelX[0];
          }
          if (wikiUuid != null && currentWikiUuid[0] == null) {
            currentWikiUuid[0] = wikiUuid;
            segStartX[0] = pixelX[0];
          }

          pixelX[0] += charWidth;
          return true;
        });

    // Close any remaining open segments at end of line
    if (currentUrl[0] != null) {
      int w = pixelX[0] - segStartX[0];
      if (w > 0) {
        linkHitboxes.add(
            new LinkHitbox(
                lineX + segStartX[0],
                lineY,
                w,
                textRenderer.lineHeight,
                currentUrl[0],
                Component.literal(currentUrl[0])));
      }
    }
    if (currentWikiUuid[0] != null) {
      int w = pixelX[0] - segStartX[0];
      if (w > 0) {
        wikiLinkHitboxes.add(
            new WikiLinkHitbox(
                lineX + segStartX[0], lineY, w, textRenderer.lineHeight, currentWikiUuid[0]));
      }
    }
  }

  private record WrappedEntry(
      FormattedCharSequence text, int indent, float scale, int checkboxIndex, boolean checked) {
    int height(Font tr) {
      return Math.round(tr.lineHeight * scale);
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
    Font textRenderer = Minecraft.getInstance().font;
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
        wrappedLines.add(new WrappedEntry(FormattedCharSequence.EMPTY, indent, 1.0f, -1, false));
        totalContentHeight += textRenderer.lineHeight;
        continue;
      }

      List<FormattedCharSequence> wrapped = textRenderer.split(line.text(), lineAvailWidth);
      if (wrapped.isEmpty()) {
        wrappedLines.add(new WrappedEntry(FormattedCharSequence.EMPTY, indent, scale, -1, false));
        totalContentHeight += Math.round(textRenderer.lineHeight * scale);
      } else {
        boolean first = true;
        for (FormattedCharSequence ot : wrapped) {
          wrappedLines.add(
              new WrappedEntry(ot, indent, scale, first ? cbIdx : -1, first && checked));
          totalContentHeight += Math.round(textRenderer.lineHeight * scale);
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
    Font textRenderer = Minecraft.getInstance().font;

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
          context.pose().pushMatrix();
          context.pose().translate(drawX, drawY);
          context.pose().scale(entry.scale, entry.scale);
          context.text(textRenderer, entry.text, 0, 0, Colors.TEXT_PRIMARY, false);
          context.pose().popMatrix();
        } else {
          context.text(textRenderer, entry.text, drawX, drawY, Colors.TEXT_PRIMARY, false);
        }

        if (entry.checkboxIndex >= 0) {
          int cbWidth = textRenderer.width("[x] ");
          checkboxHitboxes.add(
              new CheckboxHitbox(
                  drawX, drawY, cbWidth, lineHeight, entry.checkboxIndex, entry.checked));
        }

        collectStyledHitboxes(entry.text(), drawX, drawY, textRenderer);
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

    // Wiki-link hover preview
    previewVisible = false;
    for (WikiLinkHitbox wh : wikiLinkHitboxes) {
      if (hitTest(mouseX, mouseY, wh.x(), wh.y(), wh.width(), wh.height())) {
        if (!MarkdownRenderer.WIKI_LINK_BROKEN.equals(wh.uuid())) {
          try {
            UUID questId = UUID.fromString(wh.uuid());
            Quest quest = ClientCache.getQuestById(questId);
            if (quest != null) {
              Minecraft mc = Minecraft.getInstance();
              HoverPreviewRenderer.draw(
                  (net.minecraft.client.gui.GuiGraphicsExtractor) context,
                  quest,
                  mouseX,
                  mouseY,
                  mc.getWindow().getGuiScaledWidth(),
                  mc.getWindow().getGuiScaledHeight());
              previewVisible = true;
            }
          } catch (IllegalArgumentException ignored) {
          }
        }
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
  public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
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
        com.disqt.disquests.client.UrlOpener.open(lh.url());
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
            Screen currentScreen = Minecraft.getInstance().screen;
            if (currentScreen instanceof DisquestsBaseScreen baseScreen) {
              baseScreen.navigateToScreen(new QuestScreen(baseScreen.getParentScreen(), quest));
            } else {
              Minecraft.getInstance().setScreen(new QuestScreen(currentScreen, quest));
            }
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
