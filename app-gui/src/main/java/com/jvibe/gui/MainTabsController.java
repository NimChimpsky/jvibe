package com.jvibe.gui;

import com.jvibe.config.ConfigService;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.swing.JComponent;

@Singleton
public final class MainTabsController {
  static final String TAB_ID_PROPERTY = "jvibe.mainTabId";

  private final ConfigService configService;

  public MainTabsController(@External ConfigService configService) {
    this.configService = configService;
  }

  public ReorderableTabbedPane create(List<MainTab> tabs) {
    var pane = new ReorderableTabbedPane();
    for (var tab : ordered(tabs, savedOrder())) {
      tab.content().putClientProperty(TAB_ID_PROPERTY, tab.id());
      pane.addTab(tab.title(), tab.content());
    }
    pane.onReordered(() -> saveOrder(pane));
    return pane;
  }

  static List<MainTab> ordered(List<MainTab> tabs, List<String> requestedOrder) {
    var byId = new LinkedHashMap<String, MainTab>();
    tabs.forEach(tab -> byId.put(tab.id(), tab));
    var ordered = new ArrayList<MainTab>();
    for (var id : requestedOrder) {
      var tab = byId.remove(id);
      if (tab != null) {
        ordered.add(tab);
      }
    }
    ordered.addAll(byId.values());
    return ordered;
  }

  private List<String> savedOrder() {
    try {
      return configService.config().mainTabOrder();
    } catch (IOException e) {
      return List.of();
    }
  }

  private void saveOrder(ReorderableTabbedPane pane) {
    var order = new ArrayList<String>();
    for (var i = 0; i < pane.getTabCount(); i++) {
      var id = pane.tabIdAt(i);
      if (id != null && !id.isBlank()) {
        order.add(id);
      }
    }
    try {
      configService.updateConfig(existing -> existing.withMainTabOrder(order));
    } catch (IOException ignored) {
      // Tab order is convenience state; the UI can keep running if persistence fails.
    }
  }

  public record MainTab(String id, String title, JComponent content) {}
}
