package com.jvibe.gui.settings;

import com.jvibe.api.Account;
import com.jvibe.api.JvibeClient;
import java.awt.BorderLayout;
import java.awt.Component;
import java.net.InetAddress;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * Email sign-in. JVibe returns an API key at once; it starts working after the user opens the
 * emailed link and approves the code shown here.
 */
public final class SignInDialog {
  private SignInDialog() {}

  public static void show(Component owner, JvibeClient client, String email, Consumer<Account> signedIn) {
    var field = new JTextField(email, 28);
    var panel = new JPanel(new BorderLayout(0, 8));
    panel.add(new JLabel("<html><b>Sign in to JVibe</b><br>"
        + "Enter your email. We'll send a link; open it and approve the code<br>"
        + "this app shows to activate JVibe on this computer.</html>"), BorderLayout.NORTH);
    panel.add(field, BorderLayout.CENTER);
    field.addAncestorListener(new javax.swing.event.AncestorListener() {
      @Override public void ancestorAdded(javax.swing.event.AncestorEvent event) { field.requestFocusInWindow(); }
      @Override public void ancestorRemoved(javax.swing.event.AncestorEvent event) {}
      @Override public void ancestorMoved(javax.swing.event.AncestorEvent event) {}
    });
    var choice = JOptionPane.showConfirmDialog(owner, panel, "JVibe sign-in",
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    var entered = field.getText().trim();
    if (choice != JOptionPane.OK_OPTION || entered.isBlank()) return;

    new SwingWorker<Account, Void>() {
      @Override protected Account doInBackground() {
        return client.signIn(entered, deviceName());
      }

      @Override protected void done() {
        try {
          var account = get();
          var message = account.approvalCode().isBlank()
              ? "Signed in as " + account.email() + "."
              : "<html>We emailed a sign-in link to <b>" + account.email() + "</b>.<br><br>"
                  + "Open it and approve this code:<br><br>"
                  + "<span style='font-size:20pt'><b>" + account.approvalCode() + "</b></span><br><br>"
                  + "JVibe starts working as soon as you approve.</html>";
          JOptionPane.showMessageDialog(owner, message, "JVibe sign-in", JOptionPane.INFORMATION_MESSAGE);
          signedIn.accept(account);
        } catch (Exception e) {
          var cause = e.getCause() == null ? e : e.getCause();
          JOptionPane.showMessageDialog(owner, cause.getMessage(), "JVibe sign-in failed", JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  static String deviceName() {
    var user = System.getProperty("user.name", "");
    String host;
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      host = System.getenv().getOrDefault("COMPUTERNAME", "desktop");
    }
    return (user.isBlank() ? "" : user + "@") + host;
  }
}
