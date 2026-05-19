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
}
