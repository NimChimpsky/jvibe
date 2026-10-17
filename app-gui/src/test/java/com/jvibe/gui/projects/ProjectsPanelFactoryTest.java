package com.jvibe.gui.projects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.avaje.inject.BeanScope;
import java.awt.Component;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.Test;

class ProjectsPanelFactoryTest {
  @Test
  void initialLayoutKeepsTheBuildComposerAndGitPanelUsable() throws Exception {
    var root = new java.util.concurrent.atomic.AtomicReference<JSplitPane>();
    var chat = new JPanel();
    var git = new JPanel();
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      var projects = new JPanel();
      projects.setPreferredSize(new java.awt.Dimension(240, 0));
      projects.setMinimumSize(new java.awt.Dimension(200, 0));
      git.setMinimumSize(new java.awt.Dimension(280, 0));
      git.setPreferredSize(new java.awt.Dimension(340, 0));
      var layout = ProjectsPanelFactory.layout(projects, chat, git);
      layout.setSize(1040, 680);
      root.set(layout);
      layoutTree(layout);
    });
    javax.swing.SwingUtilities.invokeAndWait(() -> layoutTree(root.get()));
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      layoutTree(root.get());
      assertTrue(chat.getWidth() >= 420, "Build composer needs at least 420px");
      assertTrue(git.getWidth() >= 280, "Git panel should remain usable");
    });
  }

  private static void layoutTree(java.awt.Container container) {
    container.doLayout();
    for (var component : container.getComponents()) {
      if (component instanceof java.awt.Container child) layoutTree(child);
    }
  }

  @Test
  void factoryIsResolvedByAvajeInject() {
    try (var scope = BeanScope.builder().build()) {
      assertNotNull(scope.get(ProjectsPanelFactory.class));
    }
  }

  @Test
  void viewUsesProjectChatAndGitColumns() {
    var projects = new JPanel();
    projects.add(new JButton("+"));
    var chat = new JPanel();
    var git = new JPanel();
    git.add(new JButton("Refresh"));
    var gitTabs = new JTabbedPane();
    gitTabs.addTab("Changes", new JPanel());
    gitTabs.addTab("Diff", new JPanel());
    git.add(gitTabs);

    var root = ProjectsPanelFactory.layout(projects, chat, git);

    assertEquals(JSplitPane.HORIZONTAL_SPLIT, root.getOrientation());
    assertTrue(root.getRightComponent() instanceof JSplitPane);
    assertNotNull(findButton(root, "+"));
    assertNotNull(findButton(root, "Refresh"));
    assertTrue(containsTab(root, "Changes"));
    assertTrue(containsTab(root, "Diff"));
  }

  private static JButton findButton(Component component, String text) {
    if (component instanceof JButton button && text.equals(button.getText())) {
      return button;
    }
    if (component instanceof java.awt.Container container) {
      for (var child : container.getComponents()) {
        var found = findButton(child, text);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static boolean containsTab(Component component, String title) {
    if (component instanceof JTabbedPane tabs) {
      for (var index = 0; index < tabs.getTabCount(); index++) {
        if (title.equals(tabs.getTitleAt(index))) {
          return true;
        }
      }
    }
    if (component instanceof java.awt.Container container) {
      for (var child : container.getComponents()) {
        if (containsTab(child, title)) {
          return true;
        }
      }
    }
    return false;
  }
}
