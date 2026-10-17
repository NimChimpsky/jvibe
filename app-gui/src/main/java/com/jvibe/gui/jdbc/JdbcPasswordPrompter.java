package com.jvibe.gui.jdbc;

import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.jdbc.JdbcCredentialStore;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

final class JdbcPasswordPrompter {
  private JdbcPasswordPrompter() {}

  static char[] password(Component owner, JdbcConnectionConfig connection, JdbcCredentialStore credentials) {
    if (credentials.contains(connection.id())) {
      return credentials.password(connection.id());
    }
    var field = new JPasswordField(28);
    var panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
    panel.add(new JLabel("Password for " + connection.name()), java.awt.BorderLayout.NORTH);
    panel.add(field, java.awt.BorderLayout.CENTER);
    var option = JOptionPane.showConfirmDialog(
        owner, panel, "JDBC Credentials", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (option != JOptionPane.OK_OPTION) {
      return null;
    }
    var value = field.getPassword();
    credentials.put(connection.id(), value);
    return value;
  }
}
