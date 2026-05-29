package com.disqt.disquests.client.mixin;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.screen.MainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class InventoryBadgeMixin extends Screen {

  @Unique private static final int BADGE_SIZE = 16;
  @Unique private static final int BADGE_MARGIN = 4;

  protected InventoryBadgeMixin() {
    super(null);
  }

  @Inject(method = "render", at = @At("TAIL"))
  private void renderQuestBadge(
      GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    if (!ClientSession.isOnServer()) return;

    int badgeX = this.width - BADGE_SIZE - BADGE_MARGIN;
    int badgeY = BADGE_MARGIN;

    // Draw badge background
    context.fill(badgeX, badgeY, badgeX + BADGE_SIZE, badgeY + BADGE_SIZE, 0xCC222222);

    // Draw "Q" centered in badge
    Font tr = Minecraft.getInstance().font;
    String label = "Q";
    int textWidth = tr.width(label);
    int textX = badgeX + (BADGE_SIZE - textWidth) / 2;
    int textY = badgeY + (BADGE_SIZE - tr.lineHeight) / 2;
    context.text(tr, label, textX, textY, 0xFFFFFFFF, false);

    // Draw notification count if pending requests
    int pendingCount = ClientSession.getPendingRequestCount();
    if (pendingCount > 0) {
      String countStr = pendingCount > 99 ? "99+" : String.valueOf(pendingCount);
      int countWidth = tr.width(countStr);
      int dotSize = Math.max(countWidth + 4, 10);
      int dotX = badgeX + BADGE_SIZE - dotSize / 2;
      int dotY = badgeY - dotSize / 2 + 2;

      context.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, Colors.BADGE_RED);
      int countTextX = dotX + (dotSize - countWidth) / 2;
      int countTextY = dotY + (dotSize - tr.lineHeight) / 2;
      context.text(tr, countStr, countTextX, countTextY, 0xFFFFFFFF, false);
    }
  }

  @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
  private void onMouseClicked(
      MouseButtonEvent click, boolean simulated, CallbackInfoReturnable<Boolean> cir) {
    if (!ClientSession.isOnServer()) return;

    double mouseX = click.x();
    double mouseY = click.y();
    int badgeX = this.width - BADGE_SIZE - BADGE_MARGIN;
    int badgeY = BADGE_MARGIN;

    if (mouseX >= badgeX
        && mouseX < badgeX + BADGE_SIZE
        && mouseY >= badgeY
        && mouseY < badgeY + BADGE_SIZE) {
      Minecraft.getInstance().setScreen(new MainScreen());
      cir.setReturnValue(true);
    }
  }
}
