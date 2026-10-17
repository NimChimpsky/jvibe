package com.jvibe.session;

import com.jvibe.tool.ToolCall;
import java.util.List;

/** One conversation entry in the OpenAI chat shape the JVibe service accepts. */
public record Message(Role role, String content, List<ToolCall> toolCalls, String toolCallId) {
  public enum Role {
    USER,
    ASSISTANT,
    TOOL
  }

  public Message {
    content = content == null ? "" : content;
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  public static Message user(String content) {
    return new Message(Role.USER, content, List.of(), null);
  }

  public static Message assistant(String content, List<ToolCall> toolCalls) {
    return new Message(Role.ASSISTANT, content, toolCalls, null);
  }

  public static Message tool(String toolCallId, String content) {
    return new Message(Role.TOOL, content, List.of(), toolCallId);
  }
}
