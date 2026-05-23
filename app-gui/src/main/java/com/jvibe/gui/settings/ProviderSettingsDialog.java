package com.jvibe.gui.settings;

import com.jvibe.gui.controller.ProviderSettingsController;
import com.jvibe.provider.JvibeProviderClient;
import com.jvibe.provider.ProviderDefinition;
import com.jvibe.provider.ProviderSettings;
import com.jvibe.provider.QwenProviderClient;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class ProviderSettingsDialog {
  private final JFrame owner;
  private final ProviderSettingsController controller;
  private final Runnable onSaved;
  private final Consumer<String> activity;
  private final List<ProviderTab> providerTabs = new ArrayList<>();
  private JDialog activeDialog;

  public ProviderSettingsDialog(
      JFrame owner,
      ProviderSettingsController controller,
      Runnable onSaved,
      Consumer<String> activity) {
    this.owner = owner;
    this.controller = controller;
    this.onSaved = onSaved;
    this.activity = activity;
  }

  public void show() {
    var dialog = new JDialog(owner, "Providers", true);
    activeDialog = dialog;
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setContentPane(buildContent(dialog));
    dialog.setMinimumSize(new Dimension(640, 480));
    dialog.setSize(dialogSize());
    dialog.setLocationRelativeTo(owner);
    bindEscape(dialog);
    dialog.setVisible(true);
  }

  private JComponent buildContent(JDialog dialog) {
    providerTabs.clear();
    var tabs = new JTabbedPane();
    for (var definition : controller.configurableProviders()) {
      var providerTab = new ProviderTab(definition, controller.settings(definition.id()));
      providerTabs.add(providerTab);
      tabs.addTab(definition.displayName(), providerTab.component());
    }

    var cancel = new JButton("Cancel");
    cancel.addActionListener(event -> dialog.dispose());

    var save = saveButton(dialog);
    dialog.getRootPane().setDefaultButton(save);
    var buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
    buttons.setBorder(new EmptyBorder(8, 12, 12, 12));
    buttons.add(cancel);
    buttons.add(save);

    var panel = new JPanel(new BorderLayout(0, 8));
    panel.setBorder(new EmptyBorder(12, 12, 0, 12));
    panel.add(tabs, BorderLayout.CENTER);
    panel.add(buttons, BorderLayout.SOUTH);
    return panel;
  }

  private JButton saveButton(JDialog dialog) {
    var save = new JButton("Save");
    save.addActionListener(event -> {
      controller.saveAll(providerTabs.stream().map(ProviderTab::settings).toList());
      onSaved.run();
      activity.accept("Saved provider settings");
      dialog.dispose();
    });
    return save;
  }

  private void bindEscape(JDialog dialog) {
    var root = dialog.getRootPane();
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeSettings");
    root.getActionMap().put("closeSettings", new AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent event) {
        dialog.dispose();
      }
    });
  }

  private Dimension dialogSize() {
    var bounds = owner.getGraphicsConfiguration().getBounds();
    var width = Math.max(640, Math.min(760, bounds.width - 120));
    var height = Math.max(480, Math.min(560, bounds.height - 120));
    return new Dimension(width, height);
  }

  private final class ProviderTab {
    private final ProviderDefinition definition;
    private final ProviderSettings stored;
    private final JCheckBox enabled;
    private final JPasswordField apiKey;
    private final JComboBox<String> model;
    private final JComboBox<String> thinking;
    private final JComboBox<RegionOption> region;
    private final JLabel status;
    private final JLabel credits;
    private final JButton refresh;
    private final JButton test;
    private final JButton refreshCredits;

    ProviderTab(ProviderDefinition definition, ProviderSettings stored) {
      this.definition = definition;
      this.stored = stored;
      this.enabled = new JCheckBox("Enabled", stored.enabled());
      this.apiKey = new JPasswordField(28);
      this.model = new JComboBox<>();
      this.thinking = new JComboBox<>();
      this.region = new JComboBox<>(RegionOption.values());
      this.status = label(" ");
      this.credits = label("Credits: unavailable");
      this.refresh = new JButton("Refresh Models");
      this.test = new JButton("Test Connection");
      this.refreshCredits = new JButton("Refresh Credits");
    }

    JComponent component() {
      apiKey.setToolTipText("Leave blank to keep saved key or use " + definition.apiKeyEnv());
      model.addItem(stored.selectedModel());
      definition.thinkingOptions().forEach(option -> thinking.addItem(option.id()));
      thinking.setSelectedItem(stored.thinking());
      status.setForeground(UIManager.getColor("Label.disabledForeground"));

      refresh.addActionListener(event -> refreshModels());
      test.addActionListener(event -> testConnection());
      refreshCredits.addActionListener(event -> refreshCredits());

      var form = new JPanel();
      form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
      form.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
          new EmptyBorder(16, 16, 16, 16)));
      form.add(title(definition.displayName()));
      form.add(row("Enabled", enabled));
      form.add(row("API key", apiKey));
      if (isQwen()) {
        region.setSelectedItem(RegionOption.fromBaseUrl(stored.baseUrl()));
        form.add(row("Region", region));
      }
      form.add(row("Model", model));
      form.add(row("Thinking", thinking));
      if (isJvibe()) {
        credits.setForeground(UIManager.getColor("Label.disabledForeground"));
        credits.setText(controller.credits(settings()));
        form.add(row("Account", accountButton()));
        var creditsPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        creditsPanel.add(credits);
        creditsPanel.add(javax.swing.Box.createHorizontalStrut(8));
        creditsPanel.add(refreshCredits);
        form.add(row("Credits", creditsPanel));
      }

      var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
      actions.add(refresh);
      actions.add(javax.swing.Box.createHorizontalStrut(8));
      actions.add(test);
      form.add(row("Actions", actions));
      form.add(row("Status", status));

      var outer = new JPanel(new BorderLayout());
      outer.setBorder(new EmptyBorder(12, 12, 12, 12));
      outer.add(form, BorderLayout.NORTH);
      var scroll = new JScrollPane(outer);
      scroll.setBorder(BorderFactory.createEmptyBorder());
      return scroll;
    }

    ProviderSettings settings() {
      var enteredKey = new String(apiKey.getPassword()).trim();
      var key = enteredKey.isBlank() ? stored.apiKey() : enteredKey;
      var selectedModel = model.getSelectedItem() == null ? stored.selectedModel() : model.getSelectedItem().toString();
      var selectedThinking = thinking.getSelectedItem() == null ? stored.thinking() : thinking.getSelectedItem().toString();
      var baseUrl = isQwen() ? ((RegionOption) region.getSelectedItem()).baseUrl : definition.baseUrl();
      return new ProviderSettings(definition.id(), baseUrl, key, selectedModel, selectedThinking, enabled.isSelected());
    }

    private boolean isQwen() {
      return "qwen".equals(definition.id());
    }

    private boolean isJvibe() {
      return "jvibe".equals(definition.id());
    }

    private void refreshModels() {
      setBusy(true, "Refreshing models...");
      new SwingWorker<List<String>, Void>() {
        @Override
        protected List<String> doInBackground() {
          return controller.refreshModels(settings()).stream().map(descriptor -> descriptor.id()).toList();
        }

        @Override
        protected void done() {
          try {
            var models = get();
            model.removeAllItems();
            models.forEach(model::addItem);
            if (model.getItemCount() > 0) {
              model.setSelectedIndex(0);
            }
            setBusy(false, "Refreshed " + models.size() + " models");
            activity.accept("Refreshed models for " + definition.displayName());
          } catch (Exception e) {
            setBusy(false, "Refresh failed: " + rootMessage(e));
          }
        }
      }.execute();
    }

    private void testConnection() {
      setBusy(true, "Testing connection...");
      new SwingWorker<Integer, Void>() {
        @Override
        protected Integer doInBackground() {
          return controller.refreshModels(settings()).size();
        }

        @Override
        protected void done() {
          try {
            var count = get();
            setBusy(false, "Connection OK");
            JOptionPane.showMessageDialog(activeDialog, count == 0 ? "No models returned" : "OK: " + count + " models");
          } catch (Exception e) {
            var message = rootMessage(e);
            setBusy(false, "Connection failed: " + message);
            JOptionPane.showMessageDialog(activeDialog, message, "Connection failed", JOptionPane.ERROR_MESSAGE);
          }
        }
      }.execute();
    }

    private void refreshCredits() {
      setBusy(true, "Refreshing credits...");
      new SwingWorker<String, Void>() {
        @Override
        protected String doInBackground() {
          return controller.credits(settings());
        }

        @Override
        protected void done() {
          try {
            credits.setText(get());
            setBusy(false, "Credits refreshed");
            activity.accept("Refreshed credits for " + definition.displayName());
          } catch (Exception e) {
            setBusy(false, "Credits unavailable");
            credits.setText("Credits: unavailable");
          }
        }
      }.execute();
    }

    private void setBusy(boolean busy, String message) {
      refresh.setEnabled(!busy);
      test.setEnabled(!busy);
      refreshCredits.setEnabled(!busy);
      status.setText(message);
    }

    private JButton accountButton() {
      var button = new JButton("Buy credits / register account");
      button.setToolTipText(JvibeProviderClient.ACCOUNT_URL);
      button.addActionListener(event -> openAccountPage());
      return button;
    }

    private void openAccountPage() {
      try {
        if (!Desktop.isDesktopSupported()) {
          throw new IllegalStateException("Desktop browser is unavailable");
        }
        Desktop.getDesktop().browse(URI.create(JvibeProviderClient.ACCOUNT_URL));
        activity.accept("Opened JVibe account page");
      } catch (Exception e) {
        JOptionPane.showMessageDialog(
            activeDialog,
            JvibeProviderClient.ACCOUNT_URL,
            "JVibe account",
            JOptionPane.INFORMATION_MESSAGE);
      }
    }
  }

  private enum RegionOption {
    INTERNATIONAL("International", QwenProviderClient.INTERNATIONAL_BASE_URL),
    CHINA("China mainland", QwenProviderClient.CHINA_BASE_URL);

    private final String label;
    private final String baseUrl;

    RegionOption(String label, String baseUrl) {
      this.label = label;
      this.baseUrl = baseUrl;
    }

    static RegionOption fromBaseUrl(String baseUrl) {
      return CHINA.baseUrl.equals(baseUrl) ? CHINA : INTERNATIONAL;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static JComponent row(String label, JComponent value) {
    var row = new JPanel(new BorderLayout(12, 0));
    row.setBorder(new EmptyBorder(8, 0, 8, 0));
    var name = label(label);
    name.setFont(name.getFont().deriveFont(Font.BOLD, 12));
    name.setPreferredSize(new Dimension(120, 26));
    row.add(name, BorderLayout.WEST);
    row.add(value, BorderLayout.CENTER);
    return row;
  }

  private static JLabel label(String text) {
    return new JLabel(text);
  }

  private static JLabel title(String text) {
    var label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(Font.BOLD, 16));
    label.setBorder(new EmptyBorder(0, 0, 10, 0));
    return label;
  }

  private static String rootMessage(Exception e) {
    var cause = e.getCause() == null ? e : e.getCause();
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }
}
