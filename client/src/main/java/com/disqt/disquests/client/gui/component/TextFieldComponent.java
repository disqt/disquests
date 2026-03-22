package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

/**
 * Thin owo-ui wrapper around MultiLineTextFieldWidget.
 * Delegates all rendering and input to the underlying vanilla widget,
 * using graphics translation to match the owo-ui layout position.
 * Implements GreedyInputUIComponent so owo-ui routes key/char events to it.
 */
public class TextFieldComponent extends BaseUIComponent implements GreedyInputUIComponent {

    private final MultiLineTextFieldWidget delegate;
    private final int preferredWidth;
    private final int preferredHeight;

    // Offset from delegate's fixed position to owo-ui layout position
    private int offsetX;
    private int offsetY;

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
        computeOffset();
        // Translate graphics so delegate renders at owo-ui position
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(offsetX, offsetY);
        delegate.render((DrawContext) context, mouseX - offsetX, mouseY - offsetY, delta);
        context.getMatrices().popMatrix();
    }

    @Override
    public boolean onMouseDown(Click click, boolean doubled) {
        computeOffset();
        // Click is component-relative, delegate expects absolute coords
        double absX = click.x() + this.x() - offsetX;
        double absY = click.y() + this.y() - offsetY;
        Click delegateClick = new Click(absX, absY, click.buttonInfo());
        boolean result = delegate.mouseClicked(delegateClick, false);
        // Always force focus when clicked -- delegate.mouseClicked may not set it
        // if coordinate translation causes isMouseOver to fail
        delegate.setFocused(true);
        return result || super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseUp(Click click) {
        computeOffset();
        double absX = click.x() + this.x() - offsetX;
        double absY = click.y() + this.y() - offsetY;
        Click delegateClick = new Click(absX, absY, click.buttonInfo());
        return delegate.mouseReleased(delegateClick) || super.onMouseUp(click);
    }

    @Override
    public boolean onMouseDrag(Click click, double deltaX, double deltaY) {
        computeOffset();
        double absX = click.x() + this.x() - offsetX;
        double absY = click.y() + this.y() - offsetY;
        Click delegateClick = new Click(absX, absY, click.buttonInfo());
        return delegate.mouseDragged(delegateClick, deltaX, deltaY);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        computeOffset();
        double absX = mouseX + this.x() - offsetX;
        double absY = mouseY + this.y() - offsetY;
        return delegate.mouseScrolled(absX, absY, 0, amount);
    }

    @Override
    public boolean onKeyPress(KeyInput keyInput) {
        delegate.setFocused(true);
        return delegate.keyPressed(keyInput);
    }

    @Override
    public boolean onCharTyped(CharInput charInput) {
        delegate.setFocused(true);
        return delegate.charTyped(charInput);
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
