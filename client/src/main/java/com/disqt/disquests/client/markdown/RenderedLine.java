package com.disqt.disquests.client.markdown;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public record RenderedLine(MutableText text, int indent, float scale) {
  public static RenderedLine normal(MutableText text, int indent) {
    return new RenderedLine(text, indent, 1.0f);
  }

  public static RenderedLine heading(MutableText text, float scale) {
    return new RenderedLine(text, 0, scale);
  }

  public static RenderedLine empty() {
    return new RenderedLine(Text.empty().copy(), 0, 1.0f);
  }
}
