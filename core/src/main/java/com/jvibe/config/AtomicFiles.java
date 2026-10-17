package com.jvibe.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes small configuration files without exposing partially-written content. */
public final class AtomicFiles {
  private static final java.util.Set<java.nio.file.attribute.PosixFilePermission> OWNER_ONLY = java.util.Set.of(
      java.nio.file.attribute.PosixFilePermission.OWNER_READ,
      java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);

  private AtomicFiles() {}

  /** Writes a file that holds secrets, readable and writable only by the owner where supported. */
  public static void writePrivate(Path target, String content) throws IOException {
    writeString(target, content);
    try {
      Files.setPosixFilePermissions(target, OWNER_ONLY);
    } catch (UnsupportedOperationException ignored) {
      // Windows relies on the user profile ACLs.
    }
  }

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
