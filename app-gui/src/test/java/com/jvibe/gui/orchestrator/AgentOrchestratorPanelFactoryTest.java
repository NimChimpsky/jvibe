package com.jvibe.gui.orchestrator;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibeConfig;
import com.jvibe.config.JvibePaths;
import com.jvibe.deployment.DeploymentService;
import com.jvibe.monitor.MonitorRegistry;
import com.jvibe.ssh.SshDeploymentService;
import io.avaje.inject.BeanScope;
import java.awt.Component;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentOrchestratorPanelFactoryTest {
  @TempDir
  Path temp;

  @Test
  void createsDeployAndServiceActions() throws Exception {
    var config = new ConfigService(JvibePaths.under(temp));
    config.saveConfig(new JvibeConfig("jvibe", "jvibe-coder", 10, "DRACULA", false, "127.0.0.1", 8737, List.of(),
        List.of(), false, "127.0.0.1", 0, List.of(), List.of(), List.of()));
    var registry = new MonitorRegistry(config);
    var factory = new AgentOrchestratorPanelFactory(
        new DeploymentService(config, org.mockito.Mockito.mock(SshDeploymentService.class)),
        registry,
        new MonitorIntakeServer(config, registry),
        null);

    var panel = factory.create(
        null,
        () -> new AgentOrchestratorContext(temp, "jvibe", "jvibe-coder"),
        message -> {});

    assertNotNull(findButton(panel, "Deploy Agent"));
    assertNotNull(findButton(panel, "Add Service"));
    assertNotNull(findButton(panel, "Analyze Selected"));
  }

  @Test
  void isAvailableFromAvajeScope() {
    try (var scope = BeanScope.builder().build()) {
      assertNotNull(scope.get(AgentOrchestratorPanelFactory.class));
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
