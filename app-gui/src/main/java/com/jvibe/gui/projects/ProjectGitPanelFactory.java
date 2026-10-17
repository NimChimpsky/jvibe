package com.jvibe.gui.projects;

import com.jvibe.git.GitService;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.util.function.Consumer;

@Singleton
public final class ProjectGitPanelFactory {
  private final GitService git;

  public ProjectGitPanelFactory(@External GitService git) {
    this.git = git;
  }

  ProjectGitPanel create(Consumer<String> log) {
    return new ProjectGitPanel(git, log);
  }
}
