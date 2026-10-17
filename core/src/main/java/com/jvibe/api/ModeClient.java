package com.jvibe.api;

import java.util.stream.Stream;

/** Streams one model turn for a tab. The seam lets tests replace the JVibe service. */
@FunctionalInterface
public interface ModeClient {
  Stream<StreamEvent> stream(ModeRequest request);
}
