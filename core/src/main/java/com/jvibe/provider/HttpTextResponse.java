package com.jvibe.provider;

public record HttpTextResponse(int statusCode, String body) {
  public boolean success() {
    return statusCode >= 200 && statusCode < 300;
  }
}
