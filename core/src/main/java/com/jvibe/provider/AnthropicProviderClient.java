package com.jvibe.provider;

import com.jvibe.tool.ToolCall;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jakarta.inject.Singleton;

@Singleton
public final class AnthropicProviderClient implements ProviderClient {
  private static final String API_VERSION = "2023-06-01";
  private final ProviderHttpClient http;
  private final ProviderConfigService configService;

  public AnthropicProviderClient(ProviderHttpClient http, ProviderConfigService configService) {
    this.http = http;
    this.configService = configService;
  }

  @Override
  public String providerId() {
    return "anthropic";
  }

  @Override
  public Stream<ProviderEvent> stream(ProviderRequest request) {
    var key = request.settings().apiKey();
    if (key == null || key.isBlank()) {
      return Stream.of(new ProviderEvent.Error("ANTHROPIC_API_KEY is not configured", true));
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("model", request.model());
    body.put("max_tokens", 8192);
    body.put("system", request.systemPrompt());
    body.put("messages", ProviderMessageMapper.anthropicMessages(request));
    body.put("stream", true);
    if (!request.tools().isEmpty()) {
      body.put("tools", ProviderMessageMapper.anthropicTools(request.tools()));
    }
    applyThinking(body, request.thinking());
    var response = http.postStream(endpoint(request.settings(), "/messages"), headers(key), JsonSupport.toJson(body));
    if (!response.success()) {
      return Stream.of(ProviderStreamErrors.httpError(response.statusCode(), response.lines()));
    }
    return parseSse(response.lines());
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(
        model("claude-sonnet-4-20250514", "Claude Sonnet 4", ModelDescriptor.Source.STATIC),
        model("claude-opus-4-1-20250805", "Claude Opus 4.1", ModelDescriptor.Source.STATIC),
        model("claude-3-7-sonnet-20250219", "Claude Sonnet 3.7", ModelDescriptor.Source.STATIC));
  }

  @Override
  public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
    if (settings.apiKey() == null || settings.apiKey().isBlank()) {
      return models();
    }
    var response = http.get(endpoint(settings, "/models"), headers(settings.apiKey()));
    if (!response.success()) {
      return models();
    }
    var parsed = JsonSupport.object(response.body());
    return JsonSupport.list(parsed.get("data")).stream()
        .map(JsonSupport::map)
        .map(item -> model(
            JsonSupport.string(item.get("id")),
            JsonSupport.string(item.getOrDefault("display_name", item.get("id"))),
            ModelDescriptor.Source.DYNAMIC))
        .filter(model -> !model.id().isBlank())
        .toList();
  }

  List<ProviderEvent> parseData(String data) {
    var events = new ArrayList<ProviderEvent>();
    var parsed = JsonSupport.object(data);
    var type = JsonSupport.string(parsed.get("type"));
    if ("error".equals(type)) {
      events.add(new ProviderEvent.Error(JsonSupport.stringAt(parsed, "error", "message"), true));
      return events;
    }
    if ("content_block_delta".equals(type)) {
      var delta = JsonSupport.map(parsed.get("delta"));
      if ("text_delta".equals(JsonSupport.string(delta.get("type")))) {
        events.add(new ProviderEvent.AssistantDelta(JsonSupport.string(delta.get("text"))));
      } else if ("input_json_delta".equals(JsonSupport.string(delta.get("type")))) {
        events.add(new ProviderEvent.AssistantDelta(""));
      }
    } else if ("content_block_stop".equals(type)) {
      var block = JsonSupport.map(parsed.get("content_block"));
      if ("tool_use".equals(JsonSupport.string(block.get("type")))) {
        events.add(new ProviderEvent.ToolCallRequested(new ToolCall(
            JsonSupport.string(block.get("id")),
            JsonSupport.string(block.get("name")),
            JsonSupport.map(block.get("input")))));
      }
    } else if ("message_stop".equals(type)) {
      events.add(new ProviderEvent.Done());
    }
    return events;
  }

  Stream<ProviderEvent> parseSse(Stream<String> lines) {
    var events = new ArrayList<ProviderEvent>();
    var toolBlocks = new LinkedHashMap<String, ToolBuffer>();
    var rawLines = lines.toList();
    for (var line : rawLines) {
      if (line.startsWith("data:")) {
        var data = line.substring(5).trim();
        if (!data.isBlank() && !"[DONE]".equals(data)) {
          parseStreamingData(data, events, toolBlocks);
        }
      }
    }
    if (events.stream().noneMatch(ProviderEvent.Done.class::isInstance)
        && events.stream().noneMatch(ProviderEvent.ToolCallRequested.class::isInstance)
        && events.stream().noneMatch(ProviderEvent.Error.class::isInstance)) {
      events.add(ProviderStreamErrors.emptyResponse(providerId(), String.join("\n", rawLines)));
    }
    return events.stream();
  }

  private void parseStreamingData(String data, List<ProviderEvent> events, Map<String, ToolBuffer> toolBlocks) {
    var parsed = JsonSupport.object(data);
    var type = JsonSupport.string(parsed.get("type"));
    if ("content_block_start".equals(type)) {
      var block = JsonSupport.map(parsed.get("content_block"));
      if ("tool_use".equals(JsonSupport.string(block.get("type")))) {
        var key = JsonSupport.string(parsed.get("index"));
        var buffer = toolBlocks.computeIfAbsent(key, ignored -> new ToolBuffer());
        buffer.id = JsonSupport.string(block.get("id"));
        buffer.name = JsonSupport.string(block.get("name"));
        var input = block.get("input");
        if (input instanceof Map<?, ?> map && !map.isEmpty()) {
          buffer.arguments.append(JsonSupport.toJson(input));
        }
      }
      return;
    }
    if ("content_block_delta".equals(type)) {
      var delta = JsonSupport.map(parsed.get("delta"));
      if ("text_delta".equals(JsonSupport.string(delta.get("type")))) {
        events.add(new ProviderEvent.AssistantDelta(JsonSupport.string(delta.get("text"))));
      } else if ("input_json_delta".equals(JsonSupport.string(delta.get("type")))) {
        var key = JsonSupport.string(parsed.get("index"));
        toolBlocks.computeIfAbsent(key, ignored -> new ToolBuffer())
            .arguments.append(JsonSupport.string(delta.get("partial_json")));
      }
      return;
    }
    if ("content_block_stop".equals(type)) {
      var key = JsonSupport.string(parsed.get("index"));
      var buffer = toolBlocks.remove(key);
      if (buffer != null && !buffer.name.isBlank()) {
        events.add(new ProviderEvent.ToolCallRequested(new ToolCall(
            buffer.id,
            buffer.name,
            JsonSupport.parseArguments(buffer.arguments.toString()))));
      }
      return;
    }
    events.addAll(parseData(data));
  }

  private void applyThinking(Map<String, Object> body, ThinkingOption thinking) {
    if (thinking == null || "off".equals(thinking.id())) {
      return;
    }
    var budget = JsonSupport.intAt(Map.of("thinking", thinking.parameters()), 1024, "thinking", "budget_tokens");
    body.put("thinking", Map.of("type", "enabled", "budget_tokens", budget));
  }

  private URI endpoint(ProviderSettings settings, String path) {
    return URI.create(settings.baseUrl().replaceAll("/+$", "") + path);
  }

  private Map<String, String> headers(String key) {
    return Map.of(
        "x-api-key", key,
        "anthropic-version", API_VERSION,
        "Content-Type", "application/json",
        "Accept", "text/event-stream");
  }

  private ModelDescriptor model(String id, String displayName, ModelDescriptor.Source source) {
    var thinking = configService.definition("anthropic").map(ProviderDefinition::thinkingOptions).orElse(List.of());
    return new ModelDescriptor("anthropic", id, displayName, 200_000, true, thinking, source);
  }

  private static final class ToolBuffer {
    private String id = java.util.UUID.randomUUID().toString();
    private String name = "";
    private final StringBuilder arguments = new StringBuilder();
  }
}
