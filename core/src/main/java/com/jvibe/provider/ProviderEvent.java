package com.jvibe.provider;

import com.jvibe.tool.ToolCall;

public sealed interface ProviderEvent permits ProviderEvent.AssistantDelta, ProviderEvent.ReasoningDelta, ProviderEvent.AssistantMetadata, ProviderEvent.ToolCallRequested, ProviderEvent.Done, ProviderEvent.Error {
  record AssistantDelta(String text) implements ProviderEvent {}

  record ReasoningDelta(String text) implements ProviderEvent {}

  /** Opaque provider protocol data retained in history, never rendered as chat text. */
  record AssistantMetadata(java.util.Map<String, Object> values) implements ProviderEvent {}

  record ToolCallRequested(ToolCall call) implements ProviderEvent {}

  record Done() implements ProviderEvent {}

  record Error(String message, boolean fatal) implements ProviderEvent {}
}
