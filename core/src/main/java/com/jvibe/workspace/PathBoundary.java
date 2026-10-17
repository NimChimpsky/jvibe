package com.jvibe.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Checks existing ancestors as well as the requested path, including new output files. */
public final class PathBoundary {
  private PathBoundary() {}

  public static Path requireInside(Path root, Path requested) {
    var base = root.toAbsolutePath().normalize();
    var target = requested.toAbsolutePath().normalize();
    try {
      if (!target.startsWith(base) || !realPath(target).startsWith(realPath(base))) {
        throw new IllegalArgumentException("Path escapes workspace: " + requested);
      }
      return target;
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to resolve path: " + requested, e);
    }
  }

  private static Path realPath(Path path) throws IOException {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return path.toRealPath();
    var parent = path.getParent();
    if (parent == null) return path.toRealPath();
    return realPath(parent).resolve(path.getFileName());
  }
}
