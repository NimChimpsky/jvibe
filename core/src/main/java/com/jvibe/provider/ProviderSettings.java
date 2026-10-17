package com.jvibe.provider;

import io.avaje.jsonb.Json;

@Json
public record ProviderSettings(
    String provider,
    String baseUrl,
    String apiKey,
    String selectedModel,
    String thinking,
    boolean enabled) {
  public ProviderSettings withApiKey(String value) {
    return new ProviderSettings(provider, baseUrl, value, selectedModel, thinking, enabled);
  }

  public ProviderSettings withSelectedModel(String value) {
    return new ProviderSettings(provider, baseUrl, apiKey, value, thinking, enabled);
  }

  public ProviderSettings withThinking(String value) {
    return new ProviderSettings(provider, baseUrl, apiKey, selectedModel, value, enabled);
  }

  @Override public String toString() {
    return "JVibe settings [model=" + selectedModel + ", thinking=" + thinking + ", apiKey=[REDACTED]]";
  }
}
