package com.jvibe.gui.settings;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibeConfig;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

public final class GeneralSettingsDialog {
  private final JFrame owner;
  private final ConfigService configService;
  private final Consumer<String> log;

  public GeneralSettingsDialog(JFrame owner, ConfigService configService, Consumer<String> log) {
    this.owner = owner;
    this.configService = configService;
    this.log = log;
  }

  public void show() {
    try {
      var dialog = new JDialog(owner, "General", true);
      dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
      dialog.setContentPane(content(dialog, configService.config(), configService.chatPrompt(), configService.workspacePrompt()));
      dialog.setMinimumSize(new Dimension(640, 480));
      dialog.setSize(760, 560);
      dialog.setLocationRelativeTo(owner);
      bindEscape(dialog);
      dialog.setVisible(true);
    } catch (IOException e) {
      JOptionPane.showMessageDialog(owner, e.getMessage(), "Unable to load settings", JOptionPane.ERROR_MESSAGE);
    }
  }

  private JComponent content(JDialog dialog, JvibeConfig config, String chatPrompt, String workspacePrompt) {
    var maxToolIterations = new JSpinner(new SpinnerNumberModel(config.maxToolIterations(), 1, 100, 1));
    var chatPromptArea = promptArea(chatPrompt);
    var workspacePromptArea = promptArea(workspacePrompt);

    var body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setBorder(new EmptyBorder(16, 16, 16, 16));
    body.add(row("Max tool iterations", maxToolIterations));
    body.add(title("Built-in Chat system prompt"));
    body.add(scroll(chatPromptArea));
    body.add(title("Built-in Workspace system prompt"));
    body.add(scroll(workspacePromptArea));

    var cancel = new JButton("Cancel");
    cancel.addActionListener(event -> dialog.dispose());

    var save = new JButton("Save");
    save.addActionListener(event -> {
      try {
        configService.saveConfig(new JvibeConfig(
            config.defaultProvider(),
            config.defaultModel(),
            ((Number) maxToolIterations.getValue()).intValue(),
            config.theme(),
            config.autonomyMode(),
            config.gatewayEnabled(),
            config.gatewayHost(),
            config.gatewayPort(),
            config.deploymentProfiles()));
        log.accept("Saved general settings");
        dialog.dispose();
      } catch (IOException e) {
        JOptionPane.showMessageDialog(dialog, e.getMessage(), "Unable to save settings", JOptionPane.ERROR_MESSAGE);
      }
    });
    dialog.getRootPane().setDefaultButton(save);

    var buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
    buttons.add(cancel);
    buttons.add(save);

    var panel = new JPanel(new BorderLayout());
    panel.add(new JScrollPane(body), BorderLayout.CENTER);
    panel.add(buttons, BorderLayout.SOUTH);
    return panel;
  }

  private static JTextArea promptArea(String text) {
    var prompt = new JTextArea(text, 7, 48);
    prompt.setLineWrap(true);
    prompt.setWrapStyleWord(true);
    prompt.setEditable(false);
    prompt.setEnabled(false);
    return prompt;
  }

  private static JComponent row(String label, JComponent value) {
    var row = new JPanel(new BorderLayout(12, 0));
    row.setBorder(new EmptyBorder(8, 0, 8, 0));
    var name = new JLabel(label);
    name.setFont(name.getFont().deriveFont(Font.BOLD, 12));
    name.setPreferredSize(new Dimension(150, 26));
    row.add(name, BorderLayout.WEST);
    row.add(value, BorderLayout.CENTER);
    return row;
  }

  private static JLabel title(String text) {
    var label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(Font.BOLD, 14));
    label.setBorder(new EmptyBorder(12, 0, 8, 0));
    return label;
  }

  private static JScrollPane scroll(JTextArea textArea) {
    var scroll = new JScrollPane(textArea);
    scroll.setBorder(BorderFactory.createEtchedBorder());
    return scroll;
  }

  private void bindEscape(JDialog dialog) {
    var root = dialog.getRootPane();
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeGeneral");
    root.getActionMap().put("closeGeneral", new AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent event) {
        dialog.dispose();
      }
    });
  }
}
