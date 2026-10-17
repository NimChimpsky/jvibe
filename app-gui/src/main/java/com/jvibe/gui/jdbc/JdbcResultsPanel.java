package com.jvibe.gui.jdbc;

import com.jvibe.jdbc.JdbcQueryResult;
import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Shows the most recent query result from the Databases conversation. */
final class JdbcResultsPanel extends JPanel {
  private final DefaultTableModel model = new DefaultTableModel() {
    @Override public boolean isCellEditable(int row, int column) { return false; }
  };
  private final JTable table = new JTable(model);
  private final JLabel status = new JLabel("Query results appear here");

  JdbcResultsPanel() {
    super(new BorderLayout(0, 6));
    setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    var title = new JLabel("Results");
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    add(title, BorderLayout.NORTH);
    table.setAutoCreateRowSorter(true);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setFillsViewportHeight(true);
    add(new JScrollPane(table), BorderLayout.CENTER);
    add(status, BorderLayout.SOUTH);
  }

  void show(JdbcQueryResult result) {
    model.setDataVector(
        result.rows().stream().map(row -> row.toArray(Object[]::new)).toArray(Object[][]::new),
        result.columns().toArray(Object[]::new));
    for (var column = 0; column < table.getColumnCount(); column++) {
      var width = Math.max(90, table.getColumnName(column).length() * 9 + 24);
      table.getColumnModel().getColumn(column).setPreferredWidth(Math.min(width, 280));
    }
    status.setText(result.message() + " in " + result.elapsedMillis() + " ms");
  }
}
