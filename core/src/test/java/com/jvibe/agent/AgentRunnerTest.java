package com.jvibe.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.provider.ModelDescriptor;
import com.jvibe.provider.ProviderClient;
import com.jvibe.provider.ProviderEvent;
import com.jvibe.provider.ProviderRequest;
import com.jvibe.provider.ProviderSettings;
import com.jvibe.session.AgentSession;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.tool.ToolResult;
import com.jvibe.tool.builtin.FileListTool;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRunnerTest {
  @TempDir Path temp;

  @Test
  void runsProviderToolCallAndReturnsDoneEvent() {
    var provider = new ProviderClient() {
      private final AtomicInteger calls = new AtomicInteger();

      @Override
      public String providerId() {
        return "local";
      }

      @Override
      public Stream<ProviderEvent> stream(ProviderRequest request) {
        if (calls.getAndIncrement() == 0) {
          return Stream.of(new ProviderEvent.ToolCallRequested(new ToolCall("file_list", Map.of("path", "."))));
        }
        return Stream.of(new ProviderEvent.AssistantDelta("done"), new ProviderEvent.Done());
      }

      @Override
      public List<ModelDescriptor> models() {
        return List.of();
      }

      @Override
      public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
        return List.of();
      }
    };

    var registry = new ToolRegistry();
    registry.register(FileListTool.registered());
    var session = new AgentSession(new Workspace(temp), registry);
    var events = new AgentRunner(provider, "system", 10).run(session, "list files").toList();

    assertTrue(events.stream().anyMatch(event -> event.type() == com.jvibe.events.AgentEvent.Type.TOOL_FINISHED));
    assertTrue(events.stream().anyMatch(event -> event.type() == com.jvibe.events.AgentEvent.Type.DONE));
  }

  @Test
  void feedsToolResultBackIntoProviderLoop() {
    var provider = new ProviderClient() {
      private final AtomicInteger calls = new AtomicInteger();

      @Override
      public String providerId() {
        return "local";
      }

      @Override
      public Stream<ProviderEvent> stream(ProviderRequest request) {
        if (calls.getAndIncrement() == 0) {
          return Stream.of(new ProviderEvent.ToolCallRequested(new ToolCall("fake_call", Map.of())));
        }
        assertTrue(request.messages().stream().anyMatch(message -> message.role() == com.jvibe.session.Message.Role.TOOL));
        return Stream.of(new ProviderEvent.AssistantDelta("used tool"), new ProviderEvent.Done());
      }

      @Override
      public List<ModelDescriptor> models() {
        return List.of();
      }

      @Override
      public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
        return List.of();
      }
    };
    var registry = new com.jvibe.tool.ToolRegistry();
    registry.register(new RegisteredTool(
        new ToolDefinition("fake_call", "Fake", "Fake tool", Map.of("type", "object")),
        (call, context) -> ToolResult.success(call.id(), Map.of("ok", true), "fake result")));
    var session = new AgentSession(new Workspace(temp), registry);

    var events = new AgentRunner(provider, "system", 10).run(session, "use tool").toList();

    assertTrue(events.stream().anyMatch(event -> event.message().contains("used tool")));
  }

  @Test
  void storesReasoningAndTextOnAssistantToolCallMessage() {
    var provider = new ProviderClient() {
      @Override
      public String providerId() {
        return "local";
      }

      @Override
      public Stream<ProviderEvent> stream(ProviderRequest request) {
        return Stream.of(
            new ProviderEvent.ReasoningDelta("hidden"),
            new ProviderEvent.AssistantDelta("I will inspect."),
            new ProviderEvent.ToolCallRequested(new ToolCall("fake_call", Map.of())));
      }

      @Override
      public List<ModelDescriptor> models() {
        return List.of();
      }

      @Override
      public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
        return List.of();
      }
    };
    var registry = new com.jvibe.tool.ToolRegistry();
    registry.register(new RegisteredTool(
        new ToolDefinition("fake_call", "Fake", "Fake tool", Map.of("type", "object")),
        (call, context) -> ToolResult.success(call.id(), Map.of("ok", true), "fake result")));
    var session = new AgentSession(new Workspace(temp), registry);

    new AgentRunner(provider, "system", 1).run(session, "use tool").toList();

    var assistantToolMessage = session.conversation().messages().stream()
        .filter(message -> message.role() == com.jvibe.session.Message.Role.ASSISTANT)
        .filter(message -> message.metadata().containsKey("toolCallId"))
        .findFirst()
        .orElseThrow();
    assertEquals("I will inspect.", assistantToolMessage.content());
    assertEquals("hidden", assistantToolMessage.metadata().get("reasoningContent"));
  }

  @Test
  void stopsBeforeExecutingToolPastIterationLimit() {
    var calls = new AtomicInteger();
    var provider = new ProviderClient() {
      @Override
      public String providerId() {
        return "local";
      }

      @Override
      public Stream<ProviderEvent> stream(ProviderRequest request) {
        calls.incrementAndGet();
        return Stream.of(new ProviderEvent.ToolCallRequested(new ToolCall("fake_call", Map.of())));
      }

      @Override
      public List<ModelDescriptor> models() {
        return List.of();
      }

      @Override
      public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
        return List.of();
      }
    };
    var executed = new AtomicInteger();
    var registry = new com.jvibe.tool.ToolRegistry();
    registry.register(new RegisteredTool(
        new ToolDefinition("fake_call", "Fake", "Fake tool", Map.of("type", "object")),
        (call, context) -> {
          executed.incrementAndGet();
          return ToolResult.success(call.id(), Map.of("ok", true), "fake result");
        }));
    var session = new AgentSession(new Workspace(temp), registry);

    var events = new AgentRunner(provider, "system", 1).run(session, "use tool").toList();

    assertEquals(1, executed.get());
    assertTrue(events.stream().anyMatch(event -> event.message().contains("Max tool iterations exceeded")));
  }

  @Test
  void requestedToolsExecuteWhenPresentInSessionRegistry() {
    var provider = new ProviderClient() {
      private final AtomicInteger calls = new AtomicInteger();

      @Override
      public String providerId() {
        return "local";
      }

      @Override
      public Stream<ProviderEvent> stream(ProviderRequest request) {
        if (calls.getAndIncrement() == 0) {
          return Stream.of(new ProviderEvent.ToolCallRequested(new ToolCall("risky_call", Map.of())));
        }
        return Stream.of(new ProviderEvent.AssistantDelta("done"), new ProviderEvent.Done());
      }

      @Override
      public List<ModelDescriptor> models() {
        return List.of();
      }

      @Override
      public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
        return List.of();
      }
    };
    var executed = new AtomicInteger();
    var registry = new com.jvibe.tool.ToolRegistry();
    registry.register(new RegisteredTool(
        new ToolDefinition("risky_call", "Risky", "Risky tool", Map.of("type", "object")),
        (call, context) -> {
          executed.incrementAndGet();
          return ToolResult.success(call.id(), Map.of("ok", true), "risky result");
        }));
    var session = new AgentSession(new Workspace(temp), registry);

    var events = new AgentRunner(provider, "system", 10).run(session, "use tool").toList();

    assertEquals(1, executed.get());
    assertTrue(events.stream().anyMatch(event -> event.type() == com.jvibe.events.AgentEvent.Type.TOOL_FINISHED));
  }

  @Test
  void usesChatPromptForChatSessions() throws Exception {
    var captured = new AtomicReference<String>();
    var runner = runnerCapturingSystemPrompt(captured);
    var session = new AgentSession(new Workspace(temp), new ToolRegistry(), AgentSession.Mode.CHAT);

    runner.run(session, "hello").toList();

    assertTrue(captured.get().contains("default Chat mode"));
    assertTrue(captured.get().contains("under ~/.jvibe/tmp"));
  }

  @Test
  void usesWorkspacePromptForWorkspaceSessions() throws Exception {
    var captured = new AtomicReference<String>();
    var runner = runnerCapturingSystemPrompt(captured);
    var session = new AgentSession(new Workspace(temp), new ToolRegistry(), AgentSession.Mode.WORKSPACE);

    runner.run(session, "hello").toList();

    assertTrue(captured.get().contains("Workspace mode"));
    assertTrue(captured.get().contains("workspace root"));
  }

  private ProviderClient providerCalling(String tool) {
    return new ProviderClient() {
      @Override
      public String providerId() {
        return "local";
      }

      @Override
      public Stream<ProviderEvent> stream(ProviderRequest request) {
        return Stream.of(new ProviderEvent.ToolCallRequested(new ToolCall(tool, Map.of())));
      }

      @Override
      public List<ModelDescriptor> models() {
        return List.of();
      }

      @Override
      public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
        return List.of();
      }
    };
  }

  private AgentRunner runnerCapturingSystemPrompt(AtomicReference<String> captured) throws Exception {
    var provider = new ProviderClient() {
      @Override
      public String providerId() {
        return "local";
      }

      @Override
      public Stream<ProviderEvent> stream(ProviderRequest request) {
        captured.set(request.systemPrompt());
        return Stream.of(new ProviderEvent.Done());
      }

      @Override
      public List<ModelDescriptor> models() {
        return List.of();
      }

      @Override
      public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
        return List.of();
      }
    };
    var paths = com.jvibe.config.JvibePaths.under(temp.resolve("prompt-home-" + java.util.UUID.randomUUID()));
    var config = new com.jvibe.config.ConfigService(paths);
    return new AgentRunner(
        com.jvibe.provider.ProviderRegistry.of(provider),
        new com.jvibe.provider.ProviderConfigService(paths),
        config);
  }
}
