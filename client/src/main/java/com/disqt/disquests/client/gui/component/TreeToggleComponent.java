package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.gui.helper.Colors;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The expand toggle left of a quest list entry that has subquests: "▸" collapsed, "▾" expanded,
 * level with the entry's title. Its id is {@code toggle-<questId>}.
 */
public class TreeToggleComponent extends BaseUIComponent {

  private final UUID questId;
  private final boolean expanded;
  private final Consumer<UUID> onToggle;

  public TreeToggleComponent(UUID questId, boolean expanded, Consumer<UUID> onToggle) {
    this.questId = questId;
    this.expanded = expanded;
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
    context.text(
        Minecraft.getInstance().font,
        expanded ? "▾" : "▸",
        x() + 1,
        y() + QuestEntryLayout.TITLE_Y,
        isInBoundingBox(mouseX, mouseY) ? Colors.TEXT_PRIMARY : Colors.TEXT_MUTED,
        false);
  }

  @Override
  public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
    if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
    onToggle.accept(questId);
    return true;
  }
}
