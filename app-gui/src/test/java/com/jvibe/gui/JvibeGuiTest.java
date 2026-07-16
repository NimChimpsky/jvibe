package com.jvibe.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import com.jvibe.gui.chat.DesktopChatController;
import org.junit.jupiter.api.Test;

class JvibeGuiTest {
  @Test
  void terminalButtonIsIconOnlyWithTooltip() {
    var button = DesktopChatController.terminalButton(event -> {});

    assertEquals("", button.getText());
    assertEquals("Open terminal", button.getToolTipText());
    assertNotNull(button.getIcon());
  }

  @Test
  void composerAndPromptInputHaveFixedHeights() {
    var composer = DesktopChatController.composerForTest();

    assertEquals(DesktopChatController.COMPOSER_HEIGHT, composer.getPreferredSize().height);
    assertEquals(DesktopChatController.COMPOSER_HEIGHT, composer.getMinimumSize().height);
    assertEquals(DesktopChatController.COMPOSER_HEIGHT, composer.getMaximumSize().height);
    assertTrue(hasPromptScroll(composer));
  }

  @Test
  void composerFileReferenceChipsDoNotUsePromptScrollHeader() {
    var composer = DesktopChatController.composerForTest();
    var scroll = promptScroll(composer);
    var fileReferenceRow = fileReferenceRow(composer);

    assertNotNull(scroll);
    assertNotNull(fileReferenceRow);
    assertEquals(DesktopChatController.PROMPT_INPUT_HEIGHT, scroll.getPreferredSize().height);
    assertEquals(DesktopChatController.FILE_REFERENCE_ROW_HEIGHT, fileReferenceRow.getPreferredSize().height);
    assertEquals(null, scroll.getColumnHeader());
  }

  @Test
  void fileReferenceChipShowsFilenameAndPathTooltip() {
    var chip = DesktopChatController.fileReferenceChip(java.nio.file.Path.of("/tmp/example.txt"), () -> {});

    assertEquals("example.txt x", chip.getText());
    assertEquals("/tmp/example.txt", chip.getToolTipText());
  }

  private static boolean hasPromptScroll(Component component) {
    if (component instanceof JScrollPane scroll && scroll.getPreferredSize().height == DesktopChatController.PROMPT_INPUT_HEIGHT) {
      return true;
    }
    if (component instanceof JComponent container) {
      for (var child : container.getComponents()) {
        if (hasPromptScroll(child)) {
          return true;
        }
      }
    }
    return false;
  }

  private static JScrollPane promptScroll(Component component) {
    if (component instanceof JScrollPane scroll && scroll.getPreferredSize().height == DesktopChatController.PROMPT_INPUT_HEIGHT) {
      return scroll;
    }
    if (component instanceof JComponent container) {
      for (var child : container.getComponents()) {
        var found = promptScroll(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static JComponent fileReferenceRow(Component component) {
    if (component instanceof JComponent container
        && container.getPreferredSize().height == DesktopChatController.FILE_REFERENCE_ROW_HEIGHT) {
      return container;
    }
    if (component instanceof JComponent container) {
      for (var child : container.getComponents()) {
        var found = fileReferenceRow(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
