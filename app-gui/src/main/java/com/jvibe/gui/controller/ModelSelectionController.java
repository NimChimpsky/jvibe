package com.jvibe.gui.controller;

import com.jvibe.provider.ProviderConfigService;
import com.jvibe.provider.JvibeProviderClient;
import com.jvibe.session.AgentSession;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.util.LinkedHashSet;
import java.util.List;

@Singleton
public final class ModelSelectionController {
  private final ProviderConfigService config;
  private final JvibeProviderClient client;

  public ModelSelectionController(@External ProviderConfigService config, @External JvibeProviderClient client) {
    this.config = config;
    this.client = client;
  }

  public List<ModelChoice> choices() {
    var models = new LinkedHashSet<String>();
    models.add(config.settings("jvibe").selectedModel());
    client.models().forEach(model -> models.add(model.id()));
    return models.stream().map(model -> new ModelChoice("jvibe", "JVibe", model)).toList();
  }

  public void bind(javax.swing.JComboBox<String> selector) {
    Runnable refresh = () -> {
      var selected = selector.getSelectedItem();
      selector.setModel(new javax.swing.DefaultComboBoxModel<>(
          choices().stream().map(ModelChoice::display).toArray(String[]::new)));
      if (selected != null && choices().stream().anyMatch(choice -> choice.display().equals(selected))) {
        selector.setSelectedItem(selected);
      }
    };
    refresh.run();
    selector.addHierarchyListener(event -> {
      if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && selector.isShowing()) refresh.run();
    });
  }

  public void apply(AgentSession session, String value) {
    choices().stream().filter(choice -> choice.display().equals(value)).findFirst()
        .ifPresent(choice -> session.selectModel("jvibe", choice.model()));
  }

  public String display(String provider, String model) { return model; }
}
