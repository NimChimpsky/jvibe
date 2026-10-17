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
  void chatCompletionHttpErrorSurfacesProviderMessage() {
    var http = new FakeProviderHttpClient();
    http.streamStatusCode = 400;
    http.streamLines = List.of("{\"error\":{\"message\":\"model not found\"}}");
    var client = new JvibeProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, "key", "jvibe-coder", "disabled", true);

    var events = client.stream(new ProviderRequest("jvibe", settings.selectedModel(), settings, ThinkingOption.of("disabled", "Off"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    var error = assertInstanceOf(ProviderEvent.Error.class, events.getFirst());
    assertTrue(error.fatal());
    assertTrue(error.message().contains("Provider HTTP 400"));
    assertTrue(error.message().contains("model not found"));
  }

  @Test
  void chatCompletionEmptyStreamIsAnError() {
    var http = new FakeProviderHttpClient();
    var client = new JvibeProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, "key", "jvibe-coder", "disabled", true);

    var events = client.stream(new ProviderRequest("jvibe", settings.selectedModel(), settings, ThinkingOption.of("disabled", "Off"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    var error = assertInstanceOf(ProviderEvent.Error.class, events.getFirst());
    assertTrue(error.message().contains("returned no streaming response data"));
  }

  @Test
  void jvibeUsesChatCompletionsAndFallsBackToStaticModels() {
    var http = new FakeProviderHttpClient();
    http.getResponse = new HttpTextResponse(500, "{}");
    http.streamLines = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    var client = new JvibeProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("jvibe", "https://untrusted.invalid", "key", "jvibe-coder", "high", true);

    var models = client.refreshModels(settings);
    client.stream(new ProviderRequest("jvibe", "jvibe-coder", settings, ThinkingOption.of("high", "High"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    assertEquals(List.of("jvibe-coder", "jvibe-agent"), models.stream().map(ModelDescriptor::id).toList());
    assertEquals(JvibeProviderClient.BASE_URL + "/chat/completions", http.lastUri.toString());
    assertTrue(http.lastJson.contains("\"reasoning_effort\":\"high\""));
  }

  @Test
  void jvibeCreditsUsesApiV1CreditsEndpoint() {
    var http = new FakeProviderHttpClient();
    http.getResponse = new HttpTextResponse(200, "{\"object\":\"credits\",\"credits_balance\":12.5}");
    var client = new JvibeProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, "key", "jvibe-coder", "none", true);

    assertEquals("Credits: 12.50", client.creditsLabel(settings));
    assertEquals(JvibeProviderClient.BASE_URL + "/credits", http.lastUri.toString());
    assertEquals("https://jvibe.net/api/v1", JvibeProviderClient.BASE_URL);
    assertEquals("https://jvibe.net/account", JvibeProviderClient.ACCOUNT_URL);
  }

  @Test
  void chatCompletionParserBuffersFragmentedToolCalls() {
    var http = new FakeProviderHttpClient();
    http.streamLines = List.of(
        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"file_read\",\"arguments\":\"{\"}}]}}]}",
        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"path\\\":\\\"README.md\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}");
    var client = new JvibeProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, "key", "jvibe-agent", "disabled", true);

    var events = client.stream(new ProviderRequest("jvibe", "jvibe-agent", settings, ThinkingOption.of("disabled", "Off"), "system", List.of(new Message(Message.Role.USER, "read")), List.of(), Map.of())).toList();

    var call = (ProviderEvent.ToolCallRequested) events.getFirst();
    assertEquals("file_read", call.call().name());
    assertEquals("README.md", call.call().arguments().get("path"), call.call().arguments().toString());
  }

  @Test
  void jvibePreservesReasoningContentForToolFollowUp() {
    var http = new FakeProviderHttpClient();
    http.streamLines = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    var client = new JvibeProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, "key", "jvibe-agent", "high", true);

    client.stream(new ProviderRequest(
        "jvibe",
        "jvibe-agent",
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
  void jvibeFlattensCompletedToolHistoryBeforeNewUserPrompt() {
    var http = new FakeProviderHttpClient();
    http.streamLines = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
    var client = new JvibeProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, "key", "jvibe-agent", "high", true);

    client.stream(new ProviderRequest(
        "jvibe",
        "jvibe-agent",
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
    var client = new JvibeProviderClient(http, new ProviderConfigService(JvibePaths.under(home)));
    var settings = new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, "key", "jvibe-agent", "high", true);

    var events = client.stream(new ProviderRequest("jvibe", settings.selectedModel(), settings, ThinkingOption.of("high", "High"), "system", List.of(new Message(Message.Role.USER, "hello")), List.of(), Map.of())).toList();

    assertEquals("thinking", assertInstanceOf(ProviderEvent.ReasoningDelta.class, events.getFirst()).text());
  }

}
