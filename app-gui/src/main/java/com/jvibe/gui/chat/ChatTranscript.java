package com.jvibe.gui.chat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class ChatTranscript {
  private static final int MESSAGE_MAX_WIDTH = 640;
  private static final double MESSAGE_WIDTH_RATIO = 0.68;

  private final JPanel messages = new JPanel();
  private JTextArea activeAssistantMessage;

  public ChatTranscript() {
    messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
    messages.setBorder(new EmptyBorder(12, 12, 12, 12));
  }

  public JComponent component() {
    return messages;
  }

  public void clear() {
    messages.removeAll();
    activeAssistantMessage = null;
    refresh(messages);
  }

  public void appendUser(String text, List<Path> references) {
    messages.add(messageBubble(text, true, references));
    refresh(messages);
  }

  public void appendAssistant(String text) {
    messages.add(messageBubble(text, false, List.of()));
    refresh(messages);
  }

  public boolean appendAssistantDelta(String text) {
    if (text.isBlank()) {
      return false;
    }
    if (activeAssistantMessage == null) {
      var bubble = messageBubble("", false, List.of());
      activeAssistantMessage = (JTextArea) bubble.getClientProperty("body");
      messages.add(bubble);
    }
    activeAssistantMessage.append(text);
    fitMessageBody(activeAssistantMessage);
    refresh(messages);
    return true;
  }

  public void finishAssistant() {
    activeAssistantMessage = null;
  }

  private JComponent messageBubble(String text, boolean user, List<Path> references) {
    var row = new JPanel(new BorderLayout());
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(3, 0, 3, 0));

    var wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(true);
    wrapper.setBackground(messageBackground(user));
    wrapper.setBorder(new EmptyBorder(6, 8, 6, 8));

    var body = new JTextArea(text);
    body.setEditable(false);
    body.setLineWrap(true);
    body.setWrapStyleWord(true);
    body.setOpaque(false);
    body.setBorder(null);
    body.putClientProperty("messageWrapper", wrapper);
    if (!references.isEmpty()) {
      wrapper.add(historyReferencePanel(references), BorderLayout.NORTH);
    }
    wrapper.add(body, BorderLayout.CENTER);
    row.add(wrapper, user ? BorderLayout.EAST : BorderLayout.WEST);
    row.putClientProperty("body", body);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    fitMessageBody(body);
    return row;
  }

  private void fitMessageBody(JTextArea body) {
    var metrics = body.getFontMetrics(body.getFont());
    var contentWidth = 32;
    for (var line : body.getText().split("\\R", -1)) {
      contentWidth = Math.max(contentWidth, metrics.stringWidth(line) + 2);
    }
    var transcriptWidth = messages.getWidth();
    var maxWidth = transcriptWidth > 0
        ? Math.min(MESSAGE_MAX_WIDTH, Math.max(280, (int) (transcriptWidth * MESSAGE_WIDTH_RATIO)))
        : MESSAGE_MAX_WIDTH;
    var width = Math.min(maxWidth, contentWidth);
    body.setSize(new Dimension(width, Short.MAX_VALUE));
    var preferred = body.getPreferredSize();
    var textHeight = Math.max(preferred.height, estimatedTextHeight(body, width));
    body.setPreferredSize(new Dimension(width, textHeight));
    if (body.getClientProperty("messageWrapper") instanceof JComponent wrapper) {
      var wrapperHeight = textHeight + 12 + wrapperExtraHeight(wrapper);
      wrapper.setPreferredSize(new Dimension(width + 16, wrapperHeight));
      wrapper.setMaximumSize(new Dimension(width + 16, wrapperHeight));
      if (wrapper.getParent() instanceof JComponent row) {
        var insets = row.getInsets();
        var rowHeight = wrapperHeight + insets.top + insets.bottom;
        row.setPreferredSize(new Dimension(0, rowHeight));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
        row.revalidate();
      }
    }
  }

  private int wrapperExtraHeight(JComponent wrapper) {
    var extra = 0;
    for (var component : wrapper.getComponents()) {
      if (!(component instanceof JTextArea)) {
        extra += component.getPreferredSize().height + 4;
      }
    }
    return extra;
  }

  private int estimatedTextHeight(JTextArea body, int width) {
    var metrics = body.getFontMetrics(body.getFont());
    var usableWidth = Math.max(1, width - 4);
    var lines = 0;
    for (var line : body.getText().split("\\R", -1)) {
      var lineWidth = Math.max(1, metrics.stringWidth(line));
      lines += Math.max(1, (int) Math.ceil(lineWidth / (double) usableWidth));
    }
    return metrics.getHeight() * lines + 6;
  }

  private JComponent historyReferencePanel(List<Path> references) {
    var panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
    panel.setOpaque(false);
    for (var path : references) {
      var label = new JLabel(filename(path));
      label.setToolTipText(path.toString());
      label.setOpaque(true);
      label.setBackground(messageBackground(true));
      label.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(blend(messageBackground(true), UIManager.getColor("Label.foreground"), 0.18)),
          new EmptyBorder(2, 8, 2, 8)));
      panel.add(label);
    }
    return panel;
  }

  private Color messageBackground(boolean user) {
    var base = UIManager.getColor("Panel.background");
    var tint = UIManager.getColor(user ? "TextArea.selectionBackground" : "TextArea.background");
    if (base == null) {
      base = Color.GRAY;
    }
    if (tint == null) {
      tint = base;
    }
    return blend(base, tint, user ? 0.18 : 0.08);
  }

  private static Color blend(Color base, Color tint, double amount) {
    var inverse = 1.0 - amount;
    return new Color(
        (int) Math.round(base.getRed() * inverse + tint.getRed() * amount),
        (int) Math.round(base.getGreen() * inverse + tint.getGreen() * amount),
        (int) Math.round(base.getBlue() * inverse + tint.getBlue() * amount));
  }

  private String filename(Path path) {
    var name = path.getFileName();
    return name == null ? path.toString() : name.toString();
  }

  private void refresh(JComponent component) {
    component.revalidate();
    component.repaint();
  }
}
