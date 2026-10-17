package com.jvibe.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.api.ModeClient;
import com.jvibe.api.ModeRequest;
import com.jvibe.api.StreamEvent;
import com.jvibe.api.Thinking;
import com.jvibe.config.JvibeConfig;
import com.jvibe.config.ModeSettings;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRunnerTest {
  @TempDir Path temp;

  /** Replays one scripted model turn per request and records every request. */
  private static final class ScriptedClient implements ModeClient {
    private final List<List<StreamEvent>> turns;
    private final List<ModeRequest> requests = new ArrayList<>();

    @SafeVarargs
    ScriptedClient(List<StreamEvent>... turns) {
      this.turns = List.of(turns);
    }

    @Override
    public Stream<StreamEvent> stream(ModeRequest request) {
      requests.add(request);
      return turns.get(Math.min(requests.size() - 1, turns.size() - 1)).stream();
    }
  }

  private static StreamEvent call(String id, String name) {
    return new StreamEvent.ToolCallRequested(new ToolCall(id, name, Map.of("command", "ls")));
  }

  private static StreamEvent text(String value) {
    return new StreamEvent.AssistantDelta(value);
  }

  private static final StreamEvent DONE = new StreamEvent.Done();

  private AgentSession session(Mode mode, RegisteredTool... tools) {
    return new AgentSession(mode, new Workspace(temp), new ToolRegistry(List.of(tools)), () -> "ctx");
  }

  private static RegisteredTool counting(String name, boolean changesState, AtomicInteger executed) {
    return new RegisteredTool(name, changesState, (call, context) -> {
      executed.incrementAndGet();
      return ToolResult.success(call.id(), Map.of("ok", true), name + " result");
    });
  }

  private static AgentRunner runner(ModeClient client) {
    return runner(client, JvibeConfig.defaults());
  }

  private static AgentRunner runner(ModeClient client, JvibeConfig config) {
    return new AgentRunner(client, () -> config);
  }

  @Test
  void sendsModeThinkingInstructionsAndContext() {
    var client = new ScriptedClient(List.of(text("hi"), DONE));
    var config = JvibeConfig.defaults().withAssistant(Thinking.HIGH, 5,
        Map.of(Mode.SERVERS, new ModeSettings("Prefer journalctl.", true)));

    var events = runner(client, config).run(session(Mode.SERVERS), "uptime?").toList();

    var request = client.requests.getFirst();
    assertEquals(Mode.SERVERS, request.mode());
    assertEquals(Thinking.HIGH, request.thinking());
    assertEquals("Prefer journalctl.", request.instructions());
    assertEquals("ctx", request.context());
    assertEquals(List.of(Message.user("uptime?")), request.messages());
    assertEquals(AgentEvent.Type.DONE, events.getLast().type());
  }

  @Test
  void executesEveryToolCallThenFeedsResultsBack() {
    var executed = new AtomicInteger();
    var client = new ScriptedClient(
        List.of(text("Checking."), call("a", "shell_run"), call("b", "shell_run"), DONE),
        List.of(text("All good"), DONE));

    var events = runner(client).run(session(Mode.BUILD, counting("shell_run", true, executed)), "check").toList();

    assertEquals(2, executed.get());
    var second = client.requests.get(1).messages();
    var assistant = second.get(1);
    assertEquals("Checking.", assistant.content());
    assertEquals(List.of("a", "b"), assistant.toolCalls().stream().map(ToolCall::id).toList());
    assertEquals(List.of("a", "b"), second.stream()
        .filter(message -> message.role() == Message.Role.TOOL).map(Message::toolCallId).toList());
    assertTrue(second.get(2).content().contains("\"ok\":true"));
    assertEquals(List.of(AgentEvent.Type.ASSISTANT_DELTA, AgentEvent.Type.TOOL_STARTED, AgentEvent.Type.TOOL_FINISHED,
        AgentEvent.Type.TOOL_STARTED, AgentEvent.Type.TOOL_FINISHED, AgentEvent.Type.ASSISTANT_DELTA,
        AgentEvent.Type.DONE), events.stream().map(AgentEvent::type).toList());
  }

  @Test
  void toolStartedIsDeliveredBeforeTheToolRuns() {
    var executed = new AtomicInteger();
    var client = new ScriptedClient(List.of(call("a", "shell_run"), DONE), List.of(DONE));
    var events = runner(client).run(session(Mode.BUILD, counting("shell_run", true, executed)), "go").iterator();

    assertEquals(AgentEvent.Type.TOOL_STARTED, events.next().type());
    assertEquals(0, executed.get());
    assertEquals(AgentEvent.Type.TOOL_FINISHED, events.next().type());
    assertEquals(1, executed.get());
  }

  @Test
  void declinedStateChangingToolsAreReportedAndReadingToolsSkipApproval() {
    var reads = new AtomicInteger();
    var writes = new AtomicInteger();
    var asked = new ArrayList<String>();
    ToolApprover decline = (mode, call) -> {
      asked.add(call.name());
      return false;
    };
    var client = new ScriptedClient(List.of(call("r", "sql_schema"), call("w", "sql_execute"), DONE), List.of(DONE));

    runner(client).run(
        session(Mode.DATABASES, counting("sql_schema", false, reads), counting("sql_execute", true, writes)),
        "delete rows", new RunControl(), decline).toList();

    assertEquals(List.of("sql_execute"), asked);
    assertEquals(1, reads.get());
    assertEquals(0, writes.get());
    assertTrue(client.requests.get(1).messages().getLast().content().contains("declined"));
  }

  @Test
  void stopsAtTheToolLimitAndAnswersEveryPendingCall() {
    var executed = new AtomicInteger();
    var client = new ScriptedClient(List.of(call("1", "shell_run"), call("2", "shell_run"), call("3", "shell_run"), DONE));
    var config = JvibeConfig.defaults().withAssistant(Thinking.NONE, 1, Map.of());
    var session = session(Mode.BUILD, counting("shell_run", true, executed));

    var events = runner(client, config).run(session, "loop").toList();

    assertEquals(1, executed.get());
    assertEquals(1, client.requests.size());
    assertEquals(List.of("1", "2", "3"), session.conversation().messages().stream()
        .filter(message -> message.role() == Message.Role.TOOL).map(Message::toolCallId).toList());
    assertTrue(events.getLast().message().contains("Stopped after 1 tool calls"));
  }

  @Test
  void streamErrorDoesNotRunBufferedToolsButAnswersThem() {
    var executed = new AtomicInteger();
    var client = new ScriptedClient(List.of(text("partial"), call("a", "shell_run"), new StreamEvent.Error("boom")));
    var session = session(Mode.BUILD, counting("shell_run", true, executed));

    var events = runner(client).run(session, "go").toList();

    assertEquals(0, executed.get());
    assertEquals(AgentEvent.Type.ERROR, events.getLast().type());
    var messages = session.conversation().messages();
    assertEquals("partial", messages.get(1).content());
    assertEquals("a", messages.get(2).toolCallId());
  }

  @Test
  void unknownToolsFailWithoutStoppingTheRun() {
    var client = new ScriptedClient(List.of(call("x", "not_a_tool"), DONE), List.of(text("ok"), DONE));

    var events = runner(client).run(session(Mode.CHAT), "go").toList();

    assertTrue(client.requests.get(1).messages().getLast().content().contains("Unknown tool: not_a_tool"));
    assertEquals(AgentEvent.Type.DONE, events.getLast().type());
  }
}
