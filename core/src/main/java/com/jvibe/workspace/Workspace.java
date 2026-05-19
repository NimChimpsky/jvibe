package com.jvibe.workspace;

import java.nio.file.Path;

public record Workspace(Path root) {
  public Workspace {
    root = root.toAbsolutePath().normalize();
  }

  public Path resolveInside(String path) {
    var resolved = root.resolve(path).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Path escapes workspace: " + path);
    }
    return resolved;
  }
}
