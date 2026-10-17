package com.jvibe.api;

import java.util.Locale;

/** Result of checking the stored API key against the JVibe service. */
public record AccountStatus(State state, String email, double credits, String message) {
  public enum State {
    SIGNED_OUT,
    PENDING,
    ACTIVE,
    INVALID,
    UNAVAILABLE
  }

  static AccountStatus of(State state, String message) {
    return new AccountStatus(state, "", 0, message);
  }

  /** One-line summary for the settings screen. */
  public String summary() {
    return switch (state) {
      case ACTIVE -> "Signed in" + (email.isBlank() ? "" : " as " + email) + " · " + creditsLabel() + " credits";
      case SIGNED_OUT -> "Not signed in";
      default -> message;
    };
  }

  private String creditsLabel() {
    return Math.rint(credits) == credits
        ? Long.toString((long) credits)
        : String.format(Locale.US, "%.2f", credits);
  }
}
