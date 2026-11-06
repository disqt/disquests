package com.disqt.disquests.client.image;

import com.disqt.disquests.client.markdown.ImageLinkDetector;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Fetches image bytes over https (plain http only under the test flag, see {@link
 * ImageLinkDetector#allowHttp()}). Rejects non-200 answers, non-image content types and bodies over
 * {@code maxBytes}. Exception messages start with "scheme", "status", "content-type" or "too large"
 * so the cache can keep a short reason.
 */
public final class HttpImageFetcher implements ImageFetcher {

  public static final long DEFAULT_MAX_BYTES = 8L * 1024 * 1024;
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final HttpClient CLIENT =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(TIMEOUT)
          .build();

  private final long maxBytes;

  public HttpImageFetcher() {
    this(DEFAULT_MAX_BYTES);
  }

  public HttpImageFetcher(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  public byte[] fetch(String url) throws IOException {
    URI uri = URI.create(url);
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !(scheme.equals("http") && ImageLinkDetector.allowHttp())) {
      throw new IOException("scheme not allowed: " + scheme);
    }
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(TIMEOUT)
            .header("User-Agent", "Disquests")
            .GET()
            .build();
    HttpResponse<InputStream> response;
    try {
      response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted", e);
    }
    try (InputStream body = response.body()) {
      if (response.statusCode() != 200) {
        throw new IOException("status " + response.statusCode());
      }
      String type = response.headers().firstValue("Content-Type").orElse("");
      if (!type.toLowerCase(Locale.ROOT).startsWith("image/")) {
        throw new IOException("content-type " + type);
      }
      OptionalLong length = response.headers().firstValueAsLong("Content-Length");
      if (length.isPresent() && length.getAsLong() > maxBytes) {
        throw new IOException("too large: " + length.getAsLong() + " bytes");
      }
      return readCapped(body);
    }
  }

  private byte[] readCapped(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    long total = 0;
    int n;
    while ((n = in.read(buf)) > 0) {
      total += n;
      if (total > maxBytes) throw new IOException("too large: over " + maxBytes + " bytes");
      out.write(buf, 0, n);
    }
    return out.toByteArray();
  }
}
