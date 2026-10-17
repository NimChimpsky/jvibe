package com.jvibe.api;

import java.net.URI;
import java.util.Map;

/** HTTP seam for the JVibe client so tests can replace the network. */
public interface HttpTransport {
  HttpTextResponse get(URI uri, Map<String, String> headers);

  HttpTextResponse post(URI uri, Map<String, String> headers, String json);

  HttpStreamResponse postStream(URI uri, Map<String, String> headers, String json);
}
