package com.disqt.disquests.client.mixin;

import com.disqt.disquests.client.hud.HudPinRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {

  // MC 26.2 dropped the GuiGraphicsExtractor parameter from extractRenderState. The Gui now owns
  // the GuiRenderState, and an extractor is built around it on demand.
  @Shadow @Final private GuiRenderState guiRenderState;

  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void renderPinnedQuest(
      DeltaTracker tickCounter, boolean renderHud, boolean renderOverlay, CallbackInfo ci) {
    Minecraft minecraft = Minecraft.getInstance();
    GuiGraphicsExtractor context =
        new GuiGraphicsExtractor(
            minecraft,
            this.guiRenderState,
            minecraft.getWindow().getGuiScaledWidth(),
            minecraft.getWindow().getGuiScaledHeight());
    HudPinRenderer.render(context);
  }
}
