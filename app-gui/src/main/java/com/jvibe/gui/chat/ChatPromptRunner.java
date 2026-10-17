package com.jvibe.gui.chat;

import com.jvibe.events.AgentEvent;
import com.jvibe.gui.controller.PromptController;
import com.jvibe.logging.FileLogService;
import com.jvibe.session.AgentSession;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.util.List;
import javax.swing.SwingWorker;

/** Streams one provider run into a transcript without coupling it to tab layout. */
@Singleton
public final class ChatPromptRunner {
  private final PromptController prompts;
  private final FileLogService fileLog;

  public ChatPromptRunner(PromptController prompts, @External FileLogService fileLog) {
    this.prompts = prompts;
    this.fileLog = fileLog;
  }

  public com.jvibe.agent.RunControl submit(
      AgentSession session,
      ChatTranscript transcript,
      String request,
      Runnable finished,
      DesktopChatController.Callbacks callbacks) {
    var run = new RunState();
    var control = new com.jvibe.agent.RunControl();
    fileLog.prompt(session.provider(), session.model(), request);
    callbacks.log("INFO", "Prompt submitted to " + session.provider() + "/" + session.model());

    new SwingWorker<Void, AgentEvent>() {
      @Override
      protected Void doInBackground() {
        try (var events = prompts.submit(session, request, control)) {
          events.forEach(this::publish);
        } catch (RuntimeException e) {
          publish(AgentEvent.error(
              e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return null;
      }

      @Override
      protected void process(List<AgentEvent> events) {
        events.forEach(event -> handle(session, transcript, run, event, callbacks));
      }

      @Override
      protected void done() {
        control.close();
        flushAssistantLog(session, run);
        transcript.finishAssistant();
        finished.run();
        callbacks.workspaceChanged();
        try {
          get();
        } catch (Exception e) {
          callbacks.log("ERROR", rootMessage(e));
        }
      }
    }.execute();
    return control;
  }

  private void handle(
      AgentSession session,
      ChatTranscript transcript,
      RunState run,
      AgentEvent event,
      DesktopChatController.Callbacks callbacks) {
    switch (event.type()) {
      case ASSISTANT_DELTA -> {
        if (transcript.appendAssistantDelta(event.message())) {
          run.assistantText.append(event.message());
          run.assistantChars += event.message().length();
        }
      }
      case TOOL_STARTED -> callbacks.log("INFO", "Tool started: " + event.message());
      case TOOL_FINISHED -> {
        callbacks.log(Boolean.FALSE.equals(event.data().get("success")) ? "WARN" : "INFO", "Tool finished: " + event.message());
        callbacks.workspaceChanged();
      }
      case ERROR -> {
        flushAssistantLog(session, run);
        transcript.appendAssistant("Error: " + event.message());
        callbacks.log("ERROR", event.message());
      }
      case DONE -> {
        flushAssistantLog(session, run);
        transcript.finishAssistant();
        callbacks.log(
            run.assistantChars == 0 ? "WARN" : "INFO",
            run.assistantChars == 0 ? "Run completed without assistant text" : "Run complete");
      }
    }
  }

  private void flushAssistantLog(
      AgentSession session,
      RunState run) {
    if (!run.assistantText.isEmpty()) {
      fileLog.assistant(session.provider(), session.model(), run.assistantText.toString());
      run.assistantText.setLength(0);
    }
  }

  private static String rootMessage(Exception exception) {
    var cause = exception;
    while (cause.getCause() instanceof Exception nested) {
      cause = nested;
    }
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  private static final class RunState {
    private final StringBuilder assistantText = new StringBuilder();
    private int assistantChars;
  }
}
