package com.jvibe.gui.layout;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JSplitPane;

/** Applies an initial divider ratio once real layout dimensions exist, preserving later user drags. */
public final class SplitPaneSizing {
  private SplitPaneSizing() { }

  public static void initialProportion(JSplitPane split, double proportion) {
    split.addComponentListener(new ComponentAdapter() {
      @Override public void componentResized(ComponentEvent event) {
        int size = split.getOrientation() == JSplitPane.VERTICAL_SPLIT ? split.getHeight() : split.getWidth();
        if (size <= split.getDividerSize()) return;
        split.setDividerLocation(proportion);
        split.removeComponentListener(this);
      }
    });
  }
}
