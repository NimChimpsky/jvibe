package com.jvibe.gui.analysis;

import jakarta.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

@Singleton
public final class JfrPanelFactory {
  private final JfrAnalysisService service;

  public JfrPanelFactory(JfrAnalysisService service) {
    this.service = service;
  }

  public JComponent create() {
    var panel = new JPanel(new BorderLayout(0, 8));
    panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));

    var status = new JLabel("Open a Java Flight Recorder file.");
    var open = new JButton("Open JFR");
    var header = new JPanel(new BorderLayout(8, 0));
    header.add(open, BorderLayout.WEST);
    header.add(status, BorderLayout.CENTER);
    panel.add(header, BorderLayout.NORTH);

    var overview = textArea();
    var summary = table("Event type", "Label", "Count", "First", "Last", "Total duration");
    var events = table("Event type", "Start", "Duration", "Description");
    var eventFilter = new JTextField();
    var eventSorter = new TableRowSorter<>((DefaultTableModel) events.getModel());
    events.setRowSorter(eventSorter);
    eventFilter.getDocument().addDocumentListener((SimpleDocumentListener) () -> applyFilter(eventSorter, eventFilter.getText()));
    var eventPanel = new JPanel(new BorderLayout(0, 8));
    eventPanel.add(eventFilter, BorderLayout.NORTH);
    eventPanel.add(scroll(events), BorderLayout.CENTER);

    var hotMethods = table("Method", "Samples");
    var hotClasses = table("Class", "Samples");
    var hotSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll(hotMethods), scroll(hotClasses));
    hotSplit.setResizeWeight(0.5);
    hotSplit.setDividerSize(6);
    hotSplit.setBorder(null);
    var stacks = textArea();

    var tabs = new JTabbedPane();
    tabs.addTab("Overview", scroll(overview));
    tabs.addTab("Event Summary", scroll(summary));
    tabs.addTab("Events", eventPanel);
    tabs.addTab("Hot Frames", hotSplit);
    tabs.addTab("Stacks", scroll(stacks));
    panel.add(tabs, BorderLayout.CENTER);

    open.addActionListener(event -> chooseAndLoad(panel, status, overview, summary, events, hotMethods, hotClasses, stacks));
    return panel;
  }

  private void chooseAndLoad(
      JComponent parent,
      JLabel status,
      JTextArea overview,
      JTable summary,
      JTable events,
      JTable hotMethods,
      JTable hotClasses,
      JTextArea stacks) {
    var chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Java Flight Recorder (*.jfr)", "jfr"));
    if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
      load(chooser.getSelectedFile().toPath(), status, overview, summary, events, hotMethods, hotClasses, stacks);
    }
  }

  private void load(
      Path file,
      JLabel status,
      JTextArea overview,
      JTable summary,
      JTable events,
      JTable hotMethods,
      JTable hotClasses,
      JTextArea stacks) {
    status.setText("Loading " + file);
    new SwingWorker<JfrAnalysis, Void>() {
      @Override
      protected JfrAnalysis doInBackground() throws Exception {
        return service.analyze(file);
      }

      @Override
      protected void done() {
        try {
          var analysis = get();
          render(analysis, overview, summary, events, hotMethods, hotClasses, stacks);
          status.setText("Loaded " + file);
        } catch (Exception e) {
          clear(summary);
          clear(events);
          clear(hotMethods);
          clear(hotClasses);
          stacks.setText("");
          overview.setText("Unable to read JFR file:\n" + rootMessage(e));
          status.setText("Unable to read JFR file");
        }
      }
    }.execute();
  }

  private static void render(
      JfrAnalysis analysis,
      JTextArea overview,
      JTable summary,
      JTable events,
      JTable hotMethods,
      JTable hotClasses,
      JTextArea stacks) {
    overview.setText("""
        File: %s
        Start: %s
        End: %s
        Duration: %s
        Events: %d
        Event types: %d
        Event detail: %s
        Stack detail: %s
        """.formatted(
        analysis.file(),
        analysis.startTime(),
        analysis.endTime(),
        analysis.duration(),
        analysis.eventCount(),
        analysis.eventTypeCount(),
        analysis.eventsTruncated() ? "first 100,000 rows shown" : "complete",
        analysis.stacksTruncated() ? "first 4 MiB shown" : "complete"));
    var summaryModel = model(summary);
    summaryModel.setRowCount(0);
    for (var row : analysis.eventSummaries()) {
      summaryModel.addRow(new Object[] {row.eventType(), row.label(), row.count(), row.firstTime(), row.lastTime(), row.totalDuration()});
    }
    var eventModel = model(events);
    eventModel.setRowCount(0);
    for (var row : analysis.events()) {
      eventModel.addRow(new Object[] {row.eventType(), row.startTime(), row.duration(), row.description()});
    }
    fillHotTable(hotMethods, analysis.hotMethods());
    fillHotTable(hotClasses, analysis.hotClasses());
    stacks.setText(analysis.stackTraceTree());
  }

  private static void fillHotTable(JTable table, java.util.List<JfrHotFrame> rows) {
    var model = model(table);
    model.setRowCount(0);
    for (var row : rows) {
      model.addRow(new Object[] {row.name(), row.samples()});
    }
  }

  private static JTable table(String... columns) {
    var table = new JTable(new DefaultTableModel(columns, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    });
    table.setAutoCreateRowSorter(true);
    return table;
  }

  private static JTextArea textArea() {
    var area = new JTextArea();
    area.setEditable(false);
    area.setLineWrap(false);
    area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    return area;
  }

  private static JScrollPane scroll(java.awt.Component component) {
    var scroll = new JScrollPane(component);
    scroll.setPreferredSize(new Dimension(320, 220));
    return scroll;
  }

  private static DefaultTableModel model(JTable table) {
    return (DefaultTableModel) table.getModel();
  }

  private static void clear(JTable table) {
    model(table).setRowCount(0);
  }

  private static void applyFilter(TableRowSorter<DefaultTableModel> sorter, String text) {
    sorter.setRowFilter(text == null || text.isBlank() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
  }

  private static String rootMessage(Exception e) {
    var cause = e.getCause() == null ? e : e.getCause();
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  @FunctionalInterface
  private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
    void update();

    @Override
    default void insertUpdate(javax.swing.event.DocumentEvent event) {
      update();
    }

    @Override
    default void removeUpdate(javax.swing.event.DocumentEvent event) {
      update();
    }

    @Override
    default void changedUpdate(javax.swing.event.DocumentEvent event) {
      update();
    }
  }
}
