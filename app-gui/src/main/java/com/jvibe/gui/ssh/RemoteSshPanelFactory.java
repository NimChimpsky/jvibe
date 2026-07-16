package com.jvibe.gui.ssh;

import com.jvibe.config.ConfigService;
import com.jvibe.events.AgentEvent;
import com.jvibe.gui.chat.ChatTranscript;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.gui.controller.PromptController;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import com.jvibe.ssh.SshAccessMode;
import com.jvibe.ssh.SshClientService;
import com.jvibe.ssh.SshConnection;
import com.jvibe.ssh.SshConnectionService;
import com.jvibe.ssh.SshCredentialStore;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

@Singleton
public final class RemoteSshPanelFactory {
  private final ConfigService configService;
  private final SshConnectionService connections;
  private final SshClientService sshClient;
  private final SshCredentialStore credentials;
  private final SessionFactory sessionFactory;
  private final PromptController promptController;
  private final ModelSelectionController modelSelectionController;

  public RemoteSshPanelFactory(
      @External ConfigService configService,
      @External SshConnectionService connections,
      @External SshClientService sshClient,
      @External SshCredentialStore credentials,
      @External SessionFactory sessionFactory,
      PromptController promptController,
      ModelSelectionController modelSelectionController) {
    this.configService = configService;
    this.connections = connections;
    this.sshClient = sshClient;
    this.credentials = credentials;
    this.sessionFactory = sessionFactory;
    this.promptController = promptController;
    this.modelSelectionController = modelSelectionController;
  }

  public JComponent create(Consumer<String> log) {
    var attached = new ArrayList<SshConnection>();
    var session = sessionFactory.createRemoteSsh(configService.paths().tmp(), () -> List.copyOf(attached));
    var transcript = new ChatTranscript();
    var prompt = new JTextArea();
    prompt.setLineWrap(true);
    prompt.setWrapStyleWord(true);
    var attachments = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
    var modelSelector = new JComboBox<String>();
    var running = new AtomicBoolean();
    refreshModels(modelSelector, session);

    var connectionModel = new SshConnectionTableModel();
    connectionModel.setConnections(connections.connections());
    var connectionTable = new JTable(connectionModel);
    connectionTable.setFillsViewportHeight(true);
    var keyList = new JTextArea(keysText());
    keyList.setEditable(false);

    new SshMentionController(prompt, connections::connections, connection -> {
      if (attached.stream().noneMatch(existing -> existing.id().equals(connection.id()))) {
        attached.add(connection);
        attachments.add(chip(connection, attached, attachments));
        attachments.revalidate();
        attachments.repaint();
      }
    });

    var management = managementPanel(connectionTable, connectionModel, keyList, log);
    var chat = chatPanel(transcript, prompt, attachments, modelSelector, session, attached, running, log);
    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, management, chat);
    split.setResizeWeight(0.42);
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
    panel.add(title("Remote SSH"), BorderLayout.NORTH);
    var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
    actions.add(button("Import", event -> {
      var imported = connections.importAll();
      model.setConnections(imported.connections());
      keyList.setText(keysText());
      log.accept("Imported " + imported.connections().size() + " SSH connections");
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

  private JComponent chatPanel(
      ChatTranscript transcript,
      JTextArea prompt,
      JPanel attachments,
      JComboBox<String> modelSelector,
      AgentSession session,
      List<SshConnection> attached,
      AtomicBoolean running,
      Consumer<String> log) {
    var panel = new JPanel(new BorderLayout(0, 8));
    panel.setBorder(new EmptyBorder(0, 16, 16, 16));
    var note = new JLabel("Use @ to attach SSH sessions. Host keys are verified against ~/.ssh/known_hosts.");
    note.setForeground(UIManager.getColor("Label.disabledForeground"));
    panel.add(note, BorderLayout.NORTH);
    panel.add(new JScrollPane(transcript.component()), BorderLayout.CENTER);

    var composer = new JPanel(new BorderLayout(0, 6));
    attachments.setPreferredSize(new Dimension(0, 28));
    composer.add(attachments, BorderLayout.NORTH);
    var inputScroll = new JScrollPane(prompt);
    inputScroll.setPreferredSize(new Dimension(0, 110));
    composer.add(inputScroll, BorderLayout.CENTER);
    var footer = new JPanel(new BorderLayout());
    footer.add(modelSelector, BorderLayout.WEST);
    var send = button("Send", event -> send(session, transcript, prompt, attached, modelSelector, running, log));
    footer.add(send, BorderLayout.EAST);
    composer.add(footer, BorderLayout.SOUTH);
    prompt.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "sendPrompt");
    prompt.getInputMap().put(
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.SHIFT_DOWN_MASK),
        "insert-break");
    prompt.getActionMap().put("sendPrompt", new javax.swing.AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent event) {
        send(session, transcript, prompt, attached, modelSelector, running, log);
      }
    });
    panel.add(composer, BorderLayout.SOUTH);
    return panel;
  }

  private void send(
      AgentSession session,
      ChatTranscript transcript,
      JTextArea prompt,
      List<SshConnection> attached,
      JComboBox<String> modelSelector,
      AtomicBoolean running,
      Consumer<String> log) {
    var text = prompt.getText().trim();
    if (text.isBlank()) {
      return;
    }
    if (!running.compareAndSet(false, true)) {
      log.accept("A Remote SSH prompt is already running");
      return;
    }
    if (modelSelector.getSelectedItem() == null) {
      running.set(false);
      transcript.appendAssistant("Error: No provider model selected. Open Providers and enable a provider.");
      return;
    }
    modelSelectionController.apply(session, (String) modelSelector.getSelectedItem());
    prompt.setText("");
    transcript.appendUser(text, List.of());
    transcript.finishAssistant();
    new SwingWorker<Void, AgentEvent>() {
      @Override
      protected Void doInBackground() {
        promptController.submit(session, promptWithSessions(text, attached)).forEach(this::publish);
        return null;
      }

      @Override
      protected void process(List<AgentEvent> events) {
        for (var event : events) {
          switch (event.type()) {
            case ASSISTANT_DELTA -> transcript.appendAssistantDelta(event.message());
            case ERROR -> transcript.appendAssistant("Error: " + event.message());
            case DONE -> transcript.finishAssistant();
            case TOOL_STARTED -> log.accept("SSH tool started: " + event.message());
            case TOOL_FINISHED -> log.accept("SSH tool finished: " + event.message());
          }
        }
      }

      @Override
      protected void done() {
        running.set(false);
        transcript.finishAssistant();
        try {
          get();
        } catch (Exception e) {
          transcript.appendAssistant("Error: " + rootMessage(e));
          log.accept("Remote SSH prompt failed: " + rootMessage(e));
        }
      }
    }.execute();
  }

  private String promptWithSessions(String text, List<SshConnection> attached) {
    var sessions = new StringBuilder("Attached SSH sessions:\n");
    for (var connection : attached) {
      sessions.append("@")
          .append(connection.name())
          .append(" sessionId=")
          .append(connection.id())
          .append(" host=")
          .append(connection.host())
          .append(" user=")
          .append(connection.user())
          .append(" accessMode=")
          .append(connection.accessMode())
          .append("\n");
    }
    return sessions + "\nUser request:\n" + text;
  }

  private void refreshModels(JComboBox<String> selector, AgentSession session) {
    selector.removeAllItems();
    for (var choice : modelSelectionController.choices()) {
      selector.addItem(choice.display());
    }
    if (selector.getItemCount() > 0) {
      selector.setSelectedIndex(0);
      modelSelectionController.apply(session, (String) selector.getSelectedItem());
    }
  }

  private Component chip(SshConnection connection, List<SshConnection> attached, JPanel container) {
    var button = new JButton("@" + connection.name() + " x");
    button.setToolTipText(connection.user() + "@" + connection.host() + ":" + connection.port());
    button.setMargin(new java.awt.Insets(2, 8, 2, 8));
    button.setFocusable(false);
    button.addActionListener(event -> {
      attached.removeIf(existing -> existing.id().equals(connection.id()));
      container.remove(button);
      container.revalidate();
      container.repaint();
    });
    return button;
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
