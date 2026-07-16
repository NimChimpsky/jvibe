package com.jvibe.gui.analysis;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

@Singleton
public final class JfrAnalysisService {
  private static final int MAX_EVENT_ROWS = 100_000;
  private static final int MAX_STACK_TEXT_CHARS = 4 * 1024 * 1024;

  public JfrAnalysis analyze(Path file) throws IOException {
    var summaries = new LinkedHashMap<String, SummaryAccumulator>();
    var events = new ArrayList<JfrEventRow>();
    var methods = new LinkedHashMap<String, Long>();
    var classes = new LinkedHashMap<String, Long>();
    var stacks = new StringBuilder();
    long eventCount = 0;
    var eventsTruncated = false;
    var stacksTruncated = false;
    Instant first = null;
    Instant last = null;

    try (var recording = new RecordingFile(file)) {
      while (recording.hasMoreEvents()) {
        var event = recording.readEvent();
        var type = event.getEventType().getName();
        var label = event.getEventType().getLabel();
        var start = event.getStartTime();
        var duration = event.getDuration();
        eventCount++;
        first = min(first, start);
        last = max(last, event.getEndTime());
        summaries.computeIfAbsent(type, ignored -> new SummaryAccumulator(type, label)).add(start, duration);
        if (events.size() < MAX_EVENT_ROWS) {
          events.add(new JfrEventRow(type, start, duration, describe(event)));
        } else {
          eventsTruncated = true;
        }
        stacksTruncated |= addStack(event.getStackTrace(), methods, classes, stacks);
      }
    }

    var eventSummaries = summaries.values().stream()
        .map(SummaryAccumulator::summary)
        .sorted(Comparator.comparingLong(JfrEventSummary::count).reversed())
        .toList();
    return new JfrAnalysis(
        file,
        first,
        last,
        first == null || last == null ? Duration.ZERO : Duration.between(first, last),
        eventCount,
        eventSummaries.size(),
        eventsTruncated,
        stacksTruncated,
        eventSummaries,
        events,
        hotFrames(methods),
        hotFrames(classes),
        stacks.isEmpty()
            ? stacksTruncated
                ? "Stack trace detail exceeded the display limit. Hot-frame summaries are still available."
                : "No stack traces found in this recording."
            : stacks.toString());
  }

  private static boolean addStack(
      RecordedStackTrace stack,
      Map<String, Long> methods,
      Map<String, Long> classes,
      StringBuilder stacks) {
    if (stack == null || stack.getFrames().isEmpty()) {
      return false;
    }
    var rendered = new StringBuilder("Stack trace\n");
    for (RecordedFrame frame : stack.getFrames()) {
      var method = frame.getMethod();
      if (method == null) {
        continue;
      }
      var type = method.getType();
      var className = type == null ? "<unknown>" : type.getName();
      var methodName = className + "." + method.getName();
      methods.merge(methodName, 1L, Long::sum);
      classes.merge(className, 1L, Long::sum);
      rendered.append("  at ").append(methodName).append('\n');
    }
    rendered.append('\n');
    if (stacks.length() + rendered.length() <= MAX_STACK_TEXT_CHARS) {
      stacks.append(rendered);
      return false;
    }
    return true;
  }

  private static List<JfrHotFrame> hotFrames(Map<String, Long> values) {
    return values.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(50)
        .map(entry -> new JfrHotFrame(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static String describe(RecordedEvent event) {
    var label = event.getEventType().getLabel();
    var builder = new StringBuilder(label == null || label.isBlank() ? event.getEventType().getName() : label);
    var thread = event.getThread();
    if (thread != null) {
      builder.append(" thread=").append(thread.getJavaName());
    }
    return builder.toString();
  }

  private static Instant min(Instant current, Instant next) {
    if (next == null) {
      return current;
    }
    return current == null || next.isBefore(current) ? next : current;
  }

  private static Instant max(Instant current, Instant next) {
    if (next == null) {
      return current;
    }
    return current == null || next.isAfter(current) ? next : current;
  }

  private static final class SummaryAccumulator {
    private final String type;
    private final String label;
    private long count;
    private Instant first;
    private Instant last;
    private Duration totalDuration = Duration.ZERO;

    private SummaryAccumulator(String type, String label) {
      this.type = type;
      this.label = label == null || label.isBlank() ? type : label;
    }

    private void add(Instant start, Duration duration) {
      count++;
      first = min(first, start);
      last = max(last, start);
      if (duration != null) {
        totalDuration = totalDuration.plus(duration);
      }
    }

    private JfrEventSummary summary() {
      return new JfrEventSummary(type, label, count, first, last, totalDuration);
    }
  }
}
