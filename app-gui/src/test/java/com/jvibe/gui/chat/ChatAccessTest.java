package com.jvibe.gui.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.jvibe.agent.RunControl;
import com.jvibe.config.JvibePaths;
import com.jvibe.session.AgentSession;
import com.jvibe.session.Mode;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import io.avaje.inject.BeanScope;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatAccessTest {
  @TempDir Path home;

  @Test void attachmentRemainsReadableAfterSubmissionButChatCannotWriteOrOpenWorkspace() throws Exception {
    var runner = mock(ChatPromptRunner.class);
    var attachment = Files.writeString(home.resolve("notes.txt"), "context");
    when(runner.submit(any(), any(), anyString(), any(), any())).thenAnswer(invocation -> {
      var session = (AgentSession) invocation.getArgument(0);
      var result = session.toolRegistry().execute(new ToolCall("file_read", Map.of("path", attachment.toString())),
          ToolExecutionContext.defaults(session.workspace()));
      assertTrue(result.success(), result.summary());
      assertTrue(session.toolRegistry().find("file_write").isEmpty());
      assertTrue(session.context().contains("notes.txt"));
      return new RunControl();
    });
    try (var scope = BeanScope.builder().bean(JvibePaths.class, JvibePaths.under(home))
        .bean(ChatPromptRunner.class, runner).build()) {
      SwingUtilities.invokeAndWait(() -> {
        try (var chat = scope.get(DesktopChatFactory.class).create(callbacks())) {
          assertEquals(Mode.CHAT, chat.activeSession().mode());
          assertThrows(IllegalStateException.class, () -> chat.createWorkspace(home));
          chat.addFileReference(attachment);
          components(chat.component(), JTextArea.class).stream().filter(JTextArea::isEditable).findFirst().orElseThrow().setText("Summarise");
          components(chat.component(), JButton.class).stream().filter(button -> "Send".equals(button.getText())).findFirst().orElseThrow().doClick();
          verify(runner).submit(any(), any(), contains("notes.txt"), any(), any());
        }
      });
    }
  }

  @Test void buildHasNoDefaultChatAndRequiresGitRepository() throws Exception {
    try (var scope = BeanScope.builder().bean(JvibePaths.class, JvibePaths.under(home)).build()) {
      SwingUtilities.invokeAndWait(() -> {
        try (var build = scope.get(DesktopChatFactory.class).createBuild(callbacks())) {
          assertNull(build.activeSession());
          assertThrows(IllegalArgumentException.class, () -> build.createWorkspace(home));
          try { Files.createDirectory(home.resolve(".git")); } catch (Exception e) { throw new AssertionError(e); }
          build.createWorkspace(home);
          assertEquals(Mode.BUILD, build.activeSession().mode());
          assertTrue(build.activeSession().toolRegistry().find("file_write").isPresent());
        }
      });
    }
  }

  private static ChatCallbacks callbacks() { return mock(ChatCallbacks.class); }

  static <T> List<T> components(Component root, Class<T> type) {
    var found = new ArrayList<T>();
    if (type.isInstance(root)) found.add(type.cast(root));
    if (root instanceof Container container) for (var child : container.getComponents()) found.addAll(components(child, type));
    return found;
  }
}
