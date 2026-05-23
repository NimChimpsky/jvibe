package com.jvibe.gui.swarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import com.jvibe.deployment.DeploymentService;
import io.avaje.inject.BeanScope;
import java.awt.Component;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentSwarmPanelFactoryTest {
  @TempDir
  Path temp;

  @Test
  void createsDeployAgentSection() {
    var factory = new AgentSwarmPanelFactory(new DeploymentService(new ConfigService(JvibePaths.under(temp))));
    var panel = factory.create(
        null,
        () -> new AgentSwarmContext(temp, "jvibe", "jvibe-coder"),
        message -> {});

    var button = findButton(panel, "Deploy Agent");
    assertNotNull(button);
    assertEquals("Deploy Agent", button.getText());
  }

  @Test
  void isAvailableFromAvajeScope() {
    try (var scope = BeanScope.builder().build()) {
      assertNotNull(scope.get(AgentSwarmPanelFactory.class));
    }
  }

  private static JButton findButton(Component component, String text) {
    if (component instanceof JButton button && text.equals(button.getText())) {
      return button;
    }
    if (component instanceof JComponent container) {
      for (var child : container.getComponents()) {
        var found = findButton(child, text);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
