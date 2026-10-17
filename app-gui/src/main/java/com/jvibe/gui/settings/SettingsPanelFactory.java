package com.jvibe.gui.settings;

import com.jvibe.api.AccountService;
import com.jvibe.api.JvibeClient;
import com.jvibe.config.ConfigService;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import javax.swing.JComponent;

@Singleton
public final class SettingsPanelFactory {
  private final ConfigService config;
  private final AccountService accounts;
  private final JvibeClient client;

  public SettingsPanelFactory(
      @External ConfigService config,
      @External AccountService accounts,
      @External JvibeClient client) {
    this.config = config;
    this.accounts = accounts;
    this.client = client;
  }

  public JComponent create(JComponent themeSelector) {
    return new JvibeSettingsPanel(config, accounts, client, themeSelector);
  }

  /** First-run sign-in prompt, shown when no API key is configured. */
  public void signInIfNeeded(JComponent parent) {
    if (accounts.apiKey().isBlank()) {
      SignInDialog.show(parent, client, "", account -> {});
    }
  }
}
