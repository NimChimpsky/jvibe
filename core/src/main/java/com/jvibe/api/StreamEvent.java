package com.jvibe.api;

import com.jvibe.tool.ToolCall;

/** One parsed event from a streamed JVibe mode response. */
public sealed interface StreamEvent {
  record AssistantDelta(String text) implements StreamEvent {}

  record ToolCallRequested(ToolCall call) implements StreamEvent {}

  record Done() implements StreamEvent {}

  record Error(String message) implements StreamEvent {}
}
