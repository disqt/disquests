package com.disqt.disquests.client.markdown;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a markdown link destination is an image we render inline. Pure Java (no Minecraft
 * types) so it stays unit-testable in the client {@code test} source set.
 *
 * <p>Only https URLs qualify in production. Plain http is accepted only when the JVM property
 * {@code disquests.test.allowHttp} is true, which the gametest run configs set so the E2E journey
 * can serve a fixture image from a local server.
 */
public final class ImageLinkDetector {

  // webp is left out on purpose: stb_image cannot decode it, so it would always show as failed.
  private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif");
  private static volatile boolean allowHttp = Boolean.getBoolean("disquests.test.allowHttp");

  private ImageLinkDetector() {}

  /** True when plain http image links are accepted (test harness only). */
  public static boolean allowHttp() {
    return allowHttp;
  }

  public static void setAllowHttpForTests(boolean value) {
    allowHttp = value;
  }

  /**
   * True when {@code destination} is an https (or allowed http) URL whose path ends in an image
   * extension.
   */
  public static boolean isImageUrl(String destination) {
    if (destination == null || destination.isEmpty()) return false;
    URI uri;
    try {
      uri = URI.create(destination);
    } catch (IllegalArgumentException e) {
      return false;
    }
    String scheme = uri.getScheme();
    if (scheme == null) return false;
    scheme = scheme.toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !(scheme.equals("http") && allowHttp)) return false;
    String path = uri.getPath();
    if (path == null) return false;
    int dot = path.lastIndexOf('.');
    if (dot < 0 || dot == path.length() - 1) return false;
    return IMAGE_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
  }

  /** A bare image link: an image URL whose visible text is the URL itself (autolinked). */
  public static boolean isBareImageLink(String destination, String linkText) {
    if (!isImageUrl(destination)) return false;
    return linkText != null && linkText.trim().equals(destination.trim());
  }
}
