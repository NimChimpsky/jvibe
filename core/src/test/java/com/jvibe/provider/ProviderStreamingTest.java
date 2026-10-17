package com.jvibe.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.jvibe.config.JvibePaths;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderStreamingTest {
  @TempDir Path home;

  @Test
  void allAdaptersDeliverTextBeforeReadingTheNextNetworkChunk() {
    var config = new ProviderConfigService(JvibePaths.under(home));
    var http = new FakeProviderHttpClient();
    assertIncremental(new JvibeProviderClient(http, config)::parseChatSse,
        "{\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}",
        "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");

  }

  @Test
  void preservesWhitespaceTokens() {
    var config = new ProviderConfigService(JvibePaths.under(home));
    var client = new JvibeProviderClient(new FakeProviderHttpClient(), config);
    var chunks = List.of("hello", " ", "world", "\n");
    var lines = Stream.concat(chunks.stream().map(chunk -> "data: " + JsonSupport.toJson(
        java.util.Map.of("choices", List.of(java.util.Map.of("delta", java.util.Map.of("content", chunk)))))),
        Stream.of("data: [DONE]"));
    var text = new StringBuilder();
    client.parseChatSse(lines).forEach(event -> {
      if (event instanceof ProviderEvent.AssistantDelta delta) text.append(delta.text());
    });
    assertEquals("hello world\n", text.toString());
  }

  @Test
  void closesEarlyAndReportsTruncatedStreams() {
    var closed = new AtomicBoolean();
    var source = Stream.of("data: partial").onClose(() -> closed.set(true));
    var events = ProviderEventStream.parse("test", source,
        data -> List.of(new ProviderEvent.AssistantDelta(data))).toList();
    assertInstanceOf(ProviderEvent.Error.class, events.getLast());
    assertTrue(closed.get());

    closed.set(false);
    try (var stream = ProviderEventStream.parse("test", Stream.of("data: first", "data: second")
        .onClose(() -> closed.set(true)), data -> List.of(new ProviderEvent.AssistantDelta(data)))) {
      assertTrue(stream.findFirst().isPresent());
    }
    assertTrue(closed.get());
  }

  private void assertIncremental(Function<Stream<String>, Stream<ProviderEvent>> parser, String first, String... rest) {
    var consumed = new AtomicInteger();
    var closed = new AtomicBoolean();
    try (var events = parser.apply(Stream.concat(Stream.of(first), Stream.of(rest)).map(data -> "data: " + data)
        .peek(line -> consumed.incrementAndGet()).onClose(() -> closed.set(true)))) {
      assertEquals(0, consumed.get());
      var iterator = events.iterator();
      assertEquals("hi", assertInstanceOf(ProviderEvent.AssistantDelta.class, iterator.next()).text());
      assertEquals(1, consumed.get());
      ProviderEvent completion;
      do { completion = iterator.next(); } while (completion instanceof ProviderEvent.AssistantMetadata);
      assertInstanceOf(ProviderEvent.Done.class, completion);
      assertTrue(closed.get());
    }
  }
}
