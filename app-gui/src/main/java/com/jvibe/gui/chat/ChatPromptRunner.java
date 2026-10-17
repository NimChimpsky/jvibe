package com.jvibe.gui.chat;

import com.jvibe.agent.AgentRunner;
import com.jvibe.agent.RunControl;
import com.jvibe.events.AgentEvent;
import com.jvibe.logging.FileLogService;
import com.jvibe.session.AgentSession;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

/** Streams one prompt run into a transcript, off the event thread, for every tab. */
@Singleton
public final class ChatPromptRunner {
  private final AgentRunner runner;
  private final ToolApprovalDialog approvals;
  private final FileLogService fileLog;

  public ChatPromptRunner(@External AgentRunner runner, ToolApprovalDialog approvals, @External FileLogService fileLog) {
    this.runner = runner;
    this.approvals = approvals;
    this.fileLog = fileLog;
  }

  public RunControl submit(
      AgentSession session,
      ChatTranscript transcript,
      String request,
      Runnable finished,
      ChatCallbacks callbacks) {
    var control = new RunControl();
    var state = new RunState();
    fileLog.prompt(session.mode(), request);

    new SwingWorker<Void, AgentEvent>() {
      @Override
      protected Void doInBackground() {
        try (var events = runner.run(session, request, control, approvals)) {
          events.forEach(this::publish);
        } catch (RuntimeException e) {
          publish(AgentEvent.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return null;
      }

      @Override
      protected void process(List<AgentEvent> events) {
        events.forEach(event -> handle(event, transcript, state, callbacks));
      }

      @Override
      protected void done() {
        control.close();
        flushAssistantLog(session, state);
        transcript.finishAssistant();
        finished.run();
      }
    }.execute();
    return control;
  }

  private void handle(AgentEvent event, ChatTranscript transcript, RunState state, ChatCallbacks callbacks) {
    switch (event.type()) {
      case ASSISTANT_DELTA -> {
        if (transcript.appendAssistantDelta(event.message())) state.assistantText.append(event.message());
      }
      case TOOL_STARTED -> {
        @SuppressWarnings("unchecked")
        var arguments = (Map<String, Object>) event.data().getOrDefault("arguments", Map.of());
        var description = ToolCalls.describe(event.message(), arguments);
        state.activity = transcript.appendActivity("▸ " + description);
        state.activity.setToolTipText(ToolCalls.detail(arguments));
        callbacks.log("INFO", "Tool started: " + description);
      }
      case TOOL_FINISHED -> {
        var success = !Boolean.FALSE.equals(event.data().get("success"));
        if (state.activity != null) {
          state.activity.setText((success ? "✓ " : "✗ ") + state.activity.getText().substring(2));
          if (!success) state.activity.setToolTipText(event.message());
          state.activity = null;
        }
        callbacks.log(success ? "INFO" : "WARN", "Tool finished: " + event.message());
        callbacks.toolFinished(event);
      }
      case ERROR -> {
        transcript.appendAssistant("Error: " + event.message());
        callbacks.log("ERROR", event.message());
      }
      case DONE -> transcript.finishAssistant();
    }
  }

  private void flushAssistantLog(AgentSession session, RunState state) {
    if (!state.assistantText.isEmpty()) {
      fileLog.assistant(session.mode(), state.assistantText.toString());
      state.assistantText.setLength(0);
    }
  }

  private static final class RunState {
    private final StringBuilder assistantText = new StringBuilder();
    private JLabel activity;
  }
}
