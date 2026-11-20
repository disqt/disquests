package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.gui.helper.Colors;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The expand toggle left of a quest list entry that has subquests: a filled chevron pointing right
 * collapsed and down expanded, level with the entry's title, clickable over the whole slot ({@link
 * TreeRowGeometry#TOGGLE_WIDTH} wide, the entry's height). Its id is {@code toggle-<questId>}. An
 * inactive toggle (while a search forces the expansion) shows its state without a hover highlight
 * and ignores clicks.
 */
public class TreeToggleComponent extends BaseUIComponent {

  private final UUID questId;
  private final boolean expanded;
  private final boolean active;
  private final Consumer<UUID> onToggle;

  public TreeToggleComponent(
      UUID questId, boolean expanded, boolean active, Consumer<UUID> onToggle) {
    this.questId = questId;
    this.expanded = expanded;
    this.active = active;
    this.onToggle = onToggle;
    this.id("toggle-" + questId);
    this.sizing(
        Sizing.fixed(TreeRowGeometry.TOGGLE_WIDTH), Sizing.fixed(QuestEntryComponent.ENTRY_HEIGHT));
  }

  public UUID questId() {
    return questId;
  }

  public boolean isExpanded() {
    return expanded;
  }

  @Override
  protected int determineHorizontalContentSize(Sizing sizing) {
    return TreeRowGeometry.TOGGLE_WIDTH;
  }

  @Override
  protected int determineVerticalContentSize(Sizing sizing) {
    return QuestEntryComponent.ENTRY_HEIGHT;
  }

  @Override
  public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
    // Level with the title's capitals; the whole entry-high slot takes the click
    boolean hovered = active && isInBoundingBox(mouseX, mouseY);
    if (hovered) context.fill(x(), y(), x() + width(), y() + height(), Colors.ENTRY_HOVER);
    drawChevron(
        context,
        TreeRowGeometry.chevronX(x()),
        y() + QuestEntryLayout.TITLE_Y,
        expanded,
        chevronColor(active, hovered));
  }

  /** Primary text colour, slightly softened at rest and full on hover; muted while inactive. */
  public static int chevronColor(boolean active, boolean hovered) {
    if (!active) return Colors.TEXT_MUTED;
    return hovered ? Colors.TEXT_PRIMARY : (Colors.TEXT_PRIMARY & 0x00FFFFFF) | 0xC0000000;
  }

  /** The filled chevron of {@link TreeRowGeometry#chevron}, its box's top-left at (x, y). */
  public static void drawChevron(OwoUIGraphics context, int x, int y, boolean expanded, int color) {
    for (int[] r : TreeRowGeometry.chevron(expanded)) {
      context.fill(x + r[0], y + r[1], x + r[2], y + r[3], color);
    }
  }

  @Override
  public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
    if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
    if (active) onToggle.accept(questId);
    return true;
  }
}
