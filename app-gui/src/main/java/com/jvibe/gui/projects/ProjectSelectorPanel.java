package com.jvibe.gui.projects;

import com.jvibe.project.ProjectRepositoryService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;

final class ProjectSelectorPanel extends JPanel {
  private final ProjectRepositoryService projects;
  private final Consumer<Path> selected;
  private final Consumer<String> log;
  private final DefaultListModel<Path> model = new DefaultListModel<>();
  private final JList<Path> list = new JList<>(model);

  ProjectSelectorPanel(
      ProjectRepositoryService projects,
      List<Path> initialProjects,
      Consumer<Path> selected,
      Consumer<String> log) {
    super(new BorderLayout(0, 8));
    this.projects = projects;
    this.selected = selected;
    this.log = log;
    setBorder(new EmptyBorder(10, 10, 10, 10));
    setPreferredSize(new Dimension(240, 0));
    setMinimumSize(new Dimension(200, 0));

    var add = new JButton("+");
    add.setToolTipText("Add project");
    add.setFocusable(false);
    add.addActionListener(event -> chooseProject());
    var header = new JPanel(new BorderLayout());
    var title = new JLabel("PROJECTS");
    title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 11));
    header.add(title, BorderLayout.WEST);
    header.add(add, BorderLayout.EAST);
    add(header, BorderLayout.NORTH);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setFixedCellHeight(34);
    list.setCellRenderer(new ProjectRenderer());
    list.addListSelectionListener(event -> {
      if (!event.getValueIsAdjusting() && list.getSelectedValue() != null) {
        selected.accept(list.getSelectedValue());
      }
    });
    var menu = new JPopupMenu();
    var remove = new JMenuItem("Remove from Projects");
    remove.addActionListener(event -> removeSelected());
    menu.add(remove);
    list.setComponentPopupMenu(menu);
    add(scroll(list), BorderLayout.CENTER);
    replace(initialProjects);
  }

  void select(Path project) {
    list.setSelectedValue(project.toAbsolutePath().normalize(), true);
  }

  private void chooseProject() {
    var chooser = new JFileChooser();
    chooser.setDialogTitle("Add Git project");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    try {
      var root = projects.add(chooser.getSelectedFile().toPath());
      replace(projects.projects());
      select(root);
      log.accept("Added project: " + root);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          this,
          e.getMessage(),
          "Unable to add project",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void removeSelected() {
    var project = list.getSelectedValue();
    if (project == null) {
      return;
    }
    try {
      projects.remove(project);
      replace(projects.projects());
      log.accept("Removed project: " + project);
    } catch (Exception e) {
      log.accept("Unable to remove project: " + e.getMessage());
    }
  }

  private void replace(List<Path> paths) {
    model.clear();
    paths.forEach(model::addElement);
  }

  private static JScrollPane scroll(java.awt.Component component) {
    var pane = new JScrollPane(component);
    pane.setBorder(BorderFactory.createEmptyBorder());
    return pane;
  }

  private static final class ProjectRenderer extends DefaultListCellRenderer {
    @Override
    public java.awt.Component getListCellRendererComponent(
        JList<?> list,
        Object value,
        int index,
        boolean selected,
        boolean focus) {
      var label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
      if (value instanceof Path path) {
        label.setText(path.getFileName() == null ? path.toString() : path.getFileName().toString());
        label.setToolTipText(path.toString());
        label.setBorder(new EmptyBorder(0, 8, 0, 8));
      }
      return label;
    }
  }
}
