package com.jvibe.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes small configuration files without exposing partially-written content. */
public final class AtomicFiles {
  private AtomicFiles() {}

  public static void writeString(Path target, String content) throws IOException {
    var parent = target.toAbsolutePath().normalize().getParent();
    if (parent == null) {
      throw new IOException("File has no parent directory: " + target);
    }
    Files.createDirectories(parent);
    java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions = null;
    if (Files.exists(target)) {
      try {
        permissions = Files.getPosixFilePermissions(target);
      } catch (UnsupportedOperationException ignored) {
        // Windows and some filesystems do not expose POSIX permissions.
      }
    }
    var temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
    try {
      Files.writeString(temporary, content);
      try {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      if (permissions != null) {
        Files.setPosixFilePermissions(target, permissions);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
