package com.jvibe.gui.chat;

import com.jvibe.config.ConfigService;
import com.jvibe.session.Mode;
import com.jvibe.tool.ToolApprover;
import com.jvibe.tool.ToolCall;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Asks before state-changing tools run, for tabs where "Ask before running" is enabled.
 * Called from the run's worker thread; the dialog itself runs on the event thread.
 */
@Singleton
public final class ToolApprovalDialog implements ToolApprover {
  private final ConfigService config;

  public ToolApprovalDialog(@External ConfigService config) {
    this.config = config;
  }

  @Override
  public boolean approve(Mode mode, ToolCall call) {
    if (!askBeforeRunning(mode)) return true;
    if (SwingUtilities.isEventDispatchThread()) return ask(mode, call);
    var approved = new AtomicBoolean();
    try {
      SwingUtilities.invokeAndWait(() -> approved.set(ask(mode, call)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (InvocationTargetException e) {
      return false;
    }
    return approved.get();
  }

  private boolean askBeforeRunning(Mode mode) {
    try {
      return config.config().mode(mode).askBeforeRunning();
    } catch (java.io.IOException e) {
      return true;
    }
  }

  private static boolean ask(Mode mode, ToolCall call) {
    var panel = new JPanel(new BorderLayout(0, 8));
    panel.add(new JLabel(ToolCalls.describe(call.name(), call.arguments())), BorderLayout.NORTH);
    var detail = ToolCalls.detail(call.arguments());
    if (!detail.isBlank()) {
      var text = new JTextArea(detail);
      text.setEditable(false);
      text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
      var scroll = new JScrollPane(text);
      scroll.setPreferredSize(new Dimension(560, Math.min(320, 40 + 16 * (int) detail.lines().count())));
      panel.add(scroll, BorderLayout.CENTER);
    }
    var options = new Object[] {"Run", "Skip"};
    var choice = JOptionPane.showOptionDialog(
        KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(),
        panel,
        "JVibe " + mode.label() + ": run this?",
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0]);
    return choice == 0;
  }
}
