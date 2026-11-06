package com.disqt.disquests.client.image;

/** What the {@link ImageCache} currently knows about a URL. */
public sealed interface ImageState
    permits ImageState.Loading, ImageState.Loaded, ImageState.Failed {

  /** A fetch is in flight (or was just started by this call). */
  record Loading() implements ImageState {}

  /** The image is decoded and registered as a texture under {@code textureKey}. */
  record Loaded(String textureKey, int width, int height) implements ImageState {}

  /**
   * The fetch or decode failed; memoised for the session. Reason starts with "fetch" or "decode".
   */
  record Failed(String reason) implements ImageState {}
}
