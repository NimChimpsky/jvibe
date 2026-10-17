package com.jvibe.gui.settings;

import com.jvibe.config.ConfigService;
import com.jvibe.gui.controller.ProviderSettingsController;
import com.jvibe.provider.JvibeProviderClient;
import com.jvibe.provider.ModelDescriptor;
import com.jvibe.provider.ProviderSettings;
import com.jvibe.provider.ThinkingOption;
import java.awt.*;
import java.net.URI;
import java.util.List;
import javax.swing.*;

/** View-owned account draft; no alternative provider or endpoint controls. */
final class JvibeSettingsPanel extends JPanel {
  private final ConfigService config;
  private final ProviderSettingsController account;
  private final Runnable saved;
  private ProviderSettings stored;
  private boolean clearKey;
  private final JPasswordField key = new JPasswordField(32);
  private final JComboBox<String> model = new JComboBox<>();
  private final JComboBox<ThinkingOption> thinking = new JComboBox<>();
  private final JSpinner iterations = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));
  private final JTextArea instructions = new JTextArea(6, 48);
  private final JLabel credits = new JLabel("Credits: unavailable");
  private final JLabel status = new JLabel(" ");

  JvibeSettingsPanel(ConfigService config, ProviderSettingsController account, JComponent theme, Runnable saved) {
    super(new BorderLayout());
    this.config = config;
    this.account = account;
    this.saved = saved;
    stored = account.settings();
    model.setEditable(true);
    model.addItem(stored.selectedModel());
    thinking.setModel(new DefaultComboBoxModel<>(account.thinkingOptions().toArray(ThinkingOption[]::new)));
    for (int i = 0; i < thinking.getItemCount(); i++) {
      if (thinking.getItemAt(i).id().equals(stored.thinking())) thinking.setSelectedIndex(i);
    }
    key.putClientProperty("JTextField.placeholderText",
        stored.apiKey().isBlank() ? "API key or JVIBE_API_KEY" : "Key saved; leave blank to keep");
    instructions.setLineWrap(true);
    instructions.setWrapStyleWord(true);
    try {
      var settings = config.config();
      instructions.setText(settings.customInstructions());
      iterations.setValue(Math.min(1000, settings.maxToolIterations()));
    } catch (java.io.IOException e) { status.setText(e.getMessage()); }

    var form = new JPanel(new GridBagLayout());
    form.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
    var title = new JLabel("JVibe");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 22));
    var heading = new GridBagConstraints();
    heading.gridx = 0; heading.gridy = 0; heading.gridwidth = 2;
    heading.anchor = GridBagConstraints.WEST; heading.insets = new Insets(8, 0, 8, 0);
    form.add(title, heading);
    row(form, 1, "Account", link("jvibe.net/account", JvibeProviderClient.ACCOUNT_URL));
    var keyRow = new JPanel(new BorderLayout(8, 0));
    keyRow.add(key, BorderLayout.CENTER);
    var clear = new JButton("Clear key");
    clear.addActionListener(event -> { clearKey = true; key.setText(""); key.putClientProperty("JTextField.placeholderText", "Key will be cleared on Save"); });
    keyRow.add(clear, BorderLayout.EAST);
    row(form, 2, "API key", keyRow);
    row(form, 3, "Model", model);
    row(form, 4, "Thinking", thinking);
    var refresh = new JButton("Refresh account");
    refresh.addActionListener(event -> refresh(refresh));
    var balance = new JPanel(new BorderLayout(12, 0));
    balance.add(credits, BorderLayout.CENTER);
    balance.add(refresh, BorderLayout.EAST);
    row(form, 5, "", balance);
    row(form, 6, "Theme", theme);
    iterations.setPreferredSize(new Dimension(90, iterations.getPreferredSize().height));
    var limit = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    limit.add(iterations);
    row(form, 7, "Max tool iterations", limit);
    row(form, 8, "Custom instructions", new JScrollPane(instructions));
    var save = new JButton("Save");
    save.addActionListener(event -> save());
    var actions = new JPanel(new BorderLayout(12, 0));
    actions.add(status, BorderLayout.CENTER);
    actions.add(save, BorderLayout.EAST);
    row(form, 9, "", actions);
    var links = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
    links.add(link("Discord", "https://discord.gg/TN3nWFBf8H"));
    links.add(link("GitHub", "https://github.com/NimChimpsky/jvibe"));
    links.add(link("Site", "https://jvibe.net"));
    row(form, 10, "", links);
    var wrapper = new JPanel(new BorderLayout());
    wrapper.add(form, BorderLayout.NORTH);
    var scroll = new JScrollPane(wrapper);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    add(scroll, BorderLayout.CENTER);
  }

  private ProviderSettings draft() {
    var chars = key.getPassword();
    try {
      var secret = chars.length == 0 && !clearKey ? stored.apiKey() : new String(chars);
      var selectedThinking = (ThinkingOption) thinking.getSelectedItem();
      var selectedModel = model.getEditor().getItem();
      return new ProviderSettings("jvibe", JvibeProviderClient.BASE_URL, secret,
          selectedModel == null ? "" : selectedModel.toString().trim(),
          selectedThinking == null ? "none" : selectedThinking.id(), true);
    } finally { java.util.Arrays.fill(chars, '\0'); }
  }

  private void save() {
    try {
      var value = draft();
      if (value.selectedModel().isBlank()) throw new IllegalArgumentException("Choose a model");
      account.save(value);
      config.updateConfig(current -> current.withMaxToolIterations((Integer) iterations.getValue())
          .withCustomInstructions(instructions.getText()));
      stored = account.settings();
      key.setText("");
      clearKey = false;
      key.putClientProperty("JTextField.placeholderText", stored.apiKey().isBlank() ? "API key or JVIBE_API_KEY" : "Key saved; leave blank to keep");
      saved.run();
      status.setText("Saved");
    } catch (Exception e) { status.setText("Could not save settings: " + e.getMessage()); }
  }

  private void refresh(JButton button) {
    var value = draft();
    button.setEnabled(false);
    status.setText("Refreshing...");
    new SwingWorker<AccountResult, Void>() {
      @Override protected AccountResult doInBackground() {
        return new AccountResult(account.refreshModels(value), account.credits(value));
      }
      @Override protected void done() {
        button.setEnabled(true);
        try {
          var result = get();
          var selected = model.getEditor().getItem();
          model.setModel(new DefaultComboBoxModel<>(result.models().stream().map(ModelDescriptor::id).toArray(String[]::new)));
          if (selected != null) model.setSelectedItem(selected);
          credits.setText(result.credits());
          status.setText("Account refreshed");
        } catch (Exception e) {
          status.setText("Account refresh failed");
        }
      }
    }.execute();
  }

  private static void row(JPanel form, int y, String label, Component component) {
    var c = new GridBagConstraints();
    c.gridx = 0; c.gridy = y; c.anchor = GridBagConstraints.NORTHWEST; c.insets = new Insets(8, 0, 8, 20);
    form.add(new JLabel(label), c);
    c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(8, 0, 8, 0);
    form.add(component, c);
  }

  private static JButton link(String label, String url) {
    var button = new JButton(label);
    button.putClientProperty("JButton.buttonType", "borderless");
    button.setToolTipText(url);
    button.addActionListener(event -> {
      try {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
          throw new IllegalStateException("Browser unavailable");
        }
        Desktop.getDesktop().browse(URI.create(url));
      } catch (Exception e) {
        JOptionPane.showMessageDialog(button, url, "Open this link in your browser", JOptionPane.INFORMATION_MESSAGE);
      }
    });
    return button;
  }

  private record AccountResult(List<ModelDescriptor> models, String credits) {}
}
