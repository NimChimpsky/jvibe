package com.jvibe.workspace;

import java.nio.file.Path;

public record Workspace(Path root) {
  public Workspace {
    root = root.toAbsolutePath().normalize();
  }

  public Path resolveInside(String path) {
    return PathBoundary.requireInside(root, root.resolve(path));
  }
}
