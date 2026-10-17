package com.jvibe.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.avaje.inject.BeanScope;
import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import com.jvibe.gui.chat.DesktopChatController;
import org.junit.jupiter.api.Test;

class JvibeGuiTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path home;

  @Test
  void rootHasExactlyTheFiveProductTabsWithNoOtherFeatures() throws Exception {
    var paths = com.jvibe.config.JvibePaths.under(home);
    try (var scope = BeanScope.builder().bean(com.jvibe.config.JvibePaths.class, paths).build()) {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        var root = (javax.swing.JTabbedPane) scope.get(JvibeGui.class).buildRoot();
        assertEquals(java.util.List.of("Chat", "Build", "Servers", "Databases", "JVibe"),
            java.util.stream.IntStream.range(0, root.getTabCount()).mapToObj(root::getTitleAt).toList());
        assertEquals(0, root.getSelectedIndex());
        assertEquals(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT, root.getTabLayoutPolicy());
        root.removeNotify();
      });
    }
  }
  @Test
  void applicationGraphIsResolvedByAvajeInject() {
    try (var scope = BeanScope.builder().build()) {
      assertNotNull(scope.get(JvibeGui.class));
    }
  }

  @Test
  void terminalButtonIsIconOnlyWithTooltip() {
    var button = com.jvibe.gui.terminal.TerminalToggleButton.create(() -> {}, () -> {});

    assertEquals("", button.getText());
    assertEquals("Open terminal", button.getToolTipText());
    assertNotNull(button.getIcon());
  }

  @Test
  void composerAndPromptInputHaveFixedHeights() {
    var composer = new com.jvibe.gui.chat.ChatComposer(() -> {}, () -> {});

    assertEquals(DesktopChatController.COMPOSER_HEIGHT, composer.getPreferredSize().height);
    assertEquals(DesktopChatController.COMPOSER_HEIGHT, composer.getMinimumSize().height);
    assertEquals(DesktopChatController.COMPOSER_HEIGHT, composer.getMaximumSize().height);
    assertTrue(hasPromptScroll(composer));
    composer.setSize(600, DesktopChatController.COMPOSER_HEIGHT);
    layoutChildren(composer);
    assertEquals(DesktopChatController.PROMPT_INPUT_HEIGHT, promptScroll(composer).getHeight());
    composer.setSize(900, DesktopChatController.COMPOSER_HEIGHT);
    layoutChildren(composer);
    assertEquals(DesktopChatController.PROMPT_INPUT_HEIGHT, promptScroll(composer).getHeight());
  }

  private static void layoutChildren(Component component) {
    if (component instanceof java.awt.Container container) {
      container.doLayout();
      for (var child : container.getComponents()) layoutChildren(child);
    }
  }

  @Test
  void composerFileReferenceChipsDoNotUsePromptScrollHeader() {
    var composer = new com.jvibe.gui.chat.ChatComposer(() -> {}, () -> {});
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
    var chip = com.jvibe.gui.chat.ChatComposer.fileReferenceChip(java.nio.file.Path.of("/tmp/example.txt"), () -> {});

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
