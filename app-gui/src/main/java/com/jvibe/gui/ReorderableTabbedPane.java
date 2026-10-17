package com.jvibe.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTabbedPane;

public final class ReorderableTabbedPane extends JTabbedPane {
  private int draggedIndex = -1;
  private boolean reordered;
  private Runnable onReordered = () -> {};

  public ReorderableTabbedPane() {
    setTabLayoutPolicy(SCROLL_TAB_LAYOUT);
    var mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        beginDrag(indexAtLocation(event.getX(), event.getY()));
      }

      @Override
      public void mouseDragged(MouseEvent event) {
        moveDraggedTabTo(indexAtLocation(event.getX(), event.getY()));
      }

      @Override
      public void mouseReleased(MouseEvent event) {
        finishDrag();
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  public void onReordered(Runnable onReordered) {
    this.onReordered = onReordered == null ? () -> {} : onReordered;
  }

  void beginDrag(int index) {
    draggedIndex = validIndex(index) ? index : -1;
    reordered = false;
    if (draggedIndex >= 0) {
      setSelectedIndex(draggedIndex);
    }
  }

  void moveDraggedTabTo(int target) {
    if (draggedIndex < 0 || !validIndex(target) || target == draggedIndex) {
      return;
    }
    moveTab(draggedIndex, target);
    draggedIndex = target;
    reordered = true;
  }

  void finishDrag() {
    if (reordered) {
      onReordered.run();
    }
    draggedIndex = -1;
    reordered = false;
  }

  void moveTab(int from, int to) {
    if (!validIndex(from) || !validIndex(to) || from == to) {
      return;
    }
    var title = getTitleAt(from);
    var icon = getIconAt(from);
    var disabledIcon = getDisabledIconAt(from);
    var component = getComponentAt(from);
    var tabComponent = getTabComponentAt(from);
    var tooltip = getToolTipTextAt(from);
    var enabled = isEnabledAt(from);
    var mnemonic = getMnemonicAt(from);
    var mnemonicIndex = getDisplayedMnemonicIndexAt(from);
    Color foreground = getForegroundAt(from);
    Color background = getBackgroundAt(from);

    removeTabAt(from);
    insertTab(title, icon, component, tooltip, to);
    setDisabledIconAt(to, disabledIcon);
    setEnabledAt(to, enabled);
    setMnemonicAt(to, mnemonic);
    if (mnemonicIndex >= 0) {
      setDisplayedMnemonicIndexAt(to, mnemonicIndex);
    }
    setForegroundAt(to, foreground);
    setBackgroundAt(to, background);
    setTabComponentAt(to, tabComponent);
    setSelectedIndex(to);
  }

  public String tabIdAt(int index) {
    var component = getComponentAt(index);
    if (component instanceof javax.swing.JComponent jComponent) {
      var id = jComponent.getClientProperty(MainTabsController.TAB_ID_PROPERTY);
      return id instanceof String value ? value : null;
    }
    return null;
  }

  private boolean validIndex(int index) {
    return index >= 0 && index < getTabCount();
  }
}
