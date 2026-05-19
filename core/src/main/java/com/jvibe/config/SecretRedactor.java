package com.jvibe.config;

import java.util.regex.Pattern;

public final class SecretRedactor {
  private static final Pattern API_KEY = Pattern.compile("(?i)(sk-[a-z0-9_-]{12,}|api[_-]?key\\s*[:=]\\s*)\\S+");

  private SecretRedactor() {}

  public static String redact(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    return API_KEY.matcher(value).replaceAll("$1[REDACTED]");
  }
}
