package com.jvibe.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.api.StreamEvent;
import com.jvibe.config.JvibeConfig;
import com.jvibe.events.AgentEvent;
import com.jvibe.session.AgentSession;
import com.jvibe.session.Message;
import com.jvibe.session.Mode;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolApprover;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.tool.ToolResult;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentCancellationTest {
  @TempDir Path temp;

  @Test
  void cancellationStopsCurrentToolAndAnswersEveryPendingCall() throws Exception {
    var started = new CountDownLatch(1);
    var control = new RunControl();
    var executed = new AtomicInteger();
    var slow = RegisteredTool.changing("slow", (call, context) -> {
      assertSame(control.cancelled(), context.cancelled());
      executed.incrementAndGet();
      started.countDown();
      Thread.sleep(30_000);
      return ToolResult.success(call.id(), Map.of(), "finished");
    });
    var session = session(new ToolRegistry(List.of(slow)));
    var runner = new AgentRunner(request -> Stream.of(
        new StreamEvent.AssistantDelta("Inspecting"),
        new StreamEvent.ToolCallRequested(new ToolCall("one", "slow", Map.of())),
        new StreamEvent.ToolCallRequested(new ToolCall("two", "slow", Map.of())),
        new StreamEvent.Done()), JvibeConfig::defaults);

    var executor = Executors.newCachedThreadPool();
    try {
      var task = executor.submit(() -> {
        try (var events = runner.run(session, "inspect", control, ToolApprover.ALWAYS)) {
          return events.toList();
        }
      });
      assertTrue(started.await(3, TimeUnit.SECONDS));
      control.cancel();
      assertTrue(task.get(3, TimeUnit.SECONDS).stream().anyMatch(event -> event.type() == AgentEvent.Type.ERROR
          && event.message().contains("cancelled")));
    } finally {
      executor.shutdownNow();
    }
    assertEquals(1, executed.get());
    assertEquals(List.of("one", "two"), session.conversation().messages().stream()
        .filter(message -> message.role() == Message.Role.TOOL).map(Message::toolCallId).toList());
    assertTrue(session.conversation().messages().stream().anyMatch(message -> message.content().equals("Inspecting")));
  }

  @Test
  void deadlineClosesBlockedStreamAndKeepsPartialResponse() throws Exception {
    var closed = new CountDownLatch(1);
    var waiting = new CountDownLatch(1);
    var control = new RunControl(Duration.ofMillis(150), Duration.ofSeconds(1));
    var session = session(new ToolRegistry());
    var runner = new AgentRunner(request -> StreamSupport.stream(
        new Spliterators.AbstractSpliterator<StreamEvent>(Long.MAX_VALUE, 0) {
          private boolean first = true;

          @Override
          public boolean tryAdvance(Consumer<? super StreamEvent> action) {
            if (first) {
              first = false;
              action.accept(new StreamEvent.AssistantDelta("partial"));
              return true;
            }
            waiting.countDown();
            try {
              closed.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return false;
          }
        }, false).onClose(closed::countDown), JvibeConfig::defaults);

    var executor = Executors.newCachedThreadPool();
    try {
      var task = executor.submit(() -> {
        try (var events = runner.run(session, "inspect", control, ToolApprover.ALWAYS)) {
          return events.toList();
        }
      });
      assertTrue(waiting.await(3, TimeUnit.SECONDS));
      assertTrue(task.get(3, TimeUnit.SECONDS).stream().anyMatch(event -> event.message().contains("timed out")));
      assertTrue(closed.await(1, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
    assertTrue(session.conversation().messages().stream().anyMatch(message ->
        message.role() == Message.Role.ASSISTANT && message.content().equals("partial")));
  }

  private AgentSession session(ToolRegistry tools) {
    return new AgentSession(Mode.BUILD, new Workspace(temp), tools, () -> "");
  }
}
