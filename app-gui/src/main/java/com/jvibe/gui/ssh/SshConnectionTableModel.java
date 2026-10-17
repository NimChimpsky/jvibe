package com.jvibe.gui.ssh;

import com.jvibe.ssh.SshConnection;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

public final class SshConnectionTableModel extends AbstractTableModel {
  private static final String[] COLUMNS = {"Name", "Host", "User", "Port", "Key", "Last test"};
  private final ArrayList<SshConnection> connections = new ArrayList<>();

  public void setConnections(List<SshConnection> values) {
    connections.clear();
    connections.addAll(values);
    fireTableDataChanged();
  }

  public SshConnection connectionAt(int row) {
    return connections.get(row);
  }

  @Override
  public int getRowCount() {
    return connections.size();
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
    var connection = connections.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> connection.name();
      case 1 -> connection.host();
      case 2 -> connection.user();
      case 3 -> connection.port();
      case 4 -> connection.keyPath();
      case 5 -> connection.lastTestResult();
      default -> "";
    };
  }
}
