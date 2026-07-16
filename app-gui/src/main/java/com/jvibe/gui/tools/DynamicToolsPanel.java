package com.jvibe.gui.tools;

import com.jvibe.tool.ToolRegistry;
import com.jvibe.tool.ToolResult;
import com.jvibe.tool.dynamic.DynamicToolManagementTools;
import com.jvibe.tool.dynamic.DynamicToolManagementTools.ToolInfo;
import com.jvibe.workspace.Workspace;
import java.awt.BorderLayout;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

/** User-facing approval and lifecycle controls for dynamic Java tools. */
public final class DynamicToolsPanel extends JPanel {
  private final DynamicToolManagementTools tools;
  private final Supplier<Workspace> workspace;
  private final Supplier<ToolRegistry> registry;
  private final BiConsumer<String, String> log;
  private final ToolTableModel model = new ToolTableModel();
  private final JTable table = new JTable(model);
  private final JButton approve = button("Approve");
  private final JButton reload = button("Reload");
  private final JButton disable = button("Disable");
  private final JButton delete = button("Delete");

  public DynamicToolsPanel(
      DynamicToolManagementTools tools,
      Supplier<Workspace> workspace,
      Supplier<ToolRegistry> registry,
      BiConsumer<String, String> log) {
    super(new BorderLayout(0, 8));
    this.tools = tools;
    this.workspace = workspace;
    this.registry = registry;
    this.log = log;

    table.setFillsViewportHeight(true);
    table.setAutoCreateRowSorter(true);
    table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(event -> updateActions());

    var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
    var refresh = button("Refresh");
    refresh.addActionListener(event -> refresh());
    approve.addActionListener(event -> run("Approve", selected(), tools::approve));
    reload.addActionListener(event -> run("Reload", selected(), tools::reload));
    disable.addActionListener(event -> run("Disable", selected(), tools::disable));
    delete.addActionListener(event -> deleteSelected());
    actions.add(refresh);
    actions.add(approve);
    actions.add(reload);
    actions.add(disable);
    actions.add(delete);

    add(new JScrollPane(table), BorderLayout.CENTER);
    add(actions, BorderLayout.SOUTH);
    refresh();
  }

  public void refresh() {
    var current = workspace.get();
    model.setRows(current == null ? List.of() : tools.discovered(current));
    updateActions();
  }

  private void deleteSelected() {
    var selected = selected();
    if (selected == null) {
      return;
    }
    var answer = JOptionPane.showConfirmDialog(
        this,
        "Delete dynamic tool '" + selected.name() + "' and its source files?",
        "Delete Dynamic Tool",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE);
    if (answer == JOptionPane.OK_OPTION) {
      run("Delete", selected, tools::delete);
    }
  }

  private void run(String action, ToolInfo selected, ToolAction operation) {
    if (selected == null) {
      return;
    }
    setActionsEnabled(false);
    new SwingWorker<ToolResult, Void>() {
      @Override
      protected ToolResult doInBackground() {
        return operation.run(
            UUID.randomUUID().toString(), workspace.get(), registry.get(), selected.id(), selected.scope());
      }

      @Override
      protected void done() {
        try {
          var result = get();
          log.accept(result.success() ? "INFO" : "ERROR", action + ": " + result.summary());
        } catch (Exception e) {
          log.accept("ERROR", action + " failed: " + rootMessage(e));
        }
        refresh();
      }
    }.execute();
  }

  private ToolInfo selected() {
    var viewRow = table.getSelectedRow();
    return viewRow < 0 ? null : model.row(table.convertRowIndexToModel(viewRow));
  }

  private void updateActions() {
    var selected = selected();
    approve.setEnabled(selected != null && !selected.approved());
    reload.setEnabled(selected != null && selected.approved() && selected.enabled());
    disable.setEnabled(selected != null && selected.enabled());
    delete.setEnabled(selected != null);
  }

  private void setActionsEnabled(boolean enabled) {
    approve.setEnabled(enabled);
    reload.setEnabled(enabled);
    disable.setEnabled(enabled);
    delete.setEnabled(enabled);
  }

  private static JButton button(String text) {
    return new JButton(text);
  }

  private static String rootMessage(Exception exception) {
    var cause = exception;
    while (cause.getCause() instanceof Exception nested) {
      cause = nested;
    }
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  @FunctionalInterface
  private interface ToolAction {
    ToolResult run(String callId, Workspace workspace, ToolRegistry registry, String id, String scope);
  }

  private static final class ToolTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Tool", "Scope", "Version", "Status"};
    private List<ToolInfo> rows = List.of();

    void setRows(List<ToolInfo> rows) {
      this.rows = List.copyOf(rows);
      fireTableDataChanged();
    }

    ToolInfo row(int index) {
      return rows.get(index);
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      var tool = rows.get(rowIndex);
      return switch (columnIndex) {
        case 0 -> tool.name();
        case 1 -> tool.scope();
        case 2 -> tool.version();
        case 3 -> status(tool);
        default -> "";
      };
    }

    private static String status(ToolInfo tool) {
      if (!tool.approved()) return "Approval required";
      if (!tool.enabled()) return "Disabled";
      return tool.loaded() ? "Loaded" : "Ready to load";
    }
  }
}
