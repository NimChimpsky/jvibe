package com.jvibe.api;

import io.avaje.jsonb.Json;

/** The signed-in JVibe identity stored in {@code ~/.jvibe/account.json}. */
@Json
public record Account(String email, String apiKey, String approvalCode) {
  public static final Account NONE = new Account("", "", "");

  public Account {
    email = email == null ? "" : email.trim();
    apiKey = apiKey == null ? "" : apiKey.trim();
    approvalCode = approvalCode == null ? "" : approvalCode.trim();
  }

  public boolean hasApiKey() {
    return !apiKey.isBlank();
  }

  @Override
  public String toString() {
    return "Account[email=" + email + ", apiKey=" + (hasApiKey() ? "[REDACTED]" : "") + "]";
  }
}
