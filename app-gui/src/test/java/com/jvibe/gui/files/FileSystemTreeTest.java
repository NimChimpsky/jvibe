package com.jvibe.gui.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemTreeTest {
  @TempDir
  Path temp;

  @Test
  void childrenAreSortedWithDirectoriesFirst() throws Exception {
    Files.createDirectory(temp.resolve("Beta"));
    Files.createDirectory(temp.resolve("AlphaDir"));
    Files.writeString(temp.resolve("zeta.txt"), "");
    Files.writeString(temp.resolve("alpha.txt"), "");

    var children = FileSystemTree.children(temp);

    assertEquals(
        java.util.List.of("AlphaDir", "Beta", "alpha.txt", "zeta.txt"),
        children.stream().map(FileSystemTree.Entry::label).toList());
    assertEquals(
        java.util.List.of(true, true, false, false),
        children.stream().map(FileSystemTree.Entry::directory).toList());
  }

  @Test
  void unreadableDirectoryReturnsErrorEntry() {
    var children = FileSystemTree.children(temp.resolve("missing"));

    assertEquals(1, children.size());
    assertTrue(children.getFirst().error());
    assertEquals("Unable to read", children.getFirst().label());
  }

  @Test
  void hiddenEntriesAreHiddenByDefaultAndShownWhenRequested() throws Exception {
    Files.writeString(temp.resolve(".env"), "");
    Files.writeString(temp.resolve("README.md"), "");

    assertEquals(
        java.util.List.of("README.md"),
        FileSystemTree.children(temp).stream().map(FileSystemTree.Entry::label).toList());
    assertEquals(
        java.util.List.of(".env", "README.md"),
        FileSystemTree.children(temp, true).stream().map(FileSystemTree.Entry::label).toList());
  }

  @Test
  void treeContextMenuTogglesShowHidden() throws Exception {
    Files.writeString(temp.resolve(".env"), "");
    Files.writeString(temp.resolve("README.md"), "");
    var tree = new AtomicReference<JTree>();
    SwingUtilities.invokeAndWait(() -> tree.set(FileSystemTree.create(temp)));

    var selected = (DefaultMutableTreeNode) tree.get().getSelectionPath().getLastPathComponent();
    assertEquals(1, selected.getChildCount());

    var showHidden = (JCheckBoxMenuItem) tree.get().getComponentPopupMenu().getComponent(1);
    SwingUtilities.invokeAndWait(showHidden::doClick);
    selected = (DefaultMutableTreeNode) tree.get().getSelectionPath().getLastPathComponent();

    assertEquals(2, selected.getChildCount());
    assertEquals("Show Hidden", showHidden.getText());
    assertTrue(showHidden.isSelected());
  }

  @Test
  void addToChatUsesSelectedFileTreePath() throws Exception {
    var added = new AtomicReference<Path>();
    var tree = new AtomicReference<JTree>();
    SwingUtilities.invokeAndWait(() -> tree.set(FileSystemTree.create(temp, added::set)));
    var selected = tree.get().getSelectionPath();
    tree.get().setSelectionPath(selected);

    var addToChat = (JMenuItem) tree.get().getComponentPopupMenu().getComponent(0);
    SwingUtilities.invokeAndWait(addToChat::doClick);

    assertEquals(temp.toAbsolutePath().normalize(), added.get());
    assertEquals("Add to Chat", addToChat.getText());
  }

  @Test
  void treeUsesFilesystemRootAndSelectsInitialPath() throws Exception {
    var tree = new AtomicReference<JTree>();
    SwingUtilities.invokeAndWait(() -> tree.set(FileSystemTree.create(temp)));

    var root = (DefaultMutableTreeNode) tree.get().getModel().getRoot();
    var selected = (DefaultMutableTreeNode) tree.get().getSelectionPath().getLastPathComponent();

    assertEquals(temp.toAbsolutePath().normalize().getRoot(), ((FileSystemTree.Entry) root.getUserObject()).path());
    assertEquals(temp.toAbsolutePath().normalize(), ((FileSystemTree.Entry) selected.getUserObject()).path());
  }

  @Test
  void expandingNodeLoadsChildren() throws Exception {
    Files.createDirectory(temp.resolve("src"));
    Files.writeString(temp.resolve("README.md"), "");
    var tree = new AtomicReference<JTree>();
    SwingUtilities.invokeAndWait(() -> {
      tree.set(FileSystemTree.create(temp));
    });

    var selected = (DefaultMutableTreeNode) tree.get().getSelectionPath().getLastPathComponent();

    assertEquals("src", ((DefaultMutableTreeNode) selected.getChildAt(0)).getUserObject().toString());
    assertEquals("README.md", ((DefaultMutableTreeNode) selected.getChildAt(1)).getUserObject().toString());
  }
}
