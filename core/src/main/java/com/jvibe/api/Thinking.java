package com.jvibe.api;

import java.util.Locale;

/** Reasoning effort requested from the JVibe service. */
public enum Thinking {
  NONE("None"),
  LOW("Low"),
  MEDIUM("Medium"),
  HIGH("High");

  private final String label;

  Thinking(String label) {
    this.label = label;
  }

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Thinking fromId(String id) {
    for (var value : values()) {
      if (value.id().equalsIgnoreCase(id)) return value;
    }
    return MEDIUM;
  }

  @Override
  public String toString() {
    return label;
  }
}
