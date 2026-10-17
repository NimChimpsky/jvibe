package com.jvibe.tool.builtin;

import com.jvibe.workspace.Workspace;
import com.jvibe.workspace.PathBoundary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public interface FileToolAccess {
  Path resolveReadable(Workspace workspace, String path);

  Path resolveWritable(Workspace workspace, String path);

  default List<Path> listRoots(Workspace workspace) {
    return List.of(workspace.root());
  }

  static FileToolAccess workspace() {
    return new WorkspaceFileToolAccess();
  }

  static FileToolAccess attachments(Supplier<List<Path>> references) {
    return new DefaultChatFileToolAccess(references);
  }
}

final class WorkspaceFileToolAccess implements FileToolAccess {
  @Override
  public Path resolveReadable(Workspace workspace, String path) {
    return workspace.resolveInside(path);
  }

  @Override
  public Path resolveWritable(Workspace workspace, String path) {
    return workspace.resolveInside(path);
  }
}

final class DefaultChatFileToolAccess implements FileToolAccess {
  private final Supplier<List<Path>> references;

  DefaultChatFileToolAccess(Supplier<List<Path>> references) {
    this.references = references;
  }

  @Override
  public Path resolveReadable(Workspace workspace, String raw) {
    var requested = Path.of(raw);
    if (requested.isAbsolute()) {
      var absolute = requested.toAbsolutePath().normalize();
      if (allowedRead(absolute)) {
        return absolute;
      }
      throw new IllegalArgumentException("Path is not attached to chat: " + raw);
    }

    for (var reference : normalizedReferences()) {
      if (reference.getFileName() != null && reference.getFileName().toString().equals(raw)) {
        return reference;
      }
      if (Files.isDirectory(reference)) {
        var candidate = reference.resolve(raw).normalize();
        if (candidate.startsWith(reference) && Files.exists(candidate)) {
          return PathBoundary.requireInside(reference, candidate);
        }
      }
    }
    throw new IllegalArgumentException("Path is not attached to chat: " + raw);
  }

  @Override
  public Path resolveWritable(Workspace workspace, String raw) {
    throw new IllegalArgumentException("Chat is read-only; use Build to edit project files");
  }

  @Override
  public List<Path> listRoots(Workspace workspace) {
    return normalizedReferences();
  }

  private boolean allowedRead(Path path) {
    for (var reference : normalizedReferences()) {
      if (Files.isDirectory(reference)) {
        if (path.startsWith(reference)) {
          PathBoundary.requireInside(reference, path);
          return true;
        }
      } else if (path.equals(reference)) {
        return true;
      }
    }
    return false;
  }

  private List<Path> normalizedReferences() {
    return references.get().stream()
        .map(path -> path.toAbsolutePath().normalize())
        .distinct()
        .toList();
  }
}
