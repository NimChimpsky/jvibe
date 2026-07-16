package com.jvibe.tool.builtin;

import java.util.Map;

final class ToolArguments {
  private ToolArguments() {}

  static String string(Map<String, Object> arguments, String key) {
    var value = arguments.get(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing argument: " + key);
    }
    return value.toString();
  }

  static String string(Map<String, Object> arguments, String key, String fallback) {
    var value = arguments.get(key);
    return value == null ? fallback : value.toString();
  }

  static boolean bool(Map<String, Object> arguments, String key, boolean fallback) {
    var value = arguments.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.parseBoolean(value.toString());
  }

  static int integer(Map<String, Object> arguments, String key, int fallback) {
    var value = arguments.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Argument must be an integer: " + key);
    }
  }
}
