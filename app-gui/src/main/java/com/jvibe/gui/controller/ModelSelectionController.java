package com.jvibe.gui.controller;

import com.jvibe.provider.ModelDescriptor;
import com.jvibe.provider.ProviderConfigService;
import com.jvibe.provider.ProviderRegistry;
import com.jvibe.session.AgentSession;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Singleton
public final class ModelSelectionController {
  private final ProviderConfigService configService;
  private final ProviderRegistry registry;

  public ModelSelectionController(
      @External ProviderConfigService configService,
      @External ProviderRegistry registry) {
    this.configService = configService;
    this.registry = registry;
  }

  public List<ModelChoice> choices() {
    var choices = new ArrayList<ModelChoice>();
    for (var settings : configService.load().providers()) {
      if (!settings.enabled()) {
        continue;
      }
      registry.find(settings.provider()).ifPresent(client -> {
        var providerName = configService.definition(settings.provider())
            .map(com.jvibe.provider.ProviderDefinition::displayName)
            .orElse(settings.provider());
        var models = new LinkedHashSet<String>();
        models.add(settings.selectedModel());
        client.models().stream().map(ModelDescriptor::id).forEach(models::add);
        models.stream()
            .map(model -> new ModelChoice(settings.provider(), providerName, model))
            .forEach(choices::add);
      });
    }
    return choices;
  }

  public void apply(AgentSession session, String displayValue) {
    var selected = choices().stream()
        .filter(choice -> choice.display().equals(displayValue))
        .findFirst();
    if (selected.isPresent()) {
      session.selectModel(selected.get().provider(), selected.get().model());
      return;
    }
    var slash = displayValue == null ? -1 : displayValue.indexOf('/');
    if (slash <= 0 || slash == displayValue.length() - 1) {
      return;
    }
    session.selectModel(displayValue.substring(0, slash), displayValue.substring(slash + 1));
  }

  public String display(String provider, String model) {
    var providerName = configService.definition(provider)
        .map(com.jvibe.provider.ProviderDefinition::displayName)
        .orElse(provider);
    return providerName + "/" + model;
  }
}
