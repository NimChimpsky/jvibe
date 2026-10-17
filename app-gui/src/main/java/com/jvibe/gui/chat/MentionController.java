package com.jvibe.gui.chat;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Offers matching items when the user types {@code @name} in the prompt. */
public final class MentionController<T> {
  private final JTextArea input;
  private final Supplier<List<T>> items;
  private final Function<T, String> name;
  private final Function<T, String> detail;
  private final Consumer<T> selected;
  private final JPopupMenu popup = new JPopupMenu();

  public MentionController(JTextArea input, Supplier<List<T>> items, Function<T, String> name,
      Function<T, String> detail, Consumer<T> selected) {
    this.input = input;
    this.items = items;
    this.name = name;
    this.detail = detail;
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
      var lowerPrefix = prefix.toLowerCase(java.util.Locale.ROOT);
      var matches = items.get().stream()
          .filter(item -> name.apply(item).toLowerCase(java.util.Locale.ROOT).startsWith(lowerPrefix))
          .limit(8)
          .toList();
      if (matches.isEmpty()) {
        popup.setVisible(false);
        return;
      }
      popup.removeAll();
      for (var match : matches) {
        var item = new JMenuItem("@" + name.apply(match) + "  " + detail.apply(match));
        item.addActionListener(event -> {
          selected.accept(match);
          replaceMention(name.apply(match));
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
