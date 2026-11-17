package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Thin owo-ui wrapper around MultiLineTextFieldWidget. Delegates all rendering and input to the
 * underlying vanilla widget, using graphics translation to match the owo-ui layout position.
 * Implements GreedyInputUIComponent so owo-ui routes key/char events to it.
 */
public class TextFieldComponent extends BaseUIComponent implements GreedyInputUIComponent {
  private UIComponent tabTarget;

  private final MultiLineTextFieldWidget delegate;
  private final int preferredWidth;
  private final int preferredHeight;

  // Offset from delegate's fixed position to owo-ui layout position
  private int offsetX;
  private int offsetY;

  // Optional suggestion dropdown: gets the keys first while open; onEdit runs after each key
  private SuggestionDropdown<?> dropdown;
  private Runnable onEdit;

  public TextFieldComponent(MultiLineTextFieldWidget delegate) {
    this.delegate = delegate;
    this.preferredWidth = delegate.width;
    this.preferredHeight = delegate.height;
  }

  public MultiLineTextFieldWidget getDelegate() {
    return delegate;
  }

  public String getText() {
    return delegate.getText();
  }

  /**
   * Attaches a dropdown: while it is open it gets the arrow keys, Enter and Escape first, and
   * {@code onEdit} runs after every other key or character typed (to refresh the suggestions).
   */
  public void setAutocomplete(SuggestionDropdown<?> dropdown, Runnable onEdit) {
    this.dropdown = dropdown;
    this.onEdit = onEdit;
  }

  @Override
  protected int determineHorizontalContentSize(Sizing sizing) {
    return preferredWidth;
  }

  @Override
  protected int determineVerticalContentSize(Sizing sizing) {
    return preferredHeight;
  }

  private void computeOffset() {
    offsetX = this.x() - delegate.x;
    offsetY = this.y() - delegate.y;
  }

  @Override
  public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
    delegate.resize(this.width(), this.height());

    computeOffset();
    // Translate graphics so delegate renders at owo-ui position
    context.pose().pushMatrix();
    context.pose().translate(offsetX, offsetY);
    delegate.extractRenderState(
        (GuiGraphicsExtractor) context, mouseX - offsetX, mouseY - offsetY, delta);
    context.pose().popMatrix();
  }

  @Override
  public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
    computeOffset();
    // Click is component-relative, delegate expects absolute coords
    double absX = click.x() + this.x() - offsetX;
    double absY = click.y() + this.y() - offsetY;
    MouseButtonEvent delegateClick = new MouseButtonEvent(absX, absY, click.buttonInfo());
    boolean result = delegate.mouseClicked(delegateClick, false);
    // Always force focus when clicked -- delegate.mouseClicked may not set it
    // if coordinate translation causes isMouseOver to fail
    delegate.setFocused(true);
    return result || super.onMouseDown(click, doubled);
  }

  @Override
  public boolean onMouseUp(MouseButtonEvent click) {
    computeOffset();
    double absX = click.x() + this.x() - offsetX;
    double absY = click.y() + this.y() - offsetY;
    MouseButtonEvent delegateClick = new MouseButtonEvent(absX, absY, click.buttonInfo());
    return delegate.mouseReleased(delegateClick) || super.onMouseUp(click);
  }

  @Override
  public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
    computeOffset();
    double absX = click.x() + this.x() - offsetX;
    double absY = click.y() + this.y() - offsetY;
    MouseButtonEvent delegateClick = new MouseButtonEvent(absX, absY, click.buttonInfo());
    return delegate.mouseDragged(delegateClick, deltaX, deltaY);
  }

  @Override
  public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
    computeOffset();
    double absX = mouseX + this.x() - offsetX;
    double absY = mouseY + this.y() - offsetY;
    return delegate.mouseScrolled(absX, absY, 0, amount);
  }

  /** Where Tab sends focus from a single-line field; null falls back to owo-ui's focus cycling. */
  public TextFieldComponent tabTarget(UIComponent target) {
    this.tabTarget = target;
    return this;
  }

  @Override
  public boolean onKeyPress(KeyEvent keyInput) {
    // Intercept navigation keys for autocomplete dropdown before forwarding to delegate
    if (dropdown != null && dropdown.onKey(keyInput)) {
      return true;
    }
    if (keyInput.isCycleFocus() && delegate.isSingleLine()) {
      var handler = focusHandler();
      if (tabTarget != null && !keyInput.hasShiftDown() && handler != null) {
        handler.focus(tabTarget, FocusSource.KEYBOARD_CYCLE);
        return true;
      }
      return false; // unconsumed: the owo-ui root cycles focus in tree order
    }
    delegate.setFocused(true);
    boolean result = delegate.keyPressed(keyInput);
    if (onEdit != null) onEdit.run();
    return result;
  }

  @Override
  public boolean onCharTyped(CharacterEvent charInput) {
    delegate.setFocused(true);
    boolean result = delegate.charTyped(charInput);
    if (onEdit != null) onEdit.run();
    return result;
  }

  /**
   * The text typed after an unclosed {@code [[} before the cursor (the wiki-link being written), or
   * null when the cursor is not inside one.
   */
  public String wikiLinkQuery() {
    String text = delegate.getText();
    if (text == null || text.isEmpty()) return null;
    int cursorPos = delegate.getCursorAbsolute();
    if (cursorPos < 2) return null;
    // Find the last [[ before cursor
    int openBracket = text.lastIndexOf("[[", cursorPos - 1);
    if (openBracket < 0 || openBracket + 2 > cursorPos) return null;
    // Ensure there is no closing ]] between the [[ and cursor
    String afterOpen = text.substring(openBracket + 2, cursorPos);
    return afterOpen.contains("]]") ? null : afterOpen;
  }

  /** Screen x of the cursor. */
  public int cursorScreenX() {
    return this.x() + delegate.getCursorScreenX();
  }

  /** Screen y of the top of the cursor's line. */
  public int cursorScreenY() {
    return this.y() + delegate.getCursorScreenY();
  }

  @Override
  public boolean canFocus(FocusSource source) {
    return true;
  }

  @Override
  public void onFocusGained(FocusSource source) {
    super.onFocusGained(source);
    delegate.setFocused(true);
  }

  @Override
  public void onFocusLost() {
    super.onFocusLost();
    delegate.setFocused(false);
  }
}
