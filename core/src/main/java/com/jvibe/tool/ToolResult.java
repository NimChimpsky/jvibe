package com.jvibe.tool;

import java.util.Map;

public record ToolResult(String callId, boolean success, Map<String, Object> payload, String summary) {
  public static ToolResult success(String callId, Map<String, Object> payload, String summary) {
    return new ToolResult(callId, true, payload, summary);
  }

  public static ToolResult failure(String callId, String message) {
    return new ToolResult(callId, false, Map.of("error", message), message);
  }

  public static ToolResult failure(String callId, Map<String, Object> payload, String message) {
    var details = new java.util.LinkedHashMap<String, Object>(payload);
    details.putIfAbsent("error", message);
    return new ToolResult(callId, false, Map.copyOf(details), message);
  }
}
