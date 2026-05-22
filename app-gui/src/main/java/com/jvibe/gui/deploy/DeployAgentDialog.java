package com.jvibe.gui.deploy;

import com.jvibe.deployment.DeploymentProfile;
import com.jvibe.deployment.DeploymentService;
import com.jvibe.deployment.DeploymentTarget;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public final class DeployAgentDialog {
  private final JFrame owner;
  private final DeploymentService deploymentService;
  private final Path workspace;
  private final String provider;
  private final String model;
  private final Consumer<String> log;

  public DeployAgentDialog(
      JFrame owner,
      DeploymentService deploymentService,
      Path workspace,
      String provider,
      String model,
      Consumer<String> log) {
    this.owner = owner;
    this.deploymentService = deploymentService;
    this.workspace = workspace;
    this.provider = provider;
    this.model = model;
    this.log = log;
  }

  public void show() {
    log.accept("Opened Deploy Agent");
    var dialog = new JDialog(owner, "Deploy Agent", true);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(java.awt.event.WindowEvent event) {
        log.accept("Closed Deploy Agent");
      }
    });

    var body = new JPanel(new BorderLayout(0, 16));
    body.setBorder(new EmptyBorder(18, 18, 14, 18));

    var fields = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
    var type = new JComboBox<>(new String[] {"local", "ssh"});
    var host = new JTextField();
    var user = new JTextField(System.getProperty("user.name", ""));
    var path = new JTextField("~/.jvibe/agent");
    var workspaceField = new JTextField(workspace.toString());
    fields.add(label("Target"));
    fields.add(type);
    fields.add(label("Host"));
    fields.add(host);
    fields.add(label("User"));
    fields.add(user);
    fields.add(label("Install path"));
    fields.add(path);
    fields.add(label("Workspace"));
    fields.add(workspaceField);
    body.add(fields, BorderLayout.CENTER);

    var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
    var cancel = new JButton("Cancel");
    cancel.addActionListener(event -> dialog.dispose());
    var deploy = new JButton("Deploy");
    deploy.addActionListener(event -> deploy(dialog, type, host, user, path, workspaceField));
    actions.add(cancel);
    actions.add(deploy);
    body.add(actions, BorderLayout.SOUTH);

    dialog.setContentPane(body);
    dialog.setSize(new Dimension(620, 320));
    dialog.setMinimumSize(new Dimension(520, 280));
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private void deploy(
      JDialog dialog,
      JComboBox<String> type,
      JTextField host,
      JTextField user,
      JTextField path,
      JTextField workspaceField) {
    var target = new DeploymentTarget(
        type.getSelectedItem().toString(),
        host.getText(),
        user.getText(),
        path.getText(),
        workspaceField.getText());
    var profile = new DeploymentProfile(
        "gui",
        target,
        provider,
        model,
        "autonomous",
        true,
        "127.0.0.1",
        8737);
    var cliJar = Path.of("app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
    var result = "local".equals(target.type())
        ? deploymentService.deployLocal(profile, cliJar)
        : deploymentService.plan(profile, cliJar);
    log.accept(result.summary());
    JOptionPane.showMessageDialog(
        dialog,
        result.summary() + "\n\n" + String.join("\n", result.commands()),
        "Deploy Agent",
        result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
  }

  private static JLabel label(String text) {
    var label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(Font.PLAIN, 13));
    return label;
  }
}
