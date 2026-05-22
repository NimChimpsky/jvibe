package com.jvibe.provider;

import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;

@Singleton
public final class DeepSeekProviderClient extends AbstractChatCompletionsProviderClient {
  public DeepSeekProviderClient(ProviderHttpClient http, ProviderConfigService configService) {
    super(http, configService);
  }

  @Override
  public String providerId() {
    return "deepseek";
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(
        model("deepseek-v4-flash", "DeepSeek V4 Flash"),
        model("deepseek-v4-pro", "DeepSeek V4 Pro"));
  }

  @Override
  protected String apiKeyName() {
    return "DEEPSEEK_API_KEY";
  }

  @Override
  protected int defaultContextWindow() {
    return 1_000_000;
  }

  @Override
  protected List<ThinkingOption> thinkingOptions() {
    return configService().definition("deepseek").map(ProviderDefinition::thinkingOptions).orElse(List.of());
  }

  @Override
  protected void applyThinking(Map<String, Object> body, ThinkingOption thinking) {
    var effort = thinking == null ? "high" : thinking.id();
    if ("disabled".equals(effort)) {
      body.put("thinking", Map.of("type", "disabled"));
      return;
    }
    body.put("thinking", Map.of("type", "enabled"));
    body.put("reasoning_effort", "max".equals(effort) ? "max" : "high");
  }

  private ModelDescriptor model(String id, String label) {
    return new ModelDescriptor("deepseek", id, label, 1_000_000, true, thinkingOptions(), ModelDescriptor.Source.STATIC);
  }
}
