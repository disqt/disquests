package com.disqt.disquests.client.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JDK HttpClient fetcher with redirects, timeouts and a response size cap. */
public final class HttpFetcher implements TileFetcher {
  static final long DEFAULT_MAX_AGE = 86400;
  private static final Pattern MAX_AGE = Pattern.compile("max-age\\s*=\\s*(\\d+)");
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient client =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(TIMEOUT)
          .build();
  private final long maxBytes;

  public HttpFetcher(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  public static boolean isAllowedUrl(String url) {
    if (url == null) {
      return false;
    }
    String lower = url.toLowerCase(Locale.ROOT);
    return lower.startsWith("https://")
        || (lower.startsWith("http://") && Boolean.getBoolean("disquests.test.allowHttp"));
  }

  public static long parseMaxAge(String cacheControl) {
    if (cacheControl == null) {
      return DEFAULT_MAX_AGE;
    }
    Matcher m = MAX_AGE.matcher(cacheControl.toLowerCase(Locale.ROOT));
    if (!m.find()) {
      return DEFAULT_MAX_AGE;
    }
    try {
      return Long.parseLong(m.group(1));
    } catch (NumberFormatException e) {
      return DEFAULT_MAX_AGE;
    }
  }

  @Override
  public FetchResult fetch(String url) throws IOException {
    if (!isAllowedUrl(url)) {
      throw new IOException("URL not allowed: " + url);
    }
    try {
      HttpRequest request;
      try {
        request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
      } catch (IllegalArgumentException e) {
        throw new IOException("Bad URL: " + url, e);
      }
      HttpResponse<byte[]> response = client.send(request, this::bodyHandler);
      byte[] body = response.body();
      if (body == null || body.length > maxBytes) {
        throw new IOException("Response too large: " + url);
      }
      String contentType = response.headers().firstValue("Content-Type").orElse("");
      long maxAge = parseMaxAge(response.headers().firstValue("Cache-Control").orElse(null));
      return new FetchResult(response.statusCode(), body, contentType, maxAge);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted fetching " + url, e);
    }
  }

  /** Rejects oversized responses by Content-Length before reading; a null body marks rejection. */
  private HttpResponse.BodySubscriber<byte[]> bodyHandler(HttpResponse.ResponseInfo info) {
    long length = info.headers().firstValueAsLong("Content-Length").orElse(-1);
    if (length > maxBytes) {
      return HttpResponse.BodySubscribers.replacing(null);
    }
    return HttpResponse.BodySubscribers.ofByteArray();
  }
}
