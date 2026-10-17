package com.jvibe.agent;

import com.jvibe.api.ModeClient;
import com.jvibe.api.ModeRequest;
import com.jvibe.api.StreamEvent;
import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibeConfig;
import com.jvibe.events.AgentEvent;
import com.jvibe.session.AgentSession;
import com.jvibe.session.Message;
import com.jvibe.tool.ToolApprover;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Runs one user prompt: streams a model turn, executes requested tools, and repeats until the
 * model answers without tools. Events are produced lazily so callers can render and cancel.
 */
@Singleton
public final class AgentRunner {
  private final ModeClient client;
  private final Supplier<JvibeConfig> settings;

  @Inject
  public AgentRunner(ModeClient client, ConfigService config) {
    this(client, () -> {
      try {
        return config.config();
      } catch (IOException e) {
        return JvibeConfig.defaults();
      }
    });
  }

  AgentRunner(ModeClient client, Supplier<JvibeConfig> settings) {
    this.client = client;
    this.settings = settings;
  }

  public Stream<AgentEvent> run(AgentSession session, String prompt) {
    return run(session, prompt, new RunControl(), ToolApprover.ALWAYS);
  }

  public Stream<AgentEvent> run(AgentSession session, String prompt, RunControl control, ToolApprover approver) {
    var run = new Run(session, prompt, control, approver, settings.get());
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(run, Spliterator.ORDERED), false)
        .onClose(run::close);
  }

  private final class Run implements Iterator<AgentEvent> {
    private final AgentSession session;
    private final RunControl control;
    private final ToolApprover approver;
    private final JvibeConfig config;
    private final ArrayDeque<AgentEvent> events = new ArrayDeque<>();
    private final StringBuilder assistant = new StringBuilder();
    private final List<ToolCall> requested = new ArrayList<>();
    private final ArrayDeque<ToolCall> toolQueue = new ArrayDeque<>();
    private Iterator<StreamEvent> stream;
    private ToolCall running;
    private int toolRuns;
    private boolean limitReached;
    private boolean complete;

    Run(AgentSession session, String prompt, RunControl control, ToolApprover approver, JvibeConfig config) {
      this.session = session;
      this.control = control;
      this.approver = approver;
      this.config = config;
      session.conversation().add(Message.user(prompt));
    }

    @Override
    public boolean hasNext() {
      fill();
      return !events.isEmpty();
    }

    @Override
    public AgentEvent next() {
      if (!hasNext()) throw new NoSuchElementException();
      return events.removeFirst();
    }

    private void fill() {
      control.start();
      while (events.isEmpty() && !complete) {
        if (control.cancelled().get()) {
          fail(control.reason());
        } else if (running != null) {
          runTool();
        } else if (!toolQueue.isEmpty()) {
          startNextTool();
        } else if (limitReached) {
          fail("Stopped after " + config.maxToolIterations()
              + " tool calls. Send another message to continue, or raise the limit in the JVibe tab.");
        } else if (stream == null) {
          openStream();
        } else {
          var event = nextStreamEvent();
          // A cancelled run's stream is closed underneath us and ends early; report why.
          if (control.cancelled().get()) fail(control.reason());
          else handle(event);
        }
      }
    }

    private void openStream() {
      try {
        var mode = session.mode();
        var response = client.stream(new ModeRequest(
            mode,
            session.conversation().messages(),
            config.thinkingLevel(),
            config.mode(mode).instructions(),
            session.context()));
        control.track(response);
        stream = response.iterator();
      } catch (RuntimeException e) {
        fail(message(e));
      }
    }

    private StreamEvent nextStreamEvent() {
      try {
        return stream.hasNext() ? stream.next() : new StreamEvent.Done();
      } catch (RuntimeException e) {
        return new StreamEvent.Error(message(e));
      }
    }

    private void handle(StreamEvent event) {
      switch (event) {
        case StreamEvent.AssistantDelta delta -> {
          assistant.append(delta.text());
          events.add(new AgentEvent(AgentEvent.Type.ASSISTANT_DELTA, delta.text()));
        }
        case StreamEvent.ToolCallRequested call -> requested.add(call.call());
        case StreamEvent.Error error -> fail(error.message());
        case StreamEvent.Done ignored -> {
          closeStream();
          recordAssistantTurn();
          if (toolQueue.isEmpty()) finish();
        }
      }
    }

    /** Adds the assistant message and queues its tool calls. */
    private void recordAssistantTurn() {
      if (!assistant.isEmpty() || !requested.isEmpty()) {
        session.conversation().add(Message.assistant(assistant.toString(), requested));
      }
      toolQueue.addAll(requested);
      requested.clear();
      assistant.setLength(0);
    }

    private void startNextTool() {
      var call = toolQueue.removeFirst();
      var tool = session.toolRegistry().find(call.name());
      if (toolRuns >= config.maxToolIterations()) {
        limitReached = true;
        recordResult(call, ToolResult.failure(call.id(), "Tool call limit reached; not executed"));
      } else if (tool.isPresent() && tool.get().changesState() && !approver.approve(session.mode(), call)) {
        recordResult(call, ToolResult.failure(call.id(), "The user declined to run this tool call"));
      } else {
        toolRuns++;
        running = call;
        events.add(new AgentEvent(AgentEvent.Type.TOOL_STARTED, call.name(),
            Map.of("arguments", call.arguments()), java.time.Instant.now()));
      }
    }

    private void runTool() {
      var call = running;
      running = null;
      var result = session.toolRegistry().execute(call, new ToolExecutionContext(
          session.workspace(), control.toolTimeout(), control.cancelled()));
      recordResult(call, result);
      if (toolQueue.isEmpty()) stream = null;
    }

    private void recordResult(ToolCall call, ToolResult result) {
      session.conversation().add(Message.tool(call.id(), ModelToolResult.encode(result)));
      var data = new LinkedHashMap<String, Object>();
      data.put("success", result.success());
      if (result.display() != null) data.put("display", result.display());
      events.add(new AgentEvent(AgentEvent.Type.TOOL_FINISHED, result.summary(), data, java.time.Instant.now()));
    }

    /** Ends the run with an error, answering every outstanding tool call so history stays valid. */
    private void fail(String message) {
      closeStream();
      recordAssistantTurn();
      if (running != null) toolQueue.addFirst(running);
      running = null;
      while (!toolQueue.isEmpty()) {
        var call = toolQueue.removeFirst();
        recordResult(call, ToolResult.failure(call.id(), message + "; tool not executed"));
      }
      events.add(AgentEvent.error(message));
      complete = true;
      control.close();
    }

    private void finish() {
      complete = true;
      events.add(new AgentEvent(AgentEvent.Type.DONE, "done"));
      control.close();
    }

    private void closeStream() {
      control.closeResponse();
      stream = null;
    }

    private void close() {
      if (!complete) {
        control.cancel();
        fail(control.reason());
      }
      control.close();
    }

    private static String message(RuntimeException e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
  }
}
