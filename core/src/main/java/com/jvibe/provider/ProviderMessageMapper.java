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
          var toolCalls = toolCalls(message);
          if (preserveToolProtocol && !toolCalls.isEmpty()) {
            var assistant = new LinkedHashMap<String, Object>();
            assistant.put("role", "assistant");
            assistant.put("content", message.content());
            var reasoning = JsonSupport.string(message.metadata().get("reasoningContent"));
            if ("deepseek".equals(request.provider()) && !reasoning.isBlank()) {
              assistant.put("reasoning_content", reasoning);
            }
            assistant.put("tool_calls", toolCalls.stream().map(call -> Map.of(
                "id", call.get("id"),
                "type", "function",
                "function", Map.of(
                    "name", call.get("name"),
                    "arguments", JsonSupport.toJson(call.getOrDefault("arguments", Map.of()))))).toList());
            messages.add(assistant);
          } else if (!toolCalls.isEmpty()) {
            var content = message.content().isBlank()
                ? "Called tools: " + toolCalls.stream().map(call -> JsonSupport.string(call.get("name"))).toList() + "."
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
    var lastUser = -1;
    for (var i = messages.size() - 1; i >= 0; i--) {
      if (messages.get(i).role() == Message.Role.USER) {
        lastUser = i;
        break;
      }
    }
    for (var i = lastUser + 1; i < messages.size(); i++) {
      var message = messages.get(i);
      if (message.role() != Message.Role.ASSISTANT) {
        continue;
      }
      var callIds = new java.util.HashSet<String>();
      toolCalls(message).forEach(call -> callIds.add(JsonSupport.string(call.get("id"))));
      if (callIds.isEmpty()) {
        continue;
      }
      for (var j = i + 1; j < messages.size(); j++) {
        var candidate = messages.get(j);
        if (candidate.role() == Message.Role.USER) {
          return messages.size();
        }
        if (candidate.role() == Message.Role.TOOL) {
          callIds.remove(JsonSupport.string(candidate.metadata().get("toolCallId")));
        }
      }
      return callIds.isEmpty() ? i : messages.size();
    }
    return messages.size();
  }

  static List<Map<String, Object>> anthropicMessages(ProviderRequest request) {
    var messages = new ArrayList<Map<String, Object>>();
    for (var message : request.messages()) {
      switch (message.role()) {
        case USER -> messages.add(Map.of("role", "user", "content", message.content()));
        case ASSISTANT -> {
          var toolCalls = toolCalls(message);
          if (!toolCalls.isEmpty()) {
            var content = new ArrayList<Map<String, Object>>();
            if (!message.content().isBlank()) {
              content.add(Map.of("type", "text", "text", message.content()));
            }
            content.addAll(toolCalls.stream().map(call -> Map.<String, Object>of(
                "type", "tool_use",
                "id", call.get("id"),
                "name", call.get("name"),
                "input", call.getOrDefault("arguments", Map.of()))).toList());
            messages.add(Map.of(
                "role", "assistant",
                "content", content));
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
          for (var call : toolCalls(message)) {
            input.add(Map.of(
                "type", "function_call",
                "call_id", call.get("id"),
                "name", call.get("name"),
                "arguments", JsonSupport.toJson(call.getOrDefault("arguments", Map.of()))));
          }
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

  private static List<Map<String, Object>> toolCalls(Message message) {
    var batch = message.metadata().get("toolCalls");
    if (batch instanceof List<?> list) {
      var calls = new ArrayList<Map<String, Object>>();
      for (var value : list) {
        if (value instanceof Map<?, ?> raw) {
          var id = raw.get("id");
          var name = raw.get("name");
          if (id != null && name != null) {
            var call = new LinkedHashMap<String, Object>();
            call.put("id", id);
            call.put("name", name);
            call.put("arguments", raw.get("arguments") instanceof Map<?, ?> arguments ? arguments : Map.of());
            calls.add(call);
          }
        }
      }
      return calls;
    }
    if (message.metadata().containsKey("toolCallId")) {
      return List.of(Map.of(
          "id", message.metadata().get("toolCallId"),
          "name", message.metadata().get("toolName"),
          "arguments", message.metadata().getOrDefault("toolArguments", Map.of())));
    }
    return List.of();
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
