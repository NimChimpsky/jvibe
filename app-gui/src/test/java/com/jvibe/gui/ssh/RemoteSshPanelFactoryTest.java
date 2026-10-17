package com.jvibe.gui.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.jvibe.ssh.SshAccessMode;
import com.jvibe.ssh.SshConnection;
import io.avaje.inject.BeanScope;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class RemoteSshPanelFactoryTest {
  @Test
  void tableShowsConnectionFields() {
    var model = new SshConnectionTableModel();
    model.setConnections(java.util.List.of(new SshConnection("", "prod", "host", "deploy", 22, "/key", SshAccessMode.READ_ONLY, "OK")));

    assertEquals("prod", model.getValueAt(0, 0));
    assertEquals("host", model.getValueAt(0, 1));
    assertEquals(SshAccessMode.READ_ONLY, model.getValueAt(0, 5));
  }

  @Test
  void mentionControllerDetectsPrefix() {
    var input = new JTextArea("@pro");
    input.setCaretPosition(4);
    var controller = new SshMentionController(input, java.util.List::of, connection -> {});

    assertEquals("pro", controller.activePrefix());
  }

  @Test
  void remoteSshPanelFactoryIsInjectable() {
    try (var scope = BeanScope.builder().build()) {
      assertNotNull(scope.get(RemoteSshPanelFactory.class));
    }
  }

  @Test
  void sshChatReusesSharedComposerAndContextDescribesAccess() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      var panel = new RemoteSshChatPanel(
          java.nio.file.Path.of("."), mock(com.jvibe.session.SessionFactory.class),
          mock(com.jvibe.ssh.SshConnectionService.class),
          mock(com.jvibe.gui.controller.ModelSelectionController.class),
          mock(com.jvibe.gui.chat.ChatPromptRunner.class), message -> { });
      assertTrue(java.util.Arrays.stream(panel.getComponents())
          .anyMatch(com.jvibe.gui.chat.ChatComposer.class::isInstance));
      var connection = new SshConnection("prod", "Production", "host", "user", 22, "", SshAccessMode.READ_ONLY, "");
      var context = RemoteSshChatPanel.promptWithSessions("Inspect disk", java.util.List.of(connection));
      assertTrue(context.contains("sessionId=prod"));
      assertTrue(context.contains("accessMode=READ_ONLY"));
      assertTrue(context.endsWith("User request:\nInspect disk"));
    });
  }
}
