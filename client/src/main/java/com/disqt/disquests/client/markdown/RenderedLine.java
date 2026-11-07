package com.disqt.disquests.client.markdown;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * One rendered block of quest content: a line of styled text, or an image block when {@code
 * imageUrl} is set. For an image block {@code text} holds a bracketed placeholder ("[alt]") so
 * consumers that only know text (previews, HUD) still show something sensible.
 */
public record RenderedLine(
    MutableComponent text, int indent, float scale, String imageUrl, String imageAlt) {

  public static RenderedLine normal(MutableComponent text, int indent) {
    return new RenderedLine(text, indent, 1.0f, null, null);
  }

  public static RenderedLine heading(MutableComponent text, float scale) {
    return new RenderedLine(text, 0, scale, null, null);
  }

  public static RenderedLine empty() {
    return new RenderedLine(Component.empty().copy(), 0, 1.0f, null, null);
  }

  public static RenderedLine image(String url, String alt, int indent) {
    String label = alt == null || alt.isBlank() ? "image" : alt;
    MutableComponent text =
        Component.literal("[" + label + "]").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
    return new RenderedLine(text, indent, 1.0f, url, alt);
  }

  public boolean isImage() {
    return imageUrl != null;
  }
}
