package com.jvibe.api;

import com.jvibe.session.Message;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the JSON body for {@code POST /modes/{mode}}. */
final class MessageMapper {
  private MessageMapper() {}

  static Map<String, Object> body(ModeRequest request) {
    var body = new LinkedHashMap<String, Object>();
    body.put("messages", request.messages().stream().map(MessageMapper::message).toList());
    body.put("thinking", request.thinking().id());
    if (!request.instructions().isBlank()) body.put("instructions", request.instructions());
    if (!request.context().isBlank()) body.put("context", request.context());
    return body;
  }

  static Map<String, Object> message(Message message) {
    var json = new LinkedHashMap<String, Object>();
    json.put("role", message.role().name().toLowerCase(java.util.Locale.ROOT));
    json.put("content", message.content());
    if (message.role() == Message.Role.TOOL) {
      json.put("tool_call_id", message.toolCallId());
    }
    if (!message.toolCalls().isEmpty()) {
      json.put("tool_calls", message.toolCalls().stream().map(call -> Map.of(
          "id", call.id(),
          "type", "function",
          "function", Map.of("name", call.name(), "arguments", JsonSupport.toJson(call.arguments()))))
          .toList());
    }
    return json;
  }

}
