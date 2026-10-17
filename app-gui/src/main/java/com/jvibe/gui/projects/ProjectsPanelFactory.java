package com.jvibe.gui.projects;

import com.jvibe.gui.chat.DesktopChatController;
import com.jvibe.gui.chat.DesktopChatFactory;
import com.jvibe.project.ProjectRepositoryService;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JSplitPane;

@Singleton
public final class ProjectsPanelFactory {
  private final ProjectRepositoryService projects;
  private final DesktopChatFactory chats;
  private final ProjectGitPanelFactory gitPanels;

  public ProjectsPanelFactory(
      @External ProjectRepositoryService projects,
      DesktopChatFactory chats,
      ProjectGitPanelFactory gitPanels) {
    this.projects = projects;
    this.chats = chats;
    this.gitPanels = gitPanels;
  }

  public ProjectsView create(
      Path currentDirectory,
      DesktopChatController.Callbacks callbacks,
      Consumer<String> log) {
    var roots = loadProjects(currentDirectory, log);
    var initial = roots.isEmpty() ? null : roots.get(0);
    var git = gitPanels.create(log);
    var projectCallbacks = new ProjectCallbacks(callbacks, git);
    var chat = initial == null
        ? chats.createBuild(projectCallbacks)
        : chats.createWorkspace(initial, projectCallbacks);
    projectCallbacks.chat = chat;
    if (initial != null) {
      git.setRepository(initial);
    }
    var selector = new ProjectSelectorPanel(projects, roots, root -> {
      chat.createWorkspace(root);
    }, log);
    if (initial != null) {
      selector.select(initial);
    }

    var build = new javax.swing.JPanel(new java.awt.BorderLayout());
    build.add(chat.component(), java.awt.BorderLayout.CENTER);
    var terminal = com.jvibe.gui.terminal.TerminalToggleButton.create(chat::toggleActiveShell, chat::restartActiveShell);
    var footer = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 4));
    footer.add(terminal);
    build.add(footer, java.awt.BorderLayout.SOUTH);
    var root = layout(selector, build, git);
    return new ProjectsView(root, chat);
  }

  private static final class ProjectCallbacks implements DesktopChatController.Callbacks {
    private final DesktopChatController.Callbacks delegate;
    private final ProjectGitPanel git;
    private DesktopChatController chat;

    ProjectCallbacks(DesktopChatController.Callbacks delegate, ProjectGitPanel git) {
      this.delegate = delegate;
      this.git = git;
    }

    @Override public void log(String level, String message) { delegate.log(level, message); }
    @Override public void workspaceChanged() {
      delegate.workspaceChanged();
      git.refresh();
    }
    @Override public void activeSessionChanged() {
      if (chat != null) {
        var session = chat.activeSession();
        git.setRepository(session != null && session.mode() == com.jvibe.session.AgentSession.Mode.WORKSPACE
            ? chat.activeWorkingDirectory() : null);
      }
      delegate.activeSessionChanged();
    }
  }

  static JSplitPane layout(
      java.awt.Component selector,
      java.awt.Component chat,
      java.awt.Component git) {
    var chatAndGit = split(chat, git, 1.0);
    chat.setMinimumSize(new java.awt.Dimension(420, 0));
    com.jvibe.gui.layout.SplitPaneSizing.initialProportion(chatAndGit, 0.65);
    var root = split(selector, chatAndGit, 0.0);
    com.jvibe.gui.layout.SplitPaneSizing.initialProportion(root, 0.20);
    return root;
  }

  private List<Path> loadProjects(Path currentDirectory, Consumer<String> log) {
    try {
      var saved = projects.projects();
      if (!saved.isEmpty()) {
        return saved;
      }
      var current = projects.findGitRoot(currentDirectory);
      if (current.isPresent()) {
        projects.add(current.get());
        return projects.projects();
      }
    } catch (Exception e) {
      log.accept("Unable to load projects: " + e.getMessage());
    }
    return List.of();
  }

  private static JSplitPane split(
      java.awt.Component left,
      java.awt.Component right,
      double resizeWeight) {
    var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
    split.setResizeWeight(resizeWeight);
    split.setContinuousLayout(true);
    split.setDividerSize(6);
    split.setBorder(null);
    return split;
  }
}
