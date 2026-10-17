package com.jvibe.api;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Turns HTTP and server error bodies into one readable message. */
final class StreamErrors {
  private StreamErrors() {}

  static StreamEvent.Error httpError(int statusCode, Stream<String> lines) {
    try (lines) {
      return new StreamEvent.Error(httpMessage(statusCode, lines.limit(100).collect(Collectors.joining("\n"))));
    }
  }

  static String httpMessage(int statusCode, String body) {
    var message = message(body);
    return switch (statusCode) {
      case 401 -> "JVibe rejected the API key. Sign in again from the JVibe tab.";
      case 402 -> "Out of JVibe credits. Buy more at jvibe.net/account.";
      case 403 -> message;
      default -> statusCode == JdkHttpTransport.NETWORK_ERROR ? message : "JVibe HTTP " + statusCode + ": " + message;
    };
  }

  static String message(String body) {
    if (body == null || body.isBlank()) {
      return "empty response body";
    }
    try {
      var parsed = JsonSupport.object(body);
      for (var candidate : new String[] {
          JsonSupport.stringAt(parsed, "error", "message"),
          JsonSupport.string(parsed.get("message")),
          JsonSupport.string(parsed.get("error"))}) {
        if (!candidate.isBlank()) return candidate;
      }
    } catch (RuntimeException ignored) {
      // Not JSON; fall through to the raw body.
    }
    var normalized = body.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
  }
}
