package com.jvibe.tool.builtin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ToolSchemas {
  private ToolSchemas() {}

  static Map<String, Object> object() {
    return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
  }

  static Map<String, Object> object(Map<String, Object> properties, String... required) {
    var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", properties);
    if (required.length > 0) {
      schema.put("required", List.of(required));
    }
    schema.put("additionalProperties", false);
    return schema;
  }

  static Map<String, Object> string(String description) {
    return Map.of("type", "string", "description", description);
  }

  static Map<String, Object> bool(String description) {
    return Map.of("type", "boolean", "description", description);
  }
}
