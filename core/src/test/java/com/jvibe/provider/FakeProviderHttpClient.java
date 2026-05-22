package com.jvibe.provider;

import java.net.URI;
import java.util.List;
import java.util.Map;

final class FakeProviderHttpClient implements ProviderHttpClient {
  String lastJson;
  URI lastUri;
  HttpTextResponse getResponse = new HttpTextResponse(200, "{\"data\":[]}");
  int streamStatusCode = 200;
  List<String> streamLines = List.of();

  @Override
  public HttpTextResponse get(URI uri, Map<String, String> headers) {
    lastUri = uri;
    return getResponse;
  }

  @Override
  public HttpTextResponse post(URI uri, Map<String, String> headers, String json) {
    lastUri = uri;
    lastJson = json;
    return new HttpTextResponse(200, "{}");
  }

  @Override
  public HttpStreamResponse postStream(URI uri, Map<String, String> headers, String json) {
    lastUri = uri;
    lastJson = json;
    return new HttpStreamResponse(streamStatusCode, streamLines.stream());
  }
}
