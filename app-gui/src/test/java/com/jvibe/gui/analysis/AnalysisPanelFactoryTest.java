package com.jvibe.gui.analysis;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.avaje.inject.BeanScope;
import java.awt.Component;
import javax.swing.JButton;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

class AnalysisPanelFactoryTest {
  @Test
  void jfrPanelIsInjectableAndExposesOpenAction() {
    try (var scope = BeanScope.builder().build()) {
      var panel = scope.get(JfrPanelFactory.class).create();

      assertNotNull(findButton(panel, "Open JFR"));
    }
  }

  @Test
  void stacktracePanelIsInjectableAndExposesOpenAction() {
    try (var scope = BeanScope.builder().build()) {
      var panel = scope.get(StacktracePanelFactory.class).create();

      assertNotNull(findButton(panel, "Open Stacktrace"));
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
