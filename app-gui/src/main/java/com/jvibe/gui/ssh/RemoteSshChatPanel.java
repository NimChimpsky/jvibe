package com.jvibe.gui.ssh;

import com.jvibe.gui.chat.ChatComposer;
import com.jvibe.gui.chat.ChatPromptRunner;
import com.jvibe.gui.chat.ChatTranscript;
import com.jvibe.gui.chat.DesktopChatController;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import com.jvibe.ssh.SshConnection;
import com.jvibe.ssh.SshConnectionService;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Per-view SSH context; the composer and streaming lifecycle are shared with local chat. */
final class RemoteSshChatPanel extends JPanel {
  private final List<SshConnection> attached = new CopyOnWriteArrayList<>();
  private final ChatTranscript transcript = new ChatTranscript();
  private final ChatComposer composer;
  private final AgentSession session;
  private final SshConnectionService connections;
  private final ModelSelectionController models;
  private final ChatPromptRunner runner;
  private final DesktopChatController.Callbacks callbacks;
  private boolean running;
  private com.jvibe.agent.RunControl runControl;

  RemoteSshChatPanel(
      Path workspace,
      SessionFactory sessions,
      SshConnectionService connections,
      ModelSelectionController models,
      ChatPromptRunner runner,
      Consumer<String> log) {
    super(new BorderLayout());
    this.connections = connections;
    this.models = models;
    this.runner = runner;
    session = sessions.createRemoteSsh(workspace, () -> List.copyOf(attached));
    callbacks = new DesktopChatController.Callbacks() {
      @Override public void log(String level, String message) { log.accept(level + ": " + message); }
      @Override public void workspaceChanged() { }
      @Override public void activeSessionChanged() { }
    };
    composer = new ChatComposer(this::send, () -> { });
    composer.prompt().setToolTipText("Attach a connection with @. Host keys use ~/.ssh/known_hosts.");
    models.bind(composer.modelSelector());
    new SshMentionController(composer.prompt(), connections::connections, this::attach);
    add(new JScrollPane(transcript.component()), BorderLayout.CENTER);
    add(composer, BorderLayout.SOUTH);
  }

  private void attach(SshConnection connection) {
    if (attached.stream().noneMatch(existing -> existing.id().equals(connection.id()))) {
      attached.add(connection);
      refreshChips();
    }
  }

  private void refreshChips() {
    composer.showContext(attached.stream().map(connection -> {
      var chip = new JButton("@" + connection.name() + " x");
      chip.setToolTipText(connection.user() + "@" + connection.host() + ":" + connection.port());
      chip.setFocusable(false);
      chip.setMargin(new java.awt.Insets(2, 8, 2, 8));
      chip.addActionListener(event -> {
        attached.removeIf(existing -> existing.id().equals(connection.id()));
        refreshChips();
      });
      return chip;
    }).toList());
  }

  private void send() {
    var text = composer.prompt().getText().trim();
    if (running) {
      if (runControl != null) runControl.cancel();
      return;
    }
    if (text.isBlank()) return;
    var selected = (String) composer.modelSelector().getSelectedItem();
    if (selected == null) {
      transcript.appendAssistant("Select a model in the JVibe tab.");
      return;
    }
    var current = attached.stream().map(connection -> connections.find(connection.id()))
        .flatMap(java.util.Optional::stream).toList();
    attached.clear();
    attached.addAll(current);
    refreshChips();
    var request = promptWithSessions(text, current);
    models.apply(session, selected);
    composer.prompt().setText("");
    var names = current.stream().map(connection -> "@" + connection.name()).toList();
    transcript.appendUser(names.isEmpty() ? text : String.join(" ", names) + "\n" + text, List.of());
    transcript.finishAssistant();
    running = true;
    composer.setSending(true);
    runControl = runner.submit(session, transcript, request, () -> {
      running = false;
      runControl = null;
      composer.setSending(false);
      transcript.finishAssistant();
    }, callbacks);
  }

  @Override public void removeNotify() {
    if (runControl != null) runControl.cancel();
    super.removeNotify();
  }

  static String promptWithSessions(String text, List<SshConnection> attached) {
    var context = new StringBuilder("Attached SSH sessions:\n");
    for (var connection : attached) {
      context.append("@").append(connection.name())
          .append(" sessionId=").append(connection.id())
          .append(" host=").append(connection.host())
          .append(" user=").append(connection.user())
          .append(" accessMode=").append(connection.accessMode()).append("\n");
    }
    return context + "\nUser request:\n" + text;
  }
}
