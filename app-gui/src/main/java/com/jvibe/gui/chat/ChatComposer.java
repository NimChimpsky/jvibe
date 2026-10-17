package com.jvibe.gui.chat;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

/** Fixed-height prompt composer shared by local and remote chat views. */
public final class ChatComposer extends JPanel {
  static final int HEIGHT = 220;
  static final int INPUT_HEIGHT = 118;
  static final int REFERENCE_ROW_HEIGHT = 26;

  private final JTextArea prompt = new JTextArea(4, 20);
  private final JComboBox<String> modelSelector = new JComboBox<>();
  private final JButton send = new JButton("Send");
  private final JPanel footer = new JPanel(new BorderLayout(10, 0));
  private final JPanel referencePanel =
      new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 1));

  public ChatComposer(
      Runnable sendPrompt,
      Runnable modelSelected) {
    super(new BorderLayout(10, 0));
    configurePrompt(sendPrompt);
    configureModelSelector(modelSelected);
    send.addActionListener(event -> sendPrompt.run());

    referencePanel.setOpaque(false);
    referencePanel.setPreferredSize(new Dimension(0, REFERENCE_ROW_HEIGHT));
    referencePanel.setMinimumSize(new Dimension(0, REFERENCE_ROW_HEIGHT));
    referencePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, REFERENCE_ROW_HEIGHT));

    var promptStack = new JPanel();
    promptStack.setLayout(new javax.swing.BoxLayout(promptStack, javax.swing.BoxLayout.Y_AXIS));
    promptStack.add(referencePanel);
    promptStack.add(javax.swing.Box.createVerticalStrut(4));
    promptStack.add(fixedPromptScroll(prompt));
    promptStack.add(javax.swing.Box.createVerticalGlue());

    setBorder(new EmptyBorder(12, 12, 12, 12));
    setPreferredSize(new Dimension(0, HEIGHT));
    setMinimumSize(new Dimension(0, HEIGHT));
    setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
    add(promptStack, BorderLayout.CENTER);
    add(controls(), BorderLayout.SOUTH);
  }

  public JTextArea prompt() {
    return prompt;
  }

  public void onAttach(Runnable action) {
    var attach = new JButton(javax.swing.UIManager.getIcon("FileView.fileIcon"));
    attach.setToolTipText("Attach files or folders (read-only)");
    attach.addActionListener(event -> action.run());
    var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
    actions.add(attach);
    footer.add(actions, BorderLayout.CENTER);
  }

  public JComboBox<String> modelSelector() {
    return modelSelector;
  }

  public void setSending(boolean sending) {
    send.setText(sending ? "Stop" : "Send");
    send.setToolTipText(sending ? "Stop response" : "Send prompt");
    modelSelector.setEnabled(!sending);
  }

  public void showContext(List<? extends Component> chips) {
    referencePanel.removeAll();
    chips.forEach(referencePanel::add);
    referencePanel.revalidate();
    referencePanel.repaint();
  }

  void showReferences(List<Path> paths, Consumer<Path> remove) {
    referencePanel.removeAll();
    paths.forEach(path -> referencePanel.add(fileReferenceChip(path, () -> remove.accept(path))));
    referencePanel.revalidate();
    referencePanel.repaint();
  }

  private void configurePrompt(Runnable sendPrompt) {
    prompt.setLineWrap(true);
    prompt.setWrapStyleWord(true);
    prompt.setMargin(new Insets(10, 10, 10, 10));
    prompt.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendPrompt");
    prompt.getInputMap().put(
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "insert-break");
    prompt.getActionMap().put("sendPrompt", new javax.swing.AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent event) {
        sendPrompt.run();
      }
    });
  }

  private void configureModelSelector(Runnable modelSelected) {
    modelSelector.setPreferredSize(new Dimension(180, modelSelector.getPreferredSize().height));
    modelSelector.addActionListener(event -> modelSelected.run());
  }

  private JComponent controls() {
    var modelPanel = new JPanel();
    modelPanel.add(new JLabel("Model"));
    modelPanel.add(modelSelector);
    footer.add(modelPanel, BorderLayout.WEST);
    footer.add(send, BorderLayout.EAST);

    return footer;
  }

  public static JButton fileReferenceChip(Path path, Runnable remove) {
    var button = new JButton(fileName(path) + " x");
    button.setToolTipText(path.toString());
    button.setFocusable(false);
    button.setMargin(new Insets(2, 8, 2, 8));
    button.addActionListener(event -> remove.run());
    return button;
  }

  private static JScrollPane fixedPromptScroll(Component component) {
    var scroll = new JScrollPane(component);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    var size = new Dimension(0, INPUT_HEIGHT);
    scroll.setMinimumSize(size);
    scroll.setPreferredSize(size);
    scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, INPUT_HEIGHT));
    return scroll;
  }

  private static String fileName(Path path) {
    return path.getFileName() == null ? path.toString() : path.getFileName().toString();
  }

}
