package com.jvibe.gui.ssh;

import com.jvibe.ssh.SshConnection;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

final class SshConnectionDialog {
  private SshConnectionDialog() {}

  static SshConnection show(Component owner, SshConnection existing) {
    var name = new JTextField(existing == null ? "" : existing.name(), 24);
    var host = new JTextField(existing == null ? "" : existing.host(), 24);
    var user = new JTextField(existing == null ? System.getProperty("user.name", "") : existing.user(), 24);
    var port = new JSpinner(new SpinnerNumberModel(existing == null ? 22 : existing.port(), 1, 65_535, 1));
    var key = new JTextField(existing == null ? "" : existing.keyPath(), 24);

    var form = new JPanel(new GridBagLayout());
    add(form, 0, "Name", name);
    add(form, 1, "Host", host);
    add(form, 2, "User", user);
    add(form, 3, "Port", port);
    add(form, 4, "Private key", key);

    while (true) {
      var result = JOptionPane.showConfirmDialog(
          owner,
          form,
          existing == null ? "Add SSH Connection" : "Edit SSH Connection",
          JOptionPane.OK_CANCEL_OPTION,
          JOptionPane.PLAIN_MESSAGE);
      if (result != JOptionPane.OK_OPTION) return null;
      if (!host.getText().isBlank() && !name.getText().isBlank()) break;
      JOptionPane.showMessageDialog(form, "Name and host are required.", "SSH Connection", JOptionPane.WARNING_MESSAGE);
    }

    return new SshConnection(
        existing == null ? "" : existing.id(),
        name.getText().trim(),
        host.getText().trim(),
        user.getText().trim(),
        ((Number) port.getValue()).intValue(),
        key.getText().trim(),
        existing == null ? "" : existing.lastTestResult());
  }

  private static void add(JPanel panel, int row, String label, Component field) {
    var constraints = new GridBagConstraints();
    constraints.gridy = row;
    constraints.insets = new Insets(4, 4, 4, 8);
    constraints.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(label), constraints);
    constraints.gridx = 1;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(field, constraints);
  }
}
