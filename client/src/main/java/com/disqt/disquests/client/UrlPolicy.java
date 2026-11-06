package com.disqt.disquests.client;

import java.net.URI;
import java.util.Locale;

/**
 * Which link destinations may be handed to the system browser. Quest content is written by other
 * players, so only http and https URLs are ever opened; anything else (a bare program name, a UNC
 * path, file:, search-ms:, javascript:) is refused. Pure Java so it is unit-tested.
 */
public final class UrlPolicy {

  private UrlPolicy() {}

  public static boolean isOpenable(String url) {
    if (url == null || url.isEmpty()) return false;
    URI uri;
    try {
      uri = new URI(url);
    } catch (Exception e) {
      return false;
    }
    String scheme = uri.getScheme();
    if (scheme == null) return false;
    scheme = scheme.toLowerCase(Locale.ROOT);
    return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
  }
}
