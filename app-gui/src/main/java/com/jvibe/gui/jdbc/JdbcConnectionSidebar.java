package com.jvibe.gui.jdbc;

import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.jdbc.JdbcConnectionRepository;
import com.jvibe.jdbc.JdbcCredentialStore;
import com.jvibe.jdbc.JdbcDatabaseService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

final class JdbcConnectionSidebar extends JPanel {
  private final JdbcConnectionRepository repository;
  private final JdbcCredentialStore credentials;
  private final JdbcDatabaseService database;
  private final Consumer<JdbcConnectionConfig> selection;
  private final Consumer<String> log;
  private final DefaultListModel<JdbcConnectionConfig> model = new DefaultListModel<>();
  private final JList<JdbcConnectionConfig> list = new JList<>(model);

  JdbcConnectionSidebar(
      JdbcConnectionRepository repository,
      JdbcCredentialStore credentials,
      JdbcDatabaseService database,
      Consumer<JdbcConnectionConfig> selection,
      Consumer<String> log) {
    super(new BorderLayout(0, 8));
    this.repository = repository;
    this.credentials = credentials;
    this.database = database;
    this.selection = selection;
    this.log = log;
    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    setMinimumSize(new Dimension(210, 300));
    setPreferredSize(new Dimension(260, 600));

    var title = new javax.swing.JLabel("JDBC Connections");
    title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
    add(title, BorderLayout.NORTH);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setFixedCellHeight(42);
    list.setCellRenderer(new ConnectionRenderer());
    list.addListSelectionListener(event -> {
      if (!event.getValueIsAdjusting()) {
        selection.accept(list.getSelectedValue());
      }
    });
    add(new JScrollPane(list), BorderLayout.CENTER);

    var actions = new JPanel(new java.awt.GridLayout(2, 1, 0, 6));
    var add = new JButton("Add");
    var edit = new JButton("Edit");
    var delete = new JButton("Delete");
    var test = new JButton("Test");
    var refresh = new JButton("Refresh");
    for (var action : java.util.List.of(add, edit, delete, test, refresh)) {
      action.setMargin(new java.awt.Insets(3, 6, 3, 6));
    }
    add.addActionListener(event -> editConnection(null));
    edit.addActionListener(event -> editConnection(list.getSelectedValue()));
    delete.addActionListener(event -> deleteSelected());
    test.addActionListener(event -> testSelected(test));
    refresh.addActionListener(event -> reload(list.getSelectedValue() == null ? null : list.getSelectedValue().id()));
    var editActions = new JPanel(new java.awt.GridLayout(1, 3, 6, 0));
    editActions.add(add);
    editActions.add(edit);
    editActions.add(delete);
    var connectionActions = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
    connectionActions.add(test);
    connectionActions.add(refresh);
    actions.add(editActions);
    actions.add(connectionActions);
    add(actions, BorderLayout.SOUTH);
    reload(null);
  }

  private void editConnection(JdbcConnectionConfig existing) {
    var result = JdbcConnectionDialog.show(this, existing);
    if (result == null) {
      return;
    }
    try (result) {
      repository.save(result.config());
      if (result.replacePassword()) {
        credentials.put(result.config().id(), result.password());
      }
      reload(result.config().id());
      log.accept("Saved JDBC connection: " + result.config().name());
    }
  }

  private void deleteSelected() {
    var selected = list.getSelectedValue();
    if (selected == null) {
      return;
    }
    var option = JOptionPane.showConfirmDialog(
        this,
        "Delete JDBC connection '" + selected.name() + "'?",
        "Delete Connection",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE);
    if (option == JOptionPane.OK_OPTION) {
      repository.delete(selected.id());
      reload(null);
      log.accept("Deleted JDBC connection: " + selected.name());
    }
  }

  private void testSelected(JButton button) {
    var selected = list.getSelectedValue();
    if (selected == null) {
      JOptionPane.showMessageDialog(this, "Select a JDBC connection first", "Test Connection", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    var password = JdbcPasswordPrompter.password(this, selected, credentials);
    if (password == null) {
      return;
    }
    button.setEnabled(false);
    new SwingWorker<String, Void>() {
      @Override
      protected String doInBackground() throws Exception {
        return database.test(selected, password);
      }

      @Override
      protected void done() {
        Arrays.fill(password, '\0');
        button.setEnabled(true);
        try {
          var message = get();
          log.accept("JDBC connection succeeded: " + selected.name());
          JOptionPane.showMessageDialog(JdbcConnectionSidebar.this, message, "Connection Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
          credentials.remove(selected.id());
          var message = errorMessage(e);
          log.accept("JDBC connection failed: " + message);
          JOptionPane.showMessageDialog(JdbcConnectionSidebar.this, message, "Connection Failed", JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private void reload(String selectedId) {
    model.clear();
    repository.connections().forEach(model::addElement);
    if (model.isEmpty()) {
      selection.accept(null);
      return;
    }
    var index = 0;
    if (selectedId != null) {
      for (var i = 0; i < model.size(); i++) {
        if (model.get(i).id().equals(selectedId)) {
          index = i;
          break;
        }
      }
    }
    list.setSelectedIndex(index);
  }

  private static String errorMessage(Exception exception) {
    var cause = exception instanceof java.util.concurrent.ExecutionException && exception.getCause() != null
        ? exception.getCause()
        : exception;
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  private static final class ConnectionRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean selected, boolean focused) {
      var label = (javax.swing.JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
      if (value instanceof JdbcConnectionConfig connection) {
        label.setText(connection.name());
        label.setToolTipText(connection.jdbcUrl());
        label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
      }
      return label;
    }
  }
}
