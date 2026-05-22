package com.jvibe.gui;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.util.function.Consumer;

final class FileSystemTree {
  private static final Loading LOADING = new Loading();
  private static final Comparator<Entry> ENTRY_ORDER = Comparator
      .comparing((Entry entry) -> !entry.directory())
      .thenComparing(entry -> entry.label().toLowerCase(Locale.ROOT))
      .thenComparing(Entry::label);

  private FileSystemTree() {}

  static JTree create(Path initialPath) {
    return create(initialPath, path -> {});
  }

  static JTree create(Path initialPath, Consumer<Path> addToChat) {
    var focusedPath = initialPath.toAbsolutePath().normalize();
    var rootPath = filesystemRoot(focusedPath);
    var root = node(new Entry(rootPath, label(rootPath), true, false));
    var model = new DefaultTreeModel(root);
    var tree = new JTree(model);
    tree.putClientProperty("showHidden", false);
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.setCellRenderer(new Renderer());
    tree.setComponentPopupMenu(contextMenu(tree, model, root, focusedPath, addToChat));
    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        selectPopupPath(tree, event);
      }

      @Override
      public void mouseReleased(MouseEvent event) {
        selectPopupPath(tree, event);
      }
    });
    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) {
        var expanded = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
        loadChildren(model, expanded, showHidden(tree));
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
        // Collapsing does not need any work.
      }
    });
    focusPath(tree, model, root, focusedPath, false);
    return tree;
  }

  static List<Entry> children(Path directory) {
    return children(directory, false);
  }

  static List<Entry> children(Path directory, boolean showHidden) {
    try (var stream = Files.list(directory)) {
      return stream
          .filter(path -> showHidden || !hidden(path))
          .map(FileSystemTree::entry)
          .sorted(ENTRY_ORDER)
          .toList();
    } catch (IOException | SecurityException e) {
      return List.of(Entry.error("Unable to read"));
    }
  }

  private static DefaultMutableTreeNode node(Entry entry) {
    var node = new DefaultMutableTreeNode(entry);
    if (entry.directory() && !entry.error()) {
      node.add(new DefaultMutableTreeNode(LOADING));
    }
    return node;
  }

  private static void loadChildren(DefaultTreeModel model, DefaultMutableTreeNode node, boolean showHidden) {
    if (!hasLoadingPlaceholder(node) || !(node.getUserObject() instanceof Entry entry)) {
      return;
    }
    node.removeAllChildren();
    for (var child : children(entry.path(), showHidden)) {
      node.add(node(child));
    }
    model.reload(node);
  }

  private static void focusPath(
      JTree tree,
      DefaultTreeModel model,
      DefaultMutableTreeNode root,
      Path focusedPath,
      boolean showHidden) {
    var currentNode = root;
    var currentPath = ((Entry) root.getUserObject()).path();
    loadChildren(model, currentNode, true);
    for (var name : currentPath.relativize(focusedPath)) {
      var nextPath = currentPath.resolve(name);
      var nextNode = childForPath(currentNode, nextPath);
      if (nextNode == null) {
        break;
      }
      currentNode = nextNode;
      currentPath = nextPath;
      loadChildren(model, currentNode, true);
    }
    reloadLoadedNodes(model, root, showHidden);
    var treePath = new TreePath(currentNode.getPath());
    tree.expandPath(treePath);
    tree.setSelectionPath(treePath);
    tree.scrollPathToVisible(treePath);
  }

  private static DefaultMutableTreeNode childForPath(DefaultMutableTreeNode parent, Path path) {
    for (var index = 0; index < parent.getChildCount(); index++) {
      var child = (DefaultMutableTreeNode) parent.getChildAt(index);
      if (child.getUserObject() instanceof Entry entry && path.equals(entry.path())) {
        return child;
      }
    }
    return null;
  }

  private static JPopupMenu contextMenu(
      JTree tree,
      DefaultTreeModel model,
      DefaultMutableTreeNode root,
      Path focusedPath,
      Consumer<Path> addToChat) {
    var menu = new JPopupMenu();
    var add = new JMenuItem("Add to Chat");
    add.addActionListener(event -> {
      var selected = selectedPath(tree, null);
      if (selected != null) {
        addToChat.accept(selected);
      }
    });
    menu.add(add);
    var showHidden = new JCheckBoxMenuItem("Show Hidden");
    showHidden.addActionListener(event -> {
      var enabled = showHidden.isSelected();
      tree.putClientProperty("showHidden", enabled);
      var selected = selectedPath(tree, focusedPath);
      root.removeAllChildren();
      root.add(new DefaultMutableTreeNode(LOADING));
      model.reload(root);
      focusPath(tree, model, root, selected, enabled);
    });
    menu.add(showHidden);
    menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) {
        add.setEnabled(selectedPath(tree, null) != null);
      }

      @Override
      public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) {
        // No state to clear.
      }

      @Override
      public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) {
        // No state to clear.
      }
    });
    return menu;
  }

  private static Path selectedPath(JTree tree, Path fallback) {
    var selection = tree.getSelectionPath();
    if (selection == null) {
      return fallback;
    }
    var node = (DefaultMutableTreeNode) selection.getLastPathComponent();
    if (node.getUserObject() instanceof Entry entry && entry.path() != null) {
      return entry.path();
    }
    return fallback;
  }

  private static void selectPopupPath(JTree tree, MouseEvent event) {
    if (!event.isPopupTrigger()) {
      return;
    }
    var path = tree.getPathForLocation(event.getX(), event.getY());
    if (path != null) {
      tree.setSelectionPath(path);
    }
  }

  private static void reloadLoadedNodes(DefaultTreeModel model, DefaultMutableTreeNode root, boolean showHidden) {
    for (var node : loadedDirectoryNodes(root)) {
      if (node.getUserObject() instanceof Entry entry && entry.directory() && !entry.error()) {
        node.removeAllChildren();
        for (var child : children(entry.path(), showHidden)) {
          node.add(node(child));
        }
        model.reload(node);
      }
    }
  }

  private static List<DefaultMutableTreeNode> loadedDirectoryNodes(DefaultMutableTreeNode root) {
    var nodes = new ArrayList<DefaultMutableTreeNode>();
    collectLoadedDirectoryNodes(root, nodes);
    return nodes;
  }

  private static void collectLoadedDirectoryNodes(
      DefaultMutableTreeNode node,
      List<DefaultMutableTreeNode> nodes) {
    if (!(node.getUserObject() instanceof Entry entry) || !entry.directory() || hasLoadingPlaceholder(node)) {
      return;
    }
    nodes.add(node);
    for (var index = 0; index < node.getChildCount(); index++) {
      collectLoadedDirectoryNodes((DefaultMutableTreeNode) node.getChildAt(index), nodes);
    }
  }

  private static boolean hasLoadingPlaceholder(DefaultMutableTreeNode node) {
    return node.getChildCount() == 1
        && ((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject() == LOADING;
  }

  private static Entry entry(Path path) {
    return new Entry(path, label(path), Files.isDirectory(path), false);
  }

  private static boolean hidden(Path path) {
    var name = path.getFileName();
    if (name != null && name.toString().startsWith(".")) {
      return true;
    }
    try {
      return Files.isHidden(path);
    } catch (IOException | SecurityException e) {
      return false;
    }
  }

  private static boolean showHidden(JTree tree) {
    return Boolean.TRUE.equals(tree.getClientProperty("showHidden"));
  }

  private static Path filesystemRoot(Path path) {
    var root = path.getRoot();
    return root == null ? path : root;
  }

  private static String label(Path path) {
    var name = path.getFileName();
    return name == null ? path.toString() : name.toString();
  }

  record Entry(Path path, String label, boolean directory, boolean error) {
    static Entry error(String label) {
      return new Entry(null, label, false, true);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private record Loading() {
    @Override
    public String toString() {
      return "Loading...";
    }
  }

  private static final class Renderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(
        JTree tree,
        Object value,
        boolean selected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {
      var component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Entry entry && entry.error()) {
        setForeground(UIManager.getColor("Label.disabledForeground"));
      }
      return component;
    }
  }
}
