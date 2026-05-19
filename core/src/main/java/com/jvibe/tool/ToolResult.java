package com.jvibe.tool;

import java.util.Map;

public record ToolResult(String callId, boolean success, Map<String, Object> payload, String summary) {
  public static ToolResult success(String callId, Map<String, Object> payload, String summary) {
    return new ToolResult(callId, true, payload, summary);
  }

  public static ToolResult failure(String callId, String message) {
    return new ToolResult(callId, false, Map.of("error", message), message);
  }
}
