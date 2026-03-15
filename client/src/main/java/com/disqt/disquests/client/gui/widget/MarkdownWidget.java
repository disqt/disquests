package com.disqt.disquests.client.gui.widget;

import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.markdown.RenderedLine;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.OrderedText;

import java.util.ArrayList;
import java.util.List;

/**
 * A scrollable widget that renders pre-parsed markdown as styled text lines.
 * Each RenderedLine is word-wrapped to fit the available width, and the widget
 * supports vertical scrolling.
 */
public class MarkdownWidget implements Drawable, Element, Selectable {

    private final TextRenderer textRenderer;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private static final int PADDING = 5;
    private static final int SCROLLBAR_THICKNESS = 6;

    private final List<WrappedEntry> wrappedLines = new ArrayList<>();
    private int totalContentHeight = 0;
    private double scrollOffset = 0;
    private boolean focused = false;

    /**
     * A single visual line after word-wrapping, with its indent and scale.
     */
    private record WrappedEntry(OrderedText text, int indent, float scale) {
        int height(TextRenderer tr) {
            return Math.round(tr.fontHeight * scale);
        }
    }

    public MarkdownWidget(TextRenderer textRenderer, int x, int y, int width, int height, List<RenderedLine> lines) {
        this.textRenderer = textRenderer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        rebuildWrappedLines(lines);
    }

    /**
     * Update the content displayed by this widget.
     */
    public void setContent(List<RenderedLine> lines) {
        this.scrollOffset = 0;
        rebuildWrappedLines(lines);
    }

    private void rebuildWrappedLines(List<RenderedLine> lines) {
        wrappedLines.clear();
        totalContentHeight = 0;

        int availableWidth = width - PADDING * 2 - SCROLLBAR_THICKNESS - 2;

        for (RenderedLine line : lines) {
            int indent = line.indent();
            float scale = line.scale();
            int lineAvailWidth = (int) ((availableWidth - indent) / scale);

            if (lineAvailWidth <= 0) lineAvailWidth = availableWidth;

            // Empty lines produce one blank entry
            if (line.text().getString().isEmpty()) {
                wrappedLines.add(new WrappedEntry(OrderedText.EMPTY, indent, 1.0f));
                totalContentHeight += textRenderer.fontHeight;
                continue;
            }

            List<OrderedText> wrapped = textRenderer.wrapLines(line.text(), lineAvailWidth);
            if (wrapped.isEmpty()) {
                wrappedLines.add(new WrappedEntry(OrderedText.EMPTY, indent, scale));
                totalContentHeight += Math.round(textRenderer.fontHeight * scale);
            } else {
                for (OrderedText ot : wrapped) {
                    wrappedLines.add(new WrappedEntry(ot, indent, scale));
                    totalContentHeight += Math.round(textRenderer.fontHeight * scale);
                }
            }
        }
    }

    // --- Rendering ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.enableScissor(x, y, x + width, y + height);

        int contentX = x + PADDING;
        int contentY = y + PADDING;
        int drawY = contentY - (int) scrollOffset;

        for (WrappedEntry entry : wrappedLines) {
            int lineHeight = entry.height(textRenderer);

            // Culling: skip lines that are entirely above or below visible area
            if (drawY + lineHeight > y && drawY < y + height) {
                int drawX = contentX + entry.indent;

                if (entry.scale != 1.0f) {
                    // Scale rendering for headings
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(drawX, drawY);
                    context.getMatrices().scale(entry.scale, entry.scale);
                    context.drawText(textRenderer, entry.text, 0, 0, Colors.TEXT_PRIMARY, false);
                    context.getMatrices().popMatrix();
                } else {
                    context.drawText(textRenderer, entry.text, drawX, drawY, Colors.TEXT_PRIMARY, false);
                }
            }

            drawY += lineHeight;
        }

        context.disableScissor();

        // Draw scrollbar if needed
        if (needsScrollbar()) {
            renderScrollbar(context);
        }
    }

    private void renderScrollbar(DrawContext context) {
        int scrollbarX = x + width - SCROLLBAR_THICKNESS - 2;
        int trackHeight = height - PADDING * 2;
        int trackY = y + PADDING;

        float visibleRatio = (float) height / totalContentHeight;
        float thumbHeight = Math.max(10, visibleRatio * trackHeight);
        float maxScroll = getMaxScroll();
        float thumbY = maxScroll > 0 ? (float) (scrollOffset / maxScroll) * (trackHeight - thumbHeight) : 0;

        context.fill(
                scrollbarX, trackY + (int) thumbY,
                scrollbarX + SCROLLBAR_THICKNESS, trackY + (int) (thumbY + thumbHeight),
                Colors.SCROLLBAR_THUMB_INACTIVE
        );
    }

    // --- Scrolling ---

    private boolean needsScrollbar() {
        return totalContentHeight > height - PADDING * 2;
    }

    private float getMaxScroll() {
        return Math.max(0, totalContentHeight - (height - PADDING * 2));
    }

    private void clampScroll() {
        if (scrollOffset < 0) scrollOffset = 0;
        float max = getMaxScroll();
        if (scrollOffset > max) scrollOffset = max;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scrollOffset -= verticalAmount * 10.0;
        clampScroll();
        return true;
    }

    // --- Element ---

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    // --- Selectable ---

    @Override
    public SelectionType getType() {
        return focused ? SelectionType.FOCUSED : SelectionType.NONE;
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
        // Not needed for this widget
    }
}
