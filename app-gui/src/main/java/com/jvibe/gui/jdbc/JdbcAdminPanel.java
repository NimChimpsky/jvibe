package com.jvibe.gui.jdbc;

import com.jvibe.jdbc.JdbcAdminStat;
import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.jdbc.JdbcCredentialStore;
import com.jvibe.jdbc.JdbcDatabaseService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

final class JdbcAdminPanel extends JPanel {
  private final JdbcDatabaseService database;
  private final JdbcCredentialStore credentials;
  private final Consumer<String> log;
  private final DefaultTableModel model = new DefaultTableModel(new Object[] {"Metric", "Value"}, 0) {
    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }
  };
  private final JTable table = new JTable(model);
  private final JLabel status = new JLabel("Select a connection");
  private final JButton refresh = new JButton("Refresh Stats");
  private JdbcConnectionConfig connection;
  private long revision;

  JdbcAdminPanel(JdbcDatabaseService database, JdbcCredentialStore credentials, Consumer<String> log) {
    super(new BorderLayout());
    this.database = database;
    this.credentials = credentials;
    this.log = log;
    setMinimumSize(new Dimension(250, 420));
    refresh.addActionListener(event -> refresh());

    var top = new JPanel(new BorderLayout(0, 7));
    top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    var heading = new JPanel(new BorderLayout());
    var title = new JLabel("Connection Details");
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    heading.add(title, BorderLayout.WEST);
    heading.add(refresh, BorderLayout.EAST);
    top.add(heading, BorderLayout.NORTH);
    table.setFillsViewportHeight(true);
    table.setRowHeight(24);
    table.getColumnModel().getColumn(0).setPreferredWidth(120);
    table.getColumnModel().getColumn(1).setPreferredWidth(220);
    top.add(new JScrollPane(table), BorderLayout.CENTER);
    top.add(status, BorderLayout.SOUTH);

    var split = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, top, new JPanel());
    split.setResizeWeight(0.55);
    com.jvibe.gui.layout.SplitPaneSizing.initialProportion(split, 0.55);
    split.setDividerSize(6);
    split.setBorder(null);
    add(split, BorderLayout.CENTER);
  }

  void setConnection(JdbcConnectionConfig connection) {
    this.connection = connection;
    revision++;
    model.setRowCount(0);
    status.setText(connection == null ? "Select a connection" : "Ready: " + connection.name());
    if (connection != null && credentials.contains(connection.id())) {
      refresh();
    }
  }

  private void refresh() {
    var selected = connection;
    if (selected == null) {
      return;
    }
    var password = JdbcPasswordPrompter.password(this, selected, credentials);
    if (password == null) {
      return;
    }
    refresh.setEnabled(false);
    var taskRevision = ++revision;
    status.setText("Loading database statistics...");
    new SwingWorker<List<JdbcAdminStat>, Void>() {
      @Override
      protected List<JdbcAdminStat> doInBackground() throws Exception {
        return database.adminStats(selected, password);
      }

      @Override
      protected void done() {
        Arrays.fill(password, '\0');
        refresh.setEnabled(true);
        if (taskRevision != revision) return;
        try {
          model.setRowCount(0);
          var stats = get();
          stats.forEach(stat -> model.addRow(new Object[] {stat.name(), stat.value()}));
          status.setText("Updated");
        } catch (Exception e) {
          credentials.remove(selected.id());
          var cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
          var message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
          status.setText(message);
          log.accept("JDBC stats failed: " + message);
        }
      }
    }.execute();
  }
}
