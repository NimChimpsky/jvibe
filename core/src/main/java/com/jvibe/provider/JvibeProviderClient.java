package com.jvibe.provider;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public final class JvibeProviderClient extends AbstractChatCompletionsProviderClient {
  public static final String BASE_URL = "https://jvibe.net/api/v1";
  public static final String ACCOUNT_URL = "https://vibe.net/account";

  public JvibeProviderClient(ProviderHttpClient http, ProviderConfigService configService) {
    super(http, configService);
  }

  @Override
  public String providerId() {
    return "jvibe";
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(
        model("jvibe-coder", "JVibe Coder"),
        model("jvibe-agent", "JVibe Agent"));
  }

  @Override
  protected String apiKeyName() {
    return "JVIBE_API_KEY";
  }

  @Override
  protected int defaultContextWindow() {
    return 128_000;
  }

  @Override
  protected List<ThinkingOption> thinkingOptions() {
    return configService().definition("jvibe").map(ProviderDefinition::thinkingOptions).orElse(List.of());
  }

  @Override
  protected void applyThinking(Map<String, Object> body, ThinkingOption thinking) {
    if (thinking != null && !"none".equals(thinking.id())) {
      body.put("reasoning_effort", thinking.id());
    }
  }

  private ModelDescriptor model(String id, String label) {
    return new ModelDescriptor("jvibe", id, label, 128_000, true, thinkingOptions(), ModelDescriptor.Source.STATIC);
  }
}
