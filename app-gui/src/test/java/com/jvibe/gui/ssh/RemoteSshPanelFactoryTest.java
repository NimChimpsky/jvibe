package com.jvibe.gui.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
