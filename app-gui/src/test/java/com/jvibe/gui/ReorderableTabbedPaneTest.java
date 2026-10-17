package com.jvibe.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class ReorderableTabbedPaneTest {
  @Test
  void movesSelectedTabAndPreservesMetadata() {
    var tabs = new ReorderableTabbedPane();
    var first = new JPanel();
    var second = new JPanel();
    var header = new JLabel("First");
    tabs.addTab("First", first);
    tabs.addTab("Second", second);
    tabs.setToolTipTextAt(0, "first tooltip");
    tabs.setTabComponentAt(0, header);

    tabs.beginDrag(0);
    tabs.moveDraggedTabTo(1);
    tabs.finishDrag();

    assertSame(first, tabs.getComponentAt(1));
    assertSame(first, tabs.getSelectedComponent());
    assertSame(header, tabs.getTabComponentAt(1));
    assertEquals("first tooltip", tabs.getToolTipTextAt(1));
  }

  @Test
  void reportsOneOrderChangeWhenDragFinishes() {
    var tabs = new ReorderableTabbedPane();
    tabs.addTab("First", new JPanel());
    tabs.addTab("Second", new JPanel());
    tabs.addTab("Third", new JPanel());
    var changes = new AtomicInteger();
    tabs.onReordered(changes::incrementAndGet);

    tabs.beginDrag(0);
    tabs.moveDraggedTabTo(1);
    tabs.moveDraggedTabTo(2);
    assertEquals(0, changes.get());

    tabs.finishDrag();
    assertEquals(1, changes.get());
  }
}
