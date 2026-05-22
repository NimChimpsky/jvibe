package com.jvibe.provider;

import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;

@Singleton
public final class OpenRouterProviderClient extends AbstractChatCompletionsProviderClient {
  public OpenRouterProviderClient(ProviderHttpClient http, ProviderConfigService configService) {
    super(http, configService);
  }

  @Override
  public String providerId() {
    return "openrouter";
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(
        model("openrouter/pareto-code", "Pareto Code Router", 2_000_000),
        model("openrouter/auto", "Auto Router", 2_000_000));
  }

  @Override
  protected String apiKeyName() {
    return "OPENROUTER_API_KEY";
  }

  @Override
  protected int defaultContextWindow() {
    return 2_000_000;
  }

  @Override
  protected List<ThinkingOption> thinkingOptions() {
    return configService().definition("openrouter").map(ProviderDefinition::thinkingOptions).orElse(List.of());
  }

  @Override
  protected void applyThinking(Map<String, Object> body, ThinkingOption thinking) {
    // OpenRouter routes to heterogeneous backends, so model-specific reasoning is not enabled in v1.
  }

  @Override
  protected boolean supportsTools(Map<String, Object> model) {
    var supported = JsonSupport.list(model.get("supported_parameters"));
    if (!supported.isEmpty()) {
      return supported.stream().map(Object::toString).anyMatch(value -> "tools".equals(value) || "tool_choice".equals(value));
    }
    return true;
  }

  private ModelDescriptor model(String id, String label, int contextWindow) {
    return new ModelDescriptor("openrouter", id, label, contextWindow, true, thinkingOptions(), ModelDescriptor.Source.STATIC);
  }
}
