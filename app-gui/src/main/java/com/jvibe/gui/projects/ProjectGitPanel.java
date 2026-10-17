package com.jvibe.gui.projects;

import com.jvibe.git.GitService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

final class ProjectGitPanel extends JPanel {
  private static final int MAX_DIFF_CHARS = 1_000_000;
  private final GitService git;
  private final Consumer<String> log;
  private final JLabel repositoryLabel = new JLabel("No project selected");
  private final JLabel branchLabel = new JLabel(" ");
  private final DefaultListModel<String> changes = new DefaultListModel<>();
  private final JTextArea diff = new JTextArea();
  private final JButton refresh = new JButton("Refresh");
  private Path repository;
  private long revision;

  ProjectGitPanel(GitService git, Consumer<String> log) {
    super(new BorderLayout(0, 8));
    this.git = git;
    this.log = log;
    setBorder(new EmptyBorder(10, 10, 10, 10));
    setPreferredSize(new Dimension(340, 0));
    setMinimumSize(new Dimension(280, 0));

    repositoryLabel.setFont(repositoryLabel.getFont().deriveFont(java.awt.Font.BOLD));
    var title = new JPanel(new BorderLayout(6, 4));
    title.add(repositoryLabel, BorderLayout.NORTH);
    title.add(branchLabel, BorderLayout.CENTER);
    refresh.addActionListener(event -> refresh());
    title.add(refresh, BorderLayout.EAST);
    add(title, BorderLayout.NORTH);

    var changeList = new JList<>(changes);
    changeList.setFixedCellHeight(24);
    diff.setEditable(false);
    diff.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
    diff.setTabSize(2);

    var tabs = new JTabbedPane();
    tabs.addTab("Changes", scroll(changeList));
    tabs.addTab("Diff", scroll(diff));
    add(tabs, BorderLayout.CENTER);
    showEmpty();
  }

  void setRepository(Path value) {
    revision++;
    repository = value == null ? null : value.toAbsolutePath().normalize();
    if (repository == null) {
      showEmpty();
      return;
    }
    repositoryLabel.setText(fileName(repository));
    repositoryLabel.setToolTipText(repository.toString());
    refresh();
  }

  void refresh() {
    if (repository == null) {
      return;
    }
    var target = repository;
    var taskRevision = ++revision;
    refresh.setEnabled(false);
    branchLabel.setText("Refreshing Git status...");
    new SwingWorker<GitSnapshot, Void>() {
      @Override
      protected GitSnapshot doInBackground() {
        try {
          var status = git.run(target, "status", "--short", "--branch");
          if (status.exitCode() != 0) {
            return GitSnapshot.error(message(status.stderr(), "Unable to read Git status"));
          }
          var lines = status.stdout().lines().toList();
          var branch = lines.isEmpty() ? "Branch unavailable" : branch(lines.get(0));
          var changed = lines.size() <= 1 ? java.util.List.<String>of() : lines.subList(1, lines.size());
          var gitDiff = git.run(target, "diff", "--no-ext-diff", "--");
          return new GitSnapshot(
              branch,
              java.util.List.copyOf(changed),
              gitDiff.exitCode() == 0 ? gitDiff.stdout() : gitDiff.stderr(),
              "");
        } catch (Exception e) {
          return GitSnapshot.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
      }

      @Override
      protected void done() {
        if (taskRevision != revision) return;
        refresh.setEnabled(true);
        try {
          render(get());
        } catch (Exception e) {
          render(GitSnapshot.error(e.getMessage()));
        }
      }
    }.execute();
  }

  private void render(GitSnapshot snapshot) {
    changes.clear();
    if (!snapshot.error().isBlank()) {
      branchLabel.setText(snapshot.error());
      diff.setText("");
      log.accept("Git refresh failed: " + snapshot.error());
      return;
    }
    branchLabel.setText(snapshot.branch());
    if (snapshot.changes().isEmpty()) {
      changes.addElement("Working tree clean");
    } else {
      snapshot.changes().forEach(changes::addElement);
    }
    diff.setText(snapshot.diff().isBlank() ? "No unstaged changes." : limitedDiff(snapshot.diff()));
    diff.setCaretPosition(0);
  }

  private void showEmpty() {
    repositoryLabel.setText("No project selected");
    repositoryLabel.setToolTipText(null);
    branchLabel.setText("Add or select a Git repository.");
    changes.clear();
    diff.setText("");
    refresh.setEnabled(false);
  }

  private static JScrollPane scroll(java.awt.Component component) {
    var pane = new JScrollPane(component);
    pane.setBorder(BorderFactory.createEmptyBorder());
    return pane;
  }

  private static String branch(String statusHeader) {
    var value = statusHeader.startsWith("##") ? statusHeader.substring(2).trim() : statusHeader.trim();
    var tracking = value.indexOf("...");
    return tracking < 0 ? value : value.substring(0, tracking);
  }

  private static String fileName(Path path) {
    return path.getFileName() == null ? path.toString() : path.getFileName().toString();
  }

  private static String message(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String limitedDiff(String value) {
    if (value.length() <= MAX_DIFF_CHARS) {
      return value;
    }
    return value.substring(0, MAX_DIFF_CHARS)
        + "\n\nDiff truncated after 1,000,000 characters.";
  }
}
