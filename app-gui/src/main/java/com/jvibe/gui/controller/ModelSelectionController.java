package com.jvibe.gui.controller;

import com.jvibe.provider.ModelDescriptor;
import com.jvibe.provider.ProviderConfigService;
import com.jvibe.provider.ProviderRegistry;
import com.jvibe.session.AgentSession;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Singleton
public final class ModelSelectionController {
  private final ProviderConfigService configService;
  private final ProviderRegistry registry;

  public ModelSelectionController(ProviderConfigService configService, ProviderRegistry registry) {
    this.configService = configService;
    this.registry = registry;
  }

  public List<ModelChoice> choices() {
    var choices = new ArrayList<ModelChoice>();
    for (var settings : configService.load().providers()) {
      if (!settings.enabled() || "local".equals(settings.provider())) {
        continue;
      }
      registry.find(settings.provider()).ifPresent(client -> {
        var models = new LinkedHashSet<String>();
        models.add(settings.selectedModel());
        client.models().stream().map(ModelDescriptor::id).forEach(models::add);
        models.stream()
            .map(model -> new ModelChoice(settings.provider(), model))
            .forEach(choices::add);
      });
    }
    return choices;
  }

  public void apply(AgentSession session, String displayValue) {
    var slash = displayValue == null ? -1 : displayValue.indexOf('/');
    if (slash <= 0 || slash == displayValue.length() - 1) {
      return;
    }
    session.selectModel(displayValue.substring(0, slash), displayValue.substring(slash + 1));
  }
}
