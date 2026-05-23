package com.jvibe.tool.builtin;

import com.jvibe.workspace.Workspace;
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

  static FileToolAccess defaultChat(Path tmp, Supplier<List<Path>> references) {
    return new DefaultChatFileToolAccess(tmp, references);
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
  private final Path tmp;
  private final Supplier<List<Path>> references;

  DefaultChatFileToolAccess(Path tmp, Supplier<List<Path>> references) {
    this.tmp = tmp.toAbsolutePath().normalize();
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

    var tmpCandidate = tmp.resolve(raw).normalize();
    if (tmpCandidate.startsWith(tmp) && Files.exists(tmpCandidate)) {
      return tmpCandidate;
    }
    for (var reference : normalizedReferences()) {
      if (reference.getFileName() != null && reference.getFileName().toString().equals(raw)) {
        return reference;
      }
      if (Files.isDirectory(reference)) {
        var candidate = reference.resolve(raw).normalize();
        if (candidate.startsWith(reference) && Files.exists(candidate)) {
          return candidate;
        }
      }
    }
    throw new IllegalArgumentException("Path is not attached to chat: " + raw);
  }

  @Override
  public Path resolveWritable(Workspace workspace, String raw) {
    var path = Path.of(raw);
    var resolved = path.isAbsolute() ? path.toAbsolutePath().normalize() : tmp.resolve(path).normalize();
    if (!resolved.startsWith(tmp)) {
      throw new IllegalArgumentException("Output path escapes ~/.jvibe/tmp: " + raw);
    }
    return resolved;
  }

  @Override
  public List<Path> listRoots(Workspace workspace) {
    var roots = new java.util.ArrayList<Path>();
    roots.add(tmp);
    roots.addAll(normalizedReferences());
    return roots;
  }

  private boolean allowedRead(Path path) {
    if (path.startsWith(tmp)) {
      return true;
    }
    for (var reference : normalizedReferences()) {
      if (Files.isDirectory(reference)) {
        if (path.startsWith(reference)) {
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
