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
  void chatMessagesPreserveOneAssistantBatchAndEveryToolResult() {
    var settings = new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, "key", "jvibe", "high", true);
    var request = new ProviderRequest(
        "jvibe",
        "jvibe",
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
