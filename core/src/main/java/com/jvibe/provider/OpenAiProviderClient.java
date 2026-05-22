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
public final class OpenAiProviderClient implements ProviderClient {
  private final ProviderHttpClient http;
  private final ProviderConfigService configService;

  public OpenAiProviderClient(ProviderHttpClient http, ProviderConfigService configService) {
    this.http = http;
    this.configService = configService;
  }

  @Override
  public String providerId() {
    return "openai";
  }

  @Override
  public Stream<ProviderEvent> stream(ProviderRequest request) {
    var apiKey = request.settings().apiKey();
    if (apiKey == null || apiKey.isBlank()) {
      return Stream.of(new ProviderEvent.Error("OPENAI_API_KEY is not configured", true));
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("model", request.model());
    body.put("instructions", request.systemPrompt());
    body.put("input", ProviderMessageMapper.openAiInput(request));
    body.put("stream", true);
    if (request.thinking() != null && !"none".equals(request.thinking().id())) {
      body.put("reasoning", Map.of("effort", request.thinking().id()));
    }
    if (!request.tools().isEmpty()) {
      body.put("tools", ProviderMessageMapper.openAiTools(request.tools()));
      body.put("tool_choice", "auto");
    }
    var response = http.postStream(
        URI.create(request.settings().baseUrl().replaceAll("/+$", "") + "/responses"),
        authHeaders(apiKey),
        JsonSupport.toJson(body));
    if (!response.success()) {
      return Stream.of(ProviderStreamErrors.httpError(response.statusCode(), response.lines()));
    }
    return parseResponseSse(response.lines());
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(
        model("gpt-5.4", "GPT-5.4", 1_000_000, ModelDescriptor.Source.STATIC),
        model("gpt-5.4-mini", "GPT-5.4 Mini", 400_000, ModelDescriptor.Source.STATIC),
        model("gpt-5.5", "GPT-5.5", 1_000_000, ModelDescriptor.Source.STATIC));
  }

  @Override
  public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
    if (settings.apiKey() == null || settings.apiKey().isBlank()) {
      return models();
    }
    var response = http.get(
        URI.create(settings.baseUrl().replaceAll("/+$", "") + "/models"),
        authHeaders(settings.apiKey()));
    if (!response.success()) {
      return models();
    }
    var parsed = JsonSupport.object(response.body());
    return JsonSupport.list(parsed.get("data")).stream()
        .map(JsonSupport::map)
        .map(item -> model(JsonSupport.string(item.get("id")), JsonSupport.string(item.get("id")), 0, ModelDescriptor.Source.DYNAMIC))
        .filter(model -> !model.id().isBlank())
        .toList();
  }

  List<ProviderEvent> parseResponseData(String data) {
    var events = new ArrayList<ProviderEvent>();
    var parsed = JsonSupport.object(data);
    var error = JsonSupport.stringAt(parsed, "error", "message");
    if (!error.isBlank()) {
      events.add(new ProviderEvent.Error(error, true));
      return events;
    }
    var type = JsonSupport.string(parsed.get("type"));
    if ("response.output_text.delta".equals(type)) {
      events.add(new ProviderEvent.AssistantDelta(JsonSupport.string(parsed.get("delta"))));
    } else if ("response.output_item.done".equals(type)) {
      var item = JsonSupport.map(parsed.get("item"));
      if ("function_call".equals(JsonSupport.string(item.get("type")))) {
        events.add(new ProviderEvent.ToolCallRequested(new ToolCall(
            JsonSupport.string(item.get("call_id")),
            JsonSupport.string(item.get("name")),
            JsonSupport.parseArguments(JsonSupport.string(item.get("arguments"))))));
      }
    } else if ("response.completed".equals(type)) {
      events.add(new ProviderEvent.Done());
    }
    return events;
  }

  Stream<ProviderEvent> parseResponseSse(Stream<String> lines) {
    var events = new ArrayList<ProviderEvent>();
    var rawLines = lines.toList();
    for (var line : rawLines) {
      if (line.startsWith("data:")) {
        var data = line.substring(5).trim();
        if (!data.isBlank() && !"[DONE]".equals(data)) {
          events.addAll(parseResponseData(data));
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

  private Map<String, String> authHeaders(String apiKey) {
    return Map.of(
        "Authorization", "Bearer " + apiKey,
        "Content-Type", "application/json",
        "Accept", "text/event-stream");
  }

  private ModelDescriptor model(String id, String displayName, int contextWindow, ModelDescriptor.Source source) {
    var thinking = configService.definition("openai")
        .map(ProviderDefinition::thinkingOptions)
        .orElse(List.of(ThinkingOption.of("none", "None")));
    return new ModelDescriptor("openai", id, displayName, contextWindow, true, thinking, source);
  }
}
