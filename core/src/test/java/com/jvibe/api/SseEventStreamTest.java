package com.jvibe.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.tool.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SseEventStreamTest {
  private static String delta(String content) {
    return "data: " + JsonSupport.toJson(Map.of("choices", List.of(Map.of("delta", Map.of("content", content)))));
  }

  @Test
  void deliversTextBeforeReadingTheNextChunk() {
    var consumed = new AtomicInteger();
    var closed = new AtomicBoolean();
    var lines = Stream.of(delta("hi"), "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}")
        .peek(line -> consumed.incrementAndGet())
        .onClose(() -> closed.set(true));
    try (var events = SseEventStream.parse(lines)) {
      var iterator = events.iterator();
      assertEquals(new StreamEvent.AssistantDelta("hi"), iterator.next());
      assertEquals(1, consumed.get());
      assertInstanceOf(StreamEvent.Done.class, iterator.next());
      assertTrue(closed.get());
    }
  }

  @Test
  void preservesWhitespaceTokens() {
    var text = new StringBuilder();
    SseEventStream.parse(Stream.concat(Stream.of("hello", " ", "world", "\n").map(SseEventStreamTest::delta),
        Stream.of("data: [DONE]"))).forEach(event -> {
          if (event instanceof StreamEvent.AssistantDelta delta) text.append(delta.text());
        });
    assertEquals("hello world\n", text.toString());
  }

  @Test
  void assemblesFragmentedToolCallsByIndex() {
    var events = SseEventStream.parse(Stream.of(
        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"ssh_exec\",\"arguments\":\"{\\\"serverId\\\":\"}}]}}]}",
        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"web01\\\"}\"}}]}}]}",
        "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}")).toList();

    assertEquals(List.of(
        new StreamEvent.ToolCallRequested(new ToolCall("call_1", "ssh_exec", Map.of("serverId", "web01"))),
        new StreamEvent.Done()), events);
  }

  @Test
  void reportsServerErrorsAndTruncatedStreams() {
    assertEquals(List.of(new StreamEvent.Error("upstream exploded")),
        SseEventStream.parse(Stream.of("data: {\"error\":{\"message\":\"upstream exploded\"}}")).toList());

    var closed = new AtomicBoolean();
    var events = SseEventStream.parse(Stream.of(delta("partial")).onClose(() -> closed.set(true))).toList();
    assertInstanceOf(StreamEvent.Error.class, events.getLast());
    assertTrue(closed.get());
  }
}
