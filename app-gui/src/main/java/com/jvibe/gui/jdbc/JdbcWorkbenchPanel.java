package com.jvibe.gui.jdbc;

import com.jvibe.config.ConfigService;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.jdbc.JdbcCredentialStore;
import com.jvibe.jdbc.JdbcDatabaseService;
import com.jvibe.jdbc.JdbcQueryResult;
import com.jvibe.session.SessionFactory;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

final class JdbcWorkbenchPanel extends JPanel {
  private final JdbcDatabaseService database;
  private final JdbcCredentialStore credentials;
  private final SessionFactory sessions;
  private final JdbcSqlGenerationService sqlGeneration;
  private final ModelSelectionController modelSelection;
  private final ConfigService configService;
  private final Consumer<String> log;
  private final JTextArea request = editor(false);
  private final JTextArea sql = editor(true);
  private final DefaultTableModel resultModel = new DefaultTableModel() {
    @Override public boolean isCellEditable(int row, int column) { return false; }
  };
  private final JTable results = new JTable(resultModel);
  private final JLabel resultStatus = new JLabel("Select a connection to begin");
  private final JComboBox<String> model = new JComboBox<>();
  private JdbcConnectionConfig connection;
  private long revision;

  JdbcWorkbenchPanel(
      JdbcDatabaseService database,
      JdbcCredentialStore credentials,
      SessionFactory sessions,
      JdbcSqlGenerationService sqlGeneration,
      ModelSelectionController modelSelection,
      ConfigService configService,
      Consumer<String> log) {
    super(new BorderLayout());
    this.database = database;
    this.credentials = credentials;
    this.sessions = sessions;
    this.sqlGeneration = sqlGeneration;
    this.modelSelection = modelSelection;
    this.configService = configService;
    this.log = log;
    setMinimumSize(new Dimension(420, 420));

    modelSelection.bind(model);
    var generate = new JButton("Generate SQL");
    generate.addActionListener(event -> generate(generate));
    var requestFooter = new JPanel(new BorderLayout(8, 0));
    var modelRow = new JPanel(new BorderLayout(7, 0));
    modelRow.add(new JLabel("Model"), BorderLayout.WEST);
    modelRow.add(model, BorderLayout.CENTER);
    requestFooter.add(modelRow, BorderLayout.CENTER);
    requestFooter.add(generate, BorderLayout.EAST);
    var requestPanel = section("Query Request", new JScrollPane(request), requestFooter);

    var execute = new JButton("Execute SQL");
    execute.addActionListener(event -> execute(execute));
    var sqlFooter = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
    sqlFooter.add(execute);
    var sqlPanel = section("Generated SQL", new JScrollPane(sql), sqlFooter);

    results.setAutoCreateRowSorter(true);
    results.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    results.setFillsViewportHeight(true);
    resultStatus.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
    var resultPanel = section("Results", new JScrollPane(results), resultStatus);

    var lower = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sqlPanel, resultPanel);
    configureSplit(lower, 0.38);
    var all = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestPanel, lower);
    configureSplit(all, 0.30);
    add(all, BorderLayout.CENTER);
  }

  void setConnection(JdbcConnectionConfig connection) {
    this.connection = connection;
    revision++;
    sql.setText("");
    clearResults();
    resultStatus.setText(connection == null ? "Select a connection to begin" : "Ready: " + connection.name());
  }

  private void generate(JButton button) {
    var selected = requireConnection();
    if (selected == null || request.getText().isBlank()) {
      if (selected != null) {
        JOptionPane.showMessageDialog(this, "Describe the required query or change first", "Generate SQL", JOptionPane.INFORMATION_MESSAGE);
      }
      return;
    }
    var password = JdbcPasswordPrompter.password(this, selected, credentials);
    if (password == null) {
      return;
    }
    button.setEnabled(false);
    var taskRevision = ++revision;
    var description = request.getText();
    var selectedModel = (String) model.getSelectedItem();
    resultStatus.setText("Reading schema and generating SQL...");
    new SwingWorker<String, Void>() {
      @Override
      protected String doInBackground() throws Exception {
        var schema = database.schemaSummary(selected, password);
        var session = sessions.createJdbc(configService.paths().tmp());
        if (selectedModel != null) {
          modelSelection.apply(session, selectedModel);
        }
        return sqlGeneration.generate(session, description, schema);
      }

      @Override
      protected void done() {
        Arrays.fill(password, '\0');
        button.setEnabled(true);
        if (taskRevision != revision) return;
        try {
          sql.setText(get());
          sql.setCaretPosition(0);
          resultStatus.setText("SQL generated. Review it before execution.");
          log.accept("Generated SQL for JDBC connection: " + selected.name());
        } catch (Exception e) {
          showFailure("SQL generation failed", e);
        }
      }
    }.execute();
  }

  private void execute(JButton button) {
    var selected = requireConnection();
    if (selected == null || sql.getText().isBlank()) {
      if (selected != null) {
        JOptionPane.showMessageDialog(this, "Generate or enter SQL first", "Execute SQL", JOptionPane.INFORMATION_MESSAGE);
      }
      return;
    }
    var password = JdbcPasswordPrompter.password(this, selected, credentials);
    if (password == null) {
      return;
    }
    button.setEnabled(false);
    var taskRevision = ++revision;
    var statement = sql.getText();
    resultStatus.setText("Executing SQL...");
    new SwingWorker<JdbcQueryResult, Void>() {
      @Override
      protected JdbcQueryResult doInBackground() throws Exception {
        return database.execute(selected, password, statement);
      }

      @Override
      protected void done() {
        Arrays.fill(password, '\0');
        button.setEnabled(true);
        if (taskRevision != revision) return;
        try {
          render(get());
          log.accept("Executed SQL on JDBC connection: " + selected.name());
        } catch (Exception e) {
          showFailure("SQL execution failed", e);
        }
      }
    }.execute();
  }

  private JdbcConnectionConfig requireConnection() {
    if (connection == null) {
      JOptionPane.showMessageDialog(this, "Select a JDBC connection first", "JDBC", JOptionPane.INFORMATION_MESSAGE);
    }
    return connection;
  }

  private void render(JdbcQueryResult result) {
    resultModel.setDataVector(
        result.rows().stream().map(row -> row.toArray(Object[]::new)).toArray(Object[][]::new),
        result.columns().toArray(Object[]::new));
    resizeColumns();
    resultStatus.setText(result.message() + " in " + result.elapsedMillis() + " ms");
  }

  private void clearResults() {
    resultModel.setDataVector(new Object[0][0], new Object[0]);
  }

  private void resizeColumns() {
    for (var column = 0; column < results.getColumnCount(); column++) {
      var width = Math.max(90, results.getColumnName(column).length() * 9 + 24);
      results.getColumnModel().getColumn(column).setPreferredWidth(Math.min(width, 280));
    }
  }

  private void showFailure(String title, Exception exception) {
    var cause = exception instanceof java.util.concurrent.ExecutionException && exception.getCause() != null
        ? exception.getCause()
        : exception;
    var message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    resultStatus.setText(message);
    log.accept(title + ": " + message);
    JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
  }

  private static JPanel section(String title, java.awt.Component center, java.awt.Component footer) {
    var panel = new JPanel(new BorderLayout(0, 7));
    panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    var label = new JLabel(title);
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    panel.add(label, BorderLayout.NORTH);
    panel.add(center, BorderLayout.CENTER);
    panel.add(footer, BorderLayout.SOUTH);
    return panel;
  }

  private static JTextArea editor(boolean code) {
    var area = new JTextArea();
    area.setLineWrap(!code);
    area.setWrapStyleWord(!code);
    if (code) {
      area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
      area.setTabSize(2);
    }
    area.setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 7));
    return area;
  }

  private static void configureSplit(JSplitPane split, double weight) {
    split.setResizeWeight(weight);
    com.jvibe.gui.layout.SplitPaneSizing.initialProportion(split, weight);
    split.setContinuousLayout(true);
    split.setDividerSize(6);
    split.setBorder(null);
  }
}
