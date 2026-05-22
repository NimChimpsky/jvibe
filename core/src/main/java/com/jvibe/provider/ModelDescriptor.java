package com.jvibe.provider;

import java.util.List;

public record ModelDescriptor(
    String provider,
    String id,
    String displayName,
    int contextWindow,
    boolean supportsTools,
    List<ThinkingOption> thinkingOptions,
    Source source) {
  public enum Source {
    STATIC,
    DYNAMIC
  }

  public ModelDescriptor {
    thinkingOptions = thinkingOptions == null ? List.of() : List.copyOf(thinkingOptions);
    source = source == null ? Source.STATIC : source;
  }

  public ModelDescriptor(String provider, String id, String displayName, int contextWindow, boolean supportsTools) {
    this(provider, id, displayName, contextWindow, supportsTools, List.of(), Source.STATIC);
  }
}
