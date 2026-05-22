package com.jvibe.provider;

import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;

@Singleton
public final class QwenProviderClient extends AbstractChatCompletionsProviderClient {
  public static final String INTERNATIONAL_BASE_URL = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
  public static final String CHINA_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

  public QwenProviderClient(ProviderHttpClient http, ProviderConfigService configService) {
    super(http, configService);
  }

  @Override
  public String providerId() {
    return "qwen";
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(
        model("qwen3-coder-plus", "Qwen3 Coder Plus", 1_000_000),
        model("qwen3.6-plus", "Qwen3.6 Plus", 1_000_000),
        model("qwen3.6-max-preview", "Qwen3.6 Max Preview", 256_000));
  }

  @Override
  protected String apiKeyName() {
    return "DASHSCOPE_API_KEY";
  }

  @Override
  protected int defaultContextWindow() {
    return 1_000_000;
  }

  @Override
  protected List<ThinkingOption> thinkingOptions() {
    return configService().definition("qwen").map(ProviderDefinition::thinkingOptions).orElse(List.of());
  }

  @Override
  protected void applyThinking(Map<String, Object> body, ThinkingOption thinking) {
    body.put("enable_thinking", thinking != null && "on".equals(thinking.id()));
  }

  private ModelDescriptor model(String id, String label, int contextWindow) {
    return new ModelDescriptor("qwen", id, label, contextWindow, true, thinkingOptions(), ModelDescriptor.Source.STATIC);
  }
}
