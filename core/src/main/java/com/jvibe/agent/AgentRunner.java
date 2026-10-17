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
    return run(session, prompt, new RunControl());
  }

  public Stream<AgentEvent> run(AgentSession session, String prompt, RunControl control) {
    var run = new RunIterator(session, prompt, control);
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
        run, java.util.Spliterator.ORDERED), false).onClose(run::close);
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
      return switch (session.mode()) {
        case CHAT -> configService.chatPrompt();
        case WORKSPACE -> configService.workspacePrompt();
        case REMOTE_SSH -> configService.remoteSshPrompt();
        case JDBC -> configService.jdbcPrompt();
      };
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
    private final java.util.ArrayDeque<AgentEvent> events = new java.util.ArrayDeque<>();
    private final AgentSession session;
    private final RunControl control;
    private Iterator<ProviderEvent> providerEvents;
    private final StringBuilder assistant = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final java.util.LinkedHashMap<String, Object> assistantMetadata = new java.util.LinkedHashMap<>();
    private final ArrayList<com.jvibe.tool.ToolCall> pendingToolCalls = new ArrayList<>();
    private int iterations;
    private boolean complete;

    RunIterator(AgentSession session, String prompt, RunControl control) {
      this.session = session;
      this.control = control;
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
      control.start();
      while (events.isEmpty() && !complete) {
        if (control.cancelled().get()) {
          stop();
          return;
        }
        if (providerEvents == null) {
          try {
            var providerStream = client(session.provider()).stream(request(session));
            control.track(providerStream);
            providerEvents = providerStream.iterator();
          } catch (RuntimeException e) {
            complete = true;
            closeProvider();
            events.add(AgentEvent.error(control.cancelled().get() ? control.reason() : e.getMessage()));
            control.close();
            return;
          }
        }
        ProviderEvent event;
        try {
          event = providerEvents.hasNext() ? providerEvents.next() : new ProviderEvent.Done();
        } catch (RuntimeException e) {
          event = new ProviderEvent.Error(e.getMessage() == null ? "Provider stream failed" : e.getMessage(), true);
        }
        if (control.cancelled().get()) { stop(); return; }
        if (event instanceof ProviderEvent.Done) {
          closeProvider();
          if (!pendingToolCalls.isEmpty()) {
            executePendingTools();
            continue;
          }
          finishAssistantMessage();
          complete = true;
          control.close();
          events.add(new AgentEvent(AgentEvent.Type.DONE, "done"));
          return;
        }
        switch (event) {
          case ProviderEvent.AssistantDelta delta -> {
            assistant.append(delta.text());
            events.add(new AgentEvent(AgentEvent.Type.ASSISTANT_DELTA, delta.text()));
          }
          case ProviderEvent.ReasoningDelta delta -> reasoning.append(delta.text());
          case ProviderEvent.AssistantMetadata metadata -> assistantMetadata.putAll(metadata.values());
          case ProviderEvent.ToolCallRequested toolCall -> pendingToolCalls.add(toolCall.call());
          case ProviderEvent.Done ignored -> throw new IllegalStateException("Done handled above");
          case ProviderEvent.Error error -> {
            events.add(AgentEvent.error(error.message()));
            if (error.fatal()) {
              complete = true;
              closeProvider();
              if (!pendingToolCalls.isEmpty()) completeToolTurn(error.message());
              else finishAssistantMessage();
              control.close();
            }
          }
        }
      }
    }

    private void executePendingTools() {
      completeToolTurn(null);
    }

    private void completeToolTurn(String skipReason) {
      var metadata = new java.util.LinkedHashMap<String, Object>(assistantMetadata);
      assistantMetadata.clear();
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
        com.jvibe.tool.ToolResult result;
        if (skipReason != null || control.cancelled().get() || iterations >= currentMaxToolIterations()) {
          complete = true;
          result = com.jvibe.tool.ToolResult.failure(call.id(), skipReason != null ? skipReason + "; tool not executed" : control.cancelled().get()
              ? control.reason() + "; tool not executed" : "Max tool iterations exceeded; tool not executed");
        } else {
          iterations++;
          events.add(new AgentEvent(AgentEvent.Type.TOOL_STARTED, call.name()));
          result = session.toolRegistry().execute(call, new ToolExecutionContext(
              session.workspace(), control.toolTimeout(), control.cancelled()));
        }
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
      if (control.cancelled().get()) complete = true;
      if (complete && skipReason == null) {
        events.add(AgentEvent.error(control.cancelled().get() ? control.reason() : "Max tool iterations exceeded"));
        control.close();
      }
      pendingToolCalls.clear();
      providerEvents = null;
    }

    private void closeProvider() {
      control.closeResponse();
    }

    private void stop() {
      closeProvider();
      if (!pendingToolCalls.isEmpty()) executePendingTools();
      else {
        finishAssistantMessage();
        events.add(AgentEvent.error(control.reason()));
      }
      complete = true;
      control.close();
    }

    private void close() {
      if (!complete) {
        closeProvider();
        control.cancel();
        stop();
      }
      control.close();
    }

    private void finishAssistantMessage() {
      if (!assistant.isEmpty() || !assistantMetadata.isEmpty()) {
        var metadata = new java.util.LinkedHashMap<String, Object>(assistantMetadata);
        assistantMetadata.clear();
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
