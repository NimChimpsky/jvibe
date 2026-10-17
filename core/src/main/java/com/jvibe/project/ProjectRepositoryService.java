package com.jvibe.project;

import com.jvibe.config.ConfigService;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public final class ProjectRepositoryService {
  private final ConfigService configService;

  public ProjectRepositoryService(@External ConfigService configService) {
    this.configService = configService;
  }

  public List<Path> projects() throws IOException {
    return configService.config().projectPaths().stream()
        .map(Path::of)
        .map(path -> path.toAbsolutePath().normalize())
        .filter(path -> Files.isDirectory(path) && Files.exists(path.resolve(".git")))
        .toList();
  }

  public Path add(Path selectedDirectory) throws IOException {
    var root = findGitRoot(selectedDirectory)
        .orElseThrow(() -> new IllegalArgumentException("Select a directory inside a Git repository"));
    var projects = new ArrayList<>(projects());
    projects.remove(root);
    projects.add(0, root);
    save(projects);
    return root;
  }

  public void remove(Path repository) throws IOException {
    var normalized = repository.toAbsolutePath().normalize();
    save(projects().stream().filter(path -> !path.equals(normalized)).toList());
  }

  public Optional<Path> findGitRoot(Path start) {
    if (start == null) {
      return Optional.empty();
    }
    var current = start.toAbsolutePath().normalize();
    if (!Files.isDirectory(current)) {
      current = current.getParent();
    }
    while (current != null) {
      if (Files.exists(current.resolve(".git"))) {
        return Optional.of(current);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  private void save(List<Path> projects) throws IOException {
    configService.updateConfig(config -> config.withProjectPaths(
        projects.stream().map(Path::toString).toList()));
  }
}
