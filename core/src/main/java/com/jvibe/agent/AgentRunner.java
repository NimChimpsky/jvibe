package com.jvibe.agent;

import com.jvibe.events.AgentEvent;
import com.jvibe.provider.ProviderClient;
import com.jvibe.provider.ProviderConfigService;
import com.jvibe.provider.ProviderEvent;
import com.jvibe.provider.ProviderRequest;
import com.jvibe.provider.ProviderRegistry;
import com.jvibe.session.AgentSession;
import com.jvibe.session.Message;
import com.jvibe.tool.ToolExecutionContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import java.util.stream.Stream;
import jakarta.inject.Singleton;

@Singleton
public final class AgentRunner {
  private final ProviderRegistry providerRegistry;
  private final ProviderConfigService providerConfigService;
  private final com.jvibe.config.ConfigService configService;
  private final String fallbackSystemPrompt;
  private final int fallbackMaxToolIterations;

  public AgentRunner(
      ProviderRegistry providerRegistry,
      ProviderConfigService providerConfigService,
      com.jvibe.config.ConfigService configService) throws java.io.IOException {
    this.providerRegistry = providerRegistry;
    this.providerConfigService = providerConfigService;
    this.configService = configService;
    this.fallbackSystemPrompt = configService.workspacePrompt();
    this.fallbackMaxToolIterations = configService.config().maxToolIterations();
  }

  AgentRunner(ProviderClient providerClient, String systemPrompt, int maxToolIterations) {
    this.providerRegistry = ProviderRegistry.of(providerClient);
    this.providerConfigService = null;
    this.configService = null;
    this.fallbackSystemPrompt = systemPrompt;
    this.fallbackMaxToolIterations = maxToolIterations;
  }

  public Stream<AgentEvent> run(AgentSession session, String prompt) {
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
        new RunIterator(session, prompt),
        java.util.Spliterator.ORDERED), false);
  }

  private ProviderClient client(String provider) {
    return providerRegistry.require(provider);
  }

  private ProviderRequest request(AgentSession session) {
    var settings = providerConfigService == null
        ? new com.jvibe.provider.ProviderSettings(session.provider(), "", "test", session.model(), "none", true)
        : providerConfigService.effectiveSettings(session.provider());
    var model = session.model() == null || session.model().isBlank() ? settings.selectedModel() : session.model();
    var thinking = providerConfigService == null
        ? com.jvibe.provider.ThinkingOption.of("none", "None")
        : providerConfigService.definition(session.provider()).stream()
            .flatMap(definition -> definition.thinkingOptions().stream())
            .filter(option -> option.id().equals(settings.thinking()))
            .findFirst()
            .orElse(com.jvibe.provider.ThinkingOption.of(settings.thinking(), settings.thinking()));
    return new ProviderRequest(
        session.provider(),
        model,
        settings,
        thinking,
        currentSystemPrompt(session),
        session.conversation().messages(),
        new ArrayList<>(session.toolRegistry().definitions()),
        Map.of());
  }

  private String currentSystemPrompt(AgentSession session) {
    if (configService == null) {
      return fallbackSystemPrompt;
    }
    try {
      return session.mode() == AgentSession.Mode.CHAT
          ? configService.chatPrompt()
          : session.mode() == AgentSession.Mode.REMOTE_SSH
              ? configService.remoteSshPrompt()
              : configService.workspacePrompt();
    } catch (java.io.IOException e) {
      return fallbackSystemPrompt;
    }
  }

  private int currentMaxToolIterations() {
    if (configService == null) {
      return fallbackMaxToolIterations;
    }
    try {
      return configService.config().maxToolIterations();
    } catch (java.io.IOException e) {
      return fallbackMaxToolIterations;
    }
  }

  private final class RunIterator implements Iterator<AgentEvent> {
    private final ArrayList<AgentEvent> events = new ArrayList<>();
    private final AgentSession session;
    private Iterator<ProviderEvent> providerEvents;
    private final StringBuilder assistant = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final ArrayList<com.jvibe.tool.ToolCall> pendingToolCalls = new ArrayList<>();
    private int iterations;
    private boolean complete;

    RunIterator(AgentSession session, String prompt) {
      this.session = session;
      session.conversation().add(new Message(Message.Role.USER, prompt));
    }

    @Override
    public boolean hasNext() {
      fill();
      return !events.isEmpty();
    }

    @Override
    public AgentEvent next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return events.removeFirst();
    }

    private void fill() {
      while (events.isEmpty() && !complete) {
        if (iterations > currentMaxToolIterations()) {
          complete = true;
          events.add(AgentEvent.error("Max tool iterations exceeded"));
          return;
        }
        if (providerEvents == null) {
          try {
            providerEvents = client(session.provider()).stream(request(session)).iterator();
          } catch (RuntimeException e) {
            complete = true;
            events.add(AgentEvent.error(e.getMessage()));
            return;
          }
        }
        if (!providerEvents.hasNext()) {
          if (!pendingToolCalls.isEmpty()) {
            executePendingTools();
            continue;
          }
          finishAssistantMessage();
          complete = true;
          events.add(new AgentEvent(AgentEvent.Type.DONE, "done"));
          return;
        }
        var event = providerEvents.next();
        switch (event) {
          case ProviderEvent.AssistantDelta delta -> {
            assistant.append(delta.text());
            events.add(new AgentEvent(AgentEvent.Type.ASSISTANT_DELTA, delta.text()));
          }
          case ProviderEvent.ReasoningDelta delta -> reasoning.append(delta.text());
          case ProviderEvent.ToolCallRequested toolCall -> pendingToolCalls.add(toolCall.call());
          case ProviderEvent.Done ignored -> {
            if (!pendingToolCalls.isEmpty()) {
              executePendingTools();
              continue;
            }
            finishAssistantMessage();
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
    }

    private void executePendingTools() {
      var metadata = new java.util.LinkedHashMap<String, Object>();
      metadata.put("toolCalls", pendingToolCalls.stream().map(call -> Map.<String, Object>of(
          "id", call.id(),
          "name", call.name(),
          "arguments", call.arguments())).toList());
      if (!reasoning.isEmpty()) {
        metadata.put("reasoningContent", reasoning.toString());
        reasoning.setLength(0);
      }
      session.conversation().add(new Message(
          Message.Role.ASSISTANT,
          assistant.toString(),
          metadata,
          java.time.Instant.now()));
      assistant.setLength(0);

      for (var call : pendingToolCalls) {
        if (iterations >= currentMaxToolIterations()) {
          complete = true;
          events.add(AgentEvent.error("Max tool iterations exceeded"));
          break;
        }
        iterations++;
        events.add(new AgentEvent(AgentEvent.Type.TOOL_STARTED, call.name()));
        var result = session.toolRegistry().execute(call, ToolExecutionContext.defaults(session.workspace()));
        session.conversation().add(new Message(
            Message.Role.TOOL,
            ModelToolResult.encode(result),
            Map.of("toolCallId", result.callId()),
            java.time.Instant.now()));
        events.add(new AgentEvent(
            AgentEvent.Type.TOOL_FINISHED,
            result.summary(),
            Map.of("success", result.success(), "payload", result.payload()),
            java.time.Instant.now()));
      }
      pendingToolCalls.clear();
      providerEvents = null;
    }

    private void finishAssistantMessage() {
      if (!assistant.isEmpty()) {
        var metadata = new java.util.LinkedHashMap<String, Object>();
        if (!reasoning.isEmpty()) {
          metadata.put("reasoningContent", reasoning.toString());
          reasoning.setLength(0);
        }
        session.conversation().add(new Message(Message.Role.ASSISTANT, assistant.toString(), metadata, java.time.Instant.now()));
        assistant.setLength(0);
      }
    }
  }
}
