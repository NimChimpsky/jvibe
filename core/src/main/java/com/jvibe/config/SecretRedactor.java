package com.jvibe.config;

public final class SecretRedactor {

  private SecretRedactor() {}

  public static String redact(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    return "[REDACTED]";
  }
}
