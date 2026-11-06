package com.disqt.disquests.client.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HttpFetcherTest {
  @Test
  void isAllowedUrl_httpsOnly_unlessTestProperty() {
    String prop = "disquests.test.allowHttp";
    String old = System.getProperty(prop);
    try {
      System.clearProperty(prop);
      assertTrue(HttpFetcher.isAllowedUrl("https://x/y"));
      assertFalse(HttpFetcher.isAllowedUrl("http://x/y"));
      assertFalse(HttpFetcher.isAllowedUrl("ftp://x/y"));
      assertFalse(HttpFetcher.isAllowedUrl(null));
      System.setProperty(prop, "true");
      assertTrue(HttpFetcher.isAllowedUrl("http://x/y"));
    } finally {
      if (old == null) {
        System.clearProperty(prop);
      } else {
        System.setProperty(prop, old);
      }
    }
  }

  @Test
  void parseMaxAge_parsesDirective_defaults86400() {
    assertEquals(86400, HttpFetcher.parseMaxAge("public, max-age=86400"));
    assertEquals(60, HttpFetcher.parseMaxAge("max-age=60, immutable"));
    assertEquals(86400, HttpFetcher.parseMaxAge(null));
    assertEquals(86400, HttpFetcher.parseMaxAge("no-cache"));
    assertEquals(86400, HttpFetcher.parseMaxAge("max-age=abc"));
  }
}
