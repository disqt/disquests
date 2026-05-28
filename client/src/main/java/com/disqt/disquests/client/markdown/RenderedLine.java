package com.disqt.disquests.client.markdown;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

public record RenderedLine(MutableComponent text, int indent, float scale) {
  public static RenderedLine normal(MutableComponent text, int indent) {
    return new RenderedLine(text, indent, 1.0f);
  }

  public static RenderedLine heading(MutableComponent text, float scale) {
    return new RenderedLine(text, 0, scale);
  }

  public static RenderedLine empty() {
    return new RenderedLine(Component.empty().copy(), 0, 1.0f);
  }
}
