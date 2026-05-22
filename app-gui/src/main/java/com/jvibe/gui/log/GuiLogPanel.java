package com.jvibe.gui.log;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseEvent;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class GuiLogPanel {
  private final DefaultListModel<LogEntry> logModel = new DefaultListModel<>();
  private final JList<LogEntry> logList = logList(logModel);
  private final JTextArea fullLogArea = createFullLogArea();

  public JList<LogEntry> logList() {
    return logList;
  }

  public JTextArea fullLogArea() {
    return fullLogArea;
  }

  public void append(String time, String level, String message) {
    logModel.addElement(new LogEntry(time, level, message));
    logList.ensureIndexIsVisible(logModel.size() - 1);
  }

  public void setFullLog(String text) {
    fullLogArea.setText(text);
    fullLogArea.setCaretPosition(fullLogArea.getDocument().getLength());
  }

  private JList<LogEntry> logList(DefaultListModel<LogEntry> model) {
    var list = new JList<LogEntry>(model) {
      @Override
      public String getToolTipText(MouseEvent event) {
        var index = locationToIndex(event.getPoint());
        if (index < 0) {
          return null;
        }
        var bounds = getCellBounds(index, index);
        if (bounds == null || !bounds.contains(event.getPoint())) {
          return null;
        }
        var entry = getModel().getElementAt(index);
        return entry.time() + "  " + entry.level();
      }
    };
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new LogEntryRenderer());
    ToolTipManager.sharedInstance().registerComponent(list);
    return list;
  }

  private JTextArea createFullLogArea() {
    var area = new JTextArea();
    area.setEditable(false);
    area.setLineWrap(false);
    area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    return area;
  }

  private record LogEntry(String time, String level, String message) {}

  private static final class LogEntryRenderer extends JLabel implements ListCellRenderer<LogEntry> {
    LogEntryRenderer() {
      setOpaque(true);
      setBorder(new EmptyBorder(5, 8, 5, 8));
    }

    @Override
    public Component getListCellRendererComponent(
        JList<? extends LogEntry> list,
        LogEntry value,
        int index,
        boolean selected,
        boolean cellHasFocus) {
      setText(value.message());
      setFont(list.getFont());
      setBackground(selected ? list.getSelectionBackground() : list.getBackground());
      if (selected) {
        setForeground(list.getSelectionForeground());
      } else {
        setForeground(switch (value.level()) {
          case "WARN" -> UIManager.getColor("Actions.Yellow");
          case "ERROR" -> UIManager.getColor("Actions.Red");
          default -> list.getForeground();
        });
        if (getForeground() == null) {
          setForeground(list.getForeground());
        }
      }
      return this;
    }
  }
}
