package com.disqt.disquests.client.net;

import java.io.IOException;

/** Blocking HTTP GET abstraction, faked in tests. */
public interface TileFetcher {
  FetchResult fetch(String url) throws IOException;
}
