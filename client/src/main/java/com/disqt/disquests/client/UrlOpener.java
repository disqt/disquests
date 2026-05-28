package com.disqt.disquests.client;

import java.net.URI;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens URLs in the system browser, preserving URI fragments on Windows.
 *
 * <p>Windows' rundll32 url.dll,FileProtocolHandler strips the #fragment from URIs (JDK-7073184).
 * This utility uses {@code cmd /c start "" url} on Windows instead, which preserves fragments.
 */
public class UrlOpener {

  private static final Logger LOGGER = LoggerFactory.getLogger("Disquests.UrlOpener");

  public static void open(String url) {
    try {
      if (Util.getPlatform() == Util.OS.WINDOWS) {
        String safeUrl = url.replace("'", "''");
        new ProcessBuilder(
                "powershell", "-NoProfile", "-Command", "Start-Process '" + safeUrl + "'")
            .redirectErrorStream(true)
            .start();
      } else {
        Util.getPlatform().open(URI.create(url));
      }
    } catch (Exception e) {
      LOGGER.error("Failed to open URL: {}", url, e);
    }
  }
}
