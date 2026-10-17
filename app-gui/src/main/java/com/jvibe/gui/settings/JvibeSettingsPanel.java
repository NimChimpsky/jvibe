package com.jvibe.gui.settings;

import com.jvibe.api.Account;
import com.jvibe.api.AccountService;
import com.jvibe.api.AccountStatus;
import com.jvibe.api.JvibeClient;
import com.jvibe.api.Thinking;
import com.jvibe.config.ConfigService;
import com.jvibe.config.ModeSettings;
import com.jvibe.session.Mode;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.HierarchyEvent;
import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/** The JVibe tab: account, assistant behaviour, per-tab customisation and appearance. */
final class JvibeSettingsPanel extends JPanel {
  private static final char ECHO = '•';

  private final ConfigService config;
  private final AccountService accounts;
  private final JvibeClient client;
  private final JLabel status = new JLabel("Checking account…");
  private final JLabel email = new JLabel();
  private final JPasswordField apiKey = new JPasswordField(36);
  private final JComboBox<Thinking> thinking = new JComboBox<>(Thinking.values());
  private final JSpinner maxToolCalls = new JSpinner(new SpinnerNumberModel(25, 1, 500, 1));
  private final Map<Mode, JTextArea> instructions = new EnumMap<>(Mode.class);
  private final Map<Mode, JCheckBox> askBeforeRunning = new EnumMap<>(Mode.class);
  private final JLabel saveStatus = new JLabel(" ");
  private int row;

  JvibeSettingsPanel(ConfigService config, AccountService accounts, JvibeClient client, JComponent themeSelector) {
    super(new BorderLayout());
    this.config = config;
    this.accounts = accounts;
    this.client = client;

    var form = new JPanel(new GridBagLayout());
    form.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));
    heading(form, "Account");
    field(form, "Status", accountStatusRow());
    field(form, "Email", email);
    field(form, "API key", apiKeyRow());
    field(form, "", accountActions());

    heading(form, "Assistant");
    field(form, "Thinking", left(thinking));
    field(form, "Max tool calls per message", left(maxToolCalls));

    heading(form, "Tabs");
    field(form, "", modeTabs());

    heading(form, "Appearance");
    field(form, "Theme", left(themeSelector));

    var save = new JButton("Save");
    save.addActionListener(event -> save());
    var actions = new JPanel(new BorderLayout(12, 0));
    actions.add(saveStatus, BorderLayout.CENTER);
    actions.add(save, BorderLayout.EAST);
    field(form, "", actions);
    field(form, "", links());

    var wrapper = new JPanel(new BorderLayout());
    wrapper.add(form, BorderLayout.NORTH);
    var scroll = new JScrollPane(wrapper);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    add(scroll, BorderLayout.CENTER);

    load();
    addHierarchyListener(event -> {
      if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) refreshStatus();
    });
  }

  private JComponent accountStatusRow() {
    var refresh = new JButton("Refresh");
    refresh.addActionListener(event -> refreshStatus());
    var panel = new JPanel(new BorderLayout(12, 0));
    panel.add(status, BorderLayout.CENTER);
    panel.add(refresh, BorderLayout.EAST);
    return panel;
  }

  private JComponent apiKeyRow() {
    apiKey.setEchoChar(ECHO);
    apiKey.putClientProperty("JTextField.placeholderText", "Sign in, or paste a key from jvibe.net/account");
    var show = new JButton("Show");
    show.addActionListener(event -> {
      var hidden = apiKey.getEchoChar() != 0;
      apiKey.setEchoChar(hidden ? (char) 0 : ECHO);
      show.setText(hidden ? "Hide" : "Show");
    });
    var copy = new JButton("Copy");
    copy.addActionListener(event -> {
      var key = new String(apiKey.getPassword());
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(key), null);
      saveStatus.setText("API key copied");
    });
    var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    buttons.add(show);
    buttons.add(copy);
    var panel = new JPanel(new BorderLayout(6, 0));
    panel.add(apiKey, BorderLayout.CENTER);
    panel.add(buttons, BorderLayout.EAST);
    return panel;
  }

  private JComponent accountActions() {
    var signIn = new JButton("Sign in with email…");
    signIn.addActionListener(event -> SignInDialog.show(this, client, email.getText(), account -> {
      load();
      refreshStatus();
    }));
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.add(signIn);
    panel.add(link("Manage account and credits", JvibeClient.ACCOUNT_URL));
    if (accounts.usesEnvironmentKey()) {
      panel.add(new JLabel("  Using the JVIBE_API_KEY environment variable."));
    }
    return panel;
  }

  /** One sub-tab per assistant tab with its custom instructions and run approval setting. */
  private JComponent modeTabs() {
    var tabs = new JTabbedPane();
    for (var mode : Mode.values()) {
      var text = new JTextArea(5, 48);
      text.setLineWrap(true);
      text.setWrapStyleWord(true);
      instructions.put(mode, text);
      var panel = new JPanel(new BorderLayout(0, 6));
      panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
      panel.add(new JLabel("Custom instructions, added to JVibe's built-in " + mode.label() + " instructions"),
          BorderLayout.NORTH);
      panel.add(new JScrollPane(text), BorderLayout.CENTER);
      if (mode.changesState()) {
        var ask = new JCheckBox("Ask before running commands, edits or SQL that change things");
        askBeforeRunning.put(mode, ask);
        panel.add(ask, BorderLayout.SOUTH);
      }
      tabs.addTab(mode.label(), panel);
    }
    return tabs;
  }

  private void load() {
    var account = accounts.account();
    email.setText(account.email().isBlank() ? "—" : account.email());
    apiKey.setText(accounts.apiKey());
    try {
      var settings = config.config();
      thinking.setSelectedItem(settings.thinkingLevel());
      maxToolCalls.setValue(Math.min(500, settings.maxToolIterations()));
      for (var mode : Mode.values()) {
        var modeSettings = settings.mode(mode);
        instructions.get(mode).setText(modeSettings.instructions());
        if (askBeforeRunning.containsKey(mode)) askBeforeRunning.get(mode).setSelected(modeSettings.askBeforeRunning());
      }
    } catch (java.io.IOException e) {
      saveStatus.setText("Could not read settings: " + e.getMessage());
    }
  }

  private void save() {
    try {
      var key = new String(apiKey.getPassword()).trim();
      var account = accounts.account();
      if (!accounts.usesEnvironmentKey() && !key.equals(account.apiKey())) {
        accounts.save(key.isBlank() ? Account.NONE : new Account("", key, ""));
      }
      var perMode = new EnumMap<Mode, ModeSettings>(Mode.class);
      for (var mode : Mode.values()) {
        var ask = askBeforeRunning.get(mode);
        perMode.put(mode, new ModeSettings(instructions.get(mode).getText().trim(), ask != null && ask.isSelected()));
      }
      config.updateConfig(current -> current.withAssistant(
          (Thinking) thinking.getSelectedItem(), (Integer) maxToolCalls.getValue(), perMode));
      saveStatus.setText("Saved");
      load();
      refreshStatus();
    } catch (Exception e) {
      saveStatus.setText("Could not save: " + e.getMessage());
    }
  }

  private void refreshStatus() {
    status.setText("Checking account…");
    new SwingWorker<AccountStatus, Void>() {
      @Override protected AccountStatus doInBackground() {
        return client.status();
      }

      @Override protected void done() {
        try {
          var result = get();
          status.setText(result.summary());
          status.setForeground(UIManager.getColor(result.state() == AccountStatus.State.ACTIVE
              ? "Label.foreground" : "Component.warning.focusedBorderColor"));
          if (!result.email().isBlank()) email.setText(result.email());
        } catch (Exception e) {
          status.setText("Could not check the account");
        }
      }
    }.execute();
  }

  private JComponent links() {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.add(link("jvibe.net", JvibeClient.SITE_URL));
    panel.add(link("Discord", "https://discord.gg/TN3nWFBf8H"));
    panel.add(link("GitHub", "https://github.com/NimChimpsky/jvibe"));
    return panel;
  }

  private void heading(JPanel form, String text) {
    var label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() + 3f));
    var c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = row++;
    c.gridwidth = 2;
    c.anchor = GridBagConstraints.WEST;
    c.insets = new Insets(row == 1 ? 0 : 18, 0, 6, 0);
    form.add(label, c);
  }

  private void field(JPanel form, String label, Component component) {
    var c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = row;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(6, 0, 6, 20);
    form.add(new JLabel(label), c);
    c.gridx = 1;
    c.gridy = row++;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(6, 0, 6, 0);
    form.add(component, c);
  }

  private static JComponent left(JComponent component) {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.add(component);
    return panel;
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
}
