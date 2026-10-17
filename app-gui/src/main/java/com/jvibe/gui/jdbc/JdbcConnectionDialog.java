package com.jvibe.gui.jdbc;

import com.jvibe.jdbc.JdbcConnectionConfig;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

final class JdbcConnectionDialog {
  private JdbcConnectionDialog() {}

  static Result show(Component owner, JdbcConnectionConfig existing) {
    var name = new JTextField(existing == null ? "" : existing.name(), 32);
    var url = new JTextField(existing == null ? "" : existing.jdbcUrl(), 32);
    var user = new JTextField(existing == null ? "" : existing.username(), 32);
    var driverClass = new JTextField(existing == null ? "" : existing.driverClass(), 32);
    var driverJar = new JTextField(existing == null ? "" : existing.driverJar(), 26);
    var password = new JPasswordField(32);
    password.setToolTipText(existing == null ? "Password is kept in memory only" : "Leave blank to keep the current in-memory password");

    var browse = new JButton("Browse");
    browse.addActionListener(event -> {
      var chooser = new JFileChooser();
      chooser.setDialogTitle("Select JDBC driver jar");
      chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      if (!driverJar.getText().isBlank()) {
        chooser.setSelectedFile(new File(driverJar.getText()));
      }
      if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
        driverJar.setText(chooser.getSelectedFile().getAbsolutePath());
      }
    });

    var jarRow = new JPanel(new java.awt.BorderLayout(6, 0));
    jarRow.add(driverJar, java.awt.BorderLayout.CENTER);
    jarRow.add(browse, java.awt.BorderLayout.EAST);

    var panel = new JPanel(new GridBagLayout());
    var constraints = new GridBagConstraints();
    constraints.insets = new Insets(5, 5, 5, 5);
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    addRow(panel, constraints, 0, "Name", name);
    addRow(panel, constraints, 1, "JDBC URL", url);
    addRow(panel, constraints, 2, "Username", user);
    addRow(panel, constraints, 3, "Password", password);
    addRow(panel, constraints, 4, "Driver class", driverClass);
    addRow(panel, constraints, 5, "Driver jar", jarRow);

    while (true) {
      var option = JOptionPane.showConfirmDialog(
          owner,
          panel,
          existing == null ? "Add JDBC Connection" : "Edit JDBC Connection",
          JOptionPane.OK_CANCEL_OPTION,
          JOptionPane.PLAIN_MESSAGE);
      if (option != JOptionPane.OK_OPTION) {
        java.util.Arrays.fill(password.getPassword(), '\0');
        return null;
      }
      if (!url.getText().trim().startsWith("jdbc:")) {
        JOptionPane.showMessageDialog(owner, "JDBC URL must start with jdbc:", "Invalid connection", JOptionPane.ERROR_MESSAGE);
        continue;
      }
      var config = existing == null
          ? JdbcConnectionConfig.create(name.getText(), url.getText(), user.getText(), driverClass.getText(), driverJar.getText())
          : new JdbcConnectionConfig(existing.id(), name.getText(), url.getText(), user.getText(), driverClass.getText(), driverJar.getText());
      var secret = password.getPassword();
      return new Result(config, secret, existing == null || secret.length > 0);
    }
  }

  private static void addRow(JPanel panel, GridBagConstraints constraints, int row, String label, Component field) {
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.weightx = 0;
    panel.add(new JLabel(label), constraints);
    constraints.gridx = 1;
    constraints.weightx = 1;
    panel.add(field, constraints);
  }

  record Result(JdbcConnectionConfig config, char[] password, boolean replacePassword) implements AutoCloseable {
    @Override
    public void close() {
      java.util.Arrays.fill(password, '\0');
    }
  }
}
