package com.jvibe.gui.terminal;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Creates the shared icon-only terminal toggle and its restart context action. */
public final class TerminalToggleButton {
  private TerminalToggleButton() {}

  public static JButton create(Runnable toggle, Runnable restart) {
    var button = new JButton(new TerminalIcon());
    button.setText("");
    button.setToolTipText("Open terminal");
    button.setFocusable(false);
    button.setPreferredSize(new Dimension(32, 28));
    button.setMinimumSize(new Dimension(32, 28));
    button.addActionListener(event -> toggle.run());

    var menu = new JPopupMenu();
    var restartItem = new JMenuItem("Restart Shell");
    restartItem.addActionListener(event -> restart.run());
    menu.add(restartItem);
    button.setComponentPopupMenu(menu);
    return button;
  }

  private static final class TerminalIcon implements Icon {
    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      var g = (Graphics2D) graphics.create();
      try {
        g.setColor(component.getForeground());
        g.drawRoundRect(x, y + 1, 15, 12, 3, 3);
        g.drawLine(x + 4, y + 5, x + 7, y + 7);
        g.drawLine(x + 7, y + 7, x + 4, y + 9);
        g.drawLine(x + 9, y + 10, x + 13, y + 10);
      } finally {
        g.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return 16;
    }

    @Override
    public int getIconHeight() {
      return 14;
    }
  }
}
