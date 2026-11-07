package com.disqt.disquests.client.image;

/** Fits an image into the available content width without upscaling, keeping its aspect ratio. */
public final class ImageLayout {

  private ImageLayout() {}

  /** Returns {@code {drawWidth, drawHeight}} in GUI units. */
  public static int[] fit(int naturalW, int naturalH, int availableW) {
    int drawW = Math.min(naturalW, Math.max(1, availableW));
    int drawH = Math.max(1, Math.round((float) drawW * naturalH / naturalW));
    return new int[] {drawW, drawH};
  }
}
