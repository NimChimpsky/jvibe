package com.jvibe.api;

import java.net.URI;
import java.util.List;
import java.util.Map;

final class FakeHttpTransport implements HttpTransport {
  URI lastUri;
  String lastJson;
  Map<String, String> lastHeaders = Map.of();
  HttpTextResponse getResponse = new HttpTextResponse(200, "{}");
  HttpTextResponse postResponse = new HttpTextResponse(200, "{}");
  int streamStatusCode = 200;
  List<String> streamLines = List.of();

  @Override
  public HttpTextResponse get(URI uri, Map<String, String> headers) {
    lastUri = uri;
    lastHeaders = headers;
    return getResponse;
  }

  @Override
  public HttpTextResponse post(URI uri, Map<String, String> headers, String json) {
    lastUri = uri;
    lastHeaders = headers;
    lastJson = json;
    return postResponse;
  }

  @Override
  public HttpStreamResponse postStream(URI uri, Map<String, String> headers, String json) {
    lastUri = uri;
    lastHeaders = headers;
    lastJson = json;
    return new HttpStreamResponse(streamStatusCode, streamLines.stream());
  }
}
