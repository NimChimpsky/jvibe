package com.jvibe.api;

import com.jvibe.tool.ToolCall;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Lazily parses OpenAI-format chat-completion SSE lines into {@link StreamEvent}s.
 * Pulls only enough lines to produce the next event and owns (closes) the HTTP body.
 */
final class SseEventStream {
  private SseEventStream() {}

  static Stream<StreamEvent> parse(Stream<String> lines) {
    var input = lines.iterator();
    var parser = new ChunkParser();
    var source = new Spliterators.AbstractSpliterator<StreamEvent>(Long.MAX_VALUE, Spliterator.ORDERED) {
      private final ArrayDeque<StreamEvent> pending = new ArrayDeque<>();
      private final StringBuilder diagnostic = new StringBuilder();
      private boolean finished;

      @Override
      public boolean tryAdvance(Consumer<? super StreamEvent> action) {
        while (pending.isEmpty() && !finished) {
          try {
            if (!input.hasNext()) {
              var suffix = diagnostic.isEmpty() ? "" : ": " + StreamErrors.message(diagnostic.toString());
              pending.add(new StreamEvent.Error("JVibe returned no streaming data" + suffix));
              finish();
              break;
            }
            var line = input.next();
            if (diagnostic.length() < 2048) {
              diagnostic.append(line, 0, Math.min(line.length(), 2048 - diagnostic.length())).append('\n');
            }
            if (!line.startsWith("data:")) continue;
            var data = line.substring(5).trim();
            if (data.isEmpty()) continue;
            pending.addAll(parser.parse(data));
            if (pending.stream().anyMatch(event -> event instanceof StreamEvent.Done
                || event instanceof StreamEvent.Error)) {
              finish();
            }
          } catch (RuntimeException e) {
            pending.add(new StreamEvent.Error("Unable to read JVibe response: "
                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            finish();
          }
        }
        if (pending.isEmpty()) return false;
        action.accept(pending.removeFirst());
        return true;
      }

      private void finish() {
        finished = true;
        lines.close();
      }
    };
    return StreamSupport.stream(source, false).onClose(lines::close);
  }

  /** Accumulates tool-call fragments by index and emits them once the turn finishes. */
  static final class ChunkParser {
    private final Map<String, ToolBuffer> toolCalls = new LinkedHashMap<>();

    List<StreamEvent> parse(String data) {
      var events = new ArrayList<StreamEvent>();
      if ("[DONE]".equals(data)) {
        flushToolCalls(events);
        events.add(new StreamEvent.Done());
        return events;
      }
      var chunk = JsonSupport.object(data);
      var error = JsonSupport.stringAt(chunk, "error", "message");
      if (!error.isBlank()) {
        events.add(new StreamEvent.Error(error));
        return events;
      }
      for (var choiceObject : JsonSupport.list(chunk.get("choices"))) {
        var choice = JsonSupport.map(choiceObject);
        var delta = JsonSupport.map(choice.get("delta"));
        var text = JsonSupport.string(delta.get("content"));
        if (!text.isEmpty()) {
          events.add(new StreamEvent.AssistantDelta(text));
        }
        for (var toolObject : JsonSupport.list(delta.get("tool_calls"))) {
          bufferToolCall(JsonSupport.map(toolObject));
        }
        if (!JsonSupport.string(choice.get("finish_reason")).isBlank()) {
          flushToolCalls(events);
          events.add(new StreamEvent.Done());
        }
      }
      return events;
    }

    private void bufferToolCall(Map<String, Object> tool) {
      var index = JsonSupport.string(tool.get("index"));
      var id = JsonSupport.string(tool.get("id"));
      var buffer = toolCalls.computeIfAbsent(index.isBlank() ? id : index, ignored -> new ToolBuffer());
      if (!id.isBlank()) buffer.id = id;
      var function = JsonSupport.map(tool.get("function"));
      var name = JsonSupport.string(function.get("name"));
      if (!name.isBlank()) buffer.name = name;
      buffer.arguments.append(JsonSupport.string(function.get("arguments")));
    }

    private void flushToolCalls(List<StreamEvent> events) {
      for (var buffer : toolCalls.values()) {
        if (!buffer.name.isBlank()) {
          events.add(new StreamEvent.ToolCallRequested(new ToolCall(
              buffer.id, buffer.name, JsonSupport.parseArguments(buffer.arguments.toString()))));
        }
      }
      toolCalls.clear();
    }
  }

  private static final class ToolBuffer {
    private String id = UUID.randomUUID().toString();
    private String name = "";
    private final StringBuilder arguments = new StringBuilder();
  }
}
