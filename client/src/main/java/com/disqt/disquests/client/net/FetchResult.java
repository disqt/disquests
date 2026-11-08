package com.disqt.disquests.client.net;

/** Result of an HTTP GET: status, body, Content-Type header and Cache-Control max-age. */
public record FetchResult(int status, byte[] body, String contentType, long maxAgeSeconds) {}
