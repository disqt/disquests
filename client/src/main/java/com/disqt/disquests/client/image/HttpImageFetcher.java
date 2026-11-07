package com.disqt.disquests.client.image;

import com.disqt.disquests.client.markdown.ImageLinkDetector;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetches image bytes over https (plain http only under the test flag, see {@link
 * ImageLinkDetector#allowHttp()}). One deadline covers headers and body, so a server that trickles
 * or withholds the body cannot hold an IO thread. Rejects non-200 answers, non-image content types
 * and bodies over {@code maxBytes} (the body subscriber cancels as soon as the cap is crossed).
 * Exception messages start with "scheme", "status", "content-type", "too large" or "timeout" so the
 * cache can keep a short reason.
 */
public final class HttpImageFetcher implements ImageFetcher {

  public static final long DEFAULT_MAX_BYTES = 8L * 1024 * 1024;
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final HttpClient CLIENT =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(DEFAULT_TIMEOUT)
          .build();

  private final long maxBytes;
  private final Duration timeout;

  public HttpImageFetcher() {
    this(DEFAULT_MAX_BYTES, DEFAULT_TIMEOUT);
  }

  public HttpImageFetcher(long maxBytes) {
    this(maxBytes, DEFAULT_TIMEOUT);
  }

  public HttpImageFetcher(long maxBytes, Duration timeout) {
    this.maxBytes = maxBytes;
    this.timeout = timeout;
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
            .timeout(timeout)
            .header("User-Agent", "Disquests")
            .GET()
            .build();
    CompletableFuture<HttpResponse<byte[]>> future =
        CLIENT.sendAsync(request, info -> new CappedBodySubscriber(maxBytes));
    HttpResponse<byte[]> response;
    try {
      response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new IOException("timeout after " + timeout.toMillis() + " ms");
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof java.net.http.HttpTimeoutException) {
        throw new IOException("timeout: " + cause.getMessage());
      }
      if (cause instanceof IOException io) throw io;
      throw new IOException(cause == null ? e.getMessage() : cause.getMessage(), cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      throw new IOException("interrupted", e);
    }
    if (response.statusCode() != 200) {
      throw new IOException("status " + response.statusCode());
    }
    String type = response.headers().firstValue("Content-Type").orElse("");
    if (!type.toLowerCase(Locale.ROOT).startsWith("image/")) {
      throw new IOException("content-type " + type);
    }
    return response.body();
  }

  /** Accumulates the body and cancels the download the moment it exceeds {@code max} bytes. */
  static final class CappedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final long max;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;
    private long total;

    CappedBodySubscriber(long max) {
      this.max = max;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
      return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> items) {
      for (ByteBuffer buffer : items) {
        total += buffer.remaining();
        if (total > max) {
          subscription.cancel();
          result.completeExceptionally(new IOException("too large: over " + max + " bytes"));
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        out.write(chunk, 0, chunk.length);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      result.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
      result.complete(out.toByteArray());
    }
  }
}
