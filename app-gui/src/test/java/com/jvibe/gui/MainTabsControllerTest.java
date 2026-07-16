package com.jvibe.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class MainTabsControllerTest {
  @Test
  void ordersKnownTabsAndAppendsMissingTabs() {
    var ordered = MainTabsController.ordered(
        List.of(
            new MainTabsController.MainTab("desktop", "Desktop", new JPanel()),
            new MainTabsController.MainTab("agent-orchestrator", "Agent Orchestrator", new JPanel()),
            new MainTabsController.MainTab("jfr", "JFR", new JPanel())),
        List.of("unknown", "jfr", "desktop"));

    assertEquals(List.of("jfr", "desktop", "agent-orchestrator"), ordered.stream().map(MainTabsController.MainTab::id).toList());
  }
}
