package com.jvibe.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.jvibe.tool.builtin.FileToolAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathBoundaryTest {
  @TempDir Path home;

  @Test
  void rejectsSymlinkEscapesForExistingAndNewFiles() throws Exception {
    var root = Files.createDirectory(home.resolve("workspace"));
    var outside = Files.createDirectory(home.resolve("outside"));
    Files.writeString(outside.resolve("secret.txt"), "secret");
    link(root.resolve("link"), outside);
    var workspace = new Workspace(root);
    assertThrows(IllegalArgumentException.class, () -> workspace.resolveInside("link/secret.txt"));
    assertThrows(IllegalArgumentException.class, () -> workspace.resolveInside("link/new/file.txt"));
    assertThrows(IllegalArgumentException.class, () -> workspace.resolveInside("../outside/secret.txt"));
    assertEquals(root.resolve("new/file.txt"), workspace.resolveInside("new/file.txt"));
  }

  @Test
  void chatProtectsTmpAndAttachedDirectories() throws Exception {
    var tmp = Files.createDirectory(home.resolve("tmp"));
    var ref = Files.createDirectory(home.resolve("ref"));
    var outside = Files.createDirectory(home.resolve("outside"));
    var secret = Files.writeString(outside.resolve("secret.txt"), "secret");
    link(tmp.resolve("link"), outside);
    link(ref.resolve("link"), outside);
    var access = FileToolAccess.defaultChat(() -> List.of(ref));
    var workspace = new Workspace(tmp);
    assertThrows(IllegalArgumentException.class, () -> access.resolveWritable(workspace, "link/new.txt"));
    assertThrows(IllegalArgumentException.class, () -> access.resolveReadable(workspace, "link/secret.txt"));
    assertThrows(IllegalArgumentException.class, () -> access.resolveReadable(workspace, ref.resolve("link/secret.txt").toString()));
    var direct = FileToolAccess.defaultChat(() -> List.of(secret));
    assertEquals(secret, direct.resolveReadable(workspace, secret.toString()));
  }

  @Test
  void permitsSymlinksWithinWorkspaceAndSymlinkedWorkspaceRoot() throws Exception {
    var root = Files.createDirectory(home.resolve("root"));
    var folder = Files.createDirectory(root.resolve("folder"));
    link(root.resolve("internal"), folder);
    link(home.resolve("alias"), root);
    var workspace = new Workspace(home.resolve("alias"));
    assertEquals(home.resolve("alias/internal/new.txt"), workspace.resolveInside("internal/new.txt"));
  }

  private void link(Path link, Path target) throws Exception {
    try {
      Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
      org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + e.getMessage());
    }
  }
}
