package com.jvibe.gui.chat;

import com.jvibe.gui.terminal.ShellPanel;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/** View-owned terminal below the fixed Build composer; never instantiated by Chat. */
final class BuildTerminal implements AutoCloseable {
  private static final int HEIGHT = 220;
  private final ShellPanel shell;
  private final JSplitPane split;
  private final Consumer<String> log;
  private boolean closed;

  BuildTerminal(Path root, JComponent chatAndComposer, Consumer<String> log) {
    this.log = log;
    shell = new ShellPanel(root, log);
    shell.setVisible(false);
    split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chatAndComposer, shell);
    split.setResizeWeight(1.0);
    split.setDividerSize(0);
    split.setBorder(null);
  }

  JComponent component() { return split; }

  void toggle() {
    if (closed) return;
    if (shell.isVisible()) {
      shell.setVisible(false);
      split.setDividerSize(0);
      split.setDividerLocation(1.0);
      split.revalidate();
    } else show();
  }

  void restart() {
    if (closed) return;
    show();
    shell.restart();
    log.accept("Shell restart requested");
  }

  private void show() {
    shell.setVisible(true);
    split.setDividerSize(6);
    split.revalidate();
    SwingUtilities.invokeLater(() -> {
      if (closed || !shell.isVisible()) return;
      split.setDividerLocation(Math.max(180, split.getHeight() - HEIGHT));
      shell.startOrRestore();
    });
  }

  @Override public void close() {
    closed = true;
    shell.stopSession();
  }
}
