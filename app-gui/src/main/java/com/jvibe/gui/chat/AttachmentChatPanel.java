package com.jvibe.gui.chat;

import com.jvibe.agent.RunControl;
import com.jvibe.session.AgentSession;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * A conversation over items the user attaches with {@code @name}: servers in the Servers tab,
 * JDBC connections in the Databases tab. Attachments persist across messages until removed.
 */
public final class AttachmentChatPanel<T> extends JPanel implements AutoCloseable {
  /** How to list, identify and describe the attachable items. */
  public record Attachables<T>(
      String placeholder,
      Supplier<List<T>> available,
      Function<T, String> id,
      Function<T, String> name,
      Function<T, String> detail) {}

  private final Attachables<T> attachables;
  private final Function<Supplier<List<T>>, AgentSession> sessions;
  private final Predicate<List<T>> beforeSend;
  private final ChatPromptRunner runner;
  private final ChatCallbacks callbacks;
  private final List<T> attached = new CopyOnWriteArrayList<>();
  private final ChatTranscript transcript = new ChatTranscript();
  private final ChatComposer composer;
  private AgentSession session;
  private RunControl runControl;

  /**
   * @param sessions creates a session that reads the current attachments
   * @param beforeSend runs on the event thread before each message (e.g. unlock credentials); false cancels
   */
  public AttachmentChatPanel(
      Attachables<T> attachables,
      Function<Supplier<List<T>>, AgentSession> sessions,
      Predicate<List<T>> beforeSend,
      ChatPromptRunner runner,
      ChatCallbacks callbacks) {
    super(new BorderLayout());
    this.attachables = attachables;
    this.sessions = sessions;
    this.beforeSend = beforeSend;
    this.runner = runner;
    this.callbacks = callbacks;
    session = sessions.apply(() -> List.copyOf(attached));
    composer = new ChatComposer(this::send);
    composer.prompt().putClientProperty("JTextField.placeholderText", attachables.placeholder());
    composer.addAction("New conversation", "Clear the conversation; attachments are kept", this::reset);
    new MentionController<>(composer.prompt(), attachables.available(), attachables.name(),
        attachables.detail(), this::attach);
    var scroll = new JScrollPane(transcript.component());
    scroll.setBorder(BorderFactory.createEmptyBorder());
    add(scroll, BorderLayout.CENTER);
    add(composer, BorderLayout.SOUTH);
  }

  public void attach(T item) {
    var id = attachables.id().apply(item);
    if (attached.stream().noneMatch(existing -> attachables.id().apply(existing).equals(id))) {
      attached.add(item);
      refreshChips();
    }
    composer.prompt().requestFocusInWindow();
  }

  private void refreshChips() {
    composer.showContext(attached.stream().map(item -> {
      var chip = new JButton("@" + attachables.name().apply(item) + " ×");
      chip.setToolTipText(attachables.detail().apply(item) + " — click to detach");
      chip.setFocusable(false);
      chip.setMargin(new Insets(2, 8, 2, 8));
      chip.addActionListener(event -> {
        attached.remove(item);
        refreshChips();
      });
      return chip;
    }).toList());
  }

  /** Drops attachments whose items were deleted and picks up edits to the rest. */
  private void syncAttachments() {
    var current = attached.stream()
        .map(item -> find(attachables.id().apply(item)))
        .flatMap(Optional::stream)
        .toList();
    attached.clear();
    attached.addAll(current);
    refreshChips();
  }

  private Optional<T> find(String id) {
    return attachables.available().get().stream()
        .filter(item -> attachables.id().apply(item).equals(id))
        .findFirst();
  }

  private void send() {
    if (runControl != null) {
      runControl.cancel();
      return;
    }
    var text = composer.prompt().getText().trim();
    if (text.isBlank()) return;
    syncAttachments();
    if (!beforeSend.test(List.copyOf(attached))) return;
    composer.prompt().setText("");
    var names = attached.stream().map(item -> "@" + attachables.name().apply(item)).toList();
    transcript.appendUser(names.isEmpty() ? text : String.join(" ", names) + "\n" + text, List.of());
    composer.setSending(true);
    runControl = runner.submit(session, transcript, text, () -> {
      runControl = null;
      composer.setSending(false);
    }, callbacks);
  }

  private void reset() {
    close();
    session = sessions.apply(() -> List.copyOf(attached));
    transcript.clear();
    composer.prompt().requestFocusInWindow();
  }

  @Override
  public void close() {
    if (runControl != null) runControl.cancel();
  }
}
