package com.jvibe.gui.controller;

import com.jvibe.provider.ModelDescriptor;
import com.jvibe.provider.ProviderConfigService;
import com.jvibe.provider.ProviderDefinition;
import com.jvibe.provider.ProviderRegistry;
import com.jvibe.provider.ProviderSettings;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public final class ProviderSettingsController {
  private final ProviderConfigService configService;
  private final ProviderRegistry registry;

  public ProviderSettingsController(ProviderConfigService configService, ProviderRegistry registry) {
    this.configService = configService;
    this.registry = registry;
  }

  public List<ProviderDefinition> configurableProviders() {
    return configService.definitions().stream()
        .filter(definition -> !"local".equals(definition.id()))
        .toList();
  }

  public ProviderSettings settings(String provider) {
    return configService.settings(provider);
  }

  public ProviderSettings effective(ProviderSettings settings) {
    if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
      return settings;
    }
    var effective = configService.effectiveSettings(settings.provider());
    return new ProviderSettings(
        settings.provider(),
        settings.baseUrl(),
        effective.apiKey(),
        settings.selectedModel(),
        settings.thinking(),
        settings.enabled());
  }

  public void save(ProviderSettings settings) {
    configService.save(settings);
  }

  public void saveAll(List<ProviderSettings> settings) {
    settings.forEach(configService::save);
  }

  public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
    return registry.require(settings.provider()).refreshModels(effective(settings));
  }
}
