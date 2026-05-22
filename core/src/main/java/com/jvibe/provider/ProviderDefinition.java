package com.jvibe.provider;

import io.avaje.jsonb.Json;
import java.util.List;

@Json
public record ProviderDefinition(
    String id,
    String displayName,
    String baseUrl,
    String apiKeyEnv,
    String defaultModel,
    List<ThinkingOption> thinkingOptions) {
  public ProviderDefinition {
    thinkingOptions = thinkingOptions == null ? List.of() : List.copyOf(thinkingOptions);
  }
}
