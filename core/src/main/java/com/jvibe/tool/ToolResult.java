package com.jvibe.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of one tool call. {@code payload} is sent back to the model; {@code display}
 * carries optional richer data for the UI only (for example a full query result).
 */
public record ToolResult(String callId, boolean success, Map<String, Object> payload, String summary, Object display) {
  public static ToolResult success(String callId, Map<String, Object> payload, String summary) {
    return new ToolResult(callId, true, payload, summary, null);
  }

  public static ToolResult failure(String callId, String message) {
    return new ToolResult(callId, false, Map.of("error", message), message, null);
  }

  public static ToolResult failure(String callId, Map<String, Object> payload, String message) {
    var details = new LinkedHashMap<String, Object>(payload);
    details.putIfAbsent("error", message);
    return new ToolResult(callId, false, Map.copyOf(details), message, null);
  }

  /** Command-style result: success when the exit code is zero. */
  public static ToolResult exit(String callId, int exitCode, String stdout, String stderr, String summary) {
    var payload = Map.<String, Object>of("exitCode", exitCode, "stdout", stdout, "stderr", stderr);
    return exitCode == 0 ? success(callId, payload, summary) : failure(callId, payload, summary);
  }

  public ToolResult withDisplay(Object value) {
    return new ToolResult(callId, success, payload, summary, value);
  }
}
