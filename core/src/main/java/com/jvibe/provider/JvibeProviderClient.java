package com.jvibe.provider;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Singleton
public final class JvibeProviderClient extends AbstractChatCompletionsProviderClient {
  /** Desktop contract base URL — maps to jvibe-web {@code /api/v1/*} provider routes. */
  public static final String BASE_URL = "https://jvibe.net/api/v1";
  public static final String ACCOUNT_URL = "https://jvibe.net/account";

  private final ProviderHttpClient http;

  public JvibeProviderClient(ProviderHttpClient http, ProviderConfigService configService) {
    super(http, configService);
    this.http = http;
  }

  @Override
  public String providerId() {
    return "jvibe";
  }

  @Override
  java.net.URI endpoint(ProviderSettings settings, String path) {
    return java.net.URI.create(BASE_URL + path);
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(
        model("jvibe-coder", "JVibe Coder"),
        model("jvibe-agent", "JVibe Agent"));
  }

  /**
   * Fetches the account credit balance via {@code GET /api/v1/credits}.
   */
  public String creditsLabel(ProviderSettings settings) {
    var key = settings.apiKey();
    if (key == null || key.isBlank()) {
      return "Credits: configure API key";
    }
    var response = http.get(endpoint(settings, "/credits"), authHeaders(key));
    if (!response.success()) {
      return "Credits: unavailable";
    }
    var balance = JsonSupport.object(response.body()).get("credits_balance");
    if (!(balance instanceof Number number)) {
      return "Credits: unavailable";
    }
    var value = number.doubleValue();
    if (Math.rint(value) == value) {
      return "Credits: " + (long) value;
    }
    return "Credits: " + String.format(Locale.US, "%.2f", value);
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
