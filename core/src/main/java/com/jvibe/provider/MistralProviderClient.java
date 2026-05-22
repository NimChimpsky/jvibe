package com.jvibe.provider;

import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;

@Singleton
public final class MistralProviderClient extends AbstractChatCompletionsProviderClient {
  public MistralProviderClient(ProviderHttpClient http, ProviderConfigService configService) {
    super(http, configService);
  }

  @Override
  public String providerId() {
    return "mistral";
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(
        model("mistral-large-latest", "Mistral Large", 128_000),
        model("mistral-medium-latest", "Mistral Medium", 128_000),
        model("devstral-latest", "Devstral", 128_000));
  }

  @Override
  protected String apiKeyName() {
    return "MISTRAL_API_KEY";
  }

  @Override
  protected int defaultContextWindow() {
    return 128_000;
  }

  @Override
  protected List<ThinkingOption> thinkingOptions() {
    return configService().definition("mistral").map(ProviderDefinition::thinkingOptions).orElse(List.of());
  }

  @Override
  protected void applyThinking(Map<String, Object> body, ThinkingOption thinking) {
    if (thinking != null && "high".equals(thinking.id())) {
      body.put("reasoning_effort", "high");
      body.put("prompt_mode", "reasoning");
    }
  }

  private ModelDescriptor model(String id, String label, int contextWindow) {
    return new ModelDescriptor("mistral", id, label, contextWindow, true, thinkingOptions(), ModelDescriptor.Source.STATIC);
  }
}
