package com.jvibe.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;

class JvibeGuiTest {
  @Test
  void terminalButtonIsIconOnlyWithTooltip() {
    var button = JvibeGui.terminalButton(event -> {});

    assertEquals("", button.getText());
    assertEquals("Open terminal", button.getToolTipText());
    assertNotNull(button.getIcon());
  }

  @Test
  void terminalButtonHasRestartPopupAction() {
    var button = new JvibeGui().shellToggleButton();

    assertNotNull(button.getComponentPopupMenu());
    assertEquals("Restart Shell", ((JMenuItem) button.getComponentPopupMenu().getComponent(0)).getText());
  }

  @Test
  void composerAndPromptInputHaveFixedHeights() {
    var composer = new JvibeGui().buildComposer();

    assertEquals(JvibeGui.COMPOSER_HEIGHT, composer.getPreferredSize().height);
    assertEquals(JvibeGui.COMPOSER_HEIGHT, composer.getMinimumSize().height);
    assertEquals(JvibeGui.COMPOSER_HEIGHT, composer.getMaximumSize().height);
    assertTrue(hasPromptScroll(composer));
  }

  @Test
  void composerFileReferenceChipsDoNotUsePromptScrollHeader() {
    var composer = new JvibeGui().buildComposer();
    var scroll = promptScroll(composer);
    var fileReferenceRow = fileReferenceRow(composer);

    assertNotNull(scroll);
    assertNotNull(fileReferenceRow);
    assertEquals(JvibeGui.PROMPT_INPUT_HEIGHT, scroll.getPreferredSize().height);
    assertEquals(JvibeGui.FILE_REFERENCE_ROW_HEIGHT, fileReferenceRow.getPreferredSize().height);
    assertEquals(null, scroll.getColumnHeader());
  }

  @Test
  void fileReferenceChipShowsFilenameAndPathTooltip() {
    var chip = new JvibeGui().fileReferenceChip(java.nio.file.Path.of("/tmp/example.txt"));

    assertEquals("example.txt x", chip.getText());
    assertEquals("/tmp/example.txt", chip.getToolTipText());
  }

  private static boolean hasPromptScroll(Component component) {
    if (component instanceof JScrollPane scroll && scroll.getPreferredSize().height == JvibeGui.PROMPT_INPUT_HEIGHT) {
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
    if (component instanceof JScrollPane scroll && scroll.getPreferredSize().height == JvibeGui.PROMPT_INPUT_HEIGHT) {
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
        && container.getPreferredSize().height == JvibeGui.FILE_REFERENCE_ROW_HEIGHT) {
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
