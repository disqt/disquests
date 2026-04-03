package com.disqt.disquests.client.gui.widget;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.HoverPreviewRenderer;
import com.disqt.disquests.client.gui.widget.undoredo.TextAction;
import com.disqt.disquests.client.gui.widget.undoredo.UndoManager;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MultiLineTextFieldWidget implements Drawable, Element, Selectable {

  private final TextRenderer textRenderer;
  public final int x;
  public final int y;
  public final int width;
  public final int height;

  protected final int maxLines;

  protected final boolean scrollingEnabled;
  protected boolean allowVerticalScroll;
  protected boolean allowHorizontalScroll;

  protected List<String> lines = Lists.newArrayList("");
  protected int cursorX = 0; // column
  protected int cursorY = 0; // line index
  protected boolean focused = false;
  protected double scrollY = 0;
  protected double scrollX = 0;

  protected static final int SCROLLBAR_THICKNESS = 6;
  protected boolean isDraggingVScrollbar = false;
  protected double vScrollbarDragStartY = 0;
  protected double vScrollbarDragStartScrollY = 0;

  protected boolean isDraggingHScrollbar = false;
  protected double hScrollbarDragStartX = 0;
  protected double hScrollbarDragStartScrollX = 0;

  // Selection as absolute indices across the joined text (includes '\n' between lines)
  protected int selectionStart = 0;
  protected int selectionEnd = 0;
  protected int selectionAnchor = 0; // anchor for mouse dragging (fixed until mouse released)
  protected long lastClickTime = 0;
  protected int lastClickIndex = -1;
  protected int clickCount = 0;
  private static final long DOUBLE_CLICK_INTERVAL_MS = 300;

  private boolean shiftDown = false;
  private boolean ctrlDown = false;

  // dragging selection by mouse
  protected boolean isDraggingText = false;

  private final UndoManager undoManager = new UndoManager(this);
  protected String placeholderText;
  // caret blink
  protected boolean caretVisible = true;
  protected long lastBlinkTime = System.currentTimeMillis();
  protected static final long BLINK_INTERVAL_MS = 500;
  private boolean caretEnabled = true;

  private boolean internalScissoringEnabled = true;

  private Consumer<String> changedListener = s -> {};

  // Word-wrap support
  protected final boolean wordWrap;
  protected List<String> displayLines = new ArrayList<>();
  protected List<Integer> displayToLogical = new ArrayList<>();
  protected List<Integer> displayToOffset = new ArrayList<>();

  // Wiki-link syntax highlighting
  private static final Pattern WIKI_LINK_EDIT_PATTERN = Pattern.compile("\\[\\[[^\\]]*\\]\\]");
  private static final int WIKI_LINK_COLOR = 0xFFe8a86d; // amber
  private List<List<int[]>> displayLineWikiLinks = new ArrayList<>();
  private boolean wikiLinkSegmentsDirty = true;
  private boolean previewVisible = false;

  public MultiLineTextFieldWidget(
      TextRenderer textRenderer,
      int x,
      int y,
      int width,
      int height,
      String initialText,
      String placeholder,
      int maxLines,
      boolean scrollingEnabled) {
    this(
        textRenderer,
        x,
        y,
        width,
        height,
        initialText,
        placeholder,
        maxLines,
        scrollingEnabled,
        false);
  }

  public MultiLineTextFieldWidget(
      TextRenderer textRenderer,
      int x,
      int y,
      int width,
      int height,
      String initialText,
      String placeholder,
      int maxLines,
      boolean scrollingEnabled,
      boolean wordWrap) {
    this.textRenderer = textRenderer;
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.maxLines = maxLines;
    this.scrollingEnabled = scrollingEnabled;
    this.placeholderText = placeholder;
    this.wordWrap = wordWrap;

    boolean defaultVerticalScroll = this.scrollingEnabled;
    if (maxLines == 1) {
      defaultVerticalScroll = false;
    }
    this.allowVerticalScroll = defaultVerticalScroll;
    this.allowHorizontalScroll = !wordWrap;

    setText(initialText);
  }

  public void setCaretEnabled(boolean enabled) {
    this.caretEnabled = enabled;
    this.caretVisible = enabled;
  }

  public void setChangedListener(Consumer<String> listener) {
    this.changedListener = listener;
  }

  private void onChanged() {
    if (this.changedListener != null) {
      this.changedListener.accept(this.getText());
    }
    rebuildDisplayLines();
  }

  protected int getEffectiveLineCount() {
    return wordWrap ? displayLines.size() : lines.size();
  }

  protected void rebuildDisplayLines() {
    displayLines.clear();
    displayToLogical.clear();
    displayToOffset.clear();

    if (!wordWrap) {
      for (int i = 0; i < lines.size(); i++) {
        displayLines.add(lines.get(i));
        displayToLogical.add(i);
        displayToOffset.add(0);
      }
      wikiLinkSegmentsDirty = true;
      return;
    }

    int maxWidth = this.width - 10; // 5px padding each side
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.isEmpty()) {
        displayLines.add("");
        displayToLogical.add(i);
        displayToOffset.add(0);
        continue;
      }
      int offset = 0;
      while (offset < line.length()) {
        String remaining = line.substring(offset);
        String trimmed = textRenderer.trimToWidth(remaining, maxWidth);
        if (trimmed.isEmpty() && !remaining.isEmpty()) {
          trimmed = remaining.substring(0, 1); // at least 1 char to avoid infinite loop
        }
        displayLines.add(trimmed);
        displayToLogical.add(i);
        displayToOffset.add(offset);
        offset += trimmed.length();
      }
    }
    wikiLinkSegmentsDirty = true;
  }

  /**
   * Scans display lines for [[...]] patterns and caches their character ranges. Each entry in
   * displayLineWikiLinks corresponds to a display line and contains a list of [start, end] pairs
   * (relative to the display line string).
   */
  private void rebuildWikiLinkSegments() {
    displayLineWikiLinks.clear();
    for (int di = 0; di < displayLines.size(); di++) {
      List<int[]> segments = new ArrayList<>();
      int logicalLine = displayToLogical.get(di);
      int dispOffset = displayToOffset.get(di);
      String fullLogical = lines.get(logicalLine);

      // Find all [[...]] in the logical line
      Matcher m = WIKI_LINK_EDIT_PATTERN.matcher(fullLogical);
      int dispLen = displayLines.get(di).length();
      int dispEnd = dispOffset + dispLen;
      while (m.find()) {
        int matchStart = m.start();
        int matchEnd = m.end();
        // Clip to this display line's range
        int segStart = Math.max(matchStart, dispOffset) - dispOffset;
        int segEnd = Math.min(matchEnd, dispEnd) - dispOffset;
        if (segStart < segEnd && segStart < dispLen) {
          segments.add(new int[] {segStart, segEnd});
        }
      }
      displayLineWikiLinks.add(segments);
    }
    wikiLinkSegmentsDirty = false;
  }

  protected int getDisplayLineForCursor() {
    for (int i = displayToLogical.size() - 1; i >= 0; i--) {
      if (displayToLogical.get(i) == cursorY && displayToOffset.get(i) <= cursorX) {
        return i;
      }
    }
    return 0;
  }

  protected int getDisplayCursorX() {
    int displayLine = getDisplayLineForCursor();
    return cursorX - displayToOffset.get(displayLine);
  }

  public void setInternalScissoring(boolean enabled) {
    this.internalScissoringEnabled = enabled;
  }

  public void setText(String text) {
    this.lines.clear();
    this.lines.addAll(Arrays.asList(Objects.requireNonNullElse(text, "").split("\n", -1)));
    if (this.lines.isEmpty()) this.lines.add("");
    this.setCursorToEnd();
    this.clearSelection();
    this.focused = false;
    this.scrollX = 0;
    this.scrollY = 0;

    rebuildDisplayLines();
    clampScroll();
  }

  public String getText() {
    return String.join("\n", this.lines);
  }

  protected void setCursor(int x, int y) {
    this.cursorY = Math.max(0, Math.min(y, this.lines.size() - 1));
    this.cursorX = Math.max(0, Math.min(x, this.lines.get(this.cursorY).length()));
    ensureCursorVisible();
  }

  protected void setCursorToEnd() {
    this.cursorY = this.lines.size() - 1;
    this.cursorX = this.lines.get(this.cursorY).length();
    ensureCursorVisible();
  }

  // ---------- Absolute index helpers ----------
  protected int getTotalLength() {
    int total = 0;
    for (String s : lines) total += s.length();
    total += Math.max(0, lines.size() - 1); // newlines
    return total;
  }

  protected int getAbsoluteIndex(int lineIndex, int col) {
    int abs = 0;
    for (int i = 0; i < lineIndex; i++) {
      abs += lines.get(i).length() + 1; // line + newline
    }
    abs += Math.max(0, Math.min(col, lines.get(lineIndex).length()));
    return abs;
  }

  protected int[] getLineColFromAbsolute(int absoluteIndex) {
    int remaining = Math.max(0, Math.min(absoluteIndex, getTotalLength()));
    for (int i = 0; i < lines.size(); i++) {
      int lineLen = lines.get(i).length();
      if (remaining <= lineLen) {
        return new int[] {i, remaining};
      }
      remaining -= (lineLen + 1); // consume line + newline
      if (remaining < 0) {
        return new int[] {i, lineLen};
      }
    }
    int last = lines.size() - 1;
    return new int[] {last, lines.get(last).length()};
  }

  public int getCursorAbsolute() {
    return getAbsoluteIndex(cursorY, cursorX);
  }

  /** Returns the Y offset of the cursor relative to the widget's top, accounting for scroll. */
  public int getCursorScreenY() {
    int displayLine = 0;
    if (wordWrap) {
      for (int i = 0; i < displayToLogical.size(); i++) {
        if (displayToLogical.get(i) == cursorY
            && displayToOffset.get(i) + displayLines.get(i).length() >= cursorX) {
          displayLine = i;
          break;
        }
      }
    } else {
      displayLine = cursorY;
    }
    int padding = 5;
    return displayLine * textRenderer.fontHeight - (int) scrollY + padding;
  }

  /** Returns the X offset of the cursor relative to the widget's left, accounting for scroll. */
  public int getCursorScreenX() {
    int displayCursorX = getDisplayCursorX();
    int displayLine = getDisplayLineForCursor();
    String dispLineStr = displayLines.get(displayLine);
    String beforeCursor = dispLineStr.substring(0, Math.min(displayCursorX, dispLineStr.length()));
    int padding = 5;
    return padding + (int) Math.round(textRenderer.getWidth(beforeCursor) - scrollX);
  }

  /** Returns the height of a single text line. */
  public int getLineHeight() {
    return textRenderer.fontHeight;
  }

  /** Returns true if a wiki-link hover preview is currently being drawn. For E2E testing. */
  public boolean isPreviewVisible() {
    return previewVisible;
  }

  /** Searches ClientCache for a quest matching the given title. Returns null if not found. */
  private static Quest findQuestByTitle(String title) {
    if (title == null || title.isEmpty()) return null;
    return Stream.concat(ClientCache.getMyQuests().stream(), ClientCache.getServerQuests().stream())
        .filter(q -> title.equals(q.getTitle()))
        .findFirst()
        .orElse(null);
  }

  public int getSelectionStartAbsolute() {
    return this.selectionStart;
  }

  public int getSelectionEndAbsolute() {
    return this.selectionEnd;
  }

  public void setCursorFromAbsolute(int absoluteIndex) {
    int[] lc = getLineColFromAbsolute(absoluteIndex);
    // lc[0] = line, lc[1] = col
    setCursor(lc[1], lc[0]);
  }

  // ---------- Selection helpers ----------
  public void setSelectionAbsolute(int a, int b) {
    int t = Math.max(0, Math.min(a, getTotalLength()));
    int e = Math.max(0, Math.min(b, getTotalLength()));
    if (t <= e) {
      selectionStart = t;
      selectionEnd = e;
    } else {
      selectionStart = e;
      selectionEnd = t;
    }
  }

  protected void clearSelection() {
    int abs = getAbsoluteIndex(cursorY, cursorX);
    selectionStart = abs;
    selectionEnd = abs;
    selectionAnchor = abs;
  }

  protected boolean hasSelection() {
    return selectionEnd > selectionStart;
  }

  protected String getSelectedText() {
    if (!hasSelection()) return "";
    StringBuilder sb = new StringBuilder();
    int start = selectionStart;
    int end = selectionEnd;
    int[] sLC = getLineColFromAbsolute(start);
    int[] eLC = getLineColFromAbsolute(end);
    if (sLC[0] == eLC[0]) {
      return lines.get(sLC[0]).substring(sLC[1], eLC[1]);
    }
    sb.append(lines.get(sLC[0]).substring(sLC[1]));
    sb.append('\n');
    for (int i = sLC[0] + 1; i < eLC[0]; i++) {
      sb.append(lines.get(i));
      sb.append('\n');
    }
    sb.append(lines.get(eLC[0]), 0, eLC[1]);
    return sb.toString();
  }

  protected void deleteSelection() {
    if (!hasSelection()) return;

    final int start = selectionStart;
    final int end = selectionEnd;
    final String selectedText = getSelectedText();

    // Create an action that knows how to delete AND re-insert the text
    TextAction action =
        new TextAction() {
          @Override
          public void execute() {
            _deleteTextInternal(start, end);
          }

          @Override
          public void undo() {
            _insertTextInternal(start, selectedText);
          }
        };

    undoManager.perform(action);
    onChanged();
  }

  protected void selectWordAt(int absoluteIndex) {
    int[] lc = getLineColFromAbsolute(absoluteIndex);
    int line = lc[0];
    int col = lc[1];
    String lineStr = this.lines.get(line);

    if (lineStr.isEmpty()) return; // Nothing to select on an empty line

    // Find the start of the word by moving backward
    int wordStartCol = col;
    // If the cursor is at the end of a word, move it back one to be "inside" it
    if (wordStartCol > 0 && wordStartCol >= lineStr.length()
        || Character.isWhitespace(lineStr.charAt(wordStartCol))) {
      if (wordStartCol > 0) wordStartCol--;
    }
    while (wordStartCol > 0 && !Character.isWhitespace(lineStr.charAt(wordStartCol - 1))) {
      wordStartCol--;
    }

    // Find the end of the word by moving forward
    int wordEndCol = col;
    while (wordEndCol < lineStr.length() && !Character.isWhitespace(lineStr.charAt(wordEndCol))) {
      wordEndCol++;
    }

    int selectionStartAbs = getAbsoluteIndex(line, wordStartCol);
    int selectionEndAbs = getAbsoluteIndex(line, wordEndCol);

    setSelectionAbsolute(selectionStartAbs, selectionEndAbs);
    setCursorFromAbsolute(selectionEndAbs); // Move cursor to the end of the new selection
  }

  protected void selectLineAt(int absoluteIndex) {
    int[] lc = getLineColFromAbsolute(absoluteIndex);
    int line = lc[0];

    int lineStartAbs = getAbsoluteIndex(line, 0);
    int lineEndAbs = getAbsoluteIndex(line, this.lines.get(line).length());

    setSelectionAbsolute(lineStartAbs, lineEndAbs);
    setCursorFromAbsolute(lineEndAbs); // Move cursor to the end of the line
  }

  // ---------- Insertion ----------
  public void insertText(String textToInsert) {
    if (textToInsert == null || textToInsert.isEmpty()) return;
    if (hasSelection()) deleteSelection();

    final int start = getCursorAbsolute();
    final String text = textToInsert;

    TextAction action =
        new TextAction() {
          @Override
          public void execute() {
            _insertTextInternal(start, text);
          }

          @Override
          public void undo() {
            _deleteTextInternal(start, start + text.length());
          }
        };
    undoManager.perform(action);

    onChanged();
  }

  // ---------- Rendering ----------
  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    // Focus is managed by TextFieldComponent via setFocused() -- don't override it here.
    // (Previously checked screen.getFocused() == this, but that fails when wrapped in owo-ui.)

    int padding = 5;
    int contentX = this.x + padding;
    int contentY = this.y + padding;
    int contentWidth = this.width - padding * 2;
    int contentHeight = this.height - padding * 2;

    // Reserve space for scrollbars if needed
    boolean vNeeded = isScrollbarNeededV();
    boolean hNeeded = isScrollbarNeededH();
    if (vNeeded) contentWidth -= (SCROLLBAR_THICKNESS + 2);
    if (hNeeded) contentHeight -= (SCROLLBAR_THICKNESS + 2);

    if (this.internalScissoringEnabled) {
      context.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height);
    }

    if (getText().isEmpty()
        && !this.focused
        && this.placeholderText != null
        && !this.placeholderText.isEmpty()) {
      // Draw placeholder text, respecting horizontal scroll
      int drawX = contentX - (int) Math.round(scrollX);
      context.drawText(
          textRenderer,
          this.placeholderText,
          drawX,
          contentY,
          Colors.TEXT_DISABLED,
          false); // Gray color
    }

    int firstVisibleLine = (int) (scrollY / textRenderer.fontHeight);
    int lastVisibleLine =
        Math.min(
            displayLines.size() - 1,
            firstVisibleLine + (contentHeight / textRenderer.fontHeight) + 1);

    // Draw selection background (per display-line)
    if (hasSelection() && this.focused) {
      int selStart = selectionStart;
      int selEnd = selectionEnd;
      for (int di = firstVisibleLine; di <= lastVisibleLine; di++) {
        int logicalLine = displayToLogical.get(di);
        int dispOffset = displayToOffset.get(di);
        String dispLineStr = displayLines.get(di);
        int dispLineLen = dispLineStr.length();
        // absolute index of start and end of this display segment
        int dispSegStartAbs = getAbsoluteIndex(logicalLine, dispOffset);
        int dispSegEndAbs = dispSegStartAbs + dispLineLen;
        int interStart = Math.max(selStart, dispSegStartAbs);
        int interEnd = Math.min(selEnd, dispSegEndAbs);
        if (interStart < interEnd) {
          int startCol = interStart - dispSegStartAbs;
          int endCol = interEnd - dispSegStartAbs;
          int sx =
              contentX
                  + (int)
                      Math.round(
                          textRenderer.getWidth(dispLineStr.substring(0, startCol)) - scrollX);
          int ex =
              contentX
                  + (int)
                      Math.round(textRenderer.getWidth(dispLineStr.substring(0, endCol)) - scrollX);
          int lineYPos = contentY + (di * textRenderer.fontHeight) - (int) scrollY;
          context.fill(
              sx, lineYPos, ex, lineYPos + textRenderer.fontHeight, Colors.SELECTION_BACKGROUND);
        }
      }
    }

    // Draw lines with wiki-link syntax highlighting
    if (wikiLinkSegmentsDirty) rebuildWikiLinkSegments();
    for (int di = firstVisibleLine; di <= lastVisibleLine; di++) {
      int lineYPos = contentY + (di * textRenderer.fontHeight) - (int) scrollY;
      if (lineYPos > this.y - textRenderer.fontHeight && lineYPos < this.y + this.height) {
        int drawX = contentX - (int) Math.round(scrollX);
        String dispLine = displayLines.get(di);
        List<int[]> wikiLinks =
            di < displayLineWikiLinks.size() ? displayLineWikiLinks.get(di) : List.of();

        if (wikiLinks.isEmpty()) {
          // Fast path: no wiki-links on this line
          context.drawText(
              this.textRenderer, dispLine, drawX, lineYPos, Colors.TEXT_PRIMARY, false);
        } else {
          // Segmented drawing: alternate between normal and wiki-link styled text
          int pos = 0;
          int currentX = drawX;
          for (int[] seg : wikiLinks) {
            // Normal segment before this wiki-link
            if (pos < seg[0]) {
              String normal = dispLine.substring(pos, seg[0]);
              context.drawText(
                  this.textRenderer, normal, currentX, lineYPos, Colors.TEXT_PRIMARY, false);
              currentX += this.textRenderer.getWidth(normal);
            }
            // Wiki-link segment: amber + italics + underline
            String wikiText = dispLine.substring(seg[0], seg[1]);
            Text styledText =
                Text.literal(wikiText)
                    .setStyle(
                        Style.EMPTY
                            .withColor(WIKI_LINK_COLOR)
                            .withItalic(true)
                            .withUnderline(true));
            context.drawText(
                this.textRenderer, styledText.asOrderedText(), currentX, lineYPos, -1, false);
            currentX += this.textRenderer.getWidth(wikiText);
            pos = seg[1];
          }
          // Remaining normal text after last wiki-link
          if (pos < dispLine.length()) {
            String remaining = dispLine.substring(pos);
            context.drawText(
                this.textRenderer, remaining, currentX, lineYPos, Colors.TEXT_PRIMARY, false);
          }
        }
      }
    }

    // Wiki-link hover preview in edit mode (works without focus)
    previewVisible = false;
    {
      // Check if mouse is over a wiki-link segment
      int hoverAbsIndex = absoluteIndexFromMouse(mouseX, mouseY);
      // Find which display line the mouse is on
      int hoverDisplayLine =
          (int) ((mouseY - (this.y + padding) + scrollY) / textRenderer.fontHeight);
      hoverDisplayLine = Math.max(0, Math.min(hoverDisplayLine, displayLines.size() - 1));
      if (hoverDisplayLine < displayLineWikiLinks.size()) {
        List<int[]> segments = displayLineWikiLinks.get(hoverDisplayLine);
        int dispOffset = displayToOffset.get(hoverDisplayLine);
        int logicalLine = displayToLogical.get(hoverDisplayLine);
        // Convert absolute index to position within this display line
        int absLineStart = getAbsoluteIndex(logicalLine, dispOffset);
        int posInDispLine = hoverAbsIndex - absLineStart;
        for (int[] seg : segments) {
          if (posInDispLine >= seg[0] && posInDispLine < seg[1]) {
            // Extract title from between [[ and ]]
            String dispLine = displayLines.get(hoverDisplayLine);
            String wikiText = dispLine.substring(seg[0], seg[1]);
            if (wikiText.startsWith("[[") && wikiText.endsWith("]]")) {
              String title = wikiText.substring(2, wikiText.length() - 2);
              Quest quest = findQuestByTitle(title);
              if (quest != null) {
                MinecraftClient mc = MinecraftClient.getInstance();
                HoverPreviewRenderer.draw(
                    context,
                    quest,
                    mouseX,
                    mouseY,
                    mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight());
                previewVisible = true;
              }
            }
            break;
          }
        }
      }
    }

    // caret blink
    long now = System.currentTimeMillis();
    if (now - lastBlinkTime >= BLINK_INTERVAL_MS) {
      caretVisible = !caretVisible;
      lastBlinkTime = now;
    }

    // Caret drawing (vertical bar) - make it a bit wider and taller for visibility
    if (this.caretEnabled && this.focused && caretVisible) {
      int paddingTop = 1;
      int paddingBottom = 1;
      int displayLine = getDisplayLineForCursor();
      if (displayLine >= firstVisibleLine && displayLine <= lastVisibleLine) {
        int displayCursorX = getDisplayCursorX();
        String dispLineStr = displayLines.get(displayLine);
        String beforeCursor =
            dispLineStr.substring(0, Math.min(displayCursorX, dispLineStr.length()));
        int caretPixelX =
            contentX + (int) Math.round(textRenderer.getWidth(beforeCursor) - scrollX);
        int caretYPos = contentY + (displayLine * textRenderer.fontHeight) - (int) scrollY;
        int top = caretYPos - paddingTop;
        int bottom = caretYPos + textRenderer.fontHeight + paddingBottom;
        // draw 2px wide vertical caret
        context.fill(caretPixelX, top, caretPixelX + 2, bottom, Colors.CARET_PRIMARY);
      }
    }

    if (this.internalScissoringEnabled) {
      context.disableScissor();
    }

    // Draw scrollbars
    if (this.scrollingEnabled && vNeeded) renderVScrollbar(context, contentHeight);
    if (this.scrollingEnabled && hNeeded) renderHScrollbar(context, contentX, contentWidth);
  }

  protected void renderVScrollbar(DrawContext context, int contentHeight) {
    int scrollbarX = this.x + this.width - SCROLLBAR_THICKNESS - 2;
    int maxScroll = getMaxScrollV();
    int lineCount = getEffectiveLineCount();
    float contentPixelHeight = lineCount * textRenderer.fontHeight;
    float thumbHeight = Math.max(10, (contentHeight / contentPixelHeight) * contentHeight);
    float thumbY =
        (float) ((scrollY / (double) Math.max(1, maxScroll)) * (contentHeight - thumbHeight));
    int thumbColor =
        isDraggingVScrollbar ? Colors.SCROLLBAR_THUMB_ACTIVE : Colors.SCROLLBAR_THUMB_INACTIVE;
    context.fill(
        scrollbarX,
        this.y + 5 + (int) thumbY,
        scrollbarX + SCROLLBAR_THICKNESS,
        this.y + 5 + (int) (thumbY + thumbHeight),
        thumbColor);
  }

  protected void renderHScrollbar(DrawContext context, int contentX, int contentWidth) {
    int scrollbarY = this.y + this.height - SCROLLBAR_THICKNESS - 2;
    // compute max horizontal content width
    int maxLinePixel = getMaxLinePixelWidth();
    if (maxLinePixel <= 0) return;
    float thumbWidth =
        Math.max(10, (contentWidth / (float) Math.max(1, getMaxLinePixelWidth())) * contentWidth);
    float thumbX =
        (float) ((scrollX / (double) Math.max(1, getMaxScrollH())) * (contentWidth - thumbWidth));
    int thumbColor =
        isDraggingHScrollbar ? Colors.SCROLLBAR_THUMB_ACTIVE : Colors.SCROLLBAR_THUMB_INACTIVE;
    context.fill(
        contentX + (int) thumbX,
        scrollbarY,
        contentX + (int) (thumbX + thumbWidth),
        scrollbarY + SCROLLBAR_THICKNESS,
        thumbColor);
  }

  // ---------- Mouse handling ----------
  @Override
  public boolean mouseClicked(Click click, boolean simulated) {
    double mouseX = click.x();
    double mouseY = click.y();
    int button = click.button();
    if (isMouseOver(mouseX, mouseY)) {
      boolean vNeeded = this.scrollingEnabled && isScrollbarNeededV();
      boolean hNeeded = this.scrollingEnabled && isScrollbarNeededH();

      // vertical scrollbar click?
      if (vNeeded) {
        int vXStart = this.x + this.width - SCROLLBAR_THICKNESS - 2;
        // The vertical scrollbar's clickable area stops where the horizontal one begins.
        int vYEnd = this.y + this.height - (hNeeded ? (SCROLLBAR_THICKNESS + 2) : 0);
        if (mouseX >= vXStart && mouseY < vYEnd) {
          this.isDraggingVScrollbar = true;
          this.vScrollbarDragStartY = mouseY;
          this.vScrollbarDragStartScrollY = this.scrollY;
          this.focused = true;
          return true;
        }
      }
      // horizontal scrollbar click?
      if (hNeeded) {
        int hYStart = this.y + this.height - SCROLLBAR_THICKNESS - 2;
        // The horizontal scrollbar's clickable area stops where the vertical one begins.
        int hXEnd = this.x + this.width - (vNeeded ? (SCROLLBAR_THICKNESS + 2) : 0);
        if (mouseY >= hYStart && mouseX < hXEnd) {
          this.isDraggingHScrollbar = true;
          this.hScrollbarDragStartX = mouseX;
          this.hScrollbarDragStartScrollX = this.scrollX;
          this.focused = true;
          return true;
        }
      }

      // normal text area click
      this.focused = true;
      int clickedAbs = absoluteIndexFromMouse(mouseX, mouseY);

      // --- Double/Triple click detection ---
      long now = System.currentTimeMillis();
      boolean isDoubleClick =
          (clickedAbs == lastClickIndex) && (now - lastClickTime < DOUBLE_CLICK_INTERVAL_MS);
      lastClickTime = now;
      lastClickIndex = clickedAbs;

      if (isDoubleClick) {
        if (clickCount == 1) { // It was a single click, now it's a double
          clickCount = 2;
          selectWordAt(clickedAbs);
        } else { // It was already a double, now it's a triple
          clickCount = 0; // Reset
          selectLineAt(clickedAbs);
        }
        this.isDraggingText = false;
      } else { // This is a single click
        clickCount = 1;
        if (this.shiftDown) {
          setSelectionAbsolute(selectionAnchor, clickedAbs);
          setCursorFromAbsolute(clickedAbs);
        } else {
          selectionAnchor = clickedAbs;
          setSelectionAbsolute(clickedAbs, clickedAbs);
          setCursorFromAbsolute(clickedAbs);
          this.isDraggingText = true;
        }
      }
      return true;
    }
    this.focused = false;
    return false;
  }

  @Override
  public boolean mouseReleased(Click click) {
    isDraggingVScrollbar = false;
    isDraggingHScrollbar = false;
    isDraggingText = false;
    return Element.super.mouseReleased(click);
  }

  @Override
  public boolean mouseDragged(Click click, double deltaX, double deltaY) {
    double mouseX = click.x();
    double mouseY = click.y();
    if (this.scrollingEnabled && isDraggingVScrollbar) {
      double dragDelta = mouseY - this.vScrollbarDragStartY;
      int trackHeight = this.height - 10 - (isScrollbarNeededH() ? (SCROLLBAR_THICKNESS + 2) : 0);

      double maxScroll = Math.max(1, getMaxScrollV());
      int lineCount = getEffectiveLineCount();
      double contentPixelHeight = lineCount * textRenderer.fontHeight;
      double thumbHeight = Math.max(10, (trackHeight / contentPixelHeight) * trackHeight);
      double toTrack = (trackHeight - thumbHeight);

      if (toTrack <= 0) return true;
      this.scrollY = this.vScrollbarDragStartScrollY + (dragDelta * (maxScroll / toTrack));
      clampScroll();
      return true;
    }
    if (this.scrollingEnabled && isDraggingHScrollbar) {
      double dragDelta = mouseX - this.hScrollbarDragStartX;
      int padding = 5;
      int contentWidth =
          this.width - padding * 2 - (isScrollbarNeededV() ? (SCROLLBAR_THICKNESS + 2) : 0);
      int maxH = getMaxScrollH();

      double thumbWidth =
          Math.max(10, (contentWidth / (float) Math.max(1, getMaxLinePixelWidth())) * contentWidth);
      double toTrack = (contentWidth - thumbWidth);

      if (toTrack <= 0) return true;
      this.scrollX = this.hScrollbarDragStartScrollX + (dragDelta * (maxH / toTrack));
      clampScroll();
      return true;
    }
    if (isDraggingText) {
      int abs = absoluteIndexFromMouse(mouseX, mouseY);
      // anchor remains as when mouseClicked started
      setSelectionAbsolute(selectionAnchor, abs);
      setCursorFromAbsolute(abs);
      return true;
    }
    return false;
  }

  @Override
  public boolean mouseScrolled(
      double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    if (!isMouseOver(mouseX, mouseY)) {
      return false;
    }

    final double scrollMultiplier = 10.0;
    boolean handled = false;

    if (this.shiftDown && this.allowHorizontalScroll && verticalAmount != 0) {
      this.scrollX -= verticalAmount * scrollMultiplier;
      clampScroll();
      handled = true;
    } else if (this.allowVerticalScroll && verticalAmount != 0) {
      this.scrollY -= verticalAmount * scrollMultiplier;
      clampScroll();
      handled = true;
    }

    if (this.allowHorizontalScroll && horizontalAmount != 0) {
      this.scrollX -= horizontalAmount * scrollMultiplier;
      clampScroll();
      handled = true;
    }

    return handled;
  }

  protected int absoluteIndexFromMouse(double mouseX, double mouseY) {
    int padding = 5;
    int contentX = this.x + padding;

    if (wordWrap) {
      int clickedDisplayLine =
          (int) ((mouseY - (this.y + padding) + scrollY) / textRenderer.fontHeight);
      clickedDisplayLine = Math.max(0, Math.min(clickedDisplayLine, displayLines.size() - 1));
      int relX = (int) Math.round(mouseX - contentX);
      if (relX < 0) relX = 0;
      String dispLine = displayLines.get(clickedDisplayLine);
      int charInDisplayLine = textRenderer.trimToWidth(dispLine, relX).length();
      int logicalLine = displayToLogical.get(clickedDisplayLine);
      int logicalCol = displayToOffset.get(clickedDisplayLine) + charInDisplayLine;
      logicalCol = Math.min(logicalCol, lines.get(logicalLine).length());
      return getAbsoluteIndex(logicalLine, logicalCol);
    }

    int clickedLine = (int) ((mouseY - (this.y + padding) + scrollY) / textRenderer.fontHeight);
    clickedLine = Math.max(0, Math.min(clickedLine, this.lines.size() - 1));
    int relX = (int) Math.round(mouseX - (contentX) + scrollX);
    if (relX < 0) relX = 0;
    String line = this.lines.get(clickedLine);
    int charIndex = this.textRenderer.trimToWidth(line, relX).length();
    return getAbsoluteIndex(clickedLine, charIndex);
  }

  // ---------- Keyboard ----------
  @Override
  public boolean keyPressed(KeyInput keyInput) {
    int keyCode = keyInput.key();
    int scanCode = keyInput.scancode();
    int modifiers = keyInput.modifiers();
    boolean hasShift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    boolean hasCtrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

    if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
      this.shiftDown = true;
    }
    if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
      this.ctrlDown = true;
    }

    if (!this.focused) return false;

    if (hasCtrl && keyCode == GLFW.GLFW_KEY_Z) {
      undoManager.undo();
      onChanged();
      return true;
    }
    if (hasCtrl && keyCode == GLFW.GLFW_KEY_Y) {
      undoManager.redo();
      onChanged();
      return true;
    }

    // ctrl+word moves (absolute space)
    if (hasCtrl && keyCode == GLFW.GLFW_KEY_LEFT) {
      int oldAbs = getAbsoluteIndex(cursorY, cursorX);
      int newAbs = moveWordBackAbsolute(oldAbs);
      moveCursorToAbsolute(newAbs, hasShift);
      return true;
    }
    if (hasCtrl && keyCode == GLFW.GLFW_KEY_RIGHT) {
      int oldAbs = getAbsoluteIndex(cursorY, cursorX);
      int newAbs = moveWordForwardAbsolute(oldAbs);
      moveCursorToAbsolute(newAbs, hasShift);
      return true;
    }

    // Select all: Ctrl+A
    if (hasCtrl && keyCode == GLFW.GLFW_KEY_A) {
      setSelectionAbsolute(0, getTotalLength());
      setCursorFromAbsolute(getTotalLength());
      return true;
    }
    // Copy: Ctrl+C
    if (hasCtrl && keyCode == GLFW.GLFW_KEY_C) {
      if (hasSelection()) MinecraftClient.getInstance().keyboard.setClipboard(getSelectedText());
      return true;
    }
    // Paste: Ctrl+V
    if (hasCtrl && keyCode == GLFW.GLFW_KEY_V) {
      String clip = MinecraftClient.getInstance().keyboard.getClipboard();
      if (clip != null && !clip.isEmpty()) insertText(clip);
      return true;
    }
    // Cut: Ctrl+X
    if (hasCtrl && keyCode == GLFW.GLFW_KEY_X) {
      if (hasSelection()) {
        MinecraftClient.getInstance().keyboard.setClipboard(getSelectedText());
        deleteSelection();
      }
      return true;
    }

    switch (keyCode) {
      case GLFW.GLFW_KEY_TAB -> {
        insertText("    ");
        return true;
      }
      case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
        if (this.lines.size() < this.maxLines) {
          insertText("\n");
        }
        return true;
      }
      case GLFW.GLFW_KEY_BACKSPACE -> {
        if (hasSelection()) {
          deleteSelection();
          return true;
        }
        if (hasCtrl) {
          int oldAbs = getAbsoluteIndex(cursorY, cursorX);
          if (oldAbs > 0) {
            int newAbs = moveWordBackAbsolute(oldAbs);
            setSelectionAbsolute(newAbs, oldAbs);
            deleteSelection();
          }
          return true;
        }
        // Smart tab deletion: if 4 spaces precede cursor, delete all 4
        if (cursorX >= 4) {
          String line = this.lines.get(this.cursorY);
          if (cursorX <= line.length() && line.substring(cursorX - 4, cursorX).equals("    ")) {
            setSelectionAbsolute(
                getAbsoluteIndex(cursorY, cursorX - 4), getAbsoluteIndex(cursorY, cursorX));
            deleteSelection();
            onChanged();
            return true;
          }
        }
        if (cursorX == 0 && cursorY > 0) {
          String lineToMerge = this.lines.remove(this.cursorY);
          int prevLineIndex = this.cursorY - 1;
          String prevLine = this.lines.get(prevLineIndex);
          int newCursorX = prevLine.length();
          this.lines.set(prevLineIndex, prevLine + lineToMerge);
          setCursor(newCursorX, prevLineIndex);
        } else if (cursorX > 0) {
          String line = this.lines.get(this.cursorY);
          String before = line.substring(0, cursorX - 1);
          String after = line.substring(cursorX);
          this.lines.set(this.cursorY, before + after);
          setCursor(cursorX - 1, cursorY);
        }
        onChanged();
        return true;
      }
      case GLFW.GLFW_KEY_DELETE -> {
        if (hasSelection()) {
          deleteSelection();
          return true;
        }
        if (hasCtrl) {
          int oldAbs = getAbsoluteIndex(cursorY, cursorX);
          if (oldAbs < getTotalLength()) {
            int newAbs = moveWordForwardAbsolute(oldAbs);
            setSelectionAbsolute(oldAbs, newAbs);
            deleteSelection();
          }
          return true;
        }
        String line = this.lines.get(this.cursorY);
        if (cursorX == line.length() && cursorY < this.lines.size() - 1) {
          String nextLine = this.lines.remove(cursorY + 1);
          this.lines.set(cursorY, line + nextLine);
        } else if (cursorX < line.length()) {
          String before = line.substring(0, cursorX);
          String after = line.substring(cursorX + 1);
          this.lines.set(this.cursorY, before + after);
        }
        onChanged();
        return true;
      }
      case GLFW.GLFW_KEY_UP -> {
        int newLine = Math.max(0, cursorY - 1);
        int newCol = Math.min(cursorX, lines.get(newLine).length());
        int newAbs = getAbsoluteIndex(newLine, newCol);
        moveCursorToAbsolute(newAbs, hasShift);
        return true;
      }
      case GLFW.GLFW_KEY_DOWN -> {
        int newLine = Math.min(lines.size() - 1, cursorY + 1);
        int newCol = Math.min(cursorX, lines.get(newLine).length());
        int newAbs = getAbsoluteIndex(newLine, newCol);
        moveCursorToAbsolute(newAbs, hasShift);
        return true;
      }
      case GLFW.GLFW_KEY_LEFT -> {
        int oldAbs = getAbsoluteIndex(cursorY, cursorX);
        if (oldAbs == 0) return true;
        int newAbs = oldAbs - 1;
        moveCursorToAbsolute(newAbs, hasShift);
        return true;
      }
      case GLFW.GLFW_KEY_RIGHT -> {
        int oldAbs = getAbsoluteIndex(cursorY, cursorX);
        if (oldAbs >= getTotalLength()) return true;
        int newAbs = oldAbs + 1;
        moveCursorToAbsolute(newAbs, hasShift);
        return true;
      }
      case GLFW.GLFW_KEY_HOME -> {
        int newAbs = getAbsoluteIndex(cursorY, 0);
        moveCursorToAbsolute(newAbs, hasShift);
        return true;
      }
      case GLFW.GLFW_KEY_END -> {
        int newAbs = getAbsoluteIndex(cursorY, lines.get(cursorY).length());
        moveCursorToAbsolute(newAbs, hasShift);
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean keyReleased(KeyInput keyInput) {
    int keyCode = keyInput.key();
    if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
      this.shiftDown = false;
    }
    if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
      this.ctrlDown = false;
    }
    return false;
  }

  @Override
  public boolean charTyped(CharInput charInput) {
    if (this.focused) {
      if (charInput.isValidChar()) {
        insertText(charInput.asString());
        return true;
      }
    }
    return false;
  }

  protected void ensureCursorVisible() {
    int padding = 5;
    int contentWidth =
        this.width - padding * 2 - (isScrollbarNeededV() ? (SCROLLBAR_THICKNESS + 2) : 0);
    int contentHeight =
        this.height - padding * 2 - (isScrollbarNeededH() ? (SCROLLBAR_THICKNESS + 2) : 0);

    if (wordWrap) {
      // Use display-line index for vertical scroll
      int displayLine = getDisplayLineForCursor();
      int topVisibleLine = (int) (scrollY / textRenderer.fontHeight);
      int linesOnScreen = contentHeight / textRenderer.fontHeight;
      if (displayLine < topVisibleLine) scrollY = displayLine * textRenderer.fontHeight;
      if (displayLine >= topVisibleLine + linesOnScreen)
        scrollY = (displayLine - linesOnScreen + 1) * textRenderer.fontHeight;
      // scrollX stays 0 in word-wrap mode
    } else {
      // vertical
      int topVisibleLine = (int) (scrollY / textRenderer.fontHeight);
      if (cursorY < topVisibleLine) scrollY = cursorY * textRenderer.fontHeight;
      int linesOnScreen = contentHeight / textRenderer.fontHeight;
      if (cursorY >= topVisibleLine + linesOnScreen)
        scrollY = (cursorY - linesOnScreen + 1) * textRenderer.fontHeight;

      // horizontal - ensure the caret's pixel location (within the line) is visible
      String line = lines.get(cursorY);
      int caretPixel = textRenderer.getWidth(line.substring(0, cursorX));
      if (caretPixel - scrollX < 0) {
        scrollX = caretPixel;
      } else if (caretPixel - scrollX > contentWidth - 4) {
        scrollX = caretPixel - (contentWidth - 4);
      }
    }
    clampScroll();
  }

  // ---------- Scroll metrics ----------
  protected int getMaxScrollV() {
    int lineCount = getEffectiveLineCount();
    return Math.max(
        0,
        (lineCount * textRenderer.fontHeight)
            - (height - 10 - (isScrollbarNeededH() ? (SCROLLBAR_THICKNESS + 2) : 0)));
  }

  protected boolean isScrollbarNeededV() {
    int lineCount = getEffectiveLineCount();
    return (lineCount * textRenderer.fontHeight) > (height - 10);
  }

  protected int getMaxLinePixelWidth() {
    int max = 0;
    for (String s : lines) {
      int w = textRenderer.getWidth(s);
      if (w > max) max = w;
    }
    return max;
  }

  protected int getMaxScrollH() {
    if (wordWrap) return 0;
    int padding = 5;
    int contentWidth =
        this.width - padding * 2 - (isScrollbarNeededV() ? (SCROLLBAR_THICKNESS + 2) : 0);
    int maxLine = getMaxLinePixelWidth();
    return Math.max(0, maxLine - contentWidth);
  }

  protected boolean isScrollbarNeededH() {
    if (wordWrap) return false;
    return getMaxLinePixelWidth()
        > (this.width - 10 - (isScrollbarNeededV() ? (SCROLLBAR_THICKNESS + 2) : 0));
  }

  protected void clampScroll() {
    if (allowVerticalScroll) {
      double maxV = getMaxScrollV();
      if (this.scrollY > maxV) this.scrollY = maxV;
      if (this.scrollY < 0) this.scrollY = 0;
    } else {
      this.scrollY = 0;
    }

    if (wordWrap) {
      this.scrollX = 0;
    } else if (allowHorizontalScroll) {
      double maxH = getMaxScrollH();
      if (this.scrollX > maxH) this.scrollX = maxH;
      if (this.scrollX < 0) this.scrollX = 0;
    } else {
      this.scrollX = 0;
    }
  }

  // ---------- Cursor & selection movement ----------
  // move cursor absolute with correct selection anchor behavior
  protected void moveCursorToAbsolute(int newAbs, boolean keepSelection) {
    newAbs = Math.max(0, Math.min(newAbs, getTotalLength()));
    int oldAbs = getAbsoluteIndex(cursorY, cursorX);
    if (keepSelection) {
      if (!hasSelection()) {
        // start selection anchored at old position
        selectionAnchor = oldAbs;
        setSelectionAbsolute(selectionAnchor, newAbs);
      } else {
        // extend/shrink selection anchored at selectionAnchor
        setSelectionAbsolute(selectionAnchor, newAbs);
      }
    } else {
      setCursorFromAbsolute(newAbs);
      clearSelection();
      return;
    }
    setCursorFromAbsolute(newAbs);
    ensureCursorVisible();
  }

  protected int moveWordBackAbsolute(int abs) {
    if (abs <= 0) return 0;
    int[] lc = getLineColFromAbsolute(abs);
    int line = lc[0], col = lc[1];
    if (col == 0) {
      if (line == 0) return 0;
      int prevLine = line - 1;
      return getAbsoluteIndex(prevLine, lines.get(prevLine).length());
    }
    String lineStr = lines.get(line);
    int pos = col;
    while (pos > 0 && Character.isWhitespace(lineStr.charAt(pos - 1))) pos--;
    while (pos > 0 && !Character.isWhitespace(lineStr.charAt(pos - 1))) pos--;
    return getAbsoluteIndex(line, pos);
  }

  protected int moveWordForwardAbsolute(int abs) {
    if (abs >= getTotalLength()) return getTotalLength();
    int[] lc = getLineColFromAbsolute(abs);
    int line = lc[0], col = lc[1];
    String lineStr = lines.get(line);
    if (col == lineStr.length()) {
      if (line >= lines.size() - 1) return getTotalLength();
      return getAbsoluteIndex(line + 1, 0);
    }
    int pos = col;
    while (pos < lineStr.length() && !Character.isWhitespace(lineStr.charAt(pos))) pos++;
    while (pos < lineStr.length() && Character.isWhitespace(lineStr.charAt(pos))) pos++;
    return getAbsoluteIndex(line, pos);
  }

  // ---------- Misc ----------
  @Override
  public boolean isMouseOver(double mouseX, double mouseY) {
    return mouseX >= this.x
        && mouseX < this.x + this.width
        && mouseY >= this.y
        && mouseY < this.y + this.height;
  }

  @Override
  public void setFocused(boolean focused) {
    // This check prevents redundant logic if the focus state isn't changing.
    if (this.focused != focused) {
      this.focused = focused;
    }
  }

  @Override
  public boolean isFocused() {
    return this.focused;
  }

  public void _deleteTextInternal(int startAbsolute, int endAbsolute) {
    int[] sLC = getLineColFromAbsolute(startAbsolute);
    int[] eLC = getLineColFromAbsolute(endAbsolute);

    if (sLC[0] == eLC[0]) {
      String line = lines.get(sLC[0]);
      String before = line.substring(0, sLC[1]);
      String after = line.substring(eLC[1]);
      lines.set(sLC[0], before + after);
    } else {
      String firstPart = lines.get(sLC[0]).substring(0, sLC[1]);
      String lastPart = lines.get(eLC[0]).substring(eLC[1]);
      if (eLC[0] >= sLC[0] + 1) {
        lines.subList(sLC[0] + 1, eLC[0] + 1).clear();
      }
      lines.set(sLC[0], firstPart + lastPart);
    }
    setCursorFromAbsolute(startAbsolute);
    clearSelection();
  }

  public void _insertTextInternal(int startAbsolute, String textToInsert) {
    setCursorFromAbsolute(startAbsolute); // Set cursor to know where to insert

    String[] parts = textToInsert.split("\n", -1);
    String currentLine = lines.get(cursorY);
    String beforeCursor = currentLine.substring(0, cursorX);
    String afterCursor = currentLine.substring(cursorX);

    if (parts.length == 1) {
      lines.set(cursorY, beforeCursor + parts[0] + afterCursor);
      setCursor(beforeCursor.length() + parts[0].length(), cursorY);
    } else {
      lines.set(cursorY, beforeCursor + parts[0]);
      int insertAt = cursorY + 1;
      for (int i = 1; i < parts.length - 1; i++) {
        if (lines.size() >= maxLines) break;
        lines.add(insertAt, parts[i]);
        insertAt++;
      }
      if (lines.size() < maxLines) {
        lines.add(insertAt, parts[parts.length - 1] + afterCursor);
        setCursor(parts[parts.length - 1].length(), insertAt);
      } else {
        String last = lines.get(lines.size() - 1);
        lines.set(lines.size() - 1, last + afterCursor);
        setCursor(lines.get(lines.size() - 1).length(), lines.size() - 1);
      }
    }
    ensureCursorVisible();
  }

  @Override
  public SelectionType getType() {
    return this.focused ? SelectionType.FOCUSED : SelectionType.NONE;
  }

  @Override
  public void appendNarrations(NarrationMessageBuilder builder) {
    /* Not needed */
  }
}
