package com.jvibe.provider;

import com.jvibe.tool.ToolCall;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

abstract class AbstractChatCompletionsProviderClient implements ProviderClient {
  private final ProviderHttpClient http;
  private final ProviderConfigService configService;

  AbstractChatCompletionsProviderClient(ProviderHttpClient http, ProviderConfigService configService) {
    this.http = http;
    this.configService = configService;
  }

  @Override
  public Stream<ProviderEvent> stream(ProviderRequest request) {
    var settings = request.settings();
    var key = settings.apiKey();
    if (key == null || key.isBlank()) {
      return Stream.of(new ProviderEvent.Error("Add your API key in the JVibe tab or set " + apiKeyName(), true));
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("model", request.model());
    body.put("messages", ProviderMessageMapper.chatMessages(request, true));
    body.put("stream", true);
    if (!request.tools().isEmpty()) {
      body.put("tools", ProviderMessageMapper.chatTools(request.tools()));
      body.put("tool_choice", "auto");
    }
    applyThinking(body, request.thinking());

    var response = http.postStream(
        endpoint(settings, "/chat/completions"),
        authHeaders(key),
        JsonSupport.toJson(body));
    if (!response.success()) {
      return Stream.of(ProviderStreamErrors.httpError(response.statusCode(), response.lines()));
    }
    return parseChatSse(response.lines());
  }

  @Override
  public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
    var key = settings.apiKey();
    if (key == null || key.isBlank()) {
      return models();
    }
    var response = http.get(endpoint(settings, "/models"), authHeaders(key));
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new IllegalStateException("JVibe authentication failed. Check your API key.");
    }
    if (!response.success()) {
      return models();
    }
    var parsed = JsonSupport.object(response.body());
    return JsonSupport.list(parsed.get("data")).stream()
        .map(JsonSupport::map)
        .map(model -> new ModelDescriptor(
            providerId(),
            JsonSupport.string(model.get("id")),
            JsonSupport.string(model.get("id")),
            JsonSupport.intAt(model, JsonSupport.intAt(model, defaultContextWindow(), "context_length"), "max_context_length"),
            supportsTools(model),
            thinkingOptions(),
            ModelDescriptor.Source.DYNAMIC))
        .filter(model -> !model.id().isBlank())
        .toList();
  }

  URI endpoint(ProviderSettings settings, String path) {
    return URI.create(settings.baseUrl().replaceAll("/+$", "") + path);
  }

  Map<String, String> authHeaders(String key) {
    return Map.of(
        "Authorization", "Bearer " + key,
        "Content-Type", "application/json",
        "Accept", "text/event-stream");
  }

  List<ProviderEvent> parseChatData(String data) {
    var events = new ArrayList<ProviderEvent>();
    var parsed = JsonSupport.object(data);
    var error = JsonSupport.stringAt(parsed, "error", "message");
    if (!error.isBlank()) {
      events.add(new ProviderEvent.Error(error, true));
      return events;
    }
    for (var choiceObject : JsonSupport.list(parsed.get("choices"))) {
      var choice = JsonSupport.map(choiceObject);
      var delta = JsonSupport.map(choice.get("delta"));
      var reasoning = JsonSupport.string(delta.get("reasoning_content"));
      if (!reasoning.isEmpty()) {
        events.add(new ProviderEvent.ReasoningDelta(reasoning));
      }
      var text = JsonSupport.string(delta.get("content"));
      if (!text.isEmpty()) {
        events.add(new ProviderEvent.AssistantDelta(text));
      }
      for (var toolObject : JsonSupport.list(delta.get("tool_calls"))) {
        var tool = JsonSupport.map(toolObject);
        var function = JsonSupport.map(tool.get("function"));
        var name = JsonSupport.string(function.get("name"));
        var args = JsonSupport.string(function.get("arguments"));
        if (!name.isBlank()) {
          events.add(new ProviderEvent.ToolCallRequested(new ToolCall(
              JsonSupport.string(tool.get("id")),
              name,
              JsonSupport.parseArguments(args))));
        }
      }
      var message = JsonSupport.map(choice.get("message"));
      var messageReasoning = JsonSupport.string(message.get("reasoning_content"));
      if (!messageReasoning.isBlank()) {
        events.add(new ProviderEvent.ReasoningDelta(messageReasoning));
      }
      var messageText = JsonSupport.string(message.get("content"));
      if (!messageText.isBlank()) {
        events.add(new ProviderEvent.AssistantDelta(messageText));
      }
      for (var toolObject : JsonSupport.list(message.get("tool_calls"))) {
        var tool = JsonSupport.map(toolObject);
        var function = JsonSupport.map(tool.get("function"));
        var name = JsonSupport.string(function.get("name"));
        if (!name.isBlank()) {
          events.add(new ProviderEvent.ToolCallRequested(new ToolCall(
              JsonSupport.string(tool.get("id")),
              name,
              JsonSupport.parseArguments(JsonSupport.string(function.get("arguments"))))));
        }
      }
      if (!JsonSupport.string(choice.get("finish_reason")).isBlank()) {
        events.add(new ProviderEvent.Done());
      }
    }
    return events;
  }

  Stream<ProviderEvent> parseChatSse(Stream<String> lines) {
    var toolCalls = new LinkedHashMap<String, ToolBuffer>();
    return ProviderEventStream.parse(providerId(), lines, data -> {
      var events = new ArrayList<ProviderEvent>();
      if ("[DONE]".equals(data)) {
        emitBufferedToolCalls(events, toolCalls);
        events.add(new ProviderEvent.Done());
      } else if (!data.isBlank()) {
        parseStreamingChatData(data, events, toolCalls);
      }
      return events;
    });
  }

  private void parseStreamingChatData(String data, List<ProviderEvent> events, Map<String, ToolBuffer> toolCalls) {
    var parsed = JsonSupport.object(data);
    var error = JsonSupport.stringAt(parsed, "error", "message");
    if (!error.isBlank()) {
      events.add(new ProviderEvent.Error(error, true));
      return;
    }
    for (var choiceObject : JsonSupport.list(parsed.get("choices"))) {
      var choice = JsonSupport.map(choiceObject);
      var delta = JsonSupport.map(choice.get("delta"));
      var reasoning = JsonSupport.string(delta.get("reasoning_content"));
      if (!reasoning.isEmpty()) {
        events.add(new ProviderEvent.ReasoningDelta(reasoning));
      }
      var text = JsonSupport.string(delta.get("content"));
      if (!text.isEmpty()) {
        events.add(new ProviderEvent.AssistantDelta(text));
      }
      for (var toolObject : JsonSupport.list(delta.get("tool_calls"))) {
        var tool = JsonSupport.map(toolObject);
        var index = JsonSupport.string(tool.get("index"));
        var id = JsonSupport.string(tool.get("id"));
        var key = index.isBlank() ? id : index;
        var buffer = toolCalls.computeIfAbsent(key, ignored -> new ToolBuffer());
        if (!id.isBlank()) {
          buffer.id = id;
        }
        var function = JsonSupport.map(tool.get("function"));
        var name = JsonSupport.string(function.get("name"));
        if (!name.isBlank()) {
          buffer.name = name;
        }
        buffer.arguments.append(JsonSupport.string(function.get("arguments")));
      }
      var message = JsonSupport.map(choice.get("message"));
      if (!message.isEmpty()) {
        events.addAll(parseChatData(data));
      }
      var finish = JsonSupport.string(choice.get("finish_reason"));
      if ("tool_calls".equals(finish)) {
        emitBufferedToolCalls(events, toolCalls);
        events.add(new ProviderEvent.Done());
      } else if (!finish.isBlank() && toolCalls.isEmpty()) {
        events.add(new ProviderEvent.Done());
      }
    }
  }

  private void emitBufferedToolCalls(List<ProviderEvent> events, Map<String, ToolBuffer> toolCalls) {
    for (var buffer : toolCalls.values()) {
      if (!buffer.name.isBlank()) {
        events.add(new ProviderEvent.ToolCallRequested(new ToolCall(buffer.id, buffer.name, JsonSupport.parseArguments(buffer.arguments.toString()))));
      }
    }
    toolCalls.clear();
  }

  private static final class ToolBuffer {
    private String id = java.util.UUID.randomUUID().toString();
    private String name = "";
    private final StringBuilder arguments = new StringBuilder();
  }

  protected ProviderConfigService configService() {
    return configService;
  }

  protected abstract String apiKeyName();

  protected abstract int defaultContextWindow();

  protected abstract List<ThinkingOption> thinkingOptions();

  protected abstract void applyThinking(Map<String, Object> body, ThinkingOption thinking);

  protected boolean supportsTools(Map<String, Object> model) {
    var capabilities = JsonSupport.map(model.get("capabilities"));
    var value = capabilities.get("function_calling");
    return value instanceof Boolean bool ? bool : true;
  }
}
