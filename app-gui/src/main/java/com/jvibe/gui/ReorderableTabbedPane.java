package com.jvibe.gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTabbedPane;

public final class ReorderableTabbedPane extends JTabbedPane {
  private int draggedIndex = -1;
  private Runnable onReordered = () -> {};

  public ReorderableTabbedPane() {
    var mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        draggedIndex = indexAtLocation(event.getX(), event.getY());
      }

      @Override
      public void mouseDragged(MouseEvent event) {
        var target = indexAtLocation(event.getX(), event.getY());
        if (draggedIndex >= 0 && target >= 0 && target != draggedIndex) {
          moveTab(draggedIndex, target);
          draggedIndex = target;
          onReordered.run();
        }
      }

      @Override
      public void mouseReleased(MouseEvent event) {
        draggedIndex = -1;
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  public void onReordered(Runnable onReordered) {
    this.onReordered = onReordered == null ? () -> {} : onReordered;
  }

  private void moveTab(int from, int to) {
    var title = getTitleAt(from);
    var icon = getIconAt(from);
    var component = getComponentAt(from);
    var tooltip = getToolTipTextAt(from);
    var enabled = isEnabledAt(from);
    var mnemonic = getMnemonicAt(from);
    var selected = getSelectedIndex() == from;

    removeTabAt(from);
    insertTab(title, icon, component, tooltip, to);
    setEnabledAt(to, enabled);
    setMnemonicAt(to, mnemonic);
    if (selected) {
      setSelectedIndex(to);
    }
  }

  public String tabIdAt(int index) {
    var component = getComponentAt(index);
    return component instanceof javax.swing.JComponent jComponent
        ? (String) jComponent.getClientProperty(MainTabsController.TAB_ID_PROPERTY)
        : null;
  }
}
