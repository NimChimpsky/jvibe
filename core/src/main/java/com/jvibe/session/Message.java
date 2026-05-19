package com.jvibe.session;

import java.time.Instant;
import java.util.Map;

public record Message(Role role, String content, Map<String, Object> metadata, Instant createdAt) {
  public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
  }

  public Message(Role role, String content) {
    this(role, content, Map.of(), Instant.now());
  }
}
