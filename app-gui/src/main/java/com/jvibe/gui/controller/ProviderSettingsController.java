package com.jvibe.gui.controller;

import com.jvibe.provider.*;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.util.List;

/** Account settings for the fixed JVibe.net service; provider/endpoint selection is not exposed. */
@Singleton
public final class ProviderSettingsController {
  private final ProviderConfigService config;
  private final JvibeProviderClient client;

  public ProviderSettingsController(@External ProviderConfigService config, @External JvibeProviderClient client) {
    this.config = config;
    this.client = client;
  }

  public ProviderSettings settings() { return config.settings("jvibe"); }
  public void save(ProviderSettings settings) { config.save(settings); }
  public List<ThinkingOption> thinkingOptions() { return config.definition("jvibe").orElseThrow().thinkingOptions(); }

  public List<ModelDescriptor> refreshModels(ProviderSettings draft) {
    return client.refreshModels(effective(draft));
  }

  public String credits(ProviderSettings draft) { return client.creditsLabel(effective(draft)); }

  private ProviderSettings effective(ProviderSettings draft) {
    return draft.apiKey() == null || draft.apiKey().isBlank()
        ? draft.withApiKey(config.effectiveSettings("jvibe").apiKey()) : draft;
  }
}
