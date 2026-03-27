package com.disqt.disquests.client.gui.helper;

import io.wispforest.owo.ui.core.Surface;

public enum Theme {
  VANILLA(
      "Vanilla",
      0x77000000,
      0xFFFFFFFF,
      0xFFCCCCCC,
      0xFF888888,
      0x8855AADD,
      0xFFFFFFFF,
      0x44000000,
      0xAA000000,
      0x44000000,
      0x88FFFFFF,
      0xFFFFFFFF,
      0xFF101010,
      0xFF101010,
      0x60000000,
      0x00000000,
      0xFFFFAA33,
      0x22FFFFFF,
      0x44FFFFFF,
      0x00000000,
      0x00000000),

  FLAT(
      "Flat",
      0xFF1A1A1A,
      0xFFE0E0E0,
      0xFF777777,
      0xFF555555,
      0x6650AACC,
      0xFFE0E0E0,
      0xFF1A1A1A,
      0xFF2A2A2A,
      0xFF1A1A1A,
      0xFF555555,
      0xFFAAAAAA,
      0xFF0E0E0E,
      0xFF0E0E0E,
      0x600E0E0E,
      0x000E0E0E,
      0xFFFFAA33,
      0xFF222222,
      0xFF2A2A2A,
      0x00000000,
      0x00000000),

  INSET(
      "Inset",
      0xFF141414,
      0xFFDDDDDD,
      0xFF888888,
      0xFF555555,
      0x7755AADD,
      0xFFDDDDDD,
      0xFF1A1A1A,
      0xFF2A2A2A,
      0xFF1A1A1A,
      0xFF555555,
      0xFFBBBBBB,
      0xFF181818,
      0xFF181818,
      0x60181818,
      0x00181818,
      0xFFFFAA33,
      0x15FFFFFF,
      0x22FFFFFF,
      0x00000000,
      0x00000000),

  FROSTED(
      "Frosted",
      0xB30F0F14,
      0xFFEEEEEE,
      0xFF999999,
      0xFF666666,
      0x7755AADD,
      0xFFEEEEEE,
      0x33000000,
      0x55000000,
      0x33000000,
      0x66FFFFFF,
      0xCCFFFFFF,
      0x00000000,
      0x00000000,
      0x40000000,
      0x00000000,
      0xFFFFBB55,
      0x0AFFFFFF,
      0x17FFFFFF,
      0x00000000,
      0x00000000),

  ACCENT_LINE(
      "Accent Line",
      0xFF161616,
      0xFFE0E0E0,
      0xFF777777,
      0xFF555555,
      0x6655AACC,
      0xFFE0E0E0,
      0xFF1A1A1A,
      0xFF252525,
      0xFF1A1A1A,
      0xFF555555,
      0xFFAAAAAA,
      0xFF0C0C0C,
      0xFF0C0C0C,
      0x600C0C0C,
      0x000C0C0C,
      0xFFFFAA33,
      0xFF1E1E1E,
      0xFF222222,
      0xFFFFAA33,
      0xFF333333);

  private final String displayName;
  private final int panelBackground, textPrimary, textMuted, textDisabled;
  private final int selectionBackground, caretPrimary, buttonDisabled, buttonHover;
  private final int tabInactive, scrollbarThumbInactive, scrollbarThumbActive;
  private final int gradientStart, gradientEnd, fadeGradientTop, fadeGradientBottom;
  private final int amber, entryHover, entrySelected;
  private final int accentLineActive, accentLineInactive;

  Theme(
      String displayName,
      int panelBackground,
      int textPrimary,
      int textMuted,
      int textDisabled,
      int selectionBackground,
      int caretPrimary,
      int buttonDisabled,
      int buttonHover,
      int tabInactive,
      int scrollbarThumbInactive,
      int scrollbarThumbActive,
      int gradientStart,
      int gradientEnd,
      int fadeGradientTop,
      int fadeGradientBottom,
      int amber,
      int entryHover,
      int entrySelected,
      int accentLineActive,
      int accentLineInactive) {
    this.displayName = displayName;
    this.panelBackground = panelBackground;
    this.textPrimary = textPrimary;
    this.textMuted = textMuted;
    this.textDisabled = textDisabled;
    this.selectionBackground = selectionBackground;
    this.caretPrimary = caretPrimary;
    this.buttonDisabled = buttonDisabled;
    this.buttonHover = buttonHover;
    this.tabInactive = tabInactive;
    this.scrollbarThumbInactive = scrollbarThumbInactive;
    this.scrollbarThumbActive = scrollbarThumbActive;
    this.gradientStart = gradientStart;
    this.gradientEnd = gradientEnd;
    this.fadeGradientTop = fadeGradientTop;
    this.fadeGradientBottom = fadeGradientBottom;
    this.amber = amber;
    this.entryHover = entryHover;
    this.entrySelected = entrySelected;
    this.accentLineActive = accentLineActive;
    this.accentLineInactive = accentLineInactive;
  }

  public String displayName() {
    return displayName;
  }

  public void applyColors() {
    Colors.PANEL_BACKGROUND = panelBackground;
    Colors.TEXT_PRIMARY = textPrimary;
    Colors.TEXT_MUTED = textMuted;
    Colors.TEXT_DISABLED = textDisabled;
    Colors.SELECTION_BACKGROUND = selectionBackground;
    Colors.CARET_PRIMARY = caretPrimary;
    Colors.BUTTON_DISABLED = buttonDisabled;
    Colors.BUTTON_HOVER = buttonHover;
    Colors.TAB_INACTIVE = tabInactive;
    Colors.SCROLLBAR_THUMB_INACTIVE = scrollbarThumbInactive;
    Colors.SCROLLBAR_THUMB_ACTIVE = scrollbarThumbActive;
    Colors.GRADIENT_START = gradientStart;
    Colors.GRADIENT_END = gradientEnd;
    Colors.FADE_GRADIENT_TOP = fadeGradientTop;
    Colors.FADE_GRADIENT_BOTTOM = fadeGradientBottom;
    Colors.AMBER = amber;
    Colors.ENTRY_HOVER = entryHover;
    Colors.ENTRY_SELECTED = entrySelected;
    Colors.ACCENT_LINE_ACTIVE = accentLineActive;
    Colors.ACCENT_LINE_INACTIVE = accentLineInactive;
  }

  public Surface rootSurface() {
    return switch (this) {
      case VANILLA -> Surface.VANILLA_TRANSLUCENT;
      case FLAT -> Surface.flat(0xC00E0E0E);
      case INSET -> Surface.flat(0xC0181818);
      case FROSTED -> Surface.BLANK;
      case ACCENT_LINE -> Surface.flat(0xC00C0C0C);
    };
  }

  public Surface panelSurface() {
    return switch (this) {
      case VANILLA -> Surface.DARK_PANEL;
      case FLAT -> Surface.flat(0xFF1A1A1A);
      case INSET -> Surface.panelWithInset(2);
      case FROSTED -> frostedPanelSurface();
      case ACCENT_LINE -> accentLinePanelSurface();
    };
  }

  private static Surface frostedPanelSurface() {
    return Surface.blur(10f, 6f).and(Surface.flat(0xB30F0F14)).and(Surface.outline(0x14FFFFFF));
  }

  private static Surface accentLinePanelSurface() {
    return (context, component) -> {
      int x = component.x();
      int y = component.y();
      int w = component.width();
      int h = component.height();
      context.fill(x, y, x + w, y + h, 0xFF161616);
      context.fill(x, y, x + 3, y + h, Colors.AMBER);
    };
  }

  public Theme next() {
    Theme[] values = values();
    return values[(ordinal() + 1) % values.length];
  }
}
