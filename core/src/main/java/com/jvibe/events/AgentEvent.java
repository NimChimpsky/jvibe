package com.jvibe.events;

import java.time.Instant;
import java.util.Map;

public record AgentEvent(Type type, String message, Map<String, Object> data, Instant timestamp) {
  public enum Type {
    ASSISTANT_DELTA,
    TOOL_STARTED,
    TOOL_FINISHED,
    APPROVAL_REQUIRED,
    ERROR,
    DONE
  }

  public AgentEvent(Type type, String message) {
    this(type, message, Map.of(), Instant.now());
  }

  public static AgentEvent error(String message) {
    return new AgentEvent(Type.ERROR, message);
  }
}
