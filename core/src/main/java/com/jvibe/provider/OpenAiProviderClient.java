package com.jvibe.provider;

import java.net.http.HttpClient;
import java.util.List;
import java.util.stream.Stream;

public final class OpenAiProviderClient implements ProviderClient {
  private final String apiKey;
  private final HttpClient httpClient;

  public OpenAiProviderClient(String apiKey) {
    this(apiKey, HttpClient.newHttpClient());
  }

  public OpenAiProviderClient(String apiKey, HttpClient httpClient) {
    this.apiKey = apiKey;
    this.httpClient = httpClient;
  }

  @Override
  public Stream<ProviderEvent> stream(ProviderRequest request) {
    if (apiKey == null || apiKey.isBlank()) {
      return Stream.of(new ProviderEvent.Error("OPENAI_API_KEY is not configured", true));
    }
    return Stream.of(new ProviderEvent.Error("OpenAI streaming transport is not implemented yet", true));
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(
        new ModelDescriptor("openai", "gpt-5.2", "GPT-5.2", 128000, true),
        new ModelDescriptor("openai", "gpt-5.4", "GPT-5.4", 128000, true));
  }

  HttpClient httpClient() {
    return httpClient;
  }
}
