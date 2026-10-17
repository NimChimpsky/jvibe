package com.jvibe.gui;

import static org.junit.jupiter.api.Assertions.*;
import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTabsControllerTest {
  @TempDir Path home;

  private List<MainTabsController.MainTab> definitions() {
    return List.of(new MainTabsController.MainTab("chat", "Chat", new JPanel()),
        new MainTabsController.MainTab("build", "Build", new JPanel()),
        new MainTabsController.MainTab("servers", "Servers", new JPanel()),
        new MainTabsController.MainTab("databases", "Databases", new JPanel()),
        new MainTabsController.MainTab("jvibe", "JVibe", new JPanel()));
  }

  @Test void removedTabsAreIgnoredAndEveryCurrentTabIsAdded() {
    var tabs = MainTabsController.ordered(definitions(), List.of("removed", "build", "chat", "build"));
    assertEquals(List.of("build", "chat", "servers", "databases", "jvibe"),
        tabs.stream().map(MainTabsController.MainTab::id).toList());
  }

  @Test void dragOrderPersistsAndAlwaysOpensOnItsFirstTab() throws Exception {
    var config = new ConfigService(JvibePaths.under(home));
    var controller = new MainTabsController(config);
    var tabs = controller.create(definitions());
    assertEquals(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT, tabs.getTabLayoutPolicy());
    assertEquals("Chat", tabs.getTitleAt(0));
    tabs.beginDrag(1);
    tabs.moveDraggedTabTo(0);
    tabs.finishDrag();
    assertEquals("build", config.config().mainTabOrder().getFirst());
    var reopened = controller.create(definitions());
    assertEquals(0, reopened.getSelectedIndex());
    assertEquals("Build", reopened.getTitleAt(0));
  }
}
