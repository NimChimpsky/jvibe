package com.jvibe.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.jvibe.events.AgentEvent;
import com.jvibe.provider.*;
import com.jvibe.session.AgentSession;
import com.jvibe.session.Message;
import com.jvibe.tool.*;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentCancellationTest {
  @TempDir Path temp;

  @Test
  void cancellationStopsCurrentToolAndCompletesEveryPendingToolResult() throws Exception {
    var started = new CountDownLatch(1);
    var control = new RunControl();
    var executed = new AtomicInteger();
    var tools = new ToolRegistry();
    tools.register(new RegisteredTool(new ToolDefinition("slow", "Slow", "Slow", Map.of()), (call, context) -> {
      assertSame(control.cancelled(), context.cancelled());
      executed.incrementAndGet();
      started.countDown();
      Thread.sleep(30_000);
      return ToolResult.success(call.id(), Map.of(), "finished");
    }));
    var session = session(tools);
    var runner = new AgentRunner(provider(request -> Stream.of(
        new ProviderEvent.AssistantDelta("Inspecting"),
        new ProviderEvent.ToolCallRequested(new ToolCall("one", "slow", Map.of())),
        new ProviderEvent.ToolCallRequested(new ToolCall("two", "slow", Map.of())),
        new ProviderEvent.Done())), "system", 10);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var task = executor.submit(() -> {
        try (var events = runner.run(session, "inspect", control)) { return events.toList(); }
      });
      assertTrue(started.await(3, TimeUnit.SECONDS));
      control.cancel();
      assertTrue(task.get(3, TimeUnit.SECONDS).stream().anyMatch(e -> e.type() == AgentEvent.Type.ERROR
          && e.message().contains("cancelled")));
    }
    assertEquals(1, executed.get());
    assertEquals(List.of("one", "two"), session.conversation().messages().stream()
        .filter(m -> m.role() == Message.Role.TOOL).map(m -> m.metadata().get("toolCallId")).toList());
    assertTrue(session.conversation().messages().stream().anyMatch(m -> m.content().equals("Inspecting")));
  }

  @Test
  void deadlineClosesBlockedProviderAndPreservesPartialResponse() throws Exception {
    var closed = new CountDownLatch(1);
    var waiting = new CountDownLatch(1);
    var control = new RunControl(Duration.ofMillis(150), Duration.ofSeconds(1));
    var session = session(new ToolRegistry());
    var runner = new AgentRunner(provider(request -> StreamSupport.stream(
        new Spliterators.AbstractSpliterator<ProviderEvent>(Long.MAX_VALUE, 0) {
          private boolean first = true;
          @Override public boolean tryAdvance(java.util.function.Consumer<? super ProviderEvent> action) {
            if (first) { first = false; action.accept(new ProviderEvent.AssistantDelta("partial")); return true; }
            waiting.countDown();
            try { closed.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return false;
          }
        }, false).onClose(closed::countDown)), "system", 10);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var task = executor.submit(() -> {
        try (var events = runner.run(session, "inspect", control)) { return events.toList(); }
      });
      assertTrue(waiting.await(3, TimeUnit.SECONDS));
      assertTrue(task.get(3, TimeUnit.SECONDS).stream().anyMatch(e -> e.message().contains("timed out")));
      assertTrue(closed.await(1, TimeUnit.SECONDS));
    }
    assertTrue(session.conversation().messages().stream().anyMatch(m -> m.role() == Message.Role.ASSISTANT
        && m.content().equals("partial")));
  }

  @Test
  void fatalProviderErrorDoesNotExecuteBufferedTools() {
    var tools = new ToolRegistry();
    var executed = new AtomicInteger();
    tools.register(new RegisteredTool(new ToolDefinition("write", "Write", "Write", Map.of()), (call, context) -> {
      executed.incrementAndGet();
      return ToolResult.success(call.id(), Map.of(), "written");
    }));
    var session = session(tools);
    var runner = new AgentRunner(provider(request -> Stream.of(
        new ProviderEvent.ToolCallRequested(new ToolCall("one", "write", Map.of())),
        new ProviderEvent.Error("stream failed", true))), "system", 10);
    try (var events = runner.run(session, "write")) { events.toList(); }
    assertEquals(0, executed.get());
    assertTrue(session.conversation().messages().stream().anyMatch(m -> m.role() == Message.Role.TOOL
        && m.content().contains("tool not executed")));
  }

  @Test
  void retainsOpaqueProtocolBlocksInNextToolTurn() {
    var calls = new AtomicInteger();
    var nextRequest = new AtomicReference<ProviderRequest>();
    var blocks = List.of(Map.of("type", "thinking", "thinking", "reason", "signature", "signature"));
    var runner = new AgentRunner(provider(request -> {
      if (calls.getAndIncrement() == 0) return Stream.of(
          new ProviderEvent.AssistantMetadata(Map.of("anthropicContent", blocks)),
          new ProviderEvent.ToolCallRequested(new ToolCall("one", "missing", Map.of())), new ProviderEvent.Done());
      nextRequest.set(request);
      return Stream.of(new ProviderEvent.Done());
    }), "system", 10);
    try (var events = runner.run(session(new ToolRegistry()), "inspect")) { events.toList(); }
    assertTrue(nextRequest.get().messages().stream().anyMatch(m -> blocks.equals(m.metadata().get("anthropicContent"))));
  }

  private AgentSession session(ToolRegistry tools) {
    return new AgentSession("test", new Workspace(temp), tools, "local", "echo", AgentSession.Mode.WORKSPACE);
  }

  private static ProviderClient provider(Function<ProviderRequest, Stream<ProviderEvent>> events) {
    return new ProviderClient() {
      @Override public String providerId() { return "local"; }
      @Override public Stream<ProviderEvent> stream(ProviderRequest request) { return events.apply(request); }
      @Override public List<ModelDescriptor> models() { return List.of(); }
      @Override public List<ModelDescriptor> refreshModels(ProviderSettings settings) { return List.of(); }
    };
  }
}
