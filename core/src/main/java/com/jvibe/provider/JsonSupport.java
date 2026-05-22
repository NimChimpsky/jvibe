package com.jvibe.provider;

import io.avaje.jsonb.Jsonb;
import java.util.List;
import java.util.Map;

final class JsonSupport {
  private static final Jsonb JSONB = Jsonb.instance();

  private JsonSupport() {}

  static String toJson(Object value) {
    return JSONB.toJson(value);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> object(String json) {
    var parsed = JSONB.type(Object.class).fromJson(json);
    return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  @SuppressWarnings("unchecked")
  static List<Object> list(Object value) {
    return value instanceof List<?> list ? (List<Object>) list : List.of();
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  static String string(Object value) {
    return value == null ? "" : value.toString();
  }

  static String stringAt(Map<String, Object> map, String... path) {
    Object current = map;
    for (var part : path) {
      if (!(current instanceof Map<?, ?> currentMap)) {
        return "";
      }
      current = currentMap.get(part);
    }
    return string(current);
  }

  static int intAt(Map<String, Object> map, int fallback, String... path) {
    Object current = map;
    for (var part : path) {
      if (!(current instanceof Map<?, ?> currentMap)) {
        return fallback;
      }
      current = currentMap.get(part);
    }
    if (current instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(string(current));
    } catch (RuntimeException e) {
      return fallback;
    }
  }

  static Map<String, Object> parseArguments(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return object(json);
    } catch (RuntimeException e) {
      return Map.of("raw", json);
    }
  }
}
