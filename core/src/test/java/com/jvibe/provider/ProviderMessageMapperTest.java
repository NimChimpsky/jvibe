package com.jvibe.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.session.Message;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderMessageMapperTest {
  @Test
  void responsesInputPreservesFunctionCallBeforeItsOutput() {
    var settings = new ProviderSettings("openai", "https://api.openai.com/v1", "key", "gpt", "none", true);
    var request = new ProviderRequest(
        "openai",
        "gpt",
        settings,
        ThinkingOption.of("none", "None"),
        "system",
        List.of(
            new Message(Message.Role.USER, "inspect"),
            new Message(
                Message.Role.ASSISTANT,
                "",
                Map.of("toolCallId", "call_1", "toolName", "file_read", "toolArguments", Map.of("path", "README.md")),
                Instant.now()),
            new Message(Message.Role.TOOL, "{\"content\":\"text\"}", Map.of("toolCallId", "call_1"), Instant.now())),
        List.of(),
        Map.of());

    var input = ProviderMessageMapper.openAiInput(request);

    assertEquals("function_call", input.get(1).get("type"));
    assertEquals("call_1", input.get(1).get("call_id"));
    assertEquals("function_call_output", input.get(2).get("type"));
    assertEquals("call_1", input.get(2).get("call_id"));
  }

  @Test
  void chatMessagesPreserveOneAssistantBatchAndEveryToolResult() {
    var settings = new ProviderSettings("deepseek", "https://api.deepseek.com", "key", "deepseek", "high", true);
    var request = new ProviderRequest(
        "deepseek",
        "deepseek",
        settings,
        ThinkingOption.of("high", "High"),
        "system",
        List.of(
            new Message(Message.Role.USER, "inspect"),
            new Message(Message.Role.ASSISTANT, "Checking.", Map.of(
                "reasoningContent", "reasoning",
                "toolCalls", List.of(
                    Map.of("id", "call_1", "name", "file_read", "arguments", Map.of("path", "a")),
                    Map.of("id", "call_2", "name", "file_read", "arguments", Map.of("path", "b")))), Instant.now()),
            new Message(Message.Role.TOOL, "a", Map.of("toolCallId", "call_1"), Instant.now()),
            new Message(Message.Role.TOOL, "b", Map.of("toolCallId", "call_2"), Instant.now())),
        List.of(),
        Map.of());

    var messages = ProviderMessageMapper.chatMessages(request, true);

    assertEquals(5, messages.size());
    assertEquals("assistant", messages.get(2).get("role"));
    assertEquals(2, ((List<?>) messages.get(2).get("tool_calls")).size());
    assertEquals("reasoning", messages.get(2).get("reasoning_content"));
    assertTrue(messages.subList(3, 5).stream().allMatch(message -> "tool".equals(message.get("role"))));
  }
}
