package com.jvibe.gui.swarm;

import com.jvibe.deployment.DeploymentService;
import com.jvibe.gui.deploy.DeployAgentDialog;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

@Singleton
public final class AgentSwarmPanelFactory {
  private final DeploymentService deploymentService;

  public AgentSwarmPanelFactory(@External DeploymentService deploymentService) {
    this.deploymentService = deploymentService;
  }

  public JComponent create(JFrame owner, Supplier<AgentSwarmContext> context, Consumer<String> log) {
    var panel = new JPanel(new BorderLayout());
    panel.setBorder(new EmptyBorder(18, 18, 18, 18));

    var header = new JPanel(new BorderLayout(0, 8));
    header.add(title("Agent Orchestrator"), BorderLayout.NORTH);
    header.add(description("Deploy the CLI as a long-running autonomous agent."), BorderLayout.CENTER);
    panel.add(header, BorderLayout.NORTH);

    var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
    var deploy = new JButton("Deploy Agent");
    deploy.setPreferredSize(new Dimension(180, 34));
    deploy.addActionListener(event -> showDeployDialog(owner, context.get(), log));
    actions.add(deploy);
    panel.add(actions, BorderLayout.CENTER);
    return panel;
  }

  private void showDeployDialog(JFrame owner, AgentSwarmContext context, Consumer<String> log) {
    new DeployAgentDialog(
        owner,
        deploymentService,
        context.workspace(),
        context.provider(),
        context.model(),
        log).show();
  }

  private static JLabel title(String text) {
    var label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(Font.BOLD, 18));
    return label;
  }

  private static JLabel description(String text) {
    var label = new JLabel(text);
    label.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
    return label;
  }
}
