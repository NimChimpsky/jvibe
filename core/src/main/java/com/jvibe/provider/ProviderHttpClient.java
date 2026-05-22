package com.jvibe.provider;

import java.net.URI;
import java.util.Map;

public interface ProviderHttpClient {
  HttpTextResponse get(URI uri, Map<String, String> headers);

  HttpTextResponse post(URI uri, Map<String, String> headers, String json);

  HttpStreamResponse postStream(URI uri, Map<String, String> headers, String json);
}
