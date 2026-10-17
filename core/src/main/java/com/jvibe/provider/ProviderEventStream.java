package com.jvibe.provider;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Pulls only enough SSE lines to deliver the next event and owns the HTTP body. */
final class ProviderEventStream {
  private ProviderEventStream() {}

  static Stream<ProviderEvent> parse(
      String provider, Stream<String> lines, Function<String, List<ProviderEvent>> parseData) {
    var input = lines.iterator();
    var pending = new ArrayDeque<ProviderEvent>();
    var source = new Spliterators.AbstractSpliterator<ProviderEvent>(Long.MAX_VALUE, Spliterator.ORDERED) {
      private boolean finished;
      private final StringBuilder diagnostic = new StringBuilder();

      @Override
      public boolean tryAdvance(Consumer<? super ProviderEvent> action) {
        while (pending.isEmpty() && !finished) {
          try {
            if (!input.hasNext()) {
              pending.add(ProviderStreamErrors.emptyResponse(provider, diagnostic.toString()));
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
            pending.addAll(parseData.apply(data));
            if (pending.stream().anyMatch(event -> event instanceof ProviderEvent.Done
                || event instanceof ProviderEvent.Error error && error.fatal())) {
              finish();
            }
          } catch (RuntimeException e) {
            pending.add(new ProviderEvent.Error("Unable to read " + provider + " response: "
                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), true));
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
}
