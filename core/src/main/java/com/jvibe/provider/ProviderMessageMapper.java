package com.jvibe.provider;

import com.jvibe.session.Message;
import com.jvibe.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProviderMessageMapper {
  private ProviderMessageMapper() {}

  static List<Map<String, Object>> chatMessages(ProviderRequest request, boolean includeToolCalls) {
    var messages = new ArrayList<Map<String, Object>>();
    if (!request.systemPrompt().isBlank()) {
      messages.add(Map.of("role", "system", "content", request.systemPrompt()));
    }
    var protocolStart = includeToolCalls ? trailingToolProtocolStart(request.messages()) : request.messages().size();
    for (var i = 0; i < request.messages().size(); i++) {
      var message = request.messages().get(i);
      var preserveToolProtocol = i >= protocolStart;
      switch (message.role()) {
        case SYSTEM -> messages.add(Map.of("role", "system", "content", message.content()));
        case USER -> messages.add(Map.of("role", "user", "content", message.content()));
        case ASSISTANT -> {
          if (preserveToolProtocol && message.metadata().containsKey("toolCallId")) {
            var assistant = new LinkedHashMap<String, Object>();
            assistant.put("role", "assistant");
            assistant.put("content", message.content());
            var reasoning = JsonSupport.string(message.metadata().get("reasoningContent"));
            if ("deepseek".equals(request.provider()) && !reasoning.isBlank()) {
              assistant.put("reasoning_content", reasoning);
            }
            assistant.put("tool_calls", List.of(Map.of(
                "id", message.metadata().get("toolCallId"),
                "type", "function",
                "function", Map.of(
                    "name", message.metadata().get("toolName"),
                    "arguments", JsonSupport.toJson(message.metadata().getOrDefault("toolArguments", Map.of()))))));
            messages.add(assistant);
          } else if (message.metadata().containsKey("toolCallId")) {
            var content = message.content().isBlank()
                ? "Called tool " + JsonSupport.string(message.metadata().get("toolName")) + "."
                : message.content();
            messages.add(Map.of("role", "assistant", "content", content));
          } else if (!message.content().isBlank()) {
            var assistant = new LinkedHashMap<String, Object>();
            assistant.put("role", "assistant");
            assistant.put("content", message.content());
            var reasoning = JsonSupport.string(message.metadata().get("reasoningContent"));
            if ("deepseek".equals(request.provider()) && !reasoning.isBlank()) {
              assistant.put("reasoning_content", reasoning);
            }
            messages.add(assistant);
          }
        }
        case TOOL -> {
          if (preserveToolProtocol) {
            messages.add(Map.of(
                "role", "tool",
                "tool_call_id", message.metadata().getOrDefault("toolCallId", message.createdAt().toString()),
                "content", message.content()));
          } else {
            messages.add(Map.of(
                "role", "user",
                "content", "Tool result: " + message.content()));
          }
        }
      }
    }
    return messages;
  }

  private static int trailingToolProtocolStart(List<Message> messages) {
    var start = messages.size();
    var index = messages.size() - 1;
    while (index > 0 && messages.get(index).role() == Message.Role.TOOL) {
      var tool = messages.get(index);
      var assistant = messages.get(index - 1);
      if (!isMatchingAssistantToolCall(assistant, tool)) {
        break;
      }
      start = index - 1;
      index -= 2;
    }
    return start;
  }

  private static boolean isMatchingAssistantToolCall(Message assistant, Message tool) {
    if (assistant.role() != Message.Role.ASSISTANT || !assistant.metadata().containsKey("toolCallId")) {
      return false;
    }
    var assistantCall = JsonSupport.string(assistant.metadata().get("toolCallId"));
    var toolCall = JsonSupport.string(tool.metadata().get("toolCallId"));
    return !assistantCall.isBlank() && assistantCall.equals(toolCall);
  }

  static List<Map<String, Object>> anthropicMessages(ProviderRequest request) {
    var messages = new ArrayList<Map<String, Object>>();
    for (var message : request.messages()) {
      switch (message.role()) {
        case USER -> messages.add(Map.of("role", "user", "content", message.content()));
        case ASSISTANT -> {
          if (message.metadata().containsKey("toolCallId")) {
            messages.add(Map.of(
                "role", "assistant",
                "content", List.of(Map.of(
                    "type", "tool_use",
                    "id", message.metadata().get("toolCallId"),
                    "name", message.metadata().get("toolName"),
                    "input", message.metadata().getOrDefault("toolArguments", Map.of())))));
          } else if (!message.content().isBlank()) {
            messages.add(Map.of("role", "assistant", "content", message.content()));
          }
        }
        case TOOL -> messages.add(Map.of(
            "role", "user",
            "content", List.of(Map.of(
                "type", "tool_result",
                "tool_use_id", message.metadata().getOrDefault("toolCallId", message.createdAt().toString()),
                "content", message.content()))));
        case SYSTEM -> {
          // Anthropic sends system prompt at the top level.
        }
      }
    }
    return messages;
  }

  static List<Map<String, Object>> openAiInput(ProviderRequest request) {
    var input = new ArrayList<Map<String, Object>>();
    for (var message : request.messages()) {
      switch (message.role()) {
        case SYSTEM -> input.add(Map.of("role", "system", "content", message.content()));
        case USER -> input.add(Map.of("role", "user", "content", message.content()));
        case ASSISTANT -> {
          if (!message.content().isBlank()) {
            input.add(Map.of("role", "assistant", "content", message.content()));
          }
        }
        case TOOL -> input.add(Map.of(
            "type", "function_call_output",
            "call_id", message.metadata().getOrDefault("toolCallId", message.createdAt().toString()),
            "output", message.content()));
      }
    }
    return input;
  }

  static List<Map<String, Object>> openAiTools(List<ToolDefinition> definitions) {
    return definitions.stream()
        .map(tool -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("type", "function");
          map.put("name", tool.id());
          map.put("description", tool.description());
          map.put("parameters", tool.inputSchema());
          map.put("strict", false);
          return map;
        })
        .toList();
  }

  static List<Map<String, Object>> chatTools(List<ToolDefinition> definitions) {
    return definitions.stream()
        .map(tool -> Map.<String, Object>of(
            "type", "function",
            "function", Map.of(
                "name", tool.id(),
                "description", tool.description(),
                "parameters", tool.inputSchema())))
        .toList();
  }

  static List<Map<String, Object>> anthropicTools(List<ToolDefinition> definitions) {
    return definitions.stream()
        .map(tool -> Map.<String, Object>of(
            "name", tool.id(),
            "description", tool.description(),
            "input_schema", tool.inputSchema()))
        .toList();
  }
}
