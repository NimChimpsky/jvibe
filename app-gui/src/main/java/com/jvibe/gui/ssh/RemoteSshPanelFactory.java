package com.jvibe.gui.ssh;

import com.jvibe.config.ConfigService;
import com.jvibe.gui.chat.ChatPromptRunner;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.session.SessionFactory;
import com.jvibe.ssh.SshAccessMode;
import com.jvibe.ssh.SshClientService;
import com.jvibe.ssh.SshConnection;
import com.jvibe.ssh.SshConnectionService;
import com.jvibe.ssh.SshCredentialStore;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

@Singleton
public final class RemoteSshPanelFactory {
  private final ConfigService configService;
  private final SshConnectionService connections;
  private final SshClientService sshClient;
  private final SshCredentialStore credentials;
  private final SessionFactory sessionFactory;
  private final ChatPromptRunner promptRunner;
  private final ModelSelectionController modelSelectionController;

  public RemoteSshPanelFactory(
      @External ConfigService configService,
      @External SshConnectionService connections,
      @External SshClientService sshClient,
      @External SshCredentialStore credentials,
      @External SessionFactory sessionFactory,
      ChatPromptRunner promptRunner,
      ModelSelectionController modelSelectionController) {
    this.configService = configService;
    this.connections = connections;
    this.sshClient = sshClient;
    this.credentials = credentials;
    this.sessionFactory = sessionFactory;
    this.promptRunner = promptRunner;
    this.modelSelectionController = modelSelectionController;
  }

  public JComponent create(Consumer<String> log) {
    var connectionModel = new SshConnectionTableModel();
    connectionModel.setConnections(connections.connections());
    var connectionTable = new JTable(connectionModel);
    connectionTable.setFillsViewportHeight(true);
    var keyList = new JTextArea(keysText());
    keyList.setEditable(false);

    var management = managementPanel(connectionTable, connectionModel, keyList, log);
    var chat = new RemoteSshChatPanel(configService.paths().tmp(), sessionFactory,
        connections, modelSelectionController, promptRunner, log);
    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, management, chat);
    split.setResizeWeight(0.42);
    com.jvibe.gui.layout.SplitPaneSizing.initialProportion(split, 0.42);
    split.setDividerSize(6);
    split.setBorder(null);
    return split;
  }

  private JComponent managementPanel(
      JTable table,
      SshConnectionTableModel model,
      JTextArea keyList,
      Consumer<String> log) {
    var panel = new JPanel(new BorderLayout(0, 10));
    panel.setBorder(new EmptyBorder(16, 16, 12, 16));
    panel.add(title("Servers"), BorderLayout.NORTH);
    var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
    actions.add(button("Import PuTTY", event -> {
      var button = (JButton) event.getSource();
      button.setEnabled(false);
      new SwingWorker<com.jvibe.ssh.SshConnectionConfig, Void>() {
        @Override protected com.jvibe.ssh.SshConnectionConfig doInBackground() { return connections.importAll(); }
        @Override protected void done() {
          button.setEnabled(true);
          try {
            var imported = get();
            model.setConnections(imported.connections());
            keyList.setText(keysText());
            log.accept("Imported " + imported.connections().size() + " PuTTY connections");
          } catch (Exception e) {
            JOptionPane.showMessageDialog(panel, rootMessage(e), "PuTTY import failed", JOptionPane.ERROR_MESSAGE);
          }
        }
      }.execute();
    }));
    actions.add(button("Add", event -> addConnection(model, keyList)));
    actions.add(button("Edit", event -> editConnection(table, model, keyList)));
    actions.add(button("Delete", event -> deleteConnection(table, model, keyList)));
    actions.add(button("Test", event -> testConnection(table, model, log)));
    actions.add(button("Unlock Key", event -> unlockKey(table, model, keyList)));
    actions.add(button("Set Access Mode", event -> setAccessMode(table, model)));
    panel.add(actions, BorderLayout.SOUTH);

    var content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), new JScrollPane(keyList));
    content.setResizeWeight(0.72);
    panel.add(content, BorderLayout.CENTER);
    return panel;
  }

  private void addConnection(SshConnectionTableModel model, JTextArea keyList) {
    var connection = SshConnectionDialog.show(null, null);
    if (connection != null) {
      connections.save(connection);
      refresh(model, keyList);
    }
  }

  private void editConnection(JTable table, SshConnectionTableModel model, JTextArea keyList) {
    var connection = selected(table, model);
    if (connection == null) return;
    var updated = SshConnectionDialog.show(table, connection);
    if (updated != null) {
      connections.save(updated);
      refresh(model, keyList);
    }
  }

  private void deleteConnection(JTable table, SshConnectionTableModel model, JTextArea keyList) {
    var connection = selected(table, model);
    if (connection != null && JOptionPane.showConfirmDialog(
        table,
        "Delete SSH connection '" + connection.name() + "'?",
        "Delete SSH Connection",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
      connections.delete(connection.id());
      refresh(model, keyList);
    }
  }

  private void testConnection(JTable table, SshConnectionTableModel model, Consumer<String> log) {
    var connection = selected(table, model);
    if (connection == null) return;
    new SwingWorker<String, Void>() {
      @Override
      protected String doInBackground() throws Exception {
        return sshClient.test(connection);
      }

      @Override
      protected void done() {
        try {
          connections.saveTestResult(connection.id(), get());
        } catch (Exception e) {
          connections.saveTestResult(connection.id(), "Failed: " + e.getMessage());
        }
        model.setConnections(connections.connections());
        log.accept("SSH test complete: " + connection.name());
      }
    }.execute();
  }

  private void unlockKey(JTable table, SshConnectionTableModel model, JTextArea keyList) {
    var connection = selected(table, model);
    if (connection == null || connection.keyPath() == null || connection.keyPath().isBlank()) {
      JOptionPane.showMessageDialog(null, "Select a connection with a private key first.", "Unlock SSH Key", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    var passphrase = new JPasswordField(24);
    var result = JOptionPane.showConfirmDialog(
        null,
        passphrase,
        "Passphrase for " + connection.keyPath(),
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE);
    if (result == JOptionPane.OK_OPTION) {
      var secret = passphrase.getPassword();
      try {
        credentials.putPassphrase(connection.id(), secret);
      } finally {
        java.util.Arrays.fill(secret, '\0');
        passphrase.setText("");
      }
      keyList.setText(keysText());
    }
  }

  private void setAccessMode(JTable table, SshConnectionTableModel model) {
    var connection = selected(table, model);
    if (connection == null) return;
    var mode = (SshAccessMode) JOptionPane.showInputDialog(
        null,
        "Access mode",
        "SSH Access",
        JOptionPane.PLAIN_MESSAGE,
        null,
        SshAccessMode.values(),
        connection.accessMode());
    if (mode != null) {
      if (mode == SshAccessMode.FULL_ACCESS && JOptionPane.showConfirmDialog(
          table,
          "Full access allows arbitrary commands on " + connection.name() + ". Continue?",
          "Enable Full SSH Access",
          JOptionPane.OK_CANCEL_OPTION,
          JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
        return;
      }
      connections.saveAccessMode(connection.id(), mode);
      model.setConnections(connections.connections());
    }
  }

  private SshConnection selected(JTable table, SshConnectionTableModel model) {
    var row = table.getSelectedRow();
    return row < 0 ? null : model.connectionAt(table.convertRowIndexToModel(row));
  }

  private void refresh(SshConnectionTableModel model, JTextArea keyList) {
    model.setConnections(connections.connections());
    keyList.setText(keysText());
  }

  private String keysText() {
    var text = new StringBuilder("Defined keys\n\n");
    for (var key : connections.keys()) {
      var unlocked = connections.connections().stream()
          .anyMatch(connection -> connection.keyPath().equals(key.path()) && credentials.isUnlocked(connection.id()));
      text.append(key.format()).append("  ").append(key.path()).append(unlocked ? "  unlocked" : "").append("\n");
    }
    return text.toString();
  }

  private static String rootMessage(Exception exception) {
    var cause = exception;
    while (cause.getCause() instanceof Exception nested) cause = nested;
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  private static JButton button(String text, java.awt.event.ActionListener listener) {
    var button = new JButton(text);
    button.addActionListener(listener);
    return button;
  }

  private static JLabel title(String text) {
    var label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(Font.BOLD, 18));
    return label;
  }
}
