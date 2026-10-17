package com.jvibe.provider;

import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ProviderStreamErrors {
  private ProviderStreamErrors() {}

  static ProviderEvent.Error httpError(int statusCode, Stream<String> lines) {
    return new ProviderEvent.Error("Provider HTTP " + statusCode + ": " + message(lines), true);
  }

  static ProviderEvent.Error emptyResponse(String provider, String rawBody) {
    var suffix = rawBody == null || rawBody.isBlank() ? "" : ": " + message(rawBody);
    return new ProviderEvent.Error(provider + " returned no streaming response data" + suffix, true);
  }

  static String message(Stream<String> lines) {
    try (lines) {
      return message(lines.limit(100).collect(Collectors.joining("\n")));
    }
  }

  static String message(String body) {
    if (body == null || body.isBlank()) {
      return "empty response body";
    }
    try {
      var parsed = JsonSupport.object(body);
      var error = JsonSupport.stringAt(parsed, "error", "message");
      if (!error.isBlank()) {
        return error;
      }
      var message = JsonSupport.string(parsed.get("message"));
      if (!message.isBlank()) {
        return message;
      }
      var errorValue = JsonSupport.string(parsed.get("error"));
      if (!errorValue.isBlank()) {
        return errorValue;
      }
    } catch (RuntimeException ignored) {
      // Fall through to the raw body. Provider errors are often plain text.
    }
    return truncate(body);
  }

  private static String truncate(String value) {
    var normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
  }
}
