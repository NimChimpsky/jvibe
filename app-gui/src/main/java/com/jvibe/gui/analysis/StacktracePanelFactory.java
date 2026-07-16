package com.jvibe.gui.analysis;

import jakarta.inject.Singleton;
import java.awt.BorderLayout;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

@Singleton
public final class StacktracePanelFactory {
  private final StacktraceAnalysisService service;

  public StacktracePanelFactory(StacktraceAnalysisService service) {
    this.service = service;
  }

  public JComponent create() {
    var panel = new JPanel(new BorderLayout(0, 8));
    panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    var status = new JLabel("Open a JVM stacktrace, exception log, or thread dump.");
    var open = new JButton("Open Stacktrace");
    var header = new JPanel(new BorderLayout(8, 0));
    header.add(open, BorderLayout.WEST);
    header.add(status, BorderLayout.CENTER);
    panel.add(header, BorderLayout.NORTH);

    var overview = textArea();
    var findings = table("Severity", "Finding");
    var states = table("State", "Threads");
    var threads = new JTree(new DefaultMutableTreeNode("No thread dump loaded"));
    var exceptions = table("Type", "Message", "Frames");
    var raw = textArea();
    var rawSearch = new JTextField();
    rawSearch.setToolTipText("Search raw text");
    rawSearch.addActionListener(event -> find(raw, rawSearch.getText()));
    var rawPanel = new JPanel(new BorderLayout(0, 8));
    rawPanel.add(rawSearch, BorderLayout.NORTH);
    rawPanel.add(scroll(raw), BorderLayout.CENTER);

    var tabs = new JTabbedPane();
    tabs.addTab("Overview", scroll(overview));
    tabs.addTab("Findings", scroll(findings));
    tabs.addTab("Thread States", scroll(states));
    tabs.addTab("Threads", scroll(threads));
    tabs.addTab("Exceptions", scroll(exceptions));
    tabs.addTab("Raw", rawPanel);
    panel.add(tabs, BorderLayout.CENTER);

    open.addActionListener(event -> chooseAndLoad(panel, status, overview, findings, states, threads, exceptions, raw));
    return panel;
  }

  private void chooseAndLoad(
      JComponent parent,
      JLabel status,
      JTextArea overview,
      JTable findings,
      JTable states,
      JTree threads,
      JTable exceptions,
      JTextArea raw) {
    var chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Stacktrace/log files", "txt", "log", "out", "stacktrace"));
    if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
      load(chooser.getSelectedFile().toPath(), status, overview, findings, states, threads, exceptions, raw);
    }
  }

  private void load(
      Path file,
      JLabel status,
      JTextArea overview,
      JTable findings,
      JTable states,
      JTree threads,
      JTable exceptions,
      JTextArea raw) {
    status.setText("Loading " + file);
    new SwingWorker<StacktraceAnalysis, Void>() {
      @Override
      protected StacktraceAnalysis doInBackground() throws Exception {
        return service.analyze(file);
      }

      @Override
      protected void done() {
        try {
          var analysis = get();
          render(analysis, overview, findings, states, threads, exceptions, raw);
          status.setText("Loaded " + file);
        } catch (Exception e) {
          overview.setText("Unable to read stacktrace file:\n" + rootMessage(e));
          clear(findings);
          clear(states);
          clear(exceptions);
          threads.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Unable to read")));
          raw.setText("");
          status.setText("Unable to read stacktrace file");
        }
      }
    }.execute();
  }

  private static void render(
      StacktraceAnalysis analysis,
      JTextArea overview,
      JTable findings,
      JTable states,
      JTree threads,
      JTable exceptions,
      JTextArea raw) {
    overview.setText("""
        File: %s
        Threads: %d
        Exceptions: %d
        Findings: %d
        Raw characters: %d
        Input: %s
        """.formatted(
        analysis.file(),
        analysis.threads().size(),
        analysis.exceptions().size(),
        analysis.findings().size(),
        analysis.rawText().length(),
        analysis.truncated() ? "first 16 MiB shown" : "complete"));
    var findingModel = model(findings);
    findingModel.setRowCount(0);
    for (var finding : analysis.findings()) {
      findingModel.addRow(new Object[] {finding.severity(), finding.message()});
    }
    var stateModel = model(states);
    stateModel.setRowCount(0);
    analysis.stateCounts().forEach((state, count) -> stateModel.addRow(new Object[] {state, count}));
    threads.setModel(new DefaultTreeModel(threadRoot(analysis)));
    var exceptionModel = model(exceptions);
    exceptionModel.setRowCount(0);
    for (var exception : analysis.exceptions()) {
      exceptionModel.addRow(new Object[] {exception.type(), exception.message(), exception.frames().size()});
    }
    raw.setText(analysis.rawText());
    raw.setCaretPosition(0);
  }

  private static DefaultMutableTreeNode threadRoot(StacktraceAnalysis analysis) {
    var root = new DefaultMutableTreeNode("Threads (" + analysis.threads().size() + ")");
    for (var thread : analysis.threads()) {
      var threadNode = new DefaultMutableTreeNode(thread.name() + " [" + thread.state() + "]");
      var frames = new DefaultMutableTreeNode("Frames (" + thread.frames().size() + ")");
      thread.frames().forEach(frame -> frames.add(new DefaultMutableTreeNode(frame)));
      var locks = new DefaultMutableTreeNode("Locks (" + thread.lockLines().size() + ")");
      thread.lockLines().forEach(lock -> locks.add(new DefaultMutableTreeNode(lock)));
      threadNode.add(frames);
      threadNode.add(locks);
      root.add(threadNode);
    }
    return root;
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
    return new JScrollPane(component);
  }

  private static DefaultTableModel model(JTable table) {
    return (DefaultTableModel) table.getModel();
  }

  private static void clear(JTable table) {
    model(table).setRowCount(0);
  }

  private static void find(JTextArea raw, String text) {
    if (text == null || text.isBlank()) {
      return;
    }
    var source = raw.getText().toLowerCase(java.util.Locale.ROOT);
    var target = text.toLowerCase(java.util.Locale.ROOT);
    var start = Math.max(0, raw.getCaretPosition());
    var index = source.indexOf(target, start);
    if (index < 0) {
      index = source.indexOf(target);
    }
    if (index >= 0) {
      raw.requestFocusInWindow();
      raw.select(index, index + text.length());
    }
  }

  private static String rootMessage(Exception e) {
    var cause = e.getCause() == null ? e : e.getCause();
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }
}
