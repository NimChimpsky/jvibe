package com.jvibe.tool;

import java.util.Map;
import java.util.UUID;

public record ToolCall(String id, String name, Map<String, Object> arguments) {
  public ToolCall(String name, Map<String, Object> arguments) {
    this(UUID.randomUUID().toString(), name, arguments);
  }
}
