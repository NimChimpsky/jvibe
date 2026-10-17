package com.jvibe.gui.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.jvibe.gui.chat.AttachmentChatPanel;
import com.jvibe.gui.chat.ChatCallbacks;
import com.jvibe.gui.chat.ChatPromptRunner;
import com.jvibe.session.SessionFactory;
import com.jvibe.ssh.SshConnection;
import io.avaje.inject.BeanScope;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class RemoteSshPanelFactoryTest {
  private final SshConnection prod = new SshConnection("prod", "prod", "host", "deploy", 22, "/key", "OK");

  @Test
  void tableShowsConnectionFields() {
    var model = new SshConnectionTableModel();
    model.setConnections(List.of(prod));

    assertEquals("prod", model.getValueAt(0, 0));
    assertEquals("host", model.getValueAt(0, 1));
    assertEquals("OK", model.getValueAt(0, 5));
  }

  @Test
  void remoteSshPanelFactoryIsInjectable() {
    try (var scope = BeanScope.builder().build()) {
      assertNotNull(scope.get(RemoteSshPanelFactory.class));
    }
  }

  @Test
  void attachedServersShowAsChipsAndFeedTheSession() throws Exception {
    try (var scope = BeanScope.builder().build()) {
      var sessions = scope.get(SessionFactory.class);
      SwingUtilities.invokeAndWait(() -> {
        var created = new ArrayList<String>();
        var panel = new AttachmentChatPanel<>(
            new AttachmentChatPanel.Attachables<>("", () -> List.of(prod), SshConnection::id,
                SshConnection::name, SshConnection::address),
            attached -> {
              var session = sessions.servers(attached);
              created.add(session.mode().id());
              return session;
            },
            attached -> true,
            mock(ChatPromptRunner.class),
            mock(ChatCallbacks.class));
        panel.attach(prod);
        panel.attach(prod);
        assertEquals(List.of("servers"), created);
        assertEquals(1, components(panel, JButton.class).stream()
            .filter(button -> button.getText().startsWith("@prod")).count());
        assertTrue(components(panel, JButton.class).stream().anyMatch(button -> "New conversation".equals(button.getText())));
      });
    }
  }

  private static <T> List<T> components(Component component, Class<T> type) {
    var found = new ArrayList<T>();
    if (type.isInstance(component)) found.add(type.cast(component));
    if (component instanceof Container container) {
      for (var child : container.getComponents()) found.addAll(components(child, type));
    }
    return found;
  }
}
