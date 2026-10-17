package com.jvibe.gui.chat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jvibe.agent.AgentRunner;
import com.jvibe.agent.RunControl;
import com.jvibe.events.AgentEvent;
import com.jvibe.logging.FileLogService;
import com.jvibe.session.AgentSession;
import com.jvibe.session.Mode;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatPromptRunnerTest {
  @TempDir Path temp;

  @Test
  void streamsTextShowsToolActivityAndLogs() throws Exception {
    var agent = mock(AgentRunner.class);
    var fileLog = mock(FileLogService.class);
    var callbacks = mock(ChatCallbacks.class);
    var approvals = mock(ToolApprovalDialog.class);
    var session = new AgentSession(Mode.SERVERS, new Workspace(temp), new ToolRegistry(), () -> "");
    var finished = new AgentEvent(AgentEvent.Type.TOOL_FINISHED, "web01: uptime (exit 0)", Map.of("success", true), Instant.now());
    when(agent.run(eq(session), eq("hello"), any(RunControl.class), eq(approvals))).thenReturn(Stream.of(
        new AgentEvent(AgentEvent.Type.TOOL_STARTED, "ssh_exec",
            Map.of("arguments", Map.of("serverId", "web01", "command", "uptime")), Instant.now()),
        finished,
        new AgentEvent(AgentEvent.Type.ASSISTANT_DELTA, "response"),
        new AgentEvent(AgentEvent.Type.DONE, "")));
    var done = new CountDownLatch(1);
    var transcript = new ChatTranscript();

    SwingUtilities.invokeAndWait(() ->
        new ChatPromptRunner(agent, approvals, fileLog).submit(session, transcript, "hello", done::countDown, callbacks));

    assertTrue(done.await(3, TimeUnit.SECONDS));
    SwingUtilities.invokeAndWait(() -> {});
    verify(fileLog).prompt(Mode.SERVERS, "hello");
    verify(fileLog).assistant(Mode.SERVERS, "response");
    verify(callbacks).toolFinished(finished);
    assertTrue(ChatAccessTest.components(transcript.component(), JLabel.class).stream()
        .anyMatch(label -> label.getText().equals("✓ ssh_exec @web01 uptime")));
  }
}
