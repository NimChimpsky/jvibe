package com.jvibe.agent;

import com.jvibe.events.AgentEvent;
import com.jvibe.provider.ProviderClient;
import com.jvibe.provider.ProviderEvent;
import com.jvibe.provider.ProviderRequest;
import com.jvibe.session.AgentSession;
import com.jvibe.session.Message;
import com.jvibe.tool.ToolExecutionContext;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Stream;

public final class AgentRunner {
  private final ProviderClient providerClient;
  private final String systemPrompt;
  private final int maxToolIterations;

  public AgentRunner(ProviderClient providerClient, String systemPrompt, int maxToolIterations) {
    this.providerClient = providerClient;
    this.systemPrompt = systemPrompt;
    this.maxToolIterations = maxToolIterations;
  }

  public Stream<AgentEvent> run(AgentSession session, String prompt) {
    var events = new ArrayList<AgentEvent>();
    session.conversation().add(new Message(Message.Role.USER, prompt));
    var iterations = 0;
    var complete = false;
    while (!complete && iterations <= maxToolIterations) {
      var request = new ProviderRequest(
          session.provider(),
          session.model(),
          systemPrompt,
          session.conversation().messages(),
          new ArrayList<>(session.toolRegistry().definitions()),
          Map.of());
      var providerEvents = providerClient.stream(request).toList();
      for (var event : providerEvents) {
        switch (event) {
          case ProviderEvent.AssistantDelta delta -> {
            session.conversation().add(new Message(Message.Role.ASSISTANT, delta.text()));
            events.add(new AgentEvent(AgentEvent.Type.ASSISTANT_DELTA, delta.text()));
          }
          case ProviderEvent.ToolCallRequested toolCall -> {
            iterations++;
            events.add(new AgentEvent(AgentEvent.Type.TOOL_STARTED, toolCall.call().name()));
            var result = session.toolRegistry().execute(toolCall.call(), ToolExecutionContext.defaults(session.workspace()));
            session.conversation().add(new Message(Message.Role.TOOL, result.summary(), Map.of("toolCallId", result.callId()), java.time.Instant.now()));
            events.add(new AgentEvent(AgentEvent.Type.TOOL_FINISHED, result.summary(), Map.of("success", result.success(), "payload", result.payload()), java.time.Instant.now()));
          }
          case ProviderEvent.Done ignored -> {
            complete = true;
            events.add(new AgentEvent(AgentEvent.Type.DONE, "done"));
          }
          case ProviderEvent.Error error -> {
            events.add(AgentEvent.error(error.message()));
            if (error.fatal()) {
              complete = true;
            }
          }
        }
      }
      if (providerEvents.stream().noneMatch(ProviderEvent.ToolCallRequested.class::isInstance)) {
        complete = true;
      }
    }
    if (iterations > maxToolIterations) {
      events.add(AgentEvent.error("Max tool iterations exceeded"));
    }
    return events.stream();
  }
}
