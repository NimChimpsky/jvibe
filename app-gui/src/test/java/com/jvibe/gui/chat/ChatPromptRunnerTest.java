package com.jvibe.gui.chat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jvibe.events.AgentEvent;
import com.jvibe.gui.controller.PromptController;
import com.jvibe.logging.FileLogService;
import com.jvibe.session.AgentSession;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatPromptRunnerTest {
  @TempDir
  Path temp;

  @Test
  void streamsAndLogsAssistantResponse() throws Exception {
    var prompts = mock(PromptController.class);
    var fileLog = mock(FileLogService.class);
    var callbacks = mock(DesktopChatController.Callbacks.class);
    var session = new AgentSession(new Workspace(temp), new ToolRegistry());
    session.selectModel("test-provider", "test-model");
    when(prompts.submit(org.mockito.ArgumentMatchers.eq(session), org.mockito.ArgumentMatchers.eq("hello"),
        org.mockito.ArgumentMatchers.any(com.jvibe.agent.RunControl.class))).thenReturn(Stream.of(
        new AgentEvent(AgentEvent.Type.ASSISTANT_DELTA, "response"),
        new AgentEvent(AgentEvent.Type.DONE, "")));
    var finished = new CountDownLatch(1);
    var runner = new ChatPromptRunner(prompts, fileLog);

    SwingUtilities.invokeAndWait(() ->
        runner.submit(session, new ChatTranscript(), "hello", finished::countDown, callbacks));

    assertTrue(finished.await(3, TimeUnit.SECONDS));
    SwingUtilities.invokeAndWait(() -> {});
    verify(fileLog).prompt("test-provider", "test-model", "hello");
    verify(fileLog).assistant("test-provider", "test-model", "response");
    verify(callbacks).log("INFO", "Run complete");
    verify(callbacks).workspaceChanged();
  }
}
