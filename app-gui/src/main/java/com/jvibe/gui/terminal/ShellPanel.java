package com.jvibe.gui.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class ShellPanel extends JPanel {
  private final Path workingDirectory;
  private final Consumer<String> log;
  private ShellSession session;
  private JediTermWidget terminal;

  public ShellPanel(Path workingDirectory, Consumer<String> log) {
    super(new BorderLayout());
    this.workingDirectory = workingDirectory;
    this.log = log;
    setBorder(new EmptyBorder(0, 12, 12, 12));
    setMinimumSize(new Dimension(0, 120));
    setPreferredSize(new Dimension(0, 220));
  }

  public void startOrRestore() {
    if (session == null || !session.isConnected()) {
      start();
      return;
    }
    terminal.requestFocusInWindow();
  }

  public void stopSession() {
    closeSession();
  }

  public void restart() {
    closeSession();
    start();
  }

  private void start() {
    closeSession();
    var command = ShellLauncher.currentShellCommand();
    session = new ShellSession(workingDirectory, command);
    var background = terminalBackground();
    var foreground = terminalForeground();
    terminal = new JediTermWidget(new TerminalSettings(foreground, background, selectionBackground()));
    terminal.setTtyConnector(session);
    terminal.setOpaque(true);
    terminal.setBackground(background);
    terminal.getTerminalPanel().setOpaque(true);
    terminal.getTerminalPanel().setBackground(background);
    terminal.getTerminalPanel().setForeground(foreground);
    removeAll();
    add(terminal.getComponent(), BorderLayout.CENTER);
    revalidate();
    repaint();
    try {
      session.start();
      terminal.start();
      SwingUtilities.invokeLater(terminal::requestFocusInWindow);
      log.accept("Shell started: " + String.join(" ", command));
    } catch (IOException e) {
      log.accept("Shell failed: " + e.getMessage());
      closeSession();
    }
  }

  private void closeSession() {
    if (terminal != null) {
      terminal.close();
      terminal = null;
    }
    if (session != null) {
      session.close();
      session = null;
    }
    removeAll();
    revalidate();
    repaint();
  }

  private Color terminalBackground() {
    var base = UIManager.getColor("Panel.background");
    var tint = UIManager.getColor("TextArea.background");
    if (base == null) {
      base = Color.DARK_GRAY;
    }
    if (tint == null) {
      tint = base;
    }
    return blend(base, tint, 0.35);
  }

  private Color terminalForeground() {
    var foreground = UIManager.getColor("TextArea.foreground");
    if (foreground == null) {
      foreground = UIManager.getColor("Label.foreground");
    }
    return foreground == null ? Color.WHITE : foreground;
  }

  private Color selectionBackground() {
    var selection = UIManager.getColor("TextArea.selectionBackground");
    return selection == null ? new Color(82, 109, 165) : selection;
  }

  private static Color blend(Color base, Color tint, double amount) {
    var inverse = 1.0 - amount;
    return new Color(
        (int) Math.round(base.getRed() * inverse + tint.getRed() * amount),
        (int) Math.round(base.getGreen() * inverse + tint.getGreen() * amount),
        (int) Math.round(base.getBlue() * inverse + tint.getBlue() * amount));
  }

  private static TerminalColor terminalColor(Color color) {
    if (color == null) {
      return TerminalColor.WHITE;
    }
    return TerminalColor.rgb(color.getRed(), color.getGreen(), color.getBlue());
  }

  private static final class TerminalSettings extends DefaultSettingsProvider {
    private final TerminalColor foreground;
    private final TerminalColor background;
    private final TerminalColor selectionBackground;

    TerminalSettings(Color foreground, Color background, Color selectionBackground) {
      this.foreground = terminalColor(foreground);
      this.background = terminalColor(background);
      this.selectionBackground = terminalColor(selectionBackground);
    }

    @Override
    public TerminalColor getDefaultForeground() {
      return foreground;
    }

    @Override
    public TerminalColor getDefaultBackground() {
      return background;
    }

    @SuppressWarnings("deprecation")
    @Override
    public TextStyle getDefaultStyle() {
      return new TextStyle(foreground, background);
    }

    @Override
    public TextStyle getSelectionColor() {
      return new TextStyle(foreground, selectionBackground);
    }

    @Override
    public TextStyle getHyperlinkColor() {
      return new TextStyle(TerminalColor.rgb(88, 166, 255), background);
    }

    @Override
    public Font getTerminalFont() {
      return new Font(Font.MONOSPACED, Font.PLAIN, 12);
    }

    @Override
    public float getTerminalFontSize() {
      return 12f;
    }

    @Override
    public boolean audibleBell() {
      return false;
    }

    @Override
    public int maxRefreshRate() {
      return 60;
    }

    @Override
    public int getBufferMaxLinesCount() {
      return 5000;
    }
  }
}
