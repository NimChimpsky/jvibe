package com.jvibe.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.config.JvibePaths;
import com.jvibe.session.Message;
import com.jvibe.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderClientParsingTest {
  @TempDir java.nio.file.Path home;

  @Test
  void openAiParsesTextAndToolEvents() {
    var client = new OpenAiProviderClient(new FakeProviderHttpClient(), new ProviderConfigService(JvibePaths.under(home)));

    var text = client.parseResponseData("""
        {"type":"response.output_text.delta","delta":"hello"}
        """);
    var tool = client.parseResponseData("""
        {"type":"response.output_item.done","item":{"type":"function_call","call_id":"call_1","name":"file_list","arguments":"{\\"path\\":\\".\\"}"}}
        """);

    assertEquals("hello", ((ProviderEvent.AssistantDelta) text.getFirst()).text());
    var call = ((ProviderEvent.ToolCallRequested) tool.getFirst()).call();
    assertEquals("file_list", call.name());
    assertEquals(".", call.arguments().get("path"));
  }

  @Test
  void providerToolArgumentParsingHandlesBlankAndInvalidJson() {
    var client = new OpenAiProviderClient(new FakeProviderHttpClient(), new ProviderConfigService(JvibePaths.under(home)));

    var blank = client.parseResponseData("""
        {"type":"response.output_item.done","item":{"type":"function_call","call_id":"call_1","name":"file_list","arguments":""}}
        """);
    var invalid = client.parseResponseData("""
        {"type":"response.output_item.done","item":{"type":"function_call","call_id":"call_2","name":"file_list","arguments":"{bad json"}}
        """);

    assertTrue(((ProviderEvent.ToolCallRequested) blank.getFirst()).call().arguments().isEmpty());
    assertEquals("{bad json", ((ProviderEvent.ToolCallRequested) invalid.getFirst()).call().arguments().get("raw"));
  }

  @Test
  void mistralParsesModelListAndBuildsThinkingRequest() {
    var http = new FakeProviderHttpClient();
    http.getResponse = new HttpTextResponse(200, """
        {"data":[{"id":"mistral-large-latest","max_context_length":128000,"capabilities":{"function_calling":true}}]}
        """);
    http.streamLines = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}");
    var config = new ProviderConfigService(JvibePaths.under(home));
    var client = new MistralProviderClient(http, config);
    var settings = new ProviderSettings("mistral", "https://api.mistral.ai/v1", "key", "mistral-large-latest", "high", true);

    var models = client.refreshModels(settings);
    var events = client.stream(new ProviderRequest(
        "mistral",
        "mistral-large-latest",
        settings,
        ThinkingOption.of("high", "High"),
        "system",
        List.of(new Message(Message.Role.USER, "hello")),
        List.of(new ToolDefinition("file_list", "List", "List files", Map.of("type", "object"), com.jvibe.approval.ApprovalRequest.Risk.LOW)),
        Map.of())).toList();

    assertEquals("mistral-large-latest", models.getFirst().id());
    assertTrue(http.lastJson.contains("\"reasoning_effort\":\"high\""));
    assertEquals("hi", ((ProviderEvent.AssistantDelta) events.getFirst()).text());
  }

  @Test
  void deepSeekParsesModelsAndThinking() {
    var http = new FakeProviderHttpClient();
    http.getResponse = new HttpTextResponse(200, """
        {"object":"list","data":[{"id":"deepseek-v4-pro","object":"model","owned_by":"deepseek"}]}
        """);
    http.streamLines = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    var client = new DeepSeekProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("deepseek", "https://api.deepseek.com", "key", "deepseek-v4-pro", "max", true);

    var models = client.refreshModels(settings);
    client.stream(new ProviderRequest("deepseek", "deepseek-v4-pro", settings, ThinkingOption.of("max", "Max"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    assertEquals("deepseek-v4-pro", models.getFirst().id());
    assertTrue(http.lastJson.contains("\"reasoning_effort\":\"max\""));
  }

  @Test
  void chatCompletionHttpErrorSurfacesProviderMessage() {
    var http = new FakeProviderHttpClient();
    http.streamStatusCode = 400;
    http.streamLines = List.of("{\"error\":{\"message\":\"model not found\"}}");
    var client = new DeepSeekProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("deepseek", "https://api.deepseek.com", "key", "deepseek-v4-flash", "disabled", true);

    var events = client.stream(new ProviderRequest("deepseek", settings.selectedModel(), settings, ThinkingOption.of("disabled", "Off"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    var error = assertInstanceOf(ProviderEvent.Error.class, events.getFirst());
    assertTrue(error.fatal());
    assertTrue(error.message().contains("Provider HTTP 400"));
    assertTrue(error.message().contains("model not found"));
  }

  @Test
  void chatCompletionEmptyStreamIsAnError() {
    var http = new FakeProviderHttpClient();
    var client = new DeepSeekProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("deepseek", "https://api.deepseek.com", "key", "deepseek-v4-flash", "disabled", true);

    var events = client.stream(new ProviderRequest("deepseek", settings.selectedModel(), settings, ThinkingOption.of("disabled", "Off"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    var error = assertInstanceOf(ProviderEvent.Error.class, events.getFirst());
    assertTrue(error.message().contains("returned no streaming response data"));
  }

  @Test
  void qwenMapsThinkingAndUsesRegionBaseUrl() {
    var http = new FakeProviderHttpClient();
    http.getResponse = new HttpTextResponse(200, """
        {"data":[{"id":"qwen3-coder-plus","max_context_length":1000000}]}
        """);
    http.streamLines = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    var client = new QwenProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("qwen", QwenProviderClient.CHINA_BASE_URL, "key", "qwen3-coder-plus", "on", true);

    var models = client.refreshModels(settings);
    client.stream(new ProviderRequest("qwen", "qwen3-coder-plus", settings, ThinkingOption.of("on", "On"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    assertEquals("qwen3-coder-plus", models.getFirst().id());
    assertEquals(QwenProviderClient.CHINA_BASE_URL + "/chat/completions", http.lastUri.toString());
    assertTrue(http.lastJson.contains("\"enable_thinking\":true"));
  }

  @Test
  void jvibeUsesChatCompletionsAndFallsBackToStaticModels() {
    var http = new FakeProviderHttpClient();
    http.getResponse = new HttpTextResponse(500, "{}");
    http.streamLines = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    var client = new JvibeProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, "key", "jvibe-coder", "high", true);

    var models = client.refreshModels(settings);
    client.stream(new ProviderRequest("jvibe", "jvibe-coder", settings, ThinkingOption.of("high", "High"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    assertEquals(List.of("jvibe-coder", "jvibe-agent"), models.stream().map(ModelDescriptor::id).toList());
    assertEquals(JvibeProviderClient.BASE_URL + "/chat/completions", http.lastUri.toString());
    assertTrue(http.lastJson.contains("\"reasoning_effort\":\"high\""));
  }

  @Test
  void chatCompletionParserBuffersFragmentedToolCalls() {
    var http = new FakeProviderHttpClient();
    http.streamLines = List.of(
        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"file_read\",\"arguments\":\"{\"}}]}}]}",
        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"path\\\":\\\"README.md\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}");
    var client = new DeepSeekProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("deepseek", "https://api.deepseek.com", "key", "deepseek-v4-pro", "disabled", true);

    var events = client.stream(new ProviderRequest("deepseek", "deepseek-v4-pro", settings, ThinkingOption.of("disabled", "Off"), "system", List.of(new Message(Message.Role.USER, "read")), List.of(), Map.of())).toList();

    var call = (ProviderEvent.ToolCallRequested) events.getFirst();
    assertEquals("file_read", call.call().name());
    assertEquals("README.md", call.call().arguments().get("path"), call.call().arguments().toString());
  }

  @Test
  void deepSeekPreservesReasoningContentForToolFollowUp() {
    var http = new FakeProviderHttpClient();
    http.streamLines = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    var client = new DeepSeekProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("deepseek", "https://api.deepseek.com", "key", "deepseek-v4-pro", "high", true);

    client.stream(new ProviderRequest(
        "deepseek",
        "deepseek-v4-pro",
        settings,
        ThinkingOption.of("high", "High"),
        "system",
        List.of(
            new Message(Message.Role.USER, "list"),
            new Message(Message.Role.ASSISTANT, "", Map.of(
                "toolCallId", "call_1",
                "toolName", "file_list",
                "toolArguments", Map.of("path", "."),
                "reasoningContent", "hidden chain"),
                java.time.Instant.now()),
            new Message(Message.Role.TOOL, "ok", Map.of("toolCallId", "call_1"), java.time.Instant.now())),
        List.of(),
        Map.of())).toList();

    assertTrue(http.lastJson.contains("\"reasoning_content\":\"hidden chain\""), http.lastJson);
  }

  @Test
  void deepSeekFlattensCompletedToolHistoryBeforeNewUserPrompt() {
    var http = new FakeProviderHttpClient();
    http.streamLines = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    var client = new DeepSeekProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("deepseek", "https://api.deepseek.com", "key", "deepseek-v4-pro", "high", true);

    client.stream(new ProviderRequest(
        "deepseek",
        "deepseek-v4-pro",
        settings,
        ThinkingOption.of("high", "High"),
        "system",
        List.of(
            new Message(Message.Role.USER, "list"),
            new Message(Message.Role.ASSISTANT, "", Map.of(
                "toolCallId", "call_1",
                "toolName", "file_list",
                "toolArguments", Map.of("path", "."),
                "reasoningContent", "hidden chain"),
                java.time.Instant.now()),
            new Message(Message.Role.TOOL, "Listed files", Map.of("toolCallId", "call_1"), java.time.Instant.now()),
            new Message(Message.Role.USER, "now explain")),
        List.of(),
        Map.of())).toList();

    assertTrue(!http.lastJson.contains("\"tool_calls\""), http.lastJson);
    assertTrue(!http.lastJson.contains("\"role\":\"tool\""), http.lastJson);
    assertTrue(http.lastJson.contains("Tool result: Listed files"), http.lastJson);
  }

  @Test
  void chatCompletionParserEmitsReasoningDelta() {
    var http = new FakeProviderHttpClient();
    http.streamLines = List.of(
        "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking\"}}]}",
        "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    var client = new DeepSeekProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("deepseek", "https://api.deepseek.com", "key", "deepseek-v4-pro", "high", true);

    var events = client.stream(new ProviderRequest("deepseek", settings.selectedModel(), settings, ThinkingOption.of("high", "High"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    assertEquals("thinking", assertInstanceOf(ProviderEvent.ReasoningDelta.class, events.getFirst()).text());
  }

  @Test
  void anthropicParsesTextAndModelList() {
    var http = new FakeProviderHttpClient();
    http.getResponse = new HttpTextResponse(200, """
        {"data":[{"id":"claude-sonnet-4-20250514","display_name":"Claude Sonnet 4"}]}
        """);
    var client = new AnthropicProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));

    var events = client.parseData("""
        {"type":"content_block_delta","delta":{"type":"text_delta","text":"hello"}}
        """);
    var models = client.refreshModels(new ProviderSettings("anthropic", "https://api.anthropic.com/v1", "key", "claude-sonnet-4-20250514", "off", true));

    assertEquals("hello", ((ProviderEvent.AssistantDelta) events.getFirst()).text());
    assertEquals("claude-sonnet-4-20250514", models.getFirst().id());
  }

  @Test
  void openAiAndAnthropicHttpErrorsSurfaceProviderMessages() {
    var http = new FakeProviderHttpClient();
    http.streamStatusCode = 401;
    http.streamLines = List.of("{\"error\":{\"message\":\"invalid api key\"}}");
    var openAi = new OpenAiProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var openAiSettings = new ProviderSettings("openai", "https://api.openai.com/v1", "key", "gpt-5.4", "none", true);

    var openAiEvents = openAi.stream(new ProviderRequest("openai", openAiSettings.selectedModel(), openAiSettings, ThinkingOption.of("none", "None"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    assertTrue(assertInstanceOf(ProviderEvent.Error.class, openAiEvents.getFirst()).message().contains("invalid api key"));

    http.streamLines = List.of("{\"error\":{\"message\":\"auth failed\"}}");
    var anthropic = new AnthropicProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var anthropicSettings = new ProviderSettings("anthropic", "https://api.anthropic.com/v1", "key", "claude-sonnet-4-20250514", "off", true);

    var anthropicEvents = anthropic.stream(new ProviderRequest("anthropic", anthropicSettings.selectedModel(), anthropicSettings, ThinkingOption.of("off", "Off"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    assertTrue(assertInstanceOf(ProviderEvent.Error.class, anthropicEvents.getFirst()).message().contains("auth failed"));
  }

  @Test
  void anthropicParserBuffersFragmentedToolCalls() {
    var http = new FakeProviderHttpClient();
    http.streamLines = List.of(
        "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"file_list\",\"input\":{}}}",
        "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\"}}",
        "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\".\\\"}\"}}",
        "data: {\"type\":\"content_block_stop\",\"index\":1}");
    var client = new AnthropicProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("anthropic", "https://api.anthropic.com/v1", "key", "claude-sonnet-4-20250514", "off", true);

    var events = client.stream(new ProviderRequest("anthropic", settings.selectedModel(), settings, ThinkingOption.of("off", "Off"), "system", List.of(new Message(Message.Role.USER, "list")), List.of(), Map.of())).toList();

    var call = (ProviderEvent.ToolCallRequested) events.getFirst();
    assertEquals("file_list", call.call().name());
    assertEquals(".", call.call().arguments().get("path"));
  }
}
