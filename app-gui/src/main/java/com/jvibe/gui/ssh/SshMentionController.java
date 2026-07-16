package com.jvibe.gui.ssh;

import com.jvibe.ssh.SshConnection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class SshMentionController {
  private final JTextArea input;
  private final Supplier<List<SshConnection>> connections;
  private final Consumer<SshConnection> selected;
  private final JPopupMenu popup = new JPopupMenu();

  public SshMentionController(JTextArea input, Supplier<List<SshConnection>> connections, Consumer<SshConnection> selected) {
    this.input = input;
    this.connections = connections;
    this.selected = selected;
    input.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent event) {
        update();
      }

      @Override
      public void removeUpdate(DocumentEvent event) {
        update();
      }

      @Override
      public void changedUpdate(DocumentEvent event) {
        update();
      }
    });
    input.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
          popup.setVisible(false);
        }
      }
    });
  }

  void update() {
    SwingUtilities.invokeLater(() -> {
      var prefix = activePrefix();
      if (prefix == null) {
        popup.setVisible(false);
        return;
      }
      var matches = connections.get().stream()
          .filter(connection -> connection.name().toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT)))
          .limit(8)
          .toList();
      if (matches.isEmpty()) {
        popup.setVisible(false);
        return;
      }
      popup.removeAll();
      for (var connection : matches) {
        var item = new JMenuItem("@" + connection.name() + "  " + connection.user() + "@" + connection.host());
        item.addActionListener(event -> {
          selected.accept(connection);
          replaceMention(connection.name());
          popup.setVisible(false);
        });
        popup.add(item);
      }
      popup.show(input, 8, input.getHeight() - 4);
    });
  }

  String activePrefix() {
    var caret = input.getCaretPosition();
    var text = input.getText();
    var start = text.lastIndexOf('@', Math.max(0, caret - 1));
    if (start < 0) {
      return null;
    }
    for (var i = start + 1; i < caret; i++) {
      if (Character.isWhitespace(text.charAt(i))) {
        return null;
      }
    }
    return text.substring(start + 1, caret);
  }

  private void replaceMention(String name) {
    var caret = input.getCaretPosition();
    var text = input.getText();
    var start = text.lastIndexOf('@', Math.max(0, caret - 1));
    if (start >= 0) {
      input.replaceRange("@" + name + " ", start, caret);
    }
  }
}
