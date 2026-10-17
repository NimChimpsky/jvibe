package com.jvibe.api;

import java.util.stream.Stream;

public record HttpStreamResponse(int statusCode, Stream<String> lines) {
  public boolean success() {
    return statusCode >= 200 && statusCode < 300;
  }
}
