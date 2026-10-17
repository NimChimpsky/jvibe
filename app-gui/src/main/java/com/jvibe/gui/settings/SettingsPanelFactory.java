package com.jvibe.gui.settings;

import com.jvibe.config.ConfigService;
import com.jvibe.gui.controller.ProviderSettingsController;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import javax.swing.JComponent;

@Singleton
public final class SettingsPanelFactory {
  private final ConfigService config;
  private final ProviderSettingsController account;

  public SettingsPanelFactory(@External ConfigService config, ProviderSettingsController account) {
    this.config = config;
    this.account = account;
  }

  public JComponent create(JComponent theme, Runnable saved) {
    return new JvibeSettingsPanel(config, account, theme, saved);
  }
}
