package com.jvibe.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.ComponentEvent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SplitPaneSizingTest {
  @Test
  void setsFirstDividerAndDoesNotOverrideUserResizing() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JPanel(), new JPanel());
      split.setDividerSize(6);
      split.setBorder(null);
      SplitPaneSizing.initialProportion(split, 0.4);
      split.setSize(500, 606);
      var event = new ComponentEvent(split, ComponentEvent.COMPONENT_RESIZED);
      for (var listener : split.getComponentListeners()) listener.componentResized(event);
      assertEquals(240, split.getDividerLocation());
      split.setDividerLocation(300);
      for (var listener : split.getComponentListeners()) listener.componentResized(event);
      assertEquals(300, split.getDividerLocation());
    });
  }
}
