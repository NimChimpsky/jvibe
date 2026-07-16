package com.jvibe.gui.output;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class OutputPanel extends JPanel {
  private final Path tmp;
  private final BiConsumer<String, String> log;
  private final JPanel rows = new JPanel();

  public OutputPanel(Path tmp, BiConsumer<String, String> log) {
    super(new BorderLayout());
    this.tmp = tmp.toAbsolutePath().normalize();
    this.log = log;
    rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
    rows.setBorder(new EmptyBorder(4, 0, 4, 0));
    add(new JScrollPane(rows), BorderLayout.CENTER);
  }

  public void refresh() {
    rows.removeAll();
    try {
      Files.createDirectories(tmp);
      try (var stream = Files.list(tmp)) {
        stream
            .filter(Files::isRegularFile)
            .sorted(Comparator.comparing(this::modifiedMillis).reversed().thenComparing(Path::toString))
            .forEach(path -> rows.add(row(path)));
      }
    } catch (IOException e) {
      log.accept("ERROR", "Unable to refresh output files: " + e.getMessage());
    }
    rows.revalidate();
    rows.repaint();
  }

  java.util.List<Path> files() {
    try {
      Files.createDirectories(tmp);
      try (var stream = Files.list(tmp)) {
        return stream
            .filter(Files::isRegularFile)
            .sorted(Comparator.comparing(this::modifiedMillis).reversed().thenComparing(Path::toString))
            .toList();
      }
    } catch (IOException e) {
      return java.util.List.of();
    }
  }

  private JPanel row(Path path) {
    var row = new JPanel(new BorderLayout(8, 0));
    row.setBorder(new EmptyBorder(4, 6, 4, 2));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    var link = new JButton(path.getFileName().toString());
    link.setToolTipText(path.toString());
    link.setBorder(BorderFactory.createEmptyBorder());
    var linkColour = UIManager.getColor("Component.linkColor");
    if (linkColour != null) {
      link.setForeground(linkColour);
    }
    link.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    link.setHorizontalAlignment(JButton.LEFT);
    link.addActionListener(event -> open(path));
    var delete = new JButton(new BinIcon());
    delete.setToolTipText("Delete " + path.getFileName());
    delete.setFocusable(false);
    delete.setPreferredSize(new Dimension(28, 24));
    delete.addActionListener(event -> delete(path));
    row.add(link, BorderLayout.CENTER);
    row.add(delete, BorderLayout.EAST);
    return row;
  }

  private void open(Path path) {
    try {
      if (!Desktop.isDesktopSupported()) {
        log.accept("WARN", "Desktop file open is not supported");
        return;
      }
      Desktop.getDesktop().open(path.toFile());
      log.accept("INFO", "Opened output file: " + path);
    } catch (IOException | RuntimeException e) {
      log.accept("ERROR", "Unable to open output file: " + e.getMessage());
    }
  }

  private void delete(Path path) {
    try {
      if (!path.toAbsolutePath().normalize().startsWith(tmp)) {
        throw new IOException("Path escapes tmp: " + path);
      }
      Files.deleteIfExists(path);
      log.accept("INFO", "Deleted output file: " + path.getFileName());
      refresh();
    } catch (IOException e) {
      log.accept("ERROR", "Unable to delete output file: " + e.getMessage());
    }
  }

  private long modifiedMillis(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (IOException e) {
      return 0L;
    }
  }

  private static final class BinIcon implements Icon {
    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      var g = (Graphics2D) graphics.create();
      try {
        g.setColor(UIManager.getColor("Button.foreground"));
        g.drawLine(x + 4, y + 4, x + 12, y + 4);
        g.drawLine(x + 6, y + 2, x + 10, y + 2);
        g.drawRect(x + 5, y + 6, 6, 8);
        g.drawLine(x + 7, y + 8, x + 7, y + 12);
        g.drawLine(x + 9, y + 8, x + 9, y + 12);
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
      return 16;
    }
  }
}
