package com.jvibe.provider;

import io.avaje.jsonb.Json;
import java.util.Map;

@Json
public record ThinkingOption(String id, String label, Map<String, Object> parameters) {
  public ThinkingOption {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }

  public static ThinkingOption of(String id, String label) {
    return new ThinkingOption(id, label, Map.of());
  }

  @Override public String toString() { return label; }
}
